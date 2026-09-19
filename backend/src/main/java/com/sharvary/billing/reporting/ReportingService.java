package com.sharvary.billing.reporting;

import com.sharvary.billing.common.Money;
import com.sharvary.billing.subscription.SubscriptionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Revenue numbers for the dashboard, straight from SQL. Everything is per currency; there is no
 * FX conversion in this system, so the dashboard shows one currency at a time.
 *
 * <p>MRR normalises annual plans to a twelfth. Churn is subscriptions canceled in the month over
 * the count active at the start of it.
 */
@Service
public class ReportingService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record PlanRevenue(String planCode, String planName, Money revenue, long invoices) {
    }

    public record MonthPoint(String month, Money amount) {
    }

    public record ChurnPoint(String month, long activeAtStart, long canceled, double rate) {
    }

    public record Dashboard(String currency, Money mrr, long activeSubscriptions, long trialing, long pastDue,
                            long inDunning, double churnRate, List<MonthPoint> revenueByMonth,
                            List<PlanRevenue> revenueByPlan, List<ChurnPoint> churnByMonth,
                            Map<SubscriptionStatus, Long> byStatus) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);

        Long mrrMinor = jdbc.queryForObject("""
                select coalesce(sum(case v.billing_interval when 'ANNUAL' then
                    (case v.pricing_model when 'PER_SEAT' then v.base_price_minor * s.quantity else v.base_price_minor end) / 12
                    else (case v.pricing_model when 'PER_SEAT' then v.base_price_minor * s.quantity else v.base_price_minor end) end), 0)
                from subscriptions s join plan_versions v on v.id = s.plan_version_id
                where s.status in ('ACTIVE', 'PAST_DUE') and v.currency = ?
                """, Long.class, currencyCode);

        Map<SubscriptionStatus, Long> byStatus = new java.util.EnumMap<>(SubscriptionStatus.class);
        jdbc.query("select status, count(*) as n from subscriptions group by status",
                rs -> {
                    byStatus.put(SubscriptionStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });

        Long inDunning = jdbc.queryForObject("select count(*) from dunning_cases where state = 'RETRYING'", Long.class);

        List<MonthPoint> revenueByMonth = new ArrayList<>();
        YearMonth start = YearMonth.from(today).minusMonths(5);
        Map<String, Long> paidByMonth = new java.util.HashMap<>();
        jdbc.query("""
                select to_char(paid_at at time zone 'UTC', 'YYYY-MM') as m, sum(amount_paid_minor) as paid
                from invoices where status in ('PAID', 'REFUNDED') and currency = ? and paid_at >= ?
                group by m
                """, rs -> {
            paidByMonth.put(rs.getString("m"), rs.getLong("paid"));
        }, currencyCode, java.sql.Timestamp.from(start.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        for (int i = 0; i < 6; i++) {
            YearMonth m = start.plusMonths(i);
            revenueByMonth.add(new MonthPoint(m.toString(), Money.of(paidByMonth.getOrDefault(m.toString(), 0L), currency)));
        }

        List<PlanRevenue> byPlan = jdbc.query("""
                select p.code, p.name, sum(l.amount_minor) as revenue, count(distinct i.id) as invoices
                from invoice_line_items l
                join invoices i on i.id = l.invoice_id
                join subscriptions s on s.id = i.subscription_id
                join plan_versions v on v.id = s.plan_version_id
                join plans p on p.id = v.plan_id
                where i.status in ('PAID', 'REFUNDED') and i.currency = ? and l.line_type in ('PLAN', 'PRORATION_CHARGE', 'PRORATION_CREDIT', 'USAGE')
                group by p.code, p.name order by revenue desc
                """, (rs, n) -> new PlanRevenue(rs.getString("code"), rs.getString("name"),
                Money.of(rs.getLong("revenue"), currency), rs.getLong("invoices")), currencyCode);

        List<ChurnPoint> churn = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth m = start.plusMonths(i);
            java.sql.Timestamp monthStart = java.sql.Timestamp.from(m.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            java.sql.Timestamp monthEnd = java.sql.Timestamp.from(m.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            Long activeAtStart = jdbc.queryForObject("""
                    select count(*) from subscriptions
                    where created_at < ? and (canceled_at is null or canceled_at >= ?)
                    """, Long.class, monthStart, monthStart);
            Long canceled = jdbc.queryForObject(
                    "select count(*) from subscriptions where canceled_at >= ? and canceled_at < ?",
                    Long.class, monthStart, monthEnd);
            double rate = activeAtStart == 0 ? 0 : (double) canceled / activeAtStart;
            churn.add(new ChurnPoint(m.toString(), activeAtStart, canceled, rate));
        }
        double currentChurn = churn.isEmpty() ? 0 : churn.get(churn.size() - 1).rate();

        return new Dashboard(currencyCode, Money.of(mrrMinor == null ? 0 : mrrMinor, currency),
                byStatus.getOrDefault(SubscriptionStatus.ACTIVE, 0L), byStatus.getOrDefault(SubscriptionStatus.TRIALING, 0L),
                byStatus.getOrDefault(SubscriptionStatus.PAST_DUE, 0L), inDunning == null ? 0 : inDunning, currentChurn,
                revenueByMonth, byPlan, churn, byStatus);
    }
}

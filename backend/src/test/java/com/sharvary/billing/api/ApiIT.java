package com.sharvary.billing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharvary.billing.auth.Role;
import com.sharvary.billing.config.MutableClock;
import com.sharvary.billing.customer.Customer;
import com.sharvary.billing.invoice.Invoice;
import com.sharvary.billing.invoice.InvoiceRepository;
import com.sharvary.billing.payment.MockPaymentProvider;
import com.sharvary.billing.plan.PlanVersion;
import com.sharvary.billing.subscription.BillingService;
import com.sharvary.billing.subscription.Subscription;
import com.sharvary.billing.support.Fixtures;
import com.sharvary.billing.support.PostgresIT;
import com.sharvary.billing.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth, ownership, validation and idempotency at the HTTP boundary. */
@AutoConfigureMockMvc
class ApiIT extends PostgresIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Fixtures fixtures;
    @Autowired BillingService billing;
    @Autowired InvoiceRepository invoices;
    @Autowired MutableClock clock;
    @Autowired MockPaymentProvider provider;

    String admin;
    String alice;
    Customer aliceCustomer;
    Customer bobCustomer;
    Subscription bobSub;
    Invoice bobInvoice;
    PlanVersion plan;

    @BeforeEach
    void setUp() {
        fixtures.wipe();
        provider.reset();
        clock.set(TestClockConfig.START);
        admin = fixtures.login("admin-" + UUID.randomUUID() + "@test.local", "password1", Role.ADMIN, null);
        aliceCustomer = fixtures.customer("US");
        bobCustomer = fixtures.customer("GB");
        alice = fixtures.login(aliceCustomer.getEmail(), "password1", Role.CUSTOMER, aliceCustomer.getId());
        plan = fixtures.flatMonthly(1500);
        bobSub = billing.subscribe(bobCustomer.getId(), plan.getId(), 1, 0, List.of());
        bobInvoice = invoices.findBySubscriptionIdOrderByCreatedAtAsc(bobSub.getId()).get(0);
    }

    @Test
    void unauthenticatedRequestsGet401() throws Exception {
        mvc.perform(get("/api/me/invoices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/invoices").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesATokenAndBadCredentialsDoNot() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceCustomer.getEmail() + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerId").value(aliceCustomer.getId().toString()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + aliceCustomer.getEmail() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(aliceCustomer.getEmail()));
    }

    @Test
    void customersCannotReachAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/customers").header("Authorization", "Bearer " + alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/invoices").header("Authorization", "Bearer " + alice)).andExpect(status().isForbidden());
    }

    @Test
    void probingAnotherCustomersInvoiceIs404NotForbidden() throws Exception {
        // Bob's invoice exists; Alice asks for it by id and must not learn that it exists.
        mvc.perform(get("/api/me/invoices/" + bobInvoice.getId()).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/subscriptions/" + bobSub.getId()).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/subscriptions/" + bobSub.getId() + "/estimate").header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/me/subscriptions/" + bobSub.getId() + "/cancel").header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"immediately\":true}"))
                .andExpect(status().isNotFound());
        // A genuinely missing id looks exactly the same.
        mvc.perform(get("/api/me/invoices/" + UUID.randomUUID()).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());
        // The admin can see it.
        mvc.perform(get("/api/admin/invoices/" + bobInvoice.getId()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceNumber").value(bobInvoice.getInvoiceNumber()));
        // Bob's own list would include it; Alice's does not.
        mvc.perform(get("/api/me/invoices").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validationErrorsAre400WithFieldDetails() throws Exception {
        mvc.perform(post("/api/admin/customers").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"currency\":\"usd\",\"taxRegion\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.currency").exists());
        mvc.perform(post("/api/admin/subscriptions").header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + aliceCustomer.getId() + "\",\"planVersionId\":\"" + plan.getId() + "\",\"quantity\":0,\"trialDays\":0}"))
                .andExpect(status().isBadRequest());
        // Missing idempotency key header.
        mvc.perform(post("/api/admin/subscriptions").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + aliceCustomer.getId() + "\",\"planVersionId\":\"" + plan.getId() + "\",\"quantity\":1,\"trialDays\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayingASubscribeKeyReturnsTheOriginalInsteadOfADuplicate() throws Exception {
        String body = "{\"customerId\":\"" + aliceCustomer.getId() + "\",\"planVersionId\":\"" + plan.getId() + "\",\"quantity\":2,\"trialDays\":0}";
        MvcResult first = mvc.perform(post("/api/admin/subscriptions").header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", "sub-abc").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult second = mvc.perform(post("/api/admin/subscriptions").header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", "sub-abc").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode a = json.readTree(first.getResponse().getContentAsString());
        JsonNode b = json.readTree(second.getResponse().getContentAsString());
        assertThat(b.get("id").asText()).isEqualTo(a.get("id").asText());
        assertThat(b.get("periodPrice").get("minor").asLong()).isEqualTo(1500);
        assertThat(billing.listForCustomer(aliceCustomer.getId())).hasSize(1);

        // Same key, different body: refused.
        String other = "{\"customerId\":\"" + aliceCustomer.getId() + "\",\"planVersionId\":\"" + plan.getId() + "\",\"quantity\":3,\"trialDays\":0}";
        mvc.perform(post("/api/admin/subscriptions").header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", "sub-abc").contentType(MediaType.APPLICATION_JSON).content(other))
                .andExpect(status().isUnprocessableEntity());
        assertThat(billing.listForCustomer(aliceCustomer.getId())).hasSize(1);
    }

    @Test
    void usageIsIdempotentAndOwnershipScoped() throws Exception {
        PlanVersion metered = fixtures.tieredMonthly();
        Subscription aliceSub = billing.subscribe(aliceCustomer.getId(), plan.getId(), 1, 0, List.of(metered.getId()));
        UUID item = aliceSub.getItems().get(0).getId();
        String body = "{\"subscriptionItemId\":\"" + item + "\",\"quantity\":40}";
        MvcResult first = mvc.perform(post("/api/usage").header("Authorization", "Bearer " + alice)
                        .header("Idempotency-Key", "use-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = mvc.perform(post("/api/usage").header("Authorization", "Bearer " + alice)
                        .header("Idempotency-Key", "use-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json.readTree(replay.getResponse().getContentAsString()).get("id"))
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()).get("id"));
        mvc.perform(get("/api/usage/items/" + item).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // Bob's token cannot meter Alice's item.
        String bob = fixtures.login(bobCustomer.getEmail(), "password1", Role.CUSTOMER, bobCustomer.getId());
        mvc.perform(post("/api/usage").header("Authorization", "Bearer " + bob)
                        .header("Idempotency-Key", "use-2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/usage/items/" + item).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        // Admin can.
        mvc.perform(post("/api/usage").header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", "use-3").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void customerFacingHappyPath() throws Exception {
        Subscription mine = billing.subscribe(aliceCustomer.getId(), plan.getId(), 1, 0, List.of());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(aliceCustomer.getEmail()));
        mvc.perform(get("/api/me/subscriptions").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(mine.getId().toString()));
        mvc.perform(get("/api/me/subscriptions/" + mine.getId() + "/estimate").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total.minor").value(1500));
        mvc.perform(get("/api/me/invoices").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].total.amount").value("15.00"));
        mvc.perform(get("/api/me/payment-methods").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].last4").value("4242"));
        mvc.perform(post("/api/me/payment-methods").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok_ok\",\"brand\":\"amex\",\"last4\":\"0005\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + alice)).andExpect(status().isOk());
        PlanVersion bigger = fixtures.flatMonthly(3000);
        mvc.perform(post("/api/me/subscriptions/" + mine.getId() + "/change").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersionId\":\"" + bigger.getId() + "\",\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoice.kind").value("PRORATION"));
        mvc.perform(get("/api/me/notifications").header("Authorization", "Bearer " + alice)).andExpect(status().isOk());
    }

    @Test
    void adminConsoleEndpoints() throws Exception {
        mvc.perform(post("/api/admin/plans").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"api","name":"API","price":{"currency":"USD","interval":"MONTHLY","pricingModel":"TIERED",
                                 "basePriceMinor":0,"tiers":[{"upTo":100,"unitPriceMinor":10,"flatFeeMinor":0},{"upTo":null,"unitPriceMinor":5,"flatFeeMinor":0}]}}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentVersion.tiers.length()").value(2));
        mvc.perform(get("/api/admin/plans").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/customers?q=Customer").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/admin/subscriptions?status=ACTIVE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].customerName").exists());
        mvc.perform(get("/api/admin/invoices?status=PAID").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/invoices/" + bobInvoice.getId() + "/attempts").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
        mvc.perform(get("/api/admin/dunning").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/notifications").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mrr.minor").value(1500))
                .andExpect(jsonPath("$.activeSubscriptions").value(1));
        mvc.perform(post("/api/admin/jobs/billing/run").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(0));
        mvc.perform(post("/api/admin/jobs/dunning/run").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        // Illegal state transition surfaces as 409.
        mvc.perform(post("/api/admin/invoices/" + bobInvoice.getId() + "/void").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/invoices/" + bobInvoice.getId() + "/credit-notes").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"oops\",\"lines\":[{\"description\":\"partial\",\"amountMinor\":500}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total.minor").value(500));
        mvc.perform(get("/api/admin/invoices/" + bobInvoice.getId() + "/credit-notes").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/customers/" + bobCustomer.getId() + "/payment-methods").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/subscriptions/" + bobSub.getId() + "/estimate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/customers/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiIsPublished() throws Exception {
        mvc.perform(get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title").exists());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}

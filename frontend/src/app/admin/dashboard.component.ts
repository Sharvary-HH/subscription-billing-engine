import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DemoClockService } from '../core/demo-clock.service';
import { Dashboard } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { StatTileComponent } from '../shared/stat-tile.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, PercentPipe, RouterLink, MoneyPipe, StatTileComponent, BarChartComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Revenue</h1><p class="slate">All figures in {{ d()?.currency }}.</p></div>
      </div>

      @if (demo.info(); as info) {
        <div class="card demo">
          <div class="row">
            <div>
              <div class="lbl">Time travel</div>
              <div class="big">{{ info.now | date:'EEEE d MMMM y' }}</div>
              <div class="muted" style="font-size:13px">The clock is injected everywhere, so advancing it runs the real billing and dunning jobs a day at a time.</div>
            </div>
            <div class="controls">
              <button class="btn accent" (click)="advance(30)" [disabled]="busy()">Advance one month</button>
              <button class="btn" (click)="advance(1)" [disabled]="busy()">Advance one day</button>
              <button class="btn" (click)="paymentMode(info.paymentMode === 'DECLINE' ? 'SUCCEED' : 'DECLINE')" [disabled]="busy()">
                {{ info.paymentMode === 'DECLINE' ? 'Stop forcing failures' : 'Force payment failures' }}
              </button>
              <button class="btn ghost" (click)="reset()" [disabled]="busy()">Reset demo</button>
            </div>
          </div>
          @if (last()) { <div class="notice" style="margin:12px 0 0">{{ last() }}</div> }
          @if (info.paymentMode !== 'SUCCEED') { <div class="notice err" style="margin:12px 0 0">Mock provider is set to <b>{{ info.paymentMode }}</b>: every new charge will fail and open a dunning case.</div> }
        </div>
      }

      @if (d(); as d) {
        <div class="grid cols-4" style="margin:16px 0">
          <app-stat label="MRR" [value]="d.mrr | money" hint="annual plans counted at a twelfth" accent />
          <app-stat label="Active" [value]="d.activeSubscriptions" [hint]="d.trialing + ' trialing'" />
          <app-stat label="Past due" [value]="d.pastDue" [hint]="d.inDunning + ' in dunning'" />
          <app-stat label="Churn this month" [value]="d.churnRate | percent:'1.0-1'" hint="canceled ÷ active at month start" />
        </div>
        <div class="grid cols-2">
          <div class="card">
            <h3>Cash collected by month</h3>
            <app-bar-chart [data]="byMonth()" title="Cash collected by month" />
          </div>
          <div class="card">
            <h3>Churn by month</h3>
            <app-bar-chart [data]="churn()" title="Churn rate by month" />
          </div>
        </div>
        <div class="grid cols-2" style="margin-top:16px">
          <div class="card">
            <h3>Revenue by plan</h3>
            <table class="data" style="margin-top:8px">
              <thead><tr><th>Plan</th><th class="num">Invoices</th><th class="num">Revenue</th></tr></thead>
              <tbody>
                @for (p of d.revenueByPlan; track p.planCode) {
                  <tr><td>{{ p.planName }}</td><td class="num">{{ p.invoices }}</td><td class="num">{{ p.revenue | money:false }}</td></tr>
                } @empty { <tr><td colspan="3" class="empty">Nothing paid yet.</td></tr> }
              </tbody>
            </table>
          </div>
          <div class="card">
            <h3>Subscriptions by status</h3>
            <table class="data" style="margin-top:8px">
              <tbody>
                @for (s of statuses(); track s.key) {
                  <tr class="click" [routerLink]="['/admin/subscriptions']" [queryParams]="{ status: s.key }"><td>{{ s.key }}</td><td class="num">{{ s.value }}</td></tr>
                }
              </tbody>
            </table>
            <a routerLink="/admin/dunning" class="btn ghost small" style="margin-top:12px;display:inline-block">Open dunning queue →</a>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .demo { border-left: 6px solid var(--acid); }
    .row { display: flex; justify-content: space-between; gap: 16px; align-items: center; flex-wrap: wrap; }
    .lbl { font-size: 12px; text-transform: uppercase; letter-spacing: .05em; color: var(--slate); font-weight: 600; }
    .big { font-size: 22px; font-weight: 600; }
    .controls { display: flex; gap: 8px; flex-wrap: wrap; }
  `],
})
export class DashboardComponent {
  private api = inject(ApiService);
  demo = inject(DemoClockService);
  d = signal<Dashboard | null>(null);
  busy = signal(false);
  last = signal('');

  byMonth = computed<BarDatum[]>(() => (this.d()?.revenueByMonth ?? []).map((m) => ({
    label: monthLabel(m.month), value: m.amount.minor, display: m.amount.amount,
  })));
  churn = computed<BarDatum[]>(() => (this.d()?.churnByMonth ?? []).map((m) => ({
    label: monthLabel(m.month), value: m.rate, display: (m.rate * 100).toFixed(1) + '%',
  })));
  statuses = computed(() => Object.entries(this.d()?.byStatus ?? {}).map(([key, value]) => ({ key, value })));

  constructor() {
    this.load();
  }

  load(): void {
    this.api.dashboard().subscribe((d) => this.d.set(d));
  }

  advance(days: number): void {
    this.busy.set(true);
    this.api.demoAdvance(days).subscribe({
      next: (r) => {
        this.last.set(`Advanced ${days} day${days === 1 ? '' : 's'}: ${r.subscriptionsBilled} subscription${r.subscriptionsBilled === 1 ? '' : 's'} billed, ${r.retriesFired} payment retr${r.retriesFired === 1 ? 'y' : 'ies'} fired.`);
        this.done();
      },
      error: () => this.busy.set(false),
    });
  }

  paymentMode(mode: 'SUCCEED' | 'DECLINE'): void {
    this.busy.set(true);
    this.api.demoPaymentMode(mode).subscribe({ next: () => this.done(), error: () => this.busy.set(false) });
  }

  reset(): void {
    if (!confirm('Reseed the demo data and set the clock back?')) return;
    this.busy.set(true);
    this.last.set('Reseeding…');
    this.api.demoReset().subscribe({ next: () => { this.last.set('Demo reset.'); this.done(); }, error: () => this.busy.set(false) });
  }

  private done(): void {
    this.busy.set(false);
    this.demo.refresh();
    this.load();
  }
}

function monthLabel(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleString('en', { month: 'short' }) + ' ' + String(y).slice(2);
}

import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Customer, Estimate, Invoice, Subscription } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { BadgeComponent } from '../shared/badge.component';
import { StatTileComponent } from '../shared/stat-tile.component';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [DatePipe, RouterLink, MoneyPipe, BadgeComponent, StatTileComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Hello, {{ customer()?.name }}</h1>
          <p class="slate">{{ customer()?.email }} · bills in {{ customer()?.currency }} · tax region {{ customer()?.taxRegion }}</p>
        </div>
        @if (customer()?.creditBalance?.minor) {
          <div class="notice" style="margin:0">Credit balance: <b>{{ customer()!.creditBalance | money }}</b> — applied to your next invoice.</div>
        }
      </div>

      @for (s of subscriptions(); track s.id) {
        <div class="card sub">
          <div class="head">
            <div>
              <h2>{{ s.planVersion.planName }}
                @if (s.planVersion.pricingModel === 'PER_SEAT') { <span class="slate">× {{ s.quantity }} seats</span> }
              </h2>
              <p class="slate">{{ s.periodPrice | money }} per {{ s.planVersion.interval === 'ANNUAL' ? 'year' : 'month' }} · version {{ s.planVersion.version }}</p>
            </div>
            <app-badge [value]="s.status" />
          </div>
          <div class="grid cols-3" style="margin:16px 0">
            <app-stat label="Current period" [value]="(s.currentPeriodStart | date:'d MMM') + ' – ' + (s.currentPeriodEnd | date:'d MMM y')" />
            @if (s.status === 'TRIALING') {
              <app-stat label="Trial ends" [value]="s.trialEnd | date:'d MMM y'" accent />
            } @else if (s.cancelAt) {
              <app-stat label="Cancels on" [value]="s.cancelAt | date:'d MMM y'" />
            } @else {
              <app-stat label="Next invoice" [value]="(estimates()[s.id]?.total | money) || '—'" [hint]="'on ' + (s.currentPeriodEnd | date:'d MMM y')" accent />
            }
            <app-stat label="Metered add-ons" [value]="s.items.length" [hint]="s.items.length ? itemNames(s) : 'none'" />
          </div>
          @if (estimates()[s.id]; as e) {
            @if (e.lines.length) {
              <details>
                <summary class="slate">What the next invoice will include</summary>
                <table class="data" style="margin-top:8px">
                  <tbody>
                    @for (l of e.lines; track $index) {
                      <tr><td>{{ l.description }}</td><td class="num">{{ l.amount | money:false }}</td></tr>
                    }
                    <tr><td class="slate">Tax</td><td class="num">{{ e.tax | money:false }}</td></tr>
                    <tr><td><b>Total</b></td><td class="num"><b>{{ e.total | money }}</b></td></tr>
                  </tbody>
                </table>
              </details>
            }
          }
          <div class="toolbar no-print" style="margin:14px 0 0">
            @if (s.status === 'ACTIVE' || s.status === 'TRIALING') {
              <a class="btn" [routerLink]="['/account/change-plan', s.id]">Change plan or seats</a>
              @if (s.cancelAt) {
                <button class="btn ghost" (click)="undoCancel(s)">Keep my subscription</button>
              } @else {
                <button class="btn ghost" (click)="cancel(s)">Cancel at period end</button>
              }
            }
          </div>
        </div>
      } @empty {
        <div class="card empty">You have no subscriptions.</div>
      }

      <h2 style="margin:28px 0 12px">Invoice history</h2>
      <div class="card" style="padding:0">
        <table class="data">
          <thead><tr><th>Number</th><th>Period</th><th>Kind</th><th>Status</th><th class="num">Total</th></tr></thead>
          <tbody>
            @for (i of invoices(); track i.id) {
              <tr class="click" [routerLink]="['/account/invoices', i.id]">
                <td>{{ i.invoiceNumber }}</td>
                <td>{{ i.periodStart | date:'d MMM y' }} – {{ i.periodEnd | date:'d MMM y' }}</td>
                <td class="slate">{{ i.kind.toLowerCase() }}</td>
                <td><app-badge [value]="i.status" /></td>
                <td class="num">{{ i.total | money }}</td>
              </tr>
            } @empty { <tr><td colspan="5" class="empty">No invoices yet.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`.sub { margin-bottom: 16px; } .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; } details summary { cursor: pointer; }`],
})
export class OverviewComponent {
  private api = inject(ApiService);
  customer = signal<Customer | null>(null);
  subscriptions = signal<Subscription[]>([]);
  invoices = signal<Invoice[]>([]);
  estimates = signal<Record<string, Estimate>>({});

  constructor() {
    this.load();
  }

  load(): void {
    forkJoin({ c: this.api.me(), s: this.api.mySubscriptions(), i: this.api.myInvoices() }).subscribe(({ c, s, i }) => {
      this.customer.set(c);
      this.subscriptions.set(s);
      this.invoices.set(i);
      for (const sub of s) {
        this.api.myEstimate(sub.id).subscribe((e) => this.estimates.update((m) => ({ ...m, [sub.id]: e })));
      }
    });
  }

  itemNames(s: Subscription): string {
    return s.items.map((i) => i.planVersion.planName).join(', ');
  }

  cancel(s: Subscription): void {
    if (confirm('Cancel at the end of the current period? You keep access until then.')) {
      this.api.myCancel(s.id, false).subscribe(() => this.load());
    }
  }

  undoCancel(s: Subscription): void {
    this.api.myUndoCancel(s.id).subscribe(() => this.load());
  }
}

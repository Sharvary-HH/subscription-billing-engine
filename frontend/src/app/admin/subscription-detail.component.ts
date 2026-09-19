import { Component, Input, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Estimate, Invoice, Plan, Subscription } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { BadgeComponent } from '../shared/badge.component';

@Component({
  selector: 'app-subscription-detail',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MoneyPipe, BadgeComponent],
  template: `
    <div class="page">
      <a routerLink="/admin/subscriptions" class="btn ghost small">← Subscriptions</a>
      @if (sub(); as s) {
        <div class="page-head" style="margin-top:14px">
          <div>
            <h1>{{ s.customerName }} <app-badge [value]="s.status" /></h1>
            <p class="slate"><a [routerLink]="['/admin/customers', s.customerId]">{{ s.customerEmail }}</a> · {{ s.planVersion.planName }} v{{ s.planVersion.version }}
              @if (s.planVersion.pricingModel === 'PER_SEAT') { × {{ s.quantity }} seats } · {{ s.periodPrice | money }} / {{ s.planVersion.interval === 'ANNUAL' ? 'year' : 'month' }}</p>
          </div>
          <div class="toolbar" style="margin:0">
            @if (s.status === 'ACTIVE') { <button class="btn ghost small" (click)="act(api.pause(s.id))">Pause</button> }
            @if (s.status === 'PAUSED') { <button class="btn small" (click)="act(api.resume(s.id))">Resume</button> }
            @if (s.status !== 'CANCELED') {
              @if (s.cancelAt) { <button class="btn ghost small" (click)="act(api.undoCancel(s.id))">Undo cancel</button> }
              @else { <button class="btn ghost small" (click)="act(api.cancel(s.id, false))">Cancel at period end</button> }
              <button class="btn danger small" (click)="cancelNow(s)">Cancel now</button>
            }
          </div>
        </div>
        @if (message()) { <div class="notice">{{ message() }}</div> }
        @if (error()) { <div class="notice err">{{ error() }}</div> }

        <div class="grid cols-3">
          <div class="card">
            <h3>Cycle</h3>
            <dl class="kv" style="margin-top:8px">
              <dt>Anchor day</dt><dd>{{ s.anchorDay }}</dd>
              <dt>Period</dt><dd>{{ s.currentPeriodStart | date:'d MMM y' }} – {{ s.currentPeriodEnd | date:'d MMM y' }}</dd>
              @if (s.trialEnd) { <dt>Trial ends</dt><dd>{{ s.trialEnd | date:'d MMM y' }}</dd> }
              @if (s.cancelAt) { <dt>Cancels</dt><dd>{{ s.cancelAt | date:'d MMM y' }}</dd> }
              @if (s.canceledAt) { <dt>Canceled</dt><dd>{{ s.canceledAt | date:'d MMM y, HH:mm' }}</dd> }
              <dt>Since</dt><dd>{{ s.createdAt | date:'d MMM y' }}</dd>
            </dl>
            @if (s.items.length) {
              <h3 style="margin-top:14px">Metered items</h3>
              <ul style="margin:6px 0 0;padding-left:18px">@for (i of s.items; track i.id) { <li>{{ i.planVersion.planName }} <span class="muted" style="font-size:12px">{{ i.id }}</span></li> }</ul>
            }
          </div>
          <div class="card">
            <h3>Next invoice estimate</h3>
            @if (estimate(); as e) {
              @if (e.lines.length) {
                <table class="data" style="margin-top:8px"><tbody>
                  @for (l of e.lines; track $index) { <tr><td>{{ l.description }}</td><td class="num">{{ l.amount | money:false }}</td></tr> }
                  <tr><td class="slate">Tax</td><td class="num">{{ e.tax | money:false }}</td></tr>
                  <tr><td><b>Total on {{ e.nextBillingDate | date:'d MMM' }}</b></td><td class="num"><b>{{ e.total | money }}</b></td></tr>
                </tbody></table>
              } @else { <div class="empty">Nothing further will be billed.</div> }
            }
          </div>
          <div class="card">
            <h3>Change plan or seats</h3>
            <form [formGroup]="change" (ngSubmit)="applyChange(s)" style="margin-top:8px">
              <div class="field"><label>Plan</label>
                <select formControlName="planVersionId">
                  @for (p of plans(); track p.id) { @if (p.currentVersion && p.currentVersion.interval === s.planVersion.interval) { <option [value]="p.currentVersion.id">{{ p.name }} — {{ p.currentVersion.basePrice | money }}</option> } }
                </select></div>
              <div class="field"><label>Seats</label><input type="number" min="1" formControlName="quantity"></div>
              <button class="btn small" type="submit" [disabled]="change.invalid || s.status === 'CANCELED' || s.status === 'PAUSED' || s.status === 'PAST_DUE'">Apply from today</button>
            </form>
          </div>
        </div>

        <h2 style="margin:24px 0 10px">Invoices</h2>
        <div class="card" style="padding:0">
          <table class="data">
            <thead><tr><th>Number</th><th>Kind</th><th>Period</th><th>Status</th><th class="num">Total</th></tr></thead>
            <tbody>
              @for (i of invoices(); track i.id) {
                <tr class="click" [routerLink]="['/admin/invoices', i.id]">
                  <td>{{ i.invoiceNumber }}</td><td class="slate">{{ i.kind.toLowerCase() }}</td>
                  <td>{{ i.periodStart | date:'d MMM y' }} – {{ i.periodEnd | date:'d MMM y' }}</td>
                  <td><app-badge [value]="i.status" /></td><td class="num">{{ i.total | money }}</td>
                </tr>
              } @empty { <tr><td colspan="5" class="empty">No invoices.</td></tr> }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class SubscriptionDetailComponent {
  api = inject(ApiService);
  sub = signal<Subscription | null>(null);
  invoices = signal<Invoice[]>([]);
  estimate = signal<Estimate | null>(null);
  plans = signal<Plan[]>([]);
  message = signal('');
  error = signal('');
  private subId = '';

  change = new FormGroup({
    planVersionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1)] }),
  });

  @Input() set id(v: string) {
    this.subId = v;
    this.load();
    this.api.adminPlans().subscribe((p) => this.plans.set(p.filter((x) => x.active && x.currentVersion
      && (x.currentVersion.pricingModel === 'FLAT' || x.currentVersion.pricingModel === 'PER_SEAT'))));
  }

  load(): void {
    this.api.subscription(this.subId).subscribe((s) => {
      this.sub.set(s);
      this.change.patchValue({ planVersionId: s.planVersion.id, quantity: s.quantity });
      this.api.invoices('', s.customerId).subscribe((all) => this.invoices.set(all.filter((i) => i.subscriptionId === s.id)));
      this.api.estimate(s.id).subscribe({ next: (e) => this.estimate.set(e), error: () => this.estimate.set(null) });
    });
  }

  act(call: { subscribe: (o: { next: () => void; error: (e: { error?: { detail?: string } }) => void }) => unknown }): void {
    this.error.set('');
    call.subscribe({ next: () => this.load(), error: (e) => this.error.set(e.error?.detail ?? 'That did not work.') });
  }

  cancelNow(s: Subscription): void {
    if (confirm('Cancel immediately? Access ends now and any dunning stops. Open invoices remain payable.')) {
      this.act(this.api.cancel(s.id, true));
    }
  }

  applyChange(s: Subscription): void {
    const v = this.change.getRawValue();
    this.error.set('');
    this.api.change(s.id, v.planVersionId === s.planVersion.id ? null : v.planVersionId, v.quantity).subscribe({
      next: (r) => {
        this.message.set(r.invoice
          ? `Upgrade invoiced now: ${r.invoice.invoiceNumber} for ${r.invoice.total.currency} ${r.invoice.total.amount} (credited ${r.credited.amount}, charged ${r.charged.amount}).`
          : `Downgrade applied: ${r.credited.currency} ${(r.credited.minor - r.charged.minor) / 100} added to the customer's credit balance.`);
        this.load();
      },
      error: (e) => this.error.set(e.error?.detail ?? 'The change could not be applied.'),
    });
  }
}

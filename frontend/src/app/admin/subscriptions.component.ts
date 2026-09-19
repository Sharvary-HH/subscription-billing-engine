import { Component, Input, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Customer, Plan, Subscription, SubscriptionStatus } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { BadgeComponent } from '../shared/badge.component';

@Component({
  selector: 'app-subscriptions',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MoneyPipe, BadgeComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Subscriptions</h1></div>
        <button class="btn accent" (click)="showNew.set(!showNew())">{{ showNew() ? 'Close' : 'New subscription' }}</button>
      </div>

      @if (showNew()) {
        <form class="card" [formGroup]="newForm" (ngSubmit)="create()" style="margin-bottom:16px">
          <div class="grid cols-4">
            <div class="field"><label>Customer</label>
              <select formControlName="customerId"><option value="">choose…</option>@for (c of customers(); track c.id) { <option [value]="c.id">{{ c.name }} ({{ c.currency }})</option> }</select></div>
            <div class="field"><label>Plan</label>
              <select formControlName="planVersionId"><option value="">choose…</option>@for (p of basePlans(); track p.id) { <option [value]="p.currentVersion!.id">{{ p.name }} — {{ p.currentVersion!.basePrice | money }}</option> }</select></div>
            <div class="field"><label>Seats</label><input type="number" min="1" formControlName="quantity"></div>
            <div class="field"><label>Trial days</label><input type="number" min="0" formControlName="trialDays"></div>
          </div>
          <div class="field"><label>Metered add-ons</label>
            <div style="display:flex;gap:14px;flex-wrap:wrap">
              @for (p of meteredPlans(); track p.id) {
                <label style="font-weight:400"><input type="checkbox" [checked]="addOns().has(p.currentVersion!.id)" (change)="toggleAddOn(p.currentVersion!.id)"> {{ p.name }}</label>
              }
            </div>
          </div>
          @if (error()) { <div class="notice err">{{ error() }}</div> }
          <button class="btn" type="submit" [disabled]="newForm.invalid">Subscribe</button>
          <span class="muted" style="margin-left:10px;font-size:13px">Sent with an idempotency key; a double click cannot create two.</span>
        </form>
      }

      <div class="toolbar" [formGroup]="filter">
        <input formControlName="q" placeholder="Search by customer name or email" style="min-width:280px">
        <select formControlName="status">
          <option value="">All statuses</option>
          @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
        </select>
        <span class="muted">{{ rows().length }} result{{ rows().length === 1 ? '' : 's' }}</span>
      </div>

      <div class="card" style="padding:0">
        <table class="data">
          <thead><tr><th>Customer</th><th>Plan</th><th>Status</th><th>Period</th><th class="num">Price</th><th>Add-ons</th></tr></thead>
          <tbody>
            @for (s of rows(); track s.id) {
              <tr class="click" [routerLink]="['/admin/subscriptions', s.id]">
                <td><b>{{ s.customerName }}</b><div class="muted" style="font-size:12px">{{ s.customerEmail }}</div></td>
                <td>{{ s.planVersion.planName }} <span class="muted">v{{ s.planVersion.version }}</span>@if (s.planVersion.pricingModel === 'PER_SEAT') { × {{ s.quantity }} }</td>
                <td><app-badge [value]="s.status" />@if (s.cancelAt) { <div class="muted" style="font-size:12px">cancels {{ s.cancelAt | date:'d MMM' }}</div> }</td>
                <td>{{ s.currentPeriodStart | date:'d MMM' }} – {{ s.currentPeriodEnd | date:'d MMM y' }}</td>
                <td class="num">{{ s.periodPrice | money:false }}</td>
                <td class="slate">{{ s.items.length ? s.items.length : '' }}</td>
              </tr>
            } @empty { <tr><td colspan="6" class="empty">No subscriptions match.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class SubscriptionsComponent {
  private api = inject(ApiService);
  rows = signal<Subscription[]>([]);
  customers = signal<Customer[]>([]);
  basePlans = signal<Plan[]>([]);
  meteredPlans = signal<Plan[]>([]);
  addOns = signal(new Set<string>());
  showNew = signal(false);
  error = signal('');
  statuses: SubscriptionStatus[] = ['TRIALING', 'ACTIVE', 'PAST_DUE', 'PAUSED', 'CANCELED'];

  filter = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    status: new FormControl<SubscriptionStatus | ''>('', { nonNullable: true }),
  });
  newForm = new FormGroup({
    customerId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    planVersionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1)] }),
    trialDays: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
  });

  @Input() set status(v: SubscriptionStatus | undefined) {
    if (v) this.filter.controls.status.setValue(v);
  }

  constructor() {
    this.filter.valueChanges.pipe(debounceTime(200)).subscribe(() => this.load());
    this.load();
    this.api.customers().subscribe((c) => this.customers.set(c));
    this.api.adminPlans().subscribe((p) => {
      const active = p.filter((x) => x.active && x.currentVersion);
      this.basePlans.set(active.filter((x) => x.currentVersion!.pricingModel === 'FLAT' || x.currentVersion!.pricingModel === 'PER_SEAT'));
      this.meteredPlans.set(active.filter((x) => x.currentVersion!.pricingModel === 'TIERED' || x.currentVersion!.pricingModel === 'VOLUME'));
    });
  }

  load(): void {
    const f = this.filter.getRawValue();
    this.api.subscriptions(f.q, f.status).subscribe((r) => this.rows.set(r));
  }

  toggleAddOn(id: string): void {
    this.addOns.update((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }

  create(): void {
    const v = this.newForm.getRawValue();
    this.api.subscribe({ ...v, meteredAddOnVersionIds: [...this.addOns()] }, crypto.randomUUID()).subscribe({
      next: () => { this.showNew.set(false); this.newForm.reset(); this.addOns.set(new Set()); this.error.set(''); this.load(); },
      error: (e) => this.error.set(e.error?.detail ?? 'Could not subscribe.'),
    });
  }
}

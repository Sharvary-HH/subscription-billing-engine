import { Component, Input, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Plan, Subscription } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';

/**
 * Typed reactive form for a plan or seat change. The submit goes straight to the change endpoint;
 * the response tells the customer whether they were invoiced now (upgrade) or credited (downgrade).
 */
@Component({
  selector: 'app-change-plan',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MoneyPipe],
  template: `
    <div class="page" style="max-width:720px">
      <a routerLink="/account" class="btn ghost small">← Back</a>
      <h1 style="margin:14px 0 4px">Change plan</h1>
      @if (sub(); as s) {
        <p class="slate" style="margin-bottom:20px">Currently on <b>{{ s.planVersion.planName }}</b>
          @if (s.planVersion.pricingModel === 'PER_SEAT') { with {{ s.quantity }} seats }
          — period {{ s.currentPeriodStart | date:'d MMM' }} to {{ s.currentPeriodEnd | date:'d MMM y' }}.</p>
        <div class="notice">Changes take effect today. Upgrades are invoiced now for the rest of the period; the unused part of your current plan is credited on the same invoice. Downgrades add the difference to your credit balance.</div>
        <form class="card" [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="plan">Plan</label>
            <select id="plan" formControlName="planVersionId">
              @for (p of plans(); track p.id) {
                @if (p.currentVersion && p.currentVersion.pricingModel !== 'TIERED' && p.currentVersion.pricingModel !== 'VOLUME' && p.currentVersion.interval === s.planVersion.interval) {
                  <option [value]="p.currentVersion.id">{{ p.name }} — {{ p.currentVersion.basePrice | money }}{{ p.currentVersion.pricingModel === 'PER_SEAT' ? ' per seat' : '' }}</option>
                }
              }
            </select>
            <div class="hint">Only plans on the same billing interval are shown.</div>
          </div>
          @if (selectedIsPerSeat()) {
            <div class="field">
              <label for="qty">Seats</label>
              <input id="qty" type="number" min="1" formControlName="quantity">
              @if (form.controls.quantity.invalid && form.controls.quantity.touched) { <div class="error">At least one seat.</div> }
            </div>
          }
          @if (error()) { <div class="notice err">{{ error() }}</div> }
          <button class="btn accent" type="submit" [disabled]="form.invalid || busy()">{{ busy() ? 'Applying…' : 'Apply change' }}</button>
        </form>
      }
    </div>
  `,
})
export class ChangePlanComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  sub = signal<Subscription | null>(null);
  plans = signal<Plan[]>([]);
  busy = signal(false);
  error = signal('');

  form = new FormGroup({
    planVersionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1)] }),
  });

  private selected = signal('');
  selectedIsPerSeat = computed(() => {
    const id = this.selected();
    return this.plans().some((p) => p.currentVersion?.id === id && p.currentVersion.pricingModel === 'PER_SEAT');
  });

  constructor() {
    this.form.controls.planVersionId.valueChanges.subscribe((v) => this.selected.set(v));
  }

  @Input() set id(value: string) {
    this.api.mySubscription(value).subscribe((s) => {
      this.sub.set(s);
      this.form.patchValue({ planVersionId: s.planVersion.id, quantity: s.quantity });
      this.selected.set(s.planVersion.id);
    });
    this.api.plans().subscribe((p) => this.plans.set(p));
  }

  submit(): void {
    const s = this.sub();
    if (!s) return;
    this.busy.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    const planVersionId = v.planVersionId === s.planVersion.id ? null : v.planVersionId;
    const quantity = this.selectedIsPerSeat() ? v.quantity : 1;
    this.api.myChange(s.id, planVersionId, quantity).subscribe({
      next: (r) => {
        if (r.invoice) {
          this.router.navigate(['/account/invoices', r.invoice.id]);
        } else {
          this.router.navigate(['/account']);
        }
      },
      error: (e) => {
        this.error.set(e.error?.detail ?? 'The change could not be applied.');
        this.busy.set(false);
      },
    });
  }
}

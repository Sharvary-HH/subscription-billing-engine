import { Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, PriceRequest } from '../core/api.service';
import { Plan, PlanVersion } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';

type PriceForm = FormGroup<{
  currency: FormControl<string>; interval: FormControl<'MONTHLY' | 'ANNUAL'>;
  pricingModel: FormControl<'FLAT' | 'PER_SEAT' | 'TIERED' | 'VOLUME'>; basePrice: FormControl<string>;
  tiers: FormArray<FormGroup<{ upTo: FormControl<string>; unitPrice: FormControl<string>; flatFee: FormControl<string> }>>;
}>;

/** Plan management. Prices are typed in major units here and sent to the API as integer minor units. */
@Component({
  selector: 'app-plans',
  standalone: true,
  imports: [NgTemplateOutlet, ReactiveFormsModule, MoneyPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Plans</h1><p class="slate">A price change publishes a new version. Existing subscriptions stay on theirs.</p></div>
        <button class="btn accent" (click)="showCreate.set(!showCreate())">{{ showCreate() ? 'Close' : 'New plan' }}</button>
      </div>

      @if (showCreate()) {
        <form class="card" [formGroup]="createForm" (ngSubmit)="create()" style="margin-bottom:16px">
          <div class="grid cols-2">
            <div class="field"><label for="code">Code</label><input id="code" formControlName="code" placeholder="team"><div class="hint">lowercase, digits, dashes</div></div>
            <div class="field"><label for="name">Name</label><input id="name" formControlName="name" placeholder="Team"></div>
          </div>
          <ng-container *ngTemplateOutlet="priceFields; context: { f: createForm.controls.price }" />
          @if (error()) { <div class="notice err">{{ error() }}</div> }
          <button class="btn" type="submit" [disabled]="createForm.invalid">Create plan</button>
        </form>
      }

      @for (p of plans(); track p.id) {
        <div class="card" style="margin-bottom:12px">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;flex-wrap:wrap">
            <div>
              <h2>{{ p.name }} <span class="muted" style="font-weight:400;font-size:14px">{{ p.code }}</span>
                @if (!p.active) { <span class="badge CANCELED">retired</span> }
              </h2>
              @if (p.currentVersion; as v) {
                <p class="slate">{{ describe(v) }}</p>
              }
            </div>
            <div class="toolbar" style="margin:0">
              <button class="btn ghost small" (click)="versioning.set(versioning() === p.id ? null : p.id)">New version</button>
              @if (p.active) { <button class="btn danger small" (click)="retire(p)">Retire</button> }
            </div>
          </div>
          @if (p.currentVersion?.tiers?.length) {
            <table class="data" style="margin-top:8px;max-width:520px">
              <thead><tr><th>Band</th><th class="num">Unit price</th><th class="num">Flat fee</th></tr></thead>
              <tbody>
                @for (t of p.currentVersion!.tiers; track t.index; let i = $index) {
                  <tr><td>{{ i === 0 ? 0 : (p.currentVersion!.tiers[i - 1].upTo ?? 0) + 1 }} – {{ t.upTo ?? '∞' }}</td>
                      <td class="num">{{ minor(t.unitPriceMinor, p.currentVersion!.currency) }}</td>
                      <td class="num">{{ minor(t.flatFeeMinor, p.currentVersion!.currency) }}</td></tr>
                }
              </tbody>
            </table>
          }
          @if (p.versions.length > 1) {
            <details style="margin-top:8px"><summary class="slate">{{ p.versions.length }} versions</summary>
              <ul style="margin:6px 0 0;padding-left:18px">@for (v of p.versions; track v.id) { <li>v{{ v.version }} — {{ describe(v) }}</li> }</ul>
            </details>
          }
          @if (versioning() === p.id) {
            <form [formGroup]="versionForm" (ngSubmit)="publish(p)" style="margin-top:14px;border-top:1px solid var(--mist);padding-top:14px">
              <ng-container *ngTemplateOutlet="priceFields; context: { f: versionForm }" />
              @if (error()) { <div class="notice err">{{ error() }}</div> }
              <button class="btn" type="submit" [disabled]="versionForm.invalid">Publish version {{ (p.currentVersion?.version ?? 0) + 1 }}</button>
            </form>
          }
        </div>
      }
    </div>

    <ng-template #priceFields let-f="f">
      <div [formGroup]="f">
        <div class="grid cols-4">
          <div class="field"><label>Currency</label><select formControlName="currency"><option>USD</option><option>EUR</option><option>GBP</option><option>INR</option></select></div>
          <div class="field"><label>Interval</label><select formControlName="interval"><option value="MONTHLY">Monthly</option><option value="ANNUAL">Annual</option></select></div>
          <div class="field"><label>Pricing</label><select formControlName="pricingModel"><option value="FLAT">Flat</option><option value="PER_SEAT">Per seat</option><option value="TIERED">Tiered (metered)</option><option value="VOLUME">Volume (metered)</option></select></div>
          @if (!isMetered(f)) {
            <div class="field"><label>Price {{ isPerSeat(f) ? 'per seat' : '' }}</label><input formControlName="basePrice" placeholder="19.00"></div>
          }
        </div>
        @if (isMetered(f)) {
          <div class="field">
            <label>Bands <span class="hint">(leave "up to" blank on the last band for open-ended)</span></label>
            <div formArrayName="tiers">
              @for (t of f.controls.tiers.controls; track $index; let i = $index) {
                <div [formGroupName]="i" class="grid cols-4" style="gap:8px;margin-bottom:6px">
                  <input formControlName="upTo" placeholder="up to (units)">
                  <input formControlName="unitPrice" placeholder="unit price">
                  <input formControlName="flatFee" placeholder="flat fee">
                  <button type="button" class="btn ghost small" (click)="f.controls.tiers.removeAt(i)">remove</button>
                </div>
              }
            </div>
            <button type="button" class="btn ghost small" (click)="addTier(f)">Add band</button>
          </div>
        }
      </div>
    </ng-template>
  `,
})
export class PlansComponent {
  private api = inject(ApiService);
  plans = signal<Plan[]>([]);
  showCreate = signal(false);
  versioning = signal<string | null>(null);
  error = signal('');

  createForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    price: this.priceForm(),
  });
  versionForm = this.priceForm();

  constructor() {
    this.load();
  }

  private priceForm(): PriceForm {
    return new FormGroup({
      currency: new FormControl('USD', { nonNullable: true }),
      interval: new FormControl<'MONTHLY' | 'ANNUAL'>('MONTHLY', { nonNullable: true }),
      pricingModel: new FormControl<'FLAT' | 'PER_SEAT' | 'TIERED' | 'VOLUME'>('FLAT', { nonNullable: true }),
      basePrice: new FormControl('0.00', { nonNullable: true, validators: [Validators.pattern(/^\d+(\.\d{1,2})?$/)] }),
      tiers: new FormArray<FormGroup<{ upTo: FormControl<string>; unitPrice: FormControl<string>; flatFee: FormControl<string> }>>([]),
    });
  }

  isMetered(f: PriceForm): boolean {
    const m = f.controls.pricingModel.value;
    return m === 'TIERED' || m === 'VOLUME';
  }

  isPerSeat(f: PriceForm): boolean {
    return f.controls.pricingModel.value === 'PER_SEAT';
  }

  addTier(f: PriceForm): void {
    f.controls.tiers.push(new FormGroup({
      upTo: new FormControl('', { nonNullable: true }),
      unitPrice: new FormControl('0.00', { nonNullable: true }),
      flatFee: new FormControl('0.00', { nonNullable: true }),
    }));
  }

  load(): void {
    this.api.adminPlans().subscribe((p) => this.plans.set(p));
  }

  create(): void {
    const v = this.createForm.getRawValue();
    this.api.createPlan(v.code, v.name, this.toRequest(this.createForm.controls.price)).subscribe({
      next: () => { this.showCreate.set(false); this.createForm.reset(); this.error.set(''); this.load(); },
      error: (e) => this.error.set(e.error?.detail ?? 'Could not create the plan.'),
    });
  }

  publish(p: Plan): void {
    this.api.publishVersion(p.id, this.toRequest(this.versionForm)).subscribe({
      next: () => { this.versioning.set(null); this.error.set(''); this.load(); },
      error: (e) => this.error.set(e.error?.detail ?? 'Could not publish the version.'),
    });
  }

  retire(p: Plan): void {
    if (confirm(`Retire ${p.name}? New subscriptions will be refused; existing ones continue.`)) {
      this.api.retirePlan(p.id).subscribe(() => this.load());
    }
  }

  describe(v: PlanVersion): string {
    const per = v.interval === 'ANNUAL' ? 'year' : 'month';
    switch (v.pricingModel) {
      case 'FLAT': return `${v.currency} ${v.basePrice.amount} per ${per}`;
      case 'PER_SEAT': return `${v.currency} ${v.basePrice.amount} per seat per ${per}`;
      case 'TIERED': return `metered, tiered (${v.tiers.length} bands), billed ${per}ly in arrears`;
      case 'VOLUME': return `metered, volume (${v.tiers.length} bands), billed ${per}ly in arrears`;
    }
  }

  minor(minor: number, currency: string): string {
    return `${currency} ${(minor / 100).toFixed(2)}`;
  }

  private toRequest(f: PriceForm): PriceRequest {
    const v = f.getRawValue();
    const metered = v.pricingModel === 'TIERED' || v.pricingModel === 'VOLUME';
    return {
      currency: v.currency, interval: v.interval, pricingModel: v.pricingModel,
      basePriceMinor: metered ? 0 : toMinor(v.basePrice),
      tiers: metered ? v.tiers.map((t) => ({ upTo: t.upTo.trim() === '' ? null : Number(t.upTo), unitPriceMinor: toMinor(t.unitPrice), flatFeeMinor: toMinor(t.flatFee) })) : [],
    };
  }
}

/** "19.99" -> 1999, done on the string so no float ever holds a price. */
function toMinor(major: string): number {
  const [whole, frac = ''] = major.trim().split('.');
  return Number(whole || '0') * 100 + Number((frac + '00').slice(0, 2));
}

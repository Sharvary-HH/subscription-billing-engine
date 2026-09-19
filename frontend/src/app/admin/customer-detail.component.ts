import { Component, Input, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Customer, Invoice, PaymentMethod, Subscription } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { BadgeComponent } from '../shared/badge.component';

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MoneyPipe, BadgeComponent],
  template: `
    <div class="page">
      <a routerLink="/admin/customers" class="btn ghost small">← Customers</a>
      @if (customer(); as c) {
        <div class="page-head" style="margin-top:14px">
          <div><h1>{{ c.name }}</h1><p class="slate">{{ c.email }} · {{ c.currency }} · tax region {{ c.taxRegion }} · credit balance {{ c.creditBalance | money }}</p></div>
        </div>
        <div class="grid cols-2">
          <div class="card">
            <h3>Subscriptions</h3>
            <table class="data" style="margin-top:8px"><tbody>
              @for (s of subs(); track s.id) {
                <tr class="click" [routerLink]="['/admin/subscriptions', s.id]"><td>{{ s.planVersion.planName }}</td><td><app-badge [value]="s.status" /></td><td class="num">{{ s.periodPrice | money:false }}</td></tr>
              } @empty { <tr><td class="empty">None.</td></tr> }
            </tbody></table>
          </div>
          <div class="card">
            <h3>Cards</h3>
            <table class="data" style="margin:8px 0 12px"><tbody>
              @for (m of methods(); track m.id) {
                <tr><td>{{ m.brand }} •••• {{ m.last4 }} <span class="muted">{{ m.token }}</span></td><td>@if (m.isDefault) { <span class="badge ACTIVE">default</span> }</td></tr>
              } @empty { <tr><td class="empty">No card.</td></tr> }
            </tbody></table>
            <form [formGroup]="card" (ngSubmit)="addCard(c)" class="grid cols-4" style="gap:8px;align-items:end">
              <div class="field" style="margin:0"><label>Token</label><input formControlName="token"></div>
              <div class="field" style="margin:0"><label>Brand</label><input formControlName="brand"></div>
              <div class="field" style="margin:0"><label>Last 4</label><input formControlName="last4" maxlength="4"></div>
              <button class="btn small" type="submit" [disabled]="card.invalid">Add as default</button>
            </form>
            <div class="hint muted" style="font-size:12px;margin-top:6px">Adding a card retries any payment in dunning immediately.</div>
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
export class CustomerDetailComponent {
  private api = inject(ApiService);
  customer = signal<Customer | null>(null);
  subs = signal<Subscription[]>([]);
  methods = signal<PaymentMethod[]>([]);
  invoices = signal<Invoice[]>([]);
  private customerId = '';
  card = new FormGroup({
    token: new FormControl('tok_ok', { nonNullable: true, validators: [Validators.required] }),
    brand: new FormControl('visa', { nonNullable: true, validators: [Validators.required] }),
    last4: new FormControl('4242', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{4}$/)] }),
  });

  @Input() set id(v: string) {
    this.customerId = v;
    this.load();
  }

  load(): void {
    this.api.customer(this.customerId).subscribe((c) => this.customer.set(c));
    this.api.customerPaymentMethods(this.customerId).subscribe((m) => this.methods.set(m));
    this.api.invoices('', this.customerId).subscribe((i) => this.invoices.set(i));
    this.api.subscriptions('', '').subscribe((all) => this.subs.set(all.filter((s) => s.customerId === this.customerId)));
  }

  addCard(c: Customer): void {
    const v = this.card.getRawValue();
    this.api.addCustomerPaymentMethod(c.id, v.token, v.brand, v.last4).subscribe(() => this.load());
  }
}

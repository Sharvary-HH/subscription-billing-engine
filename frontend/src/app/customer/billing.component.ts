import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { Notification, PaymentMethod } from '../core/models';

/** Cards and the notification log. Adding a card makes it the default and, if a payment is in retry, retries it now. */
@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <div class="page">
      <div class="page-head"><h1>Billing</h1></div>
      <div class="grid cols-2">
        <div class="card">
          <h2>Payment methods</h2>
          <table class="data" style="margin:10px 0 18px">
            <tbody>
              @for (m of methods(); track m.id) {
                <tr><td>{{ m.brand }} •••• {{ m.last4 }}</td><td class="slate">added {{ m.createdAt | date:'d MMM y' }}</td><td>@if (m.isDefault) { <span class="badge ACTIVE">default</span> }</td></tr>
              } @empty { <tr><td class="empty">No card on file.</td></tr> }
            </tbody>
          </table>
          <h3>Add a card</h3>
          <p class="muted" style="font-size:13px;margin:4px 0 12px">This is a mock provider. Tokens: <code>tok_ok</code> succeeds, <code>tok_decline</code> declines, <code>tok_timeout</code> times out.</p>
          <form [formGroup]="form" (ngSubmit)="add()">
            <div class="field"><label for="token">Provider token</label><input id="token" formControlName="token"></div>
            <div class="grid cols-2">
              <div class="field"><label for="brand">Brand</label><input id="brand" formControlName="brand"></div>
              <div class="field"><label for="last4">Last 4</label><input id="last4" formControlName="last4" maxlength="4"></div>
            </div>
            @if (message()) { <div class="notice">{{ message() }}</div> }
            <button class="btn" type="submit" [disabled]="form.invalid">Add and make default</button>
          </form>
        </div>
        <div class="card">
          <h2>Notifications</h2>
          <p class="muted" style="font-size:13px;margin:4px 0 12px">What we would have emailed you. Nothing is actually sent.</p>
          @for (n of notifications(); track n.id) {
            <div class="note">
              <div class="slate small">{{ n.createdAt | date:'d MMM y, HH:mm' }} · {{ n.kind.replaceAll('_', ' ') }}</div>
              <div><b>{{ n.subject }}</b></div>
              <div class="small">{{ n.body }}</div>
            </div>
          } @empty { <div class="empty">Nothing yet.</div> }
        </div>
      </div>
    </div>
  `,
  styles: [`.note { padding: 10px 0; border-bottom: 1px solid var(--mist); } .small { font-size: 13px; }`],
})
export class BillingComponent {
  private api = inject(ApiService);
  methods = signal<PaymentMethod[]>([]);
  notifications = signal<Notification[]>([]);
  message = signal('');
  form = new FormGroup({
    token: new FormControl('tok_ok', { nonNullable: true, validators: [Validators.required] }),
    brand: new FormControl('visa', { nonNullable: true, validators: [Validators.required] }),
    last4: new FormControl('4242', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{4}$/)] }),
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.api.myPaymentMethods().subscribe((m) => this.methods.set(m));
    this.api.myNotifications().subscribe((n) => this.notifications.set(n));
  }

  add(): void {
    const v = this.form.getRawValue();
    this.api.myAddPaymentMethod(v.token, v.brand, v.last4).subscribe(() => {
      this.message.set('Card added. Any payment in retry has been retried with it.');
      this.load();
    });
  }
}

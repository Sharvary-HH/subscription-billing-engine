import { Component, Input, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { CreditNote, Invoice, PaymentAttempt } from '../core/models';
import { InvoiceViewComponent } from '../shared/invoice-view.component';

@Component({
  selector: 'app-admin-invoice',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, InvoiceViewComponent],
  template: `
    <div class="page">
      <div class="toolbar no-print">
        @if (invoice(); as inv) {
          <a [routerLink]="inv.subscriptionId ? ['/admin/subscriptions', inv.subscriptionId] : ['/admin/customers', inv.customerId]" class="btn ghost small">← Back</a>
          <button class="btn ghost small" (click)="print()">Print</button>
          @if (inv.status === 'OPEN') {
            <button class="btn accent small" (click)="pay()">Collect now</button>
            <button class="btn danger small" (click)="voidInvoice()">Void</button>
          }
          @if (inv.status === 'PAID') { <button class="btn small" (click)="showCredit.set(!showCredit())">Credit note</button> }
          @if (message()) { <span class="slate">{{ message() }}</span> }
        }
      </div>
      @if (showCredit()) {
        <form class="card no-print" [formGroup]="credit" (ngSubmit)="issueCredit()" style="margin-bottom:16px">
          <h3>Issue a credit note</h3>
          <p class="muted" style="font-size:13px;margin:4px 0 10px">The invoice is not edited. The amount is refunded through the provider; when credits reach the invoice total it becomes refunded.</p>
          <div class="grid cols-3">
            <div class="field"><label>Reason</label><input formControlName="reason"></div>
            <div class="field"><label>Line description</label><input formControlName="description"></div>
            <div class="field"><label>Amount ({{ invoice()?.currency }})</label><input formControlName="amount" placeholder="10.00"></div>
          </div>
          <button class="btn" type="submit" [disabled]="credit.invalid">Issue</button>
        </form>
      }
      @if (invoice(); as inv) {
        <div class="card"><app-invoice-view [invoice]="inv" [attempts]="attempts()" [creditNotes]="creditNotes()" /></div>
      }
    </div>
  `,
})
export class AdminInvoiceComponent {
  private api = inject(ApiService);
  invoice = signal<Invoice | null>(null);
  attempts = signal<PaymentAttempt[]>([]);
  creditNotes = signal<CreditNote[]>([]);
  showCredit = signal(false);
  message = signal('');
  private invoiceId = '';
  credit = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('Goodwill credit', { nonNullable: true, validators: [Validators.required] }),
    amount: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d+(\.\d{1,2})?$/)] }),
  });

  @Input() set id(v: string) {
    this.invoiceId = v;
    this.load();
  }

  load(): void {
    this.api.invoice(this.invoiceId).subscribe((i) => this.invoice.set(i));
    this.api.attempts(this.invoiceId).subscribe((a) => this.attempts.set(a));
    this.api.creditNotes(this.invoiceId).subscribe((n) => this.creditNotes.set(n));
  }

  pay(): void {
    this.api.pay(this.invoiceId).subscribe((r) => {
      this.message.set(r.attempt ? `Attempt ${r.attempt.attemptNumber}: ${r.attempt.status.toLowerCase().replace('_', ' ')}${r.attempt.failureReason ? ' (' + r.attempt.failureReason + ')' : ''}` : 'Nothing to collect.');
      this.load();
    });
  }

  voidInvoice(): void {
    if (confirm('Void this invoice? This cannot be undone.')) {
      this.api.voidInvoice(this.invoiceId).subscribe({ next: () => this.load(), error: (e) => this.message.set(e.error?.detail ?? 'Could not void.') });
    }
  }

  issueCredit(): void {
    const v = this.credit.getRawValue();
    const [whole, frac = ''] = v.amount.split('.');
    const minor = Number(whole) * 100 + Number((frac + '00').slice(0, 2));
    this.api.issueCreditNote(this.invoiceId, v.reason, [{ description: v.description, amountMinor: minor }]).subscribe({
      next: () => { this.showCredit.set(false); this.message.set('Credit note issued.'); this.load(); },
      error: (e) => this.message.set(e.error?.detail ?? 'Could not issue the credit note.'),
    });
  }

  print(): void {
    window.print();
  }
}

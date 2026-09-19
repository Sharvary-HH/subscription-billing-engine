import { Component, Input, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { CreditNote, Invoice, PaymentAttempt } from '../core/models';
import { InvoiceViewComponent } from '../shared/invoice-view.component';

@Component({
  selector: 'app-customer-invoice',
  standalone: true,
  imports: [RouterLink, InvoiceViewComponent],
  template: `
    <div class="page">
      <div class="toolbar no-print">
        <a routerLink="/account" class="btn ghost small">← Back</a>
        <button class="btn ghost small" (click)="print()">Print</button>
        @if (invoice()?.status === 'OPEN') { <button class="btn accent small" (click)="pay()" [disabled]="busy()">Pay now</button> }
        @if (message()) { <span class="slate">{{ message() }}</span> }
      </div>
      @if (invoice(); as inv) {
        <div class="card"><app-invoice-view [invoice]="inv" [attempts]="attempts()" [creditNotes]="creditNotes()" /></div>
      }
    </div>
  `,
})
export class CustomerInvoiceComponent {
  private api = inject(ApiService);
  invoice = signal<Invoice | null>(null);
  attempts = signal<PaymentAttempt[]>([]);
  creditNotes = signal<CreditNote[]>([]);
  busy = signal(false);
  message = signal('');
  private invoiceId = '';

  @Input() set id(value: string) {
    this.invoiceId = value;
    this.load();
  }

  load(): void {
    this.api.myInvoice(this.invoiceId).subscribe((i) => this.invoice.set(i));
    this.api.myAttempts(this.invoiceId).subscribe((a) => this.attempts.set(a));
    this.api.myCreditNotes(this.invoiceId).subscribe((n) => this.creditNotes.set(n));
  }

  pay(): void {
    this.busy.set(true);
    this.api.myPay(this.invoiceId).subscribe({
      next: (r) => {
        this.message.set(r.attempt?.status === 'SUCCEEDED' ? 'Payment received.' : `Payment ${r.attempt?.status.toLowerCase().replace('_', ' ')}: ${r.attempt?.failureReason ?? ''}`);
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  print(): void {
    window.print();
  }
}

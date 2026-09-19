import { Component, Input } from '@angular/core';
import { DatePipe, LowerCasePipe } from '@angular/common';
import { Invoice, PaymentAttempt, CreditNote } from '../core/models';
import { MoneyPipe } from './money.pipe';
import { BadgeComponent } from './badge.component';

/**
 * The invoice document: lines with their periods, proration lines marked, running totals, and
 * a print layout. Used by both the customer and admin sides.
 */
@Component({
  selector: 'app-invoice-view',
  standalone: true,
  imports: [DatePipe, LowerCasePipe, MoneyPipe, BadgeComponent],
  template: `
    <div class="invoice">
      <header>
        <div>
          <div class="muted" style="font-size:12px;text-transform:uppercase;letter-spacing:.05em">Invoice</div>
          <h1>{{ invoice.invoiceNumber }}</h1>
          <div class="slate">{{ invoice.customerName }}</div>
        </div>
        <div class="right">
          <app-badge [value]="invoice.status" />
          <dl class="kv" style="margin-top:8px">
            <dt>Kind</dt><dd>{{ invoice.kind | lowercase }}</dd>
            <dt>Period</dt><dd>{{ invoice.periodStart | date:'d MMM y' }} to {{ invoice.periodEnd | date:'d MMM y' }}</dd>
            @if (invoice.issuedAt) { <dt>Issued</dt><dd>{{ invoice.issuedAt | date:'d MMM y' }}</dd> }
            @if (invoice.dueAt) { <dt>Due</dt><dd>{{ invoice.dueAt | date:'d MMM y' }}</dd> }
            @if (invoice.paidAt) { <dt>Paid</dt><dd>{{ invoice.paidAt | date:'d MMM y, HH:mm' }}</dd> }
          </dl>
        </div>
      </header>

      <table class="data lines">
        <thead>
          <tr><th>Description</th><th>Period</th><th class="num">Qty</th><th class="num">Unit</th><th class="num">Amount</th><th class="num">Tax</th><th class="num">Running</th></tr>
        </thead>
        <tbody>
          @for (l of invoice.lines; track l.index) {
            <tr [class.proration]="l.proration" [class.credit]="l.amount.minor < 0">
              <td>
                {{ l.description }}
                @if (l.proration) { <span class="tag">prorated</span> }
                @if (l.type === 'CREDIT_BALANCE') { <span class="tag">credit</span> }
              </td>
              <td class="slate small">{{ l.periodStart | date:'d MMM' }} – {{ l.periodEnd | date:'d MMM y' }}</td>
              <td class="num">{{ l.quantity }}</td>
              <td class="num">{{ l.unitPrice | money:false }}</td>
              <td class="num">{{ l.amount | money:false }}</td>
              <td class="num">{{ l.tax | money:false }}</td>
              <td class="num slate">{{ running[l.index] | money:false }}</td>
            </tr>
          }
        </tbody>
        <tfoot>
          <tr><td colspan="4"></td><td class="num label">Subtotal</td><td colspan="2" class="num">{{ invoice.subtotal | money }}</td></tr>
          <tr><td colspan="4"></td><td class="num label">Tax</td><td colspan="2" class="num">{{ invoice.tax | money }}</td></tr>
          <tr class="total"><td colspan="4"></td><td class="num label">Total</td><td colspan="2" class="num">{{ invoice.total | money }}</td></tr>
          @if (invoice.amountPaid.minor > 0 && invoice.amountPaid.minor !== invoice.total.minor) {
            <tr><td colspan="4"></td><td class="num label">Paid</td><td colspan="2" class="num">{{ invoice.amountPaid | money }}</td></tr>
          }
          @if (invoice.status === 'OPEN') {
            <tr><td colspan="4"></td><td class="num label">Amount due</td><td colspan="2" class="num">{{ invoice.amountDue | money }}</td></tr>
          }
        </tfoot>
      </table>

      @if (attempts?.length) {
        <h3 style="margin-top:24px">Payment attempts</h3>
        <table class="data">
          <thead><tr><th>#</th><th>When</th><th>Trigger</th><th>Status</th><th>Detail</th><th class="num">Amount</th></tr></thead>
          <tbody>
            @for (a of attempts; track a.id) {
              <tr>
                <td>{{ a.attemptNumber }}</td>
                <td>{{ a.createdAt | date:'d MMM y, HH:mm' }}</td>
                <td>{{ a.triggeredBy.replace('_', ' ') }}</td>
                <td><app-badge [value]="a.status" /></td>
                <td class="slate">{{ a.providerRef || a.failureReason }}</td>
                <td class="num">{{ a.amount | money:false }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      @if (creditNotes?.length) {
        <h3 style="margin-top:24px">Credit notes</h3>
        <table class="data">
          <thead><tr><th>Number</th><th>Reason</th><th>Lines</th><th class="num">Total</th></tr></thead>
          <tbody>
            @for (n of creditNotes; track n.id) {
              <tr>
                <td>{{ n.creditNoteNumber }}</td><td>{{ n.reason }}</td>
                <td>@for (l of n.lines; track l.index) { <div>{{ l.description }} <span class="slate">({{ l.amount | money:false }})</span></div> }</td>
                <td class="num">{{ n.total | money }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [`
    header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 20px; flex-wrap: wrap; }
    .right { text-align: left; }
    .small { font-size: 13px; }
    .tag { display: inline-block; margin-left: 6px; padding: 0 6px; font-size: 11px; font-weight: 600; background: var(--acid); border-radius: 3px; }
    tr.credit td { color: var(--slate); }
    tr.proration td:first-child { border-left: 3px solid var(--acid); }
    tfoot td { border-bottom: none; padding-top: 6px; }
    tfoot .label { color: var(--slate); font-weight: 600; }
    tfoot tr.total td { font-size: 17px; font-weight: 700; border-top: 2px solid var(--ink); }
  `],
})
export class InvoiceViewComponent {
  @Input({ required: true }) invoice!: Invoice;
  @Input() attempts: PaymentAttempt[] | null = null;
  @Input() creditNotes: CreditNote[] | null = null;

  get running(): { amount: string; minor: number; currency: string }[] {
    let sum = 0;
    return this.invoice.lines.map((l) => {
      sum += l.amount.minor;
      const digits = l.amount.amount.includes('.') ? l.amount.amount.split('.')[1].length : 0;
      const abs = Math.abs(sum);
      const str = digits ? (abs / 10 ** digits).toFixed(digits) : String(abs);
      return { amount: (sum < 0 ? '-' : '') + str, minor: sum, currency: l.amount.currency };
    });
  }
}

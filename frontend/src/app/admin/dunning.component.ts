import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { DemoClockService } from '../core/demo-clock.service';
import { DunningCase, Notification } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';
import { BadgeComponent } from '../shared/badge.component';

/** Invoices in retry, each with its attempt history, plus the notification log underneath. */
@Component({
  selector: 'app-dunning',
  standalone: true,
  imports: [DatePipe, RouterLink, MoneyPipe, BadgeComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Dunning queue</h1><p class="slate">Retries on day 1, 3, 5 and 7 after the first failure. A card update retries at once and restarts the week.</p></div>
        <div class="toolbar" style="margin:0">
          <label style="font-size:14px"><input type="checkbox" [checked]="includeResolved()" (change)="toggleResolved()"> show resolved</label>
          <button class="btn small" (click)="runNow()">Run dunning job now</button>
        </div>
      </div>
      @if (message()) { <div class="notice">{{ message() }}</div> }

      @for (c of cases(); track c.id) {
        <div class="card case" [class.open]="c.state === 'RETRYING'">
          <div class="head">
            <div>
              <h3><a [routerLink]="['/admin/invoices', c.invoice.id]">{{ c.invoice.invoiceNumber }}</a> · {{ c.invoice.customerName }} · {{ c.invoice.amountDue | money }}</h3>
              <div class="slate" style="font-size:13px">
                <app-badge [value]="c.state" /> · started {{ c.startedAt | date:'d MMM y' }} · retry {{ c.retriesDone }} of {{ c.maxRetries }} done
                @if (c.nextRetryAt) { · next retry <b>{{ c.nextRetryAt | date:'d MMM y, HH:mm' }}</b> }
                @if (c.resolvedAt) { · resolved {{ c.resolvedAt | date:'d MMM y' }} }
              </div>
            </div>
            <a class="btn ghost small" [routerLink]="['/admin/subscriptions', c.subscriptionId]">Subscription</a>
          </div>
          <div class="steps">
            @for (n of [1, 2, 3, 4]; track n) {
              <div class="step" [class.done]="c.retriesDone >= n" [class.next]="c.state === 'RETRYING' && c.retriesDone === n - 1">retry {{ n }}</div>
            }
          </div>
          <table class="data">
            <thead><tr><th>#</th><th>When</th><th>Trigger</th><th>Result</th><th>Detail</th></tr></thead>
            <tbody>
              @for (a of c.attempts; track a.id) {
                <tr><td>{{ a.attemptNumber }}</td><td>{{ a.createdAt | date:'d MMM y, HH:mm' }}</td><td>{{ a.triggeredBy.replaceAll('_', ' ') }}</td><td><app-badge [value]="a.status" /></td><td class="slate">{{ a.providerRef || a.failureReason }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      } @empty { <div class="card empty">Nothing in retry. Use “Force payment failures” on the dashboard and advance the clock to see one.</div> }

      <h2 style="margin:28px 0 10px">Notification log</h2>
      <p class="muted" style="font-size:13px;margin-bottom:10px">Recorded, not sent.</p>
      <div class="card" style="padding:0">
        <table class="data">
          <thead><tr><th>When</th><th>Kind</th><th>Subject</th><th>Body</th></tr></thead>
          <tbody>
            @for (n of notifications(); track n.id) {
              <tr><td style="white-space:nowrap">{{ n.createdAt | date:'d MMM y, HH:mm' }}</td><td>{{ n.kind.replaceAll('_', ' ') }}</td><td><b>{{ n.subject }}</b></td><td class="slate">{{ n.body }}</td></tr>
            } @empty { <tr><td colspan="4" class="empty">No notifications yet.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .case { margin-bottom: 14px; }
    .case.open { border-left: 6px solid var(--acid); }
    .head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
    .steps { display: flex; gap: 6px; margin: 8px 0 12px; }
    .step { flex: 1; text-align: center; font-size: 12px; font-weight: 600; padding: 4px; border: 1px solid var(--mist); border-radius: 3px; color: var(--grey); }
    .step.done { background: var(--ink); color: var(--paper); border-color: var(--ink); }
    .step.next { border-color: var(--acid); background: var(--acid); color: var(--ink); }
  `],
})
export class DunningComponent {
  private api = inject(ApiService);
  private demo = inject(DemoClockService);
  cases = signal<DunningCase[]>([]);
  notifications = signal<Notification[]>([]);
  includeResolved = signal(false);
  message = signal('');

  constructor() {
    this.load();
  }

  load(): void {
    this.api.dunning(this.includeResolved()).subscribe((c) => this.cases.set(c));
    this.api.notifications().subscribe((n) => this.notifications.set(n));
  }

  toggleResolved(): void {
    this.includeResolved.update((v) => !v);
    this.load();
  }

  runNow(): void {
    this.api.runDunning().subscribe((r) => {
      this.message.set(`${r.fired} retr${r.fired === 1 ? 'y' : 'ies'} fired.`);
      this.demo.refresh();
      this.load();
    });
  }
}

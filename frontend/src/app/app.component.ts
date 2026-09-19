import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DatePipe } from '@angular/common';
import { AuthService } from './core/auth.service';
import { DemoClockService } from './core/demo-clock.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DatePipe],
  template: `
    @if (auth.session(); as s) {
      <div class="shell">
        <aside class="side no-print">
          <div class="brand"><span class="dot"></span>Billing</div>
          <nav>
            @if (s.role === 'ADMIN') {
              <a routerLink="/admin" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">Dashboard</a>
              <a routerLink="/admin/subscriptions" routerLinkActive="on">Subscriptions</a>
              <a routerLink="/admin/customers" routerLinkActive="on">Customers</a>
              <a routerLink="/admin/plans" routerLinkActive="on">Plans</a>
              <a routerLink="/admin/dunning" routerLinkActive="on">Dunning queue</a>
            } @else {
              <a routerLink="/account" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">Overview</a>
              <a routerLink="/account/billing" routerLinkActive="on">Billing</a>
            }
          </nav>
          <div class="foot">
            @if (demo.info(); as d) {
              <div class="clock">
                <div class="lbl">Demo clock</div>
                <div class="val">{{ d.now | date:'d MMM y' }}</div>
              </div>
            }
            <div class="who">{{ s.email }}</div>
            <button class="btn ghost small" (click)="logout()">Sign out</button>
          </div>
        </aside>
        <main><router-outlet /></main>
      </div>
    } @else {
      <router-outlet />
    }
  `,
  styles: [`
    .shell { display: flex; min-height: 100vh; }
    .side { width: 220px; flex: none; background: var(--ink); color: var(--paper); display: flex; flex-direction: column; padding: 22px 16px; position: sticky; top: 0; height: 100vh; }
    .brand { font-weight: 700; font-size: 18px; letter-spacing: .02em; display: flex; align-items: center; gap: 8px; margin: 0 8px 26px; }
    .dot { width: 12px; height: 12px; background: var(--acid); border-radius: 2px; display: inline-block; }
    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a { text-decoration: none; padding: 9px 10px; border-radius: var(--radius); color: var(--mist); border-left: 3px solid transparent; }
    nav a:hover { background: var(--slate); color: var(--paper); }
    nav a.on { color: var(--paper); border-left-color: var(--acid); background: #222; }
    .foot { margin-top: auto; display: flex; flex-direction: column; gap: 8px; }
    .clock { border: 1px solid var(--slate); border-radius: var(--radius); padding: 8px 10px; }
    .clock .lbl { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; color: var(--grey); }
    .clock .val { font-weight: 600; color: var(--acid); }
    .who { font-size: 12px; color: var(--grey); word-break: break-all; }
    .side .btn.ghost { color: var(--paper); border-color: var(--slate); }
    main { flex: 1; min-width: 0; }
    @media (max-width: 760px) { .shell { flex-direction: column; } .side { width: auto; height: auto; position: static; } }
  `],
})
export class AppComponent {
  auth = inject(AuthService);
  demo = inject(DemoClockService);
  private router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}

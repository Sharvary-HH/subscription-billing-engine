import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { DemoClockService } from '../core/demo-clock.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="wrap">
      <div class="panel">
        <div class="brand"><span class="dot"></span>Billing console</div>
        <h1>Sign in</h1>
        <p class="slate" style="margin:6px 0 22px">Subscription billing, invoicing and dunning.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="field">
            <label for="email">Email</label>
            <input id="email" type="email" formControlName="email" autocomplete="username">
          </div>
          <div class="field">
            <label for="password">Password</label>
            <input id="password" type="password" formControlName="password" autocomplete="current-password">
          </div>
          @if (error()) { <div class="notice err">{{ error() }}</div> }
          <button class="btn accent" type="submit" [disabled]="form.invalid || busy()">{{ busy() ? 'Signing in…' : 'Sign in' }}</button>
        </form>
        @if (demo.info(); as d) {
          <div class="demo">
            <div class="lbl">Demo accounts</div>
            <button type="button" class="acct" (click)="fill(d.adminEmail, d.password)"><b>Admin</b><span>{{ d.adminEmail }}</span></button>
            <button type="button" class="acct" (click)="fill(d.customerEmail, d.password)"><b>Customer</b><span>{{ d.customerEmail }}</span></button>
            <div class="muted" style="font-size:12px">Password for both: <code>{{ d.password }}</code></div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100vh; display: grid; place-items: center; background: var(--ink); padding: 24px; }
    .panel { width: 100%; max-width: 400px; background: var(--paper); border-radius: 6px; padding: 32px; border-top: 8px solid var(--acid); }
    .brand { font-weight: 700; display: flex; align-items: center; gap: 8px; color: var(--slate); font-size: 13px; text-transform: uppercase; letter-spacing: .08em; margin-bottom: 14px; }
    .dot { width: 10px; height: 10px; background: var(--acid); display: inline-block; border-radius: 2px; }
    .btn { width: 100%; }
    .demo { margin-top: 24px; border-top: 1px solid var(--mist); padding-top: 16px; display: flex; flex-direction: column; gap: 6px; }
    .lbl { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: .05em; color: var(--slate); }
    .acct { font: inherit; text-align: left; display: flex; justify-content: space-between; gap: 8px; padding: 8px 10px; border: 1px solid var(--mist); background: var(--paper); border-radius: var(--radius); cursor: pointer; }
    .acct:hover { border-color: var(--ink); }
    .acct span { color: var(--grey); font-size: 13px; }
  `],
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  demo = inject(DemoClockService);
  busy = signal(false);
  error = signal('');

  form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  fill(email: string, password: string): void {
    this.form.setValue({ email, password });
  }

  async submit(): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    try {
      const s = await this.auth.login(this.form.controls.email.value, this.form.controls.password.value);
      this.router.navigate([s.role === 'ADMIN' ? '/admin' : '/account']);
    } catch {
      this.error.set('That email and password did not match.');
    } finally {
      this.busy.set(false);
    }
  }
}

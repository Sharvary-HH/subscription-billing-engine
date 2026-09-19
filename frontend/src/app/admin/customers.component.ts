import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { ApiService } from '../core/api.service';
import { Customer } from '../core/models';
import { MoneyPipe } from '../shared/money.pipe';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MoneyPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <h1>Customers</h1>
        <button class="btn accent" (click)="showNew.set(!showNew())">{{ showNew() ? 'Close' : 'New customer' }}</button>
      </div>
      @if (showNew()) {
        <form class="card" [formGroup]="form" (ngSubmit)="create()" style="margin-bottom:16px">
          <div class="grid cols-4">
            <div class="field"><label>Name</label><input formControlName="name"></div>
            <div class="field"><label>Email</label><input formControlName="email" type="email"></div>
            <div class="field"><label>Currency</label><select formControlName="currency"><option>USD</option><option>EUR</option><option>GBP</option><option>INR</option></select></div>
            <div class="field"><label>Tax region</label><select formControlName="taxRegion"><option>US</option><option>GB</option><option>DE</option><option>FR</option><option>IN</option><option>CA</option><option>AU</option></select></div>
          </div>
          <div class="field" style="max-width:320px"><label>Login password</label><input formControlName="password" type="password"><div class="hint">Creates a customer login for the console.</div></div>
          @if (error()) { <div class="notice err">{{ error() }}</div> }
          <button class="btn" type="submit" [disabled]="form.invalid">Create</button>
        </form>
      }
      <div class="toolbar"><input [formControl]="q" placeholder="Search name or email" style="min-width:280px"></div>
      <div class="card" style="padding:0">
        <table class="data">
          <thead><tr><th>Name</th><th>Email</th><th>Currency</th><th>Tax region</th><th class="num">Credit balance</th><th>Since</th></tr></thead>
          <tbody>
            @for (c of rows(); track c.id) {
              <tr class="click" [routerLink]="['/admin/customers', c.id]">
                <td><b>{{ c.name }}</b></td><td>{{ c.email }}</td><td>{{ c.currency }}</td><td>{{ c.taxRegion }}</td>
                <td class="num">{{ c.creditBalance.minor ? (c.creditBalance | money:false) : '' }}</td><td>{{ c.createdAt | date:'d MMM y' }}</td>
              </tr>
            } @empty { <tr><td colspan="6" class="empty">No customers.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
})
export class CustomersComponent {
  private api = inject(ApiService);
  rows = signal<Customer[]>([]);
  showNew = signal(false);
  error = signal('');
  q = new FormControl('', { nonNullable: true });
  form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    currency: new FormControl('USD', { nonNullable: true }),
    taxRegion: new FormControl('US', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] }),
  });

  constructor() {
    this.q.valueChanges.pipe(debounceTime(200)).subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.api.customers(this.q.value).subscribe((c) => this.rows.set(c));
  }

  create(): void {
    this.api.createCustomer(this.form.getRawValue()).subscribe({
      next: () => { this.showNew.set(false); this.form.reset(); this.error.set(''); this.load(); },
      error: (e) => this.error.set(e.error?.detail ?? 'Could not create the customer.'),
    });
  }
}

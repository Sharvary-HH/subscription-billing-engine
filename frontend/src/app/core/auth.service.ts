import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Session } from './models';
import { environment } from '../../environments/environment';

const KEY = 'billing.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly session = signal<Session | null>(load());
  readonly isAdmin = computed(() => this.session()?.role === 'ADMIN');
  readonly isCustomer = computed(() => this.session()?.role === 'CUSTOMER');

  constructor(private http: HttpClient) {}

  async login(email: string, password: string): Promise<Session> {
    const s = await firstValueFrom(this.http.post<Session>(`${environment.apiUrl}/auth/login`, { email, password }));
    localStorage.setItem(KEY, JSON.stringify(s));
    this.session.set(s);
    return s;
  }

  logout(): void {
    localStorage.removeItem(KEY);
    this.session.set(null);
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }
}

function load(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

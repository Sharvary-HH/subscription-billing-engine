import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChangeResponse, CreditNote, Customer, Dashboard, DemoInfo, DunningCase, Estimate, Invoice, Notification, PaymentAttempt,
  PaymentMethod, Plan, PlanVersion, Subscription, SubscriptionStatus
} from './models';

export interface PriceRequest {
  currency: string; interval: 'MONTHLY' | 'ANNUAL'; pricingModel: 'FLAT' | 'PER_SEAT' | 'TIERED' | 'VOLUME';
  basePriceMinor: number; tiers: { upTo: number | null; unitPriceMinor: number; flatFeeMinor: number }[];
}

/** Thin, typed wrapper over the REST API. One method per endpoint; nothing clever. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // customer side
  me(): Observable<Customer> { return this.http.get<Customer>(`${this.base}/me`); }
  mySubscriptions(): Observable<Subscription[]> { return this.http.get<Subscription[]>(`${this.base}/me/subscriptions`); }
  mySubscription(id: string): Observable<Subscription> { return this.http.get<Subscription>(`${this.base}/me/subscriptions/${id}`); }
  myEstimate(id: string): Observable<Estimate> { return this.http.get<Estimate>(`${this.base}/me/subscriptions/${id}/estimate`); }
  myChange(id: string, planVersionId: string | null, quantity: number): Observable<ChangeResponse> {
    return this.http.post<ChangeResponse>(`${this.base}/me/subscriptions/${id}/change`, { planVersionId, quantity });
  }
  myCancel(id: string, immediately: boolean): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/me/subscriptions/${id}/cancel`, { immediately });
  }
  myUndoCancel(id: string): Observable<Subscription> { return this.http.post<Subscription>(`${this.base}/me/subscriptions/${id}/undo-cancel`, {}); }
  myInvoices(): Observable<Invoice[]> { return this.http.get<Invoice[]>(`${this.base}/me/invoices`); }
  myInvoice(id: string): Observable<Invoice> { return this.http.get<Invoice>(`${this.base}/me/invoices/${id}`); }
  myAttempts(id: string): Observable<PaymentAttempt[]> { return this.http.get<PaymentAttempt[]>(`${this.base}/me/invoices/${id}/attempts`); }
  myCreditNotes(id: string): Observable<CreditNote[]> { return this.http.get<CreditNote[]>(`${this.base}/me/invoices/${id}/credit-notes`); }
  myPay(id: string): Observable<{ invoice: Invoice; attempt: PaymentAttempt | null }> {
    return this.http.post<{ invoice: Invoice; attempt: PaymentAttempt | null }>(`${this.base}/me/invoices/${id}/pay`, {});
  }
  myPaymentMethods(): Observable<PaymentMethod[]> { return this.http.get<PaymentMethod[]>(`${this.base}/me/payment-methods`); }
  myAddPaymentMethod(token: string, brand: string, last4: string): Observable<PaymentMethod> {
    return this.http.post<PaymentMethod>(`${this.base}/me/payment-methods`, { token, brand, last4, makeDefault: true });
  }
  myNotifications(): Observable<Notification[]> { return this.http.get<Notification[]>(`${this.base}/me/notifications`); }
  plans(): Observable<Plan[]> { return this.http.get<Plan[]>(`${this.base}/plans`); }

  // admin side
  dashboard(currency = 'USD'): Observable<Dashboard> { return this.http.get<Dashboard>(`${this.base}/admin/reports/dashboard`, { params: { currency } }); }
  adminPlans(): Observable<Plan[]> { return this.http.get<Plan[]>(`${this.base}/admin/plans`); }
  createPlan(code: string, name: string, price: PriceRequest): Observable<Plan> { return this.http.post<Plan>(`${this.base}/admin/plans`, { code, name, price }); }
  publishVersion(planId: string, price: PriceRequest): Observable<PlanVersion> { return this.http.post<PlanVersion>(`${this.base}/admin/plans/${planId}/versions`, price); }
  retirePlan(planId: string): Observable<void> { return this.http.delete<void>(`${this.base}/admin/plans/${planId}`); }
  customers(q = ''): Observable<Customer[]> { return this.http.get<Customer[]>(`${this.base}/admin/customers`, { params: { q } }); }
  customer(id: string): Observable<Customer> { return this.http.get<Customer>(`${this.base}/admin/customers/${id}`); }
  createCustomer(body: { name: string; email: string; currency: string; taxRegion: string; password: string }): Observable<Customer> {
    return this.http.post<Customer>(`${this.base}/admin/customers`, body);
  }
  customerPaymentMethods(id: string): Observable<PaymentMethod[]> { return this.http.get<PaymentMethod[]>(`${this.base}/admin/customers/${id}/payment-methods`); }
  addCustomerPaymentMethod(id: string, token: string, brand: string, last4: string): Observable<PaymentMethod> {
    return this.http.post<PaymentMethod>(`${this.base}/admin/customers/${id}/payment-methods`, { token, brand, last4, makeDefault: true });
  }
  subscriptions(q = '', status: SubscriptionStatus | '' = ''): Observable<Subscription[]> {
    let params = new HttpParams().set('q', q);
    if (status) params = params.set('status', status);
    return this.http.get<Subscription[]>(`${this.base}/admin/subscriptions`, { params });
  }
  subscription(id: string): Observable<Subscription> { return this.http.get<Subscription>(`${this.base}/admin/subscriptions/${id}`); }
  subscribe(body: { customerId: string; planVersionId: string; quantity: number; trialDays: number; meteredAddOnVersionIds: string[] }, key: string): Observable<Subscription> {
    return this.http.post<Subscription>(`${this.base}/admin/subscriptions`, body, { headers: { 'Idempotency-Key': key } });
  }
  estimate(id: string): Observable<Estimate> { return this.http.get<Estimate>(`${this.base}/admin/subscriptions/${id}/estimate`); }
  change(id: string, planVersionId: string | null, quantity: number): Observable<ChangeResponse> {
    return this.http.post<ChangeResponse>(`${this.base}/admin/subscriptions/${id}/change`, { planVersionId, quantity });
  }
  cancel(id: string, immediately: boolean): Observable<Subscription> { return this.http.post<Subscription>(`${this.base}/admin/subscriptions/${id}/cancel`, { immediately }); }
  undoCancel(id: string): Observable<Subscription> { return this.http.post<Subscription>(`${this.base}/admin/subscriptions/${id}/undo-cancel`, {}); }
  pause(id: string): Observable<Subscription> { return this.http.post<Subscription>(`${this.base}/admin/subscriptions/${id}/pause`, {}); }
  resume(id: string): Observable<Subscription> { return this.http.post<Subscription>(`${this.base}/admin/subscriptions/${id}/resume`, {}); }
  invoices(status: string, customerId?: string): Observable<Invoice[]> {
    let params = new HttpParams().set('status', status);
    if (customerId) params = params.set('customerId', customerId);
    return this.http.get<Invoice[]>(`${this.base}/admin/invoices`, { params });
  }
  invoice(id: string): Observable<Invoice> { return this.http.get<Invoice>(`${this.base}/admin/invoices/${id}`); }
  attempts(id: string): Observable<PaymentAttempt[]> { return this.http.get<PaymentAttempt[]>(`${this.base}/admin/invoices/${id}/attempts`); }
  creditNotes(id: string): Observable<CreditNote[]> { return this.http.get<CreditNote[]>(`${this.base}/admin/invoices/${id}/credit-notes`); }
  pay(id: string): Observable<{ invoice: Invoice; attempt: PaymentAttempt | null }> {
    return this.http.post<{ invoice: Invoice; attempt: PaymentAttempt | null }>(`${this.base}/admin/invoices/${id}/pay`, {});
  }
  voidInvoice(id: string): Observable<Invoice> { return this.http.post<Invoice>(`${this.base}/admin/invoices/${id}/void`, {}); }
  issueCreditNote(id: string, reason: string, lines: { description: string; amountMinor: number }[]): Observable<CreditNote> {
    return this.http.post<CreditNote>(`${this.base}/admin/invoices/${id}/credit-notes`, { reason, lines });
  }
  dunning(includeResolved = false): Observable<DunningCase[]> { return this.http.get<DunningCase[]>(`${this.base}/admin/dunning`, { params: { includeResolved } }); }
  notifications(): Observable<Notification[]> { return this.http.get<Notification[]>(`${this.base}/admin/notifications`); }
  runBilling(): Observable<{ processed: number }> { return this.http.post<{ processed: number }>(`${this.base}/admin/jobs/billing/run`, {}); }
  runDunning(): Observable<{ fired: number }> { return this.http.post<{ fired: number }>(`${this.base}/admin/jobs/dunning/run`, {}); }

  // demo
  demoInfo(): Observable<DemoInfo> { return this.http.get<DemoInfo>(`${this.base}/demo/info`); }
  demoAdvance(days: number): Observable<{ now: string; subscriptionsBilled: number; retriesFired: number }> {
    return this.http.post<{ now: string; subscriptionsBilled: number; retriesFired: number }>(`${this.base}/demo/advance`, { days });
  }
  demoPaymentMode(mode: 'SUCCEED' | 'DECLINE' | 'TIMEOUT'): Observable<DemoInfo> { return this.http.post<DemoInfo>(`${this.base}/demo/payment-mode`, { mode }); }
  demoReset(): Observable<DemoInfo> { return this.http.post<DemoInfo>(`${this.base}/demo/reset`, {}); }
}

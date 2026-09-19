export interface Money { amount: string; minor: number; currency: string; }

export type Role = 'ADMIN' | 'CUSTOMER';
export interface Session { token: string | null; userId: string; email: string; role: Role; customerId: string | null; }

export interface Customer { id: string; name: string; email: string; currency: string; taxRegion: string; creditBalance: Money; createdAt: string; }
export interface PaymentMethod { id: string; brand: string; last4: string; isDefault: boolean; token: string; createdAt: string; }

export type BillingInterval = 'MONTHLY' | 'ANNUAL';
export type PricingModel = 'FLAT' | 'PER_SEAT' | 'TIERED' | 'VOLUME';
export interface Tier { index: number; upTo: number | null; unitPriceMinor: number; flatFeeMinor: number; }
export interface PlanVersion {
  id: string; planId: string; planCode: string; planName: string; version: number; currency: string;
  interval: BillingInterval; pricingModel: PricingModel; basePrice: Money; tiers: Tier[]; createdAt: string;
}
export interface Plan { id: string; code: string; name: string; active: boolean; currentVersion: PlanVersion | null; versions: PlanVersion[]; }

export type SubscriptionStatus = 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'PAUSED' | 'CANCELED';
export interface SubscriptionItem { id: string; planVersion: PlanVersion; quantity: number; }
export interface Subscription {
  id: string; customerId: string; customerName: string; customerEmail: string; planVersion: PlanVersion; quantity: number;
  status: SubscriptionStatus; anchorDay: number; currentPeriodStart: string; currentPeriodEnd: string; trialEnd: string | null;
  cancelAt: string | null; canceledAt: string | null; periodPrice: Money; items: SubscriptionItem[]; createdAt: string;
}

export type InvoiceStatus = 'DRAFT' | 'OPEN' | 'PAID' | 'VOID' | 'UNCOLLECTIBLE' | 'REFUNDED';
export type LineType = 'PLAN' | 'USAGE' | 'PRORATION_CREDIT' | 'PRORATION_CHARGE' | 'CREDIT_BALANCE';
export interface InvoiceLine {
  index: number; type: LineType; description: string; quantity: number; unitPrice: Money; amount: Money; tax: Money;
  periodStart: string; periodEnd: string; proration: boolean;
}
export interface Invoice {
  id: string; invoiceNumber: string; customerId: string; customerName: string; subscriptionId: string | null;
  status: InvoiceStatus; kind: 'RECURRING' | 'PRORATION' | 'FINAL'; currency: string; periodStart: string; periodEnd: string;
  subtotal: Money; tax: Money; total: Money; amountPaid: Money; amountDue: Money;
  issuedAt: string | null; dueAt: string | null; paidAt: string | null; lines: InvoiceLine[]; createdAt: string;
}
export interface EstimateLine { type: LineType; description: string; quantity: number; amount: Money; tax: Money; periodStart: string; periodEnd: string; proration: boolean; }
export interface Estimate { lines: EstimateLine[]; subtotal: Money; tax: Money; creditApplied: Money; total: Money; nextBillingDate: string; }

export interface PaymentAttempt { id: string; attemptNumber: number; status: 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT'; amount: Money; providerRef: string | null; failureReason: string | null; triggeredBy: string; createdAt: string; }
export interface DunningCase {
  id: string; state: 'RETRYING' | 'RECOVERED' | 'EXHAUSTED' | 'CANCELED'; retriesDone: number; maxRetries: number;
  nextRetryAt: string | null; startedAt: string; resolvedAt: string | null; invoice: Invoice; subscriptionId: string; attempts: PaymentAttempt[];
}
export interface CreditNote { id: string; creditNoteNumber: string; invoiceId: string; reason: string; total: Money; lines: { index: number; description: string; amount: Money }[]; createdAt: string; }
export interface Notification { id: string; customerId: string; kind: string; subject: string; body: string; createdAt: string; }
export interface ChangeResponse { subscription: Subscription; invoice: Invoice | null; credited: Money; charged: Money; }

export interface Dashboard {
  currency: string; mrr: Money; activeSubscriptions: number; trialing: number; pastDue: number; inDunning: number; churnRate: number;
  revenueByMonth: { month: string; amount: Money }[];
  revenueByPlan: { planCode: string; planName: string; revenue: Money; invoices: number }[];
  churnByMonth: { month: string; activeAtStart: number; canceled: number; rate: number }[];
  byStatus: Record<SubscriptionStatus, number>;
}
export interface DemoInfo { enabled: boolean; now: string; paymentMode: 'SUCCEED' | 'DECLINE' | 'TIMEOUT'; seededAt: string | null; adminEmail: string; customerEmail: string; password: string; }

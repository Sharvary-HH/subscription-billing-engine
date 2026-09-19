import { Routes } from '@angular/router';
import { adminGuard, customerGuard } from './core/guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent) },
  {
    path: 'account', canActivate: [customerGuard], children: [
      { path: '', loadComponent: () => import('./customer/overview.component').then((m) => m.OverviewComponent) },
      { path: 'invoices/:id', loadComponent: () => import('./customer/invoice.component').then((m) => m.CustomerInvoiceComponent) },
      { path: 'change-plan/:id', loadComponent: () => import('./customer/change-plan.component').then((m) => m.ChangePlanComponent) },
      { path: 'billing', loadComponent: () => import('./customer/billing.component').then((m) => m.BillingComponent) },
    ],
  },
  {
    path: 'admin', canActivate: [adminGuard], children: [
      { path: '', loadComponent: () => import('./admin/dashboard.component').then((m) => m.DashboardComponent) },
      { path: 'plans', loadComponent: () => import('./admin/plans.component').then((m) => m.PlansComponent) },
      { path: 'subscriptions', loadComponent: () => import('./admin/subscriptions.component').then((m) => m.SubscriptionsComponent) },
      { path: 'subscriptions/:id', loadComponent: () => import('./admin/subscription-detail.component').then((m) => m.SubscriptionDetailComponent) },
      { path: 'customers', loadComponent: () => import('./admin/customers.component').then((m) => m.CustomersComponent) },
      { path: 'customers/:id', loadComponent: () => import('./admin/customer-detail.component').then((m) => m.CustomerDetailComponent) },
      { path: 'invoices/:id', loadComponent: () => import('./admin/invoice.component').then((m) => m.AdminInvoiceComponent) },
      { path: 'dunning', loadComponent: () => import('./admin/dunning.component').then((m) => m.DunningComponent) },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];

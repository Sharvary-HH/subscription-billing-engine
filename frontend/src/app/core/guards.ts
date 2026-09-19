import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? true : inject(Router).createUrlTree(['/login']);
};

export const customerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isCustomer() ? true : inject(Router).createUrlTree(['/login']);
};

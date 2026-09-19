import { Pipe, PipeTransform } from '@angular/core';
import { Money } from '../core/models';

/** Formats the API's Money object. Uses the string decimal, never the number, so nothing is re-rounded here. */
@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: Money | null | undefined, showCode = true): string {
    if (!value) return '';
    const negative = value.amount.startsWith('-');
    const abs = negative ? value.amount.slice(1) : value.amount;
    const [whole, frac] = abs.split('.');
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    const body = frac !== undefined ? `${grouped}.${frac}` : grouped;
    return `${negative ? '−' : ''}${showCode ? value.currency + ' ' : ''}${body}`;
  }
}

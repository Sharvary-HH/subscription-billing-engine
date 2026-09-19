import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-badge',
  standalone: true,
  template: `<span class="badge {{ value }}">{{ label }}</span>`,
})
export class BadgeComponent {
  @Input({ required: true }) value = '';
  get label(): string {
    return this.value.replace('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  }
}

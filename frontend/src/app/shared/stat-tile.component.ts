import { Component, Input, booleanAttribute } from '@angular/core';

@Component({
  selector: 'app-stat',
  standalone: true,
  template: `
    <div class="tile" [class.accent]="accent">
      <div class="label">{{ label }}</div>
      <div class="value">{{ value }}</div>
      @if (hint) { <div class="hint">{{ hint }}</div> }
    </div>
  `,
  styles: [`
    .tile { border: 1px solid var(--mist); border-radius: var(--radius); padding: 14px 18px; background: var(--paper); }
    .tile.accent { border-left: 6px solid var(--acid); }
    .label { font-size: 12px; text-transform: uppercase; letter-spacing: .05em; color: var(--slate); font-weight: 600; }
    .value { font-size: 30px; font-weight: 600; line-height: 1.15; margin-top: 2px; font-variant-numeric: tabular-nums; }
    .hint { font-size: 13px; color: var(--grey); margin-top: 2px; }
  `],
})
export class StatTileComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) value: string | number | null = '';
  @Input() hint = '';
  @Input({ transform: booleanAttribute }) accent = false;
}

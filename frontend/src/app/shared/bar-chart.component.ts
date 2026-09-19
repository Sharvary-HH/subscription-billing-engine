import { Component, Input, computed, signal } from '@angular/core';

export interface BarDatum { label: string; value: number; display: string; }

/**
 * A single-series bar chart in plain SVG. One ink hue for the bars, the accent for the hovered
 * bar, labels in text colour. A table twin sits below for screen readers and print.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  template: `
    <div class="chart">
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" [attr.aria-label]="title">
        <line [attr.x1]="pad.l" [attr.x2]="width - pad.r" [attr.y1]="baseline" [attr.y2]="baseline" stroke="var(--mist)" stroke-width="1"/>
        @for (b of bars(); track b.label; let i = $index) {
          <g (mouseenter)="hover.set(i)" (mouseleave)="hover.set(null)">
            <rect [attr.x]="b.x - 6" [attr.y]="pad.t" [attr.width]="b.w + 12" [attr.height]="baseline - pad.t" fill="transparent"/>
            <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="baseline - b.y"
                  [attr.fill]="hover() === i ? 'var(--acid)' : 'var(--ink)'" rx="3"/>
            <text [attr.x]="b.x + b.w / 2" [attr.y]="height - 6" text-anchor="middle" font-size="11" fill="var(--slate)">{{ b.label }}</text>
            @if (hover() === i || i === bars().length - 1) {
              <text [attr.x]="b.x + b.w / 2" [attr.y]="b.y - 5" text-anchor="middle" font-size="11" font-weight="600" fill="var(--ink)">{{ b.display }}</text>
            }
          </g>
        }
      </svg>
      <table class="twin">
        <caption>{{ title }}</caption>
        <tbody>@for (d of data; track d.label) { <tr><th>{{ d.label }}</th><td>{{ d.display }}</td></tr> }</tbody>
      </table>
    </div>
  `,
  styles: [`
    .chart { width: 100%; }
    svg { width: 100%; height: auto; display: block; font-family: var(--font); }
    .twin { position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden; }
    @media print { .twin { position: static; width: auto; height: auto; margin-top: 8px; font-size: 12px; } }
  `],
})
export class BarChartComponent {
  @Input({ required: true }) data: BarDatum[] = [];
  @Input() title = '';
  readonly width = 600;
  readonly height = 190;
  readonly pad = { l: 8, r: 8, t: 22, b: 22 };
  readonly hover = signal<number | null>(null);
  get baseline(): number { return this.height - this.pad.b; }

  readonly bars = computed(() => {
    const n = this.data.length;
    if (!n) return [] as { label: string; display: string; x: number; y: number; w: number }[];
    const max = Math.max(...this.data.map((d) => d.value), 1);
    const inner = this.width - this.pad.l - this.pad.r;
    const slot = inner / n;
    const w = Math.min(slot * 0.62, 56);
    const plotH = this.baseline - this.pad.t;
    return this.data.map((d, i) => ({
      label: d.label, display: d.display,
      x: this.pad.l + slot * i + (slot - w) / 2,
      w, y: this.baseline - (d.value / max) * plotH,
    }));
  });
}

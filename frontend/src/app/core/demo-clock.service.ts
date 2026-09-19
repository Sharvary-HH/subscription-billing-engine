import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { DemoInfo } from './models';

/** Holds the demo clock so the sidebar and the dashboard controls stay in step. Silent if the demo profile is off. */
@Injectable({ providedIn: 'root' })
export class DemoClockService {
  private api = inject(ApiService);
  readonly info = signal<DemoInfo | null>(null);

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.api.demoInfo().subscribe({ next: (i) => this.info.set(i), error: () => this.info.set(null) });
  }
}

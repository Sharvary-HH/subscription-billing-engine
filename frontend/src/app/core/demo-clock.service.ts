import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { DemoInfo } from './models';

/**
 * Holds the demo clock so the sidebar and the dashboard controls stay in step. Silent if the demo
 * profile is off.
 *
 * <p>The first call can fail for a reason that is not "no demo profile": on a free hosting tier
 * the backend sleeps when idle and takes about a minute to wake, so the very first request of the
 * day times out. Rather than hide the demo controls for that visitor, keep retrying quietly in the
 * background and let the signal fill in when the service answers.
 */
@Injectable({ providedIn: 'root' })
export class DemoClockService {
  private static readonly WAKE_RETRIES = 12;
  private static readonly RETRY_DELAY_MS = 5000;

  private api = inject(ApiService);
  readonly info = signal<DemoInfo | null>(null);
  /** True while the first call has not succeeded yet, so pages can say "waking up" instead of nothing. */
  readonly waking = signal(true);

  constructor() {
    this.refresh(DemoClockService.WAKE_RETRIES);
  }

  refresh(retries = 0): void {
    this.api.demoInfo().subscribe({
      next: (i) => {
        this.info.set(i);
        this.waking.set(false);
      },
      error: () => {
        if (retries > 0) {
          setTimeout(() => this.refresh(retries - 1), DemoClockService.RETRY_DELAY_MS);
          return;
        }
        this.info.set(null);
        this.waking.set(false);
      },
    });
  }
}

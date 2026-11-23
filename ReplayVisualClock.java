package com.mirror.recorder.handler;

/** A render-only clock freezes ornaments on pause without touching the replay clock. */
final class ReplayVisualClock {
  private long elapsed, previous;
  private boolean initialized, advancing;

  long sample(long now, boolean running) {
    if (initialized && advancing && now >= previous) elapsed += now - previous;
    previous = now;
    initialized = true;
    advancing = running;
    return elapsed;
  }

  void reset() {
    elapsed = 0L;
    initialized = advancing = false;
  }
}

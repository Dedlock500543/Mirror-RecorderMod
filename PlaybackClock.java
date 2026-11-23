package com.mirror.recorder.manager;
/**
 * Playback's own timeline, fully independent of FPS and frame time.
 *
 * <p>A step is made by the game tick, not by rendering, so render FPS does not set playback frames
 * per tick; delayed game ticks still delay playback in real time. The remainder is stored as an
 * integer number of thousandths of a frame, so rounding error does not accumulate over 10 minutes
 * or an hour: from a zero remainder, N ticks advance floor(N * speedMilli / UNIT) frames at the
 * rounded, clamped speed. The class deliberately knows nothing about Minecraft or the config.
 */
public final class PlaybackClock {
  /** One thousandth of a frame is the accumulator unit. */
  public static final int UNIT = 1000, MIN_SPEED = 250, MAX_SPEED = 4000;

  private int remainder = 0;
  /** Speed in thousandths: rounding happens once here, integers only from then on. */
  public static int toMilli(double speed) {
    long v = Math.round(speed * UNIT);
    if (v < MIN_SPEED) v = MIN_SPEED;
    if (v > MAX_SPEED) v = MAX_SPEED;
    return (int) v;
  }

  public void reset() {
    remainder = 0;
  }

  public int getRemainder() {
    return remainder;
  }
  /** Remaining game ticks for this frame, including the current tick. */
  public int ticksUntilStep(int speedMilli) {
    int speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speedMilli));
    int remaining = Math.max(0, UNIT - remainder);
    return Math.max(1, (remaining + speed - 1) / speed);
  }

  /**
   * How many frames to advance this tick. The fractional part carries over exactly, without
   * floating-point arithmetic.
   */
  public int advance(int speedMilli) {
    if (speedMilli < MIN_SPEED) speedMilli = MIN_SPEED;
    if (speedMilli > MAX_SPEED) speedMilli = MAX_SPEED;
    remainder += speedMilli;
    int steps = remainder / UNIT;
    remainder -= steps * UNIT;
    return steps;
  }
  /**
   * Unused whole-frame steps return to the accumulator when advancement stops before using all
   * steps returned by advance; the fractional remainder is already retained by advance.
   */
  public void giveBack(int steps) {
    if (steps > 0) remainder += steps * UNIT;
  }
}

package com.mirror.recorder.handler;

/** Counts fresh physical events and cursor changes within one stable GUI context. */
final class MouseStopEvidence {
  private static final double DECAY = 0.75d;
  private static final int REGRAB_GRACE_TICKS = 6;
  private double pixels;
  private int grace;
  private Object cursorContext;
  private int x, y, width, height;
  private boolean cursorBase;

  void delta(int dx, int dy) {
    if (grace == 0) pixels += Math.abs((long) dx) + Math.abs((long) dy);
  }

  void cursor(Object context, int px, int py, int w, int h) {
    if (sameContext(context, w, h)) delta(px - x, py - y);
    else pixels = 0d;
    warp(context, px, py, w, h);
  }

  void warp(Object context, int px, int py, int w, int h) {
    if (!sameContext(context, w, h)) pixels = 0d;
    cursorContext = context;
    x = px;
    y = py;
    width = w;
    height = h;
    cursorBase = true;
  }

  private boolean sameContext(Object context, int w, int h) {
    return cursorBase && cursorContext == context && width == w && height == h;
  }

  void forgetCursor() {
    cursorBase = false;
    cursorContext = null;
  }

  void regrab() {
    grace = REGRAB_GRACE_TICKS;
    pixels = 0d;
    forgetCursor();
  }

  boolean triggered(int threshold) {
    if (grace > 0) {
      pixels = 0d;
      return false;
    }
    if (pixels < threshold) return false;
    pixels = 0d;
    return true;
  }

  void tick() {
    if (grace > 0) grace--;
    pixels *= DECAY;
  }

  void reset() {
    pixels = 0d;
    grace = 0;
    forgetCursor();
  }
}

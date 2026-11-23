package com.mirror.recorder.gui;

import java.util.Arrays;

/** Shared tooltip viewport; scrolling it must not also move the underlying settings list. */
final class UiTooltipScroll {
  private static String key = "";
  private static int offset, maximum;
  private static long seen;
  private static java.lang.ref.WeakReference<Object> owner;

  static void forScreen(Object screen) {
    if (owner != null && owner.get() == screen) return;
    owner = new java.lang.ref.WeakReference<Object>(screen);
    key = "";
    offset = maximum = 0;
    seen = 0L;
  }

  private UiTooltipScroll() {}

  static int show(String[] paragraphs, int width, int height, int maxScroll, long now) {
    String next = width + ":" + height + ":" + Arrays.toString(paragraphs);
    if (!next.equals(key) || now - seen > 250L) offset = 0;
    key = next;
    maximum = maxScroll;
    offset = Math.max(0, Math.min(maximum, offset));
    seen = now;
    return offset;
  }

  static boolean wheel(int delta, long now) {
    if (delta == 0 || maximum == 0 || now - seen > 150L) return false;
    offset = Math.max(0, Math.min(maximum, offset + (delta > 0 ? -2 : 2)));
    return true;
  }
}

package com.mirror.recorder.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/** A complete single-line name in its existing viewport; never discards the end of a string. */
final class UiReadingLine {
  private UiReadingLine() {}

  private static final Map<String, Clock> clocks =
      new LinkedHashMap<String, Clock>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Clock> e) {
          return size() > 256;
        }
      };

  private static final class Clock {
    long start, last;

    Clock(long now) {
      start = last = now;
    }
  }

  static int offset(int overflow, long age) {
    if (overflow <= 0) return 0;
    long travel = (long) Math.ceil(overflow * 1000.0 / 18.0);
    long phase = Math.max(0, age) % (1600L + travel + 2200L);
    if (phase <= 1600L) return 0;
    return (int) Math.min(overflow, (phase - 1600L) * 18L / 1000L);
  }

  static void draw(
      FontRenderer font, String text, int x, int y, int width, int color, boolean shadow) {
    int overflow = font.getStringWidth(text) - width;
    if (overflow <= 0) {
      paint(font, text, x, y, color, shadow);
      return;
    }
    Minecraft mc = Minecraft.getMinecraft();
    ScaledResolution resolution = new ScaledResolution(mc);
    int mx = Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
    int my =
        resolution.getScaledHeight()
            - Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight
            - 1;
    boolean reading =
        canAnimate(UiTheme.animationsEnabled(), mx, my, x, y, width, font.FONT_HEIGHT);
    long now = UiTheme.nowMs();
    String key = x + ":" + y + ":" + width + ":" + text;
    Clock clock = clocks.get(key);
    if (!reading || clock == null || now - clock.last > 500L) {
      clock = new Clock(now);
      clocks.put(key, clock);
    }
    clock.last = now;
    int shift = reading ? offset(overflow + 2, now - clock.start) : 0;
    UiTheme.beginClip(
        Minecraft.getMinecraft(), x - 1, y - 1, x + width + 2, y + font.FONT_HEIGHT + 4);
    try {
      paint(font, text, x - shift, y, color, shadow);
    } finally {
      UiTheme.endClip();
    }
  }

  static boolean canAnimate(boolean enabled, int mx, int my, int x, int y, int width, int height) {
    return enabled && mx >= x && mx < x + width && my >= y && my < y + height;
  }

  private static void paint(
      FontRenderer font, String text, int x, int y, int color, boolean shadow) {
    if (shadow) font.drawStringWithShadow(text, x, y, color);
    else font.drawString(text, x, y, color);
  }
}

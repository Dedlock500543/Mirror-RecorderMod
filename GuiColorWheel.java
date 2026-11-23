package com.mirror.recorder.gui;

import net.minecraft.client.gui.Gui;

/** Color picker with transparency: saturation/value square, hue bar and alpha bar. */
public class GuiColorWheel {
  private static final int[] PRESETS = {0x8A2BE2, 0x4F8BE0, 0x31B7D7, 0x49C08A, 0xEBB04A, 0xE5645E};
  private final int x, y, size, barW = 12, barGap = 7, presetSize = 8;
  private float hue = 0.8f, sat = 1f, val = 1f;
  private int alpha = 180;
  private boolean dragSquare, dragHue, dragAlpha;

  public GuiColorWheel(int x, int y, int size) {
    this.x = x;
    this.y = y;
    this.size = size;
  }

  public void setColorRGBA(int r, int g, int b, int a) {
    float[] v = toHSV(r, g, b);
    hue = v[0];
    sat = v[1];
    val = v[2];
    alpha = Math.max(0, Math.min(255, a));
  }

  public void setColorRGB(int r, int g, int b) {
    setColorRGBA(r, g, b, alpha);
  }

  public int getRed() {
    return (hsv(hue, sat, val) >> 16) & 255;
  }

  public int getGreen() {
    return (hsv(hue, sat, val) >> 8) & 255;
  }

  public int getBlue() {
    return hsv(hue, sat, val) & 255;
  }

  public int getAlpha() {
    return alpha;
  }

  private int hueX() {
    return x + size + barGap;
  }

  private int presetX() {
    return hueX() + barW + 7;
  }

  private int presetCell() {
    return Math.max(2, Math.min(presetSize, (size - 5) / PRESETS.length));
  }

  private int totalW() {
    return size + barGap + barW + 7 + presetCell();
  }

  public int getWidth() {
    return totalW();
  }

  public int getHeight() {
    return size + 16;
  }

  private int alphaY() {
    return y + size + 6;
  }
  // Cells of the square, the hue bar and the alpha bar are rendered in one batch instead of a
  // separate
  // Gui.drawRect call per cell. Markers and borders are drawn after the batch.
  public void draw() {
    UiTheme.shadow(x - 1, y - 1, x + totalW() + 1, alphaY() + 9);
    int step = 2;
    // Bar coordinates are needed after the rectangle batch too (borders, sliders), so they are
    // declared before try.
    int hx = hueX(), ay = alphaY(), rgb = hsv(hue, sat, val), tw = totalW();
    UiTheme.beginRects();
    try {
      for (int px = 0; px < size; px += step) {
        float s = (float) px / Math.max(1, size - 1);
        int w = Math.min(step, size - px);
        for (int py = 0; py < size; py += step) {
          float v = 1f - (float) py / Math.max(1, size - 1);
          int h = Math.min(step, size - py);
          UiTheme.rect(x + px, y + py, x + px + w, y + py + h, 0xFF000000 | hsv(hue, s, v));
        }
      }
      for (int py = 0; py < size; py += 2) {
        int h = Math.min(2, size - py);
        UiTheme.rect(
            hx,
            y + py,
            hx + barW,
            y + py + h,
            0xFF000000 | hsv((float) py / Math.max(1, size - 1), 1f, 1f));
      }
      for (int xx = 0; xx < tw; xx += 4)
        UiTheme.rect(
            x + xx,
            ay,
            Math.min(x + tw, x + xx + 4),
            ay + 8,
            (xx / 4) % 2 == 0 ? 0xFFCDD2DA : 0xFF858E9D);
      for (int xx = 0; xx < tw; xx += 2) {
        int a = Math.round((float) xx / Math.max(1, tw - 1) * 255f);
        UiTheme.rect(x + xx, ay, Math.min(x + tw, x + xx + 2), ay + 8, (a << 24) | rgb);
      }
    } finally {
      UiTheme.endRects();
    }
    UiTheme.border(x - 1, y - 1, x + size + 1, y + size + 1, UiTheme.BORDER_HI);
    int cx = x + Math.round(sat * (size - 1)), cy = y + Math.round((1f - val) * (size - 1));
    Gui.drawRect(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF080B10);
    Gui.drawRect(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
    Gui.drawRect(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000 | hsv(hue, sat, val));
    UiTheme.border(hx - 1, y - 1, hx + barW + 1, y + size + 1, UiTheme.BORDER_HI);
    int hy = y + Math.round(hue * (size - 1));
    Gui.drawRect(hx - 2, hy - 2, hx + barW + 2, hy + 3, 0xFF080B10);
    Gui.drawRect(hx - 1, hy - 1, hx + barW + 1, hy + 2, 0xFFFFFFFF);
    int px = presetX(),
        ps = presetCell(),
        gap = Math.max(1, (size - PRESETS.length * ps) / (PRESETS.length - 1));
    for (int i = 0; i < PRESETS.length; i++) {
      int py = y + i * (ps + gap);
      Gui.drawRect(px, py, px + ps, py + ps, 0xFF000000 | PRESETS[i]);
      UiTheme.border(px - 1, py - 1, px + ps + 1, py + ps + 1, UiTheme.BORDER_HI);
    }
    UiTheme.border(x - 1, ay - 1, x + tw + 1, ay + 9, UiTheme.BORDER_HI);
    int ax = x + Math.round(alpha / 255f * (tw - 1));
    Gui.drawRect(ax - 2, ay - 2, ax + 3, ay + 10, 0xFF080B10);
    Gui.drawRect(ax - 1, ay - 1, ax + 2, ay + 9, 0xFFFFFFFF);
  }

  public boolean contains(int mx, int my) {
    return mx >= x - 2 && mx <= x + totalW() + 2 && my >= y - 2 && my <= alphaY() + 11;
  }

  public boolean mouseClicked(int mx, int my) {
    if (mx >= x && mx < x + size && my >= y && my < y + size) {
      dragSquare = true;
      setSV(mx, my);
      return true;
    }
    int hx = hueX();
    if (mx >= hx && mx < hx + barW && my >= y && my < y + size) {
      dragHue = true;
      setHue(my);
      return true;
    }
    int px = presetX(),
        ps = presetCell(),
        gap = Math.max(1, (size - PRESETS.length * ps) / (PRESETS.length - 1));
    for (int i = 0; i < PRESETS.length; i++) {
      int py = y + i * (ps + gap);
      if (mx >= px - 1 && mx <= px + ps + 1 && my >= py - 1 && my <= py + ps + 1) {
        int c = PRESETS[i];
        setColorRGB((c >> 16) & 255, (c >> 8) & 255, c & 255);
        return true;
      }
    }
    int ay = alphaY();
    if (mx >= x && mx < x + totalW() && my >= ay - 2 && my <= ay + 10) {
      dragAlpha = true;
      setAlpha(mx);
      return true;
    }
    return false;
  }

  public void mouseDragged(int mx, int my) {
    if (dragSquare) setSV(mx, my);
    if (dragHue) setHue(my);
    if (dragAlpha) setAlpha(mx);
  }

  public void mouseReleased() {
    dragSquare = dragHue = dragAlpha = false;
  }

  public boolean isDragging() {
    return dragSquare || dragHue || dragAlpha;
  }

  private void setSV(int mx, int my) {
    sat = clamp((float) (mx - x) / Math.max(1, size - 1));
    val = 1f - clamp((float) (my - y) / Math.max(1, size - 1));
  }

  private void setHue(int my) {
    hue = clamp((float) (my - y) / Math.max(1, size - 1));
  }

  private void setAlpha(int mx) {
    alpha = Math.round(clamp((float) (mx - x) / Math.max(1, totalW() - 1)) * 255f);
  }

  private static float clamp(float v) {
    return v < 0f ? 0f : v > 1f ? 1f : v;
  }

  private static int hsv(float h, float s, float v) {
    float r, g, b;
    int i = (int) Math.floor(h * 6f) % 6;
    if (i < 0) i += 6;
    float f = h * 6f - (float) Math.floor(h * 6f),
        p = v * (1f - s),
        q = v * (1f - f * s),
        t = v * (1f - (1f - f) * s);
    switch (i) {
      case 0:
        r = v;
        g = t;
        b = p;
        break;
      case 1:
        r = q;
        g = v;
        b = p;
        break;
      case 2:
        r = p;
        g = v;
        b = t;
        break;
      case 3:
        r = p;
        g = q;
        b = v;
        break;
      case 4:
        r = t;
        g = p;
        b = v;
        break;
      default:
        r = v;
        g = p;
        b = q;
    }
    return ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
  }

  private static float[] toHSV(int R, int G, int B) {
    float r = R / 255f,
        g = G / 255f,
        b = B / 255f,
        max = Math.max(r, Math.max(g, b)),
        min = Math.min(r, Math.min(g, b)),
        d = max - min,
        h = 0f;
    if (d != 0f) {
      if (max == r) h = ((g - b) / d) % 6f;
      else if (max == g) h = (b - r) / d + 2f;
      else h = (r - g) / d + 4f;
      h /= 6f;
      if (h < 0f) h += 1f;
    }
    return new float[] {h, max == 0f ? 0f : d / max, max};
  }
}

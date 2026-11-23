package com.mirror.recorder.gui;

import com.mirror.recorder.MirrorRecorder;
import java.lang.ref.WeakReference;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Common look of the mod UI: colors, sizes, animations and drawing primitives (windows, cards,
 * labels, text).
 */
public final class UiTheme {
  private UiTheme() {}

  public static final int PANEL = 0xB40E1526,
      PANEL_HEAD = 0xC4172038,
      BORDER = 0xFF283449,
      BORDER_HI = 0xFF3A4A66,
      CARD = 0xAA1A2438,
      CARD_HOVER = 0xAA24304A,
      CARD_SEL = 0xAA22375C,
      CARD_SELB = 0xFF6FA8FF,
      ACCENT = 0xFF7AA2FF,
      GREEN = 0xFF56D6A0,
      BLUE = 0xFF5FB2FF,
      PURPLE = 0xFFBB94F5,
      RED = 0xFFF07B76,
      AMBER = 0xFFF3BE63,
      GRAY = 0xFF6A7690,
      TEXT = 0xFFF2F5FB,
      TEXT_DIM = 0xFFC2CCDC,
      TEXT_MUTE = 0xFF8B97AC,
      DEEP = 0xAA0B1120;
  public static final int GLASS_BACKDROP = 0x8C05080F,
      FUNCTION_LIST_GLASS = 0xB4141C2B,
      GLASS_TINT_TOP = 0x32263C68,
      GLASS_TINT_BOTTOM = 0x160A1020,
      HUD_GLASS = 0xB4121A28;
  public static final long HOVER_MS = 170L, SCROLL_MS = 170L, TIP_DELAY_MS = 260L;
  /** Unified animation scale: press, toggle, row appearance, cascade and feedback flash. */
  public static final long PRESS_MS = 110L,
      KNOB_MS = 220L,
      APPEAR_MS = 230L,
      STAGGER_MS = 22L,
      FLASH_MS = 420L;

  public static final long TIP_FADE_MS = 120L;
  private static final long CLOCK_ORIGIN = System.nanoTime();
  /**
   * UI animation clock, ms since UiTheme initialization. Monotonic: if the system time moved back,
   * currentTimeMillis would freeze transitions, hover and tooltips until the clock caught up.
   */
  public static long nowMs() {
    return (System.nanoTime() - CLOCK_ORIGIN) / 1000000L;
  }
  /** Overall layer opacity: the row appears as a whole, not in pieces. */
  private static float globalAlpha = 1f;

  public static void opacity(float a) {
    if (!animationsEnabled()) {
      globalAlpha = 1f;
      return;
    }
    globalAlpha = a < 0f ? 0f : a > 1f ? 1f : a;
  }

  public static void opacityReset() {
    globalAlpha = 1f;
  }

  private static int fade(int c) {
    return globalAlpha >= 0.999f ? c : alpha(c, globalAlpha);
  }
  /** Soft ease-out towards the end of the motion: tooltips, row appearance. */
  public static float easeOut(float t) {
    if (!animationsEnabled()) return 1f;
    if (t <= 0f) return 0f;
    if (t >= 1f) return 1f;
    float u = 1f - t;
    return 1f - u * u * u;
  }

  private static final int CORNER = 14;
  /** Soft 0..1 pulse for active-state indicators. */
  public static float pulse(long periodMs) {
    if (!animationsEnabled()) return 1f;
    if (periodMs <= 0L) return 1f;
    long m = nowMs() % periodMs;
    if (m < 0L) m += periodMs;
    double phase = m / (double) periodMs;
    return (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
  }
  // The key is held by a weak reference: otherwise the field would retain a closed screen's
  // tooltip key until the first tooltip on the next screen.
  private static WeakReference<Object> tipKey = null;
  private static long tipAt = 0L, tipLast = 0L;
  /** The tooltip appears with a delay so it does not flicker when the cursor passes over. */
  public static boolean tipReady(Object key) {
    if (key == null) return false;
    long now = nowMs();
    Object last = tipKey == null ? null : tipKey.get();
    if (last == null || !last.equals(key) || now - tipLast > 250L) {
      tipKey = new WeakReference<Object>(key);
      tipAt = now;
    }
    tipLast = now;
    return now - tipAt >= TIP_DELAY_MS;
  }
  /** How far the tooltip has appeared: 0..1. Call only after tipReady. */
  public static float tipFade() {
    if (!animationsEnabled()) return 1f;
    long e = nowMs() - tipAt - TIP_DELAY_MS;
    if (e <= 0L) return 0f;
    if (e >= TIP_FADE_MS) return 1f;
    return easeOut(e / (float) TIP_FADE_MS);
  }
  /**
   * Clips drawing to a rectangle: content does not spill out of the list. The scale is taken from
   * ScaledResolution on every call: the object is cheap, and a custom cache of its inputs would
   * diverge from mods that change the UI scale.
   */
  private static final Deque<int[]> CLIPS = new ArrayDeque<int[]>();

  private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);

  public static void beginClip(Minecraft mc, int l, int t, int r, int b) {
    boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    SCISSOR_BOX.clear();
    GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
    int[] old = {
      enabled ? 1 : 0,
      SCISSOR_BOX.get(0),
      SCISSOR_BOX.get(1),
      SCISSOR_BOX.get(2),
      SCISSOR_BOX.get(3)
    };
    CLIPS.push(old);
    ScaledResolution sr = new ScaledResolution(mc);
    int f = sr.getScaleFactor();
    int x = l * f, y = mc.displayHeight - b * f;
    int right = Math.max(l, r) * f, top = mc.displayHeight - t * f;
    if (enabled) {
      x = Math.max(x, old[1]);
      y = Math.max(y, old[2]);
      right = Math.min(right, old[1] + old[3]);
      top = Math.min(top, old[2] + old[4]);
    }
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(x, y, Math.max(0, right - x), Math.max(0, top - y));
  }

  public static void endClip() {
    if (CLIPS.isEmpty()) return;
    int[] old = CLIPS.pop();
    GL11.glScissor(old[1], old[2], old[3], old[4]);
    if (old[0] == 0) GL11.glDisable(GL11.GL_SCISSOR_TEST);
    else GL11.glEnable(GL11.GL_SCISSOR_TEST);
  }
  /** Smootherstep: softer than the regular smoothstep at both ends, without jerks. */
  public static float ease(float t) {
    if (t <= 0f) return 0f;
    if (t >= 1f) return 1f;
    return t * t * t * (t * (t * 6f - 15f) + 10f);
  }

  public static float approach(float value, float target, float delta, float speed) {
    if (!animationsEnabled()) return target;
    float dt = Math.max(0f, delta);
    float rate = Math.max(0f, speed);
    float step = (float) (1.0 - Math.exp(-dt * rate));
    return value + (target - value) * step;
  }

  public static boolean animationsEnabled() {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    return mod == null || mod.getConfig() == null || mod.getConfig().isUiAnimationsEnabled();
  }

  public static float transition(float from, float to, long started, long now, long duration) {
    if (!animationsEnabled() || duration <= 0L) return to;
    long elapsed = now - started;
    if (elapsed <= 0L) return from;
    if (elapsed >= duration) return to;
    return from + (to - from) * ease(elapsed / (float) duration);
  }

  public static int alpha(int color, float a) {
    int v = (int) (((color >>> 24) & 255) * Math.max(0f, Math.min(1f, a)));
    return (v << 24) | (color & 0x00FFFFFF);
  }

  public static int lerp(int a, int b, float t) {
    if (t <= 0f) return a;
    if (t >= 1f) return b;
    int aa = (a >>> 24) & 255,
        ar = (a >> 16) & 255,
        ag = (a >> 8) & 255,
        ab = a & 255,
        ba = (b >>> 24) & 255,
        br = (b >> 16) & 255,
        bg = (b >> 8) & 255,
        bb = b & 255;
    return ((int) (aa + (ba - aa) * t) << 24)
        | ((int) (ar + (br - ar) * t) << 16)
        | ((int) (ag + (bg - ag) * t) << 8)
        | (int) (ab + (bb - ab) * t);
  }

  public static int darken(int c, float f) {
    int a = (c >>> 24) & 255,
        r = (int) (((c >> 16) & 255) * f),
        g = (int) (((c >> 8) & 255) * f),
        b = (int) ((c & 255) * f);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static int tint(int c, int rgb, float amount) {
    return lerp(c, (c & 0xFF000000) | (rgb & 0x00FFFFFF), amount);
  }

  private static float rad(float w, float h) {
    return Math.max(1.5f, Math.min(6f, Math.min(w, h) * 0.5f));
  }

  static void glColor(int c) {
    c = fade(c);
    GL11.glColor4f(
        ((c >> 16) & 255) / 255f,
        ((c >> 8) & 255) / 255f,
        (c & 255) / 255f,
        ((c >>> 24) & 255) / 255f);
  }
  /**
   * Texture, alpha-test, culling, depth, blend and shading state changes use GlStateManager to
   * avoid cache mismatches with OptiFine / Fast Render. Direct color writes are reset in endDraw().
   */
  static void beginDraw() {
    GlStateManager.disableTexture2D();
    GlStateManager.disableAlpha();
    GlStateManager.disableCull();
    GlStateManager.disableDepth();
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
  }

  static void endDraw() {
    GlStateManager.shadeModel(GL11.GL_FLAT);
    GlStateManager.disableBlend();
    GlStateManager.enableDepth();
    GlStateManager.enableCull();
    GlStateManager.enableAlpha();
    GlStateManager.enableTexture2D();
    resetColor();
  }
  /**
   * Reset color and the GlStateManager cache: otherwise the next text or icon is tinted with our
   * color.
   */
  private static void resetColor() {
    GlStateManager.color(1f, 1f, 1f, 0.996f);
    GlStateManager.color(1f, 1f, 1f, 1f);
  }
  /**
   * Outline sampling: CORNER_N+1 points per corner, OUTLINE_POINTS in total; sin/cos are computed
   * once, not per frame.
   */
  private static final int CORNER_N = Math.max(8, CORNER), OUTLINE_POINTS = 4 * (CORNER_N + 1);

  private static final float[] UNIT_COS = new float[OUTLINE_POINTS],
      UNIT_SIN = new float[OUTLINE_POINTS];

  static {
    float[] from = {180f, 270f, 0f, 90f}, to = {270f, 360f, 90f, 180f};
    int i = 0;
    for (int q = 0; q < 4; q++)
      for (int s = 0; s <= CORNER_N; s++) {
        double a = Math.toRadians(from[q] + (to[q] - from[q]) * s / CORNER_N);
        UNIT_COS[i] = (float) Math.cos(a);
        UNIT_SIN[i] = (float) Math.sin(a);
        i++;
      }
  }
  /**
   * Outline of a rounded rectangle into xs/ys. 0 means the shape is empty and the arrays are
   * untouched.
   */
  private static int outline(
      float l, float t, float r, float b, float radius, float[] xs, float[] ys) {
    float w = r - l, h = b - t;
    if (w <= 0f || h <= 0f) return 0;
    float rad = Math.min(radius, Math.min(w, h) * 0.5f);
    for (int i = 0; i < OUTLINE_POINTS; i++) {
      int q = i / (CORNER_N + 1);
      float cx = q == 0 || q == 3 ? l + rad : r - rad, cy = q < 2 ? t + rad : b - rad;
      xs[i] = cx + UNIT_COS[i] * rad;
      ys[i] = cy + UNIT_SIN[i] * rad;
    }
    return OUTLINE_POINTS;
  }
  // Reused client-thread buffers. The fringe is geometry coverage, not a blur or a framebuffer.
  private static final float[] FX = new float[OUTLINE_POINTS], FY = new float[OUTLINE_POINTS];
  private static final float[][] EDGE_X = new float[4][OUTLINE_POINTS],
      EDGE_Y = new float[4][OUTLINE_POINTS];
  private static float cachedEdgeWidth = 0.5f;
  private static long edgeScaleAt = -100L;

  /** One physical pixel, independent of GUI scale; keeps text and shape interiors untouched. */
  static float edgeWidthForScale(int scale) {
    return 1f / Math.max(1, scale);
  }

  static float edgeWidth() {
    long now = nowMs();
    if (now - edgeScaleAt >= 16L) {
      Minecraft client = Minecraft.getMinecraft();
      if (client != null && client.gameSettings != null)
        cachedEdgeWidth = edgeWidthForScale(new ScaledResolution(client).getScaleFactor());
      edgeScaleAt = now;
    }
    return cachedEdgeWidth;
  }

  private static int offsetOutline(
      float l, float t, float r, float b, float radius, float offset, int slot) {
    return outline(
        l + offset,
        t + offset,
        r - offset,
        b - offset,
        Math.max(0f, radius - offset),
        EDGE_X[slot],
        EDGE_Y[slot]);
  }

  private static int gradientAt(float y, float topY, float height, int top, int bottom) {
    return lerp(top, bottom, height <= 1f ? 0f : Math.max(0f, Math.min(1f, (y - topY) / height)));
  }

  private static void solidFan(
      float[] xs, float[] ys, int n, float cx, float cy, float t, float h, int top, int bottom) {
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    glColor(gradientAt(cy, t, h, top, bottom));
    GL11.glVertex2f(cx, cy);
    for (int i = 0; i <= n; i++) {
      int j = i % n;
      glColor(gradientAt(ys[j], t, h, top, bottom));
      GL11.glVertex2f(xs[j], ys[j]);
    }
    GL11.glEnd();
  }

  /** Connects two contours with independent coverage; never draws overlapping translucent fans. */
  private static void edgeStrip(
      int a, int b, int n, float t, float h, int top, int bottom, boolean clearA, boolean clearB) {
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    for (int i = 0; i <= n; i++) {
      int j = i % n;
      int ca = gradientAt(EDGE_Y[a][j], t, h, top, bottom);
      int cb = gradientAt(EDGE_Y[b][j], t, h, top, bottom);
      glColor(clearA ? ca & 0x00FFFFFF : ca);
      GL11.glVertex2f(EDGE_X[a][j], EDGE_Y[a][j]);
      glColor(clearB ? cb & 0x00FFFFFF : cb);
      GL11.glVertex2f(EDGE_X[b][j], EDGE_Y[b][j]);
    }
    GL11.glEnd();
  }

  private static void fillRound(
      float l, float t, float r, float b, float radius, int top, int bottom) {
    float w = r - l, h = b - t;
    if (w <= 0f || h <= 0f) return;
    float radiusClamped = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
    float half = Math.min(edgeWidth(), Math.min(w, h) * 0.5f) * 0.5f;
    int n = offsetOutline(l, t, r, b, radiusClamped, -half, 0);
    if (offsetOutline(l, t, r, b, radiusClamped, half, 1) != n) return;
    beginDraw();
    try {
      solidFan(EDGE_X[1], EDGE_Y[1], n, (l + r) * 0.5f, (t + b) * 0.5f, t, h, top, bottom);
      edgeStrip(0, 1, n, t, h, top, bottom, true, false);
    } finally {
      endDraw();
    }
  }

  private static void strokeRound(
      float l, float t, float r, float b, float radius, int color, float thickness) {
    float w = r - l, h = b - t;
    if (w <= 1.5f || h <= 1.5f || thickness <= 0f) return;
    float rad = Math.max(0f, Math.min(radius, Math.min(w, h) * 0.5f));
    float half = Math.min(edgeWidth(), thickness) * 0.5f;
    int n = offsetOutline(l, t, r, b, rad, -half, 0);
    if (offsetOutline(l, t, r, b, rad, half, 1) != n
        || offsetOutline(l, t, r, b, rad, thickness - half, 2) != n
        || offsetOutline(l, t, r, b, rad, thickness + half, 3) != n) {
      fillRound(l, t, r, b, rad, color, color);
      return;
    }
    beginDraw();
    try {
      edgeStrip(0, 1, n, t, h, color, color, true, false);
      edgeStrip(1, 2, n, t, h, color, color, false, false);
      edgeStrip(2, 3, n, t, h, color, color, false, true);
    } finally {
      endDraw();
    }
  }

  /** Analytic coverage geometry for small disks, including badges and preview markers. */
  static void smoothDisk(float x, float y, float radius, int color) {
    if (radius <= 0f) return;
    fillRound(x - radius, y - radius, x + radius, y + radius, radius, color, color);
  }

  static void smoothRing(float x, float y, float radius, float width, int color) {
    if (radius <= 0f) return;
    float outer = radius + width * 0.5f;
    strokeRound(x - outer, y - outer, x + outer, y + outer, outer, color, width);
  }

  /** Short flash along the outline: confirms that the value changed. */
  public static void flashRing(int l, int t, int r, int b, int color, float a) {
    float k = Math.max(0f, Math.min(1f, a));
    if (k <= 0.02f) return;
    float rr = rad(r - l, b - t);
    glow(l, t, r, b, rr, color, 0.34f * k);
    strokeRound(l, t, r, b, rr, alpha(color, 0.5f * k), 1f);
  }
  /** Generated category arrow, rotating at the original center without affecting layout. */
  public static void chevron(float cx, float cy, float size, float open, int color) {
    float angle = 90f * ease(Math.max(0f, Math.min(1f, open)));
    VectorIcons.drawRotated("next", cx, cy, Math.max(3f, size) / 6f, color, angle);
  }

  public static void round(int l, int t, int r, int b, int c) {
    fillRound(l, t, r, b, rad(r - l, b - t), c, c);
  }

  public static void roundGradient(int l, int t, int r, int b, int top, int bottom) {
    fillRound(l, t, r, b, rad(r - l, b - t), top, bottom);
  }

  public static void border(int l, int t, int r, int b, int c) {
    strokeRound(l, t, r, b, rad(r - l, b - t), c, 1f);
  }
  /** Soft glow around an element: two fading outlines instead of an expensive blur. */
  private static void glow(float l, float t, float r, float b, float rr, int color, float a) {
    float k = Math.max(0f, Math.min(1f, a));
    if (k <= 0.01f) return;
    strokeRound(l - 1, t - 1, r + 1, b + 1, rr + 1f, alpha(color, 0.55f * k), 1f);
    strokeRound(l - 2, t - 2, r + 2, b + 2, rr + 2f, alpha(color, 0.24f * k), 1f);
  }

  public static void shadow(int l, int t, int r, int b) {
    shadow(l, t, r, b, 1f);
  }
  /**
   * Element shadow; a 0..1: the shadow fades together with the element, otherwise it "pops" before
   * it.
   */
  public static void shadow(int l, int t, int r, int b, float a) {
    float k = Math.max(0f, Math.min(1f, a));
    if (k <= 0.03f) return;
    fillRound(
        l + 4,
        t + 7,
        r + 4,
        b + 7,
        rad(r - l, b - t) + 2f,
        alpha(0x0E000000, k),
        alpha(0x0E000000, k));
    fillRound(
        l + 3,
        t + 5,
        r + 3,
        b + 5,
        rad(r - l, b - t) + 1f,
        alpha(0x16000000, k),
        alpha(0x16000000, k));
    fillRound(
        l + 2,
        t + 3,
        r + 2,
        b + 3,
        rad(r - l, b - t) + 0.5f,
        alpha(0x1E000000, k),
        alpha(0x1E000000, k));
    fillRound(
        l + 1, t + 2, r + 1, b + 2, rad(r - l, b - t), alpha(0x26000000, k), alpha(0x26000000, k));
  }

  public static void window(int l, int t, int r, int b, int headH) {
    shadow(l, t, r, b);
    fillRound(l, t, r, b, 6f, tint(PANEL, 0xFFFFFF, 0.05f), PANEL);
    if (headH > 8)
      fillRound(l + 1, t + 1, r - 1, t + headH, 5f, tint(PANEL_HEAD, 0xFFFFFF, 0.08f), PANEL_HEAD);
    strokeRound(l, t, r, b, 6f, BORDER_HI, 1f);
  }

  public static void card(int l, int t, int r, int b, int fill, int brdr) {
    fillRound(
        l, t, r, b, rad(r - l, b - t), tint(fill, 0xFFFFFF, 0.07f), tint(fill, 0x000000, 0.05f));
    strokeRound(l, t, r, b, rad(r - l, b - t), brdr, 1f);
  }

  /** Borderless counter: edge buttons and centered value retain their original positions. */
  public static void counterFrame(int x, int y, int button, int valueWidth) {
    int r = x + button * 2 + valueWidth, b = y + button;
    card(x, y, r, b, DEEP, 0);
  }

  /** Borderless value-change feedback; never strokes the counter or its edge buttons. */
  public static void counterFeedback(int l, int t, int r, int b, float amount) {
    if (!animationsEnabled()) return;
    float a = Math.max(0f, Math.min(1f, amount));
    if (a > 0.02f) round(l, t, r, b, alpha(ACCENT, 0.12f * a));
  }

  public static void buttonBg(
      int l,
      int t,
      int r,
      int b,
      int accent,
      float hover,
      boolean enabled,
      boolean pressed,
      boolean primary) {
    buttonBg(l, t, r, b, accent, hover, enabled, pressed, primary, 1f);
  }
  /**
   * Button background; fade 0..1: smooth button appearance. Only alpha changes: geometry and mouse
   * hits stay exact.
   */
  public static void buttonBg(
      int l,
      int t,
      int r,
      int b,
      int accent,
      float hover,
      boolean enabled,
      boolean pressed,
      boolean primary,
      float fade) {
    buttonBg(l, t, r, b, accent, hover, enabled, pressed ? 1f : 0f, primary, fade);
  }

  public static void buttonBg(
      int l,
      int t,
      int r,
      int b,
      int accent,
      float hover,
      boolean enabled,
      float pressure,
      boolean primary) {
    buttonBg(l, t, r, b, accent, hover, enabled, pressure, primary, 1f);
  }

  private static void buttonBg(
      int l,
      int t,
      int r,
      int b,
      int accent,
      float hover,
      boolean enabled,
      float pressure,
      boolean primary,
      float fade) {
    float p = Math.max(0f, Math.min(1f, pressure));
    float fa = Math.max(0f, Math.min(1f, fade));
    if (fa <= 0.03f) return;
    float h = ease(Math.max(0f, Math.min(1f, hover)));
    int top, bottom, brdr;
    float rr = rad(r - l, b - t);
    if (!enabled) {
      top = 0xA0161D2C;
      bottom = 0xA0111825;
      brdr = 0xFF242E40;
    } else if (primary) {
      int base = lerp(darken(accent, 0.44f), darken(accent, 0.60f), h);
      base = lerp(base, darken(accent, 0.36f), p);
      top = alpha(lerp(base, accent, 0.24f), 0.96f);
      bottom = alpha(base, 0.96f);
      brdr = lerp(darken(accent, 0.70f), accent, 0.34f + 0.66f * h);
    } else {
      int base = lerp(CARD, CARD_HOVER, h);
      base = lerp(base, darken(base, 0.86f), p);
      top = lerp(base, alpha(0xFFFFFFFF, 0.12f), 0.05f + 0.10f * h);
      bottom = base;
      brdr = lerp(BORDER, accent, 0.14f + 0.60f * h);
    }
    if (enabled && h > 0.02f) glow(l, t, r, b, rr, accent, (primary ? 0.17f : 0.10f) * h * fa);
    fillRound(l, t, r, b, rr, alpha(top, fa), alpha(bottom, fa));
    if (enabled && p < 0.999f && b - t > 11)
      round(
          l + 2, t + 1, r - 2, t + 2, alpha(0xFFFFFFFF, (primary ? 0.15f : 0.08f) * fa * (1f - p)));
    strokeRound(l, t, r, b, rr, alpha(brdr, fa), 1f);
  }

  public static int pillAt(FontRenderer fr, String text, int x, int y, int fill, int brdr, int tc) {
    int w = pillWidth(fr, text);
    fillRound(x, y, x + w, y + PILL_H, 6f, tint(fill, 0xFFFFFF, 0.08f), fill);
    strokeRound(x, y, x + w, y + PILL_H, 6f, brdr, 1f);
    fr.drawString(text, x + PILL_PAD, pillTextY(fr, y), tc);
    return w;
  }

  public static void sectionLabel(FontRenderer fr, String text, int x, int y, int w) {
    drawFitted(fr, text, x, y, Math.max(16, w), TEXT_MUTE);
  }
  /** Group header: an accent dot and bold text instead of extra lines. */
  public static void sectionHead(FontRenderer fr, String text, int x, int y, int w, int accent) {
    dot(x, y + 1, alpha(accent, 0.85f));
    drawFitted(fr, "\u00a7l" + text, x + 11, y, Math.max(16, w - 11), TEXT_DIM);
  }

  public static void inset(int l, int t, int r, int b) {
    card(l, t, r, b, DEEP, BORDER);
  }

  public static void dot(int x, int y, int color) {
    smoothDisk(x + 3f, y + 3f, 3.1f, darken(color, 0.55f));
    smoothDisk(x + 3f, y + 3f, 2.1f, color);
  }

  public static void progress(int l, int t, int r, int b, float value, int color) {
    float rr = rad(r - l, b - t);
    fillRound(l, t, r, b, rr, DEEP, DEEP);
    int w = (int) ((r - l) * Math.max(0f, Math.min(1f, value)));
    if (w > 1) fillRound(l, t, l + w, b, rr, tint(color, 0xFFFFFF, 0.10f), color);
    strokeRound(l, t, r, b, rr, BORDER, 1f);
  }

  /** Rounded, coverage-antialiased segments for ticks; no driver-dependent line smoothing. */
  static void line(float x1, float y1, float x2, float y2, float w, int color) {
    float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
    if (len < 0.01f || w <= 0f) return;
    float rad = w * 0.5f, half = Math.min(edgeWidth(), w * 0.5f) * 0.5f;
    int n = capsuleOutline(x1, y1, dx / len, dy / len, len, rad + half, 0);
    if (capsuleOutline(x1, y1, dx / len, dy / len, len, rad - half, 1) != n) return;
    beginDraw();
    try {
      solidFan(EDGE_X[1], EDGE_Y[1], n, (x1 + x2) * 0.5f, (y1 + y2) * 0.5f, 0f, 1f, color, color);
      edgeStrip(0, 1, n, 0f, 1f, color, color, true, false);
    } finally {
      endDraw();
    }
  }

  private static int capsuleOutline(
      float x, float y, float cos, float sin, float len, float radius, int slot) {
    int n = outline(-radius, -radius, len + radius, radius, radius, FX, FY);
    for (int i = 0; i < n; i++) {
      EDGE_X[slot][i] = x + FX[i] * cos - FY[i] * sin;
      EDGE_Y[slot][i] = y + FX[i] * sin + FY[i] * cos;
    }
    return n;
  }
  /**
   * A checkbox instead of a toggle: a rounded square, the tick is drawn as it switches on. A
   * positive {@code mixed} means some nested settings are on: a short dash instead of the tick.
   */
  public static void checkAt(
      int x, int y, int size, float progress, boolean enabled, int accent, float mixed) {
    float visualSize = checkVisualSize(size);
    float inset = (size - visualSize) * 0.5f;
    // Only the painted square shrinks; callers retain the original hit area and center.
    drawCheckAt(x + inset, y + inset, visualSize, progress, enabled, accent, mixed);
  }

  static float checkVisualSize(int size) {
    return Math.max(0, size) * 0.85f;
  }

  private static void drawCheckAt(
      float x, float y, float size, float progress, boolean enabled, int accent, float mixed) {
    float p = ease(Math.max(0f, Math.min(1f, progress))), rr = Math.max(2.5f, size * 0.26f);
    int on = enabled ? accent : GRAY;
    int fillTop =
        enabled ? lerp(0xC4141C2A, alpha(lerp(on, 0xFFFFFFFF, 0.06f), 0.92f), p) : 0xC4131923;
    int fillBot = enabled ? lerp(0xC40F1620, alpha(darken(on, 0.72f), 0.92f), p) : 0xC40E141D;
    int edge = enabled ? lerp(0xFF46536B, on, p) : 0xFF2E3746;
    if (enabled && p > 0.03f) glow(x, y, x + size, y + size, rr, on, 0.26f * p);
    fillRound(x, y, x + size, y + size, rr, fillTop, fillBot);
    strokeRound(x, y, x + size, y + size, rr, edge, 1f);
    float m = Math.max(0f, Math.min(1f, mixed));
    if (m > 0.02f && p < 0.5f) {
      int bar = alpha(ACCENT, 0.9f * m);
      line(
          x + size * 0.28f,
          y + size * 0.5f,
          x + size * 0.72f,
          y + size * 0.5f,
          Math.max(1.6f, size * 0.14f),
          bar);
      strokeRound(x, y, x + size, y + size, rr, alpha(ACCENT, 0.75f * m), 1f);
      return;
    }
    if (p <= 0.05f) return;
    // The tick is drawn in two stages: first the short arm, then the long one, as in client
    // checkboxes.
    float t = Math.min(1f, p / 0.95f), w = Math.max(1.6f, size * 0.15f);
    float ax = x + size * 0.26f, ay = y + size * 0.52f;
    float bx = x + size * 0.44f, by = y + size * 0.70f;
    float cx = x + size * 0.76f, cy = y + size * 0.30f;
    int ink = enabled ? 0xFFFFFFFF : alpha(0xFFFFFFFF, 0.45f);
    float k1 = Math.min(1f, t / 0.4f);
    line(ax, ay, ax + (bx - ax) * k1, ay + (by - ay) * k1, w, ink);
    if (t > 0.4f) {
      float k2 = (t - 0.4f) / 0.6f;
      line(bx, by, bx + (cx - bx) * k2, by + (cy - by) * k2, w, ink);
    }
  }

  public static void switchAt(int x, int y, int w, int h, float progress, boolean enabled) {
    float p = ease(progress), rr = h * 0.5f;
    int track = enabled ? lerp(0xC41A2231, 0xC4245F4C, p) : 0xC4161C28;
    int edge = enabled ? lerp(0xFF4A566C, GREEN, p) : 0xFF2E3746;
    fillRound(x, y, x + w, y + h, rr, track, track);
    if (enabled && p > 0.02f)
      fillRound(
          x + 1,
          y + 1,
          x + 1 + Math.max(2, Math.round((w - 2) * p)),
          y + h - 1,
          Math.max(1.5f, rr - 1f),
          alpha(GREEN, 0.26f * p),
          alpha(GREEN, 0.16f * p));
    if (enabled && p > 0.02f) glow(x, y, x + w, y + h, rr, GREEN, 0.22f * p);
    strokeRound(x, y, x + w, y + h, rr, edge, 1f);
    int pad = 2, d = h - pad * 2;
    float kx = x + pad + (w - pad * 2 - d) * p;
    fillRound(kx + 1, y + pad + 1, kx + d + 1, y + pad + d + 1, d * 0.5f, 0x33000000, 0x33000000);
    fillRound(
        kx,
        y + pad,
        kx + d,
        y + pad + d,
        d * 0.5f,
        enabled ? lerp(0xFFE6EBF3, 0xFFFFFFFF, p) : 0xFF7E899E,
        enabled ? lerp(0xFFC9D3E2, 0xFFEDF2FA, p) : 0xFF6B7688);
  }

  public static void vgrad(int l, int t, int r, int b, int top, int bottom) {
    int h = b - t;
    if (h <= 0 || r <= l) return;
    beginDraw();
    GL11.glBegin(GL11.GL_QUADS);
    glColor(top);
    GL11.glVertex2f(l, t);
    GL11.glVertex2f(r, t);
    glColor(bottom);
    GL11.glVertex2f(r, b);
    GL11.glVertex2f(l, b);
    GL11.glEnd();
    endDraw();
  }

  public static void header(
      FontRenderer fr, int l, int t, int r, int headH, String title, String subtitle, int accent) {
    header(fr, l, t, r, headH, title, subtitle, accent, 0);
  }

  public static void header(
      FontRenderer fr,
      int l,
      int t,
      int r,
      int headH,
      String title,
      String subtitle,
      int accent,
      int reserveRight) {
    boolean two = subtitle != null && headH >= 26;
    int room = Math.max(24, r - l - 30 - Math.max(24, reserveRight)), fh = fr.FONT_HEIGHT;
    int titleY =
        two ? t + Math.max(2, (headH - (fh * 2 + 2)) / 2) : t + Math.max(0, (headH - fh) / 2);
    drawFitted(fr, "§l" + title, l + 15, titleY, room, TEXT);
    if (two) drawFitted(fr, subtitle, l + 15, titleY + fh + 2, room, TEXT_MUTE);
  }

  public static void rowCard(
      int l, int t, int r, int b, boolean hovered, boolean selected, int accent) {
    rowCard(l, t, r, b, hovered ? 1f : 0f, selected, accent, 1f);
  }
  /** List row card; hover 0..1: smooth appearance of the row highlight. */
  public static void rowCard(
      int l, int t, int r, int b, float hover, boolean selected, int accent) {
    rowCard(l, t, r, b, hover, selected, accent, 1f);
  }
  /** List row card; fade 0..1: staggered row appearance when the list opens. */
  public static void rowCard(
      int l, int t, int r, int b, float hover, boolean selected, int accent, float fade) {
    float fa = Math.max(0f, Math.min(1f, fade));
    if (fa <= 0.03f) return;
    float h = ease(Math.max(0f, Math.min(1f, hover)));
    float rr = rad(r - l, b - t);
    int fill = selected ? CARD_SEL : lerp(CARD, CARD_HOVER, h);
    if (selected) glow(l, t, r, b, rr, CARD_SELB, 0.20f * fa);
    else if (h > 0.02f) glow(l, t, r, b, rr, accent, 0.10f * h * fa);
    fillRound(
        l,
        t,
        r,
        b,
        rr,
        alpha(tint(fill, 0xFFFFFF, 0.09f), fa),
        alpha(tint(fill, 0x000000, 0.07f), fa));
    if (selected && b - t > 9) {
      float bar = 0.92f * fa;
      fillRound(l + 2, t + 3, l + 4, b - 3, 1f, alpha(CARD_SELB, bar), alpha(CARD_SELB, bar));
    }
    strokeRound(
        l,
        t,
        r,
        b,
        rr,
        alpha(selected ? CARD_SELB : lerp(BORDER, lerp(BORDER, accent, 0.55f), h), fa),
        1f);
  }
  /** The slot card has a single border: an inner strip would look like a second scrollbar. */
  public static void slotCard(
      int l, int t, int r, int b, boolean hovered, boolean selected, int accent) {
    slotCard(l, t, r, b, hovered ? 1f : 0f, selected, accent);
  }

  public static void slotCard(
      int l, int t, int r, int b, float hover, boolean selected, int accent) {
    float h = ease(Math.max(0f, Math.min(1f, hover)));
    float rr = rad(r - l, b - t);
    int fill = selected ? CARD_SEL : lerp(CARD, CARD_HOVER, h);
    if (selected) glow(l, t, r, b, rr, CARD_SELB, 0.20f);
    else if (h > 0.02f) glow(l, t, r, b, rr, accent, 0.10f * h);
    fillRound(l, t, r, b, rr, tint(fill, 0xFFFFFF, 0.09f), tint(fill, 0x000000, 0.07f));
    strokeRound(
        l, t, r, b, rr, selected ? CARD_SELB : lerp(BORDER, lerp(BORDER, accent, 0.55f), h), 1f);
  }
  /**
   * One geometry for labels and "pills": the same height, padding and optical centering everywhere.
   */
  public static final int PILL_H = 15, PILL_PAD = 7;
  /** Unified metric grid: margins, spacing and sizes of repeated elements. */
  public static final int PAD = 12,
      GAP = 6,
      BTN_H = 20,
      ROW_H = 28,
      ROW_GAP = 6,
      BTN_PAD = 10,
      SWITCH_W = 26,
      SWITCH_H = 13,
      /* Checkbox square: the main one and a smaller one for nested settings. */
      CHECK = 15,
      CHECK_SM = 13,
      ICON = 14,
      ICON_GAP = 7,
      NAV_W = 22,
      /* Gap between the inline counter and the toggle: the elements must not merge. */
      STEP_GAP = 10,
      /* Spacing between neighboring list categories. */
      GROUP_GAP = 16;

  public static int pillWidth(FontRenderer fr, String text) {
    // Include optical ink overhang as well as logical advance and symmetrical padding.
    return fr.getStringWidth(text) + PILL_PAD * 2 + 2;
  }

  public static int centerTextY(FontRenderer fr, int top, int bottom) {
    return Math.round((top + bottom - fr.FONT_HEIGHT) / 2f);
  }

  private static int pillTextY(FontRenderer fr, int y) {
    return centerTextY(fr, y, y + PILL_H);
  }

  public static int chip(FontRenderer fr, String text, int x, int y, int color) {
    int w = pillWidth(fr, text);
    fillRound(x, y, x + w, y + PILL_H, 6f, alpha(color, 0.16f), alpha(color, 0.16f));
    strokeRound(x, y, x + w, y + PILL_H, 6f, alpha(color, 0.5f), 1f);
    fr.drawString(text, x + PILL_PAD, pillTextY(fr, y), color);
    return w;
  }

  public static void badge(
      FontRenderer fr, String glyph, int x, int y, int size, int color, boolean active) {
    int fill = alpha(color, active ? 0.30f : 0.13f),
        text = active ? lerp(color, 0xFFFFFFFF, 0.32f) : alpha(color, 0.76f);
    float rr = rad(size, size);
    fillRound(x, y, x + size, y + size, rr, tint(fill, 0xFFFFFF, 0.12f), fill);
    strokeRound(x, y, x + size, y + size, rr, alpha(color, active ? 0.92f : 0.42f), 1f);
    if (isNumber(glyph)) {
      drawNumber(fr, glyph, x, y, x + size, y + size, text, false);
      return;
    }
    VectorIcons.draw(
        glyph, x + size / 2f, y + size / 2f, Math.max(0.78f, Math.min(1.15f, size / 14f)), text);
  }

  private static boolean isNumber(String s) {
    return s != null && s.length() <= 3 && allDigits(s);
  }

  static boolean allDigits(String s) {
    if (s == null || s.length() == 0) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') return false;
    }
    return true;
  }

  private static void drawNumber(
      FontRenderer fr, String value, int l, int t, int r, int b, int color, boolean rightAligned) {
    int raw = Math.max(1, fr.getStringWidth(value));
    int x = rightAligned ? r - 4 - raw : l + ((r - l - raw) / 2);
    int y = centerTextY(fr, t, b);
    fr.drawString(value, x, y, color);
  }

  public static void slotNumber(
      FontRenderer fr, String value, int l, int t, int r, int b, int color, boolean selected) {
    int fill = selected ? alpha(color, 0.20f) : 0x28101826;
    float rr = Math.min(5f, Math.min(r - l, b - t) * 0.5f);
    fillRound(l, t, r, b, rr, tint(fill, 0xFFFFFF, 0.08f), fill);
    strokeRound(l, t, r, b, rr, alpha(color, selected ? 0.70f : 0.28f), 1f);
    drawNumber(fr, value, l, t, r, b, selected ? TEXT : TEXT_DIM, false);
  }

  public static final int TABLE_HEAD = 14, TABLE_ROW = 12;

  public static int tableValueX(int l, int r) {
    int w = Math.max(60, r - l);
    int vx = l + Math.max(56, Math.min(92, w * 36 / 100));
    return Math.max(l + 48, Math.min(vx, r - 72));
  }
  /**
   * The value column aligns to the widest label, not to a fraction of the width: otherwise short
   * labels hang far from their values.
   */
  public static int tableValueX(FontRenderer fr, String[] labels, int l, int r) {
    if (fr == null) return tableValueX(l, r);
    int need = fr.getStringWidth(Lang.t("uiTheme.parameter"));
    if (labels != null)
      for (String s : labels) if (s != null) need = Math.max(need, fr.getStringWidth(s));
    return Math.max(l + 40, Math.min(l + need + GAP * 2, Math.max(l + 40, r - 60)));
  }

  public static int tableHeight(int rows) {
    return TABLE_HEAD + Math.max(0, rows) * TABLE_ROW;
  }
  // Slot table labels are constant: the arrays are created once, not every frame.
  private static final String[]
      INFO_RU = {"Тип", "Описание", "Кадры", "Длительность", "Размер", "Создано"},
      INFO_EN = {"Type", "Description", "Frames", "Duration", "Size", "Created"},
      INFO_UK = {"Тип", "Опис", "Кадри", "Тривалість", "Розмір", "Створено"},
      INFO_DE = {"Typ", "Beschreibung", "Frames", "Dauer", "Größe", "Erstellt"},
      INFO_PL = {"Typ", "Opis", "Klatki", "Czas", "Rozmiar", "Utworzono"};
  /** Slot card table with shared labels. The label arrays are not exposed. */
  public static int slotInfoTable(
      FontRenderer fr, String[] values, int l, int t, int r, int bottom) {
    return slotInfoTable(fr, values, l, t, r, bottom, true);
  }

  /** Compact label/value pairs for the selected card; the standalone slot table stays unchanged. */
  public static int slotInfoPairs(
      FontRenderer fr, String[] values, int l, int t, int r, int bottom) {
    return slotInfoTable(fr, values, l, t, r, bottom, false);
  }

  private static int slotInfoTable(
      FontRenderer fr, String[] values, int l, int t, int r, int bottom, boolean showHeaders) {
    int head = showHeaders ? TABLE_HEAD : 0;
    String[] labels = Lang.pick(INFO_RU, INFO_EN, INFO_UK, INFO_DE, INFO_PL);
    if (fr == null || r - l < 70 || bottom - t < head + TABLE_ROW) return 0;
    int vx = tableValueX(fr, labels, l, r);
    int labelW = Math.max(24, vx - l - GAP), valueW = Math.max(24, r - vx);
    int row = Math.max(TABLE_ROW, Math.min(16, (bottom - t - head) / labels.length));
    if (showHeaders) {
      drawFitted(fr, Lang.t("uiTheme.parameter"), l, t, labelW, TEXT_MUTE);
      drawFitted(fr, Lang.t("uiTheme.value"), vx, t, valueW, TEXT_MUTE);
    }
    int shown = 0;
    for (int i = 0; i < labels.length; i++) {
      int y = t + head + i * row;
      if (y + row - 2 > bottom) break;
      int textY = y + Math.max(0, (row - fr.FONT_HEIGHT) / 2 - 1);
      drawFitted(fr, labels[i], l, textY, labelW, TEXT_DIM);
      String value = values == null || i >= values.length || values[i] == null ? "" : values[i];
      drawFitted(fr, value, vx, textY, valueW, TEXT);
      shown++;
    }
    return head + shown * row;
  }

  private static final SimpleDateFormat DATE_FMT =
      new SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.ROOT);
  private static final Date DATE_TMP = new Date();
  private static final SimpleDateFormat DATE_SHORT =
      new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT);
  /** Each format has its own Date: a shared object would be mutated under two different locks. */
  private static final Date DATE_SHORT_TMP = new Date();
  /** Recording modification date in cards and tooltips. 0 or less gives "--". */
  public static String formatDate(long t) {
    if (t <= 0L) return "--";
    synchronized (DATE_FMT) {
      DATE_TMP.setTime(t);
      return DATE_FMT.format(DATE_TMP);
    }
  }
  /** Short date for list rows: the same shared format instead of creating one on every call. */
  public static String formatShortDate(long t) {
    if (t <= 0L) return "--";
    synchronized (DATE_SHORT) {
      DATE_SHORT_TMP.setTime(t);
      return DATE_SHORT.format(DATE_SHORT_TMP);
    }
  }

  public static int tableRowsFit(int avail) {
    return Math.max(0, (avail - TABLE_HEAD) / TABLE_ROW);
  }
  /** Flat table with a default bottom limit of t + 4096. */
  public static int infoTable(
      FontRenderer fr, String[] labels, String[] values, int l, int t, int r) {
    return infoTable(fr, labels, values, l, t, r, t + 4096);
  }

  public static int infoTable(
      FontRenderer fr, String[] labels, String[] values, int l, int t, int r, int bottom) {
    int n = labels == null ? 0 : labels.length;
    if (fr == null || n == 0 || r - l < 70 || t + TABLE_HEAD > bottom) return 0;
    int vx = tableValueX(fr, labels, l, r),
        labelW = Math.max(24, vx - l - GAP),
        valueW = Math.max(24, r - vx);
    drawFitted(fr, Lang.t("uiTheme.parameter"), l, t, labelW, 0xFF6F8AA8);
    drawFitted(fr, Lang.t("uiTheme.value"), vx, t, valueW, 0xFF6F8AA8);
    int shown = 0;
    for (int i = 0; i < n; i++) {
      int y = t + TABLE_HEAD + i * TABLE_ROW;
      if (y + TABLE_ROW - 2 > bottom) break;
      drawFitted(fr, labels[i] == null ? "" : labels[i], l, y, labelW, TEXT_DIM);
      drawFitted(
          fr,
          values == null || i >= values.length || values[i] == null ? "" : values[i],
          vx,
          y,
          valueW,
          TEXT);
      shown++;
    }
    return tableHeight(shown);
  }

  public static void tableHeader(FontRenderer fr, String left, String right, int l, int r, int y) {
    int vx = tableValueX(l, r);
    drawFitted(fr, left, l + 2, y, Math.max(24, vx - l - 8), 0xFF6F8AA8);
    drawFitted(fr, right, vx, y, Math.max(24, r - vx - 2), 0xFF6F8AA8);
  }

  public static void tableRow(FontRenderer fr, String label, String value, int l, int r, int y) {
    tableRow(fr, label, value, l, r, y, false);
  }

  public static void tableRow(
      FontRenderer fr, String label, String value, int l, int r, int y, boolean alt) {
    int vx = tableValueX(l, r);
    drawFitted(fr, label == null ? "" : label, l + 2, y, Math.max(24, vx - l - 8), TEXT_DIM);
    drawFitted(fr, value == null ? "" : value, vx, y, Math.max(24, r - vx - 2), TEXT);
  }

  public static int drawWrapped(
      FontRenderer fr, String text, int x, int y, int maxW, int maxLines, int color) {
    if (text == null || maxW <= 0 || maxLines <= 0) return 0;
    List<String> lines = fr.listFormattedStringToWidth(text, maxW);
    int n = Math.min(maxLines, lines.size()), yy = y;
    for (int i = 0; i < n; i++) {
      String line = lines.get(i);
      if (i == n - 1 && lines.size() > n) line = withEllipsis(fr, line, maxW);
      fr.drawString(line, x, yy, color);
      yy += 10;
    }
    resetColor();
    return n * 10;
  }

  public static boolean isControlSymbol(String s) {
    return "+".equals(s)
        || "-".equals(s)
        || "−".equals(s)
        || "<".equals(s)
        || ">".equals(s)
        || "‹".equals(s)
        || "›".equals(s)
        || "×".equals(s);
  }

  public static void controlSymbol(String glyph, float x, float y, float scale, int color) {
    VectorIcons.draw(glyph, x, y, scale, color);
  }

  static void line(float... p) {
    GL11.glBegin(GL11.GL_LINES);
    for (int i = 0; i + 1 < p.length; i += 2) GL11.glVertex2f(p[i], p[i + 1]);
    GL11.glEnd();
  }

  static void circle(float x, float y, float r, int n, boolean fill) {
    GL11.glBegin(fill ? GL11.GL_TRIANGLE_FAN : GL11.GL_LINE_LOOP);
    if (fill) GL11.glVertex2f(x, y);
    for (int i = 0; i <= (fill ? n : n - 1); i++) {
      double a = Math.PI * 2.0 * i / n;
      GL11.glVertex2f(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
    }
    GL11.glEnd();
  }

  /**
   * The 1.12.2 bitmap font does not scale: GlStateManager.scale overlaps glyphs and they double. So
   * text that does not fit is truncated with an ellipsis.
   */
  public static String ellipsize(FontRenderer fr, String text, int maxW) {
    if (text == null || maxW <= 0) return "";
    if (fr.getStringWidth(text) <= maxW) return text;
    return withEllipsis(fr, text, maxW);
  }
  /**
   * A prefix with "..." that fits entirely within maxW. The width is measured on the string
   * including the dots: bold (§l) carries over to them, and the sum of part widths is not the width
   * of the whole. The prefix never breaks at "§" or in the middle of a surrogate pair. §l/§r codes
   * make the width non-monotonic, so the binary search guarantees the result fits, but in rare
   * cases may take a couple of characters fewer.
   */
  private static String withEllipsis(FontRenderer fr, String text, int maxW) {
    String dots = "...";
    if (fr.getStringWidth(dots) > maxW) return "";
    int lo = 0, hi = text.length();
    while (lo < hi) {
      int mid = (lo + hi + 1) >>> 1;
      if (fr.getStringWidth(safePrefix(text, mid) + dots) <= maxW) lo = mid;
      else hi = mid - 1;
    }
    return safePrefix(text, lo) + dots;
  }

  private static String safePrefix(String text, int k) {
    while (k > 0) {
      char c = text.charAt(k - 1);
      if (c == '\u00a7' || Character.isHighSurrogate(c)) k--;
      else break;
    }
    return text.substring(0, k);
  }
  /**
   * Batched drawing of flat rectangles: one glBegin/glEnd pair instead of a Gui.drawRect for each.
   * GL state, vertex order and blending are exactly as in Gui.drawRect: alpha test, depth and face
   * culling are untouched, so nearly transparent cells are discarded the same way. Only rect() is
   * allowed between beginRects and endRects; call endRects in finally.
   */
  public static void beginRects() {
    GlStateManager.enableBlend();
    GlStateManager.disableTexture2D();
    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    GL11.glBegin(GL11.GL_QUADS);
  }

  public static void rect(int l, int t, int r, int b, int color) {
    if (r <= l || b <= t) return;
    glColor(color);
    GL11.glVertex2f(l, b);
    GL11.glVertex2f(r, b);
    GL11.glVertex2f(r, t);
    GL11.glVertex2f(l, t);
  }
  /** The color was set bypassing GlStateManager, so its cache is reset. */
  public static void endRects() {
    GL11.glEnd();
    GlStateManager.enableTexture2D();
    GlStateManager.disableBlend();
    resetColor();
  }

  public static float fitScale(FontRenderer fr, String text, int maxW) {
    return 1f;
  }

  public static int fittedWidth(FontRenderer fr, String text, int maxW) {
    return Math.max(0, Math.min(maxW, text == null ? 0 : fr.getStringWidth(text)));
  }

  public static void drawFitted(
      FontRenderer fr, String text, float x, float y, int maxW, int color) {
    drawFitted0(fr, text, x, y, maxW, color, false);
  }

  public static void drawFittedShadow(
      FontRenderer fr, String text, float x, float y, int maxW, int color) {
    drawFitted0(fr, text, x, y, maxW, color, true);
  }

  private static void drawFitted0(
      FontRenderer fr, String text, float x, float y, int maxW, int color, boolean shadow) {
    if (text == null || maxW <= 0) return;
    color = fade(color);
    if ((color >>> 24) < 6) return;
    UiReadingLine.draw(fr, text, Math.round(x), Math.round(y), maxW, color, shadow);
    resetColor();
  }

  public static void drawCenteredFitted(
      FontRenderer fr, String text, float centerX, float y, int maxW, int color, boolean shadow) {
    int fw = fittedWidth(fr, text, maxW);
    drawFitted0(fr, text, centerX - fw / 2f, y, maxW, color, shadow);
  }

  public static void tooltip(
      FontRenderer fr, String[] lines, int mx, int my, int screenW, int screenH) {
    tooltip(fr, lines, mx, my, screenW, screenH, tipFade());
  }
  /** Tooltip; fade 0..1. The measured background and its text remain on the same pixel grid. */
  public static void tooltip(
      FontRenderer fr, String[] lines, int mx, int my, int screenW, int screenH, float fade) {
    float fa = Math.max(0f, Math.min(1f, fade));
    if (fa <= 0.03f) return;
    if (lines == null || lines.length == 0 || !tipsEnabled()) return;
    UiTooltipLayout layout = new UiTooltipLayout(fr, lines, mx, my, screenW, screenH);
    if (layout.lines.isEmpty()) return;
    int bx = layout.x, by = layout.y;
    try (UiTooltipLayer layer = new UiTooltipLayer()) {
      layer.text();
      shadow(bx, by, bx + layout.width, by + layout.height, fa);
      // An opaque surface prevents the labels below the popup from showing through.
      layer.text();
      card(
          bx,
          by,
          bx + layout.width,
          by + layout.height,
          alpha(0xFF161F30, fa),
          alpha(BORDER_HI, fa));
      layer.text();
      int offset = UiTooltipScroll.show(lines, screenW, screenH, layout.maxScroll, nowMs());
      // These are complete, measured lines, not fitted controls. Only visible lines are drawn;
      // a second scissor/fitting pass can cut glyphs or inherit the hovered list's viewport.
      for (int i = 0; i < layout.visibleLines; i++) {
        UiTextBlock.Line line = layout.lines.get(offset + i);
        drawTextLine(
            fr,
            line.text,
            bx + UiTooltipLayout.PAD,
            by + UiTooltipLayout.PAD + i * layout.lineHeight,
            alpha(line.color, fa));
      }
      if (layout.maxScroll > 0) {
        String page =
            "↑ ↓  "
                + (offset + 1)
                + "–"
                + (offset + layout.visibleLines)
                + " / "
                + layout.lines.size();
        drawTextLine(
            fr,
            page,
            bx + UiTooltipLayout.PAD,
            by + layout.height - UiTooltipLayout.PAD - fr.FONT_HEIGHT,
            alpha(TEXT_DIM, fa));
      }
    }
  }

  /**
   * Draw a complete pre-wrapped line. Single-line controls retain their complete strings in reading
   * windows.
   */
  public static void drawTextLine(FontRenderer fr, String text, int x, int y, int color) {
    color = fade(color);
    if (text == null || (color >>> 24) < 6) return;
    fr.drawString(text, x, y, color);
    resetColor();
  }

  public static void buttonTooltip(
      FontRenderer fr, List<GuiButton> buttons, int mx, int my, int screenW, int screenH) {
    if (!tipsEnabled()) return;
    for (GuiButton g : buttons)
      if (g instanceof StyledButton) {
        StyledButton b = (StyledButton) g;
        if (b.visible
            && b.tooltip != null
            && mx >= b.x
            && my >= b.y
            && mx < b.x + b.width
            && my < b.y + b.height) {
          if (tipReady(b)) tooltip(fr, b.tooltip, mx, my, screenW, screenH);
          return;
        }
      }
  }

  private static boolean tipsEnabled() {
    try {
      MirrorRecorder m = MirrorRecorder.getInstance();
      return m == null || m.getConfig() == null || m.getConfig().isShowTooltips();
    } catch (Throwable ignored) {
      return true;
    }
  }

  public static void scrollBar(
      int x, int top, int bottom, int visible, int total, int offset, int color) {}

  public static void scrollBar(
      int x, int top, int bottom, int visible, int total, float offset, int color, float hover) {}

  public static void scrollBar(
      int x, int top, int bottom, int visible, int total, int offset, int color, float hover) {}
}

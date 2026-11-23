package com.mirror.recorder.gui;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Render-only HUD and cursor ornaments. No game input or playback state is changed here. */
public final class ReplayHudRenderer {
  private static final long TRACK_PERIOD_MS = 2400L;

  private ReplayHudRenderer() {}

  public static final class Data {
    public String title = "", context = "", meta = "", left = "", right = "", metric = "";
    public String icon = "play";
    public int color = UiTheme.BLUE;
    public float progress = -1f;
    public boolean paused, dragging;
  }

  public static void draw(
      FontRenderer font, Data d, ReplayHudLayout layout, int x, int y, long now, boolean animate) {
    boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    GlStateManager.depthMask(false);
    try {
      drawCard(font, d, layout, x, y, now, animate);
    } finally {
      GlStateManager.depthMask(depthMask);
      if (depth) GlStateManager.enableDepth();
      else GlStateManager.disableDepth();
    }
  }

  private static void drawCard(
      FontRenderer font, Data d, ReplayHudLayout layout, int x, int y, long now, boolean animate) {
    HudTextRaster.begin();
    int right = x + layout.width, bottom = y + layout.height;
    int accent = readableAccent(d.color);
    float pulse = animate && !d.paused ? UiTheme.pulse(1600L) : 0f;
    int titleY = y + 10 + Math.max(0, (20 - layout.titles.size() * ReplayHudLayout.LINE) / 2);
    UiFinish.hudShadow(x - 1, y + 3, right + 1, bottom + 3, 0x30000000);
    float glowAmt = d.dragging ? 0.34f : 0.12f + 0.05f * pulse;
    UiTheme.border(x - 1, y - 1, right + 1, bottom + 1, UiTheme.alpha(accent, 0.55f * glowAmt));
    UiTheme.border(x - 2, y - 2, right + 2, bottom + 2, UiTheme.alpha(accent, 0.24f * glowAmt));
    UiFinish.hudSurface(x, y, right, bottom, 0xE30B1422);
    UiFinish.hudHeader(x + 1, y + 1, right - 1, y + layout.contextY - 3, UiTheme.alpha(accent, 0.085f));
    UiTheme.border(x, y, right, bottom, UiTheme.alpha(accent, d.dragging ? 0.78f : 0.38f));
    int badgeAlpha = Math.round((0.16f + ("record".equals(d.icon) ? 0.12f * pulse : 0.04f * pulse)) * 255f);
    UiFinish.hudIconBadge(x + 12, y + 10, x + 30, y + 28, (badgeAlpha << 24) | (accent & 0x00FFFFFF));
    UiTheme.controlSymbol(d.icon, x + 21, y + 19, 1.7f, accent);
    // UiTheme primitives restore the menu baseline; live text must ignore the world depth buffer.
    GlStateManager.disableDepth();
    drawLines(font, layout.titles, x + 42, titleY, UiTheme.TEXT);
    if (!d.metric.isEmpty()) {
      int mw = ReplayHudLayout.scaledTextWidth(font, d.metric);
      int mx = right - 12 - mw;
      UiTheme.roundGradient(
          mx - 4,
          titleY - 2,
          right - 8,
          titleY + 11,
          UiTheme.alpha(accent, 0.18f),
          UiTheme.alpha(accent, 0.09f));
      UiTheme.border(mx - 4, titleY - 2, right - 8, titleY + 11, UiTheme.alpha(accent, 0.42f));
      GlStateManager.disableDepth();
      drawText(font, d.metric, mx, titleY, UiTheme.lerp(accent, 0xFFFFFFFF, 0.25f));
    }
    drawLines(font, layout.contexts, x + 12, y + layout.contextY, 0xFFE1E8F3);
    drawLines(font, layout.metadata, x + 12, y + layout.metaY, 0xFFB0C0D3);
    drawLines(font, layout.leftFooter, x + 12, y + layout.footerY, UiTheme.TEXT_DIM);
    int fy =
        y
            + layout.footerY
            + (layout.splitFooter ? layout.leftFooter.size() * ReplayHudLayout.LINE : 0);
    for (String line : layout.rightFooter) {
      int fx = layout.splitFooter ? x + 12 : right - 12 - ReplayHudLayout.scaledTextWidth(font, line);
      drawText(font, line, fx, fy, 0xFFB0C0D3);
      fy += ReplayHudLayout.LINE;
    }
    drawTrack(d, accent, x + 12, y + layout.trackY, layout.textWidth, now, animate);
  }

  /** Custom dark path colors must not turn small HUD text into unreadable dark-on-dark text. */
  public static int readableAccent(int color) {
    int result = 0xFF000000 | color;
    for (int i = 0; i < 12 && luminance(result) < 0.23d; i++)
      result = UiTheme.lerp(result, 0xFFFFFFFF, 0.12f);
    return result;
  }

  private static double luminance(int color) {
    return linear((color >> 16) & 255) * 0.2126d
        + linear((color >> 8) & 255) * 0.7152d
        + linear(color & 255) * 0.0722d;
  }

  private static double linear(int channel) {
    double value = channel / 255d;
    return value <= 0.04045d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
  }

  private static void drawLines(FontRenderer font, List<String> lines, int x, int y, int color) {
    for (String line : lines) {
      drawText(font, line, x, y, color);
      y += ReplayHudLayout.LINE;
    }
  }

  private static void drawText(FontRenderer font, String text, int x, int y, int color) {
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0f);
      GlStateManager.scale(ReplayHudLayout.TEXT_SCALE, ReplayHudLayout.TEXT_SCALE, 1f);
      HudTextRaster.align();
      UiTheme.drawTextLine(font, text, 0, 0, color);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  private static void drawTrack(
      Data d, int accent, int x, int y, int width, long now, boolean animate) {
    UiFinish.hudTrack(x, y, x + width, y + 5, 0xFF1C2B40);
    GlStateManager.disableDepth();
    for (int k = 1; k < 4; k++) {
      int mark = x + width * k / 4;
      Gui.drawRect(mark, y + 1, mark + 1, y + 4, 0x300B1422);
    }
    if (d.progress < 0f) {
      float phase =
          animate && !d.paused
              ? Math.floorMod(now, TRACK_PERIOD_MS) / (float) TRACK_PERIOD_MS
              : 0.5f;
      int pillW = Math.min(34, Math.max(18, width / 5));
      int head = Math.round((width - pillW) * (0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2)));
      UiFinish.hudTrackPulse(x + head, y, x + head + pillW, y + 5, UiTheme.alpha(accent, 0.85f));
      return;
    }
    float p = Math.max(0f, Math.min(1f, d.progress));
    int fill = Math.round(width * p);
    if (fill <= 0) return;
    if (fill >= 4) UiFinish.hudTrackFill(x, y, x + fill, y + 5, accent);
    else UiTheme.round(x, y, x + Math.max(2, fill), y + 5, accent);
    if (fill > 5)
      UiTheme.line(
          x + 2.5f,
          y + 1.1f,
          x + fill - 2.5f,
          y + 1.1f,
          0.75f,
          UiTheme.lerp(accent, 0xFFFFFFFF, 0.36f));
    if (animate && !d.paused && fill > 24) {
      float sp = Math.floorMod(now, 1800L) / 1800f;
      int sx = x + 3 + Math.round(Math.max(0, fill - 22) * sp);
      int ex = Math.min(x + fill - 2, sx + 16);
      if (ex > sx + 2) UiTheme.roundGradient(sx, y + 1, ex, y + 4, 0x26FFFFFF, 0x0CFFFFFF);
    }
    if (p > 0.02f && p < 0.99f && fill >= 4) {
      float tipX = x + fill - 1.5f;
      UiTheme.smoothDisk(tipX, y + 2.5f, 2.2f, UiTheme.lerp(accent, 0xFFFFFFFF, 0.65f));
    }
    GlStateManager.disableDepth();
  }

  /** The system cursor stays unique; driven playback gets only a hollow halo and click feedback. */
  public static void pointer(float x, float y, boolean driven, float clickAge, boolean animate) {
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ZERO);
    GlStateManager.disableTexture2D();
    GL11.glLineWidth(1.3f);
    circle(x, y, driven ? 7f : 6f, 0.65f, 0.84f, 1f, 0.75f);
    if (!driven) {
      Gui.drawRect((int) x - 2, (int) y - 2, (int) x + 3, (int) y + 3, 0xFF0B1422);
      Gui.drawRect((int) x - 1, (int) y - 1, (int) x + 2, (int) y + 2, 0xFFF2F8FF);
    }
    if (animate && clickAge >= 0f && clickAge < 1f)
      circle(x, y, 7f + clickAge * 14f, 0.45f, 0.85f, 1f, (1f - clickAge) * 0.75f);
    GL11.glLineWidth(1f);
    GlStateManager.enableTexture2D();
    GlStateManager.color(1f, 1f, 1f, 1f);
  }

  private static void circle(float x, float y, float radius, float r, float g, float b, float a) {
    GlStateManager.disableTexture2D();
    GlStateManager.color(r, g, b, a);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    for (int i = 0; i < 40; i++) {
      double angle = Math.PI * 2 * i / 40;
      GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
    }
    GL11.glEnd();
  }
}

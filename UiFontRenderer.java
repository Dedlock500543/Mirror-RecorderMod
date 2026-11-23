package com.mirror.recorder.gui;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Smooth mod-only glyphs; vanilla retains formatting, wrapping, selection and fallback Unicode. */
final class UiFontRenderer extends FontRenderer {
  private static final ResourceLocation[] PAGES = pages("ui");
  private static final ResourceLocation[] WORLD_PAGES = pages("world");
  private final ResourceLocation[] atlasPages;
  private byte[] fallbackWidths;

  UiFontRenderer(Minecraft mc) {
    this(mc, false);
  }

  UiFontRenderer(Minecraft mc, boolean worldLabel) {
    super(
        mc.gameSettings,
        new ResourceLocation("textures/font/ascii.png"),
        mc.getTextureManager(),
        true);
    atlasPages = worldLabel ? WORLD_PAGES : PAGES;
    FONT_HEIGHT = UiFontMetrics.LINE_HEIGHT;
    fallbackWidths = readFallback(mc.getResourceManager());
  }

  private static ResourceLocation[] pages(String prefix) {
    ResourceLocation[] pages = new ResourceLocation[256];
    for (int i = 0; i < pages.length; i++)
      pages[i] =
          new ResourceLocation("mirror_recorder", String.format("fonts/%s_%02x.png", prefix, i));
    return pages;
  }

  @Override
  public void onResourceManagerReload(IResourceManager manager) {
    super.onResourceManagerReload(manager);
    FONT_HEIGHT = UiFontMetrics.LINE_HEIGHT;
    fallbackWidths = readFallback(manager);
  }

  @Override
  public int getCharWidth(char ch) {
    if (ch == '\u00a7') return -1;
    if (ch == '\n' || ch == '\r') return 0;
    int width = UiFontMetrics.width(ch);
    if (width > 0) return width;
    if (fallbackWidths == null) fallbackWidths = readFallback(null);
    int packed = fallbackWidths[ch] & 255;
    if (packed == 0) return 0;
    int left = packed >>> 4, right = packed & 15;
    if (right > 7) {
      left = 0;
      right = 15;
    }
    return (right + 1 - left) / 2 + 1;
  }

  // OptiFine adds this float-width entry point and uses it directly for measuring and wrapping.
  // It deliberately has no @Override: vanilla 1.12.2 does not declare it. Both paths must use
  // our atlas advances, not OptiFine's independently loaded vanilla font metrics.
  public float getCharWidthFloat(char ch) {
    return getCharWidth(ch);
  }

  @Override
  public int getStringWidth(String text) {
    if (text == null) return 0;
    int width = 0;
    boolean bold = false;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '§' && i + 1 < text.length()) {
        char code = Character.toLowerCase(text.charAt(++i));
        if (code == 'l') bold = true;
        else if ("0123456789abcdefr".indexOf(code) >= 0) bold = false;
        continue;
      }
      int advance = Math.max(0, getCharWidth(ch));
      width += advance + (bold && advance > 0 ? 1 : 0);
    }
    return width;
  }

  @Override
  public List<String> listFormattedStringToWidth(String text, int width) {
    return UiFontWrapping.wrap(this, text, Math.max(1, width));
  }

  private static byte[] readFallback(IResourceManager resources) {
    byte[] widths = new byte[65536];
    try (InputStream stream =
            resources == null
                ? UiFontRenderer.class.getResourceAsStream("/assets/minecraft/font/glyph_sizes.bin")
                : resources
                    .getResource(new ResourceLocation("font/glyph_sizes.bin"))
                    .getInputStream();
        DataInputStream data = new DataInputStream(stream)) {
      data.readFully(widths);
      return widths;
    } catch (IOException | NullPointerException ex) {
      throw new IllegalStateException("Missing Minecraft Unicode font metrics", ex);
    }
  }

  @Override
  protected float renderUnicodeChar(char ch, boolean italic) {
    int width = UiFontMetrics.width(ch);
    if (width == 0) return super.renderUnicodeChar(ch, italic);
    bindTexture(atlasPages[ch >>> 8]);
    float u = (ch & 15) / 16f, v = ((ch & 255) >>> 4) / 16f;
    float left = posX - 1f, top = posY, skew = italic ? 1f : 0f, cell = 1f / 16f;
    GlStateManager.glBegin(GL11.GL_QUADS);
    GlStateManager.glTexCoord2f(u, v);
    GlStateManager.glVertex3f(left + skew, top, 0f);
    GlStateManager.glTexCoord2f(u, v + cell);
    GlStateManager.glVertex3f(left - skew, top + 16f, 0f);
    GlStateManager.glTexCoord2f(u + cell, v + cell);
    GlStateManager.glVertex3f(left + 16f - skew, top + 16f, 0f);
    GlStateManager.glTexCoord2f(u + cell, v);
    GlStateManager.glVertex3f(left + 16f + skew, top, 0f);
    GlStateManager.glEnd();
    return width;
  }

  @Override
  public int drawString(String text, float x, float y, int color, boolean shadow) {
    // The mod font always draws its own atlas, even if a font extension changed this flag.
    setUnicodeFlag(true);
    boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    GlStateManager.enableBlend();
    GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    try {
      return super.drawString(text, x, y, color, shadow);
    } finally {
      GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
      if (!blend) GlStateManager.disableBlend();
    }
  }
}

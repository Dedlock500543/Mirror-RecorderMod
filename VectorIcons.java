package com.mirror.recorder.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Icon entry point backed by a unified 128x128 pure-white RGBA texture set with mipmaps. Existing
 * callers retain their original centers, scales, colors and input geometry; Minecraft owns texture
 * caching and resource reloads.
 */
final class VectorIcons {
  private VectorIcons() {}

  private static final Map<String, ResourceLocation> ICONS;
  private static final ResourceLocation UNKNOWN = texture("unknown");

  static {
    Map<String, ResourceLocation> icons = new HashMap<>();
    add(icons, "record", "\u25cf");
    add(icons, "play", "\u25b6");
    add(icons, "next", ">", "\u203a");
    add(icons, "previous", "<", "\u2039");
    add(icons, "edit", "\u270e", "edit");
    add(icons, "copy", "copy", "\u2398");
    add(icons, "reload", "\u21bb", "~");
    add(icons, "stop", "\u25a0");
    add(icons, "close", "\u00d7");
    add(icons, "minus", "\u2212", "-");
    add(icons, "plus", "+");
    add(icons, "up", "\u2191", "\u25b4", "\u2196");
    add(icons, "down", "\u2193", "\u25be");
    add(icons, "settings", "\u2699");
    add(icons, "mail", "\u2709");
    add(icons, "flag", "\u2691");
    add(icons, "waves", "\u2248");
    add(icons, "loop", "\u221e");
    add(icons, "circle", "\u25cb");
    add(icons, "fast_forward", "\u00bb");
    add(icons, "spark", "\u2726", "*");
    add(icons, "swords", "\u2694");
    add(icons, "timer", "\u23f1");
    add(icons, "help", "?");
    add(icons, "music", "\u266b");
    add(icons, "text");
    add(icons, "search", "search");
    add(icons, "folder", "folder");
    add(icons, "folder_outline");
    add(icons, "folder_add_outline");
    add(icons, "trash", "trash");
    add(icons, "language");
    add(icons, "mouse");
    add(icons, "keyboard");
    add(icons, "eye");
    add(icons, "opacity");
    add(icons, "distance");
    add(icons, "reset");
    add(icons, "disconnect");
    add(icons, "exit");
    add(icons, "speed_reset");
    add(icons, "unknown");
    // Semantic 37-icon expansion
    add(icons, "tab_stop");
    add(icons, "tab_visuals");
    add(icons, "route_drift");
    add(icons, "heart_hurt");
    add(icons, "steps");
    add(icons, "jump");
    add(icons, "sneak");
    add(icons, "command");
    add(icons, "chat");
    add(icons, "chat_typing");
    add(icons, "look_rotate");
    add(icons, "speed");
    add(icons, "skip_delay");
    add(icons, "return_start");
    add(icons, "target");
    add(icons, "hourglass");
    add(icons, "stabilize");
    add(icons, "route");
    add(icons, "route_style");
    add(icons, "palette");
    add(icons, "color_custom");
    add(icons, "height");
    add(icons, "path_length");
    add(icons, "line_width", "\u2500");
    add(icons, "point_size");
    add(icons, "point_step", "\u00b7");
    add(icons, "through_walls", "\u25a1");
    add(icons, "beacon");
    add(icons, "pulse");
    add(icons, "label_tag", "T");
    add(icons, "mod_message");
    add(icons, "sound");
    add(icons, "auto_clear");
    add(icons, "trash_restore");
    add(icons, "export");
    add(icons, "import");
    add(icons, "pause");
    ICONS = Collections.unmodifiableMap(icons);
  }

  private static ResourceLocation texture(String name) {
    return new ResourceLocation("mirror_recorder", "textures/icons/" + name + ".png");
  }

  private static void add(Map<String, ResourceLocation> icons, String name, String... aliases) {
    ResourceLocation resource = texture(name);
    icons.put(name, resource);
    for (String alias : aliases) icons.put(alias, resource);
  }

  /** Numeric badges keep the existing number renderer; unknown icons use the supplied diamond. */
  static ResourceLocation resourceFor(String glyph) {
    if (UiTheme.allDigits(glyph)) return null;
    ResourceLocation resource = ICONS.get(glyph);
    return resource == null ? UNKNOWN : resource;
  }

  static void draw(String glyph, float x, float y, float scale, int color) {
    drawRotated(glyph, x, y, scale, color, "\u2196".equals(glyph) ? -45f : 0f);
  }

  /** Rotates only texture vertices around their existing icon center, never the button. */
  static void drawRotated(String glyph, float x, float y, float scale, int color, float angle) {
    ResourceLocation resource = resourceFor(glyph);
    if (resource == null || scale <= 0f) return;
    float half = 4f * scale;
    double radians = Math.toRadians(angle);
    float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
    UiTheme.beginDraw();
    try {
      GlStateManager.enableTexture2D();
      Minecraft.getMinecraft().getTextureManager().bindTexture(resource);
      SharpIconFilter.apply();
      UiTheme.glColor(color);
      Tessellator tessellator = Tessellator.getInstance();
      BufferBuilder buffer = tessellator.getBuffer();
      buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      vertex(buffer, x, y, -half, half, cos, sin, 0, 1);
      vertex(buffer, x, y, half, half, cos, sin, 1, 1);
      vertex(buffer, x, y, half, -half, cos, sin, 1, 0);
      vertex(buffer, x, y, -half, -half, cos, sin, 0, 0);
      tessellator.draw();
    } finally {
      UiTheme.endDraw();
    }
  }

  private static void vertex(
      BufferBuilder buffer,
      float x,
      float y,
      float dx,
      float dy,
      float cos,
      float sin,
      int u,
      int v) {
    buffer.pos(x + dx * cos - dy * sin, y + dx * sin + dy * cos, 0).tex(u, v).endVertex();
  }
}

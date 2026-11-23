package com.mirror.recorder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Thin outline folder symbols for the recording list, without changing existing icon artwork. */
final class FolderIcons {
  private static final ResourceLocation FOLDER =
      new ResourceLocation("mirror_recorder", "textures/icons/folder_outline.png");
  private static final ResourceLocation FOLDER_ADD =
      new ResourceLocation("mirror_recorder", "textures/icons/folder_add_outline.png");

  private static final ResourceLocation SPEED_RESET =
      new ResourceLocation("mirror_recorder", "textures/icons/speed_reset.png");

  private FolderIcons() {}

  static void draw(boolean add, float x, float y, float scale, int color) {
    drawTexture(add ? FOLDER_ADD : FOLDER, x, y, scale, color);
  }

  static void drawSpeedReset(float x, float y, float scale, int color) {
    drawTexture(SPEED_RESET, x, y, scale, color);
  }

  private static void drawTexture(ResourceLocation icon, float x, float y, float scale, int color) {
    float half = 4f * scale;
    UiTheme.beginDraw();
    try {
      GlStateManager.enableTexture2D();
      Minecraft.getMinecraft().getTextureManager().bindTexture(icon);
      SharpIconFilter.apply();
      UiTheme.glColor(color);
      Tessellator tessellator = Tessellator.getInstance();
      BufferBuilder buffer = tessellator.getBuffer();
      buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      buffer.pos(x - half, y + half, 0).tex(0, 1).endVertex();
      buffer.pos(x + half, y + half, 0).tex(1, 1).endVertex();
      buffer.pos(x + half, y - half, 0).tex(1, 0).endVertex();
      buffer.pos(x - half, y - half, 0).tex(0, 0).endVertex();
      tessellator.draw();
    } finally {
      UiTheme.endDraw();
    }
  }
}

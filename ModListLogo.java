package com.mirror.recorder.gui;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.GuiScrollingList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Places our mod-list logo left of Forge's default centre, without affecting other mods. */
@Mod.EventBusSubscriber(modid = "mirror_recorder", value = Side.CLIENT)
public final class ModListLogo {
  private static final ResourceLocation LOGO =
      new ResourceLocation("mirror_recorder", "textures/gui/mod_list_logo.png");
  private static Field selectedField;
  private static Field infoField;
  private static Field sidebarField;
  private static Field scrollField;
  private static boolean lookupDone;

  private ModListLogo() {}

  private static void lookupFields() {
    if (lookupDone) return;
    lookupDone = true;
    try {
      selectedField = GuiModList.class.getDeclaredField("selectedMod");
      infoField = GuiModList.class.getDeclaredField("modInfo");
      sidebarField = GuiModList.class.getDeclaredField("listWidth");
      scrollField = GuiScrollingList.class.getDeclaredField("scrollDistance");
      for (Field f : new Field[] {selectedField, infoField, sidebarField, scrollField})
        f.setAccessible(true);
    } catch (ReflectiveOperationException | SecurityException ex) {
      selectedField = null;
    }
  }

  static int leftPosition(int panelLeft, int panelWidth, int logoWidth) {
    int desired = Math.round(panelWidth * 0.30f - logoWidth * 0.5f);
    return panelLeft + Math.max(0, Math.min(Math.max(0, panelWidth - logoWidth), desired));
  }

  @SubscribeEvent
  public static void onModListDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
    GuiScreen screen = event.getGui();
    if (!(screen instanceof GuiModList)) return;
    lookupFields();
    if (selectedField == null) return;
    try {
      ModContainer selected = (ModContainer) selectedField.get(screen);
      Object info = infoField.get(screen);
      if (selected == null || !"mirror_recorder".equals(selected.getModId()) || info == null)
        return;
      int sidebar = sidebarField.getInt(screen);
      int panelLeft = sidebar + 20;
      int panelWidth = screen.width - sidebar - 30;
      int top = 32;
      int bottom = screen.height - 84;
      int width = Math.min(200, Math.max(0, panelWidth - 12));
      int height = Math.round(width * 65f / 200f);
      int x = leftPosition(panelLeft, panelWidth, width);
      int y = top + 4 - (int) scrollField.getFloat(info);
      if (width <= 0 || height <= 0 || bottom <= top || y >= bottom || y + height <= top) return;
      draw(screen, panelLeft, panelWidth, top, bottom, x, y, width, height);
    } catch (IllegalAccessException | ClassCastException ex) {
      // Leave the rest of Forge's mod list operational if the GUI has been replaced.
    }
  }

  private static void draw(
      GuiScreen screen, int left, int panelWidth, int top, int bottom, int x, int y, int w, int h) {
    Minecraft mc = Minecraft.getMinecraft();
    ScaledResolution resolution = new ScaledResolution(mc);
    double sx = mc.displayWidth / resolution.getScaledWidth_double();
    double sy = mc.displayHeight / resolution.getScaledHeight_double();
    int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    boolean hadScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    IntBuffer oldScissor = BufferUtils.createIntBuffer(4);
    if (hadScissor) GL11.glGetInteger(GL11.GL_SCISSOR_BOX, oldScissor);
    GL11.glPushAttrib(
        GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
    try {
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      int clipX = (int) (left * sx);
      int clipY = (int) (mc.displayHeight - bottom * sy);
      int clipW = (int) (panelWidth * sx);
      int clipH = (int) ((bottom - top) * sy);
      if (hadScissor) {
        int x0 = Math.max(clipX, oldScissor.get(0));
        int y0 = Math.max(clipY, oldScissor.get(1));
        int x1 = Math.min(clipX + clipW, oldScissor.get(0) + oldScissor.get(2));
        int y1 = Math.min(clipY + clipH, oldScissor.get(1) + oldScissor.get(3));
        clipX = x0;
        clipY = y0;
        clipW = Math.max(0, x1 - x0);
        clipH = Math.max(0, y1 - y0);
      }
      GL11.glScissor(clipX, clipY, clipW, clipH);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glColor4f(1f, 1f, 1f, 1f);
      mc.getTextureManager().bindTexture(LOGO);
      SharpIconFilter.apply();
      Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, w, h);
    } finally {
      GlStateManager.bindTexture(oldTexture);
      GL11.glPopAttrib();
    }
  }
}

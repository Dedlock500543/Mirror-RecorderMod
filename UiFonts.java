package com.mirror.recorder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/** One font instance; installed before initGui so labels and input fields share exact metrics. */
public final class UiFonts {
  private static FontRenderer font;
  private static FontRenderer worldFont;

  public static FontRenderer get(Minecraft mc) {
    if (font == null) {
      UiFontRenderer renderer = new UiFontRenderer(mc);
      IResourceManager resources = mc.getResourceManager();
      if (resources instanceof IReloadableResourceManager)
        ((IReloadableResourceManager) resources).registerReloadListener(renderer);
      font = renderer;
    }
    return font;
  }

  /** World labels are magnified by perspective; their atlas must not change menu/HUD glyphs. */
  public static FontRenderer getWorld(Minecraft mc) {
    if (worldFont == null) {
      UiFontRenderer renderer = new UiFontRenderer(mc, true);
      IResourceManager resources = mc.getResourceManager();
      if (resources instanceof IReloadableResourceManager)
        ((IReloadableResourceManager) resources).registerReloadListener(renderer);
      worldFont = renderer;
    }
    return worldFont;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onInit(GuiScreenEvent.InitGuiEvent.Pre event) {
    if (event.getGui() instanceof ModScreen) ((ModScreen) event.getGui()).installUiFont();
    UiTooltipScroll.forScreen(event.getGui());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onTooltipScroll(GuiScreenEvent.MouseInputEvent.Pre event) {
    UiTooltipScroll.forScreen(event.getGui());
    if (event.getGui() instanceof ModScreen
        && UiTooltipScroll.wheel(Mouse.getEventDWheel(), UiTheme.nowMs())) event.setCanceled(true);
  }
}

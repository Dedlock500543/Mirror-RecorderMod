package com.mirror.recorder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Topmost visual-only feedback for accepted clicks in this mod's screens. */
public final class UiBubbles {
  private static final UiBubbleModel MODEL = new UiBubbleModel();
  private int width = -1, height = -1;

  public static void burst(int x, int y, int accent) {
    MODEL.burst(
        x,
        y,
        UiTheme.lerp(accent, UiTheme.TEXT, 0.35f),
        UiTheme.nowMs(),
        UiTheme.animationsEnabled());
  }

  private static boolean ours(GuiScreen screen) {
    return screen != null && screen.getClass().getName().startsWith("com.mirror.recorder.gui.");
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onOpen(GuiOpenEvent event) {
    if (!event.isCanceled() && !ours(event.getGui())) MODEL.clear();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
    GuiScreen screen = event.getGui();
    if (screen != Minecraft.getMinecraft().currentScreen || !ours(screen)) return;
    if (!UiTheme.animationsEnabled()) {
      MODEL.clear();
      return;
    }
    if (width != screen.width || height != screen.height) {
      if (width >= 0) MODEL.clear();
      width = screen.width;
      height = screen.height;
    }
    long now = UiTheme.nowMs();
    MODEL.prune(now);
    for (UiBubbleModel.Particle p : MODEL.particles()) {
      float radius = p.radius * (1f + 0.35f * p.progress(now));
      float x = Math.max(radius + 1f, Math.min(width - radius - 1f, p.drawX(now)));
      float y = Math.max(radius + 1f, Math.min(height - radius - 1f, p.drawY(now)));
      float alpha = p.opacity(now);
      UiTheme.smoothDisk(x, y, radius, UiTheme.alpha(p.color, alpha * 0.12f));
      UiTheme.smoothRing(x, y, radius, 0.55f, UiTheme.alpha(p.color, alpha));
      UiTheme.smoothDisk(
          x - radius * 0.3f, y - radius * 0.35f, 0.35f, UiTheme.alpha(UiTheme.TEXT, alpha * 0.65f));
    }
  }
}

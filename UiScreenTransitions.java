package com.mirror.recorder.gui;

import com.mirror.recorder.debug.MirrorDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * GPU-only crossfade of the previous frame. Screen lifecycle and clicks change immediately; this
 * overlay never owns input, calls old screens, or delays save/cancel/mouse release.
 */
public final class UiScreenTransitions implements IResourceManagerReloadListener {
  private final Minecraft mc = Minecraft.getMinecraft();
  private int texture = -1, captureWidth, captureHeight;
  private long started = -1L;
  private GuiScreen destination;

  private static boolean ours(GuiScreen screen) {
    return screen != null && screen.getClass().getName().startsWith("com.mirror.recorder.gui.");
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onOpen(GuiOpenEvent event) {
    if (event.isCanceled() || !mc.isCallingFromMinecraftThread()) return;
    GuiScreen next = event.getGui();
    if (next == mc.currentScreen || (!ours(next) && !ours(mc.currentScreen))) return;
    started = -1L;
    if (!UiTheme.animationsEnabled()
        || !ScreenTransitionPolicy.validCapture(mc.displayWidth, mc.displayHeight)) return;
    // There is no previous frame to copy during initialization/after a destroyed GL context.
    if (mc.getFramebuffer() == null
        || mc.getFramebuffer().framebufferWidth != mc.displayWidth
        || mc.getFramebuffer().framebufferHeight != mc.displayHeight) return;
    int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    try {
      if (texture < 0 || captureWidth != mc.displayWidth || captureHeight != mc.displayHeight) {
        releaseTexture();
        texture = TextureUtil.glGenTextures();
        captureWidth = mc.displayWidth;
        captureHeight = mc.displayHeight;
        GlStateManager.bindTexture(texture);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA8,
            captureWidth,
            captureHeight,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
      } else GlStateManager.bindTexture(texture);
      GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, captureWidth, captureHeight);
      destination = next;
      started = UiTheme.nowMs();
    } catch (RuntimeException ex) {
      started = -1L;
      MirrorDebug.log("UI", "screen transition capture unavailable: " + ex);
    } finally {
      GlStateManager.bindTexture(previous);
      GlStateManager.setActiveTexture(active);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
    if (event.getGui() == mc.currentScreen) draw(event.getGui().width, event.getGui().height);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onOverlay(RenderGameOverlayEvent.Post event) {
    if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && mc.currentScreen == null)
      draw(event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
  }

  private void draw(int width, int height) {
    if (started < 0L || texture < 0) return;
    if (mc.currentScreen != destination
        || captureWidth != mc.displayWidth
        || captureHeight != mc.displayHeight) {
      started = -1L;
      return;
    }
    float alpha =
        ScreenTransitionPolicy.outgoingOpacity(
            started, UiTheme.nowMs(), UiTheme.animationsEnabled());
    if (alpha <= 0f) {
      started = -1L;
      destination = null;
      return;
    }
    int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    try {
      // Raw state is restored by glPopAttrib; Minecraft's cached state remains unchanged.
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glDisable(GL11.GL_ALPHA_TEST);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glDisable(GL11.GL_CULL_FACE);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glColor4f(1f, 1f, 1f, alpha);
      GlStateManager.bindTexture(texture);
      BufferBuilder buffer = Tessellator.getInstance().getBuffer();
      buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
      buffer.pos(0, height, 0).tex(0, 0).endVertex();
      buffer.pos(width, height, 0).tex(1, 0).endVertex();
      buffer.pos(width, 0, 0).tex(1, 1).endVertex();
      buffer.pos(0, 0, 0).tex(0, 1).endVertex();
      Tessellator.getInstance().draw();
    } finally {
      GL11.glPopAttrib();
      GlStateManager.bindTexture(previous);
      GlStateManager.setActiveTexture(active);
    }
  }

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END && !UiTheme.animationsEnabled() && texture >= 0) clear();
  }

  @SubscribeEvent
  public void onUnload(WorldEvent.Unload event) {
    if (!event.getWorld().isRemote) return;
    if (mc.isCallingFromMinecraftThread()) clear();
    else mc.addScheduledTask(this::clear);
  }

  @Override
  public void onResourceManagerReload(IResourceManager resources) {
    clear();
  }

  private void releaseTexture() {
    if (texture >= 0) TextureUtil.deleteTexture(texture);
    texture = -1;
  }

  private void clear() {
    started = -1L;
    destination = null;
    releaseTexture();
    captureWidth = captureHeight = 0;
  }
}

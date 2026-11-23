package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.ReplayState.windowActive;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.ReplayHudRenderer;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Playback cursor: path from the recording, driving the real cursor and the drawn pointer. */
@SideOnly(Side.CLIENT)
final class PointerFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  PointerFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
  }

  /** Whether the playback pointer is active. */
  private boolean pointerOn = false;

  private float pointerX = 0f, pointerY = 0f;
  private float pointerFromX = 0f, pointerFromY = 0f, pointerToX = 0f, pointerToY = 0f;
  /**
   * Time for the drawn cursor to reach the frame's new point: usually one tick (50 ms), within
   * 25–200 ms.
   */
  private static final long FRAME_GLIDE_DEFAULT_NS = 50_000_000L,
      FRAME_GLIDE_MIN_NS = 25_000_000L,
      FRAME_GLIDE_MAX_NS = 200_000_000L;
  /** Glide duration for drag points: 33–120 ms, based on the time between playback updates. */
  private static final long SLIDER_GLIDE_MIN_NS = 33_000_000L, SLIDER_GLIDE_MAX_NS = 120_000_000L;

  private long pointerStartNs = 0L, pointerDurNs = FRAME_GLIDE_DEFAULT_NS, pointerLastNs = 0L;
  private long visualClickNs;
  private static final long VISUAL_CLICK_NS = 360000000L;

  private boolean cursorOn() {
    return mc.currentScreen != null;
  }
  /** Recording cursor in UI pixels (X here, Y below): scaled from the display coordinates. */
  private float cursorPxX() {
    try {
      ScaledResolution r = new ScaledResolution(mc);
      return (float) (Mouse.getX() * r.getScaledWidth() / (double) Math.max(1, mc.displayWidth));
    } catch (Exception e) {
      return 0f;
    }
  }

  private float cursorPxY() {
    try {
      ScaledResolution r = new ScaledResolution(mc);
      return (float)
          (r.getScaledHeight()
              - Mouse.getY() * r.getScaledHeight() / (double) Math.max(1, mc.displayHeight)
              - 1);
    } catch (Exception e) {
      return 0f;
    }
  }

  void setPointer(Frame frame) {
    if (frame == null || !frame.hasCursor) {
      pointerOn = false;
      pointerScreen = null;
      return;
    }
    // The screen from the recording, not the current one: otherwise the filter accepts any open
    // window, including the mod menu.
    pointerScreen = frame.openScreen;
    long now = System.nanoTime();
    float px, py;
    if (!pointerOn) {
      px = frame.curX;
      py = frame.curY;
      pointerLastNs = 0L;
    } else {
      px = pointerLerpX();
      py = pointerLerpY();
    }
    long dt = pointerLastNs == 0L ? FRAME_GLIDE_DEFAULT_NS : now - pointerLastNs;
    if (dt < FRAME_GLIDE_MIN_NS) dt = FRAME_GLIDE_MIN_NS;
    if (dt > FRAME_GLIDE_MAX_NS) dt = FRAME_GLIDE_MAX_NS;
    pointerFromX = px;
    pointerFromY = py;
    pointerToX = frame.curX;
    pointerToY = frame.curY;
    pointerStartNs = now;
    pointerDurNs = dt;
    pointerLastNs = now;
    pointerX = frame.curX;
    pointerY = frame.curY;
    pointerOn = true;
  }

  private float pointerT() {
    if (pointerDurNs <= 0L) return 1f;
    float t = (float) ((System.nanoTime() - pointerStartNs) / (double) pointerDurNs);
    return t < 0f ? 0f : (t > 1f ? 1f : t);
  }

  private float pointerLerpX() {
    return pointerFromX + (pointerToX - pointerFromX) * pointerT();
  }

  private float pointerLerpY() {
    return pointerFromY + (pointerToY - pointerFromY) * pointerT();
  }
  /**
   * Class of the screen where the current cursor path was recorded (Frame.openScreen). null means
   * the cursor is not ours.
   */
  private String pointerScreen = null;

  /**
   * Windows where the mod never touches the cursor: the mod's own menus, main menu, server list,
   * connecting/disconnecting. There the cursor belongs to the player only.
   */
  private static boolean cursorForbiddenScreen(GuiScreen sc) {
    if (sc == null) return true;
    String n = sc.getClass().getName();
    if (n.startsWith("com.mirror.recorder.")) return true;
    return sc instanceof GuiMainMenu
        || sc instanceof GuiMultiplayer
        || sc instanceof GuiScreenServerList
        || sc instanceof GuiScreenAddServer
        || sc instanceof GuiDisconnected
        || sc instanceof GuiDownloadTerrain
        || sc instanceof GuiConnecting;
  }

  /** The real cursor may be moved only in the same window class as in the recording. */
  private boolean cursorScreenMatches(GuiScreen sc) {
    if (cursorForbiddenScreen(sc)) return false;
    String want = pointerScreen;
    return want != null && !want.isEmpty() && want.equals(sc.getClass().getName());
  }

  /** At the moment a click is delivered, the drawn cursor is exactly at the click point. */
  void pointerSnap(int x, int y) {
    visualClickNs = System.nanoTime();
    pointerFromX = x;
    pointerFromY = y;
    pointerToX = x;
    pointerToY = y;
    pointerX = x;
    pointerY = y;
    pointerStartNs = System.nanoTime();
    pointerDurNs = FRAME_GLIDE_DEFAULT_NS;
    pointerOn = true;
    // Real cursor: playback itself puts it at the point from the recording, so vanilla sees the
    // same coordinates as from the player's hand. The conversion is the exact inverse of vanilla's:
    // x=ex*w/dw, y=h-ey*h/dh-1; half a pixel keeps the cell center.
    GuiScreen snapScreen = mc.currentScreen;
    // The event was delivered to exactly this window (class matched the recording), the cursor is
    // driven in it from here on.
    if (snapScreen != null) pointerScreen = snapScreen.getClass().getName();
    // Alt+Tab: the window is inactive, the cursor belongs to the desktop and must not be moved
    // (otherwise it flies to the edge).
    if (!windowActive() || cursorForbiddenScreen(snapScreen)) return;
    if (snapScreen != null && snapScreen.width > 0 && snapScreen.height > 0)
      try {
        if (!Mouse.isGrabbed()) {
          int dw = Math.max(1, mc.displayWidth), dh = Math.max(1, mc.displayHeight);
          int ex = MathHelper.clamp((x * dw + dw / 2) / snapScreen.width, 0, dw - 1);
          int ey =
              MathHelper.clamp(
                  ((snapScreen.height - 1 - y) * dh + dh / 2) / snapScreen.height, 0, dh - 1);
          handler.stops.sampleBeforeModWarp();
          Mouse.setCursorPosition(ex, ey);
          // Our own cursor shift must not count as hand movement and abort playback.
          handler.stops.cursorMovedByMod(ex, ey);
        }
      } catch (Exception e) {
        MirrorDebug.log("GUI", "cursor move failed: " + e);
      }
  }

  /** Slider gesture: the cursor moves to the next recorded point instead of jumping to it. */
  void pointerGlide(int x, int y) {
    long now = System.nanoTime();
    float px = pointerOn ? pointerLerpX() : x, py = pointerOn ? pointerLerpY() : y;
    long dt = pointerLastNs <= 0L ? SLIDER_GLIDE_MIN_NS : now - pointerLastNs;
    if (dt < SLIDER_GLIDE_MIN_NS) dt = SLIDER_GLIDE_MIN_NS;
    if (dt > SLIDER_GLIDE_MAX_NS) dt = SLIDER_GLIDE_MAX_NS;
    pointerFromX = px;
    pointerFromY = py;
    pointerToX = x;
    pointerToY = y;
    pointerStartNs = now;
    pointerDurNs = dt;
    pointerLastNs = now;
    pointerX = x;
    pointerY = y;
    pointerOn = true;
    GuiScreen gs = mc.currentScreen;
    if (gs != null) pointerScreen = gs.getClass().getName();
  }
  /**
   * One cursor instead of two: the real cursor follows the recorded path every frame, and the drawn
   * crosshair is not drawn; a hollow halo only identifies the playback cursor.
   */
  private boolean cursorDriven = false;

  /** The window cursor of this tick goes into the recording frame. */
  void captureCursor(Frame.Builder b) {
    b.cursor(cursorOn(), cursorPxX(), cursorPxY());
  }

  void clearCursorDriven() {
    cursorDriven = false;
  }

  private int followX = Integer.MIN_VALUE, followY = Integer.MIN_VALUE;
  private GuiScreen followScreen;
  private int followDisplayW = -1, followDisplayH = -1, followGuiW = -1, followGuiH = -1;

  private void invalidateFollow() {
    followX = Integer.MIN_VALUE;
    followY = Integer.MIN_VALUE;
    followScreen = null;
  }

  void followRealCursor() {
    cursorDriven = false;
    GuiScreen sc = mc.currentScreen;
    if (!pointerOn
        || sc == null
        || sc.width <= 0
        || sc.height <= 0
        || !windowActive()
        || !cursorScreenMatches(sc)) {
      invalidateFollow();
      return;
    }
    try {
      if (Mouse.isGrabbed()) {
        invalidateFollow();
        return;
      }
      if (followScreen != sc
          || followDisplayW != mc.displayWidth
          || followDisplayH != mc.displayHeight
          || followGuiW != sc.width
          || followGuiH != sc.height) {
        invalidateFollow();
        followScreen = sc;
        followDisplayW = mc.displayWidth;
        followDisplayH = mc.displayHeight;
        followGuiW = sc.width;
        followGuiH = sc.height;
      }
      int dw = Math.max(1, mc.displayWidth), dh = Math.max(1, mc.displayHeight);
      int x = MathHelper.clamp((int) (pointerLerpX() + 0.5f), 0, sc.width - 1),
          y = MathHelper.clamp((int) (pointerLerpY() + 0.5f), 0, sc.height - 1);
      int ex = MathHelper.clamp((x * dw + dw / 2) / sc.width, 0, dw - 1),
          ey = MathHelper.clamp(((sc.height - 1 - y) * dh + dh / 2) / sc.height, 0, dh - 1);
      if (ex != followX || ey != followY) {
        handler.stops.sampleBeforeModWarp();
        Mouse.setCursorPosition(ex, ey);
        followX = ex;
        followY = ey;
        handler.stops.cursorMovedByMod(ex, ey);
      }
      cursorDriven = true;
    } catch (Exception e) {
      cursorDriven = false;
    }
  }
  /** The cursor is drawn by us in the mod color: it is clear this is not the main system cursor. */
  void drawPointer() {
    if (!pointerOn || !config.isShowPointer()) return;
    if (!cursorScreenMatches(mc.currentScreen)) return;
    int x = (int) (pointerLerpX() + 0.5f), y = (int) (pointerLerpY() + 0.5f);
    boolean depthWas = false, blendWas = false, textureWas = true;
    try {
      depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
      textureWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
      GlStateManager.disableDepth();
      float age =
          visualClickNs <= 0L ? -1f : (System.nanoTime() - visualClickNs) / (float) VISUAL_CLICK_NS;
      ReplayHudRenderer.pointer(x, y, cursorDriven, age, config.isUiAnimationsEnabled());
    } catch (Exception e) {
      MirrorDebug.log("GUI", "pointer draw failed: " + e);
    } finally {
      try {
        if (depthWas) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        if (blendWas) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
        if (textureWas) GlStateManager.enableTexture2D();
        else GlStateManager.disableTexture2D();
      } catch (Exception e) {
      }
    }
  }

  @Override
  public void reset() {
    pointerOn = false;
    pointerScreen = null;
    cursorDriven = false;
    pointerLastNs = 0L;
    visualClickNs = 0L;
    invalidateFollow();
  }
}

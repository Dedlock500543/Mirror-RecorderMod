package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.manager.RecorderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

/**
 * Shared playback and recording state: game, manager, settings and only the fields needed by
 * several parts.
 */
@SideOnly(Side.CLIENT)
final class ReplayState {
  /**
   * A playback part with its own reset. RecordingHandler calls reset() on each one explicitly, by
   * name and in the right order.
   */
  interface Feature {
    void reset();
  }

  private final Minecraft mc = Minecraft.getMinecraft();
  final RecorderManager manager;
  final RecorderConfig config;
  final PlaybackTrajectory trajectory;
  /** One guard for the whole handler: its state (standby/check) is shared by all parts. */
  final SafetyGuard guard = new SafetyGuard();
  /**
   * GuiScreen methods for delivering window events: shared by the window queue, containers, lists
   * and chat.
   */
  final GuiReflection reflect = new GuiReflection(this);

  ReplayState(RecorderManager manager, RecorderConfig config, PlaybackTrajectory trajectory) {
    this.manager = manager;
    this.config = config;
    this.trajectory = trajectory;
  }

  static final Logger LOG = LogManager.getLogger("MirrorRecorder/Input");
  int lastMask = 0;
  private boolean clickMethodWarning = false;
  private String lastNotice = "";
  private long lastNoticeAt = 0L;

  static void setKeyState(int code, boolean down) {
    try {
      KeyBinding.setKeyBindState(code, down);
    } catch (Exception e) {
      MirrorDebug.log("INPUT", "key state update failed for code " + code + ": " + e);
    }
  }

  void warnClickFailure(String text, Exception e) {
    MirrorDebug.log(
        "CLICK", "failure: " + text + " (" + (e == null ? "unknown" : e.toString()) + ")");
    if (!clickMethodWarning) {
      clickMethodWarning = true;
      LOG.error(text, e);
    }
  }

  /** The game window has OS focus. */
  static boolean windowActive() {
    try {
      return Display.isCreated() && Display.isActive();
    } catch (Exception e) {
      return false;
    }
  }

  void sendMsg(String msg) {
    // Muted notices must not reserve the deduplication window for the next visible notice.
    if (!TransientChat.messagesEnabled()) return;
    long now = System.currentTimeMillis();
    if (now < lastNoticeAt) lastNoticeAt = 0L;
    if (msg != null && msg.equals(lastNotice) && now - lastNoticeAt < 2000L) return;
    lastNotice = msg == null ? "" : msg;
    lastNoticeAt = now;
    TransientChat.show(msg);
  }
}

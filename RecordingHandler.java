package com.mirror.recorder.handler;

import static com.mirror.recorder.handler.GuiEventQueue.GA_CLOSE;
import static com.mirror.recorder.handler.GuiEventQueue.GUI_EVENT_TTL;
import static com.mirror.recorder.handler.GuiEventQueue.replayableScreen;
import static com.mirror.recorder.handler.ReplayState.windowActive;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.manager.WorldLifecycle;
import com.mirror.recorder.model.Frame;
import com.mirror.recorder.storage.StorageManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Entry point of recording and playback: client tick, input and rendering. It only coordinates; the
 * logic lives in the package parts (see package-info).
 */
@SideOnly(Side.CLIENT)
public class RecordingHandler {
  final ReplayState state;
  private final SafetyGuard guard;
  private final CameraFeature camera;
  private final MovementFeature movement;
  final ClickFeature clicks;
  final KeyboardFeature keyboard;
  final ChatFeature chat;
  final StopConditions stops;
  final GuiEventQueue gui;
  final GuiEventRecorder guiRec;
  final ContainerFeature containers;
  final ListsSlidersFeature lists;
  final PointerFeature pointer;
  private boolean sndRec = false, sndPlay = false;
  private int sndCycle = 0;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private volatile String pendingChatMessage = null;
  /** The window lost focus: LWJGL leaves keys "pressed" forever. */
  private boolean lostFocus = false;

  private boolean keysNeedReset = false;
  /** How many ticks playback may stand still without applied input before stopping by itself. */
  private static final int STALL_LIMIT = 10;

  private int stallTicks = 0;
  /** Start of the current playback pause (0 means no pause). */
  private long pausedAtNs = 0L;
  /** Ticks of delivering the window event tail after the last frame. */
  private int tailTicks = 0;

  private long appliedRun = -1L;

  private int appliedFrame = -1, interactionFrame = -1;
  private boolean heldAttack = false, heldUse = false, recRotInit = false;
  private float lastRecYaw = 0f, lastRecPitch = 0f;
  private final LookTimeline.Capture lookCapture = new LookTimeline.Capture();
  private long interactionRun = -1L;
  private final PlaybackTrajectory trajectory;
  private int autoCheckpointTicks = 0;
  private boolean backgroundPolicyHeld = false, savedPauseOnLostFocus = true;
  private long maskRun = -1L;
  private long recordEpoch;
  private int recordedWorldSequence;
  private long boundaryRun = -1;
  private int boundaryFrame = -1;

  public RecordingHandler(
      RecorderManager manager, RecorderConfig config, PlaybackTrajectory trajectory) {
    this.manager = manager;
    this.config = config;
    this.trajectory = trajectory;
    this.state = new ReplayState(manager, config, trajectory);
    this.guard = state.guard;
    this.camera = new CameraFeature(this, state);
    this.movement = new MovementFeature(this, state);
    this.clicks = new ClickFeature(this, state);
    this.keyboard = new KeyboardFeature(this, state);
    this.chat = new ChatFeature(this, state);
    this.stops = new StopConditions(this, state);
    this.gui = new GuiEventQueue(this, state);
    this.guiRec = new GuiEventRecorder(this, state);
    this.containers = new ContainerFeature(this, state);
    this.lists = new ListsSlidersFeature(this, state);
    this.pointer = new PointerFeature(this, state);
    BaritoneReturnController returner = returnCycleSource();
    if (returner != null) returner.setSafetyHandler(this);
  }
  /** Emergency stop: shuts down recording, playback, return and all held keys. */
  private void emergencyStop(String reason) {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    if (mod != null && mod.getReturnController() != null) mod.getReturnController().cancel();
    resetAll();
    manager.stopAll();
    guard.standby(mc.player);
    SoundFx.error();
    MirrorDebug.log("GUARD", reason);
    state.sendMsg("\u00a7c[!] " + reason);
  }
  /**
   * The loop number comes from the return controller: its cycle number is the completed count plus
   * one.
   */
  private static BaritoneReturnController returnCycleSource() {
    MirrorRecorder mod = MirrorRecorder.getInstance();
    return mod == null ? null : mod.getReturnController();
  }
  /**
   * The input editing order is fixed by priority, not registration order: this handler goes first,
   * the return controller (LOWEST) edits input last, and it must win in the fine positioning and
   * preparation phases.
   */
  @SubscribeEvent(priority = EventPriority.LOW)
  public void onInputUpdate(InputUpdateEvent event) {
    if (!beforeOperationTick() || !playbackEffectsReady()) return;
    if (!manager.isPlaying()) return;
    EntityPlayerSP player = mc.player;
    if (player == null) return;
    // Pause: the frame stands still, but the player's input is left alone, otherwise the pause
    // turns into "hands off".
    if (manager.isPaused()) return;
    Frame frame = manager.getCurrentPlaybackFrame();
    if (frame == null) return;
    if (!movement.applyMovementInput(event, player, frame)) return;
    camera.applyPlaybackRotation();
    movement.applyElytraFlight(player, frame);
    appliedRun = manager.getPlaybackRunId();
    appliedFrame = manager.getCurrentFrameIndex();
    keysNeedReset = true;
  }
  /**
   * Vanilla draws the hand via partialTicks. After the entity tick prevRotation==rotation, so the
   * between-ticks angle is substituted on the render frame.
   */
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onRenderTick(TickEvent.RenderTickEvent event) {
    // Rebase before vanilla's render-time mouseXYChange, even if no client tick ran.
    if (event.phase == TickEvent.Phase.START) focusGuard();
    // Timestamped window events are delivered on render frames, not just 20 times per second.
    if (event.phase == TickEvent.Phase.START && manager.isPlaying() && playbackEffectsReady()) {
      if (!manager.isPaused()) gui.processGuiQueue(false);
      chat.tickChatTyping(false);
      pointer.followRealCursor();
      lists.releaseStuckSlider();
    } else if (event.phase == TickEvent.Phase.START) pointer.clearCursorDriven();
    // Free cursor movement is sampled on render frames; game movement uses native Forge events.
    if (event.phase == TickEvent.Phase.END) {
      stops.sampleMouseMovement();
      if (manager.isRecording() && mc.player != null && mc.world != null
          && !mc.isGamePaused() && mc.currentScreen == null) {
        lookCapture.sample(event.renderTickTime, mc.player.rotationYaw, mc.player.rotationPitch);
      }
    }
    if (!manager.isPlaying() || !playbackEffectsReady() || !config.isApplyRotation()) {
      camera.popInterpolatedLook();
      return;
    }
    if (camera.useNativeTimeline()) {
      camera.popInterpolatedLook();
      if (event.phase == TickEvent.Phase.START && !manager.isPaused() && !chat.isTyping()
          && !mc.isGamePaused() && mc.currentScreen == null) {
        camera.applyNativeTimeline(event.renderTickTime);
      }
    } else if (event.phase == TickEvent.Phase.START) camera.pushInterpolatedLook(event.renderTickTime);
    else camera.popInterpolatedLook();
  }
  /**
   * While a mod operation is running, vanilla is not allowed to open the pause menu 500 ms after
   * losing focus. The user's original setting is restored without saving to options.txt.
   */
  private void updateBackgroundPolicy() {
    if (mc.gameSettings == null) return;
    MirrorRecorder mod = MirrorRecorder.getInstance();
    BaritoneReturnController r = mod == null ? null : mod.getReturnController();
    boolean active = manager.isBusy() || (r != null && (r.isBusy() || r.isRecovering()));
    if (active) {
      if (!backgroundPolicyHeld) {
        savedPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        backgroundPolicyHeld = true;
      }
      mc.gameSettings.pauseOnLostFocus = false;
    } else releaseBackgroundPolicy();
  }

  private void releaseBackgroundPolicy() {
    if (backgroundPolicyHeld && mc.gameSettings != null)
      mc.gameSettings.pauseOnLostFocus = savedPauseOnLostFocus;
    backgroundPolicyHeld = false;
  }
  /**
   * Vanilla restores mouse grab only on a click (runTickMouse), and only with the window active
   * (setIngameFocus checks Display.isActive). If there is no grab in the world without an open
   * screen, we restore it ourselves: the effect of the required click, without a click. Foreign
   * screens, chat, pause, death and an inactive window are left alone: a free cursor is legitimate
   * there. The grab is restored only while a mod operation is running (recording, playback,
   * return): when idle the window behaves like vanilla.
   */
  private void ensureIngameGrab() {
    if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
    // Mouse grab is lost even with inGameHasFocus=true: then the camera stays still until you click
    // the mouse.
    boolean grabbed = Mouse.isGrabbed();
    if (mc.inGameHasFocus && grabbed) return;
    // Never take the cursor from the program the user switched to.
    if (!Display.isActive()) return;
    MirrorRecorder mod = MirrorRecorder.getInstance();
    BaritoneReturnController r = mod == null ? null : mod.getReturnController();
    if (!(manager.isBusy() || (r != null && (r.isBusy() || r.isRecovering())))) return;
    if (mc.inGameHasFocus) mc.mouseHelper.grabMouseCursor();
    else mc.setIngameFocus();
    stops.onMouseRegrabbed();
    // setIngameFocus sets leftClickCounter=10000, vanilla's protection against the click used to
    // return the cursor to the game. For playback that silently loses all recorded attacks for 500
    // seconds after any Alt+Tab, so the lock is lifted immediately.
    clicks.clearLeftClickBlock();
  }
  /**
   * Periodic autosave, when enabled: limits crash losses to data after the last successful save.
   */
  private void tickAutoCheckpoint() {
    int seconds = config.getAutoCheckpointSeconds();
    if (seconds <= 0) {
      autoCheckpointTicks = 0;
      return;
    }
    if (++autoCheckpointTicks < seconds * RecorderManager.TICKS_PER_SECOND) return;
    autoCheckpointTicks = 0;
    manager.checkpointRecording();
    MirrorDebug.log("STORAGE", "auto checkpoint after " + seconds + " s");
  }

  @SubscribeEvent
  public void onClientTick(TickEvent.ClientTickEvent event) {
    updateBackgroundPolicy();
    focusGuard();
    boolean ready = beforeOperationTick();
    if (event.phase == TickEvent.Phase.END) stops.advanceMouseTick();
    if (!ready) return;
    if (event.phase == TickEvent.Phase.START) {
      tickStart();
      return;
    }
    ensureIngameGrab();
    tickEnd();
  }
  /**
   * Tick start: recording frame boundary, chat typing, camera turn and playback input, before the
   * player tick.
   */
  private void tickStart() {
    guiRec.beginTick();
    if (manager.isRecording() && manager.getRecordingTickCounter() <= 0) resetCaptureState();
    if (manager.isPlaying() && !playbackEffectsReady()) return;
    chat.tickChatTyping(true);
    ensureIngameGrab();
    if (manager.isPlaying()) {
      camera.applyTickRotation();
      if (playbackClockRuns()) handlePlaybackInteractions();
      // Paused worlds do not emit InputUpdateEvent. A recorded menu needs only GUI input, not
      // movement, but still has to acknowledge its frame before the END phase advances it.
      if (mc.isGamePaused() && playbackClockRuns() && !manager.isPaused()) {
        appliedRun = manager.getPlaybackRunId();
        appliedFrame = manager.getCurrentFrameIndex();
        keysNeedReset = true;
      }
    }
  }
  /**
   * Tick end: messages and sounds, protection, start countdown, then the recording or playback of
   * the frame itself.
   */
  private void tickEnd() {
    if (!manager.getWorldLifecycle().isActive()) return;
    if (manager.isPlaying() && !playbackEffectsReady()) return;
    EntityPlayerSP player = mc.player;
    if (player == null || mc.world == null) return;
    if (!manager.isRecording()) pendingChatMessage = null;
    reportSaveFailure();
    boolean nowRec = manager.isRecording(), nowPlay = manager.isPlaying();
    reportFrameLimit();
    updateStatusSounds(nowRec, nowPlay);

    if (manager.hasPendingAction()) {
      tickStartCountdown();
      return;
    }
    // Holding with an open screen runs at the end of the tick: Esc wipes key states mid-tick
    // (unPressAllKeys), and vanilla repeats are suppressed with a screen open by the IN_GAME
    // context and the currentScreen check. The END phase also covers the tick in which the screen
    // was opened. The timer/internal checks prevent double firing.
    if (manager.isPlaying() && mc.currentScreen != null) {
      if (heldAttack) clicks.driveScreenAttackHold();
      if (heldUse) clicks.driveScreenUseHold();
    }
    clicks.alignUseTimer();
    // Only recorded settings continue on the client clock while the world is paused. Other
    // pauses keep gameplay frames frozen, so input is not consumed in a stopped world.
    if (manager.isRecording()) {
      // A closing click changes currentScreen before END, while vanilla's paused flag still
      // describes the old menu. Keep that final event instead of discarding the exit itself.
      if (!mc.isGamePaused()
          || GuiEventQueue.settingsScreen(mc.currentScreen)
          || GuiEventQueue.settingsScreen(guiRec.lastEventScreen())) {
        handleRecord(player);
        tickAutoCheckpoint();
      } else clearPendingInput();
    } else if (manager.isPlaying()) {
      if (playbackClockRuns()) handlePlayback(player);
      else stops.stopByMouseMove();
    } else if (keysNeedReset) resetAll();
  }

  /** Manual pauses still freeze gameplay; recorded menu frames use the client tick clock. */
  private boolean playbackClockRuns() {
    if (!mc.isGamePaused()) return true;
    Frame frame = manager.getCurrentPlaybackFrame();
    return GuiEventQueue.runsWhileWorldPaused(frame, manager.isPlaybackAtEnd(), openScreenName());
  }

  private void reportSaveFailure() {
    int failedSlot = manager.consumeUnsavedWarningSlot();
    if (failedSlot > 0) SoundFx.error();
    if (failedSlot > 0)
      state.sendMsg(
          Lang.t("msg.save_error_slot") + failedSlot + Lang.t("msg.is_kept_only_in_memory"));
  }

  private void reportFrameLimit() {
    int limitSlot = manager.consumeLimitReachedSlot();
    if (limitSlot > 0)
      state.sendMsg(
          Lang.t("msg.recording_stopped_the_frame_limit")
              + StorageManager.MAX_FRAMES
              + Lang.t("msg.frames_1_hour_was_reached"));
  }
  /** Sounds for recording start/end, playback start and a new return loop lap. */
  private void updateStatusSounds(boolean nowRec, boolean nowPlay) {
    if (nowRec && !sndRec) SoundFx.recordStart();
    if (!nowRec && sndRec) SoundFx.recordStop();
    if (nowPlay && !sndPlay) {
      SoundFx.playStart();
      BaritoneReturnController rc = returnCycleSource();
      sndCycle = rc == null ? 1 : rc.getCycle();
    } else if (nowPlay) {
      BaritoneReturnController rc = returnCycleSource();
      int cyc = rc == null ? 1 : rc.getCycle();
      if (cyc > sndCycle) {
        sndCycle = cyc;
        SoundFx.cycle();
      }
    }
    sndRec = nowRec;
    sndPlay = nowPlay;
  }
  /** Shared by the return controller before its HIGHEST-priority automatic input. */
  boolean beforeOperationTick() {
    ClientWorldState.update(manager);
    WorldLifecycle lifecycle = manager.getWorldLifecycle();
    if (lifecycle.consumeDisconnect()) {
      if (manager.isBusy() || hasPlaybackOperation())
        emergencyStop(
            Lang.t(
                manager.isRecording()
                    ? "msg.recording_stopped_left_the_world"
                    : "msg.playback_stopped_left_the_world"));
      return false;
    }
    if (lifecycle.getPhase() == WorldLifecycle.Phase.CONFIRMED_DEAD) {
      if (manager.isBusy() || hasPlaybackOperation())
        emergencyStop(
            Lang.t(
                manager.isRecording()
                    ? "msg.recording_stopped_the_player_died"
                    : "msg.playback_stopped_the_player_died"));
      return false;
    }
    if (hasPlaybackOperation() && stops.stopByUserInput()) return false;
    if (!lifecycle.isActive()) {
      guard.standby(mc.player);
      clearPendingInput();
      if (manager.isBusy() || hasPlaybackOperation()) releasePlaybackHolds();
      return false;
    }
    if (hasPlaybackOperation()) {
      String threat = guard.check(mc.player, config);
      if (threat != null) {
        cancelPlaybackOperation(threat);
        return false;
      }
    } else guard.standby(mc.player);
    return true;
  }

  boolean hasPlaybackOperation() {
    BaritoneReturnController returner = returnCycleSource();
    return OperationStopPolicy.playbackOperation(
        manager.isRecording(), manager.isPlaying(), returner != null && returner.isBusy());
  }

  void cancelPlaybackOperation(String reason) {
    BaritoneReturnController returner = returnCycleSource();
    if (returner != null) returner.cancel();
    resetAll();
    manager.cancelPending();
    manager.stopPlayback();
    SoundFx.stop();
    state.sendMsg(reason);
  }

  /** Admission and boundary reset precede camera, keys, GUI, chat and world interactions. */
  private boolean playbackEffectsReady() {
    ClientWorldState.update(manager);
    if (!manager.isPlaying() || !manager.getWorldLifecycle().isActive()) return false;
    boolean changed = manager.hasPlaybackWorldChange();
    int oldFrame = manager.getCurrentFrameIndex();
    if (!manager.synchronizePlaybackWorld(mc.world.provider.getDimension())) {
      cancelPlaybackOperation(Lang.t("msg.playback_stopped_world_mismatch"));
      return false;
    }
    if (changed && oldFrame != manager.getCurrentFrameIndex()) {
      state.sendMsg(Lang.t("msg.playback_resynced_world_change"));
    }
    if (!manager.isPlaybackWorldReady()) {
      releasePlaybackHolds();
      state.sendMsg(Lang.t("msg.playback_waiting_world_change"));
      return false;
    }
    Frame frame = manager.getCurrentPlaybackFrame();
    if (frame != null
        && frame.worldReset
        && (boundaryRun != manager.getPlaybackRunId()
            || boundaryFrame != manager.getCurrentFrameIndex())) {
      resetWorldSegment();
      boundaryRun = manager.getPlaybackRunId();
      boundaryFrame = manager.getCurrentFrameIndex();
    }
    return true;
  }

  /** Queued actions belong to their segment; respawn may replace a player without World.Unload. */
  private void resetWorldSegment() {
    containers.cancelDeferredPress();
    keyboard.reset();
    clicks.reset();
    movement.resetStabilizer();
    camera.onWorldReset();
    pointer.reset();
    chat.reset();
    gui.reset();
    lists.reset();
    clearPendingInput();
    heldAttack = heldUse = false;
    state.lastMask = 0;
    appliedRun = interactionRun = maskRun = -1L;
    appliedFrame = interactionFrame = -1;
    stallTicks = tailTicks = 0;
    pausedAtNs = 0L;
  }

  private void releasePlaybackHolds() {
    heldAttack = heldUse = false;
    clicks.forceReleaseClicks();
    keyboard.releaseReplayedKeys();
    if (mc.player != null && mc.player.movementInput != null) {
      mc.player.movementInput.moveForward = mc.player.movementInput.moveStrafe = 0f;
      mc.player.movementInput.jump = mc.player.movementInput.sneak = false;
      BoatReplayInput.clear(mc.player.movementInput);
      mc.player.setSprinting(false);
    }
  }

  /** Countdown before recording/playback start: seconds in chat and a sound. */
  private void tickStartCountdown() {
    int prev = manager.getDelaySecondsRemaining();
    int sec = manager.tickDelay();
    if (sec > 0 && sec != prev) {
      state.sendMsg("\u00a7e" + sec + "...");
      SoundFx.countdown(sec);
    } else if (sec == 0) state.sendMsg(Lang.t("msg.start"));
  }

  private void handlePlaybackInteractions() {
    if (!playbackEffectsReady()) return;
    // Pause: holds are released and nothing new is pressed; the frame stands still, the window
    // queue waits.
    if (manager.isPaused()) {
      suspendForPause();
      return;
    }
    if (pausedAtNs != 0L) resumeAfterPause();
    clicks.tickUseHoldFallback();
    boolean interact = config.isPlaybackInteraction();
    if (!interact && (heldAttack || heldUse)) {
      heldAttack = false;
      heldUse = false;
      state.lastMask = 0;
      clicks.forceReleaseClicks();
    }
    long run = manager.getPlaybackRunId();
    int index = manager.getCurrentFrameIndex();
    boolean fresh = interactionRun != run || interactionFrame != index;

    Frame frame = manager.getCurrentPlaybackFrame();
    // After the last frame there is no current one, but the last frames skipped by speed-up have
    // not been delivered yet.
    if (fresh && (frame != null || manager.isPlaybackAtEnd())) {
      interactionRun = run;
      interactionFrame = index;
      List<Frame> pending = manager.consumeSkippedFrames();
      // First frame of a run: held buttons (clicks, movement, sneak) are taken from the frame
      // itself, otherwise a button held in the recording looks like a new press and fires an extra
      // click on frame zero. Instant actions (Q/E, MMB, inventory) have no hold: in the first frame
      // they count as a press, otherwise the 0→1 edge never fires and the drop/inventory of the
      // first frame is lost in playback.
      if (maskRun != run && (frame != null || !pending.isEmpty())) {
        Frame seed = pending.isEmpty() ? frame : pending.get(0);
        maskRun = run;
        state.lastMask = (seed.hasKeyMask ? seed.keyMask : 0) & ~Frame.K_INSTANT;
      }
      for (int i = 0; i < pending.size(); i++) replayFrameEvents(pending.get(i), false, interact);
      if (frame != null) replayFrameEvents(frame, true, interact);
    }
    if (interact) {
      gui.processGuiQueue();
      gui.closeUnexpectedScreen(frame);
    }
    containers.dropStaleDeferredPress();
  }
  /** Pause start: playback holds and the keys it holds return to the physical state. */
  private void suspendForPause() {
    if (pausedAtNs != 0L) return;
    pausedAtNs = System.nanoTime();
    heldAttack = false;
    heldUse = false;
    clicks.forceReleaseClicks();
    keyboard.restoreReplayedToPhysical();
  }
  /**
   * Pause end: deferred window events are shifted by the pause duration, and the holds of the
   * already applied frame are restored without repeated presses (the new frame applies them
   * itself).
   */
  private void resumeAfterPause() {
    long paused = System.nanoTime() - pausedAtNs;
    pausedAtNs = 0L;
    gui.shiftTimedEvents(paused);
    keyboard.reapplyReplayedKeys();
    Frame frame = manager.getCurrentPlaybackFrame();
    boolean applied =
        interactionRun == manager.getPlaybackRunId()
            && interactionFrame == manager.getCurrentFrameIndex();
    if (!applied
        || frame == null
        || !frame.hasKeyMask
        || !config.isPlaybackInteraction()
        || mc.gameSettings == null
        || (frame.guiClick && replayableScreen(frame.guiScreen))) return;
    heldAttack = frame.key(Frame.K_ATTACK);
    heldUse = frame.key(Frame.K_USE);
    keyboard.applyHold(mc.gameSettings.keyBindAttack, heldAttack);
    keyboard.applyHold(mc.gameSettings.keyBindUseItem, heldUse);
  }
  /**
   * One recording frame: key events first, then chat enqueueing and interactions. With speed-up the
   * skipped frames go the same way, so events are not lost.
   */
  private void replayFrameEvents(Frame frame, boolean current, boolean interact) {
    if (frame == null) return;
    // Key presses and releases are discrete events: with speed-up the skipped frames deliver them
    // too.
    keyboard.applyKeyEvents(frame);
    if (current) {
      pointer.setPointer(frame);
      // With command-only playback there is no GUI key stream to submit the opened chat.
      boolean commandOnlyChat =
          !interact
              && config.isPlaybackModCommands()
              && frame.openScreen != null
              && frame.openScreen.endsWith(".GuiChat");
      if (!commandOnlyChat) gui.openRecordedMenu(frame);
    }
    chat.enqueueRecordedChat(frame);
    if (!interact) chat.enqueueCommandsWithoutGui(frame);
    if (!interact || mc.gameSettings == null) return;
    KeyBinding attack = mc.gameSettings.keyBindAttack, use = mc.gameSettings.keyBindUseItem;
    if (!frame.hasKeyMask) {
      heldAttack = false;
      heldUse = false;
      state.lastMask = 0;
      KeyBinding.setKeyBindState(attack.getKeyCode(), false);
      KeyBinding.setKeyBindState(use.getKeyCode(), false);
      clicks.fireOrderedClicks(frame, attack, use, frame.attackClicks, frame.useClicks);
      return;
    }
    int mask = frame.keyMask;
    // The hold bit cannot tell a hold from a new press in the next tick; only explicit mouse events
    // can.
    boolean attackPress = frame.leftClick || keyboard.rising(mask, Frame.K_ATTACK),
        usePress = frame.rightClick || keyboard.rising(mask, Frame.K_USE);
    int attackShots = frame.attackClicks > 0 ? frame.attackClicks : (attackPress ? 1 : 0),
        useShots = frame.useClicks > 0 ? frame.useClicks : (usePress ? 1 : 0);
    // Skipped frames still update the mask for edge detection; only the current frame applies
    // attack/use holds to the key bindings.
    keyboard.applyHotbar(frame.hotbarSlot);
    // With a window open vanilla does not read hotkeys: the accumulated press fired after the
    // window closed (phantom drop/inventory). Window actions are replayed via guiKeys, world keys
    // only when there is no window either in the recording or now.
    boolean
        guiKeyCtx = frame.guiKeys.length > 0 || (frame.hasScreenState && frame.openScreen != null),
        hasGuiClose = false;
    for (int i = 0; i + 6 <= frame.guiKeys.length; i += 6)
      if (frame.guiKeys[i] == GA_CLOSE) {
        hasGuiClose = true;
        break;
      }
    keyboard.replayInstantActions(frame, mask, guiKeyCtx, hasGuiClose);
    // Old-format window click (one point per tick); new recordings put clicks into guiKeys as
    // GA_CLICK.
    boolean guiFrame = frame.guiClick && frame.guiScreen != null && !frame.guiScreen.isEmpty();
    if (guiFrame) {
      KeyBinding.setKeyBindState(attack.getKeyCode(), false);
      KeyBinding.setKeyBindState(use.getKeyCode(), false);
      if (frame.guiButton == 2) gui.queueGuiClick(frame, 2);
      else gui.queueOrderedGuiClicks(frame, attackPress, usePress);
      heldAttack = false;
      heldUse = false;
    } else if (current) {
      boolean wantAttack = (mask & Frame.K_ATTACK) != 0, wantUse = (mask & Frame.K_USE) != 0;
      keyboard.applyHold(attack, wantAttack);
      keyboard.applyHold(use, wantUse);
      clicks.fireOrderedClicks(frame, attack, use, attackShots, useShots);
      heldAttack = wantAttack;
      heldUse = wantUse;
    } else {
      clicks.fireOrderedClicks(frame, attack, use, attackShots, useShots);
      heldAttack = (mask & Frame.K_ATTACK) != 0;
      heldUse = (mask & Frame.K_USE) != 0;
    }
    keyboard.replayQueuedPicks();
    if (frame.guiKeys.length > 0) gui.queueGuiKeys(frame);
    state.lastMask = mask;
  }

  @SubscribeEvent
  public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
    if (manager.isPlaying()) pointer.drawPointer();
  }
  /** Window focus lost (Alt+Tab, Win): release keys everywhere except during playback. */
  private void focusGuard() {
    boolean active = windowActive();
    if (active) {
      // Back in the window: the first delta after re-grabbing the cursor is a jump, not the
      // player's hand.
      if (lostFocus) {
        // setIngameFocus is a no-op when vanilla kept its focus flag during Alt+Tab.
        // Re-grabbing resets LWJGL's accumulator without a second getDX/getDY consumer.
        if (mc.player != null && mc.inGameHasFocus && mc.currentScreen == null)
          mc.mouseHelper.grabMouseCursor();
        stops.onFocusReturned();
      }
      lostFocus = false;
      return;
    }
    if (lostFocus) return;
    lostFocus = true;
    // Release them during recording too: the mod disables pause on focus loss, and a key released
    // outside the window would otherwise stick: the player keeps walking and the recording gets
    // movement that never happened. During playback the mod controls the keys, so they are left
    // alone.
    if (!manager.isPlaying())
      try {
        KeyBinding.unPressAllKeys();
      } catch (Exception e) {
        MirrorDebug.log("INPUT", "unPressAllKeys failed: " + e);
      }
  }

  private void handleRecord(EntityPlayerSP player) {
    long tickIndex = manager.getRecordingTickCounter();
    boolean jump = false;
    float moveForward = 0, moveStrafe = 0;
    boolean sneak = false;
    if (player.movementInput != null && mc.gameSettings != null) {
      jump = player.movementInput.jump;
      sneak = player.movementInput.sneak;
      moveForward =
          (mc.gameSettings.keyBindForward.isKeyDown() ? 1f : 0f)
              - (mc.gameSettings.keyBindBack.isKeyDown() ? 1f : 0f);
      moveStrafe =
          (mc.gameSettings.keyBindLeft.isKeyDown() ? 1f : 0f)
              - (mc.gameSettings.keyBindRight.isKeyDown() ? 1f : 0f);
    }
    String chatLine = pendingChatMessage;
    pendingChatMessage = null;
    boolean sprint = player.isSprinting();
    boolean attackClicked = manager.getLeftClickState();
    int attackClicks = attackClicked ? Math.max(1, manager.getLeftClickCount()) : 0;
    boolean useClicked = manager.getRightClickState();
    int useClicks = useClicked ? Math.max(1, manager.getRightClickCount()) : 0;
    chat.injectChatPrefix();
    int[] guiKeys = guiRec.flattenGuiKeys();
    String[] guiKeyScreens = guiRec.eventScreens();
    String openScreen = openScreenName();
    if ((openScreen == null || openScreen.isEmpty()) && guiKeys != null && guiKeys.length > 0)
      openScreen = guiRec.lastEventScreen();
    // Consume the click order every tick so it cannot leak into the following frames.
    int[] clickOrder = manager.consumeClickSequence();
    guiRec.dropDragIfScreenClosed(openScreenName());
    int mask = heldKeyMask(attackClicked, useClicked);
    if (player.getRidingEntity() instanceof EntityBoat && player.movementInput != null)
      mask = BoatReplayInput.captureMask(player.movementInput, mask);
    mask |= keyboard.consumeKeyMask();
    int[] actions = keyboard.consumeActions();
    boolean pendingDrop = keyboard.consumeDropAll();
    boolean dropAll = (mask & Frame.K_DROP) != 0 && (pendingDrop || GuiScreen.isCtrlKeyDown());
    int hotbar =
        player.inventory != null ? MathHelper.clamp(player.inventory.currentItem, 0, 8) : -1;
    if (tickIndex == 0) {
      recordEpoch = manager.getWorldLifecycle().getEpoch();
      recordedWorldSequence = 0;
    }
    int sequence = (int) (manager.getWorldLifecycle().getEpoch() - recordEpoch);
    boolean worldReset = sequence != recordedWorldSequence;
    recordedWorldSequence = sequence;
    if (worldReset) recRotInit = false;
    float deltaYaw = 0f, deltaPitch = 0f;
    if (recRotInit) {
      deltaYaw = MathHelper.wrapDegrees(player.rotationYaw - lastRecYaw);
      deltaPitch = player.rotationPitch - lastRecPitch;
    }
    lastRecYaw = player.rotationYaw;
    lastRecPitch = player.rotationPitch;
    recRotInit = true;
    Frame.Builder builder =
        Frame.builder()
            .x(player.posX)
            .y(player.posY)
            .z(player.posZ)
            .yaw(player.rotationYaw)
            .pitch(player.rotationPitch)
            .motionX(player.motionX)
            .motionY(player.motionY)
            .motionZ(player.motionZ)
            .onGround(player.onGround)
            .elytra(player.isElytraFlying())
            .sneak(sneak)
            .sprint(sprint)
            .leftClick(attackClicked)
            .rightClick(useClicked)
            .attackClicks(attackClicks)
            .useClicks(useClicks)
            .clickSeq(clickOrder)
            .jump(jump)
            .keyEvents(keyboard.collectKeyEvents());
    pointer.captureCursor(builder);
    Frame frame =
        builder
            .moveForward(moveForward)
            .moveStrafe(moveStrafe)
            .keyMask(mask)
            .hotbarSlot(hotbar)
            .dYaw(deltaYaw)
            .dPitch(deltaPitch)
            .lookSamples(lookCapture.drain())
            .guiKeys(guiKeys)
            .guiTextPayloads(guiRec.textPayloads())
            .guiKeyScreens(guiKeyScreens)
            .actions(actions)
            .openScreen(openScreen)
            .screenState(true)
            .dropAll(dropAll)
            .worldReset(worldReset)
            .worldIdentity(sequence, mc.world.provider.getDimension())
            .chatMessage(chatLine)
            .tickIndex(tickIndex)
            .build();
    manager.setLeftClickState(false);
    manager.setRightClickState(false);
    guiRec.clearGuiEvents();
    manager.recordTick(frame);
  }
  /**
   * Keys held this tick (Frame.K_*); a mouse click counts as attack/use even if the button was
   * already released.
   */
  private int heldKeyMask(boolean attackClicked, boolean useClicked) {
    int mask = 0;
    GameSettings gs = mc.gameSettings;
    if (gs != null) {
      if (gs.keyBindAttack.isKeyDown() || attackClicked) mask |= Frame.K_ATTACK;
      if (gs.keyBindUseItem.isKeyDown() || useClicked) mask |= Frame.K_USE;
      if (gs.keyBindForward.isKeyDown()) mask |= Frame.K_FORWARD;
      if (gs.keyBindBack.isKeyDown()) mask |= Frame.K_BACK;
      if (gs.keyBindLeft.isKeyDown()) mask |= Frame.K_LEFT;
      if (gs.keyBindRight.isKeyDown()) mask |= Frame.K_RIGHT;
      if (gs.keyBindJump.isKeyDown()) mask |= Frame.K_JUMP;
      if (gs.keyBindSneak.isKeyDown()) mask |= Frame.K_SNEAK;
      if (gs.keyBindSprint.isKeyDown()) mask |= Frame.K_SPRINT;
      if (gs.keyBindDrop.isKeyDown()) mask |= Frame.K_DROP;
      if (gs.keyBindSwapHands.isKeyDown()) mask |= Frame.K_SWAP;
      if (gs.keyBindInventory.isKeyDown()) mask |= Frame.K_INVENTORY;
      if (gs.keyBindPickBlock.isKeyDown()) mask |= Frame.K_PICK;
    }
    return mask;
  }

  /**
   * Unrecorded pauses do not write frames. Their input must not leak into the next gameplay frame;
   * settings are different and are captured on the client clock even when the world is frozen.
   */
  private void clearPendingInput() {
    lookCapture.clear();
    guiRec.clearGuiEvents();
    manager.setLeftClickState(false);
    manager.setRightClickState(false);
    manager.clearClickSequence();
    keyboard.clearPending();
    guiRec.clearGestures();
  }
  /**
   * First frame capture boundary: called at the start of the first recording tick, before its input
   * arrives. Input before the recording start does not get into the frame, while a quick tap inside
   * the first tick does.
   */
  private void resetCaptureState() {
    clearPendingInput();
    recRotInit = false;
    chat.syncChatOpen();
    guiRec.cancelDrag();
  }
  /** This tick's screen for the recording: the mod's own UI is not recorded. */
  private String openScreenName() {
    GuiScreen s = mc.currentScreen;
    if (s == null) return null;
    String n = s.getClass().getName();
    return n.startsWith("com.mirror.recorder.gui.") ? null : n;
  }

  private void handlePlayback(EntityPlayerSP player) {
    // Pause: the frame stands still, the anti-stall is silent, manual movement does not count as
    // breaking playback.
    if (manager.isPaused()) {
      stallTicks = 0;
      appliedRun = -1L;
      appliedFrame = -1;
      return;
    }
    // Live chat runs in real time: playback waits for typing to finish, otherwise events after the
    // message go into the open chat window.
    if (chat.isTyping()) {
      stallTicks = 0;
      appliedRun = -1L;
      appliedFrame = -1;
      return;
    }
    // The tail comes after waiting for typing: the message of the last frames is finished like any
    // other.
    if (manager.isPlaybackAtEnd()) {
      finishPlaybackTail();
      return;
    }
    long run = manager.getPlaybackRunId();
    int frameIndex = manager.getCurrentFrameIndex();
    if (appliedRun != run || appliedFrame != frameIndex) {
      // Input is not applied to the player (e.g. a spectator following another entity:
      // InputUpdateEvent does not arrive). Short divergences are tolerable, but playback hanging
      // forever is a bug: explicitly stop after half a second.
      if (++stallTicks >= STALL_LIMIT) {
        stallTicks = 0;
        MirrorDebug.log(
            "PLAYBACK",
            "STOP: player input not applied for " + STALL_LIMIT + " ticks at frame " + frameIndex);
        resetAll();
        manager.stopPlayback();
        SoundFx.stop();
        state.sendMsg(Lang.t("msg.playback_stopped_input_is_not"));
      }
      return;
    }
    stallTicks = 0;
    appliedRun = -1L;
    appliedFrame = -1;
    chat.sendQueuedChat(player);
    if (!manager.advancePlayback()) {
      // End of frames: the tail starts on the next tick, when its start delivers the events of the
      // skipped last frames.
      if (!manager.isPlaybackAtEnd()) resetAll();
    }
  }
  /**
   * Frames ran out: window events still in the queue get time to be delivered (at most
   * GUI_EVENT_TTL ticks), and only then is the run counted as complete; the loop and auto-leave
   * wait for this. Stop, death and leaving the world do not get here: they flush the queue
   * immediately.
   */
  private void finishPlaybackTail() {
    if (tailTicks == 0) {
      // No more frames: the last frame's holds do not continue while the window tail is delivered.
      heldAttack = false;
      heldUse = false;
      clicks.forceReleaseClicks();
      keyboard.releaseReplayedKeys();
    }
    if (gui.hasQueuedEvents() && config.isPlaybackInteraction() && tailTicks++ < GUI_EVENT_TTL)
      return;
    if (gui.hasQueuedEvents()) {
      MirrorDebug.log(
          "GUI", "playback ended with " + gui.queuedCount() + " undelivered window events");
      gui.warnGuiDrop();
    }
    tailTicks = 0;
    manager.completePlayback();
    SoundFx.finish();
    resetAll();
  }

  /**
   * Full reset on stop, death and world change. The parts are reset explicitly and in this order:
   * first release what playback holds in the game (deferred window press, keys, clicks, sprint,
   * camera angle), then clear queues and flags, so no held key outlives the queue that would
   * release it.
   */
  void resetAll() {
    containers.reset();
    keyboard.reset();
    clicks.reset();
    movement.reset();
    camera.reset();
    stops.reset();
    pointer.reset();
    chat.reset();
    gui.reset();
    guiRec.reset();
    lists.reset();
    heldAttack = false;
    heldUse = false;
    state.lastMask = 0;
    stallTicks = 0;
    tailTicks = 0;
    pausedAtNs = 0L;
    appliedRun = -1L;
    appliedFrame = -1;
    interactionRun = -1L;
    interactionFrame = -1;
    recRotInit = false;
    lookCapture.clear();
    pendingChatMessage = null;
    keysNeedReset = false;
    maskRun = -1L;
    autoCheckpointTicks = 0;
    boundaryRun = -1;
    boundaryFrame = -1;
  }

  @SubscribeEvent
  public void onWorldUnload(WorldEvent.Unload event) {
    if (event.getWorld().isRemote) {
      manager.getWorldLifecycle().suspend();
      releaseBackgroundPolicy();
      pendingChatMessage = null;
      resetAll();
    }
  }

  @SubscribeEvent
  public void onWorldLoad(WorldEvent.Load event) {
    // Only a running operation or countdown: a slot pinned between loops of a series does not count
    // here.
    if (event.getWorld().isRemote
        && (manager.isRecording() || manager.isPlaying() || manager.hasPendingAction())) {
      if (manager.isRecording()) state.sendMsg(Lang.t("msg.new_world_recording_continues"));
      else if (manager.isPlaying()) state.sendMsg(Lang.t("msg.new_world_playback_continues"));
    }
  }

  public void resetAfterCommandStop() {
    resetAll();
  }

  public void captureBeforeModCommand() {
    if (manager.isRecording() && mc.player != null) handleRecord(mc.player);
  }

  @SubscribeEvent
  public void onClientChat(ClientChatEvent event) {
    if (manager.isRecording()
        && mc.currentScreen instanceof GuiChat
        && !guiRec.isComponentClick()) {
      String msg = event.getMessage();
      if (msg != null && !msg.isEmpty()) pendingChatMessage = msg;
    }
  }

  @SubscribeEvent
  public void onMouseInput(InputEvent.MouseInputEvent event) {
    // Only events without a screen get here: any armed window drag is already stale (the window
    // quietly closed).
    if (!manager.isRecording()) return;
    guiRec.cancelDrag();
    if (mc.currentScreen != null || !Mouse.getEventButtonState()) return;
    int btn = Mouse.getEventButton();
    if (btn >= 0) keyboard.recordInstantPress(btn - KeyboardFeature.MOUSE_CODE_OFFSET);
    if (btn == 0) manager.addLeftClick();
    else if (btn == 1) manager.addRightClick();
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
  public void onPhysicalMouseMovement(MouseEvent event) {
    // Observation only: no Mouse.getDX/getDY consumption and no cancellation of vanilla input.
    stops.recordMouseMovement(event.getDx(), event.getDy(), windowActive());
  }

  @SubscribeEvent
  public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
    guiRec.recordGuiMouseInput(event);
  }
  /**
   * After mouse handling: the chat cursor and settings list scroll are visible only as a result, so
   * that is what we record.
   */
  @SubscribeEvent
  public void onGuiMouseInputPost(GuiScreenEvent.MouseInputEvent.Post event) {
    guiRec.recordGuiMouseInputPost(event);
  }

  /**
   * Keys in foreign windows: classified right away (slot action, close, typing) so playback does
   * not depend on current key bindings. Mod windows are not recorded; keys in the chat window are
   * recorded with a time offset, so live chat can retype them.
   */
  @SubscribeEvent
  public void onGuiKeyInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
    guiRec.recordGuiKeyInput(event);
  }

  @SubscribeEvent
  public void onGuiKeyInputPost(GuiScreenEvent.KeyboardInputEvent.Post event) {
    guiRec.recordGuiKeyInputPost(event);
  }

  /**
   * Mod hotkeys with a screen open. Forge sends KeyInputEvent only when currentScreen==null, so
   * playback and recording could not be stopped from the inventory, settings or any foreign window.
   * The same keys are caught in the screen event: typing text and key rebinding are not affected,
   * and our own key is consumed so the window does not react to it and it does not get recorded.
   */
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onGuiHotkey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
    keyboard.handleGuiHotkey(event);
  }

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    keyboard.handleKeyInput(event);
  }
}

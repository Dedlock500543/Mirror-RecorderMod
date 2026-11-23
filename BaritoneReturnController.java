package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Return to the recording start before playback: Baritone walks to the point, then key-based fine
 * positioning, camera turn and countdown; optionally repeat and auto-leave after the configured
 * number of cycles.
 */
@SideOnly(Side.CLIENT)
public final class BaritoneReturnController {
  private enum Phase {
    IDLE,
    BARITONE,
    ALIGN,
    PREPARE,
    PLAYING,
    RECOVERY
  }

  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private Phase phase = Phase.IDLE;
  private int slot = 0,
      phaseTicks = 0,
      blockedTicks = 0,
      completedCycles = 0,
      loopLimit = 1,
      prepareTicks = 0,
      settleTicks = 0,
      lastCountdownSecond = -1,
      viewAlignTicks = 0;
  private boolean loop = false;
  private AutoLeaveRequest leaveRequest;
  private AutoLeaveRequest timedLeaveTarget;
  private final ElapsedLeaveTimer elapsedLeave = new ElapsedLeaveTimer();
  private Frame first;
  private RecordingHandler safetyHandler;
  private long returnWorldEpoch;

  void setSafetyHandler(RecordingHandler handler) {
    safetyHandler = handler;
  }

  private float alignStepYaw = 0f, alignStepPitch = 0f;
  private EntityPlayerSP alignRenderPlayer;
  private float svYaw, svPitch, svPrevYaw, svPrevPitch, svHeadYaw, svPrevHeadYaw;
  private long completionBefore = 0L;
  private Object baritone, pathingBehavior;

  public BaritoneReturnController(RecorderManager manager, RecorderConfig config) {
    this.manager = manager;
    this.config = config;
  }

  public boolean request(int requestedSlot, boolean requestedLoop) {
    if (phase == Phase.RECOVERY) {
      phase = Phase.IDLE;
      clear();
    }
    if (isBusy()
        || manager.isBusy()
        || requestedSlot < RecorderManager.MIN_SLOT
        || requestedSlot > RecorderManager.MAX_SLOT) {
      busyMessage();
      return false;
    }
    Frame f = manager.getSlotFirstFrame(requestedSlot);
    if (f == null) {
      message(Lang.t("msg.the_slot_is_empty_or"));
      return false;
    }
    EntityPlayerSP p = mc.player;
    if (p == null || mc.world == null) {
      message(Lang.t("msg.enter_a_world_first"));
      return false;
    }
    ClientWorldState.update(manager);
    if (!manager.getWorldLifecycle().isActive()) {
      message(Lang.t("msg.enter_a_world_first"));
      return false;
    }
    if (f.worldSequence >= 0 && f.worldDimension != mc.world.provider.getDimension()) {
      message(Lang.t("msg.playback_stopped_world_mismatch"));
      return false;
    }
    // Validate busy state, slot contents and world before remembering the mode and reserving the
    // slot, so these validation failures do not change settings or an operation being prepared.
    manager.setPreferredLoopMode(requestedSlot, requestedLoop);
    if (!manager.reservePlayback(requestedSlot)) {
      busyMessage();
      return false;
    }
    slot = requestedSlot;
    loop = requestedLoop;
    int configured = config.getLoopLimit();
    loopLimit = requestedLoop ? (configured == 0 ? 0 : Math.max(2, configured)) : 1;
    completedCycles = 0;
    first = f;
    announceAutoLeave();
    return beginReturn();
  }

  private void busyMessage() {
    message(Lang.t("msg.another_action_is_already_active"));
  }

  private boolean beginReturn() {
    EntityPlayerSP p = mc.player;
    if (p == null || mc.world == null || first == null) {
      finish(false, null);
      return false;
    }
    if (first.worldSequence >= 0 && first.worldDimension != mc.world.provider.getDimension()) {
      finish(false, Lang.t("msg.playback_stopped_world_mismatch"));
      return false;
    }
    returnWorldEpoch = manager.getWorldLifecycle().getEpoch();
    phaseTicks = blockedTicks = 0;
    if (!config.isAutoReturnEnabled()) {
      beginPreparation();
      return true;
    }
    double dx = first.x - p.posX, dz = first.z - p.posZ;
    if (dx * dx + dz * dz <= ON_POINT_SQ && Math.abs(first.y - p.posY) <= ON_POINT_DY) {
      phase = Phase.ALIGN;
      return true;
    }
    if (!startBaritone()) {
      beginRecovery(pointNotFound());
      return false;
    }
    phase = Phase.BARITONE;
    if (!loop || completedCycles == 0) message(Lang.t("msg.baritone_is_returning_to_the"));
    return true;
  }
  /**
   * LOWEST: runs last, so during the fine positioning and preparation phases it overrides the
   * recording handler's input.
   */
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onInput(InputUpdateEvent event) {
    if (phase != Phase.ALIGN && phase != Phase.PREPARE) return;
    if (safetyHandler != null && !safetyHandler.beforeOperationTick()) {
      event.getMovementInput().moveForward = event.getMovementInput().moveStrafe = 0f;
      event.getMovementInput().jump = event.getMovementInput().sneak = false;
      return;
    }
    EntityPlayerSP p = mc.player;
    if (p == null) return;
    if (phase == Phase.PREPARE) {
      event.getMovementInput().jump = false;
      event.getMovementInput().sneak = false;
      event.getMovementInput().moveForward = 0f;
      event.getMovementInput().moveStrafe = 0f;
      p.setSprinting(false);
      return;
    }
    if (first == null) return;
    event.getMovementInput().jump = false;
    event.getMovementInput().sneak = false;
    double dx = first.x - p.posX, dz = first.z - p.posZ, h = Math.sqrt(dx * dx + dz * dz);
    double tolerance = config.getReturnPositionTolerance();
    if (h <= tolerance && Math.abs(first.y - p.posY) <= ON_POINT_DY) {
      event.getMovementInput().moveForward = 0f;
      event.getMovementInput().moveStrafe = 0f;
      p.setSprinting(false);
      beginPreparation();
      return;
    }
    if (h < 0.001d) {
      event.getMovementInput().moveForward = 0f;
      event.getMovementInput().moveStrafe = 0f;
      return;
    }
    double nx = dx / h,
        nz = dz / h,
        yaw = Math.toRadians(p.rotationYaw),
        forward = -nx * Math.sin(yaw) + nz * Math.cos(yaw),
        strafe = nx * Math.cos(yaw) + nz * Math.sin(yaw);
    float max = config.getReturnAlignSpeed(),
        speed = h < 0.35d ? Math.min(0.25f, max) : (h < 0.8d ? Math.min(0.5f, max) : max);
    event.getMovementInput().moveForward = MathHelper.clamp((float) forward * speed, -max, max);
    event.getMovementInput().moveStrafe = MathHelper.clamp((float) strafe * speed, -max, max);
    p.setSprinting(false);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onTick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.START || phase == Phase.IDLE) return;
    if (safetyHandler != null && !safetyHandler.beforeOperationTick()) {
      if (!manager.getWorldLifecycle().isActive()) cancelBaritone();
      return;
    }
    if (!manager.getWorldLifecycle().isActive()) return;
    if (phase == Phase.IDLE) return;
    // A manual stop or a naturally finished run wins over a timer on the same tick.
    if (phase == Phase.PLAYING && !manager.isPlaying()) {
      onPlaybackEnded();
      return;
    }
    if (!config.isAutoLeaveEnabled() || !config.isAutoLeaveTimed()) {
      elapsedLeave.cancel();
      timedLeaveTarget = null;
    } else if (elapsedLeave.expired(System.nanoTime())) {
      AutoLeaveRequest target = timedLeaveTarget;
      elapsedLeave.cancel();
      timedLeaveTarget = null;
      if (target != null && target.matches(mc.world, mc.getConnection()) && canLeaveNow()) {
        beginTimedLeave();
        return;
      }
    }
    if (phase != Phase.PLAYING && returnWorldEpoch != manager.getWorldLifecycle().getEpoch()) {
      finish(false, Lang.t("msg.playback_stopped_world_mismatch"));
      return;
    }
    EntityPlayerSP p = mc.player;
    if ((phase == Phase.BARITONE || phase == Phase.ALIGN) && !config.isAutoReturnEnabled()) {
      cancelBaritone();
      beginPreparation();
      return;
    }
    phaseTicks++;
    if (phase == Phase.RECOVERY) {
      tickRecovery(p);
      return;
    }
    if (phase == Phase.BARITONE) {
      tickBaritone(p);
      return;
    }
    if (phase == Phase.ALIGN) {
      tickAlign(p);
      return;
    }
    if (phase == Phase.PREPARE) {
      tickPrepare(p);
      return;
    }
  }

  // Disconnect after higher-priority END handlers, not in the HIGHEST phase coordinator.
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public void onLeaveTick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END && leaveRequest != null) performLeave();
  }

  /** Return did not find the recording start point: shared text for all phases. */
  private static String pointNotFound() {
    return Lang.t("msg.could_not_find_the_original");
  }

  /**
   * "Standing on the point": within 0.5 blocks horizontally (0.25 = 0.5², compared with the squared
   * distance) and 1.25 vertically.
   */
  private static final double ON_POINT_SQ = 0.25d, ON_POINT_DY = 1.25d;
  /**
   * Baritone got within 1.2 blocks horizontally (1.44 squared) and 1.75 vertically: key-based fine
   * positioning from here.
   */
  private static final double NEAR_TARGET_SQ = 1.44d, NEAR_TARGET_DY = 1.75d;
  /** Recovery waits for the player for 3 minutes (20 ticks per second). */
  private static final int RECOVERY_MAX_TICKS = 3600;
  /**
   * Fine positioning: 12 ticks in a row against a wall or 12 seconds total means the point cannot
   * be reached.
   */
  private static final int ALIGN_BLOCKED_TICKS = 12, ALIGN_MAX_TICKS = 240;
  /** Waiting for momentum to fade before the start: at most 0.5 s. */
  private static final int SETTLE_MAX_TICKS = 10;

  /** Recovery: wait until the player steps onto the start point themselves (at most 3 minutes). */
  private void tickRecovery(EntityPlayerSP p) {
    if (first == null) {
      phase = Phase.IDLE;
      clear();
      return;
    }
    double rdx = first.x - p.posX, rdz = first.z - p.posZ;
    if (rdx * rdx + rdz * rdz <= ON_POINT_SQ && Math.abs(first.y - p.posY) <= ON_POINT_DY) {
      phase = Phase.IDLE;
      message(Lang.t("msg.start_point_found_now_run"));
      clear();
      return;
    }
    if (phaseTicks > RECOVERY_MAX_TICKS) {
      phase = Phase.IDLE;
      message(Lang.t("msg.recovery_mode_ended_the_start"));
      clear();
    }
  }

  /**
   * Baritone walks to the point: when close, switch to fine positioning; on timeout or without a
   * path, recovery.
   */
  private void tickBaritone(EntityPlayerSP p) {
    if (pathingBehavior == null && !startBaritone()) {
      beginRecovery(pointNotFound());
      return;
    }
    double dx = first.x - p.posX, dz = first.z - p.posZ;
    if (dx * dx + dz * dz <= NEAR_TARGET_SQ && Math.abs(first.y - p.posY) <= NEAR_TARGET_DY) {
      MirrorDebug.phase(
          "return", "BARITONE", "ALIGN", "target area reached in " + phaseTicks + " ticks");
      cancelBaritone();
      phase = Phase.ALIGN;
      phaseTicks = 0;
      return;
    }
    if (phaseTicks > config.getReturnTimeoutSeconds() * RecorderManager.TICKS_PER_SECOND
        || (phaseTicks > 40 && phaseTicks % 4 == 0 && !baritonePathing()))
      beginRecovery(pointNotFound());
  }

  /**
   * Key-based fine positioning (input in onInput): stuck against a wall or taking too long means
   * recovery.
   */
  private void tickAlign(EntityPlayerSP p) {
    if (p.collidedHorizontally) blockedTicks++;
    else blockedTicks = 0;
    if (blockedTicks >= ALIGN_BLOCKED_TICKS || phaseTicks > ALIGN_MAX_TICKS)
      beginRecovery(pointNotFound());
  }

  /** Preparation: countdown, camera turn, momentum fade and playback start. */
  private void tickPrepare(EntityPlayerSP p) {
    boolean viewMoving = config.isCursorStabilization() && smoothFirstFrameView(p);
    if (prepareTicks > 0) {
      prepareTicks--;
      announceCountdown();
      if (prepareTicks > 0) return;
    }
    if (viewMoving) return;
    // Even with the delay off, wait for the residual velocity after positioning/return to fade:
    // otherwise playback starts "with momentum" and the first route frames drift. At most 10 ticks
    // (0.5 s).
    if (settleTicks < SETTLE_MAX_TICKS
        && p.onGround
        && (Math.abs(p.motionX) > 0.005d || Math.abs(p.motionZ) > 0.005d)) {
      settleTicks++;
      return;
    }
    completionBefore = manager.getPlaybackCompletionId();
    if (manager.startPreparedPlayback(slot)) {
      MirrorDebug.phase(
          "return",
          "PREPARE",
          "PLAYING",
          "slot="
              + slot
              + ", cycle="
              + (completedCycles + 1)
              + "/"
              + (loopLimit == 0 ? "inf" : String.valueOf(loopLimit)));
      phase = Phase.PLAYING;
      phaseTicks = 0;
      if (completedCycles == 0
          && config.isAutoLeaveEnabled()
          && config.isAutoLeaveTimed()
          && canLeaveNow()) {
        timedLeaveTarget = new AutoLeaveRequest(mc.world, mc.getConnection());
        elapsedLeave.start(System.nanoTime(), config.getAutoLeaveMinutes());
      }
      if (!loop || completedCycles == 0) message(Lang.t("msg.start"));
    } else finish(false, Lang.t("msg.could_not_start_the_slot"));
  }

  /** The run finished: next loop, auto-leave or end of the series. */
  private void onPlaybackEnded() {
    if (manager.getPlaybackCompletionId() > completionBefore) {
      completedCycles++;
      if (config.isAutoLeaveEnabled()
          && !config.isAutoLeaveTimed()
          && completedCycles >= config.getAutoLeaveCycles()) {
        if (canLeaveNow()) beginLeave(completedCycles);
        else finish(true, Lang.t("msg.auto_leave_server_only", completedCycles));
        return;
      }
      if (loop && (loopLimit == 0 || completedCycles < loopLimit)) {
        first = manager.getSlotFirstFrame(slot);
        if (first == null) {
          finish(false, Lang.t("msg.the_slot_recording_is_no"));
          return;
        }
        beginReturn();
      } else finish(true, Lang.t("msg.playback_finished"));
    } else finish(false, null);
  }
  /**
   * Smooth camera turn to the first frame's view. The speed is limited on purpose: a sharp camera
   * turn makes the game rebuild visible chunks every tick, which dropped FPS. A step that eases
   * near the target reduces the final correction; after 60 ticks the target view is applied
   * directly.
   */
  private boolean smoothFirstFrameView(EntityPlayerSP p) {
    if (p == null || first == null) {
      viewAlignTicks = 0;
      return false;
    }
    float targetYaw = MathHelper.wrapDegrees(first.yaw),
        targetPitch = MathHelper.clamp(first.pitch, -90f, 90f);
    float dy = MathHelper.wrapDegrees(targetYaw - p.rotationYaw),
        dp = targetPitch - p.rotationPitch;
    if (Math.abs(dy) < 0.5f && Math.abs(dp) < 0.5f) {
      setView(p, targetYaw, targetPitch);
      viewAlignTicks = 0;
      alignStepYaw = 0f;
      alignStepPitch = 0f;
      return false;
    }
    float sy = MathHelper.clamp(dy * 0.25f, -7.5f, 7.5f),
        sp = MathHelper.clamp(dp * 0.25f, -5f, 5f);
    setView(p, p.rotationYaw + sy, p.rotationPitch + sp);
    alignStepYaw = sy;
    alignStepPitch = sp;
    if (viewAlignTicks > 0) viewAlignTicks--;
    if (viewAlignTicks == 0) {
      setView(p, targetYaw, targetPitch);
      alignStepYaw = 0f;
      alignStepPitch = 0f;
      return false;
    }
    return true;
  }

  private void setView(EntityPlayerSP p, float yaw, float pitch) {
    p.prevRotationYaw = p.rotationYaw;
    p.prevRotationPitch = p.rotationPitch;
    p.prevRotationYawHead = p.rotationYawHead;
    p.rotationYaw = yaw;
    p.rotationPitch = MathHelper.clamp(pitch, -90f, 90f);
    p.rotationYawHead = yaw;
  }

  private void beginRecovery(String reason) {
    MirrorDebug.phase(
        "return",
        phase.name(),
        "RECOVERY",
        "phaseTicks=" + phaseTicks + ", blockedTicks=" + blockedTicks + ", slot=" + slot);
    MirrorDebug.csvClose();
    cancelBaritone();
    manager.releaseReservation();
    loop = false;
    loopLimit = 1;
    completedCycles = 0;
    prepareTicks = viewAlignTicks = 0;
    lastCountdownSecond = -1;
    blockedTicks = 0;
    phaseTicks = 0;
    phase = Phase.RECOVERY;
    if (reason != null) message(reason);
    message(Lang.t("msg.walk_to_the_marked_start"));
  }

  private void beginPreparation() {
    MirrorDebug.phase(
        "return", phase.name(), "PREPARE", "slot=" + slot + ", cycle=" + (completedCycles + 1));
    viewAlignTicks = 60;
    settleTicks = 0;
    phase = Phase.PREPARE;
    phaseTicks = 0;
    lastCountdownSecond = -1;
    prepareTicks =
        (completedCycles == 0 || !config.isSkipLoopStartDelay())
            ? config.getStartDelay() * RecorderManager.TICKS_PER_SECOND
            : 0;
    announceCountdown();
  }

  private void announceCountdown() {
    int sec = getPreparationSeconds();
    if (sec > 0 && sec != lastCountdownSecond) {
      lastCountdownSecond = sec;
      message("\u00a7e" + sec + "...");
    }
  }

  public void cancel() {
    popAlignedLook();
    if (phase == Phase.IDLE) {
      leaveRequest = null; // Cancel an already scheduled auto-leave.
      return;
    }
    if (phase == Phase.RECOVERY) {
      phase = Phase.IDLE;
      clear();
      message(Lang.t("msg.recovery_mode_cancelled_marker_removed"));
      return;
    }
    cancelBaritone();
    phase = Phase.IDLE;
    clear();
  }

  private void finish(boolean success, String text) {
    MirrorDebug.phase(
        "return",
        phase.name(),
        "IDLE",
        (success ? "finished" : "aborted") + ", cycles=" + completedCycles);
    MirrorDebug.csvClose();
    cancelBaritone();
    phase = Phase.IDLE;
    if (text != null) message(text);
    clear();
  }
  /**
   * Auto-leave: the set number of loops is done; finish the run normally, then disconnect. finish()
   * resets the phases, cancels Baritone and clears the mod state; delivery belongs to LOWEST END,
   * after the higher-priority handlers have finished using the live world.
   */
  private void beginLeave(int cycles) {
    MirrorDebug.phase(
        "return",
        "PLAYING",
        "LEAVE",
        "cycles=" + cycles + ", limit=" + config.getAutoLeaveCycles());
    AutoLeaveRequest request = new AutoLeaveRequest(mc.world, mc.getConnection());
    finish(true, Lang.t("msg.cycles_finished_leaving_the_server", cycles));
    leaveRequest = request;
  }

  private void beginTimedLeave() {
    AutoLeaveRequest request = new AutoLeaveRequest(mc.world, mc.getConnection());
    finish(true, Lang.t("msg.timed_auto_leave"));
    leaveRequest = request;
  }

  private void performLeave() {
    AutoLeaveRequest request = leaveRequest;
    leaveRequest = null;
    WorldClient world = mc.world;
    // Never apply an old request to a replacement world or connection, even on the same server.
    if (request == null || !request.matches(world, mc.getConnection()) || !canLeaveNow()) {
      MirrorDebug.log("LEAVE", "leave target changed or is not a remote server, leave skipped");
      return;
    }
    manager.stopAll();
    try {
      world.sendQuittingDisconnectingPacket();
    } catch (Throwable ignored) {
    }
    mc.loadWorld((WorldClient) null);
    mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
  }

  /** Auto-leave only disconnects from an external server, see AutoLeavePolicy. */
  private boolean canLeaveNow() {
    return AutoLeavePolicy.canLeave(
        AutoLeavePolicy.classify(
            mc.world != null, mc.getConnection() != null, mc.isIntegratedServerRunning()));
  }
  /** Warning at start: if the loop has fewer laps than auto-leave needs, there will be no leave. */
  private void announceAutoLeave() {
    // In a local world there is no leave to announce; the series ends with a server-only notice.
    if (!config.isAutoLeaveEnabled() || config.isAutoLeaveTimed() || !canLeaveNow()) return;
    int need = config.getAutoLeaveCycles();
    if (loopLimit != 0 && need > loopLimit)
      message(Lang.t("msg.auto_leave_after_cycles_will", need, loopLimit));
    else message(Lang.t("msg.auto_leave_after_cycles_is", need));
  }

  private void clear() {
    popAlignedLook();
    elapsedLeave.cancel();
    timedLeaveTarget = null;
    manager.releaseReservation();
    slot = 0;
    phaseTicks = blockedTicks = completedCycles = prepareTicks = viewAlignTicks = 0;
    lastCountdownSecond = -1;
    loop = false;
    loopLimit = 1;
    leaveRequest = null;
    first = null;
    baritone = pathingBehavior = null;
    alignStepYaw = 0f;
    alignStepPitch = 0f;
  }

  /** Also busy when an auto-leave is scheduled: Stop must see and cancel it. */
  public boolean isBusy() {
    return (phase != Phase.IDLE && phase != Phase.RECOVERY) || leaveRequest != null;
  }

  /** A "loop" series is running (finite or infinite, loopLimit 0). */
  public boolean isLooping() {
    return isBusy() && loop;
  }

  public boolean isLeaving() {
    return phase == Phase.IDLE && leaveRequest != null;
  }

  public boolean isRecovering() {
    return phase == Phase.RECOVERY && first != null;
  }

  public double getTargetX() {
    return first == null ? 0d : first.x;
  }

  public double getTargetY() {
    return first == null ? 0d : first.y;
  }

  public double getTargetZ() {
    return first == null ? 0d : first.z;
  }

  public boolean isSlotBusy(int value) {
    return isBusy() && slot == value;
  }

  public boolean isRecoveringSlot(int value) {
    return isRecovering() && slot == value;
  }

  public int getPreparationSeconds() {
    return prepareTicks <= 0
        ? 0
        : (prepareTicks + (RecorderManager.TICKS_PER_SECOND - 1))
            / RecorderManager.TICKS_PER_SECOND;
  }

  public int getCycle() {
    return completedCycles + 1;
  }

  public int getLoopLimit() {
    return loopLimit;
  }

  public int getSlot() {
    return slot;
  }

  private boolean startBaritone() {
    pathingBehavior = BaritoneAdapter.startPath(first.x, first.y, first.z);
    return pathingBehavior != null;
  }

  private boolean baritonePathing() {
    return BaritoneAdapter.isPathing(pathingBehavior);
  }

  private void cancelBaritone() {
    BaritoneAdapter.cancelAll(pathingBehavior);
    baritone = pathingBehavior = null;
  }
  /**
   * Per-frame interpolation of the view correction: the logical rotation goes in sparse steps on
   * the tick, while on screen the camera moves every frame via partialTicks. The temporary angles
   * are restored at the end of the render frame. The same trick as RecordingHandler.onRenderTick
   * with CameraFeature.pushInterpolatedLook.
   */
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void onRenderTick(TickEvent.RenderTickEvent event) {
    if (event.phase == TickEvent.Phase.START) {
      popAlignedLook();
      ClientWorldState.update(manager);
      if (!manager.getWorldLifecycle().isActive()
          || returnWorldEpoch != manager.getWorldLifecycle().getEpoch()) return;
      if (phase != Phase.PREPARE || (alignStepYaw == 0f && alignStepPitch == 0f)) return;
      EntityPlayerSP p = mc.player;
      if (p == null) return;
      svYaw = p.rotationYaw;
      svPitch = p.rotationPitch;
      svPrevYaw = p.prevRotationYaw;
      svPrevPitch = p.prevRotationPitch;
      svHeadYaw = p.rotationYawHead;
      svPrevHeadYaw = p.prevRotationYawHead;
      float pt = event.renderTickTime;
      if (pt < 0f) pt = 0f;
      else if (pt > 1f) pt = 1f;
      float back = 1f - pt;
      float y = svYaw - alignStepYaw * back, pi = svPitch - alignStepPitch * back;
      p.rotationYaw = y;
      p.rotationPitch = pi;
      p.prevRotationYaw = y;
      p.prevRotationPitch = pi;
      p.rotationYawHead = y;
      p.prevRotationYawHead = y;
      alignRenderPlayer = p;
    } else popAlignedLook();
  }

  /** A render snapshot belongs to its original entity, never to a replacement player. */
  private void popAlignedLook() {
    EntityPlayerSP p = alignRenderPlayer;
    alignRenderPlayer = null;
    if (p == null) return;
    p.rotationYaw = svYaw;
    p.rotationPitch = svPitch;
    p.prevRotationYaw = svPrevYaw;
    p.prevRotationPitch = svPrevPitch;
    p.rotationYawHead = svHeadYaw;
    p.prevRotationYawHead = svPrevHeadYaw;
  }

  private void message(String text) {
    TransientChat.show("\u00a78[\u00a7bMirror\u00a78]\u00a7r " + text);
  }

  @SubscribeEvent
  public void onWorldUnload(WorldEvent.Unload event) {
    // Stop pathing in the departing world, but wait for authoritative lifecycle admission before
    // deciding whether this is a disconnect or an incompatible navigation destination.
    if (event.getWorld().isRemote) {
      popAlignedLook();
      if (phase != Phase.PLAYING) cancelBaritone();
    }
  }
}

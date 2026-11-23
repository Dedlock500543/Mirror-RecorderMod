package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Playback movement: WASD, jump, sneak, sprint, route stabilization and elytra. */
@SideOnly(Side.CLIENT)
final class MovementFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;
  private final PlaybackTrajectory trajectory;

  MovementFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
    this.trajectory = state.trajectory;
  }

  private float adjustedForward, adjustedStrafe;
  private boolean sprintRefused = false;
  private boolean speedNoticeShown = false;
  private boolean wantSprint = false;
  private boolean sprintAssist = false;
  /**
   * WASD, jump, sneak and sprint of the playback frame. false means stabilization aborted playback
   * and the tick's input is zeroed.
   */
  boolean applyMovementInput(InputUpdateEvent event, EntityPlayerSP player, Frame frame) {
    boolean replaySneak =
        config.isPlaybackSneak() && (frame.hasKeyMask ? frame.key(Frame.K_SNEAK) : frame.sneak);
    trajectory.updateCalibration(player);
    event.getMovementInput().sneak = replaySneak;
    if (config.isPlaybackMovement()) {
      // EntityPlayerSP.updateRidden feeds these booleans to EntityBoat, not the analog axes.
      // Do not hold global key bindings: vanilla refreshes this per-tick input on dismount.
      if (player.getRidingEntity() instanceof EntityBoat)
        BoatReplayInput.apply(event.getMovementInput(), frame);
      float
          rawForward =
              frame.hasKeyMask
                  ? ((frame.key(Frame.K_FORWARD) ? 1f : 0f) - (frame.key(Frame.K_BACK) ? 1f : 0f))
                  : frame.moveForward,
          rawStrafe =
              frame.hasKeyMask
                  ? ((frame.key(Frame.K_LEFT) ? 1f : 0f) - (frame.key(Frame.K_RIGHT) ? 1f : 0f))
                  : frame.moveStrafe;
      float forward = MathHelper.clamp(rawForward, -1.0f, 1.0f),
          strafe = MathHelper.clamp(rawStrafe, -1.0f, 1.0f);
      if (!config.isApplyRotation() && (forward != 0f || strafe != 0f)) {
        double rec = Math.toRadians(frame.yaw),
            cur = Math.toRadians(player.rotationYaw),
            worldX = -forward * Math.sin(rec) + strafe * Math.cos(rec),
            worldZ = forward * Math.cos(rec) + strafe * Math.sin(rec),
            nf = -worldX * Math.sin(cur) + worldZ * Math.cos(cur),
            ns = worldX * Math.cos(cur) + worldZ * Math.sin(cur),
            peak = Math.max(Math.abs(nf), Math.abs(ns));
        if (peak > 1d) {
          nf /= peak;
          ns /= peak;
        }
        forward = (float) nf;
        strafe = (float) ns;
      }
      // Vanilla slows sneaking ×0.3 inside updatePlayerMoveState before our hook, so scale BEFORE
      // stabilization: otherwise the correction is computed from the full input but applied to the
      // reduced one and loses 70% of its authority.
      if (replaySneak) {
        forward *= 0.3f;
        strafe *= 0.3f;
      }
      if (!stabilizeRoute(player, frame, forward, strafe)) {
        BoatReplayInput.clear(event.getMovementInput());
        event.getMovementInput().moveForward = 0f;
        event.getMovementInput().moveStrafe = 0f;
        event.getMovementInput().jump = false;
        event.getMovementInput().sneak = false;
        return false;
      }
      event.getMovementInput().moveForward = adjustedForward;
      event.getMovementInput().moveStrafe = adjustedStrafe;
    }
    event.getMovementInput().jump =
        config.isPlaybackJump() && (frame.hasKeyMask ? frame.key(Frame.K_JUMP) : frame.jump);
    // Replay the recorded sprint state or sprint key: opening chat resets keys, and double-tap
    // sprint does not set the sprint key bit.
    wantSprint =
        config.isPlaybackSprint()
            && (frame.sprint || (frame.hasKeyMask && frame.key(Frame.K_SPRINT)))
            && !replaySneak;
    if (!config.isPlaybackMovement()) sprintAssist = false;
    player.setSprinting(wantSprint || sprintAssist);
    return true;
  }

  private boolean stabilizeRoute(EntityPlayerSP player, Frame frame, float forward, float strafe) {
    adjustedForward = forward;
    adjustedStrafe = strafe;
    boolean prevAssist = sprintAssist;
    sprintAssist = false;
    // The game refused sprinting (hunger ≤ 6 etc.): stop requesting it for the rest of the run.
    if (prevAssist && !player.isSprinting()) sprintRefused = true;
    if (!trajectory.isReady()) return true;
    if (Math.abs(config.getPlaybackSpeed() - 1f) > 0.001f) {
      showSpeedNotice();
      return true;
    }
    double errorX = trajectory.expectedPreX() - player.posX,
        errorZ = trajectory.expectedPreZ() - player.posZ,
        errorSq = errorX * errorX + errorZ * errorZ;
    MirrorDebug.route(
        manager.getPlaybackRunId(),
        manager.getCurrentFrameIndex(),
        trajectory.expectedPreX(),
        trajectory.expectedPreZ(),
        player.posX,
        player.posZ,
        Math.sqrt(errorSq),
        player.onGround);
    boolean elytraRoute = frame.elytra || player.isElytraFlying();
    double abortSq = elytraRoute ? ELYTRA_ABORT_SQ : GROUND_ABORT_SQ;
    int abortTicks = elytraRoute ? ELYTRA_ABORT_TICKS : 1;
    if (config.isStopOnRouteDeviation() && errorSq > abortSq) {
      // On elytra drift is tolerated longer: flight physics catches up with the route by itself,
      // and an instant abort would stop normal flights.
      if (++deviationTicks < abortTicks) return true;
      deviationTicks = 0;
      abortForDeviation(player, errorSq, abortSq, elytraRoute);
      return false;
    }
    deviationTicks = 0;
    // When enabled, at speed 1.00 with a ready trajectory, excessive drift stops playback even with
    // route
    // correction off: 3 blocks on the
    // ground, 16 blocks and a 2-second grace on elytra.
    if (!config.isRouteStabilization()) return true;
    if (player.isRiding()
        || player.capabilities.isFlying
        || player.isElytraFlying()
        || frame.elytra
        || player.isInWater()
        || player.isInLava()
        || player.isOnLadder()) return true;
    applyCorrection(player, frame, forward, strafe, errorX, errorZ, errorSq, prevAssist);
    return true;
  }

  /** Playback speed is not 1.00: stabilization is off, the warning is shown once. */
  private void showSpeedNotice() {
    if (!speedNoticeShown) {
      speedNoticeShown = true;
      MirrorDebug.log(
          "ROUTE",
          "stabilization disabled: playback speed " + config.getPlaybackSpeed() + " != 1.00");
      state.sendMsg(Lang.t("msg.playback_speed_is_not_1"));
    }
  }

  /** Drift above the threshold: playback stops with an explanation in chat. */
  private void abortForDeviation(
      EntityPlayerSP player, double errorSq, double abortSq, boolean elytraRoute) {
    MirrorDebug.log(
        "ROUTE",
        "ABORT: deviation "
            + String.format(Locale.ROOT, "%.2f", Double.valueOf(Math.sqrt(errorSq)))
            + " blocks over limit "
            + String.format(Locale.ROOT, "%.1f", Double.valueOf(Math.sqrt(abortSq)))
            + " at frame "
            + manager.getCurrentFrameIndex()
            + " (run "
            + manager.getPlaybackRunId()
            + ", onGround="
            + player.onGround
            + ", elytra="
            + elytraRoute
            + ", water="
            + player.isInWater()
            + ", riding="
            + player.isRiding()
            + ")");
    MirrorDebug.csvClose();
    if (elytraRoute) state.sendMsg(Lang.t("msg.playback_stopped_in_flight_route"));
    else state.sendMsg(Lang.t("msg.playback_stopped_route_deviation_is"));
    manager.stopPlayback();
    handler.resetAll();
  }

  /**
   * Input correction towards the expected route point and a short sprint when the player cannot
   * catch up with the route otherwise.
   */
  private void applyCorrection(
      EntityPlayerSP player,
      Frame frame,
      float forward,
      float strafe,
      double errorX,
      double errorZ,
      double errorSq,
      boolean prevAssist) {
    double velocityX = trajectory.expectedVelocityX() - player.motionX,
        velocityZ = trajectory.expectedVelocityZ() - player.motionZ,
        velocitySq = velocityX * velocityX + velocityZ * velocityZ;
    if (errorSq < ON_ROUTE_SQ && velocitySq < ON_ROUTE_VEL_SQ) return;
    float positionGain = player.onGround ? GROUND_POS_GAIN : AIR_POS_GAIN,
        velocityGain = player.onGround ? GROUND_VEL_GAIN : AIR_VEL_GAIN,
        limit = player.onGround ? GROUND_INPUT_LIMIT : AIR_INPUT_LIMIT;
    double worldX = errorX * positionGain + velocityX * velocityGain,
        worldZ = errorZ * positionGain + velocityZ * velocityGain,
        yaw = Math.toRadians(player.rotationYaw),
        localForward = -worldX * Math.sin(yaw) + worldZ * Math.cos(yaw),
        localStrafe = worldX * Math.cos(yaw) + worldZ * Math.sin(yaw);
    adjustedForward =
        MathHelper.clamp(forward + MathHelper.clamp((float) localForward, -limit, limit), -1f, 1f);
    adjustedStrafe =
        MathHelper.clamp(strafe + MathHelper.clamp((float) localStrafe, -limit, limit), -1f, 1f);
    // Vanilla drops sprint at moveForward<0.8: do not let the correction push input below this
    // threshold.
    boolean sneakNow =
        config.isPlaybackSneak() && (frame.hasKeyMask ? frame.key(Frame.K_SNEAK) : frame.sneak);
    boolean recSprint =
        config.isPlaybackSprint()
            && (frame.sprint || (frame.hasKeyMask && frame.key(Frame.K_SPRINT)))
            && !sneakNow;
    if (recSprint && forward >= SPRINT_MIN_FORWARD && adjustedForward < SPRINT_MIN_FORWARD)
      adjustedForward = SPRINT_MIN_FORWARD;
    // Lagging at full forward speed cannot be caught up: input is already 1.0. A brief sprint is
    // the only speed reserve.
    double fwdError = -errorX * Math.sin(yaw) + errorZ * Math.cos(yaw);
    boolean canAssist =
        config.isPlaybackSprint()
            && !recSprint
            && !sneakNow
            && player.onGround
            && adjustedForward >= SPRINT_MIN_FORWARD
            && !(frame.hasKeyMask && frame.key(Frame.K_ATTACK))
            && !sprintRefused;
    sprintAssist = canAssist && fwdError > (prevAssist ? ASSIST_KEEP_LAG : ASSIST_START_LAG);
  }
  /** On the ground playback aborts on drift over 3 blocks (squared distance). */
  private static final double GROUND_ABORT_SQ = 9d;
  /** Closer than 1 cm and almost the same velocity: no correction needed. */
  private static final double ON_ROUTE_SQ = 1.0E-4d, ON_ROUTE_VEL_SQ = 6.4E-5d;
  /**
   * Input correction coefficients: the share of position and velocity error converted to input, and
   * the per-tick limit. Control is weak in the air, so everything is softer.
   */
  private static final float GROUND_POS_GAIN = 0.45f,
      GROUND_VEL_GAIN = 0.18f,
      GROUND_INPUT_LIMIT = 0.28f,
      AIR_POS_GAIN = 0.12f,
      AIR_VEL_GAIN = 0.05f,
      AIR_INPUT_LIMIT = 0.08f;
  /** Vanilla drops sprint at moveForward below 0.8. */
  private static final float SPRINT_MIN_FORWARD = 0.8f;
  /** Lag along the path (blocks) at which sprint assist turns on and down to which it is held. */
  private static final double ASSIST_START_LAG = 0.15d, ASSIST_KEEP_LAG = 0.05d;

  // Elytra flight: its own drift threshold and grace period. The ground limit of 3 blocks is too
  // tight: on a glide the speed reaches 2.5 blocks per tick, and keys cannot correct the route.
  private static final double ELYTRA_ABORT_SQ = 256d,
      ELYTRA_POS_GAIN = 0.35d,
      ELYTRA_VEL_LIMIT = 0.35d;
  private static final int ELYTRA_ABORT_TICKS = 40, ELYTRA_WAIT_TICKS = 20;
  private int deviationTicks = 0, elytraMismatchTicks = 0;
  private boolean elytraNoticeShown = false;
  /**
   * Elytra flight is replayed by velocity, not keys: vanilla computes the glide from motion and
   * look, and moveForward/moveStrafe play no part in this state. Use the previous frame's recorded
   * velocity when it is also an elytra frame and add a bounded drift correction to reduce
   * accumulated drift.
   */
  void applyElytraFlight(EntityPlayerSP player, Frame frame) {
    if (frame == null || !frame.elytra) {
      elytraMismatchTicks = 0;
      return;
    }
    if (!config.isPlaybackMovement()) return;
    if (!player.isElytraFlying()) {
      // Flight is not yet confirmed by the server (START_FALL_FLYING goes over the network) or the
      // player has no elytra.
      if (++elytraMismatchTicks >= ELYTRA_WAIT_TICKS && !elytraNoticeShown) {
        elytraNoticeShown = true;
        MirrorDebug.log(
            "PLAYBACK",
            "recorded elytra flight without actual flight at frame "
                + manager.getCurrentFrameIndex());
        state.sendMsg(Lang.t("msg.the_recording_flies_with_elytra"));
      }
      return;
    }
    elytraMismatchTicks = 0;
    Frame previous = manager.getPreviousPlaybackFrame();
    double baseX = player.motionX, baseY = player.motionY, baseZ = player.motionZ;
    if (previous != null && previous.elytra) {
      baseX = previous.motionX;
      baseY = previous.motionY;
      baseZ = previous.motionZ;
    }
    // Drift correction is applied only at speed 1.00: in sped-up playback frames and physics run at
    // different paces.
    if (trajectory.isReady() && Math.abs(config.getPlaybackSpeed() - 1f) < 0.001f) {
      baseX +=
          MathHelper.clamp(
              (trajectory.expectedPreX() - player.posX) * ELYTRA_POS_GAIN,
              -ELYTRA_VEL_LIMIT,
              ELYTRA_VEL_LIMIT);
      baseY +=
          MathHelper.clamp(
              (trajectory.expectedPreY() - player.posY) * ELYTRA_POS_GAIN,
              -ELYTRA_VEL_LIMIT,
              ELYTRA_VEL_LIMIT);
      baseZ +=
          MathHelper.clamp(
              (trajectory.expectedPreZ() - player.posZ) * ELYTRA_POS_GAIN,
              -ELYTRA_VEL_LIMIT,
              ELYTRA_VEL_LIMIT);
    }
    player.motionX = baseX;
    player.motionY = baseY;
    player.motionZ = baseZ;
  }

  void resetStabilizer() {
    trajectory.reset();
    speedNoticeShown = false;
    sprintAssist = false;
    sprintRefused = false;
    deviationTicks = 0;
    elytraMismatchTicks = 0;
    elytraNoticeShown = false;
  }

  /** Sprint is removed from the player, route stabilization starts from scratch. */
  @Override
  public void reset() {
    if (mc.player != null) {
      mc.player.setSprinting(false);
      if (mc.player.getRidingEntity() instanceof EntityBoat)
        BoatReplayInput.clear(mc.player.movementInput);
    }
    wantSprint = false;
    sprintAssist = false;
    resetStabilizer();
  }
}

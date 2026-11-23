package com.mirror.recorder.handler;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Playback camera: frame rotation before clicks and a smooth angle on render frames. */
@SideOnly(Side.CLIENT)
final class CameraFeature implements ReplayState.Feature {
  private final RecordingHandler handler;
  private final ReplayState state;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RecorderManager manager;
  private final RecorderConfig config;

  CameraFeature(RecordingHandler handler, ReplayState state) {
    this.handler = handler;
    this.state = state;
    this.manager = state.manager;
    this.config = state.config;
  }

  private final BoatReplayCamera boatCamera = new BoatReplayCamera();
  private long rotRun = -1L;
  private int rotFrame = -1;
  private float savedLookYaw = 0f,
      savedLookPitch = 0f,
      savedPrevLookYaw = 0f,
      savedPrevLookPitch = 0f;
  private EntityPlayerSP renderPlayer;
  private EntityPlayerSP rotationPlayer;
  private float savedHeadYaw, savedPrevHeadYaw;
  private float offsetYaw, offsetPitch;
  private int ticksLeft = 1;
  private long blendRun = -1L;
  /**
   * Rotation anchor per run: at playback start the camera is set to the recorded start angle, then
   * playback follows deltas with a smooth correction towards the recorded angle (at most
   * MAX_ROT_CORRECTION per tick).
   */
  private long rotAnchorRun = -1L;

  private boolean rotAnchorSet = false;
  /**
   * Start of a playback tick: remove the render-frame interpolated angle and set the current
   * frame's angle. If this frame was already applied, the remaining delta is not added to it.
   */
  void applyTickRotation() {
    popInterpolatedLook();
    offsetYaw = LookSmoothing.nextTickOffset(offsetYaw, ticksLeft);
    offsetPitch = LookSmoothing.nextTickOffset(offsetPitch, ticksLeft);
    ticksLeft = manager.getFrameTicksLeft();
    if (!mc.isGamePaused() && !manager.isPaused()) {
      if (mc.player != null) beginBoatTick(mc.player);
      else boatCamera.reset();
      applyFrameRotation();
    }
  }

  private void beginBoatTick(EntityPlayerSP player) {
    boatCamera.beginTick(
        player,
        player.getRidingEntity() instanceof EntityBoat
            ? (EntityBoat) player.getRidingEntity()
            : null);
  }
  /** World boundary: neither the old anchor nor its visual residual belongs to the new world. */
  void onWorldReset() {
    popInterpolatedLook();
    boatCamera.reset();
    rotAnchorSet = false;
    rotAnchorRun = -1L;
    offsetYaw = 0f;
    offsetPitch = 0f;
  }

  private static final float MAX_ROT_CORRECTION = 10.0f;
  /** InputUpdate phase: reconcile the click angle with vanilla's later boat passenger rotation. */
  void applyPlaybackRotation() {
    applyFrameRotation();
    EntityPlayerSP player = mc.player;
    Frame frame = manager.getCurrentPlaybackFrame();
    if (player != null
        && frame != null
        && manager.isPlaying()
        && config.isApplyRotation()
        && player.getRidingEntity() instanceof EntityBoat) {
      boatCamera.preparePassenger(player, (EntityBoat) player.getRidingEntity(), frame);
    }
  }

  /**
   * Tick START sets the click angle; boat prev/current interpolation is repaired at InputUpdate.
   */
  private void applyFrameRotation() {
    EntityPlayerSP player = mc.player;
    if (player == null || !manager.isPlaying()) return;
    Frame frame = manager.getCurrentPlaybackFrame();
    if (frame == null) return;
    if (rotationPlayer != player) {
      reset();
      rotationPlayer = player;
      beginBoatTick(player);
    }
    long run = manager.getPlaybackRunId();
    int index = manager.getCurrentFrameIndex();
    if (rotRun == run && rotFrame == index) return;
    rotRun = run;
    rotFrame = index;
    float carryYaw = manager.consumeCarryYaw(), carryPitch = manager.consumeCarryPitch();
    if (blendRun != run) {
      blendRun = run;
      handler.gui.clearQueue();
    }
    if (rotAnchorRun != run) {
      rotAnchorRun = run;
      rotAnchorSet = false;
      offsetYaw = 0f;
      offsetPitch = 0f;
      ticksLeft = manager.getFrameTicksLeft();
      if (frame.hasKeyMask && config.isApplyRotation()) {
        // The recorded base already includes skipped deltas before this world/player anchor.
        carryYaw = 0f;
        carryPitch = 0f;
        float baseYaw = frame.yaw - frame.dYaw,
            basePitch = MathHelper.clamp(frame.pitch - frame.dPitch, -90.0f, 90.0f);
        player.rotationYaw = baseYaw;
        player.rotationPitch = basePitch;
        player.prevRotationYaw = baseYaw;
        player.prevRotationPitch = basePitch;
        player.rotationYawHead = baseYaw;
        boatCamera.anchor(baseYaw, basePitch);
        rotAnchorSet = true;
      }
    }
    if (!config.isApplyRotation()) {
      offsetYaw = 0f;
      offsetPitch = 0f;
      return;
    }
    // New recordings: render-time samples have already moved the real player via vanilla turn().
    // Reconcile at the tick boundary before clicks, without forcing the model's head/body fields.
    if (useNativeFrame(frame, player)) {
      offsetYaw = offsetPitch = 0f;
      turnTo(player, frame.yaw, frame.pitch);
      return;
    }
    float targetYaw, targetPitch;
    // On elytra, pitch and yaw are steering and throttle: a one-degree error pushes you meters off
    // the trajectory, and correcting by 10° deltas per tick only accumulated it. In flight frames
    // the recorded angle is set as is.
    // Boat END look already includes native passenger yaw, not only mouse deltas.
    if (frame.hasKeyMask && !frame.elytra && !(player.getRidingEntity() instanceof EntityBoat)) {
      float stepYaw = player.rotationYaw + carryYaw + frame.dYaw,
          stepPitch = player.rotationPitch + carryPitch + frame.dPitch;
      // Correction towards the recorded absolute angle, but at most MAX_ROT_CORRECTION degrees per
      // tick: the accumulated delta error is damped smoothly, without camera jerks.
      if (rotAnchorSet) {
        stepYaw +=
            MathHelper.clamp(
                MathHelper.wrapDegrees(frame.yaw - stepYaw),
                -MAX_ROT_CORRECTION,
                MAX_ROT_CORRECTION);
        stepPitch +=
            MathHelper.clamp(frame.pitch - stepPitch, -MAX_ROT_CORRECTION, MAX_ROT_CORRECTION);
      }
      targetYaw = stepYaw;
      targetPitch = MathHelper.clamp(stepPitch, -90.0f, 90.0f);
    } else {
      targetYaw = frame.yaw;
      targetPitch = MathHelper.clamp(frame.pitch, -90.0f, 90.0f);
    }
    float lookDYaw = MathHelper.wrapDegrees(targetYaw - player.rotationYaw),
        lookDPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f) - player.rotationPitch;
    if (player.getRidingEntity() instanceof EntityBoat) {
      // Passenger rotation has native prev/current interpolation and a ±105° clamp. Our walking
      // render residual would fight that clamp and remove the physical boat turn a second time.
      offsetYaw = 0f;
      offsetPitch = 0f;
    } else {
      offsetYaw += lookDYaw;
      offsetPitch += lookDPitch;
    }
    player.rotationYaw = player.rotationYaw + lookDYaw;
    player.rotationPitch = player.rotationPitch + lookDPitch;
    player.rotationYawHead = player.rotationYaw;
  }

  private boolean useNativeFrame(Frame frame, EntityPlayerSP player) {
    return frame != null && frame.lookSamples != null && !frame.elytra
        && player != null && player.getRidingEntity() == null
        && Math.abs(config.getPlaybackSpeed() - 1.0f) < 0.0001f;
  }

  boolean useNativeTimeline() {
    Frame frame = manager.getCurrentPlaybackFrame();
    Frame previous = manager.getPreviousPlaybackFrame();
    EntityPlayerSP player = mc.player;
    return useNativeFrame(frame, player) && previous != null && !frame.worldReset
        && manager.isPlaying() && config.isApplyRotation()
        && rotRun == manager.getPlaybackRunId() && rotationPlayer == player;
  }

  /** Apply the recorded trajectory through the same turn method used by Minecraft's mouse. */
  void applyNativeTimeline(float partialTicks) {
    if (!useNativeTimeline()) return;
    Frame frame = manager.getCurrentPlaybackFrame();
    Frame previous = manager.getPreviousPlaybackFrame();
    if (frame == null || previous == null) return;
    float yaw = LookTimeline.value(frame.lookSamples, partialTicks, previous.yaw, frame.yaw, 1);
    float pitch = LookTimeline.value(frame.lookSamples, partialTicks, previous.pitch, frame.pitch, 2);
    turnTo(mc.player, yaw, pitch);
  }

  private static void turnTo(EntityPlayerSP player, float yaw, float pitch) {
    // Entity.turn adds 0.15 * yaw and subtracts 0.15 * pitch; it also updates prevRotation.
    player.turn((yaw - player.rotationYaw) / 0.15f,
        (player.rotationPitch - MathHelper.clamp(pitch, -90f, 90f)) / 0.15f);
  }

  void pushInterpolatedLook(float partialTicks) {
    EntityPlayerSP player = mc.player;
    if (player == null
        || renderPlayer != null
        || player != rotationPlayer
        || rotRun != manager.getPlaybackRunId()
        || player.getRidingEntity() instanceof EntityBoat) return;
    savedLookYaw = player.rotationYaw;
    savedLookPitch = player.rotationPitch;
    savedPrevLookYaw = player.prevRotationYaw;
    savedPrevLookPitch = player.prevRotationPitch;
    savedHeadYaw = player.rotationYawHead;
    savedPrevHeadYaw = player.prevRotationYawHead;
    float yaw = savedLookYaw - LookSmoothing.renderOffset(offsetYaw, partialTicks, ticksLeft),
        pitch =
            MathHelper.clamp(
                savedLookPitch - LookSmoothing.renderOffset(offsetPitch, partialTicks, ticksLeft),
                -90f,
                90f);
    player.rotationYaw = yaw;
    player.rotationPitch = pitch;
    player.prevRotationYaw = yaw;
    player.prevRotationPitch = pitch;
    player.rotationYawHead = yaw;
    player.prevRotationYawHead = yaw;
    renderPlayer = player;
  }

  void popInterpolatedLook() {
    EntityPlayerSP player = renderPlayer;
    renderPlayer = null;
    if (player == null) return;
    player.rotationYaw = savedLookYaw;
    player.rotationPitch = savedLookPitch;
    player.prevRotationYaw = savedPrevLookYaw;
    player.prevRotationPitch = savedPrevLookPitch;
    player.rotationYawHead = savedHeadYaw;
    player.prevRotationYawHead = savedPrevHeadYaw;
  }

  /** The render-frame angle is returned to the player's real angle. */
  @Override
  public void reset() {
    popInterpolatedLook();
    boatCamera.reset();
    rotationPlayer = null;
    rotAnchorRun = -1L;
    rotAnchorSet = false;
    blendRun = -1L;
    ticksLeft = 1;
    offsetYaw = 0f;
    offsetPitch = 0f;
    rotRun = -1L;
    rotFrame = -1;
  }
}

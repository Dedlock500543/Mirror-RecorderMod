package com.mirror.recorder.handler;

import com.mirror.recorder.manager.RecorderManager;
import com.mirror.recorder.model.Frame;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Expected playback trajectory: where the player should be on each frame, anchored to the player's
 * position when calibration begins after frame 0 or a world reset.
 */
@SideOnly(Side.CLIENT)
public final class PlaybackTrajectory {
  private final RecorderManager manager;
  private long sessionId = -1L, runId = -1L;
  private boolean originSet = false, ready = false;
  private double originX,
      originY,
      originZ,
      originMotionX,
      originMotionZ,
      recordedBaseX,
      recordedBaseY,
      recordedBaseZ;

  public PlaybackTrajectory(RecorderManager manager) {
    this.manager = manager;
  }
  /**
   * Each run (and each new loop lap) is calibrated from the current position and the first recorded
   * frame. Accumulated drift of the previous lap is not carried over.
   */
  public void updateCalibration(EntityPlayerSP player) {
    if (player == null || !manager.isPlaying()) {
      reset();
      return;
    }
    long currentRun = manager.getPlaybackRunId(), currentSession = manager.getPlaybackSessionId();
    if (currentRun != runId || currentSession != sessionId) {
      reset();
      runId = currentRun;
      sessionId = currentSession;
    }
    // World boundary: coordinates of the old world do not apply to the new one. Calibration is
    // skipped for the worldReset frame
    // and the anchor is reset, otherwise the expected point shifts by the
    // coordinate difference between worlds and the stabilizer aborts playback as "deviation over 3
    // blocks".
    Frame current = manager.getCurrentPlaybackFrame();
    if (current != null && current.worldReset) {
      originSet = false;
      ready = false;
      return;
    }
    Frame first = baseFrame();
    if (!originSet) {
      // Frame 0 of a recording is the state AFTER its tick: first.x already includes the first
      // tick's movement. So the origin is taken one tick later, once frame 0's tick has also run in
      // playback. Otherwise the whole expected line is shifted by the first tick's movement (~0.22
      // blocks walking, ~0.29 sprinting) and the stabilizer pushes against that shift forever. A
      // recording started standing still has zero shift.
      if (manager.getCurrentFrameIndex() < 1) return;
      originX = player.posX;
      originY = player.posY;
      originZ = player.posZ;
      originMotionX = player.motionX;
      originMotionZ = player.motionZ;
      originSet = true;
      if (first != null) {
        recordedBaseX = first.x;
        recordedBaseY = first.y;
        recordedBaseZ = first.z;
        ready = true;
      }
      return;
    }
    if (!ready && first != null) {
      recordedBaseX = first.x;
      recordedBaseY = first.y;
      recordedBaseZ = first.z;
      ready = true;
    }
  }
  /**
   * The trajectory base is the previous frame: at the start of a run that is frame 0, after a world
   * change the first frame of the new world.
   */
  private Frame baseFrame() {
    Frame previous = manager.getCurrentFrameIndex() > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous != null ? previous : manager.getPlaybackFirstFrame();
  }

  public boolean isReady() {
    return ready && originSet && manager.isPlaying() && manager.getPlaybackRunId() == runId;
  }

  public double expectedX(Frame frame) {
    return originX + (frame.x - recordedBaseX);
  }

  public double expectedY(Frame frame) {
    return originY + (frame.y - recordedBaseY);
  }

  public double expectedZ(Frame frame) {
    return originZ + (frame.z - recordedBaseZ);
  }

  public double expectedPreX() {
    int idx = manager.getCurrentFrameIndex();
    Frame previous = idx > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous == null ? originX : expectedX(previous);
  }

  public double expectedPreY() {
    int idx = manager.getCurrentFrameIndex();
    Frame previous = idx > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous == null ? originY : expectedY(previous);
  }

  public double expectedPreZ() {
    int idx = manager.getCurrentFrameIndex();
    Frame previous = idx > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous == null ? originZ : expectedZ(previous);
  }

  public double expectedVelocityX() {
    Frame previous = manager.getCurrentFrameIndex() > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous == null ? originMotionX : previous.motionX;
  }

  public double expectedVelocityZ() {
    Frame previous = manager.getCurrentFrameIndex() > 0 ? manager.getPreviousPlaybackFrame() : null;
    return previous == null ? originMotionZ : previous.motionZ;
  }

  public void reset() {
    sessionId = -1L;
    runId = -1L;
    originSet = false;
    ready = false;
    originX =
        originY =
            originZ =
                originMotionX = originMotionZ = recordedBaseX = recordedBaseY = recordedBaseZ = 0d;
  }
}

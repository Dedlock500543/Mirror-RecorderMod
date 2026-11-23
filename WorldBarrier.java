package com.mirror.recorder.manager;

import com.mirror.recorder.model.Frame;
import java.util.List;

/** Segment admission is ordinal: visiting the same dimension twice is still two transitions. */
public final class WorldBarrier {
  private long epoch;
  private int admitted;
  private boolean identified;
  private int[] sequences = new int[0];

  public void start(List<Frame> frames, long currentEpoch) {
    epoch = currentEpoch;
    admitted = 0;
    identified = !frames.isEmpty() && frames.get(0).worldSequence == 0;
    sequences = new int[frames.size()];
    int sequence = 0;
    for (int i = 0; i < frames.size(); i++) {
      // A legacy reset on frame zero describes the initial world, not a future portal.
      if (i > 0 && frames.get(i).worldReset) sequence++;
      sequences[i] = identified ? Math.max(0, frames.get(i).worldSequence) : sequence;
    }
  }

  public boolean blocked(List<Frame> frames, int index) {
    return index < frames.size() && sequence(frames, index) > admitted;
  }

  private int sequence(List<Frame> frames, int index) {
    return sequences[index];
  }

  /** -1 means incompatible, including a legacy transition without a recoverable boundary. */
  public int synchronize(List<Frame> frames, int index, long currentEpoch, int dimension) {
    if (currentEpoch == epoch) {
      if (identified
          && index < frames.size()
          && !blocked(frames, index)
          && frames.get(index).worldDimension != dimension) return -1;
      return index;
    }
    int wanted = admitted + (int) (currentEpoch - epoch);
    epoch = currentEpoch;
    int marker = findMarker(frames, wanted);
    // Unknown legacy boundaries must not send old-world actions into a new world. Search is
    // bounded by the recording, and missing evidence ends the run instead of inventing a match.
    if (marker < 0) return -1;
    Frame frame = frames.get(marker);
    if (identified && frame.worldDimension != dimension) return -1;
    admitted = wanted;
    return marker;
  }

  private int findMarker(List<Frame> frames, int wanted) {
    int sequence = 0;
    for (int i = 0; i < frames.size(); i++) {
      Frame frame = frames.get(i);
      if (i == 0 || !frame.worldReset) continue;
      sequence++;
      if ((identified ? frame.worldSequence : sequence) == wanted) return i;
    }
    return -1;
  }

  public static int limitSteps(List<Frame> frames, int index, int steps) {
    int allowed = 0;
    for (int i = 1; i <= steps && index + i < frames.size(); i++) {
      // A boundary must be the current frame, never a carried/skipped frame. Finish the old
      // segment first so its events can still be delivered in the old world.
      if (frames.get(index + i).worldReset) return i == 1 ? 1 : i - 1;
      allowed = i;
    }
    return index + steps >= frames.size() ? steps : allowed;
  }
}

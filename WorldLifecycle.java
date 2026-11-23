package com.mirror.recorder.manager;

import java.util.concurrent.atomic.AtomicBoolean;

/** Connection and entity readiness, independent of event ordering and elapsed time. */
public final class WorldLifecycle {
  public enum Phase {
    ACTIVE,
    WAITING_WORLD,
    SYNCING_PLAYER,
    DISCONNECTED,
    CONFIRMED_DEAD
  }

  private Phase phase = Phase.WAITING_WORLD;
  private Object world, player;
  private long epoch;
  private volatile boolean disconnected;
  private final AtomicBoolean disconnectPending = new AtomicBoolean();

  public void connected() {
    disconnected = false;
  }

  public void disconnected() {
    // Latch the terminal event even if a new connection arrives before the next client tick.
    disconnectPending.set(true);
    disconnected = true;
  }

  public Phase observe(Object nextWorld, Object nextPlayer, boolean ready, boolean dead) {
    if (disconnected) {
      phase = Phase.DISCONNECTED;
      world = player = null;
      return phase;
    }
    if (nextWorld == null) phase = Phase.WAITING_WORLD;
    else if (nextPlayer == null || !ready) phase = Phase.SYNCING_PLAYER;
    else {
      // Admission, not constructor/unload order, defines a transition. Same-dimension respawn
      // replaces only the player; a dimension transfer replaces both, but is one transition.
      if (world != null && (world != nextWorld || player != nextPlayer)) epoch++;
      world = nextWorld;
      player = nextPlayer;
      phase = dead ? Phase.CONFIRMED_DEAD : Phase.ACTIVE;
    }
    return phase;
  }

  public void suspend() {
    if (phase != Phase.DISCONNECTED) phase = Phase.WAITING_WORLD;
  }

  public boolean consumeDisconnect() {
    return disconnectPending.getAndSet(false);
  }

  public boolean isActive() {
    return phase == Phase.ACTIVE;
  }

  public Phase getPhase() {
    return phase;
  }

  public long getEpoch() {
    return epoch;
  }
}

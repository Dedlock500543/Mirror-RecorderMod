package com.mirror.recorder.handler;

/** A pending disconnect belongs to the exact world and connection that finished the series. */
final class AutoLeaveRequest {
  private final Object world;
  private final Object connection;

  AutoLeaveRequest(Object world, Object connection) {
    this.world = world;
    this.connection = connection;
  }

  boolean matches(Object currentWorld, Object currentConnection) {
    return world != null
        && connection != null
        && world == currentWorld
        && connection == currentConnection;
  }
}

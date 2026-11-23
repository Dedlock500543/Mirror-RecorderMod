package com.mirror.recorder.handler;

/**
 * Where auto-leave is allowed. Leaving a world the client hosts itself (single player, also when it
 * is opened to LAN) shuts the integrated server down from inside a client tick: the client thread
 * then blocks in Minecraft.loadWorld until the still unpaused server has stopped and saved, and
 * every handler that runs later in the same tick sees no world. Vanilla only does that from the
 * pause menu with the server paused. A local world is therefore never unloaded by the mod; only a
 * connection to an external server (including a LAN world hosted by another player) is left.
 */
final class AutoLeavePolicy {
  enum Connection {
    /** No world or no network handler: nothing to leave. */
    NONE,
    /** The integrated server of this client is running (single player, own LAN world). */
    LOCAL_WORLD,
    /** Connected to a server outside this client. */
    REMOTE_SERVER
  }

  private AutoLeavePolicy() {}

  static Connection classify(boolean worldPresent, boolean hasConnection, boolean integrated) {
    if (integrated) return Connection.LOCAL_WORLD;
    if (!worldPresent || !hasConnection) return Connection.NONE;
    return Connection.REMOTE_SERVER;
  }

  static boolean canLeave(Connection connection) {
    return connection == Connection.REMOTE_SERVER;
  }
}

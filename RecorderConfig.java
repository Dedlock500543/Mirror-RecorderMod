package com.mirror.recorder.config;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.handler.SoundFx;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All mod settings: reading and writing the Forge file, slot settings (writeSlotSettings /
 * applySlotSettings) and factory reset.
 */
public class RecorderConfig {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Config");
  private static final String CAT = "general";
  private final Configuration forge;
  private String language = "auto"; // auto = follow the Minecraft client language
  /** Units: startDelay and returnTimeoutSeconds are in seconds, pathLine* colors are 0..255. */
  private int activeSlot = 1,
      startDelay = 3,
      loopLimit = 0,
      pathLineR = 138,
      pathLineG = 43,
      pathLineB = 226,
      pathLineAlpha = 180,
      startMarkerMode = 2,
      returnTimeoutSeconds = 90,
      hudX = 8,
      hudY = 8;

  private boolean stopOnRouteDeviation = true, playbackModCommands = false;
  private boolean stopOnMove = true,
      applyRotation = true,
      routeStabilization = true,
      pathLineEnabled = true,
      showTooltips = true,
      autoReturnEnabled = true,
      cursorStabilization = true,
      skipLoopStartDelay = true;
  /**
   * Units: pathLineWidth is the GL line width in pixels. returnPositionTolerance is in blocks
   * horizontally from the start point. returnAlignSpeed is the largest movement input (0..1) during
   * fine positioning.
   */
  private float pathLineWidth = 3f, returnPositionTolerance = 0.12f, returnAlignSpeed = 0.8f;

  private boolean playbackJump = true,
      playbackSneak = true,
      playbackSprint = true,
      playbackChat = true,
      playbackMovement = true,
      playbackInteraction = true,
      preferredLoopMode = false;
  private boolean visibleChat = true, showPointer = true, recordAllKeys = true;
  // Stop on cursor movement: a setting separate from WASD, with a threshold in pixels per tick so
  // hand jitter does not break playback.
  private boolean stopOnMouseMove = false;
  private int stopOnMouseThreshold = 6;
  /**
   * Route line: pathLineLength is how many frames (ticks) ahead it shows, pathPointStep puts a
   * point every N frames.
   */
  private int pathLineStyle = 0,
      pathLineLength = 300,
      pathPointStep = 10,
      markerR = 138,
      markerG = 43,
      markerB = 226,
      markerAlpha = 180,
      markerLabelDistance = 32;

  private float pathPointSize = 0.15f,
      pathLineHeight = 0.1f,
      markerRadius = 0.7f,
      markerBeaconHeight = 8f;
  private boolean pathLineFade = false,
      pathLineThroughWalls = false,
      markerCustomColor = false,
      markerPulse = true,
      markerLabel = true,
      markerLabelUnlimited = false,
      markerThroughWalls = false;
  private float playbackSpeed = 1f;
  private boolean soundsEnabled = true;
  private boolean sendModMessages = true;
  private boolean autoDeleteModMessages = true, uiAnimationsEnabled = true;
  private int modMessageLifetimeSeconds = 5;
  private boolean guardOnDamage = true;
  private int chatSendIntervalMs =
      1200; // pause between recorded chat messages: server anti-spam limits differ
  private int autoCheckpointSeconds = 120; // autosave of the live recording, 0 = off
  private boolean autoLeaveEnabled = false;
  private int autoLeaveCycles = 10; // auto-leave the server after N completed loops
  private boolean autoLeaveTimed = false;
  private int autoLeaveMinutes = 30;
  // Diagnostics are off by default; enabled in the settings or via -Dmirror.debug=true /
  // -Dmirror.debug.csv=true.
  private boolean debug = false, debugCsv = false;

  public RecorderConfig(File f) {
    forge = new Configuration(f);
    load();
  }
  /**
   * Integer from the file, clamped to [min, max] here as well as by Forge: a hand-edited file may
   * hold any value.
   */
  private int intIn(String name, int def, int min, int max, String comment) {
    return Math.max(min, Math.min(max, forge.getInt(name, CAT, def, min, max, comment)));
  }

  public void load() {
    try {
      forge.load();
      loadGeneral();
      loadStopRules();
      loadPlayback();
      loadStartAndReturn();
      loadPathLine();
      loadStartMarker();
      if (forge.hasChanged()) forge.save();
    } catch (Exception e) {
      LOG.error("Config load failed", e);
    }
  }

  /** General: active slot, language, interface, sounds, autosave and diagnostics. */
  private void loadGeneral() {
    activeSlot = intIn("activeSlot", 1, 1, 100, "Active slot");
    setLanguage(
        forge.getString("language", CAT, "auto", "Interface language: auto, ru, en, uk, de or pl"));
    showTooltips =
        forge.getBoolean("showTooltips", CAT, true, "Show hover hints in the mod interface");
    hudX = forge.getInt("hudX", CAT, 8, 0, 10000, "Status HUD X");
    hudY = forge.getInt("hudY", CAT, 8, 0, 10000, "Status HUD Y");
    soundsEnabled = forge.getBoolean("soundsEnabled", CAT, true, "Play mod sounds");
    sendModMessages =
        forge.getBoolean("sendModMessages", CAT, true, "Show messages sent by Mirror Recorder");
    autoDeleteModMessages =
        forge.getBoolean(
            "autoDeleteModMessages",
            CAT,
            true,
            "Automatically delete only Mirror Recorder chat notices");
    modMessageLifetimeSeconds =
        intIn(
            "modMessageLifetimeSeconds",
            5,
            1,
            600,
            "Lifetime of Mirror Recorder chat notices in real seconds");
    uiAnimationsEnabled =
        forge.getBoolean(
            "uiAnimationsEnabled", CAT, true, "Animate Mirror Recorder interface transitions");
    SoundFx.setEnabled(soundsEnabled);
    // Through the setter: the file and the UI share one minimum (10 s); 1–9 from the file are
    // raised to 10.
    setAutoCheckpointSeconds(
        forge.getInt(
            "autoCheckpointSeconds",
            CAT,
            120,
            0,
            3600,
            "Auto-save an in-progress recording every N seconds (minimum 10), 0 disables it"));
    debug = forge.getBoolean("debug", CAT, false, "Write diagnostics to logs/mirror-debug.log");
    debugCsv =
        forge.getBoolean(
            "debugCsv", CAT, false, "Dump every playback run to logs/mirror-playback-*.csv");
    MirrorDebug.setEnabled(debug);
    MirrorDebug.setCsvEnabled(debugCsv);
  }

  /** Stop conditions: WASD, mouse movement and damage. */
  private void loadStopRules() {
    stopOnMove = forge.getBoolean("stopOnMove", CAT, true, "Stop on WASD");
    stopOnRouteDeviation =
        forge.getBoolean(
            "stopOnRouteDeviation", CAT, true, "Keep the existing route deviation stop enabled");
    stopOnMouseMove =
        forge.getBoolean("stopOnMouseMove", CAT, false, "Stop playback when the mouse is moved");
    stopOnMouseThreshold =
        intIn(
            "stopOnMouseThreshold",
            6,
            1,
            50,
            "How many pixels of mouse movement in one tick stop the playback");
    guardOnDamage =
        forge.getBoolean(
            "guardOnDamage", CAT, true, "Stop recording and playback when the player takes damage");
  }

  /** What playback replays, its speed and the chat send interval. */
  private void loadPlayback() {
    playbackModCommands =
        forge.getBoolean(
            "playbackModCommands",
            CAT,
            false,
            "Replay all Mirror commands, including stop and delete");
    playbackMovement = forge.getBoolean("playbackMovement", CAT, true, "Play movement");
    playbackJump = forge.getBoolean("playbackJump", CAT, true, "Play jump");
    playbackSneak = forge.getBoolean("playbackSneak", CAT, true, "Play sneak");
    playbackSprint = forge.getBoolean("playbackSprint", CAT, true, "Play sprint");
    playbackInteraction = forge.getBoolean("playbackInteraction", CAT, true, "Play clicks");
    playbackChat = forge.getBoolean("playbackChat", CAT, true, "Play chat");
    visibleChat =
        forge.getBoolean(
            "visibleChat",
            CAT,
            true,
            "Open the chat window on screen and type it letter by letter");
    recordAllKeys =
        forge.getBoolean("recordAllKeys", CAT, true, "Record every key the player presses");
    showPointer =
        forge.getBoolean("showPointer", CAT, true, "Draw the mod pointer while replaying");
    applyRotation = forge.getBoolean("applyRotation", CAT, true, "Apply rotation");
    routeStabilization =
        forge.getBoolean("routeStabilization", CAT, true, "Bounded input-only route stabilization");
    // Through the setter: the file value is snapped to the same 0.05 grid as the UI value,
    // otherwise cancelling in the settings would not restore the loaded value exactly.
    setPlaybackSpeed(
        (float) forge.getFloat("playbackSpeed", CAT, 1f, 0.25f, 4f, "Playback speed multiplier"));
    chatSendIntervalMs =
        forge.getInt(
            "chatSendIntervalMs",
            CAT,
            1200,
            200,
            5000,
            "Delay in ms between recorded chat messages (server anti-flood limits differ)");
  }

  /** Start, loop, auto-leave and the Baritone return to start. */
  private void loadStartAndReturn() {
    startDelay = intIn("startDelay", 3, 0, 60, "Delay");
    loopLimit =
        normalizeLoopLimit(
            forge.getInt(
                "loopLimit", CAT, 0, 0, 1000, "Loop limit: 0 is infinite, finite starts at 2"));
    autoLeaveEnabled =
        forge.getBoolean(
            "autoLeaveEnabled",
            CAT,
            false,
            "Leave the server automatically after a number of finished playback cycles");
    autoLeaveCycles =
        intIn(
            "autoLeaveCycles",
            10,
            1,
            1000,
            "How many finished cycles trigger the automatic disconnect");
    autoLeaveTimed =
        forge.getBoolean("autoLeaveTimed", CAT, false, "Use elapsed time instead of cycles");
    autoLeaveMinutes = intIn("autoLeaveMinutes", 30, 1, 525600, "Minutes before auto-leave");
    autoReturnEnabled =
        forge.getBoolean(
            "autoReturnEnabled",
            CAT,
            true,
            "Use Baritone and exact start alignment before playback");
    cursorStabilization =
        forge.getBoolean(
            "cursorStabilization", CAT, true, "Restore recorded cursor direction after Baritone");
    skipLoopStartDelay =
        forge.getBoolean(
            "skipLoopStartDelay",
            CAT,
            true,
            "Skip start delay after the first external loop cycle");
    returnPositionTolerance =
        (float)
            forge.getFloat(
                "returnPositionTolerance", CAT, 0.12f, 0.05f, 0.5f, "Exact return tolerance");
    returnAlignSpeed =
        (float) forge.getFloat("returnAlignSpeed", CAT, 0.8f, 0.2f, 1f, "Local align input cap");
    returnTimeoutSeconds =
        forge.getInt("returnTimeoutSeconds", CAT, 90, 15, 180, "Baritone return timeout");
  }

  /** Route line appearance. */
  private void loadPathLine() {
    pathLineEnabled = forge.getBoolean("pathLineEnabled", CAT, true, "Path line");
    pathLineR = forge.getInt("pathLineR", CAT, 138, 0, 255, "R");
    pathLineG = forge.getInt("pathLineG", CAT, 43, 0, 255, "G");
    pathLineB = forge.getInt("pathLineB", CAT, 226, 0, 255, "B");
    pathLineAlpha = forge.getInt("pathLineAlpha", CAT, 180, 0, 255, "A");
    pathLineWidth = (float) forge.getFloat("pathLineWidth", CAT, 3f, 1f, 10f, "Width");
    pathLineStyle =
        forge.getInt(
            "pathLineStyle", CAT, 0, 0, 2, "Path style: 0 line, 1 line and points, 2 points");
    pathLineLength =
        forge.getInt("pathLineLength", CAT, 300, 20, 300, "How many frames of the path are drawn");
    pathPointStep = forge.getInt("pathPointStep", CAT, 10, 1, 40, "Every Nth frame gets a point");
    pathPointSize = (float) forge.getFloat("pathPointSize", CAT, 0.15f, 0.05f, 0.5f, "Point size");
    pathLineHeight =
        (float) forge.getFloat("pathLineHeight", CAT, 0.1f, 0f, 1.5f, "Path height above ground");
    pathLineFade = forge.getBoolean("pathLineFade", CAT, false, "Fade the path to its end");
    pathLineThroughWalls =
        forge.getBoolean("pathLineThroughWalls", CAT, false, "Draw the path through blocks");
  }

  /** Start marker appearance. */
  private void loadStartMarker() {
    startMarkerMode =
        forge.getInt(
            "startMarkerMode", CAT, 2, 0, 3, "Start marker: 0 off, 1 point, 2 ring, 3 beacon");
    markerCustomColor =
        forge.getBoolean("markerCustomColor", CAT, false, "Marker uses its own color");
    markerR = forge.getInt("markerR", CAT, 138, 0, 255, "Marker R");
    markerG = forge.getInt("markerG", CAT, 43, 0, 255, "Marker G");
    markerB = forge.getInt("markerB", CAT, 226, 0, 255, "Marker B");
    markerAlpha = forge.getInt("markerAlpha", CAT, 180, 0, 255, "Marker A");
    markerRadius =
        (float) forge.getFloat("markerRadius", CAT, 0.7f, 0.2f, 3f, "Marker ring radius");
    markerBeaconHeight =
        (float) forge.getFloat("markerBeaconHeight", CAT, 8f, 1f, 64f, "Beacon height");
    markerPulse = forge.getBoolean("markerPulse", CAT, true, "Marker pulsing");
    markerLabel = forge.getBoolean("markerLabel", CAT, true, "Show the marker label");
    markerLabelDistance =
        forge.getInt("markerLabelDistance", CAT, 32, 4, 256, "Label draw distance in blocks");
    markerLabelUnlimited =
        forge.getBoolean("markerLabelUnlimited", CAT, false, "Show the label at any distance");
    markerThroughWalls =
        forge.getBoolean("markerThroughWalls", CAT, false, "Draw the marker through blocks");
  }

  public boolean save() {
    try {
      forge.get(CAT, "activeSlot", 1).set(activeSlot);
      forge.get(CAT, "language", "auto").set(language);
      forge.get(CAT, "stopOnMove", true).set(stopOnMove);
      forge.get(CAT, "stopOnRouteDeviation", true).set(stopOnRouteDeviation);
      forge.get(CAT, "playbackModCommands", false).set(playbackModCommands);
      forge.get(CAT, "stopOnMouseMove", false).set(stopOnMouseMove);
      forge.get(CAT, "stopOnMouseThreshold", 6).set(stopOnMouseThreshold);
      forge.get(CAT, "applyRotation", true).set(applyRotation);
      forge.get(CAT, "routeStabilization", true).set(routeStabilization);
      forge.get(CAT, "startDelay", 3).set(startDelay);
      forge.get(CAT, "loopLimit", 0).set(loopLimit);
      forge.get(CAT, "pathLineEnabled", true).set(pathLineEnabled);
      forge.get(CAT, "showTooltips", true).set(showTooltips);
      forge.get(CAT, "sendModMessages", true).set(sendModMessages);
      forge.get(CAT, "autoDeleteModMessages", true).set(autoDeleteModMessages);
      forge.get(CAT, "modMessageLifetimeSeconds", 5).set(modMessageLifetimeSeconds);
      forge.get(CAT, "uiAnimationsEnabled", true).set(uiAnimationsEnabled);
      forge.get(CAT, "pathLineR", 138).set(pathLineR);
      forge.get(CAT, "pathLineG", 43).set(pathLineG);
      forge.get(CAT, "pathLineB", 226).set(pathLineB);
      forge.get(CAT, "pathLineAlpha", 180).set(pathLineAlpha);
      forge.get(CAT, "pathLineWidth", 3.0).set((double) pathLineWidth);
      forge.get(CAT, "startMarkerMode", 2).set(startMarkerMode);
      forge.get(CAT, "autoReturnEnabled", true).set(autoReturnEnabled);
      forge.get(CAT, "cursorStabilization", true).set(cursorStabilization);
      forge.get(CAT, "skipLoopStartDelay", true).set(skipLoopStartDelay);
      forge.get(CAT, "returnPositionTolerance", 0.12).set((double) returnPositionTolerance);
      forge.get(CAT, "returnAlignSpeed", 0.8).set((double) returnAlignSpeed);
      forge.get(CAT, "returnTimeoutSeconds", 90).set(returnTimeoutSeconds);
      forge.get(CAT, "hudX", 8).set(hudX);
      forge.get(CAT, "hudY", 8).set(hudY);
      forge.get(CAT, "pathLineStyle", 0).set(pathLineStyle);
      forge.get(CAT, "pathLineLength", 300).set(pathLineLength);
      forge.get(CAT, "pathPointStep", 10).set(pathPointStep);
      forge.get(CAT, "pathPointSize", 0.15).set((double) pathPointSize);
      forge.get(CAT, "pathLineHeight", 0.1).set((double) pathLineHeight);
      forge.get(CAT, "pathLineFade", false).set(pathLineFade);
      forge.get(CAT, "pathLineThroughWalls", false).set(pathLineThroughWalls);
      forge.get(CAT, "markerCustomColor", false).set(markerCustomColor);
      forge.get(CAT, "markerR", 138).set(markerR);
      forge.get(CAT, "markerG", 43).set(markerG);
      forge.get(CAT, "markerB", 226).set(markerB);
      forge.get(CAT, "markerAlpha", 180).set(markerAlpha);
      forge.get(CAT, "markerRadius", 0.7).set((double) markerRadius);
      forge.get(CAT, "markerBeaconHeight", 8.0).set((double) markerBeaconHeight);
      forge.get(CAT, "markerPulse", true).set(markerPulse);
      forge.get(CAT, "markerLabel", true).set(markerLabel);
      forge.get(CAT, "markerLabelDistance", 32).set(markerLabelDistance);
      forge.get(CAT, "markerLabelUnlimited", false).set(markerLabelUnlimited);
      forge.get(CAT, "markerThroughWalls", false).set(markerThroughWalls);
      forge.get(CAT, "chatSendIntervalMs", 1200).set(chatSendIntervalMs);
      forge.get(CAT, "autoCheckpointSeconds", 120).set(autoCheckpointSeconds);
      forge.get(CAT, "guardOnDamage", true).set(guardOnDamage);
      forge.get(CAT, "playbackJump", true).set(playbackJump);
      forge.get(CAT, "playbackSneak", true).set(playbackSneak);
      forge.get(CAT, "playbackSprint", true).set(playbackSprint);
      forge.get(CAT, "playbackChat", true).set(playbackChat);
      forge.get(CAT, "visibleChat", true).set(visibleChat);
      forge.get(CAT, "showPointer", true).set(showPointer);
      forge.get(CAT, "recordAllKeys", true).set(recordAllKeys);
      forge.get(CAT, "playbackMovement", true).set(playbackMovement);
      forge.get(CAT, "playbackInteraction", true).set(playbackInteraction);
      forge.get(CAT, "playbackSpeed", 1.0).set((double) playbackSpeed);
      forge.get(CAT, "soundsEnabled", true).set(soundsEnabled);
      forge.get(CAT, "debug", false).set(debug);
      forge.get(CAT, "debugCsv", false).set(debugCsv);
      forge.get(CAT, "autoLeaveEnabled", false).set(autoLeaveEnabled);
      forge.get(CAT, "autoLeaveCycles", 10).set(autoLeaveCycles);
      forge.get(CAT, "autoLeaveTimed", false).set(autoLeaveTimed);
      forge.get(CAT, "autoLeaveMinutes", 30).set(autoLeaveMinutes);
      forge.save();
      return true;
    } catch (Exception e) {
      LOG.error("Config save failed", e);
      return false;
    }
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String v) {
    if (v != null
        && Arrays.asList("ru", "en", "uk", "de", "pl").contains(v.toLowerCase(Locale.ROOT)))
      language = v.toLowerCase(Locale.ROOT);
    else language = "auto";
    Lang.set(language);
  }

  public int getActiveSlot() {
    return activeSlot;
  }

  public void setActiveSlot(int v) {
    activeSlot = Math.max(1, Math.min(100, v));
  }

  public boolean isStopOnRouteDeviation() {
    return stopOnRouteDeviation;
  }

  public void setStopOnRouteDeviation(boolean v) {
    stopOnRouteDeviation = v;
  }

  public boolean isPlaybackModCommands() {
    return playbackModCommands;
  }

  public void setPlaybackModCommands(boolean v) {
    playbackModCommands = v;
  }

  public boolean isStopOnMove() {
    return stopOnMove;
  }

  public void setStopOnMove(boolean v) {
    stopOnMove = v;
  }

  public boolean isStopOnMouseMove() {
    return stopOnMouseMove;
  }

  public void setStopOnMouseMove(boolean v) {
    stopOnMouseMove = v;
  }

  public int getStopOnMouseThreshold() {
    return stopOnMouseThreshold;
  }

  public void setStopOnMouseThreshold(int v) {
    stopOnMouseThreshold = Math.max(1, Math.min(50, v));
  }

  public boolean isApplyRotation() {
    return applyRotation;
  }

  public void setApplyRotation(boolean v) {
    applyRotation = v;
  }

  public boolean isRouteStabilization() {
    return routeStabilization;
  }

  public void setRouteStabilization(boolean v) {
    routeStabilization = v;
  }

  public int getStartDelay() {
    return startDelay;
  }

  public void setStartDelay(int v) {
    startDelay = Math.max(0, Math.min(60, v));
  }

  public int getLoopLimit() {
    return loopLimit;
  }

  public void setLoopLimit(int v) {
    loopLimit = normalizeLoopLimit(v);
  }

  private int normalizeLoopLimit(int v) {
    if (v <= 0) return 0;
    return Math.max(2, Math.min(1000, v));
  }

  public boolean isAutoReturnEnabled() {
    return autoReturnEnabled;
  }

  public void setAutoReturnEnabled(boolean v) {
    autoReturnEnabled = v;
  }

  public boolean isCursorStabilization() {
    return cursorStabilization;
  }

  public void setCursorStabilization(boolean v) {
    cursorStabilization = v;
  }

  public boolean isSkipLoopStartDelay() {
    return skipLoopStartDelay;
  }

  public void setSkipLoopStartDelay(boolean v) {
    skipLoopStartDelay = v;
  }

  public float getReturnPositionTolerance() {
    return returnPositionTolerance;
  }

  public void setReturnPositionTolerance(float v) {
    returnPositionTolerance = Math.max(0.05f, Math.min(0.5f, v));
  }

  public float getReturnAlignSpeed() {
    return returnAlignSpeed;
  }

  public void setReturnAlignSpeed(float v) {
    returnAlignSpeed = Math.max(0.2f, Math.min(1f, v));
  }

  public int getReturnTimeoutSeconds() {
    return returnTimeoutSeconds;
  }

  public void setReturnTimeoutSeconds(int v) {
    returnTimeoutSeconds = Math.max(15, Math.min(180, v));
  }
  /**
   * Diagnostics: verbose log and CSV dump of a run, off by default. Getters return the effective
   * diagnostics state: with -Dmirror.debug=true the toggle does not show "off" while logging is
   * active. The config file still stores only the user's choice.
   */
  public boolean isDebug() {
    return debug || MirrorDebug.isForced();
  }

  public void setDebug(boolean v) {
    debug = v;
    MirrorDebug.setEnabled(v);
  }

  public boolean isDebugCsv() {
    return debugCsv || MirrorDebug.isCsvForced();
  }

  public void setDebugCsv(boolean v) {
    debugCsv = v;
    MirrorDebug.setCsvEnabled(v);
  }

  public boolean isDebugForced() {
    return MirrorDebug.isForced();
  }

  public boolean isDebugCsvForced() {
    return MirrorDebug.isCsvForced();
  }

  public float getPlaybackSpeed() {
    return playbackSpeed;
  }

  public void setPlaybackSpeed(float v) {
    float c = Math.max(0.25f, Math.min(4f, v));
    playbackSpeed = Math.round(c * 20f) / 20f;
  }

  public boolean isSoundsEnabled() {
    return soundsEnabled;
  }

  public boolean isGuardOnDamage() {
    return guardOnDamage;
  }

  public void setGuardOnDamage(boolean v) {
    guardOnDamage = v;
  }
  /**
   * Auto-leave the server: after how many completed loops the mod disconnects by itself. The
   * setting is global, not per slot: switching slots does not overwrite it.
   */
  public boolean isAutoLeaveEnabled() {
    return autoLeaveEnabled;
  }

  public void setAutoLeaveEnabled(boolean v) {
    autoLeaveEnabled = v;
  }

  public int getAutoLeaveCycles() {
    return autoLeaveCycles;
  }

  public void setAutoLeaveCycles(int v) {
    autoLeaveCycles = Math.max(1, Math.min(1000, v));
  }

  public boolean isAutoLeaveTimed() {
    return autoLeaveTimed;
  }

  public void setAutoLeaveTimed(boolean v) {
    autoLeaveTimed = v;
  }

  public int getAutoLeaveMinutes() {
    return autoLeaveMinutes;
  }

  public void setAutoLeaveMinutes(int v) {
    autoLeaveMinutes = Math.max(1, Math.min(525600, v));
  }

  public void setSoundsEnabled(boolean v) {
    soundsEnabled = v;
    SoundFx.setEnabled(v);
  }
  /**
   * Interval for sending recorded chat: 1200 ms does not suit every server, so the value is
   * configurable.
   */
  public int getChatSendIntervalMs() {
    return chatSendIntervalMs;
  }

  public void setChatSendIntervalMs(int v) {
    chatSendIntervalMs = Math.max(200, Math.min(5000, v));
  }
  /** Autosave period of the live recording in seconds, 0 = off. */
  public int getAutoCheckpointSeconds() {
    return autoCheckpointSeconds;
  }

  public void setAutoCheckpointSeconds(int v) {
    autoCheckpointSeconds = v <= 0 ? 0 : Math.max(10, Math.min(3600, v));
  }

  public NBTTagCompound writeSlotSettings() {
    NBTTagCompound t = new NBTTagCompound();
    t.setInteger("SettingsVersion", 1);
    t.setInteger("StartDelay", startDelay);
    t.setInteger("LoopLimit", loopLimit);
    t.setBoolean("PreferredLoop", preferredLoopMode);
    t.setBoolean("StopOnMove", stopOnMove);
    t.setBoolean("StopOnRouteDeviation", stopOnRouteDeviation);
    t.setBoolean("PlaybackModCommands", playbackModCommands);
    t.setBoolean("StopOnMouseMove", stopOnMouseMove);
    t.setInteger("StopOnMouseThreshold", stopOnMouseThreshold);
    t.setBoolean("ApplyRotation", applyRotation);
    t.setBoolean("RouteStabilization", routeStabilization);
    t.setBoolean("AutoReturn", autoReturnEnabled);
    t.setBoolean("CursorStabilization", cursorStabilization);
    t.setBoolean("SkipLoopDelay", skipLoopStartDelay);
    t.setFloat("ReturnPositionTolerance", returnPositionTolerance);
    t.setFloat("ReturnAlignSpeed", returnAlignSpeed);
    t.setInteger("ReturnTimeout", returnTimeoutSeconds);
    t.setFloat("PlaybackSpeed", playbackSpeed);
    t.setBoolean("PlaybackJump", playbackJump);
    t.setBoolean("PlaybackSneak", playbackSneak);
    t.setBoolean("PlaybackSprint", playbackSprint);
    t.setBoolean("PlaybackChat", playbackChat);
    t.setBoolean("PlaybackMovement", playbackMovement);
    t.setBoolean("PlaybackInteraction", playbackInteraction);
    t.setBoolean("VisibleChat", visibleChat);
    t.setBoolean("RecordAllKeys", recordAllKeys);
    t.setBoolean("GuardOnDamage", guardOnDamage);
    return t;
  }

  /**
   * Slot settings are applied only if the slot file has them: an empty slot does not reset the
   * current settings (start delay and the rest).
   */
  public void applySlotSettings(NBTTagCompound value) {
    if (value == null) return;
    NBTTagCompound t = value;
    if (t.hasKey("StartDelay")) setStartDelay(t.getInteger("StartDelay"));
    if (t.hasKey("LoopLimit")) setLoopLimit(t.getInteger("LoopLimit"));
    if (t.hasKey("PreferredLoop")) preferredLoopMode = t.getBoolean("PreferredLoop");
    if (t.hasKey("StopOnRouteDeviation"))
      stopOnRouteDeviation = t.getBoolean("StopOnRouteDeviation");
    if (t.hasKey("PlaybackModCommands")) playbackModCommands = t.getBoolean("PlaybackModCommands");
    if (t.hasKey("StopOnMove")) setStopOnMove(t.getBoolean("StopOnMove"));
    if (t.hasKey("StopOnMouseMove")) setStopOnMouseMove(t.getBoolean("StopOnMouseMove"));
    if (t.hasKey("StopOnMouseThreshold"))
      setStopOnMouseThreshold(t.getInteger("StopOnMouseThreshold"));
    if (t.hasKey("ApplyRotation")) setApplyRotation(t.getBoolean("ApplyRotation"));
    if (t.hasKey("RouteStabilization")) setRouteStabilization(t.getBoolean("RouteStabilization"));
    if (t.hasKey("AutoReturn")) setAutoReturnEnabled(t.getBoolean("AutoReturn"));
    if (t.hasKey("CursorStabilization"))
      setCursorStabilization(t.getBoolean("CursorStabilization"));
    if (t.hasKey("SkipLoopDelay")) setSkipLoopStartDelay(t.getBoolean("SkipLoopDelay"));
    if (t.hasKey("ReturnPositionTolerance"))
      setReturnPositionTolerance(t.getFloat("ReturnPositionTolerance"));
    if (t.hasKey("ReturnAlignSpeed")) setReturnAlignSpeed(t.getFloat("ReturnAlignSpeed"));
    if (t.hasKey("ReturnTimeout")) setReturnTimeoutSeconds(t.getInteger("ReturnTimeout"));
    if (t.hasKey("PlaybackSpeed")) setPlaybackSpeed(t.getFloat("PlaybackSpeed"));
    if (t.hasKey("PlaybackJump")) playbackJump = t.getBoolean("PlaybackJump");
    if (t.hasKey("PlaybackSneak")) playbackSneak = t.getBoolean("PlaybackSneak");
    if (t.hasKey("PlaybackSprint")) playbackSprint = t.getBoolean("PlaybackSprint");
    if (t.hasKey("PlaybackChat")) playbackChat = t.getBoolean("PlaybackChat");
    if (t.hasKey("PlaybackMovement")) playbackMovement = t.getBoolean("PlaybackMovement");
    if (t.hasKey("PlaybackInteraction")) playbackInteraction = t.getBoolean("PlaybackInteraction");
    if (t.hasKey("VisibleChat")) visibleChat = t.getBoolean("VisibleChat");
    if (t.hasKey("RecordAllKeys")) recordAllKeys = t.getBoolean("RecordAllKeys");
    // ExactPlacement from old slot files is ignored: exact block placement was removed, playback
    // always uses the current vanilla crosshair target.
    if (t.hasKey("GuardOnDamage")) guardOnDamage = t.getBoolean("GuardOnDamage");
    // AutoLeave/AutoLeaveCycles from old slot files are deliberately not read: auto-leave is a
    // global server-disconnect policy, choosing another slot must not change it.
  }

  public boolean isPreferredLoopMode() {
    return preferredLoopMode;
  }

  public void setPreferredLoopMode(boolean v) {
    preferredLoopMode = v;
  }

  public int getHudX() {
    return hudX;
  }

  public int getHudY() {
    return hudY;
  }

  public void setHudPosition(int x, int y) {
    hudX = Math.max(0, x);
    hudY = Math.max(0, y);
  }

  /**
   * Saves only the HUD position. Other values in the file stay as last saved: the unsaved draft of
   * the settings window (which edits this same object) never reaches the disk.
   */
  public boolean saveHudPosition() {
    try {
      forge.get(CAT, "hudX", 8).set(hudX);
      forge.get(CAT, "hudY", 8).set(hudY);
      forge.save();
      return true;
    } catch (Exception e) {
      LOG.error("HUD position save failed", e);
      return false;
    }
  }

  public boolean isShowTooltips() {
    return showTooltips;
  }

  public boolean isAutoDeleteModMessages() {
    return autoDeleteModMessages;
  }

  public void setAutoDeleteModMessages(boolean value) {
    autoDeleteModMessages = value;
  }

  public int getModMessageLifetimeSeconds() {
    return modMessageLifetimeSeconds;
  }

  public void setModMessageLifetimeSeconds(int value) {
    modMessageLifetimeSeconds = Math.max(1, Math.min(600, value));
  }

  public boolean isUiAnimationsEnabled() {
    return uiAnimationsEnabled;
  }

  public void setUiAnimationsEnabled(boolean value) {
    uiAnimationsEnabled = value;
  }

  public boolean isSendModMessages() {
    return sendModMessages;
  }

  public void setSendModMessages(boolean value) {
    sendModMessages = value;
  }

  public void setShowTooltips(boolean v) {
    showTooltips = v;
  }

  public boolean isPathLineEnabled() {
    return pathLineEnabled;
  }

  public void setPathLineEnabled(boolean v) {
    pathLineEnabled = v;
  }

  public int getPathLineR() {
    return pathLineR;
  }

  public void setPathLineR(int v) {
    pathLineR = Math.max(0, Math.min(255, v));
  }

  public int getPathLineG() {
    return pathLineG;
  }

  public void setPathLineG(int v) {
    pathLineG = Math.max(0, Math.min(255, v));
  }

  public int getPathLineB() {
    return pathLineB;
  }

  public void setPathLineB(int v) {
    pathLineB = Math.max(0, Math.min(255, v));
  }

  public int getPathLineAlpha() {
    return pathLineAlpha;
  }

  public void setPathLineAlpha(int v) {
    pathLineAlpha = Math.max(0, Math.min(255, v));
  }

  public float getPathLineWidth() {
    return pathLineWidth;
  }

  public void setPathLineWidth(float v) {
    pathLineWidth = Math.max(1f, Math.min(10f, v));
  }

  public int getStartMarkerMode() {
    return startMarkerMode;
  }

  public void setStartMarkerMode(int v) {
    startMarkerMode = Math.max(0, Math.min(3, v));
  }

  public boolean isPlaybackJump() {
    return playbackJump;
  }

  public void setPlaybackJump(boolean v) {
    playbackJump = v;
  }

  public boolean isPlaybackSneak() {
    return playbackSneak;
  }

  public void setPlaybackSneak(boolean v) {
    playbackSneak = v;
  }

  public boolean isPlaybackSprint() {
    return playbackSprint;
  }

  public void setPlaybackSprint(boolean v) {
    playbackSprint = v;
  }

  public boolean isPlaybackChat() {
    return playbackChat;
  }

  public void setPlaybackChat(boolean v) {
    playbackChat = v;
  }

  public boolean isPlaybackMovement() {
    return playbackMovement;
  }

  public void setPlaybackMovement(boolean v) {
    playbackMovement = v;
  }

  public boolean isPlaybackInteraction() {
    return playbackInteraction;
  }

  public void setPlaybackInteraction(boolean v) {
    playbackInteraction = v;
  }

  /**
   * Live chat only: playback opens the chat window and types recorded messages letter by letter
   * (keys recorded in the chat window are replayed there); off sends messages directly. The drawn
   * cursor and recording all keys have their own flags (showPointer, recordAllKeys).
   */
  public boolean isVisibleChat() {
    return visibleChat;
  }

  public void setVisibleChat(boolean v) {
    visibleChat = v;
  }

  public boolean isShowPointer() {
    return showPointer;
  }

  public void setShowPointer(boolean v) {
    showPointer = v;
  }

  public boolean isRecordAllKeys() {
    return recordAllKeys;
  }

  public void setRecordAllKeys(boolean v) {
    recordAllKeys = v;
  }

  public int getPathLineStyle() {
    return pathLineStyle;
  }

  public void setPathLineStyle(int v) {
    pathLineStyle = Math.max(0, Math.min(2, v));
  }

  public int getPathLineLength() {
    return pathLineLength;
  }

  public void setPathLineLength(int v) {
    pathLineLength = Math.max(20, Math.min(300, v));
  }

  public int getPathPointStep() {
    return pathPointStep;
  }

  public void setPathPointStep(int v) {
    pathPointStep = Math.max(1, Math.min(40, v));
  }

  public float getPathPointSize() {
    return pathPointSize;
  }

  public void setPathPointSize(float v) {
    pathPointSize = Math.max(0.05f, Math.min(0.5f, v));
  }

  public float getPathLineHeight() {
    return pathLineHeight;
  }

  public void setPathLineHeight(float v) {
    pathLineHeight = Math.max(0f, Math.min(1.5f, v));
  }

  public boolean isPathLineFade() {
    return pathLineFade;
  }

  public void setPathLineFade(boolean v) {
    pathLineFade = v;
  }

  public boolean isPathLineThroughWalls() {
    return pathLineThroughWalls;
  }

  public void setPathLineThroughWalls(boolean v) {
    pathLineThroughWalls = v;
  }

  public boolean isMarkerCustomColor() {
    return markerCustomColor;
  }

  public void setMarkerCustomColor(boolean v) {
    markerCustomColor = v;
  }

  public int getMarkerR() {
    return markerCustomColor ? markerR : pathLineR;
  }

  public int getMarkerG() {
    return markerCustomColor ? markerG : pathLineG;
  }

  public int getMarkerB() {
    return markerCustomColor ? markerB : pathLineB;
  }

  public int getMarkerAlpha() {
    return markerCustomColor ? markerAlpha : pathLineAlpha;
  }

  public int getMarkerOwnR() {
    return markerR;
  }

  public int getMarkerOwnG() {
    return markerG;
  }

  public int getMarkerOwnB() {
    return markerB;
  }

  public int getMarkerOwnAlpha() {
    return markerAlpha;
  }

  public void setMarkerR(int v) {
    markerR = Math.max(0, Math.min(255, v));
  }

  public void setMarkerG(int v) {
    markerG = Math.max(0, Math.min(255, v));
  }

  public void setMarkerB(int v) {
    markerB = Math.max(0, Math.min(255, v));
  }

  public void setMarkerAlpha(int v) {
    markerAlpha = Math.max(0, Math.min(255, v));
  }

  public float getMarkerRadius() {
    return markerRadius;
  }

  public void setMarkerRadius(float v) {
    markerRadius = Math.max(0.2f, Math.min(3f, v));
  }

  public float getMarkerBeaconHeight() {
    return markerBeaconHeight;
  }

  public void setMarkerBeaconHeight(float v) {
    markerBeaconHeight = Math.max(1f, Math.min(64f, v));
  }

  public boolean isMarkerPulse() {
    return markerPulse;
  }

  public void setMarkerPulse(boolean v) {
    markerPulse = v;
  }

  public boolean isMarkerLabel() {
    return markerLabel;
  }

  public void setMarkerLabel(boolean v) {
    markerLabel = v;
  }

  public int getMarkerLabelDistance() {
    return markerLabelDistance;
  }

  public void setMarkerLabelDistance(int v) {
    markerLabelDistance = Math.max(4, Math.min(256, v));
  }

  public boolean isMarkerLabelUnlimited() {
    return markerLabelUnlimited;
  }

  public void setMarkerLabelUnlimited(boolean v) {
    markerLabelUnlimited = v;
  }

  public boolean isMarkerThroughWalls() {
    return markerThroughWalls;
  }

  public void setMarkerThroughWalls(boolean v) {
    markerThroughWalls = v;
  }

  /** Resets user settings to factory defaults, except for the active slot. */
  public void resetAll() {
    setLanguage("auto");
    startDelay = 3;
    loopLimit = 0;
    preferredLoopMode = false;
    showTooltips = true;
    sendModMessages = true;
    autoDeleteModMessages = true;
    modMessageLifetimeSeconds = 5;
    uiAnimationsEnabled = true;
    stopOnMove = true;
    stopOnRouteDeviation = true;
    playbackModCommands = false;
    stopOnMouseMove = false;
    stopOnMouseThreshold = 6;
    applyRotation = true;
    routeStabilization = true;
    autoReturnEnabled = true;
    cursorStabilization = true;
    skipLoopStartDelay = true;
    returnPositionTolerance = 0.12f;
    returnAlignSpeed = 0.8f;
    returnTimeoutSeconds = 90;
    hudX = 8;
    hudY = 8;
    playbackSpeed = 1f;
    setSoundsEnabled(true);
    guardOnDamage = true;
    autoLeaveEnabled = false;
    autoLeaveCycles = 10;
    autoLeaveTimed = false;
    autoLeaveMinutes = 30;
    chatSendIntervalMs = 1200;
    autoCheckpointSeconds = 120;

    playbackJump = true;
    playbackSneak = true;
    playbackSprint = true;
    playbackChat = true;
    playbackMovement = true;
    playbackInteraction = true;
    visibleChat = true;
    showPointer = true;
    recordAllKeys = true;
    setDebug(false);
    setDebugCsv(false);
    resetAppearance();
  }
  /** Reset of the appearance only: path line and start marker. */
  public void resetAppearance() {
    pathLineEnabled = true;
    pathLineR = 138;
    pathLineG = 43;
    pathLineB = 226;
    pathLineAlpha = 180;
    pathLineWidth = 3f;
    pathLineStyle = 0;
    pathLineLength = 300;
    pathPointStep = 10;
    pathPointSize = 0.15f;
    pathLineHeight = 0.1f;
    pathLineFade = false;
    pathLineThroughWalls = false;
    startMarkerMode = 2;
    markerCustomColor = false;
    markerR = 138;
    markerG = 43;
    markerB = 226;
    markerAlpha = 180;
    markerRadius = 0.7f;
    markerBeaconHeight = 8f;
    markerPulse = true;
    markerLabel = true;
    markerLabelDistance = 32;
    markerLabelUnlimited = false;
    markerThroughWalls = false;
  }
}

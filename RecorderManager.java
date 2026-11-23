package com.mirror.recorder.manager;

import com.mirror.recorder.config.RecorderConfig;
import com.mirror.recorder.gui.Lang;
import com.mirror.recorder.model.Frame;
import com.mirror.recorder.storage.RecordingSaveWorker;
import com.mirror.recorder.storage.StorageManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Recording and playback state: slots, current frame, speed, pause, deferred start and saving
 * through the storage.
 */
public class RecorderManager {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Manager");
  public static final int MIN_SLOT = 1, MAX_SLOT = 100;
  /** Game ticks per second: delays and durations are counted in ticks. */
  public static final int TICKS_PER_SECOND = 20;

  public enum State {
    IDLE,
    RECORDING,
    PLAYING
  }

  public enum PendingAction {
    NONE,
    RECORD
  }

  private final RecorderConfig config;
  private final StorageManager storage;
  private State state = State.IDLE;
  private int activeSlot = 1;
  private static final int MAX_CACHED_SLOTS = 3;
  /**
   * Slots whose latest frames are not yet confirmed saved, and the settings of their last snapshot
   * (for the rescue copy).
   */
  private final Map<Integer, NBTTagCompound> unsavedSlots = new HashMap<Integer, NBTTagCompound>();
  /** Last snapshot sent for background saving, per slot: only its result matters. */
  private final Map<Integer, PendingSave> latestSaves = new HashMap<Integer, PendingSave>();

  private long nextSaveTicket = 0L;
  /**
   * Frames are kept only for recent slots: a busy slot and a slot with unsaved frames are never
   * evicted.
   */
  private final Map<Integer, List<Frame>> slotData =
      new LinkedHashMap<Integer, List<Frame>>(16, 0.75f, true) {
        static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Frame>> eldest) {
          if (eldest == null || size() <= MAX_CACHED_SLOTS) return false;
          Integer key = eldest.getKey();
          return key != null && !isSlotBusy(key.intValue()) && !unsavedSlots.containsKey(key);
        }
      };
  /**
   * LinkedHashMap looks only at the eldest entry: if it is busy or unsaved, nothing is evicted and
   * frames pile up above the limit. The next eligible ones are removed, but never the slot just
   * loaded.
   */
  private void trimSlotCache(int keep) {
    drainSaveResults();
    if (slotData.size() <= MAX_CACHED_SLOTS) return;
    for (Iterator<Map.Entry<Integer, List<Frame>>> it = slotData.entrySet().iterator();
        it.hasNext() && slotData.size() > MAX_CACHED_SLOTS; ) {
      Map.Entry<Integer, List<Frame>> e = it.next();
      Integer key = e.getKey();
      if (key == null || key.intValue() == keep) continue;
      if (isSlotBusy(key.intValue()) || unsavedSlots.containsKey(key)) continue;
      it.remove();
    }
  }

  private final Map<Integer, Integer> frameCounts = new HashMap<Integer, Integer>();
  private final Map<Integer, String> names = new HashMap<Integer, String>(),
      descs = new HashMap<Integer, String>();
  private long recTicks = 0, playbackRunId = 0, playbackSessionId = 0, playbackCompletionId = 0;
  private boolean leftClick = false, rightClick = false, recordingStartSet = false;
  private int leftClicks = 0, rightClicks = 0;
  private double recordingStartX, recordingStartY, recordingStartZ;
  private int frameIdx = 0, cooldown = 0;
  private final PlaybackClock clock = new PlaybackClock();
  private final WorldLifecycle worldLifecycle = new WorldLifecycle();
  private final WorldBarrier worldBarrier = new WorldBarrier();
  private long playbackWorldEpoch;

  public WorldLifecycle getWorldLifecycle() {
    return worldLifecycle;
  }

  public synchronized boolean hasPlaybackWorldChange() {
    return playbackWorldEpoch != worldLifecycle.getEpoch();
  }

  public synchronized boolean isPlaybackWorldReady() {
    List<Frame> frames = slotData.get(activeSlot);
    return worldLifecycle.isActive() && frames != null && !worldBarrier.blocked(frames, frameIdx);
  }

  /** Returns false only for an incompatible destination, not while waiting for a transition. */
  public synchronized boolean synchronizePlaybackWorld(int dimension) {
    if (state != State.PLAYING || !worldLifecycle.isActive()) return true;
    List<Frame> frames = slotData.get(activeSlot);
    if (frames == null) return false;
    long epoch = worldLifecycle.getEpoch();
    int next = worldBarrier.synchronize(frames, frameIdx, epoch, dimension);
    if (next < 0) return false;
    if (next != frameIdx || playbackWorldEpoch != epoch) {
      frameIdx = next;
      playbackWorldEpoch = epoch;
      clearCarry();
      skipped.clear();
      clock.reset();
    }
    return true;
  }

  private PendingAction pending = PendingAction.NONE;
  /**
   * Slot pinned for a start via return/fine positioning/countdown (0 means none). Held until the
   * end of the whole series.
   */
  private int reservedSlot = 0;

  private int pendSlot = 1, delayTicks = 0, unsavedWarningSlot = 0, limitReachedSlot = 0;
  private boolean playbackPaused = false;

  public RecorderManager(RecorderConfig c, StorageManager s) {
    config = c;
    storage = s;
    selectSlot(config.getActiveSlot());
    storage.setSaveCallback(
        new RecordingSaveWorker.SaveCallback() {
          public void saved(int slot, long ticket, boolean ok) {
            reportSaveResult(slot, ticket, ok);
          }
        });
  }

  private float carryYaw = 0f, carryPitch = 0f;
  /**
   * Click order within one tick: LMB and RMB are written to a shared list, so playback does not
   * swap them. The "pressed/not pressed" state is not enough for that: it loses both the order and
   * some events.
   */
  private final int[] clickOrder = new int[Frame.MAX_CLICK_SEQ];

  private int clickOrderLen = 0;

  private void pushClick(int button) {
    if (clickOrderLen < clickOrder.length) clickOrder[clickOrderLen++] = button;
  }
  /** The order is taken together with the frame and does not carry over to the next tick. */
  public synchronized int[] consumeClickSequence() {
    if (clickOrderLen == 0) return null;
    int[] out = new int[clickOrderLen];
    System.arraycopy(clickOrder, 0, out, 0, clickOrderLen);
    clickOrderLen = 0;
    return out;
  }

  public synchronized void clearClickSequence() {
    clickOrderLen = 0;
  }

  private static final int MAX_SKIPPED = 4096;
  private final ArrayDeque<Frame> skipped = new ArrayDeque<Frame>();
  /**
   * Sped-up playback: presses and rotations of skipped frames are not lost but carried into the
   * next frame.
   */
  private void carry(Frame f) {
    if (f == null) return;
    carryYaw += f.dYaw;
    carryPitch += f.dPitch;
    if (skipped.size() < MAX_SKIPPED) skipped.add(f);
  }

  private void clearCarry() {
    carryYaw = 0f;
    carryPitch = 0f;
  }
  /** Frames skipped by speed-up are not thrown away: the playback handler gets them in order. */
  public synchronized List<Frame> consumeSkippedFrames() {
    if (skipped.isEmpty()) return Collections.<Frame>emptyList();
    List<Frame> out = new ArrayList<Frame>(skipped);
    skipped.clear();
    return out;
  }

  public synchronized float consumeCarryYaw() {
    float v = carryYaw;
    carryYaw = 0f;
    return v;
  }

  public synchronized float consumeCarryPitch() {
    float v = carryPitch;
    carryPitch = 0f;
    return v;
  }

  public synchronized boolean scheduleRecording(int s) {
    if (!ok(s) || isBusy() || getSlotFrameCount(s) > 0) return false;
    pending = PendingAction.RECORD;
    pendSlot = s;
    delayTicks = Math.max(1, config.getStartDelay() * TICKS_PER_SECOND);
    return true;
  }

  public synchronized int tickDelay() {
    if (pending == PendingAction.NONE) return -1;
    if (delayTicks > 0) delayTicks--;
    if (delayTicks <= 0) {
      exec();
      return 0;
    }
    return (delayTicks + (TICKS_PER_SECOND - 1)) / TICKS_PER_SECOND;
  }

  public synchronized int getDelaySecondsRemaining() {
    return pending == PendingAction.NONE
        ? -1
        : Math.max(0, (delayTicks + (TICKS_PER_SECOND - 1)) / TICKS_PER_SECOND);
  }

  public synchronized void cancelPending() {
    pending = PendingAction.NONE;
    pendSlot = 1;
    delayTicks = 0;
  }

  public synchronized boolean hasPendingAction() {
    return pending != PendingAction.NONE;
  }

  public synchronized int getPendingSlot() {
    return pendSlot;
  }

  private void exec() {
    PendingAction a = pending;
    int s = pendSlot;
    cancelPending();
    if (state != State.IDLE) return;
    if (a == PendingAction.RECORD && !startRecording(s))
      LOG.warn("Could not execute {} for slot {}", a, s);
  }

  public synchronized boolean startRecording(int s) {
    if (!ok(s) || isBusy() || getSlotFrameCount(s) > 0) return false;
    slotData.put(s, new ArrayList<Frame>());
    frameCounts.put(s, 0);
    activeSlot = s;
    recTicks = 0;
    leftClick = false;
    rightClick = false;
    leftClicks = 0;
    rightClicks = 0;
    clickOrderLen = 0;
    recordingStartSet = false;
    state = State.RECORDING;
    trimSlotCache(s);
    return true;
  }

  public synchronized void recordTick(Frame f) {
    if (state != State.RECORDING || f == null) return;
    List<Frame> l = slotData.get(activeSlot);
    if (l == null) {
      stopRecording();
      return;
    }
    // Frame limit: stopping automatically is fine, but staying silent is not; RecordingHandler
    // takes
    // the flag and tells the player.
    if (l.size() >= StorageManager.MAX_FRAMES) {
      limitReachedSlot = activeSlot;
      stopRecording();
      return;
    }
    if (!recordingStartSet) {
      recordingStartX = f.x;
      recordingStartY = f.y;
      recordingStartZ = f.z;
      recordingStartSet = true;
    }
    l.add(f);
    recTicks++;
    frameCounts.put(activeSlot, l.size());
  }
  /**
   * Queues a snapshot of a live recording for background saving (world change, autosave, game
   * shutdown) to reduce crash-related data loss; an unfinished save does not protect the snapshot.
   * The recording itself continues.
   */
  public synchronized void checkpointRecording() {
    if (state != State.RECORDING) return;
    List<Frame> f = slotData.get(activeSlot);
    if (f == null || f.isEmpty()) return;
    queueSave(activeSlot, f);
  }
  /**
   * The frame snapshot is queued for background saving under a new number: older results for the
   * slot are ignored.
   */
  private void queueSave(int slot, List<Frame> frames) {
    NBTTagCompound settings = config.writeSlotSettings();
    long ticket = ++nextSaveTicket;
    latestSaves.put(Integer.valueOf(slot), new PendingSave(ticket, settings));
    storage.queueRecordingSave(slot, new ArrayList<Frame>(frames), settings, ticket);
  }

  public synchronized boolean stopRecording() {
    if (state != State.RECORDING) return false;
    state = State.IDLE;
    leftClick = false;
    rightClick = false;
    leftClicks = 0;
    rightClicks = 0;
    List<Frame> f = slotData.get(activeSlot);
    if (f == null || f.isEmpty()) {
      frameCounts.put(activeSlot, 0);
      recordingStartSet = false;
      return false;
    }
    frameCounts.put(activeSlot, f.size());
    queueSave(activeSlot, f);
    Integer key = Integer.valueOf(activeSlot);
    unsavedSlots.put(key, latestSaves.get(key).settings);
    recordingStartSet = false;
    return true;
  }
  /**
   * Background save result: called by the save worker or shutdown flush. The manager monitor is not
   * taken here. The main thread waits for the slot to go quiet (delete, rename, copy, export)
   * inside synchronized methods; if the callback waited on the same monitor, the save thread would
   * stall and not pick up the next request, and the wait would run into the full timeout. Results
   * are applied in arrival order on the next call to drainSaveResults.
   */
  public void reportSaveResult(int slot, long ticket, boolean ok) {
    // Logged immediately: on game shutdown nobody will process the results any more, and lost
    // frames must stay in the log.
    if (!ok) LOG.error("Recording for slot {} remains only in memory", Integer.valueOf(slot));
    saveResults.add(new SaveResult(slot, ticket, ok));
  }

  private static final class SaveResult {
    final int slot;
    final long ticket;
    final boolean ok;

    SaveResult(int slot, long ticket, boolean ok) {
      this.slot = slot;
      this.ticket = ticket;
      this.ok = ok;
    }
  }

  private static final class PendingSave {
    final long ticket;
    final NBTTagCompound settings;

    PendingSave(long ticket, NBTTagCompound settings) {
      this.ticket = ticket;
      this.settings = settings;
    }
  }

  private final ConcurrentLinkedQueue<SaveResult> saveResults =
      new ConcurrentLinkedQueue<SaveResult>();
  /** Called only under the manager monitor. */
  private void drainSaveResults() {
    SaveResult r;
    while ((r = saveResults.poll()) != null) applySaveResult(r);
  }
  /**
   * The result of an old snapshot means nothing: a newer one was queued after it and only its
   * result is applied.
   */
  private void applySaveResult(SaveResult r) {
    Integer key = Integer.valueOf(r.slot);
    PendingSave latest = latestSaves.get(key);
    if (latest == null || latest.ticket != r.ticket) return;
    latestSaves.remove(key);
    if (r.ok) {
      unsavedSlots.remove(key);
      return;
    }
    // The slot is already gone from memory: nothing to warn about.
    if (!slotData.containsKey(key)) return;
    unsavedSlots.put(key, latest.settings);
    unsavedWarningSlot = r.slot;
  }

  /**
   * Pins the manager to preparing a slot start: while the reservation holds, isBusy() is true, so
   * switching slots, editing its settings and any other starts are rejected before anything
   * changes.
   */
  public synchronized boolean reservePlayback(int s) {
    if (!ok(s) || isBusy() || getSlotFrameCount(s) <= 0) return false;
    reservedSlot = s;
    return true;
  }

  public synchronized void releaseReservation() {
    reservedSlot = 0;
  }

  /**
   * Starts the prepared run: only for the pinned slot, the reservation stays until the end of the
   * series.
   */
  public synchronized boolean startPreparedPlayback(int s) {
    if (!ok(s) || reservedSlot != s || state != State.IDLE || pending != PendingAction.NONE)
      return false;
    load(s);
    List<Frame> f = slotData.get(s);
    if (f == null || f.isEmpty()) return false;
    activeSlot = s;
    frameIdx = 0;
    cooldown = 3;
    clock.reset();
    clearCarry();
    skipped.clear();
    playbackPaused = false;
    playbackSessionId++;
    playbackRunId++;
    playbackWorldEpoch = worldLifecycle.getEpoch();
    worldBarrier.start(f, playbackWorldEpoch);
    state = State.PLAYING;
    return true;
  }

  /**
   * Playback step. On normal exhaustion, false means no more frames: the run stays in PLAYING
   * without a current frame until the handler delivers the event tail and calls completePlayback().
   * Outside PLAYING or with an absent or empty frame list, false is returned without entering this
   * tail state.
   */
  public synchronized boolean advancePlayback() {
    if (state != State.PLAYING) return false;
    if (playbackPaused || !isPlaybackWorldReady()) return true;
    List<Frame> f = slotData.get(activeSlot);
    if (f == null || f.isEmpty()) {
      stopPlayback();
      return false;
    }
    if (frameIdx >= f.size()) return false;
    if (cooldown > 0) cooldown--;
    int steps = clock.advance(PlaybackClock.toMilli(config.getPlaybackSpeed()));
    if (steps <= 0) return true;
    int allowed = WorldBarrier.limitSteps(f, frameIdx, steps);
    clock.giveBack(steps - allowed);
    steps = allowed;
    for (int i = 0; i < steps; i++) {
      frameIdx++;
      if (frameIdx >= f.size()) {
        frameIdx = f.size();
        return false;
      }
      if (i < steps - 1) {
        if (frameIdx == f.size() - 1) {
          clock.giveBack(steps - 1 - i);
          break;
        }
        carry(f.get(frameIdx));
      }
    }
    return true;
  }
  /** The run's frames are over, the event tail is being delivered (see advancePlayback). */
  public synchronized boolean isPlaybackAtEnd() {
    List<Frame> f = slotData.get(activeSlot);
    return state == State.PLAYING && f != null && frameIdx >= f.size();
  }

  /**
   * Normal completion of a run: only it counts a lap ({@code playbackCompletionId}) for the loop
   * and auto-leave.
   */
  public synchronized void completePlayback() {
    if (!isPlaybackAtEnd()) return;
    playbackCompletionId++;
    stopPlayback();
  }

  /**
   * Playback pause: the frame does not advance, resuming continues from it. The state lives only
   * within one run.
   */
  public synchronized boolean isPaused() {
    return state == State.PLAYING && playbackPaused;
  }

  public synchronized boolean setPlaybackPaused(boolean v) {
    if (state != State.PLAYING) return false;
    playbackPaused = v;
    return true;
  }

  public synchronized void stopPlayback() {
    if (state == State.PLAYING) {
      state = State.IDLE;
      playbackPaused = false;
      frameIdx = 0;
      cooldown = 0;
      clock.reset();
      clearCarry();
      skipped.clear();
      leftClick = false;
      rightClick = false;
      leftClicks = 0;
      rightClicks = 0;
    }
  }

  public synchronized void stopAll() {
    cancelPending();
    reservedSlot = 0;
    if (state == State.RECORDING) stopRecording();
    if (state == State.PLAYING) stopPlayback();
    state = State.IDLE;
    leftClick = false;
    rightClick = false;
    leftClicks = 0;
    rightClicks = 0;
    recordingStartSet = false;
  }
  /**
   * An imported slot does not overwrite your settings: they are not applied from a foreign file
   * (see StorageManager.isImportedRecording).
   */
  public synchronized boolean selectSlot(int s) {
    if (!ok(s) || isBusy()) return false;
    config.setActiveSlot(s);
    if (!isSlotImported(s)) config.applySlotSettings(storage.loadSlotSettings(s));
    return true;
  }

  public synchronized boolean saveSlotSettings(int s) {
    return ok(s)
        && !isBusy()
        && getSlotFrameCount(s) > 0
        && storage.saveSlotSettings(s, config.writeSlotSettings());
  }

  public synchronized boolean setSlotLoopLimit(int s, int limit) {
    if (!ok(s) || isBusy() || getSlotFrameCount(s) <= 0) return false;
    int v = limit <= 0 ? 0 : Math.max(2, Math.min(1000, limit));
    if (s == config.getActiveSlot()) {
      int prev = config.getLoopLimit();
      config.setLoopLimit(v);
      if (storage.saveSlotSettings(s, config.writeSlotSettings())) return true;
      config.setLoopLimit(prev);
      return false;
    }
    NBTTagCompound t = storedSlotSettings(s);
    t.setInteger("LoopLimit", v);
    return storage.saveSlotSettings(s, t);
  }
  /**
   * Delay of an inactive slot: the one saved in the slot file, otherwise the current one; an empty
   * slot does not silently fall back to a hard default of "3".
   */
  public synchronized int getSlotStartDelay(int s) {
    if (!ok(s)) return 0;
    if (s == config.getActiveSlot()) return config.getStartDelay();
    NBTTagCompound t = storage.loadSlotSettings(s);
    return t != null && t.hasKey("StartDelay")
        ? Math.max(0, Math.min(60, t.getInteger("StartDelay")))
        : config.getStartDelay();
  }

  /**
   * Saved settings of an inactive slot (or an empty set of the current version) for targeted
   * editing.
   */
  private NBTTagCompound storedSlotSettings(int s) {
    NBTTagCompound t = storage.loadSlotSettings(s);
    if (t == null) {
      t = new NBTTagCompound();
      t.setInteger("SettingsVersion", 1);
    }
    return t;
  }

  /** Slot name, description and delay in one file write: either everything is saved or nothing. */
  public synchronized boolean saveSlotEdit(int s, String n, String d, int delay) {
    if (!ok(s) || getSlotFrameCount(s) == 0 || isSlotBusy(s) || isSlotSaving(s)) return false;
    n = cleanText(n, 40);
    d = cleanText(d, 120);
    int v = Math.max(0, Math.min(60, delay));
    boolean active = s == config.getActiveSlot();
    int old = config.getStartDelay();
    NBTTagCompound t;
    if (active) {
      config.setStartDelay(v);
      t = config.writeSlotSettings();
    } else {
      t = storedSlotSettings(s);
      t.setInteger("StartDelay", v);
    }
    if (!storage.saveSlotEdit(s, n, d, t)) {
      if (active) config.setStartDelay(old);
      return false;
    }
    names.put(s, n);
    descs.put(s, d);
    return true;
  }

  public synchronized boolean setPreferredLoopMode(int s, boolean loopMode) {
    if (!ok(s) || isBusy() || getSlotFrameCount(s) <= 0) return false;
    if (s == config.getActiveSlot()) {
      if (config.isPreferredLoopMode() == loopMode) return true;
      boolean previous = config.isPreferredLoopMode();
      config.setPreferredLoopMode(loopMode);
      if (storage.saveSlotSettings(s, config.writeSlotSettings())) return true;
      config.setPreferredLoopMode(previous);
      return false;
    }
    NBTTagCompound t = storedSlotSettings(s);
    if (t.hasKey("PreferredLoop") && t.getBoolean("PreferredLoop") == loopMode) return true;
    t.setBoolean("PreferredLoop", loopMode);
    return storage.saveSlotSettings(s, t);
  }

  public synchronized Frame getSlotFirstFrame(int s) {
    if (!ok(s)) return null;
    load(s);
    List<Frame> f = slotData.get(s);
    return f != null && !f.isEmpty() ? f.get(0) : null;
  }

  public synchronized Frame getCurrentPlaybackFrame() {
    List<Frame> f = slotData.get(activeSlot);
    return state == State.PLAYING && f != null && frameIdx >= 0 && frameIdx < f.size()
        ? f.get(frameIdx)
        : null;
  }

  public synchronized Frame getPlaybackFirstFrame() {
    List<Frame> f = slotData.get(activeSlot);
    return state == State.PLAYING && f != null && !f.isEmpty() ? f.get(0) : null;
  }

  public synchronized Frame getPreviousPlaybackFrame() {
    List<Frame> f = slotData.get(activeSlot);
    return state == State.PLAYING && f != null && frameIdx > 0 && frameIdx - 1 < f.size()
        ? f.get(frameIdx - 1)
        : null;
  }

  public synchronized int copyPlaybackFramesAhead(Frame[] target) {
    if (state != State.PLAYING || target == null || target.length == 0) return 0;
    List<Frame> f = slotData.get(activeSlot);
    if (f == null || f.isEmpty()) return 0;
    int from = Math.max(0, Math.min(frameIdx, f.size())),
        count = Math.min(target.length, f.size() - from);
    for (int i = 0; i < count; i++) target[i] = f.get(from + i);
    for (int i = count; i < target.length && target[i] != null; i++) target[i] = null;
    return count;
  }

  public synchronized void load(int s) {
    drainSaveResults();
    if (!ok(s) || slotData.containsKey(s)) return;
    StorageManager.LoadedRecording r = storage.loadRecording(s);
    List<Frame> f = r == null ? new ArrayList<Frame>() : r.frames;
    slotData.put(s, f);
    frameCounts.put(s, f.size());
    if (r != null && r.fromBackup) adoptBackupMetadata(s);
    trimSlotCache(s);
  }
  /**
   * The frames came from .bak: the slot name, description and settings must come from it too, not
   * from the damaged .nbt they may have been shown and applied from before.
   */
  private void adoptBackupMetadata(int s) {
    names.remove(s);
    descs.remove(s);
    if (s == config.getActiveSlot() && !isBusy() && !isSlotImported(s))
      config.applySlotSettings(storage.loadSlotSettings(s));
  }

  public synchronized boolean isSlotBusy(int s) {
    return ok(s)
        && ((state != State.IDLE && activeSlot == s)
            || (pending != PendingAction.NONE && pendSlot == s)
            || reservedSlot == s);
  }

  public synchronized boolean canDeleteSlot(int s) {
    return ok(s) && getSlotFrameCount(s) > 0 && !isSlotBusy(s) && !isSlotSaving(s);
  }
  /**
   * Reports whether the background saver has a queued or active write for the slot. Callers can
   * reject conflicting operations immediately: waiting in the storage would hold the client thread
   * for up to several seconds.
   */
  public synchronized boolean isSlotSaving(int s) {
    return ok(s) && storage.isSlotSaving(s);
  }

  public synchronized int deleteSlotStatus(int s) {
    if (!canDeleteSlot(s)) return StorageManager.DELETE_FAILED;
    int r = storage.deleteRecordingEx(s);
    if (r != StorageManager.DELETE_FAILED) clearCache(s);
    return r;
  }

  public synchronized boolean setSlotMetadata(int s, String n, String d) {
    if (!ok(s) || getSlotFrameCount(s) == 0 || isSlotBusy(s) || isSlotSaving(s)) return false;
    n = cleanText(n, 40);
    d = cleanText(d, 120);
    if (!storage.saveSlotMetadata(s, n, d)) return false;
    names.put(s, n);
    descs.put(s, d);
    return true;
  }

  public synchronized boolean setSlotName(int s, String n) {
    return setSlotMetadata(s, n, getSlotDescription(s));
  }

  public synchronized boolean setSlotDescription(int s, String d) {
    return setSlotMetadata(s, getSlotName(s), d);
  }

  public synchronized boolean duplicateSlot(int source, int target) {
    if (isBusy()
        || !ok(source)
        || !ok(target)
        || source == target
        || getSlotFrameCount(source) == 0
        || getSlotFrameCount(target) > 0
        || isSlotSaving(source)
        || isSlotSaving(target)) return false;
    if (!storage.duplicateRecording(
        source,
        target,
        Lang.t("manager.duplicated_slot"),
        Lang.t("manager.copy"),
        unsavedFrames(source),
        unsavedSlots.get(Integer.valueOf(source)))) return false;
    clearCache(target);
    load(target);
    return true;
  }

  public synchronized String exportSlot(int s) {
    if (isBusy() || getSlotFrameCount(s) <= 0 || isSlotSaving(s)) return null;
    return storage.exportRecording(s, unsavedFrames(s), unsavedSlots.get(Integer.valueOf(s)));
  }
  /**
   * Slot frames whose latest snapshot is not confirmed saved, otherwise null: the copy is then
   * taken from disk.
   */
  private List<Frame> unsavedFrames(int s) {
    drainSaveResults();
    if (!unsavedSlots.containsKey(Integer.valueOf(s))) return null;
    List<Frame> f = slotData.get(s);
    return f == null || f.isEmpty() ? null : new ArrayList<Frame>(f);
  }

  public synchronized boolean importSlot(int target, String file) {
    if (isBusy() || !ok(target) || getSlotFrameCount(target) > 0 || isSlotSaving(target))
      return false;
    if (!storage.importRecording(target, file)) return false;
    clearCache(target);
    load(target);
    return true;
  }

  public synchronized List<String> getExportFiles() {
    return storage.listExportFiles();
  }

  public synchronized String getExportDirectoryPath() {
    return storage.getExportDirectoryPath();
  }

  private void clearCache(int s) {
    slotData.remove(s);
    frameCounts.remove(s);
    names.remove(s);
    descs.remove(s);
    unsavedSlots.remove(Integer.valueOf(s));
    latestSaves.remove(Integer.valueOf(s));
  }

  public synchronized int getSlotFrameCount(int s) {
    if (!ok(s)) return 0;
    List<Frame> f = slotData.get(s);
    if (f != null) return Math.min(StorageManager.MAX_FRAMES, f.size());
    Integer c = frameCounts.get(s);
    if (c == null) {
      c = storage.peekFrameCount(s);
      frameCounts.put(s, c);
    }
    return Math.max(0, Math.min(StorageManager.MAX_FRAMES, c));
  }

  public synchronized long getSlotModified(int s) {
    return storage.getLastModified(s);
  }

  public synchronized long getSlotFileSize(int s) {
    return storage.getFileSize(s);
  }

  public synchronized void setLeftClickState(boolean v) {
    leftClick = v;
    if (!v) leftClicks = 0;
  }

  public synchronized void addLeftClick() {
    leftClick = true;
    if (leftClicks < Frame.MAX_CLICKS) leftClicks++;
    pushClick(0);
  }

  public synchronized int getLeftClickCount() {
    return leftClicks;
  }

  public synchronized boolean getLeftClickState() {
    return leftClick;
  }

  public synchronized void setRightClickState(boolean v) {
    rightClick = v;
    if (!v) rightClicks = 0;
  }

  public synchronized void addRightClick() {
    rightClick = true;
    if (rightClicks < Frame.MAX_CLICKS) rightClicks++;
    pushClick(1);
  }

  public synchronized int getRightClickCount() {
    return rightClicks;
  }

  public synchronized boolean getRightClickState() {
    return rightClick;
  }

  public synchronized boolean isRecording() {
    return state == State.RECORDING;
  }

  public synchronized boolean isPlaying() {
    return state == State.PLAYING;
  }

  public synchronized boolean isBusy() {
    return state != State.IDLE || pending != PendingAction.NONE || reservedSlot != 0;
  }

  public synchronized int getActiveOperationSlot() {
    return activeSlot;
  }

  public synchronized long getRecordingTickCounter() {
    return recTicks;
  }

  public synchronized int getCurrentFrameIndex() {
    return frameIdx;
  }

  /** Camera timing shares the same accumulator as frame advancement. */
  public synchronized int getFrameTicksLeft() {
    return clock.ticksUntilStep(PlaybackClock.toMilli(config.getPlaybackSpeed()));
  }

  public synchronized int getSimulateCooldown() {
    return cooldown;
  }

  public synchronized long getPlaybackRunId() {
    return playbackRunId;
  }

  public synchronized long getPlaybackSessionId() {
    return playbackSessionId;
  }

  public synchronized long getPlaybackCompletionId() {
    return playbackCompletionId;
  }

  public synchronized boolean hasRecordingStart() {
    return state == State.RECORDING && recordingStartSet;
  }

  public synchronized double getRecordingStartX() {
    return recordingStartX;
  }

  public synchronized double getRecordingStartY() {
    return recordingStartY;
  }

  public synchronized double getRecordingStartZ() {
    return recordingStartZ;
  }

  public synchronized int consumeUnsavedWarningSlot() {
    drainSaveResults();
    int s = unsavedWarningSlot;
    unsavedWarningSlot = 0;
    return s;
  }
  /**
   * The slot whose recording stopped by itself at the frame limit: RecordingHandler shows the
   * message to the player.
   */
  public synchronized int consumeLimitReachedSlot() {
    int s = limitReachedSlot;
    limitReachedSlot = 0;
    return s;
  }

  public synchronized String getSlotName(int s) {
    if (!ok(s)) return "";
    if (names.containsKey(s)) return names.get(s);
    String n = storage.loadSlotName(s);
    names.put(s, n == null ? "" : n);
    return n == null ? "" : n;
  }

  public synchronized String getSlotDescription(int s) {
    if (!ok(s)) return "";
    if (descs.containsKey(s)) return descs.get(s);
    String d = storage.loadSlotDesc(s);
    descs.put(s, d == null ? "" : d);
    return d == null ? "" : d;
  }

  private String cleanText(String v, int max) {
    if (v == null) return "";
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < v.length() && b.length() < max; i++) {
      char c = v.charAt(i);
      if (c != '\u00a7' && !Character.isISOControl(c)) b.append(c);
    }
    return b.toString().trim();
  }

  private boolean ok(int s) {
    return s >= MIN_SLOT && s <= MAX_SLOT;
  }
  /**
   * Whether the recording came from outside; imported slot settings are not applied automatically.
   */
  public synchronized boolean isSlotImported(int s) {
    return ok(s) && storage.isImportedRecording(s);
  }
  /** World unload: frames are no longer needed. Slots with unsaved frames are left alone. */
  public synchronized void releaseIdleFrames() {
    drainSaveResults();
    if (isBusy()) return;
    for (Iterator<Map.Entry<Integer, List<Frame>>> it = slotData.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Integer, List<Frame>> e = it.next();
      if (!unsavedSlots.containsKey(e.getKey())) it.remove();
    }
  }

  public synchronized List<String> getTrashFiles() {
    return storage.listTrashFiles();
  }

  public synchronized int getTrashSlot(String f) {
    return storage.trashSlot(f);
  }

  public synchronized int getTrashFrames(String f) {
    return storage.trashFrames(f);
  }

  public synchronized long getTrashTime(String f) {
    return storage.trashTime(f);
  }

  public synchronized String getTrashTitle(String f) {
    return storage.trashTitle(f);
  }

  public synchronized String getTrashDirectoryPath() {
    return storage.getTrashDirectoryPath();
  }

  public synchronized boolean restoreTrash(String f, int slot) {
    if (isBusy() || !ok(slot) || getSlotFrameCount(slot) > 0 || isSlotSaving(slot)) return false;
    if (!storage.restoreTrash(f, slot)) return false;
    clearCache(slot);
    load(slot);
    return true;
  }

  public synchronized int restoreTrashToOwnSlot(String f) {
    int s = storage.trashSlot(f);
    if (!ok(s)) return -1;
    return restoreTrash(f, s) ? s : -1;
  }

  public synchronized int undoLastDelete() {
    String f = storage.latestTrashFile();
    return f == null ? -1 : restoreTrashToOwnSlot(f);
  }

  public synchronized boolean deleteTrash(String f) {
    return storage.deleteTrash(f);
  }

  public synchronized boolean clearTrash() {
    return storage.clearTrash();
  }
}

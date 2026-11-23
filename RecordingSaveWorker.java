package com.mirror.recorder.storage;

import com.mirror.recorder.model.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Background saving of recordings: NBT serialization, compression and file writing do not block the
 * main thread. Checkpoints and stopping a recording just put a ready snapshot into the queue and
 * return without waiting for disk I/O.
 *
 * <p>Design: a single daemon thread; only the freshest request is kept per slot, a new checkpoint
 * replaces the previous one until it is picked up. Deletion, trash, metadata, duplication and
 * export first wait for the slot to go quiet ({@link #awaitQuiescent}), so cancelling requests is
 * not needed: conflicting operations simply do not overlap with the background write, and on
 * timeout they explicitly fail.
 *
 * <p>Thread safety: the save path (codec, file store, debug log) does not touch game state or
 * shared mutable fields; encoding works only on the passed snapshot, frames are read-only by
 * convention (see Frame). The summary cache in StorageManager is guarded by its own lock. The lock
 * order is the same everywhere: manager → worker; the result callback is invoked WITHOUT the worker
 * lock, so there is no cycle.
 */
public final class RecordingSaveWorker {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Storage");
  /**
   * How long read-modify-write operations (deletion, metadata, duplication, export) wait for a
   * quiet slot.
   */
  public static final long QUIESCE_TIMEOUT_MS = 5000L;
  /**
   * How long the shutdown hook waits for the background thread before saving the leftovers itself.
   */
  private static final long SHUTDOWN_JOIN_MS = 30000L;
  /**
   * Save result: called from the worker or shutdown thread; the implementation must be thread-safe.
   * ticket is the snapshot number passed to queue: the result refers only to it, not to the slot in
   * general.
   */
  public interface SaveCallback {
    void saved(int slot, long ticket, boolean ok);
  }
  /**
   * Recording snapshot: the list is private (the caller passes a copy), frames are read-only by
   * convention (see Frame).
   */
  private static final class Job {
    final List<Frame> frames;
    final NBTTagCompound settings;
    final long ticket;

    Job(List<Frame> f, NBTTagCompound s, long t) {
      frames = f;
      settings = s;
      ticket = t;
    }
  }

  private final Object lock = new Object();
  private final Map<Integer, Job> pending = new HashMap<Integer, Job>();
  private int activeSlot = -1;
  private boolean stopping = false;
  private Thread worker = null;
  private final StorageManager storage;
  private volatile SaveCallback callback = null;

  public RecordingSaveWorker(StorageManager storage) {
    this.storage = storage;
  }

  public void setCallback(SaveCallback cb) {
    callback = cb;
  }
  /**
   * Queues a slot for saving: replaces the pending request for this slot; an active write is not
   * replaced.
   */
  public void queue(int slot, List<Frame> frames, NBTTagCompound settings, long ticket) {
    if (frames == null || frames.isEmpty() || settings == null) return;
    synchronized (lock) {
      // The JVM is already shutting down and the shutdown hook will save the leftovers itself: a
      // silent refusal, not a new request.
      if (stopping) return;
      pending.put(Integer.valueOf(slot), new Job(frames, settings, ticket));
      if (worker == null) {
        worker =
            new Thread(
                new Runnable() {
                  public void run() {
                    loop();
                  }
                },
                "MirrorRecorder-save-worker");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
      }
      lock.notifyAll();
    }
  }
  /**
   * Whether the slot has a request or an active write, without waiting, for refusing on the client
   * thread.
   */
  public boolean isSaving(int slot) {
    synchronized (lock) {
      return pending.containsKey(Integer.valueOf(slot)) || activeSlot == slot;
    }
  }
  /**
   * Waits until the slot has neither a request nor an active write. False means timeout, the
   * operation must not proceed.
   */
  public boolean awaitQuiescent(int slot, long timeoutMs) {
    // Monotonic clock: changing the system time must neither cut short nor stretch the wait.
    long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1000000L;
    synchronized (lock) {
      while (pending.containsKey(Integer.valueOf(slot)) || activeSlot == slot) {
        long waitNs = deadline - System.nanoTime();
        if (waitNs <= 0L) return false;
        try {
          lock.wait(Math.max(1L, waitNs / 1000000L));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      return true;
    }
  }
  /**
   * For the shutdown hook: stop accepting requests, wait for the thread and save the leftovers
   * synchronously.
   */
  public void shutdownFlush() {
    synchronized (lock) {
      stopping = true;
      lock.notifyAll();
    }
    Thread w;
    synchronized (lock) {
      w = worker;
    }
    if (w != null) {
      try {
        w.join(SHUTDOWN_JOIN_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    List<Map.Entry<Integer, Job>> rest;
    synchronized (lock) {
      if (pending.isEmpty()) return;
      rest = new ArrayList<Map.Entry<Integer, Job>>(pending.entrySet());
      pending.clear();
    }
    for (Map.Entry<Integer, Job> e : rest) {
      int slot = e.getKey().intValue();
      // The thread is stuck on this slot (dead disk): its temp file is not overwritten.
      synchronized (lock) {
        if (activeSlot == slot) continue;
      }
      boolean ok;
      try {
        ok = runJob(slot, e.getValue());
      } catch (Throwable t) {
        LOG.error("Background save failed", t);
        ok = false;
      }
      report(slot, e.getValue().ticket, ok);
    }
  }

  private void loop() {
    for (; ; ) {
      Map.Entry<Integer, Job> next = null;
      synchronized (lock) {
        while (pending.isEmpty() && !stopping) {
          try {
            lock.wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker = null;
            return;
          }
        }
        if (stopping && pending.isEmpty()) return;
        Iterator<Map.Entry<Integer, Job>> it = pending.entrySet().iterator();
        if (it.hasNext()) {
          next = it.next();
          it.remove();
        }
        if (next != null) activeSlot = next.getKey().intValue();
      }
      // Stop was announced while taking: the loop comes back and exits via the check above.
      if (next == null) continue;
      int slot = next.getKey().intValue();
      boolean ok;
      try {
        ok = runJob(slot, next.getValue());
      } catch (Throwable t) {
        LOG.error("Background save failed", t);
        ok = false;
      }
      synchronized (lock) {
        activeSlot = -1;
        lock.notifyAll();
      }
      report(slot, next.getValue().ticket, ok);
    }
  }

  private boolean runJob(int slot, Job job) {
    try {
      if (storage.saveRecording(slot, job.frames, job.settings)) return true;
      return storage.saveRecordingLenient(slot, job.frames, job.settings);
    } catch (Throwable t) {
      LOG.error("Background save failed for slot {}", Integer.valueOf(slot), t);
      return false;
    }
  }

  private void report(int slot, long ticket, boolean ok) {
    SaveCallback cb = callback;
    if (cb == null) {
      if (!ok)
        LOG.error(
            "Recording for slot {} remains only in memory (no callback)", Integer.valueOf(slot));
      return;
    }
    try {
      cb.saved(slot, ticket, ok);
    } catch (Throwable t) {
      LOG.error("Save callback failed", t);
    }
  }
}

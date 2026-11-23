package com.mirror.recorder.storage;

import com.mirror.recorder.debug.MirrorDebug;
import com.mirror.recorder.model.Frame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Facade over RecordingCodec, RecordingFileStore and TrashStore: the only storage entry point for
 * the GUI, the manager and the handlers.
 */
public class StorageManager {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Storage");
  public static final int MAX_FRAMES = RecordingCodec.MAX_FRAMES,
      FORMAT_VERSION = RecordingCodec.FORMAT_VERSION,
      MAX_TRASH = TrashStore.MAX_TRASH;
  private final RecordingCodec codec = new RecordingCodec();
  private final RecordingFileStore fileStore;
  private final TrashStore trashStore;
  private final File saveDir, exportDir;
  /**
   * Slot snapshots, copy choice and generations. All these structures change only under this lock.
   */
  private final Object summaryLock = new Object();

  private final Map<Integer, SlotSummary> summaries = new HashMap<Integer, SlotSummary>();
  private final Map<Integer, StatEntry> stats = new HashMap<Integer, StatEntry>();
  private final Map<Integer, Selection> selections = new HashMap<Integer, Selection>();
  /**
   * The slot generation grows with each of our own writes: a snapshot started before it will no
   * longer get into the cache.
   */
  private final long[] generation = new long[101];

  private final RecordingSaveWorker saver;

  public StorageManager(File minecraftDir) {
    saveDir = new File(minecraftDir, "mirror_recorder");
    exportDir = new File(saveDir, "exports");
    File trashDir = new File(saveDir, "trash");
    if (!saveDir.exists() && !saveDir.mkdirs()) LOG.error("Failed to create save directory");
    if (!exportDir.exists() && !exportDir.mkdirs()) LOG.error("Failed to create export directory");
    if (!trashDir.exists() && !trashDir.mkdirs()) LOG.error("Failed to create trash directory");
    fileStore = new RecordingFileStore(saveDir);
    trashStore = new TrashStore(saveDir, trashDir);
    saver = new RecordingSaveWorker(this);
  }

  private boolean validSlot(int s) {
    return s >= 1 && s <= 100;
  }
  // === Writing and reading ===
  /**
   * Writing from memory: the name and description are taken from the same slot copy that will
   * become the backup.
   */
  public boolean saveRecording(int slot, List<Frame> frames, NBTTagCompound settings) {
    if (!validSlot(slot) || frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
      MirrorDebug.log(
          "STORAGE",
          "save rejected for slot "
              + slot
              + " (frames="
              + (frames == null ? -1 : frames.size())
              + ", limit="
              + MAX_FRAMES
              + ")");
      return false;
    }
    MirrorDebug.log("STORAGE", "saving slot " + slot + ", frames=" + frames.size());
    Generation previous = readBest(slot);
    NBTTagCompound root =
        codec.encodeRecording(frames, settings, nameOf(previous), descOf(previous));
    if (root == null) return false;
    boolean ok = write(slot, root, previous);
    if (!ok) LOG.error("Failed to save slot {}", slot);
    return ok;
  }
  /**
   * Fallback when the strict build rejected a frame: invalid frames become pauses (see
   * RecordingCodec).
   */
  public boolean saveRecordingLenient(int slot, List<Frame> frames, NBTTagCompound settings) {
    if (!validSlot(slot) || frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES)
      return false;
    Generation previous = readBest(slot);
    NBTTagCompound root =
        codec.encodeRecordingLenient(frames, settings, nameOf(previous), descOf(previous));
    if (root == null) return false;
    int invalid = 0;
    for (Frame f : frames) if (!codec.validFrame(f)) invalid++;
    LOG.warn(
        "Slot {}: {} of {} frames failed validation and were saved as pauses",
        slot,
        invalid,
        frames.size());
    return write(slot, root, previous);
  }
  // === Background writing ===
  /**
   * Queues a slot for background saving: the main thread hands over a ready snapshot and moves on;
   * the result with the same ticket arrives in SaveCallback, which shows which snapshot it belongs
   * to.
   */
  public void queueRecordingSave(
      int slot, List<Frame> frames, NBTTagCompound settings, long ticket) {
    if (!validSlot(slot) || frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
      MirrorDebug.log("STORAGE", "background save rejected for slot " + slot);
      return;
    }
    saver.queue(slot, frames, settings, ticket);
  }

  public void setSaveCallback(RecordingSaveWorker.SaveCallback cb) {
    saver.setCallback(cb);
  }
  /**
   * The slot is still being written in the background: user file operations are better rejected
   * than waited for on the client thread.
   */
  public boolean isSlotSaving(int slot) {
    return saver.isSaving(slot);
  }
  /**
   * Slot quiet: neither a queued request nor an active background write. False means timeout, the
   * operation must not proceed.
   */
  public boolean awaitSlotQuiescent(int slot) {
    return saver.awaitQuiescent(slot, RecordingSaveWorker.QUIESCE_TIMEOUT_MS);
  }
  /** For the shutdown hook: wait for the background thread and save the leftovers synchronously. */
  public void shutdownFlush() {
    saver.shutdownFlush();
  }
  /**
   * The slot recording chosen by the common readBest rule. loadRecording returns null if no copy is
   * readable.
   */
  public static final class LoadedRecording {
    public final List<Frame> frames;
    /** The frames came from .bak: the slot settings and name belong to that copy too. */
    public final boolean fromBackup;

    LoadedRecording(List<Frame> frames, boolean fromBackup) {
      this.frames = frames;
      this.fromBackup = fromBackup;
    }
  }

  public LoadedRecording loadRecording(int slot) {
    if (!validSlot(slot)) return null;
    Generation g = readBest(slot);
    if (g == null) {
      MirrorDebug.log("STORAGE", "slot " + slot + ": no readable recording in .nbt or .bak");
      return null;
    }
    if (g.backup || !g.decoded.intact()) {
      LOG.warn(
          "Slot {} loaded from {} with {} damaged frames replaced by pauses",
          slot,
          g.backup ? ".bak" : ".nbt",
          g.decoded.damaged);
      MirrorDebug.log(
          "STORAGE",
          "slot "
              + slot
              + ": loaded "
              + g.decoded.frames.size()
              + " frames from "
              + (g.backup ? ".bak" : ".nbt")
              + ", damaged "
              + g.decoded.damaged);
    }
    return new LoadedRecording(g.decoded.frames, g.backup);
  }
  // === Deletion and trash ===
  /** Deletion result codes: the GUI and the command show the exact text for them. */
  public static final int DELETE_FAILED = 0,
      DELETE_TRASHED = 1,
      DELETE_NO_COPY = 2,
      DELETE_PARTIAL = 3;

  public boolean deleteRecording(int slot) {
    return deleteRecordingEx(slot) != DELETE_FAILED;
  }

  public int deleteRecordingEx(int slot) {
    if (!validSlot(slot)) return DELETE_FAILED;
    if (!awaitSlotQuiescent(slot)) {
      LOG.error(
          "Refusing to delete slot {}: background save did not finish in time",
          Integer.valueOf(slot));
      return DELETE_FAILED;
    }
    // The trash gets the same copy that playback would choose: a partly intact one too, it can be
    // restored.
    Generation best = readBest(slot);
    int status = DELETE_TRASHED;
    if (best != null) {
      if (!trashStore.archiveToTrash(slot, best.root)) {
        LOG.error("Refusing to delete slot {}: trash copy failed", slot);
        return DELETE_FAILED;
      }
    } else {
      // Unreadable slot: delete without a copy, but report it.
      status = DELETE_NO_COPY;
      LOG.warn(
          "Deleting slot {} without a trash copy: no readable recording", Integer.valueOf(slot));
      MirrorDebug.log("STORAGE", "slot " + slot + " deleted without trash copy (unreadable)");
    }
    File t = fileStore.tmpFile(slot), b = fileStore.bakFile(slot), n = fileStore.nbtFile(slot);
    boolean ok = true;
    try {
      if (n.exists() && !n.delete()) {
        LOG.error("Could not delete main file of slot {}", Integer.valueOf(slot));
        return DELETE_FAILED;
      }
      if (t.exists() && !t.delete()) {
        LOG.error("Could not delete temp file of slot {}", Integer.valueOf(slot));
        ok = false;
      }
      if (b.exists() && !b.delete()) {
        LOG.error("Could not delete backup of slot {}", Integer.valueOf(slot));
        ok = false;
      }
    } finally {
      invalidateSummary(slot);
    }
    if (!ok) return DELETE_PARTIAL;
    return status;
  }

  public boolean restoreTrash(String name, int slot) {
    if (!validSlot(slot)) return false;
    if (!awaitSlotQuiescent(slot)) return false;
    if (summary(slot, true).count > 0) return false;
    NBTTagCompound r = trashStore.restorableRoot(name, fileStore, codec);
    if (r == null || !write(slot, r, readBest(slot))) return false;
    trashStore.restored(name, slot);
    return true;
  }

  public boolean deleteTrash(String name) {
    return trashStore.deleteTrash(name);
  }

  public boolean clearTrash() {
    return trashStore.clearTrash();
  }

  public String latestTrashFile() {
    return trashStore.latestTrashFile();
  }

  public List<String> listTrashFiles() {
    return trashStore.listTrashFiles();
  }

  public int trashSlot(String name) {
    return trashStore.trashSlot(name, fileStore);
  }

  public int trashFrames(String name) {
    return trashStore.trashFrames(name, fileStore);
  }

  public long trashTime(String name) {
    return trashStore.trashTime(name, fileStore);
  }

  public String trashTitle(String name) {
    return trashStore.trashTitle(name, fileStore);
  }

  public String getTrashDirectoryPath() {
    return trashStore.getTrashDir().getAbsolutePath();
  }
  // === Slot metadata and settings ===
  public int peekFrameCount(int slot) {
    return summary(slot).count;
  }

  public String loadSlotName(int s) {
    return summary(s).name;
  }

  public String loadSlotDesc(int s) {
    return summary(s).desc;
  }
  /** Slot settings from the same snapshot as the other metadata: each call gets a new object. */
  public NBTTagCompound loadSlotSettings(int slot) {
    return decodeSettings(summary(slot).settings);
  }

  /**
   * Slot edit in a single read-write cycle: name, description and settings (null means unchanged).
   * If nothing changed, the file is not rewritten and the backup stays the same.
   */
  public boolean saveSlotEdit(int slot, String name, String desc, NBTTagCompound settings) {
    if (!validSlot(slot) || !awaitSlotQuiescent(slot)) return false;
    Generation g = readBest(slot);
    if (g == null) return false;
    NBTTagCompound r = g.root;
    boolean changed = false;
    if (name != null) changed |= setText(r, "SlotName", codec.clean(name, 40));
    if (desc != null) changed |= setText(r, "SlotDesc", codec.clean(desc, 120));
    if (settings != null && !settings.equals(r.getCompoundTag("SlotSettings"))) {
      r.setTag("SlotSettings", settings);
      changed = true;
    }
    return !changed || write(slot, r, g);
  }

  public boolean saveSlotSettings(int slot, NBTTagCompound settings) {
    return settings != null && saveSlotEdit(slot, null, null, settings);
  }

  public boolean saveSlotMetadata(int slot, String name, String desc) {
    return saveSlotEdit(slot, name == null ? "" : name, desc == null ? "" : desc, null);
  }

  private static boolean setText(NBTTagCompound r, String key, String value) {
    if (value.equals(r.getString(key)) && (r.hasKey(key) || value.isEmpty())) return false;
    r.setString(key, value);
    return true;
  }
  // === Slot copies: duplication and export ===
  /**
   * Root for a slot copy. memoryFrames != null means the slot has unsaved frames: the copy is built
   * from memory, otherwise the disk would return an old checkpoint.
   */
  private NBTTagCompound copySource(int slot, List<Frame> memoryFrames, NBTTagCompound settings) {
    Generation g = readBest(slot);
    if (memoryFrames == null) return g == null ? null : g.root;
    return codec.encodeRecordingLenient(memoryFrames, settings, nameOf(g), descOf(g));
  }

  public boolean duplicateRecording(
      int source,
      int target,
      String emptyLabel,
      String copySuffix,
      List<Frame> memoryFrames,
      NBTTagCompound memorySettings) {
    if (!validSlot(source) || !validSlot(target) || source == target) return false;
    // Both slots are waited for within one shared deadline: otherwise the main thread could sit
    // through two timeouts in a row. A remainder of 0 is a check without waiting, not a refusal: an
    // already quiet slot passes.
    long deadline = System.nanoTime() + RecordingSaveWorker.QUIESCE_TIMEOUT_MS * 1000000L;
    if (!awaitSlotQuiescent(source)) return false;
    long leftMs = Math.max(0L, (deadline - System.nanoTime()) / 1000000L);
    if (!saver.awaitQuiescent(target, leftMs)) return false;
    // The busy check protects against overwriting: files only, no cache.
    if (summary(target, true).count > 0) return false;
    NBTTagCompound r = copySource(source, memoryFrames, memorySettings);
    if (r == null) return false;
    String sourceName = codec.clean(r.getString("SlotName"), 40);
    if (sourceName.isEmpty()) {
      r.setString("SlotName", codec.clean(emptyLabel, 40));
      return write(target, r, readBest(target));
    }
    String suffix = copySuffix == null ? "" : copySuffix;
    String base = sourceName;
    while (!suffix.isEmpty() && base.endsWith(suffix))
      base = base.substring(0, base.length() - suffix.length()).trim();
    if (base.length() + suffix.length() > 40)
      base = base.substring(0, Math.max(0, 40 - suffix.length())).trim();
    r.setString("SlotName", base + suffix);
    return write(target, r, readBest(target));
  }
  // === Export and import ===
  public String exportRecording(int slot, List<Frame> memoryFrames, NBTTagCompound memorySettings) {
    if (!awaitSlotQuiescent(slot)) return null;
    NBTTagCompound r = copySource(slot, memoryFrames, memorySettings);
    if (r == null) return null;
    String base = codec.safeName(codec.clean(r.getString("SlotName"), 40));
    if (base.isEmpty()) base = "slot_" + slot;
    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    File out = new File(exportDir, base + "-slot-" + slot + "-" + stamp + ".mrr");
    int suffix = 2;
    while (out.exists())
      out = new File(exportDir, base + "-slot-" + slot + "-" + stamp + "-" + (suffix++) + ".mrr");
    try (FileOutputStream fos = new FileOutputStream(out)) {
      r.setLong("ExportedAt", System.currentTimeMillis());
      CompressedStreamTools.writeCompressed(r, fos);
      return out.getName();
    } catch (Exception e) {
      LOG.error("Export failed for slot {}", slot, e);
      return null;
    }
  }

  public List<String> listExportFiles() {
    File[] files =
        exportDir.listFiles(
            new FilenameFilter() {
              public boolean accept(File d, String n) {
                return n.toLowerCase(Locale.ROOT).endsWith(".mrr");
              }
            });
    List<File> all = new ArrayList<File>();
    if (files != null) Collections.addAll(all, files);
    Collections.sort(
        all,
        new Comparator<File>() {
          public int compare(File a, File b) {
            return Long.compare(b.lastModified(), a.lastModified());
          }
        });
    List<String> names = new ArrayList<String>();
    for (File f : all) if (f.isFile()) names.add(f.getName());
    return names;
  }

  public boolean importRecording(int slot, String fileName) {
    if (!validSlot(slot) || fileName == null) return false;
    if (!awaitSlotQuiescent(slot)) return false;
    if (summary(slot, true).count > 0) return false;
    File in = new File(exportDir, new File(fileName).getName());
    NBTTagCompound r = fileStore.tryRead(in);
    if (!codec.validRoot(r)) return false;
    r.setInteger(
        "Version",
        Math.max(1, Math.min(FORMAT_VERSION, r.hasKey("Version") ? r.getInteger("Version") : 2)));
    r.setInteger("FrameCount", r.getTagList("Frames", Constants.NBT.TAG_COMPOUND).tagCount());
    r.setBoolean("Imported", true);
    return write(slot, r, readBest(slot));
  }
  // === File info ===
  // Size and date are needed by the screen every frame. This is only a file stat with a short
  // lifetime: a full read is not acceptable here, otherwise during a recording with checkpoints the
  // render thread would parse the file after each save and keep it open.
  public long getLastModified(int slot) {
    if (!validSlot(slot)) return 0L;
    FileStat s = stat(slot);
    return s.nHas ? s.ns : s.bHas ? s.bs : 0L;
  }

  public long getFileSize(int slot) {
    if (!validSlot(slot)) return 0L;
    FileStat s = stat(slot);
    return s.nHas ? s.nz : s.bHas ? s.bz : 0L;
  }

  public String getExportDirectoryPath() {
    return exportDir.getAbsolutePath();
  }

  public boolean isImportedRecording(int slot) {
    return validSlot(slot) && summary(slot).imported;
  }
  // === Internals: cache and reading ===
  /**
   * Any of our own writes to the slot. previous is the copy chosen by readBest before the write: if
   * it is .bak, the current .nbt is worse and must not replace it. The generation is incremented
   * and the cache is cleared, even on partial failure.
   */
  private boolean write(int slot, NBTTagCompound root, Generation previous) {
    try {
      return fileStore.writeAtomically(slot, root, previous != null && previous.backup);
    } finally {
      invalidateSummary(slot);
    }
  }
  /** One slot copy (.nbt or .bak), read and verified in full. */
  private static final class Generation {
    final NBTTagCompound root;
    final RecordingCodec.Decoded decoded;
    final boolean backup;

    Generation(NBTTagCompound root, RecordingCodec.Decoded decoded, boolean backup) {
      this.root = root;
      this.decoded = decoded;
      this.backup = backup;
    }
  }

  private Generation readGeneration(File f, boolean backup) {
    NBTTagCompound root = fileStore.tryRead(f);
    RecordingCodec.Decoded decoded = codec.decodeRoot(root);
    return decoded == null ? null : new Generation(root, decoded, backup);
  }
  /**
   * The single rule for choosing a slot copy, for playback, editing, copying, trash and rotation:
   * intact .nbt → intact .bak → the copy with more surviving frames (.nbt on a tie). .bak is read
   * only if .nbt is not intact. The choice is remembered for the slot summary so the name, frame
   * count and settings are shown from the same copy as the frames.
   */
  private Generation readBest(int slot) {
    if (!validSlot(slot)) return null;
    File n = fileStore.nbtFile(slot), b = fileStore.bakFile(slot);
    long gen;
    synchronized (summaryLock) {
      gen = generation[slot];
    }
    FileStat st = FileStat.of(n, b);
    Generation best = readGeneration(n, false);
    if (best == null || !best.decoded.intact()) {
      Generation bak = readGeneration(b, true);
      if (bak != null
          && (best == null || bak.decoded.intact() || bak.decoded.valid > best.decoded.valid))
        best = bak;
    }
    rememberSelection(slot, gen, st, best != null && best.backup);
    return best;
  }

  private static String nameOf(Generation g) {
    return g == null ? "" : g.root.getString("SlotName");
  }

  private static String descOf(Generation g) {
    return g == null ? "" : g.root.getString("SlotDesc");
  }
  /** Which copy was chosen for these files. A snapshot computed from another copy is reset. */
  private void rememberSelection(int slot, long gen, FileStat st, boolean backup) {
    synchronized (summaryLock) {
      if (generation[slot] != gen) return;
      selections.put(slot, new Selection(st, backup));
      SlotSummary old = summaries.get(slot);
      if (old != null && old.fromBackup != backup) summaries.remove(slot);
    }
  }

  private boolean backupSelected(int slot, FileStat st) {
    synchronized (summaryLock) {
      Selection s = selections.get(slot);
      return s != null && s.backup && s.stat.equals(st);
    }
  }
  /**
   * How often the slot snapshot is checked against the file system. Our own writes reset the cache
   * immediately.
   */
  private static final long STAT_TTL_NS = 300L * 1000000L;

  /** Root fields needed by the slot summary: the scanner keeps nothing else. */
  private static final Set<String> SUMMARY_KEYS =
      new HashSet<String>(
          Arrays.asList("Version", "Frames", "SlotName", "SlotDesc", "Imported", "SlotSettings"));

  private SlotSummary summary(int slot) {
    return summary(slot, false);
  }
  /**
   * Slot snapshot. fresh=true means checking files without using the file-stat TTL: for checks that
   * data overwriting depends on. A snapshot computed concurrently with our own write is not cached
   * and is recomputed, otherwise it would live on with the pre-write state.
   */
  private SlotSummary summary(int slot, boolean fresh) {
    if (!validSlot(slot)) return SlotSummary.EMPTY;
    File n = fileStore.nbtFile(slot), b = fileStore.bakFile(slot);
    SlotSummary result = SlotSummary.EMPTY;
    for (int attempt = 0; attempt < 3; attempt++) {
      long now = System.nanoTime(), gen;
      synchronized (summaryLock) {
        SlotSummary old = summaries.get(slot);
        if (!fresh && old != null && now - old.checkedAt < STAT_TTL_NS) return old;
        gen = generation[slot];
      }
      FileStat st = FileStat.of(n, b);
      synchronized (summaryLock) {
        if (generation[slot] == gen) {
          stats.put(slot, new StatEntry(st, now));
          SlotSummary old = summaries.get(slot);
          if (old != null && old.stat.equals(st)) {
            old.checkedAt = now;
            return old;
          }
        }
      }
      // Heavy file parsing happens outside the lock: the map is read by both the main and
      // background threads.
      result = scanSummary(slot, n, b, st);
      synchronized (summaryLock) {
        if (generation[slot] != gen) continue;
        result.checkedAt = now;
        summaries.put(slot, result);
        return result;
      }
    }
    // The slot is being rewritten right now: return the last snapshot without caching it.
    return result;
  }
  /**
   * Snapshot fields from the streaming parse. If a full read (readBest) has already chosen .bak for
   * these files, the summary comes from it too; otherwise from .nbt, and from .bak if the structure
   * is bad.
   */
  private SlotSummary scanSummary(int slot, File n, File b, FileStat st) {
    boolean fromBackup = backupSelected(slot, st);
    NbtRootScanner.Root r = fromBackup ? null : fileStore.scanRoot(n, SUMMARY_KEYS);
    if (!validStructure(r)) {
      r = fileStore.scanRoot(b, SUMMARY_KEYS);
      fromBackup = true;
    }
    if (!validStructure(r)) return new SlotSummary(st, 0, "", "", false, null, false);
    return new SlotSummary(
        st,
        r.listCount("Frames", NbtRootScanner.COMPOUND),
        codec.clean(r.getString("SlotName"), 40),
        codec.clean(r.getString("SlotDesc"), 120),
        r.getBoolean("Imported"),
        r.compoundPayload("SlotSettings"),
        fromBackup);
  }
  /** The same rule as RecordingCodec.validStructure, but on the streaming parse. */
  static boolean validStructure(NbtRootScanner.Root r) {
    if (r == null) return false;
    int v = r.hasKey("Version") ? r.getInteger("Version") : 0;
    if (v < 0 || v > FORMAT_VERSION) return false;
    int actual = r.listCount("Frames", NbtRootScanner.COMPOUND);
    return actual >= 1 && actual <= MAX_FRAMES;
  }

  private NBTTagCompound decodeSettings(byte[] payload) {
    if (payload == null) return null;
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 3);
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(NbtRootScanner.COMPOUND);
      out.writeUTF("");
      out.write(payload);
      out.flush();
      return CompressedStreamTools.read(
          new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
          new NBTSizeTracker(RecordingFileStore.MAX_NBT_BYTES));
    } catch (Exception e) {
      LOG.error("Slot settings could not be decoded", e);
      return null;
    }
  }

  private FileStat stat(int slot) {
    long now = System.nanoTime(), gen;
    synchronized (summaryLock) {
      StatEntry e = stats.get(slot);
      if (e != null && now - e.checkedAt < STAT_TTL_NS) return e.stat;
      gen = generation[slot];
    }
    FileStat st = FileStat.of(fileStore.nbtFile(slot), fileStore.bakFile(slot));
    synchronized (summaryLock) {
      if (generation[slot] == gen) stats.put(slot, new StatEntry(st, now));
    }
    return st;
  }

  private void invalidateSummary(int slot) {
    if (!validSlot(slot)) return;
    synchronized (summaryLock) {
      generation[slot]++;
      summaries.remove(slot);
      stats.remove(slot);
      selections.remove(slot);
    }
  }
  /**
   * Cold start: slot summaries are built in the background so that the first menu opening does not
   * read files on the render thread. If the menu got there first, the main thread computes the
   * missing slot itself; the result is the same, the cache is shared. Empty slots cost two FS
   * calls. Called once after the storage is created: from the constructor the thread would see a
   * partially constructed object.
   */
  public void warmUpSummaries() {
    synchronized (summaryLock) {
      if (prefetchStarted) return;
      prefetchStarted = true;
    }
    Thread t =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  for (int s = 1; s <= 100; s++) summary(s, false);
                } catch (Throwable e) {
                  LOG.warn("Slot summary prefetch stopped", e);
                }
              }
            },
            "MirrorRecorder-summary-prefetch");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  private boolean prefetchStarted = false;

  private static final class FileStat {
    final boolean nHas, bHas;
    final long ns, nz, bs, bz;

    private FileStat(boolean nHas, long ns, long nz, boolean bHas, long bs, long bz) {
      this.nHas = nHas;
      this.ns = ns;
      this.nz = nz;
      this.bHas = bHas;
      this.bs = bs;
      this.bz = bz;
    }

    static final FileStat NONE = new FileStat(false, 0L, 0L, false, 0L, 0L);

    static FileStat of(File n, File b) {
      boolean nHas = n.isFile(), bHas = b.isFile();
      return new FileStat(
          nHas,
          nHas ? n.lastModified() : 0L,
          nHas ? n.length() : 0L,
          bHas,
          bHas ? b.lastModified() : 0L,
          bHas ? b.length() : 0L);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof FileStat)) return false;
      FileStat f = (FileStat) o;
      return nHas == f.nHas
          && bHas == f.bHas
          && ns == f.ns
          && nz == f.nz
          && bs == f.bs
          && bz == f.bz;
    }

    @Override
    public int hashCode() {
      return (int) (ns ^ nz ^ bs ^ bz) ^ (nHas ? 1 : 0) ^ (bHas ? 2 : 0);
    }
  }

  /** Result of {@code readBest} for a specific file state. */
  private static final class Selection {
    final FileStat stat;
    final boolean backup;

    Selection(FileStat stat, boolean backup) {
      this.stat = stat;
      this.backup = backup;
    }
  }

  private static final class StatEntry {
    final FileStat stat;
    final long checkedAt;

    StatEntry(FileStat stat, long checkedAt) {
      this.stat = stat;
      this.checkedAt = checkedAt;
    }
  }
  /** Slot snapshot. checkedAt changes only under summaryLock. */
  private static final class SlotSummary {
    static final SlotSummary EMPTY = new SlotSummary(FileStat.NONE, 0, "", "", false, null, false);
    final FileStat stat;
    final int count;
    final String name, desc;
    final boolean imported, fromBackup;
    final byte[] settings;
    long checkedAt = 0L;

    SlotSummary(
        FileStat stat,
        int count,
        String name,
        String desc,
        boolean imported,
        byte[] settings,
        boolean fromBackup) {
      this.stat = stat;
      this.count = count;
      this.name = name;
      this.desc = desc;
      this.imported = imported;
      this.settings = settings;
      this.fromBackup = fromBackup;
    }
  }
}

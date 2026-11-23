package com.mirror.recorder.storage;

import com.mirror.recorder.debug.MirrorDebug;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Trash: storing, restoring and purging deleted recordings. */
public class TrashStore {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Storage");
  public static final int MAX_TRASH = 100;
  /** Only for clean(): the method is stateless. */
  private static final RecordingCodec CODEC = new RecordingCodec();

  /** Root fields needed by a trash list row. */
  private static final Set<String> SUMMARY_KEYS =
      new HashSet<String>(Arrays.asList("Version", "Frames", "TrashSlot", "TrashedAt", "SlotName"));

  private final File trashDir, saveDir;
  private final Set<String> sessionTrash = new HashSet<String>();
  private final Map<String, TrashSummary> trashSummaries = new HashMap<String, TrashSummary>();

  public TrashStore(File saveDir, File trashDir) {
    this.saveDir = saveDir;
    this.trashDir = trashDir;
    loadSessionTrash();
  }

  public File getTrashDir() {
    return trashDir;
  }
  /** Trims the trash to the limit: called after each successful archiving. */
  private void trimAfterArchive() {
    trimTrash();
  }
  /** Archives the best copy into the trash. */
  public boolean archiveToTrash(int slot, NBTTagCompound best) {
    File out =
        new File(
            trashDir,
            "slot-"
                + slot
                + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date())
                + ".mrr");
    int suffix = 2;
    while (out.exists())
      out =
          new File(
              trashDir,
              "slot-"
                  + slot
                  + "-"
                  + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date())
                  + "-"
                  + (suffix++)
                  + ".mrr");
    try (FileOutputStream fos = new FileOutputStream(out)) {
      best.setInteger("TrashSlot", slot);
      best.setLong("TrashedAt", System.currentTimeMillis());
      CompressedStreamTools.writeCompressed(best, fos);
    } catch (Exception e) {
      LOG.error("Trash copy failed for slot {}", slot, e);
      return false;
    }
    // The file write is closed: session state and trimming are already outside try-with-resources.
    sessionTrash.add(out.getName());
    saveSessionTrash();
    trimAfterArchive();
    MirrorDebug.log("TRASH", "slot " + slot + " archived to " + out.getName());
    return true;
  }

  public List<String> listTrashFiles() {
    File[] files =
        trashDir.listFiles(
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

  private File trashFile(String name) {
    return name == null ? null : new File(trashDir, new File(name).getName());
  }

  public TrashSummary trashSummary(String name, RecordingFileStore fileStore) {
    File f = trashFile(name);
    if (f == null || !f.isFile()) return null;
    String key = f.getName();
    long st = f.lastModified(), sz = f.length();
    TrashSummary old = trashSummaries.get(key);
    if (old != null && old.matches(st, sz)) return old;
    // A list row needs only the root fields and the frame count: parsing without the frame tree.
    NbtRootScanner.Root r = fileStore.scanRoot(f, SUMMARY_KEYS);
    if (!StorageManager.validStructure(r)) r = null;
    TrashSummary fresh =
        r == null
            ? new TrashSummary(st, sz, 0, 0, 0L, "")
            : new TrashSummary(
                st,
                sz,
                r.getInteger("TrashSlot"),
                r.listCount("Frames", NbtRootScanner.COMPOUND),
                r.hasKey("TrashedAt") ? r.getLong("TrashedAt") : 0L,
                CODEC.clean(r.getString("SlotName"), 40));
    trashSummaries.put(key, fresh);
    return fresh;
  }

  public int trashSlot(String name, RecordingFileStore fs) {
    TrashSummary t = trashSummary(name, fs);
    return t == null ? 0 : t.slot;
  }

  public int trashFrames(String name, RecordingFileStore fs) {
    TrashSummary t = trashSummary(name, fs);
    return t == null ? 0 : t.frames;
  }

  public long trashTime(String name, RecordingFileStore fs) {
    TrashSummary t = trashSummary(name, fs);
    if (t == null) return 0L;
    return t.trashedAt > 0L ? t.trashedAt : t.stamp;
  }

  public String trashTitle(String name, RecordingFileStore fs) {
    TrashSummary t = trashSummary(name, fs);
    return t == null ? "" : t.title;
  }

  public boolean wasSessionTracked(String key) {
    return sessionTrash.contains(key);
  }

  /**
   * Root for restoring into a slot, or null. The rule is the same as when loading a slot
   * (RecordingCodec.decodeRoot): a partly intact copy is accepted; damaged frames become pauses on
   * loading the restored slot. A foreign file (not from this game's trash) is marked as imported.
   */
  public NBTTagCompound restorableRoot(
      String name, RecordingFileStore fileStore, RecordingCodec codec) {
    File f = trashFile(name);
    if (f == null || !f.isFile()) return null;
    NBTTagCompound r = fileStore.tryRead(f);
    if (codec.decodeRoot(r) == null) return null;
    r.removeTag("TrashSlot");
    r.removeTag("TrashedAt");
    r.setInteger(
        "Version",
        Math.max(
            1,
            Math.min(
                RecordingCodec.FORMAT_VERSION, r.hasKey("Version") ? r.getInteger("Version") : 2)));
    r.setInteger("FrameCount", r.getTagList("Frames", Constants.NBT.TAG_COMPOUND).tagCount());
    if (!wasSessionTracked(f.getName())) r.setBoolean("Imported", true);
    return r;
  }
  /** The recording was restored into a slot: the trash file is no longer needed. */
  public void restored(String name, int slot) {
    File f = trashFile(name);
    if (f == null) return;
    String key = f.getName();
    boolean ownCopy = wasSessionTracked(key);
    // Forget the file only once it is really gone: a file that stayed behind but is no longer
    // tracked would later look like a foreign import (slot settings not applied automatically).
    if (f.isFile() && !f.delete()) {
      LOG.error("Failed to delete restored trash file {}", key);
      return;
    }
    forgetTrash(key);
    MirrorDebug.log(
        "TRASH",
        "restored " + key + " into slot " + slot + (ownCopy ? " (own copy)" : " (foreign file)"));
  }

  public boolean deleteTrash(String name) {
    File f = trashFile(name);
    if (f == null || !f.isFile() || !f.delete()) return false;
    forgetTrash(f.getName());
    return true;
  }

  public boolean clearTrash() {
    boolean ok = true;
    for (String n : listTrashFiles()) {
      File f = trashFile(n);
      if (f == null) continue;
      if (f.isFile() && !f.delete()) ok = false;
      else forgetTrash(f.getName());
    }
    return ok;
  }

  public String latestTrashFile() {
    List<String> all = listTrashFiles();
    return all.isEmpty() ? null : all.get(0);
  }

  private void forgetTrash(String fileName) {
    if (fileName == null) return;
    sessionTrash.remove(fileName);
    saveSessionTrash();
    trashSummaries.remove(fileName);
  }

  private void trimTrash() {
    List<String> all = listTrashFiles();
    for (int i = MAX_TRASH; i < all.size(); i++) {
      File f = trashFile(all.get(i));
      if (f != null && f.isFile() && f.delete()) forgetTrash(f.getName());
    }
  }

  private File sessionStateFile() {
    return new File(saveDir, ".trash_session");
  }

  private void loadSessionTrash() {
    File sf = sessionStateFile();
    if (!sf.isFile()) return;
    try (BufferedReader r =
        new BufferedReader(new InputStreamReader(new FileInputStream(sf), "UTF-8"))) {
      String line;
      while ((line = r.readLine()) != null) {
        String t = line.trim();
        if (!t.isEmpty()) sessionTrash.add(t);
      }
    } catch (Exception e) {
      LOG.error("Failed to load session trash state", e);
    }
  }

  private void saveSessionTrash() {
    File target = sessionStateFile();
    File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
    try (BufferedWriter w =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"))) {
      for (String name : sessionTrash) {
        w.write(name);
        w.newLine();
      }
      w.flush();
    } catch (Exception e) {
      LOG.error("Failed to save session trash state", e);
      tmp.delete();
      return;
    }
    if (!moveFile(tmp, target)) {
      LOG.error("Failed to replace session state file");
      tmp.delete();
    }
  }
  /** First an atomic move, then a regular one with replace, and only then renameTo. */
  private static boolean moveFile(File from, File to) {
    if (from == null || to == null || !from.isFile()) return false;
    try {
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch (Exception atomicUnsupported) {
      try {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
      } catch (Exception replaceFailed) {
        return from.renameTo(to);
      }
    }
  }

  public static final class TrashSummary {
    final long stamp, size, trashedAt;
    final int slot, frames;
    final String title;

    TrashSummary(long stamp, long size, int slot, int frames, long trashedAt, String title) {
      this.stamp = stamp;
      this.size = size;
      this.slot = slot;
      this.frames = frames;
      this.trashedAt = trashedAt;
      this.title = title;
    }

    boolean matches(long s, long z) {
      return stamp == s && size == z;
    }
  }
}

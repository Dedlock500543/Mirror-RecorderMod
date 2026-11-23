package com.mirror.recorder.storage;

import com.mirror.recorder.debug.MirrorDebug;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * File I/O: reading, writing, atomic saving, backup. Knows nothing about frames or the recording
 * schema.
 */
public class RecordingFileStore {
  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Storage");
  static final long MAX_FILE_BYTES = 64L * 1024L * 1024L, MAX_NBT_BYTES = 256L * 1024L * 1024L;
  private final File saveDir;

  public RecordingFileStore(File saveDir) {
    this.saveDir = saveDir;
  }

  public File nbtFile(int s) {
    return new File(saveDir, "slot_" + s + ".nbt");
  }

  public File tmpFile(int s) {
    return new File(saveDir, "slot_" + s + ".tmp");
  }

  public File bakFile(int s) {
    return new File(saveDir, "slot_" + s + ".bak");
  }
  /**
   * Bounded read: limits on the file size and estimated NBT memory usage. The stream is read to the
   * end: only then does GZIPInputStream check the CRC and length from the trailer, and extra bytes
   * after the NBT root mean a damaged file.
   */
  public NBTTagCompound tryRead(File f) {
    if (f == null || !f.isFile() || f.length() <= 0L || f.length() > MAX_FILE_BYTES) return null;
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new GZIPInputStream(open(f)), 16384))) {
      NBTTagCompound root = CompressedStreamTools.read(in, new NBTSizeTracker(MAX_NBT_BYTES));
      return atEnd(in, f) ? root : null;
    } catch (Exception e) {
      LOG.error("Read error for {}", f.getName(), e);
      return null;
    }
  }
  /**
   * Streaming parse of the root without building the frame tree: for slot and trash summaries. The
   * same file-size limit and end-of-stream check as tryRead, but limits decompressed bytes. null
   * means there is no file or it is unreadable.
   */
  NbtRootScanner.Root scanRoot(File f, Set<String> keepKeys) {
    if (f == null || !f.isFile() || f.length() <= 0L || f.length() > MAX_FILE_BYTES) return null;
    try (InputStream in = new BufferedInputStream(new GZIPInputStream(open(f), 16384), 16384)) {
      NbtRootScanner.Root root = NbtRootScanner.scan(in, MAX_NBT_BYTES, keepKeys);
      return atEnd(in, f) ? root : null;
    } catch (Exception e) {
      LOG.error("Read error for {}", f.getName(), e);
      return null;
    }
  }
  /**
   * End of the decompressed stream: reading -1 makes GZIPInputStream check the trailer (CRC,
   * length).
   */
  private static boolean atEnd(InputStream in, File f) throws IOException {
    if (in.read() < 0) return true;
    LOG.error("Read error for {}: unexpected data after the NBT root", f.getName());
    return false;
  }
  /**
   * Reading via NIO: on Windows such a stream is opened with FILE_SHARE_DELETE, so a concurrent
   * read does not prevent the background save from renaming this file (FileInputStream would).
   */
  private static InputStream open(File f) throws IOException {
    return Files.newInputStream(f.toPath());
  }

  public void writeFast(NBTTagCompound root, File file) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(file);
        FastGzip gz = new FastGzip(fos);
        DataOutputStream out = new DataOutputStream(gz)) {
      CompressedStreamTools.write(root, out);
    }
  }
  /** Checks GZIP integrity and decompressed size by streaming; does not validate NBT. */
  public boolean verifyStream(File f) {
    if (f == null || !f.isFile() || f.length() <= 0L || f.length() > MAX_FILE_BYTES) return false;
    try (GZIPInputStream in = new GZIPInputStream(new BufferedInputStream(open(f)), 16384)) {
      byte[] buf = new byte[16384];
      long total = 0L;
      int n;
      while ((n = in.read(buf)) >= 0) {
        total += n;
        if (total > MAX_NBT_BYTES) return false;
      }
      return total > 0L;
    } catch (Exception e) {
      LOG.error("Verification read failed for {}", f.getName(), e);
      return false;
    }
  }
  /** First an atomic move via NIO, then a regular one with replace, and only then renameTo. */
  public boolean moveFile(File from, File to) {
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
  /**
   * Atomic save: tmp → verify → rotate → commit.
   *
   * <p>Which copy stays as the backup is decided by the caller: it has already read and compared
   * both (StorageManager.readBest). keepBackup=false: the current .nbt becomes the new .bak;
   * keepBackup=true: the current .nbt is worse than .bak, so it is deleted and .bak stays.
   */
  public boolean writeAtomically(int slot, NBTTagCompound root, boolean keepBackup) {
    File tmp = tmpFile(slot), nbt = nbtFile(slot), bak = bakFile(slot);
    try {
      if (tmp.exists() && !tmp.delete()) return false;
      writeFast(root, tmp);
      if (!verifyStream(tmp)) {
        LOG.error("Written file for slot {} failed verification, keeping the old one", slot);
        MirrorDebug.log("STORAGE", "verification failed for slot " + slot + ", backup untouched");
        tmp.delete();
        return false;
      }
      if (nbt.exists()) {
        boolean rotated =
            keepBackup ? nbt.delete() : (!bak.exists() || bak.delete()) && moveFile(nbt, bak);
        if (!rotated) {
          tmp.delete();
          return false;
        }
        if (keepBackup)
          MirrorDebug.log("STORAGE", "slot " + slot + ": damaged primary dropped, backup kept");
      }
      if (!moveFile(tmp, nbt)) {
        if (bak.exists()) moveFile(bak, nbt);
        tmp.delete();
        return false;
      }
      return true;
    } catch (Exception e) {
      LOG.error("Atomic write failed for slot {}", slot, e);
      if (tmp.exists()) tmp.delete();
      if (!nbt.exists() && bak.exists()) moveFile(bak, nbt);
      return false;
    }
  }

  private static final class FastGzip extends GZIPOutputStream {
    FastGzip(OutputStream out) throws IOException {
      super(out, 16384);
      def.setLevel(Deflater.BEST_SPEED);
    }
  }
}

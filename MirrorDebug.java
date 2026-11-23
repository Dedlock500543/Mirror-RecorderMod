package com.mirror.recorder.debug;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Optional Mirror Recorder diagnostics. Off by default.
 *
 * <p>Writes a verbose log to {@code <gameDir>/logs/mirror-debug.log} and, with CSV enabled, one row
 * per sampled route tick, up to the row limit to {@code <gameDir>/logs/mirror-playback-*.csv}, so
 * the recorded route can be compared with the one the player actually took.
 *
 * <p>The class is deliberately self-contained: it depends only on the JDK and Log4j. While both
 * logging and CSV are off, output methods skip writing; initialization, configuration and queries
 * still work. Enabled in the mod settings, in the config file or with the JVM flags
 * -Dmirror.debug=true / -Dmirror.debug.csv=true.
 */
public final class MirrorDebug {

  private MirrorDebug() {}

  private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Debug");
  private static final Object LOCK = new Object();

  /** The log starts over when it outgrows this size. */
  private static final long MAX_LOG_BYTES = 8L * 1024L * 1024L;
  /** Row limit of one CSV (one playback run). */
  private static final int MAX_CSV_ROWS = 200000;
  /**
   * How many of our CSVs are kept in logs/, including the current one: an infinite loop starts a
   * new run (and file) every lap, so the per-file row limit does not bound the folder.
   */
  private static final int MAX_CSV_FILES = 20;

  private static final String CSV_PREFIX = "mirror-playback-", CSV_SUFFIX = ".csv";

  private static final Map<String, Long> THROTTLE = new HashMap<String, Long>();
  private static final Set<String> ONCE = new HashSet<String>();

  /**
   * -Dmirror.debug=true / -Dmirror.debug.csv=true enable diagnostics regardless of the config file.
   */
  private static final boolean FORCED = Boolean.getBoolean("mirror.debug");

  private static final boolean FORCED_CSV = Boolean.getBoolean("mirror.debug.csv");

  private static volatile boolean enabled = FORCED;
  private static volatile boolean csvEnabled = FORCED_CSV;

  private static File logDir;
  private static PrintWriter writer;
  private static PrintWriter csvWriter;
  private static long csvRun = -1L;
  private static int csvRows = 0;
  /** Origin of the monotonic clock: changing the system time must not affect intervals. */
  private static final long MONO_ORIGIN = System.nanoTime();

  private static long startedAtMono = 0L;
  /**
   * Estimated size of the currently open file in bytes: used to rotate the log within a session
   * too.
   */
  private static long written = 0L;
  /**
   * Lines since the last file-size check: the byte estimate in written is updated from character
   * counts, so every RESYNC_LINES lines we resync with the file.
   */
  private static int linesSinceResync = 0;

  private static final int RESYNC_LINES = 256;
  /**
   * The file failed to open or write: without this flag every line would try to open it again and
   * log a warning.
   */
  private static boolean openFailed = false;

  private static boolean rotateWarned = false;
  /** Old CSVs were not deleted: warn once, not on every new run. */
  private static boolean pruneWarned = false;
  /** The run whose CSV failed to open or write: otherwise the attempt would repeat every tick. */
  private static long csvFailedRun = Long.MIN_VALUE;

  /** Monotonic milliseconds since class loading. */
  private static long monoMs() {
    return (System.nanoTime() - MONO_ORIGIN) / 1000000L;
  }

  // ---------------------------------------------------------------- startup

  /** Called once from preInit with the game directory. */
  public static void init(File gameDir) {
    synchronized (LOCK) {
      File dir = new File(gameDir, "logs");
      logDir = (dir.isDirectory() || dir.mkdirs()) ? dir : gameDir;
      startedAtMono = monoMs();
      THROTTLE.clear();
      ONCE.clear();
      openFailed = false;
      csvFailedRun = Long.MIN_VALUE;
      if (enabled) {
        open();
        log("BOOT", "debug log: " + new File(logDir, "mirror-debug.log").getAbsolutePath());
      }
    }
  }

  public static void setEnabled(boolean value) {
    synchronized (LOCK) {
      if (FORCED) {
        value = true;
      }
      if (enabled == value) {
        return;
      }
      if (value) {
        enabled = true;
        openFailed = false;
        open();
        log("DEBUG", "diagnostics enabled");
      } else {
        log("DEBUG", "diagnostics disabled");
        enabled = false;
        close();
      }
      // CSV follows its own toggle: closing it here would split the current run's dump into two
      // files even though nobody changed csvEnabled.
    }
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void setCsvEnabled(boolean value) {
    synchronized (LOCK) {
      csvEnabled = value || FORCED_CSV;
      csvFailedRun = Long.MIN_VALUE;
      if (!csvEnabled) {
        csvClose();
      }
    }
  }

  /**
   * Whether diagnostics are forced: true if -Dmirror.debug=true pinned diagnostics on: the settings
   * toggle will not turn them off.
   */
  public static boolean isForced() {
    return FORCED;
  }

  /** Whether CSV writing is forced: true if -Dmirror.debug.csv=true pinned it on. */
  public static boolean isCsvForced() {
    return FORCED_CSV;
  }

  // ---------------------------------------------------------------- log

  /** A line to the diagnostics log and its copy to the regular game log. */
  public static void log(String tag, String message) {
    if (!enabled) {
      return;
    }
    write(tag, message);
    LOG.info("[{}] {}", tag, message);
  }

  /** At most one line per interval for the given key. */
  public static void throttled(String key, long minIntervalMs, String tag, String message) {
    if (throttleReady(key, minIntervalMs)) {
      write(tag, message);
    }
  }

  /**
   * Whether the interval for the key has elapsed. The key is marked only when the line will
   * actually be written, so expensive message formatting can be done after this check.
   */
  private static boolean throttleReady(String key, long minIntervalMs) {
    if (!enabled) {
      return false;
    }
    long now = monoMs();
    synchronized (LOCK) {
      Long last = THROTTLE.get(key);
      if (last != null && now - last.longValue() < minIntervalMs) {
        return false;
      }
      THROTTLE.put(key, Long.valueOf(now));
      return true;
    }
  }

  /** A line only on the first occurrence of the key. */
  public static void once(String key, String tag, String message) {
    if (!enabled) {
      return;
    }
    synchronized (LOCK) {
      if (!ONCE.add(key)) {
        return;
      }
    }
    log(tag, message);
  }

  /** Whether an optional integration or method was found via reflection. */
  public static void probe(String what, boolean present, String detail) {
    if (!enabled) {
      return;
    }
    once(
        "probe:" + what,
        "PROBE",
        (present ? "OK   " : "MISS ") + what + (isBlank(detail) ? "" : " (" + detail + ")"));
  }

  /** A state machine transition, e.g. BARITONE -> ALIGN. */
  public static void phase(String subsystem, String from, String to, String reason) {
    if (!enabled) {
      return;
    }
    log(
        "PHASE",
        subsystem + ": " + from + " -> " + to + (isBlank(reason) ? "" : " (" + reason + ")"));
  }

  /** Whether the class loads: this is how optional mods like Baritone are detected. */
  public static boolean classPresent(String className) {
    try {
      Class.forName(className, false, MirrorDebug.class.getClassLoader());
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  // ---------------------------------------------------------------- playback route

  /**
   * One route stabilization tick: where the player should be according to the recording, where they
   * actually are and how far apart.
   */
  public static void route(
      long run,
      int frame,
      double expectedX,
      double expectedZ,
      double actualX,
      double actualZ,
      double deviation,
      boolean onGround) {
    if (!enabled && !csvEnabled) {
      return;
    }
    if (csvEnabled) {
      csvRoute(run, frame, expectedX, expectedZ, actualX, actualZ, deviation, onGround);
    }
    // The line is built only when it will actually be written: the method is called every playback
    // tick, and with CSV on and the log off it need not be written at all.
    if (!enabled) {
      return;
    }
    boolean drift = deviation >= 0.5d;
    if (!throttleReady(drift ? "route-drift" : "route-ok", drift ? 1000L : 5000L)) {
      return;
    }
    if (drift) {
      write(
          "ROUTE",
          String.format(
              Locale.ROOT,
              "drift %.2f blocks at frame %d (run %d, %s) expected %.2f/%.2f actual %.2f/%.2f",
              Double.valueOf(deviation),
              Integer.valueOf(frame),
              Long.valueOf(run),
              onGround ? "ground" : "air",
              Double.valueOf(expectedX),
              Double.valueOf(expectedZ),
              Double.valueOf(actualX),
              Double.valueOf(actualZ)));
    } else {
      write(
          "ROUTE",
          String.format(
              Locale.ROOT,
              "tracking, deviation %.3f blocks at frame %d (run %d, %s)",
              Double.valueOf(deviation),
              Integer.valueOf(frame),
              Long.valueOf(run),
              onGround ? "ground" : "air"));
    }
  }

  private static void csvRoute(
      long run,
      int frame,
      double expectedX,
      double expectedZ,
      double actualX,
      double actualZ,
      double deviation,
      boolean onGround) {
    synchronized (LOCK) {
      csvEnsure(run);
      if (csvWriter == null || csvRows >= MAX_CSV_ROWS) {
        return;
      }
      csvWriter.println(
          String.format(
              Locale.ROOT,
              "%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%d",
              Long.valueOf(monoMs() - startedAtMono),
              Long.valueOf(run),
              Integer.valueOf(frame),
              Double.valueOf(expectedX),
              Double.valueOf(expectedZ),
              Double.valueOf(actualX),
              Double.valueOf(actualZ),
              Double.valueOf(deviation),
              Integer.valueOf(onGround ? 1 : 0)));
      csvRows++;
      // checkError flushes the buffer itself: a write error is visible here instead of being
      // silently lost.
      if (csvRows % 200 == 0 && csvWriter.checkError()) {
        csvFailed("write");
        return;
      }
      if (csvRows == MAX_CSV_ROWS) {
        log("CSV", "row limit reached, dump stopped");
      }
    }
  }

  private static void csvEnsure(long run) {
    if (csvWriter != null && csvRun == run) {
      return;
    }
    // This run already failed to open or write its CSV: without the mark the attempt would repeat
    // every tick.
    if (csvFailedRun == run) {
      return;
    }
    csvClose();
    if (logDir == null) {
      return;
    }
    File file = new File(logDir, CSV_PREFIX + fileStamp() + "-run" + run + CSV_SUFFIX);
    pruneCsv(file);
    try {
      csvWriter = new PrintWriter(new FileWriter(file, false));
      csvWriter.println(
          "elapsed_ms,run,frame,expected_x,expected_z,actual_x,actual_z,deviation,on_ground");
      csvRun = run;
      csvRows = 0;
      if (csvWriter.checkError()) {
        csvFailed("write");
        return;
      }
      log("CSV", "dumping playback run " + run + " to " + file.getName());
    } catch (IOException e) {
      csvWriter = null;
      csvFailedRun = run;
      LOG.warn("Mirror Recorder: cannot open CSV dump", e);
    }
  }

  /** Closes the current CSV: the run finished or was interrupted. */
  public static void csvClose() {
    synchronized (LOCK) {
      if (csvWriter == null) {
        csvRun = -1L;
        csvRows = 0;
        return;
      }
      boolean failed = csvWriter.checkError();
      csvWriter.close();
      csvWriter = null;
      int rows = csvRows;
      csvRun = -1L;
      csvRows = 0;
      if (failed) LOG.warn("Mirror Recorder: CSV dump could not be written completely");
      log("CSV", "dump closed, rows=" + rows + (failed ? " (write error)" : ""));
    }
  }

  /** CSV write error: close the file and stop writing this run; the next run will try again. */
  private static void csvFailed(String what) {
    long run = csvRun;
    csvWriter.close();
    csvWriter = null;
    csvRun = -1L;
    csvRows = 0;
    csvFailedRun = run;
    LOG.warn("Mirror Recorder: CSV dump " + what + " failed, dump of run " + run + " stopped");
  }

  /**
   * Tries to keep the MAX_CSV_FILES - 1 newest of our dumps before opening the new file; failed
   * deletions can leave more files than the limit.
   */
  private static void pruneCsv(File next) {
    File[] own =
        logDir.listFiles(
            new FilenameFilter() {
              public boolean accept(File d, String n) {
                return n.startsWith(CSV_PREFIX) && n.contains("-run") && n.endsWith(CSV_SUFFIX);
              }
            });
    if (own == null || own.length < MAX_CSV_FILES) return;
    Arrays.sort(
        own,
        new Comparator<File>() {
          public int compare(File a, File b) {
            int byTime = Long.compare(b.lastModified(), a.lastModified());
            return byTime != 0 ? byTime : b.getName().compareTo(a.getName());
          }
        });
    int kept = 0;
    for (File f : own) {
      if (!f.isFile() || f.equals(next)) continue;
      if (++kept < MAX_CSV_FILES) continue;
      if (!f.delete() && !pruneWarned) {
        pruneWarned = true;
        LOG.warn("Mirror Recorder: cannot delete old CSV dump " + f.getName());
      }
    }
  }

  // ---------------------------------------------------------------- internals

  private static void write(String tag, String message) {
    synchronized (LOCK) {
      open();
      if (writer == null) {
        return;
      }
      String line = stamp() + " [" + tag + "] " + message;
      writer.println(line);
      // checkError flushes the buffer and reports a write error (disk full, file taken away).
      if (writer.checkError()) {
        writeFailed();
        return;
      }
      // Checking the size only when opening the file would let one session grow the log far above
      // the limit. Character counts only estimate bytes in FileWriter's default charset
      // and platform line endings, so we periodically, and when the estimate exceeds the limit,
      // resync with the real file size.
      written += line.length() + 1L;
      if (++linesSinceResync >= RESYNC_LINES || written > MAX_LOG_BYTES) {
        linesSinceResync = 0;
        long real = logFile().length();
        if (real > 0L) {
          written = real;
        }
      }
      if (written > MAX_LOG_BYTES) {
        // Close the file: the next write will rotate or truncate it.
        close();
      }
    }
  }

  private static File logFile() {
    return new File(logDir, "mirror-debug.log");
  }

  private static void open() {
    if (writer != null || logDir == null || !enabled || openFailed) {
      return;
    }
    File file = logFile();
    // Rotation failed (the file is held by a viewer or antivirus): open with truncation. Otherwise
    // the size would stay above the limit and every line would close and reopen the file again.
    boolean append = !(file.isFile() && file.length() > MAX_LOG_BYTES && !rotate(file));
    try {
      writer = new PrintWriter(new FileWriter(file, append));
      writer.println();
      writer.println("=== Mirror Recorder debug session " + stamp() + " ===");
      if (writer.checkError()) {
        writeFailed();
        return;
      }
      // Exact size including the header: from here on it is only refined.
      written = Math.max(0L, file.length());
      linesSinceResync = 0;
    } catch (IOException e) {
      writer = null;
      openFailed = true;
      LOG.warn(
          "Mirror Recorder: cannot open debug log, diagnostics file disabled for this session", e);
    }
  }

  /** Rename to mirror-debug.log.1. False means rotation failed. */
  private static boolean rotate(File file) {
    File old = new File(logDir, "mirror-debug.log.1");
    if (old.isFile()) {
      old.delete();
    }
    try {
      Files.move(file.toPath(), old.toPath(), StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch (Exception atomicUnsupported) {
      try {
        Files.move(file.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
      } catch (Exception replaceFailed) {
        if (file.renameTo(old)) {
          return true;
        }
      }
    }
    if (!rotateWarned) {
      rotateWarned = true;
      LOG.warn("Mirror Recorder: cannot rotate debug log, it will be truncated instead");
    }
    return false;
  }

  private static void close() {
    if (writer == null) {
      return;
    }
    if (writer.checkError()) LOG.warn("Mirror Recorder: debug log could not be written completely");
    writer.close();
    writer = null;
  }

  /**
   * Writing to the log failed: the file is disabled until the end of the session with a single
   * warning, as on an open error. To retry, turn diagnostics off and on again.
   */
  private static void writeFailed() {
    writer.close();
    writer = null;
    openFailed = true;
    LOG.warn("Mirror Recorder: cannot write debug log, diagnostics file disabled for this session");
  }

  // One format instance for the whole log (SimpleDateFormat is not thread-safe, hence
  // synchronized).
  private static final SimpleDateFormat STAMP = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
  private static final Date STAMP_DATE = new Date();

  private static String stamp() {
    synchronized (STAMP) {
      STAMP_DATE.setTime(System.currentTimeMillis());
      return STAMP.format(STAMP_DATE);
    }
  }

  private static final SimpleDateFormat FILE_STAMP =
      new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

  private static String fileStamp() {
    synchronized (FILE_STAMP) {
      return FILE_STAMP.format(new Date());
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isEmpty();
  }
}

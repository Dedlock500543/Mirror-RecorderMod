package com.mirror.recorder.debug;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Optional diagnostics for Mirror Recorder. Disabled by default.
 *
 * Writes a detailed trace to <gameDir>/logs/mirror-debug.log and, when the CSV dump is
 * enabled, one row per playback tick to <gameDir>/logs/mirror-playback-*.csv so that a
 * recorded route can be compared with the route the player actually took.
 *
 * The class is intentionally self contained: it only depends on the JDK and Log4j.
 * Every method is a no-op while diagnostics are disabled, which is the default for
 * released builds. Enable it in the mod settings, in the config file, or with
 * -Dmirror.debug=true / -Dmirror.debug.csv=true on the JVM command line.
 */
public final class MirrorDebug {

    private MirrorDebug() {
    }

    private static final Logger LOG = LogManager.getLogger("MirrorRecorder/Debug");
    private static final Object LOCK = new Object();

    /** The trace file is rotated once it grows past this size. */
    private static final long MAX_LOG_BYTES = 8L * 1024L * 1024L;
    /** Hard cap so a forgotten loop cannot fill the disk. */
    private static final int MAX_CSV_ROWS = 200000;

    private static final Map<String, Long> THROTTLE = new HashMap<String, Long>();
    private static final Set<String> ONCE = new HashSet<String>();

    /** -Dmirror.debug=true / -Dmirror.debug.csv=true force diagnostics on, whatever the config file says. */
    private static final boolean FORCED = Boolean.getBoolean("mirror.debug");
    private static final boolean FORCED_CSV = Boolean.getBoolean("mirror.debug.csv");

    private static volatile boolean enabled = FORCED;
    private static volatile boolean csvEnabled = FORCED_CSV;

    private static File logDir;
    private static PrintWriter writer;
    private static PrintWriter csvWriter;
    private static long csvRun = -1L;
    private static int csvRows = 0;
    private static long startedAt = System.currentTimeMillis();

    // ---------------------------------------------------------------- lifecycle

    /** Called once from preInit with the Minecraft game directory. */
    public static void init(File gameDir) {
        synchronized (LOCK) {
            File dir = new File(gameDir, "logs");
            logDir = (dir.isDirectory() || dir.mkdirs()) ? dir : gameDir;
            startedAt = System.currentTimeMillis();
            THROTTLE.clear();
            ONCE.clear();
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
                open();
                log("DEBUG", "diagnostics enabled");
            } else {
                log("DEBUG", "diagnostics disabled");
                enabled = false;
                csvClose();
                close();
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setCsvEnabled(boolean value) {
        synchronized (LOCK) {
            csvEnabled = value || FORCED_CSV;
            if (!csvEnabled) {
                csvClose();
            }
        }
    }


    /** True when -Dmirror.debug=true pins diagnostics on; the settings toggle cannot turn them off. */
    public static boolean isForced() {
        return FORCED;
    }

    /** True when -Dmirror.debug.csv=true pins the CSV dump on. */
    public static boolean isCsvForced() {
        return FORCED_CSV;
    }

    // ---------------------------------------------------------------- logging

    /** Writes one line to the trace file and mirrors it into the normal game log. */
    public static void log(String tag, String message) {
        if (!enabled) {
            return;
        }
        write(tag, message);
        LOG.info("[{}] {}", tag, message);
    }

    /** Writes at most one line per interval for the given key. */
    public static void throttled(String key, long minIntervalMs, String tag, String message) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            Long last = THROTTLE.get(key);
            if (last != null && now - last.longValue() < minIntervalMs) {
                return;
            }
            THROTTLE.put(key, Long.valueOf(now));
        }
        write(tag, message);
    }

    /** Writes the line the first time this key is seen. */
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

    /** Records whether an optional integration or reflective handle resolved. */
    public static void probe(String what, boolean present, String detail) {
        once("probe:" + what, "PROBE",
                (present ? "OK   " : "MISS ") + what + (isBlank(detail) ? "" : " (" + detail + ")"));
    }

    /** Records a state machine transition, e.g. BARITONE -> ALIGN. */
    public static void phase(String subsystem, String from, String to, String reason) {
        log("PHASE", subsystem + ": " + from + " -> " + to + (isBlank(reason) ? "" : " (" + reason + ")"));
    }

    /** True when the class can be loaded, used to detect optional mods such as Baritone. */
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
     * One playback tick of the route stabilizer: where the recording expected the player to
     * be, where the player actually is, and how far apart the two are.
     */
    public static void route(long run, int frame, double expectedX, double expectedZ,
                             double actualX, double actualZ, double deviation, boolean onGround) {
        if (!enabled && !csvEnabled) {
            return;
        }
        if (csvEnabled) {
            csvRoute(run, frame, expectedX, expectedZ, actualX, actualZ, deviation, onGround);
        }
        if (deviation >= 0.5d) {
            throttled("route-drift", 1000L, "ROUTE", String.format(Locale.ROOT,
                    "drift %.2f blocks at frame %d (run %d, %s) expected %.2f/%.2f actual %.2f/%.2f",
                    Double.valueOf(deviation), Integer.valueOf(frame), Long.valueOf(run),
                    onGround ? "ground" : "air", Double.valueOf(expectedX), Double.valueOf(expectedZ),
                    Double.valueOf(actualX), Double.valueOf(actualZ)));
        } else {
            throttled("route-ok", 5000L, "ROUTE", String.format(Locale.ROOT,
                    "tracking, deviation %.3f blocks at frame %d (run %d, %s)",
                    Double.valueOf(deviation), Integer.valueOf(frame), Long.valueOf(run),
                    onGround ? "ground" : "air"));
        }
    }

    private static void csvRoute(long run, int frame, double expectedX, double expectedZ,
                                 double actualX, double actualZ, double deviation, boolean onGround) {
        synchronized (LOCK) {
            csvEnsure(run);
            if (csvWriter == null || csvRows >= MAX_CSV_ROWS) {
                return;
            }
            csvWriter.println(String.format(Locale.ROOT, "%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%d",
                    Long.valueOf(System.currentTimeMillis() - startedAt), Long.valueOf(run),
                    Integer.valueOf(frame), Double.valueOf(expectedX), Double.valueOf(expectedZ),
                    Double.valueOf(actualX), Double.valueOf(actualZ), Double.valueOf(deviation),
                    Integer.valueOf(onGround ? 1 : 0)));
            csvRows++;
            if (csvRows % 200 == 0) {
                csvWriter.flush();
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
        csvClose();
        if (logDir == null) {
            return;
        }
        File file = new File(logDir, "mirror-playback-" + fileStamp() + "-run" + run + ".csv");
        try {
            csvWriter = new PrintWriter(new FileWriter(file, false));
            csvWriter.println("elapsed_ms,run,frame,expected_x,expected_z,actual_x,actual_z,deviation,on_ground");
            csvRun = run;
            csvRows = 0;
            log("CSV", "dumping playback run " + run + " to " + file.getName());
        } catch (IOException e) {
            csvWriter = null;
            LOG.warn("Mirror Recorder: cannot open CSV dump", e);
        }
    }

    /** Closes the current CSV dump, called when a run ends or is aborted. */
    public static void csvClose() {
        synchronized (LOCK) {
            if (csvWriter == null) {
                csvRun = -1L;
                csvRows = 0;
                return;
            }
            csvWriter.flush();
            csvWriter.close();
            csvWriter = null;
            int rows = csvRows;
            csvRun = -1L;
            csvRows = 0;
            log("CSV", "dump closed, rows=" + rows);
        }
    }

    // ---------------------------------------------------------------- internals

    private static void write(String tag, String message) {
        synchronized (LOCK) {
            open();
            if (writer == null) {
                return;
            }
            writer.println(stamp() + " [" + tag + "] " + message);
            writer.flush();
        }
    }

    private static void open() {
        if (writer != null || logDir == null || !enabled) {
            return;
        }
        File file = new File(logDir, "mirror-debug.log");
        try {
            if (file.isFile() && file.length() > MAX_LOG_BYTES) {
                File old = new File(logDir, "mirror-debug.log.1");
                if (old.isFile()) {
                    old.delete();
                }
                try{java.nio.file.Files.move(file.toPath(),old.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }catch(Exception atomicUnsupported){
                    try{java.nio.file.Files.move(file.toPath(),old.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }catch(Exception replaceFailed){if(!file.renameTo(old))LOG.warn("Mirror Recorder: cannot rotate debug log");}}
            }
            writer = new PrintWriter(new FileWriter(file, true));
            writer.println();
            writer.println("=== Mirror Recorder debug session " + stamp() + " ===");
            writer.flush();
        } catch (IOException e) {
            writer = null;
            LOG.warn("Mirror Recorder: cannot open debug log", e);
        }
    }

    private static void close() {
        if (writer == null) {
            return;
        }
        writer.flush();
        writer.close();
        writer = null;
    }

    private static String stamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
    }

    private static String fileStamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}

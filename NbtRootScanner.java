package com.mirror.recorder.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Streaming parse of the NBT root without building a tree.
 *
 * <p>A slot summary and a trash entry need a few root fields and the frame count, while a full read
 * would build an object for every frame. The scanner walks the stream once and keeps only the
 * requested root keys: scalars, strings, list sizes and raw compound bytes (at most {@link
 * #MAX_KEPT_COMPOUND_BYTES}). Everything else is validated and skipped: the file is untrusted, so
 * memory is bounded by the requested fields, not by the whole root.
 *
 * <p>The scanner validates NBT with its own depth and list-type checks: the root must be a
 * compound, depth at most 512; a list of type End with a non-zero length, negative lengths, unknown
 * types and malformed modified UTF-8 (including in nested names and strings) are errors; a repeated
 * key overwrites the previous one. The getters mirror getInteger / getLong / getBoolean / getString
 * / getTagList. The class does not depend on Minecraft, so it can be tested on its own.
 */
final class NbtRootScanner {
  static final int END = 0,
      BYTE = 1,
      SHORT = 2,
      INT = 3,
      LONG = 4,
      FLOAT = 5,
      DOUBLE = 6,
      BYTE_ARRAY = 7,
      STRING = 8,
      LIST = 9,
      COMPOUND = 10,
      INT_ARRAY = 11,
      LONG_ARRAY = 12;
  private static final int MAX_DEPTH = 512;
  /** Limit of a kept compound (slot settings take about a kilobyte). Larger values are not kept. */
  static final int MAX_KEPT_COMPOUND_BYTES = 64 * 1024;
  /** Result of parsing the root. All values are a snapshot; nobody modifies them. */
  static final class Root {
    private final Map<String, Integer> types = new HashMap<String, Integer>();
    private final Map<String, Number> numbers = new HashMap<String, Number>();
    private final Map<String, String> strings = new HashMap<String, String>();
    private final Map<String, int[]> lists = new HashMap<String, int[]>();
    private final Map<String, byte[]> compounds = new HashMap<String, byte[]>();

    private void forget(String key) {
      types.remove(key);
      numbers.remove(key);
      strings.remove(key);
      lists.remove(key);
      compounds.remove(key);
    }

    boolean hasKey(String key) {
      return types.containsKey(key);
    }

    boolean hasKey(String key, int type) {
      Integer t = types.get(key);
      return t != null && t.intValue() == type;
    }

    private boolean numeric(String key) {
      Integer t = types.get(key);
      return t != null && t.intValue() >= BYTE && t.intValue() <= DOUBLE;
    }
    /** Like NBTTagCompound.getInteger: numeric tags only, float/double via floor. */
    int getInteger(String key) {
      if (!numeric(key)) return 0;
      Number n = numbers.get(key);
      int t = types.get(key).intValue();
      if (t == FLOAT) return floor(n.floatValue());
      if (t == DOUBLE) return floor(n.doubleValue());
      return t == LONG ? (int) n.longValue() : n.intValue();
    }
    /** Like NBTTagCompound.getLong. */
    long getLong(String key) {
      if (!numeric(key)) return 0L;
      Number n = numbers.get(key);
      int t = types.get(key).intValue();
      if (t == FLOAT) return (long) n.floatValue();
      if (t == DOUBLE) return (long) Math.floor(n.doubleValue());
      return n.longValue();
    }
    /** Like NBTTagCompound.getBoolean (getByte != 0). */
    boolean getBoolean(String key) {
      if (!numeric(key)) return false;
      Number n = numbers.get(key);
      int t = types.get(key).intValue();
      int v =
          t == FLOAT
              ? floor(n.floatValue())
              : t == DOUBLE ? floor(n.doubleValue()) : (int) n.longValue();
      return (byte) (v & 255) != 0;
    }
    /** Like NBTTagCompound.getString: string tag only. */
    String getString(String key) {
      if (!hasKey(key, STRING)) return "";
      String s = strings.get(key);
      return s == null ? "" : s;
    }
    /**
     * Like getTagList(key,type).tagCount(): a different element type in a non-empty list gives 0.
     */
    int listCount(String key, int elementType) {
      if (!hasKey(key, LIST)) return 0;
      int[] l = lists.get(key);
      if (l[1] > 0 && l[0] != elementType) return 0;
      return l[1];
    }
    /** Raw bytes of a root compound (without type and name), if it was requested to be kept. */
    byte[] compoundPayload(String key) {
      return hasKey(key, COMPOUND) ? compounds.get(key) : null;
    }
  }

  private NbtRootScanner() {}
  /**
   * Parses a decompressed NBT stream.
   *
   * @param maxBytes limit of the decompressed size: protection against a "bomb", like
   *     NBTSizeTracker.
   * @param keepKeys root keys whose values should be kept; the rest are only validated.
   */
  static Root scan(InputStream raw, long maxBytes, Set<String> keepKeys) throws IOException {
    Tap tap = new Tap(raw, maxBytes);
    DataInputStream in = new DataInputStream(tap);
    int rootType = in.readByte();
    if (rootType != COMPOUND) throw new IOException("Root tag must be a named compound tag");
    in.readUTF();
    Root root = new Root();
    while (true) {
      int type = in.readByte();
      if (type == END) break;
      String name = in.readUTF();
      root.forget(name);
      if (!keepKeys.contains(name)) {
        if (type == COMPOUND) skipCompound(in, 2);
        else skipPayload(in, type, 1);
        continue;
      }
      switch (type) {
        case BYTE:
          root.numbers.put(name, Byte.valueOf(in.readByte()));
          break;
        case SHORT:
          root.numbers.put(name, Short.valueOf(in.readShort()));
          break;
        case INT:
          root.numbers.put(name, Integer.valueOf(in.readInt()));
          break;
        case LONG:
          root.numbers.put(name, Long.valueOf(in.readLong()));
          break;
        case FLOAT:
          root.numbers.put(name, Float.valueOf(in.readFloat()));
          break;
        case DOUBLE:
          root.numbers.put(name, Double.valueOf(in.readDouble()));
          break;
        case STRING:
          root.strings.put(name, in.readUTF());
          break;
        case LIST:
          {
            int elem = in.readByte(), count = in.readInt();
            checkList(elem, count);
            for (int i = 0; i < count; i++) skipPayload(in, elem, 2);
            root.lists.put(name, new int[] {elem, count});
            break;
          }
        case COMPOUND:
          {
            tap.startCapture(MAX_KEPT_COMPOUND_BYTES);
            try {
              skipCompound(in, 2);
            } finally {
              byte[] payload = tap.stopCapture();
              if (payload != null) root.compounds.put(name, payload);
            }
            break;
          }
        default:
          skipPayload(in, type, 1);
      }
      root.types.put(name, Integer.valueOf(type));
    }
    return root;
  }

  private static void checkList(int elem, int count) throws IOException {
    if (count < 0) throw new IOException("Negative list size");
    if (elem == END && count > 0) throw new IOException("Missing type on ListTag");
    if (elem < END || elem > LONG_ARRAY) throw new IOException("Unknown tag type " + elem);
  }

  private static void skipCompound(DataInputStream in, int depth) throws IOException {
    if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
    while (true) {
      int type = in.readByte();
      if (type == END) return;
      skipUtf(in);
      skipPayload(in, type, depth + 1);
    }
  }

  private static void skipPayload(DataInputStream in, int type, int depth) throws IOException {
    if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
    switch (type) {
      case BYTE:
        skip(in, 1);
        return;
      case SHORT:
        skip(in, 2);
        return;
      case INT:
      case FLOAT:
        skip(in, 4);
        return;
      case LONG:
      case DOUBLE:
        skip(in, 8);
        return;
      case BYTE_ARRAY:
        skip(in, length(in, 1));
        return;
      case STRING:
        skipUtf(in);
        return;
      case LIST:
        {
          int elem = in.readByte(), count = in.readInt();
          checkList(elem, count);
          for (int i = 0; i < count; i++) skipPayload(in, elem, depth + 1);
          return;
        }
      case COMPOUND:
        skipCompound(in, depth);
        return;
      case INT_ARRAY:
        skip(in, length(in, 4));
        return;
      case LONG_ARRAY:
        skip(in, length(in, 8));
        return;
      default:
        throw new IOException("Unknown tag type " + type);
    }
  }

  private static long length(DataInputStream in, int unit) throws IOException {
    int n = in.readInt();
    if (n < 0) throw new IOException("Negative array size");
    return (long) n * unit;
  }

  /**
   * The string is read rather than skipped by length: this way its encoding is checked by the same
   * readUTF as in a full read.
   */
  private static void skipUtf(DataInputStream in) throws IOException {
    in.readUTF();
  }

  private static void skip(DataInputStream in, long n) throws IOException {
    while (n > 0L) {
      int step = (int) Math.min(n, Integer.MAX_VALUE);
      int done = in.skipBytes(step);
      if (done <= 0) {
        in.readByte();
        done = 1;
      }
      n -= done;
    }
  }

  private static int floor(float v) {
    int i = (int) v;
    return v < i ? i - 1 : i;
  }

  private static int floor(double v) {
    int i = (int) v;
    return v < i ? i - 1 : i;
  }
  /** Counts decompressed bytes read against maxBytes and copies them on request. */
  private static final class Tap extends FilterInputStream {
    private final long max;
    private long read = 0L;
    private ByteArrayOutputStream capture = null;
    private int captureLimit = 0;
    private boolean captureOverflow = false;
    private final byte[] skipBuf = new byte[8192];

    Tap(InputStream in, long max) {
      super(in);
      this.max = max;
    }

    void startCapture(int limit) {
      capture = new ByteArrayOutputStream();
      captureLimit = limit;
      captureOverflow = false;
    }
    /** The captured copy; null if the value turned out larger than the limit. */
    byte[] stopCapture() {
      byte[] out = captureOverflow || capture == null ? null : capture.toByteArray();
      capture = null;
      captureOverflow = false;
      return out;
    }

    private void captured(byte[] b, int off, int len) {
      if (capture == null) return;
      if (capture.size() + len > captureLimit) {
        capture = null;
        captureOverflow = true;
        return;
      }
      capture.write(b, off, len);
    }

    private void count(long n) throws IOException {
      read += n;
      if (read > max) throw new IOException("NBT data is too big: more than " + max + " bytes");
    }

    @Override
    public int read() throws IOException {
      int b = super.read();
      if (b < 0) throw new EOFException();
      count(1);
      captured(new byte[] {(byte) b}, 0, 1);
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = super.read(b, off, len);
      if (n > 0) {
        count(n);
        captured(b, off, n);
      }
      return n;
    }
    /**
     * Skipping goes through reading: otherwise neither the size can be counted nor a copy captured.
     */
    @Override
    public long skip(long n) throws IOException {
      long left = n;
      while (left > 0L) {
        int got = read(skipBuf, 0, (int) Math.min(skipBuf.length, left));
        if (got < 0) break;
        left -= got;
      }
      return n - left;
    }

    @Override
    public boolean markSupported() {
      return false;
    }
  }
}

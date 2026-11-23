package com.mirror.recorder.gui;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Packaged metrics shared by drawing, truncation, wrapping and editable-field hit testing. */
final class UiFontMetrics {
  static final int LINE_HEIGHT = 9;
  private static final byte[] WIDTHS = load();

  private UiFontMetrics() {}

  private static byte[] load() {
    try (InputStream stream =
            UiFontMetrics.class.getResourceAsStream("/assets/mirror_recorder/fonts/widths.bin");
        DataInputStream input = new DataInputStream(stream)) {
      if (input.readInt() != 0x4D524631) throw new IOException("Invalid UI font metrics");
      byte[] widths = new byte[65536];
      input.readFully(widths);
      return widths;
    } catch (IOException | NullPointerException ex) {
      throw new IllegalStateException("Missing or invalid Mirror Recorder font metrics", ex);
    }
  }

  static int width(char ch) {
    return WIDTHS[ch] & 255;
  }
}

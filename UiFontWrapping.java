package com.mirror.recorder.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** Word wrapping based on the same public measurement used to size the surrounding card. */
final class UiFontWrapping {
  private UiFontWrapping() {}

  static List<String> wrap(FontRenderer font, String text, int width) {
    List<String> lines = new ArrayList<String>();
    if (text == null) return lines;
    String style = "";
    for (String paragraph : text.split("\n", -1)) {
      if (paragraph.isEmpty()) {
        lines.add(style);
        continue;
      }
      int start = 0;
      while (start < paragraph.length()) {
        int end = start, space = -1, fit = start;
        while (end < paragraph.length()) {
          int next = end + (paragraph.charAt(end) == '§' && end + 1 < paragraph.length() ? 2 : 1);
          if (font.getStringWidth(style + paragraph.substring(start, next)) > width) break;
          if (paragraph.charAt(end) == ' ') space = end;
          fit = next;
          end = next;
        }
        int cut = fit;
        if (fit < paragraph.length() && space > start) cut = space;
        if (cut == start) {
          // Progress even when the viewport is narrower than a single glyph. Never recurse or
          // discard that glyph, and never split a formatting pair.
          cut = start + (paragraph.charAt(start) == '§' && start + 1 < paragraph.length() ? 2 : 1);
        }
        String line = style + paragraph.substring(start, cut);
        lines.add(line);
        style = activeStyle(line);
        start = cut;
        while (start < paragraph.length() && paragraph.charAt(start) == ' ') start++;
      }
    }
    return lines;
  }

  private static String activeStyle(String text) {
    StringBuilder style = new StringBuilder();
    for (int i = 0; i + 1 < text.length(); i++) {
      if (text.charAt(i) != '§') continue;
      char code = Character.toLowerCase(text.charAt(++i));
      if ("0123456789abcdefr".indexOf(code) >= 0) style.setLength(0);
      if ("0123456789abcdefklmnor".indexOf(code) >= 0) style.append('§').append(code);
    }
    return style.toString();
  }
}

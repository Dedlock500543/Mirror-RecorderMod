package com.mirror.recorder.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** Wrapped paragraphs with explicit semantic colors, rather than vanilla's very dark §8 notes. */
final class UiTextBlock {
  private UiTextBlock() {}

  static final class Line {
    final String text;
    final int color;

    Line(String text, int color) {
      this.text = text;
      this.color = color;
    }
  }

  static int inkHeight(FontRenderer font) {
    return font.FONT_HEIGHT + 2;
  }

  static int lineHeight(FontRenderer font) {
    return Math.max(12, font.FONT_HEIGHT + 3);
  }

  static int visibleLines(FontRenderer font, int height) {
    return Math.max(1, (height - inkHeight(font)) / lineHeight(font) + 1);
  }

  static List<Line> wrap(
      FontRenderer font, String[] paragraphs, int width, int firstColor, int bodyColor) {
    List<Line> result = new ArrayList<Line>();
    for (int i = 0; i < paragraphs.length; i++) {
      String text = paragraphs[i] == null ? "" : paragraphs[i].replace("\uFE0F", "");
      int color = i == 0 ? firstColor : bodyColor;
      // Only leading resource color codes are normalized. Inline emphasis and warning styles
      // remain.
      while (text.length() >= 2 && text.charAt(0) == '§') {
        char code = Character.toLowerCase(text.charAt(1));
        if (code == 'f') color = UiTheme.TEXT;
        else if (code == '7') color = UiTheme.TEXT_DIM;
        else if (code == '8') color = UiTheme.TEXT_MUTE;
        else if (code == 'c') color = UiTheme.RED;
        else break;
        text = text.substring(2);
      }
      if (text.isEmpty()) result.add(new Line("", color));
      else {
        List<String> wrapped = font.listFormattedStringToWidth(text, Math.max(1, width));
        for (String line : wrapped) result.add(new Line(line, color));
      }
    }
    return result;
  }
}

package com.mirror.recorder.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** Unabridged row text. Measurement and painting always use the same lines and font size. */
final class UiRowText {
  private UiRowText() {}

  static List<String> lines(FontRenderer font, String text, int width) {
    if (text == null || text.isEmpty()) return new ArrayList<String>();
    return font.listFormattedStringToWidth(text, Math.max(16, width - 3));
  }

  static int step(FontRenderer font) {
    return font.FONT_HEIGHT + 1;
  }

  static int height(FontRenderer font, String label, String note, int width) {
    return Math.max(1, lines(font, label, width).size() + lines(font, note, width).size())
            * step(font)
        - 1;
  }

  static void draw(
      FontRenderer font,
      String label,
      String note,
      int x,
      int y,
      int width,
      int height,
      int labelColor,
      int noteColor) {
    List<String> title = lines(font, label, width), body = lines(font, note, width);
    int lineStep = step(font);
    int yy = y + Math.max(0, (height - ((title.size() + body.size()) * lineStep - 1)) / 2);
    for (String line : title) {
      UiTheme.drawTextLine(font, line, x, yy, labelColor);
      yy += lineStep;
    }
    for (String line : body) {
      UiTheme.drawTextLine(font, line, x, yy, noteColor);
      yy += lineStep;
    }
  }
}

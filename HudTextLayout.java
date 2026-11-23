package com.mirror.recorder.gui;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** Text-driven HUD geometry, shared by every status and independently testable without OpenGL. */
public final class HudTextLayout {
  public static final int PAD = 16, MARGIN = 12, BASE_HEIGHT = 42, LINE_STEP = 15;
  public final int width, height, textWidth;
  public final List<String> titleLines, detailLines;

  public HudTextLayout(
      FontRenderer font,
      String title,
      String detail,
      int screenWidth,
      int screenHeight,
      int heldWidth) {
    int maxWidth = Math.max(40, screenWidth - MARGIN * 2);
    int measured = Math.max(font.getStringWidth(title), font.getStringWidth(detail));
    width = Math.min(maxWidth, heldWidth > 0 ? Math.max(40, heldWidth) : measured + PAD * 2 + 9);
    textWidth = Math.max(8, width - PAD * 2 - 6);
    titleLines = Collections.unmodifiableList(UiRowText.lines(font, title, textWidth));
    detailLines = Collections.unmodifiableList(UiRowText.lines(font, detail, textWidth));
    height =
        BASE_HEIGHT
            + (Math.max(1, titleLines.size()) + Math.max(1, detailLines.size()) - 2) * LINE_STEP;
  }
}

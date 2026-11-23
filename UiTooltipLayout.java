package com.mirror.recorder.gui;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** One measured rectangle for tooltip background, padding, wrapping and screen-edge placement. */
final class UiTooltipLayout {
  static final int PAD = 8, MARGIN = 6, INK = 2;
  final int x, y, width, height, textWidth, lineHeight, visibleLines, maxScroll, footer;
  final List<UiTextBlock.Line> lines;

  UiTooltipLayout(
      FontRenderer font,
      String[] paragraphs,
      int mouseX,
      int mouseY,
      int screenWidth,
      int screenHeight) {
    int wrapWidth = Math.max(1, Math.min(280, screenWidth - 2 * (MARGIN + PAD) - INK));
    lineHeight = UiTextBlock.lineHeight(font);
    List<UiTextBlock.Line> wrapped =
        UiTextBlock.wrap(font, paragraphs, wrapWidth, UiTheme.TEXT, UiTheme.TEXT_DIM);
    int maxLines = UiTextBlock.visibleLines(font, screenHeight - 2 * (MARGIN + PAD));
    // Keep the entire document. A viewport is not permission to discard its remaining lines.
    footer = wrapped.size() > maxLines ? lineHeight : 0;
    visibleLines = Math.min(wrapped.size(), Math.max(1, maxLines - (footer > 0 ? 1 : 0)));
    maxScroll = Math.max(0, wrapped.size() - visibleLines);
    int measured = 1;
    for (UiTextBlock.Line line : wrapped)
      measured = Math.max(measured, font.getStringWidth(line.text));
    if (maxScroll > 0)
      measured =
          Math.max(
              measured,
              Math.min(
                  wrapWidth,
                  font.getStringWidth(
                      "↑ ↓  " + wrapped.size() + "–" + wrapped.size() + " / " + wrapped.size())));
    textWidth = measured;
    width = measured + 2 * PAD + INK;
    height = 2 * PAD + font.FONT_HEIGHT + INK + Math.max(0, visibleLines - 1) * lineHeight + footer;
    int left = mouseX + 12, top = mouseY + 10;
    if (left + width > screenWidth - MARGIN) left = mouseX - 8 - width;
    if (top + height > screenHeight - MARGIN) top = mouseY - 8 - height;
    x = Math.max(MARGIN, Math.min(screenWidth - MARGIN - width, left));
    y = Math.max(MARGIN, Math.min(screenHeight - MARGIN - height, top));
    lines = Collections.unmodifiableList(wrapped);
  }
}

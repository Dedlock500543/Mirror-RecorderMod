package com.mirror.recorder.gui;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

/** One measured layout for the live replay card: painting never invents another text width. */
public final class ReplayHudLayout {
  public static final float TEXT_SCALE = 1.25f;
  public static final int PAD = 12, MARGIN = 12, LINE = 14, DEFAULT_WIDTH = 280;
  public final int width, height, titleWidth, textWidth;
  public final int contextY, metaY, footerY, trackY;
  public final boolean splitFooter;
  public final List<String> titles, contexts, metadata, leftFooter, rightFooter;

  public ReplayHudLayout(
      FontRenderer font, ReplayHudRenderer.Data data, int screenWidth, int heldWidth) {
    int available = Math.max(80, screenWidth - MARGIN * 2);
    width = Math.min(available, heldWidth > 0 ? Math.max(80, heldWidth) : DEFAULT_WIDTH);
    textWidth = Math.max(16, width - PAD * 2);
    titleWidth = Math.max(16, textWidth - 30 - scaledTextWidth(font, data.metric) - 10);
    titles = lines(font, data.title, titleWidth);
    contexts = lines(font, data.context, textWidth);
    metadata = lines(font, data.meta, textWidth);
    contextY = PAD + Math.max(18, titles.size() * LINE) + 4;
    metaY = contextY + contexts.size() * LINE + (contexts.isEmpty() ? 0 : 3);
    footerY = metaY + metadata.size() * LINE + (metadata.isEmpty() ? 0 : 5);
    splitFooter = scaledTextWidth(font, data.left) + scaledTextWidth(font, data.right) + 12 > textWidth;
    leftFooter =
        lines(
            font,
            data.left,
            splitFooter ? textWidth : textWidth - scaledTextWidth(font, data.right) - 12);
    rightFooter =
        lines(
            font,
            data.right,
            splitFooter ? textWidth : textWidth - scaledTextWidth(font, data.left) - 12);
    int footerLines =
        splitFooter
            ? leftFooter.size() + rightFooter.size()
            : Math.max(leftFooter.size(), rightFooter.size());
    trackY = footerY + footerLines * LINE + 7;
    height = trackY + 12;
  }

  private static List<String> lines(FontRenderer font, String text, int width) {
    int unscaledWidth = Math.max(16, (int) Math.floor(width / TEXT_SCALE));
    return Collections.unmodifiableList(UiRowText.lines(font, text, unscaledWidth));
  }

  static int scaledTextWidth(FontRenderer font, String text) {
    return (int) Math.ceil(font.getStringWidth(text) * TEXT_SCALE);
  }
}

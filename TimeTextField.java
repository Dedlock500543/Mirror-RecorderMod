package com.mirror.recorder.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;

/** A fixed left edge and a cursor-following viewport; typing never recenters the value. */
final class TimeTextField extends GuiTextField {
  private final FontRenderer font;
  private int scrollX, cursorTicks;

  TimeTextField(int id, FontRenderer font, int x, int y, int width, int height) {
    super(id, font, x, y, width, height);
    this.font = font;
    setEnableBackgroundDrawing(false);
    setTextColor(UiTheme.TEXT);
  }

  static int scrollFor(int previous, int caret, int textWidth, int viewWidth) {
    int room = Math.max(1, viewWidth - 1);
    int offset = Math.max(0, Math.min(previous, Math.max(0, textWidth - room)));
    if (caret < offset) return Math.max(0, caret);
    return caret > offset + room ? Math.max(0, caret - room) : offset;
  }

  @Override
  public void updateCursorCounter() {
    super.updateCursorCounter();
    cursorTicks++;
  }

  @Override
  public void setFocused(boolean focused) {
    if (focused && !isFocused()) cursorTicks = 0;
    super.setFocused(focused);
  }

  @Override
  public void drawTextBox() {
    if (!getVisible()) return;
    String text = getText();
    int caret = font.getStringWidth(text.substring(0, getCursorPosition()));
    scrollX = scrollFor(scrollX, caret, font.getStringWidth(text), width);
    int textY = y + UiMotion.textOffset(height, font.FONT_HEIGHT);
    UiTheme.beginClip(Minecraft.getMinecraft(), x, y, x + width, y + height);
    try {
      drawSelection(text, textY);
      font.drawString(text, x - scrollX, textY, UiTheme.TEXT, false);
      if (isFocused() && cursorTicks / 6 % 2 == 0)
        Gui.drawRect(
            x + caret - scrollX,
            textY,
            x + caret - scrollX + 1,
            textY + font.FONT_HEIGHT,
            UiTheme.TEXT);
    } finally {
      UiTheme.endClip();
    }
    Gui.drawRect(x, y + height - 1, x + width, y + height, UiTheme.ACCENT);
  }

  private void drawSelection(String text, int textY) {
    int a = getCursorPosition(), b = getSelectionEnd();
    if (a == b) return;
    int left = x + font.getStringWidth(text.substring(0, Math.min(a, b))) - scrollX;
    int right = x + font.getStringWidth(text.substring(0, Math.max(a, b))) - scrollX;
    Gui.drawRect(left, textY - 1, right, textY + font.FONT_HEIGHT + 1, 0x705E9FE8);
  }

  @Override
  public boolean mouseClicked(int mx, int my, int button) {
    boolean inside = mx >= x && mx < x + width && my >= y && my < y + height;
    setFocused(inside);
    if (!inside || button != 0) return false;
    String text = getText();
    int target = mx - x + scrollX, cursor = 0, advance = 0;
    while (cursor < text.length()) {
      int next = Math.max(0, font.getCharWidth(text.charAt(cursor)));
      if (target < advance + (next + 1) / 2) break;
      advance += next;
      cursor++;
    }
    setCursorPosition(cursor);
    cursorTicks = 0;
    return true;
  }
}

package com.mirror.recorder.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Borderless, vertically centered editor with a restrained focus rail inside its click bounds. */
public class CenteredTextField extends GuiTextField {
  private final int fontHeight;
  private boolean focusRail = true;
  private final UiMotion.Channel focusMotion = new UiMotion.Channel(0f);

  public CenteredTextField(int id, FontRenderer fr, int x, int y, int w, int h) {
    super(id, fr, x, y, w, h);
    fontHeight = fr.FONT_HEIGHT;
    setEnableBackgroundDrawing(false);
    setTextColor(UiTheme.TEXT);
    setDisabledTextColour(UiTheme.TEXT_MUTE);
  }

  /** A framed parent, such as settings search, already owns its focus border. */
  public void setFocusRail(boolean enabled) {
    focusRail = enabled;
  }

  @Override
  public void drawTextBox() {
    if (!getVisible()) return;
    int realY = y;
    float focus =
        focusMotion.update(
            isFocused() ? 1f : 0f, UiTheme.nowMs(), UiTheme.HOVER_MS, UiTheme.animationsEnabled());
    // Screens already own the field's outer inset; do not introduce a second frame or padding.
    if (focusRail && width > 0 && height > fontHeight + 2)
      UiTheme.vgrad(
          x,
          realY + height - 1,
          x + width,
          realY + height,
          UiTheme.lerp(UiTheme.BORDER, UiTheme.ACCENT, focus),
          UiTheme.lerp(UiTheme.BORDER, UiTheme.ACCENT, focus));
    try {
      y = realY + UiMotion.textOffset(height, fontHeight);
      super.drawTextBox();
    } finally {
      // Vanilla input continues to use the original rectangle, including after a renderer error.
      y = realY;
    }
  }
}

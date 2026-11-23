package com.mirror.recorder.gui;

import net.minecraft.client.gui.FontRenderer;

/** Center a folder name while retaining vanilla cursor movement, selection and scrolling. */
final class FolderNameField extends CenteredTextField {
  private final FontRenderer font;

  FolderNameField(int id, FontRenderer font, int x, int y, int width, int height) {
    super(id, font, x, y, width, height);
    this.font = font;
  }

  static int textOffset(int width, int textWidth) {
    // Leave room for the cursor. Long names use vanilla's horizontal scrolling.
    return Math.max(0, (width - Math.max(0, textWidth) - 2) / 2);
  }

  @Override
  public void drawTextBox() {
    int oldX = x, oldWidth = width;
    int offset = textOffset(width, font.getStringWidth(getText()));
    try {
      x += offset;
      width -= offset;
      super.drawTextBox();
    } finally {
      x = oldX;
      width = oldWidth;
    }
  }

  @Override
  public boolean mouseClicked(int mouseX, int mouseY, int button) {
    if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
      int offset = textOffset(width, font.getStringWidth(getText()));
      mouseX = Math.max(x, mouseX - offset);
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }
}

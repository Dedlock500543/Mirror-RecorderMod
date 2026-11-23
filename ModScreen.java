package com.mirror.recorder.gui;

import net.minecraft.client.gui.GuiScreen;

/** Marker for screens whose text uses the mod font, without changing Minecraft's global font. */
public abstract class ModScreen extends GuiScreen {
  final void installUiFont() {
    fontRenderer = UiFonts.get(mc);
  }
}

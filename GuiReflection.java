package com.mirror.recorder.handler;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Protected GuiScreen methods (click, release, key, drag) found via reflection once per session.
 * The method getters do not call resolve() implicitly: the places that log MirrorDebug.probe on the
 * first lookup do it themselves.
 */
@SideOnly(Side.CLIENT)
final class GuiReflection {
  private final ReplayState state;
  private boolean resolved = false;
  private Method mouseClicked, mouseReleased, keyTyped, mouseClickMove;

  GuiReflection(ReplayState state) {
    this.state = state;
  }

  boolean isResolved() {
    return resolved;
  }

  void ensureResolved() {
    if (!resolved) resolve();
  }

  Method mouseClicked() {
    return mouseClicked;
  }

  Method mouseReleased() {
    return mouseReleased;
  }

  Method keyTyped() {
    return keyTyped;
  }

  Method mouseClickMove() {
    return mouseClickMove;
  }

  void resolve() {
    resolve(GuiScreen.class);
  }

  // The same exact-name lookup is exercised against dev and SRG callback declarations.
  void resolve(Class<?> screenClass) {
    resolved = true;
    mouseClicked = null;
    mouseReleased = null;
    keyTyped = null;
    mouseClickMove = null;
    for (Method m : screenClass.getDeclaredMethods()) {
      String n = m.getName();
      Class<?>[] a = m.getParameterTypes();
      if (a.length != 3 || a[0] != int.class || a[1] != int.class || a[2] != int.class) continue;
      if (n.equals("mouseClicked") || n.equals("func_73864_a")) mouseClicked = m;
      else if (n.equals("mouseReleased") || n.equals("func_146286_b")) mouseReleased = m;
    }
    for (Method m : screenClass.getDeclaredMethods()) {
      String n = m.getName();
      Class<?>[] a = m.getParameterTypes();
      if (a.length == 2
          && a[0] == char.class
          && a[1] == int.class
          && (n.equals("keyTyped") || n.equals("func_73869_a"))) keyTyped = m;
    }
    for (Method m : screenClass.getDeclaredMethods()) {
      String n = m.getName();
      Class<?>[] a = m.getParameterTypes();
      if (a.length == 4
          && a[0] == int.class
          && a[1] == int.class
          && a[2] == int.class
          && a[3] == long.class) {
        if (n.equals("mouseClickMove") || n.equals("func_146273_a")) mouseClickMove = m;
      }
    }
    try {
      if (mouseClicked != null) mouseClicked.setAccessible(true);
      if (mouseReleased != null) mouseReleased.setAccessible(true);
      if (keyTyped != null) keyTyped.setAccessible(true);
      if (mouseClickMove != null) mouseClickMove.setAccessible(true);
    } catch (Exception e) {
      mouseClicked = null;
      mouseReleased = null;
      keyTyped = null;
      mouseClickMove = null;
      state.warnClickFailure("Cannot access GuiScreen click methods", e);
    }
  }

  private static final int VANILLA_LINK_CONFIRM_ID = 31102009;

  @SuppressWarnings("unchecked")
  void deliverSemanticLink(GuiScreen screen, String action, String value) {
    Minecraft mc = Minecraft.getMinecraft();
    try {
      URI uri;
      if (action.equals("OPEN_FILE")) uri = new File(value).toURI();
      else {
        if (!mc.gameSettings.chatLinks) return;
        uri = new URI(value);
        Set<String> protocols =
            (Set<String>) TextEditors.member(screen, "PROTOCOLS", "field_175284_f").get(null);
        if (uri.getScheme() == null
            || !protocols.contains(uri.getScheme().toLowerCase(Locale.ROOT)))
          throw new URISyntaxException(value, "Unsupported protocol");
        if (mc.gameSettings.chatLinksPrompt) {
          // Keep vanilla's callback and stored URI, without consulting physical Shift.
          TextEditors.member(screen, "clickedLinkURI", "field_175286_t").set(screen, uri);
          mc.displayGuiScreen(
              new GuiConfirmOpenLink(screen, value, VANILLA_LINK_CONFIRM_ID, false));
          return;
        }
      }
      TextEditors.method(
              GuiScreen.class, new Class<?>[] {URI.class}, "openWebLink", "func_175282_a")
          .invoke(screen, uri);
    } catch (ReflectiveOperationException | URISyntaxException e) {
      state.warnClickFailure("Recorded link replay failed", e);
    }
  }
}

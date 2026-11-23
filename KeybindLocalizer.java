package com.mirror.recorder.handler;

import com.mirror.recorder.MirrorRecorder;
import com.mirror.recorder.gui.Lang;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Locale;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Localization of the mod's key names in "Controls".
 *
 * <p>The keyDescription field must not contain localized text: vanilla stores the binding in
 * options.txt as the string key_&lt;keyDescription&gt; and reads it back by that at startup. A
 * localized text substituted there would make the next launch miss its line and reset the mod's
 * keys. So keyDescription is restored to the translation key when localization is refreshed (so
 * future saves use the stable description), and the translation goes into the I18n localization
 * table under the same key: "Controls" draws text through it, and the game settings file stays
 * untouched.
 */
@SideOnly(Side.CLIENT)
public final class KeybindLocalizer {
  private static final String[] KEYS = {
    "key.mirror_recorder.record",
    "key.mirror_recorder.play",
    "key.mirror_recorder.loop",
    "key.mirror_recorder.stop",
    "key.mirror_recorder.gui"
  };
  private static String lastLang = null, lastMode = null;
  private static boolean fieldsResolved = false;
  private static Field localeField = null, mapField = null;

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc == null || mc.gameSettings == null) return;
    MirrorRecorder mod = MirrorRecorder.getInstance();
    if (mod == null || mod.getKeyRecord() == null) return;
    String lang = mc.gameSettings.language == null ? "" : mc.gameSettings.language,
        mode = Lang.get();
    Map<String, String> table = localeTable();
    boolean fresh = !lang.equals(lastLang) || !mode.equals(lastMode);
    // Resource reloads replace translation values, so one sentinel entry is checked every tick
    // with a single map lookup to detect when the mod translations need to be reapplied.
    boolean missing = table != null && !kbName(0).equals(table.get(KEYS[0]));
    if (!fresh && !missing) return;
    lastLang = lang;
    lastMode = mode;
    for (int i = 0; i < KEYS.length; i++) {
      restoreDescription(binding(mod, i), KEYS[i]);
      if (table != null)
        try {
          table.put(KEYS[i], kbName(i));
        } catch (Throwable ignored) {
        }
    }
  }

  private static KeyBinding binding(MirrorRecorder mod, int kind) {
    switch (kind) {
      case 0:
        return mod.getKeyRecord();
      case 1:
        return mod.getKeyPlay();
      case 2:
        return mod.getKeyLoop();
      case 3:
        return mod.getKeyStop();
      default:
        return mod.getKeyGui();
    }
  }
  /** Literal key names for the effective UI language. */
  private static String kbName(int kind) {
    String r = Lang.resolved();
    if (Lang.RU.equals(r))
      return new String[] {"Запись", "Воспроизведение", "Зациклить", "Стоп", "Открыть интерфейс"}
          [kind];
    if (Lang.UK.equals(r))
      return new String[] {"Запис", "Відтворення", "Зациклити", "Стоп", "Відкрити інтерфейс"}[kind];
    if (Lang.DE.equals(r))
      return new String[] {"Aufnehmen", "Wiedergabe", "Schleife", "Stopp", "Oberfläche öffnen"}
          [kind];
    if (Lang.PL.equals(r))
      return new String[] {"Nagraj", "Odtwórz", "Zapętl", "Stop", "Otwórz interfejs"}[kind];
    return new String[] {"Record", "Playback", "Loop", "Stop", "Open Interface"}[kind];
  }
  /** Restores keyDescription to the translation key: vanilla stores the key binding by it. */
  private static void restoreDescription(KeyBinding kb, String key) {
    if (kb == null) return;
    String current = kb.getKeyDescription();
    if (current == null || current.equals(key)) return;
    try {
      for (Field f : KeyBinding.class.getDeclaredFields()) {
        if (f.getType() != String.class || Modifier.isStatic(f.getModifiers())) continue;
        f.setAccessible(true);
        if (current.equals(f.get(kb))) {
          f.set(kb, key);
          return;
        }
      }
    } catch (Throwable ignored) {
    }
  }
  /**
   * Translation table of the active locale: fields are looked up by type so it also works in an
   * obfuscated build.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, String> localeTable() {
    try {
      if (!fieldsResolved) {
        fieldsResolved = true;
        for (Field f : I18n.class.getDeclaredFields()) {
          if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Locale.class) continue;
          f.setAccessible(true);
          localeField = f;
          break;
        }
        if (localeField != null)
          for (Field g : Locale.class.getDeclaredFields()) {
            if (Modifier.isStatic(g.getModifiers()) || !Map.class.isAssignableFrom(g.getType()))
              continue;
            g.setAccessible(true);
            mapField = g;
            break;
          }
      }
      if (localeField == null || mapField == null) return null;
      Object locale = localeField.get(null);
      if (locale == null) return null;
      Object map = mapField.get(locale);
      return map instanceof Map ? (Map<String, String>) map : null;
    } catch (Throwable ignored) {
      return null;
    }
  }
}

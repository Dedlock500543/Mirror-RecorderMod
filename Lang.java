package com.mirror.recorder.gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * UI localization. Five languages: ru / en / uk / de / pl plus AUTO mode (the default). AUTO
 * detects the actual Minecraft client language: it first asks LanguageManager (which the game
 * really uses to draw text), falling back to gameSettings.language; unsupported languages get
 * English. The check runs on every call, so a game language change is picked up immediately,
 * without a restart. Missing key -> English -> Russian -> the key itself: the UI does not crash.
 */
public final class Lang {
  private Lang() {}

  public static final String RU = "ru", EN = "en", UK = "uk", DE = "de", PL = "pl", AUTO = "auto";
  private static final String[] SUPPORTED = {RU, EN, UK, DE, PL};
  private static volatile String current = AUTO;
  private static final Map<String, String> R = new HashMap<String, String>(),
      E = new HashMap<String, String>(),
      U = new HashMap<String, String>(),
      D = new HashMap<String, String>(),
      P = new HashMap<String, String>();

  public static String get() {
    return current;
  }

  public static String[] supported() {
    return SUPPORTED.clone();
  }
  /** Effective UI language: the explicit choice, or the game language in AUTO. */
  public static String resolved() {
    return AUTO.equals(current) ? detectGameLanguage() : current;
  }

  public static boolean isRu() {
    return RU.equals(resolved());
  }
  // The game language code is compared with the string parsed last time: the check itself still
  // runs on every call, but mapCode with toLowerCase runs only when the code changes. The code
  // and its parse live in one object: two separate fields could diverge if Lang were called off the
  // client thread, and then one call would get someone else's language.
  private static final class Mapped {
    final String code, lang;

    Mapped(String code, String lang) {
      this.code = code;
      this.lang = lang;
    }
  }

  private static volatile Mapped mapped = null;
  /**
   * Minecraft client language: LanguageManager first (what the game draws text with),
   * gameSettings.language as a fallback.
   */
  private static String detectGameLanguage() {
    String code = null;
    try {
      Minecraft mc = Minecraft.getMinecraft();
      if (mc != null) {
        if (mc.getLanguageManager() != null && mc.getLanguageManager().getCurrentLanguage() != null)
          code = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
        if ((code == null || code.isEmpty()) && mc.gameSettings != null)
          code = mc.gameSettings.language;
      }
    } catch (Throwable ignored) {
    }
    String key = code == null ? "" : code;
    Mapped m = mapped;
    if (m != null && key.equals(m.code)) return m.lang;
    String lang = mapCode(code);
    mapped = new Mapped(key, lang);
    return lang;
  }

  private static String mapCode(String code) {
    if (code == null) return EN;
    String c = code.toLowerCase(Locale.ROOT);
    if (c.startsWith("ru")) return RU;
    if (c.startsWith("uk")) return UK;
    if (c.startsWith("de")) return DE;
    if (c.startsWith("pl")) return PL;
    return EN;
  }

  public static void set(String code) {
    if (code != null) {
      for (String s : SUPPORTED)
        if (s.equalsIgnoreCase(code)) {
          current = s;
          return;
        }
    }
    current = AUTO;
  }
  /** "Language" button cycle: AUTO -> ru -> en -> uk -> de -> pl -> AUTO. */
  public static String next() {
    if (AUTO.equals(current)) return RU;
    for (int i = 0; i < SUPPORTED.length; i++)
      if (SUPPORTED[i].equals(current)) return i + 1 < SUPPORTED.length ? SUPPORTED[i + 1] : AUTO;
    return AUTO;
  }

  private static String nativeName(String code) {
    if (RU.equals(code)) return "Русский";
    if (EN.equals(code)) return "English";
    if (UK.equals(code)) return "Українська";
    if (DE.equals(code)) return "Deutsch";
    if (PL.equals(code)) return "Polski";
    return code;
  }

  private static String autoWord(String code) {
    return RU.equals(code) || UK.equals(code) ? "Авто" : "Auto";
  }

  public static String name() {
    if (AUTO.equals(current)) {
      String r = resolved();
      return autoWord(r) + " (" + nativeName(r) + ")";
    }
    return nativeName(current);
  }

  public static String t(String key) {
    return localized(resolved(), key);
  }

  static String localized(String code, String key) {
    Map<String, String> m =
        RU.equals(code) ? R : UK.equals(code) ? U : DE.equals(code) ? D : PL.equals(code) ? P : E;
    String v = m.get(key);
    if (v == null) v = E.get(key);
    if (v == null) v = R.get(key);
    return v == null ? key : v;
  }

  static final int PLURAL_ONE = 0, PLURAL_FEW = 1, PLURAL_MANY = 2;
  private static final String[] PLURAL_SUFFIX = {"_one", "_few", "_many"};
  /**
   * Plural form for a count. ru/uk: one for 1, 21, 31… (except 11), few for 2–4, 22–24… (except
   * 12–14), many otherwise. pl: one only for 1 (21 is "kopii", not "kopia"), few as in ru. en/de:
   * one for 1, many otherwise (their texts keep _few equal to _many).
   */
  static int pluralForm(int count) {
    int n = Math.abs(count), mod10 = n % 10, mod100 = n % 100;
    String r = resolved();
    boolean slavic = RU.equals(r) || UK.equals(r), polish = PL.equals(r);
    if (!slavic && !polish) return n == 1 ? PLURAL_ONE : PLURAL_MANY;
    if (polish ? n == 1 : mod10 == 1 && mod100 != 11) return PLURAL_ONE;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return PLURAL_FEW;
    return PLURAL_MANY;
  }
  /**
   * Count with the right word form: the text is taken from key_one / key_few / key_many (all three
   * exist in every language), {0} is the count.
   */
  public static String plural(String key, int count) {
    return t(key + PLURAL_SUFFIX[pluralForm(count)], count);
  }

  /**
   * Template with parameters: {0}, {1}... are replaced with String.valueOf(argument), as with
   * string concatenation.
   */
  public static String t(String key, Object... args) {
    return fill(t(key), args);
  }

  static String fill(String template, Object... args) {
    String out = template;
    for (int i = 0; i < args.length; i++) out = out.replace("{" + i + "}", String.valueOf(args[i]));
    return out;
  }

  /** Tooltip with parameters: see t(key, args). */
  public static String[] tip(String key, Object... args) {
    return t(key, args).split("\n");
  }
  /** Tooltip: lines are stored separated by \n in one value. */
  public static String[] tip(String key) {
    String v = t(key);
    return v.split("\n");
  }
  /**
   * Picks one of five per-language values. Only for non-string data (the slot card labels in
   * UiTheme); texts belong in the lang files.
   */
  public static <T> T pick(T ru, T en, T uk, T de, T pl) {
    String r = resolved();
    return RU.equals(r) ? ru : UK.equals(r) ? uk : DE.equals(r) ? de : PL.equals(r) ? pl : en;
  }
  /** Decimal separator: a dot only in English, a comma in ru/uk/de/pl. */
  public static String dec(String s) {
    return EN.equals(resolved()) ? s : s.replace('.', ',');
  }

  /**
   * Texts live in {@code assets/mirror_recorder/texts/<lang>.properties} (UTF-8): code refers to
   * them by key.
   */
  static final String TEXTS_PATH = "/assets/mirror_recorder/texts/";

  static {
    load(RU, R);
    load(EN, E);
    load(UK, U);
    load(DE, D);
    load(PL, P);
  }

  /**
   * No file means an empty dictionary: t() returns English, Russian or the key itself; the UI does
   * not crash.
   */
  private static void load(String code, Map<String, String> into) {
    InputStream in = Lang.class.getResourceAsStream(TEXTS_PATH + code + ".properties");
    if (in == null) return;
    try {
      Properties props = new Properties();
      props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      for (String key : props.stringPropertyNames()) into.put(key, props.getProperty(key));
    } catch (IOException ignored) {
    } finally {
      try {
        in.close();
      } catch (IOException ignored) {
      }
    }
  }
}

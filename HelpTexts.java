package com.mirror.recorder.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Help texts: {@code assets/mirror_recorder/texts/help_<lang>.txt}. A line "=== Title" starts a
 * section, the following lines are its text as is (blank lines are spacing). Sections are in the
 * same order in every language.
 */
final class HelpTexts {
  private HelpTexts() {}

  private static final String SECTION = "=== ";
  private static final Map<String, String[][]> CACHE = new HashMap<String, String[][]>();

  /** Sections for the UI language: [i][0] is the title, then the text lines. */
  static synchronized String[][] current() {
    String code = Lang.resolved();
    String[][] d = CACHE.get(code);
    if (d == null) {
      d = load(code);
      if (d.length == 0 && !Lang.EN.equals(code)) d = current(Lang.EN);
      CACHE.put(code, d);
    }
    return d;
  }

  private static String[][] current(String code) {
    String[][] d = CACHE.get(code);
    if (d == null) {
      d = load(code);
      CACHE.put(code, d);
    }
    return d;
  }

  /** No file means empty help. */
  static String[][] load(String code) {
    InputStream in = HelpTexts.class.getResourceAsStream(Lang.TEXTS_PATH + "help_" + code + ".txt");
    if (in == null) return new String[0][];
    List<String[]> sections = new ArrayList<String[]>();
    List<String> cur = null;
    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        if (line.startsWith(SECTION)) {
          if (cur != null) sections.add(cur.toArray(new String[0]));
          cur = new ArrayList<String>();
          cur.add(line.substring(SECTION.length()));
        } else if (cur != null) cur.add(line);
      }
    } catch (IOException ignored) {
    }
    if (cur != null) sections.add(cur.toArray(new String[0]));
    // The resource chapter order is stable in all languages; keep the interface chapter in place.
    if (sections.size() >= 10) {
      int index = 8;
      String[] original = sections.get(index);
      List<String> updated = new ArrayList<String>();
      for (String line : original) updated.add(line);
      updated.add("");
      updated.add(Lang.localized(code, "settings.interface_help"));
      updated.add("");
      updated.add(Lang.localized(code, "settings.return_help"));
      sections.set(index, updated.toArray(new String[0]));
    }
    return sections.toArray(new String[0][]);
  }
}

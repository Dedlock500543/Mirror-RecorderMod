package com.mirror.recorder.gui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search over setting names and descriptions. The query is compared in three forms at once: as is,
 * as text typed in the wrong keyboard layout (jcnfyjdrf → остановка), and as Latin transliteration
 * (ostanovka → остановка). Typos are forgiven via Levenshtein distance, so "остовновка" also finds
 * "Остановка".
 */
public final class SearchMatch {
  private SearchMatch() {}

  private static final String EN_KEYS = "qwertyuiop[]asdfghjkl;'zxcvbnm,.";
  private static final String RU_KEYS = "йцукенгшщзхъфывапролджэячсмитьбю";
  // Multi-letter combinations are parsed before single letters.
  private static final String[][] TRANSLIT = {
    {"shch", "щ"},
    {"sch", "щ"},
    {"sh", "ш"},
    {"ch", "ч"},
    {"zh", "ж"},
    {"ts", "ц"},
    {"yu", "ю"},
    {"ju", "ю"},
    {"ya", "я"},
    {"ja", "я"},
    {"yo", "ё"},
    {"jo", "ё"},
    {"ye", "е"},
    {"kh", "х"},
    {"a", "а"},
    {"b", "б"},
    {"v", "в"},
    {"g", "г"},
    {"d", "д"},
    {"e", "е"},
    {"z", "з"},
    {"i", "и"},
    {"j", "й"},
    {"y", "ы"},
    {"k", "к"},
    {"l", "л"},
    {"m", "м"},
    {"n", "н"},
    {"o", "о"},
    {"p", "п"},
    {"r", "р"},
    {"s", "с"},
    {"t", "т"},
    {"u", "у"},
    {"f", "ф"},
    {"h", "х"},
    {"c", "к"},
    {"w", "в"},
    {"x", "х"},
    {"q", "к"}
  };

  /** Lower case, ё → е, letters and digits only, single spaces. */
  public static String norm(String s) {
    if (s == null) return "";
    String low =
        Normalizer.normalize(
                s.replaceAll("§.", "").toLowerCase(Locale.ROOT).replace('ё', 'е').replace('ł', 'l'),
                Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
    StringBuilder b = new StringBuilder(low.length());
    boolean space = false;
    for (int i = 0; i < low.length(); i++) {
      char c = low.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (space && b.length() > 0) b.append(' ');
        space = false;
        b.append(c);
      } else space = true;
    }
    return b.toString();
  }

  private static String layout(String s) {
    StringBuilder b = new StringBuilder(s.length());
    boolean any = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      int k = EN_KEYS.indexOf(c);
      if (k >= 0) {
        b.append(RU_KEYS.charAt(k));
        any = true;
      } else b.append(c);
    }
    return any ? b.toString() : "";
  }

  private static String translit(String s) {
    StringBuilder b = new StringBuilder(s.length());
    boolean any = false;
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c < 'a' || c > 'z') {
        b.append(c);
        i++;
        continue;
      }
      boolean done = false;
      for (int t = 0; t < TRANSLIT.length && !done; t++) {
        String from = TRANSLIT[t][0];
        if (s.startsWith(from, i)) {
          b.append(TRANSLIT[t][1]);
          i += from.length();
          any = done = true;
        }
      }
      if (!done) {
        b.append(c);
        i++;
      }
    }
    return any ? b.toString().replace('ё', 'е') : "";
  }

  /**
   * Query variants: as is, wrong layout, transliteration. Layout and transliteration are built from
   * the raw query before normalization: ";", "[", "," in the English layout are Russian letters,
   * not separators. Empty ones and duplicates are dropped.
   */
  public static String[] variants(String query) {
    String raw = query == null ? "" : query.toLowerCase(Locale.ROOT);
    List<String> out = new ArrayList<String>(3);
    for (String v : new String[] {norm(raw), norm(layout(raw)), norm(translit(raw))})
      if (v.length() > 0 && !out.contains(v)) out.add(v);
    return out.toArray(new String[out.size()]);
  }

  /** Levenshtein distance with early exit when the distance must exceed max. */
  private static int distance(String a, String b, int max) {
    int n = a.length(), m = b.length();
    if (Math.abs(n - m) > max) return max + 1;
    int[] prev = new int[m + 1], cur = new int[m + 1];
    for (int j = 0; j <= m; j++) prev[j] = j;
    for (int i = 1; i <= n; i++) {
      cur[0] = i;
      int best = cur[0];
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= m; j++) {
        int cost = ca == b.charAt(j - 1) ? 0 : 1;
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
        if (cur[j] < best) best = cur[j];
      }
      if (best > max) return max + 1;
      int[] swap = prev;
      prev = cur;
      cur = swap;
    }
    return prev[m];
  }

  private static int tolerance(int len) {
    return len <= 3 ? 0 : len <= 5 ? 1 : 2;
  }

  private static boolean near(String hay, String needle) {
    if (hay.length() == 0) return false;
    if (hay.contains(needle)) return true;
    int tol = tolerance(needle.length());
    if (tol == 0) return false;
    // A whole word resembles the query, or the query resembles the beginning of some word.
    int from = 0;
    while (from <= hay.length()) {
      int sp = hay.indexOf(' ', from);
      String word = sp < 0 ? hay.substring(from) : hay.substring(from, sp);
      if (word.length() > 0) {
        if (distance(word, needle, tol) <= tol) return true;
        int cut = Math.min(word.length(), needle.length() + tol);
        if (cut >= needle.length() - tol && distance(word.substring(0, cut), needle, tol) <= tol)
          return true;
      }
      if (sp < 0) break;
      from = sp + 1;
    }
    return false;
  }

  /** Whether any query variant matched the text. */
  public static boolean hit(String[] variants, String text) {
    if (variants == null || variants.length == 0 || text == null) return false;
    String hay = norm(text);
    for (String variant : variants) {
      boolean all = true;
      for (String token : variant.split(" ")) {
        if (!near(hay, token)) {
          all = false;
          break;
        }
      }
      if (all) return true;
    }
    return false;
  }
}

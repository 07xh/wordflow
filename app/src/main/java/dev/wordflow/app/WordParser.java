package dev.wordflow.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Pure Java parser for 2-column vocabulary TXT, TSV and CSV files. */
public final class WordParser {
    private WordParser() {}
    public static final class Item {
        public final String english;
        public final String meaning;
        public Item(String english, String meaning) { this.english = english; this.meaning = meaning; }
    }

    public static List<Item> parse(String text) {
        LinkedHashMap<String, Item> items = new LinkedHashMap<>();
        if (text == null) return new ArrayList<>();
        for (String record : records(text.replace("\uFEFF", ""))) {
            String value = record.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            String[] pair = splitPair(value);
            if (pair == null) continue;
            String english = pair[0].trim();
            String meaning = pair[1].trim();
            if (english.isEmpty() || meaning.isEmpty() || english.length() > 120 || meaning.length() > 1000) continue;
            if ((english.equalsIgnoreCase("english") || english.equalsIgnoreCase("word"))
                    && (meaning.equalsIgnoreCase("meaning") || meaning.equalsIgnoreCase("translation"))) continue;
            items.put(english.toLowerCase(Locale.ROOT), new Item(english, meaning));
        }
        return new ArrayList<>(items.values());
    }

    /** Split physical lines only outside quoted CSV fields. */
    private static List<String> records(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder record = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                record.append(ch);
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    record.append('"'); i++;
                } else {
                    quoted = !quoted;
                }
            } else if ((ch == '\r' || ch == '\n') && !quoted) {
                out.add(record.toString()); record.setLength(0);
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else {
                record.append(ch);
            }
        }
        if (record.length() > 0) out.add(record.toString());
        return out;
    }

    private static String[] splitPair(String record) {
        // A separator within quoted fields is part of the word/meaning.
        for (String sep : new String[] {"\t", "\\t", "|", "：", " = ", " - "}) {
            int index = outsideQuoteIndex(record, sep);
            if (index > 0) return new String[]{unquote(record.substring(0, index)),
                    unquote(record.substring(index + sep.length()))};
        }
        int comma = outsideQuoteIndex(record, ",");
        if (comma < 1) return null;
        return new String[]{unquote(record.substring(0, comma)), unquote(record.substring(comma + 1))};
    }

    private static int outsideQuoteIndex(String value, String sep) {
        boolean quoted = false;
        for (int i = 0; i <= value.length() - sep.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '"') { i++; continue; }
                quoted = !quoted;
            } else if (!quoted && value.startsWith(sep, i)) return i;
        }
        return -1;
    }

    private static String unquote(String value) {
        String s = value.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }
}

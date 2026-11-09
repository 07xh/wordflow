package dev.wordflow.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Portable, versioned, UTF-8 JSON backup. Restore input is fully validated
 * before WordStore begins its replace transaction.
 */
public final class WordBackup {
    public static final String FORMAT = "wordflow.full";
    public static final int VERSION = 1;
    public static final int MAX_WORDS = 100_000;
    private WordBackup() {}

    public static final class Entry {
        public long id;
        public String english;
        public String meaning;
        public int known, failed, seen;
        public long due, lastSeen;
        public Entry(long id, String english, String meaning, int known, int failed,
                     int seen, long due, long lastSeen) {
            this.id = id;
            this.english = english;
            this.meaning = meaning;
            this.known = known;
            this.failed = failed;
            this.seen = seen;
            this.due = due;
            this.lastSeen = lastSeen;
        }
    }

    public static final class SettingsData {
        public int seconds;
        public String display;
        public String order;
        public boolean reviewOnly;
        public SettingsData(int seconds, String display, String order, boolean reviewOnly) {
            this.seconds = seconds;
            this.display = display;
            this.order = order;
            this.reviewOnly = reviewOnly;
        }
    }

    public static final class Snapshot {
        public final List<Entry> words;
        public final SettingsData settings;
        public Snapshot(List<Entry> words, SettingsData settings) {
            this.words = words;
            this.settings = settings;
        }
    }

    public static String encode(Snapshot snapshot) throws JSONException {
        validate(snapshot);
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schema", VERSION);
        root.put("exportedAt", System.currentTimeMillis());
        JSONObject options = new JSONObject();
        options.put("seconds", snapshot.settings.seconds);
        options.put("display", snapshot.settings.display);
        options.put("order", snapshot.settings.order);
        options.put("reviewOnly", snapshot.settings.reviewOnly);
        root.put("settings", options);

        JSONArray words = new JSONArray();
        for (Entry e : snapshot.words) {
            JSONObject word = new JSONObject();
            word.put("id", e.id);
            word.put("english", e.english);
            word.put("meaning", e.meaning);
            word.put("known", e.known);
            word.put("failed", e.failed);
            word.put("seen", e.seen);
            word.put("due", e.due);
            word.put("lastSeen", e.lastSeen);
            words.put(word);
        }
        root.put("words", words);
        return root.toString(2);
    }

    public static Snapshot decode(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format", ""))) throw new JSONException("Not a WordFlow full backup");
        if (!(root.opt("schema") instanceof Number) || root.getInt("schema") != VERSION)
            throw new JSONException("Unsupported backup version");
        JSONObject opts = root.getJSONObject("settings");
        int seconds = numberInt(opts, "seconds");
        String display = requiredString(opts, "display");
        String order = requiredString(opts, "order");
        Object reviewOnly = opts.get("reviewOnly");
        if (!(reviewOnly instanceof Boolean)) throw new JSONException("Invalid reviewOnly");
        SettingsData settings = new SettingsData(seconds, display, order, (Boolean) reviewOnly);
        JSONArray list = root.getJSONArray("words");
        if (list.length() > MAX_WORDS) throw new JSONException("Too many words");
        List<Entry> entries = new ArrayList<>(list.length());
        for (int i = 0; i < list.length(); i++) {
            JSONObject w = list.getJSONObject(i);
            entries.add(new Entry(
                    numberLong(w, "id"), requiredString(w, "english"), requiredString(w, "meaning"),
                    numberInt(w, "known"), numberInt(w, "failed"), numberInt(w, "seen"),
                    numberLong(w, "due"), numberLong(w, "lastSeen")));
        }
        Snapshot snapshot = new Snapshot(entries, settings);
        validate(snapshot);
        return snapshot;
    }

    private static String requiredString(JSONObject obj, String key) throws JSONException {
        Object v = obj.get(key);
        if (!(v instanceof String)) throw new JSONException("Invalid " + key);
        return (String) v;
    }
    private static long numberLong(JSONObject obj, String key) throws JSONException {
        Object v = obj.get(key);
        if (!(v instanceof Number)) throw new JSONException("Invalid " + key);
        long value = ((Number)v).longValue();
        if (!String.valueOf(value).equals(v.toString()))
            throw new JSONException("Non-integer " + key);
        return value;
    }
    private static int numberInt(JSONObject obj, String key) throws JSONException {
        long value = numberLong(obj, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new JSONException("Out-of-range " + key);
        return (int) value;
    }

    public static void validate(Snapshot snapshot) throws JSONException {
        if (snapshot == null || snapshot.words == null || snapshot.settings == null)
            throw new JSONException("Missing backup data");
        if (snapshot.words.size() > MAX_WORDS) throw new JSONException("Too many words");
        SettingsData p = snapshot.settings;
        if (p.seconds < 10 || p.seconds > 3600
                || !("both".equals(p.display) || "english".equals(p.display)
                || "chinese".equals(p.display) || "reveal".equals(p.display))
                || !("sequential".equals(p.order) || "random".equals(p.order) || "weak".equals(p.order)))
            throw new JSONException("Invalid settings");
        Set<Long> ids = new HashSet<>();
        Set<String> terms = new HashSet<>();
        for (Entry e : snapshot.words) {
            if (e == null || e.id <= 0 || e.english == null || e.meaning == null
                    || e.english.trim().isEmpty() || e.english.length() > 120
                    || e.meaning.trim().isEmpty() || e.meaning.length() > 1000
                    || e.known < 0 || e.failed < 0 || e.seen < 0
                    || e.due < 0 || e.lastSeen < 0)
                throw new JSONException("Invalid word");
            if (!ids.add(e.id) || !terms.add(e.english.toLowerCase(Locale.ROOT)))
                throw new JSONException("Duplicate word ID or English term");
        }
    }
}

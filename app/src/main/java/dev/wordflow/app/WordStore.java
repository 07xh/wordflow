package dev.wordflow.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class WordStore extends SQLiteOpenHelper {
    public static final class Word {
        public long id;
        public String english;
        public String meaning;
        public int known;
        public int failed;
        public int seen;
        public long due;
    }

    public WordStore(Context context) { super(context, "wordflow.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE words (id INTEGER PRIMARY KEY AUTOINCREMENT, english TEXT NOT NULL COLLATE NOCASE UNIQUE, meaning TEXT NOT NULL, known_count INTEGER NOT NULL DEFAULT 0, fail_count INTEGER NOT NULL DEFAULT 0, seen_count INTEGER NOT NULL DEFAULT 0, due_at INTEGER NOT NULL DEFAULT 0, last_seen INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_words_due ON words(due_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { /* Future migrations must preserve user data. */ }

    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM words", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public synchronized int learnedCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM words WHERE known_count > 0", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public synchronized int dueCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM words WHERE due_at > 0 AND due_at <= ?", new String[]{String.valueOf(System.currentTimeMillis())})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public synchronized boolean add(String english, String meaning) {
        ContentValues values = new ContentValues();
        values.put("english", english.trim());
        values.put("meaning", meaning.trim());
        return getWritableDatabase().insertWithOnConflict("words", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }
    public synchronized int addBatch(List<WordParser.Item> items) {
        SQLiteDatabase db = getWritableDatabase();
        int inserted = 0;
        db.beginTransaction();
        try {
            for (WordParser.Item item : items) {
                ContentValues v = new ContentValues(); v.put("english", item.english); v.put("meaning", item.meaning);
                if (db.insertWithOnConflict("words", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) inserted++;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return inserted;
    }
    public synchronized void update(long id, String english, String meaning) {
        ContentValues v = new ContentValues(); v.put("english", english.trim()); v.put("meaning", meaning.trim());
        getWritableDatabase().update("words", v, "id=?", new String[]{String.valueOf(id)});
    }
    public synchronized void delete(long id) { getWritableDatabase().delete("words", "id=?", new String[]{String.valueOf(id)}); }
    public synchronized void clear() { getWritableDatabase().delete("words", null, null); }

    private Word read(Cursor c) {
        Word w = new Word();
        w.id = c.getLong(0); w.english = c.getString(1); w.meaning = c.getString(2);
        w.known = c.getInt(3); w.failed = c.getInt(4); w.seen = c.getInt(5); w.due = c.getLong(6);
        return w;
    }
    private static final String COLS = "id,english,meaning,known_count,fail_count,seen_count,due_at";
    public synchronized Word find(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COLS + " FROM words WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    public synchronized List<Word> list(String query) {
        List<Word> words = new ArrayList<>();
        String where = (query == null || query.isEmpty()) ? "" : " WHERE english LIKE ? OR meaning LIKE ?";
        String[] args = where.isEmpty() ? null : new String[]{"%" + query + "%", "%" + query + "%"};
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM words" + where + " ORDER BY english COLLATE NOCASE LIMIT 120", args)) {
            while (c.moveToNext()) words.add(read(c));
        }
        return words;
    }
    public synchronized Word next(String order, boolean reviewOnly, long afterId) {
        String where = reviewOnly ? "due_at>0 AND due_at<=?" : "1=1";
        String[] args = reviewOnly ? new String[]{String.valueOf(System.currentTimeMillis())} : null;
        if ("random".equals(order) || "weak".equals(order)) {
            String filtered = where + " AND id!=" + Math.max(0, afterId);
            // Balance vocabulary coverage before prioritizing difficult words.
            // Previous score-only ordering alternated the same two high-score words indefinitely.
            String sorter = "random".equals(order) ? "RANDOM()" :
                    "seen_count ASC, (fail_count*3-known_count) DESC, last_seen ASC, RANDOM()";
            Word other = pick(filtered, args, sorter);
            return other == null ? pick(where, args, sorter) : other;
        }
        String afterWhere = where + " AND id>" + Math.max(0, afterId);
        Word found = pick(afterWhere, args, "id ASC");
        return found == null ? pick(where, args, "id ASC") : found;
    }
    private Word pick(String where, String[] args, String order) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLS + " FROM words WHERE " + where + " ORDER BY " + order + " LIMIT 1", args)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }
    /** Export vocabulary (not repetition history) with RFC 4180 quoting, UTF-8 ready. */
    public synchronized String exportCsv() {
        StringBuilder csv = new StringBuilder("english,meaning\r\n");
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT english,meaning FROM words ORDER BY english COLLATE NOCASE", null)) {
            while (c.moveToNext()) {
                csv.append(WordCsv.row(c.getString(0), c.getString(1)));
            }
        }
        return csv.toString();
    }
    public synchronized void shown(long id) {
        getWritableDatabase().execSQL("UPDATE words SET seen_count=seen_count+1,last_seen=? WHERE id=?", new Object[]{System.currentTimeMillis(), id});
    }
    public synchronized void answer(long id, boolean remembered) {
        SQLiteDatabase db = getWritableDatabase();
        Word current;
        try (Cursor c = db.rawQuery("SELECT " + COLS + " FROM words WHERE id=?", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return;
            current = read(c);
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        if (remembered) {
            values.put("known_count", WordReview.rememberedCount(current.known, true));
            values.put("due_at", WordReview.nextDue(now, current.known, true));
        } else {
            values.put("fail_count", current.failed + 1);
            values.put("known_count", WordReview.rememberedCount(current.known, false));
            values.put("due_at", WordReview.nextDue(now, current.known, false));
        }
        db.update("words", values, "id=?", new String[]{String.valueOf(id)});
    }
    /** Read all rows, including full review scheduling metadata, without the UI's 120-word limit. */
    public synchronized List<WordBackup.Entry> exportBackup() {
        List<WordBackup.Entry> entries = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            try (Cursor c = db.rawQuery(
                    "SELECT id,english,meaning,known_count,fail_count,seen_count,due_at,last_seen FROM words ORDER BY id", null)) {
                while (c.moveToNext()) entries.add(new WordBackup.Entry(
                        c.getLong(0), c.getString(1), c.getString(2),
                        c.getInt(3), c.getInt(4), c.getInt(5), c.getLong(6), c.getLong(7)));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return entries;
    }

    /** Atomic replacement: an invalid or conflicting row cannot erase the previous vocabulary. */
    public synchronized void replaceFromBackup(List<WordBackup.Entry> entries) throws org.json.JSONException {
        WordBackup.validate(new WordBackup.Snapshot(entries,
                new WordBackup.SettingsData(20, "both", "sequential", false)));
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("words", null, null);
            for (WordBackup.Entry e : entries) {
                ContentValues v = new ContentValues();
                v.put("id", e.id);
                v.put("english", e.english);
                v.put("meaning", e.meaning);
                v.put("known_count", e.known);
                v.put("fail_count", e.failed);
                v.put("seen_count", e.seen);
                v.put("due_at", e.due);
                v.put("last_seen", e.lastSeen);
                db.insertOrThrow("words", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

}

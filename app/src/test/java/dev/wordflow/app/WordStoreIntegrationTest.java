package dev.wordflow.app;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** Exercises real SQLiteOpenHelper methods in Robolectric, not mock SQL objects. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class WordStoreIntegrationTest {
    private Context context;
    private WordStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("wordflow.db");
        store = new WordStore(context);
    }

    @After public void tearDown() {
        store.close();
        context.deleteDatabase("wordflow.db");
    }

    private WordBackup.Entry word(long id, String english, String meaning, int known, int failed) {
        return new WordBackup.Entry(id, english, meaning, known, failed, 9,
                1_760_000_000_000L + id, 1_750_000_000_000L + id);
    }

    @Test public void exportRestorePreservesPrimaryKeysAndFullReviewState() throws Exception {
        List<WordBackup.Entry> original = Arrays.asList(
                word(2, "apple", "苹果", 3, 1),
                word(7, "résumé", "简历\\n档案", 1, 2));
        store.replaceFromBackup(original);
        WordBackup.Snapshot before = new WordBackup.Snapshot(
                store.exportBackup(), new WordBackup.SettingsData(40, "reveal", "weak", true));
        WordBackup.Snapshot decoded = WordBackup.decode(WordBackup.encode(before));
        store.clear();
        assertEquals(0, store.count());
        store.replaceFromBackup(decoded.words);
        List<WordBackup.Entry> restored = store.exportBackup();
        assertEquals(2, restored.size());
        for (int i = 0; i < 2; i++) {
            WordBackup.Entry expected = original.get(i);
            WordBackup.Entry actual = restored.get(i);
            assertEquals(expected.id, actual.id);
            assertEquals(expected.english, actual.english);
            assertEquals(expected.meaning, actual.meaning);
            assertEquals(expected.known, actual.known);
            assertEquals(expected.failed, actual.failed);
            assertEquals(expected.seen, actual.seen);
            assertEquals(expected.due, actual.due);
            assertEquals(expected.lastSeen, actual.lastSeen);
        }
    }

    @Test public void failedInsertWithinRestoreRollsBackEntireDatabase() throws Exception {
        List<WordBackup.Entry> original = Arrays.asList(
                word(2, "apple", "苹果", 2, 1), word(7, "banana", "香蕉", 0, 3));
        store.replaceFromBackup(original);
        SQLiteDatabase db = store.getWritableDatabase();
        db.execSQL("CREATE TRIGGER reject_blocked BEFORE INSERT ON words "
                + "WHEN NEW.english = 'blocked' BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
        try {
            store.replaceFromBackup(Arrays.asList(
                    word(11, "pear", "梨", 5, 0), word(12, "blocked", "不允许", 1, 1)));
            fail("Expected a database exception caused by the test trigger");
        } catch (SQLiteConstraintException expected) {
            // The earlier delete and the first successful insert must both roll back.
        }
        List<WordBackup.Entry> restored = store.exportBackup();
        assertEquals(2, restored.size());
        assertEquals(2L, restored.get(0).id);
        assertEquals("apple", restored.get(0).english);
        assertEquals(2, restored.get(0).known);
        assertEquals(1, restored.get(0).failed);
        assertEquals(7L, restored.get(1).id);
        assertEquals("banana", restored.get(1).english);
        assertEquals(3, restored.get(1).failed);
    }

    @Test public void backupExportsAllRowsBeyondUiDisplayLimit() throws Exception {
        List<WordBackup.Entry> items = new ArrayList<>();
        for (int i = 1; i <= 155; i++) {
            items.add(word(i, String.format(java.util.Locale.ROOT, "word_%04d", i), "释义" + i, i % 3, i % 2));
        }
        store.replaceFromBackup(items);
        assertEquals(155, store.count());
        assertEquals(120, store.list("").size());
        assertEquals(155, store.exportBackup().size());
    }

    @Test public void rejectedDuplicateBackupDoesNotTouchExistingData() throws Exception {
        store.replaceFromBackup(Arrays.asList(word(10, "existing", "保留", 4, 2)));
        try {
            store.replaceFromBackup(Arrays.asList(
                    word(1, "DUPLICATE", "甲", 0, 0),
                    word(2, "duplicate", "乙", 0, 0)));
            fail("Duplicate English terms must be rejected before database mutation");
        } catch (org.json.JSONException expected) {
            // Validation happens before opening the destructive DB transaction.
        }
        assertEquals(1, store.count());
        assertEquals("existing", store.exportBackup().get(0).english);
        assertEquals(4, store.exportBackup().get(0).known);
    }
}

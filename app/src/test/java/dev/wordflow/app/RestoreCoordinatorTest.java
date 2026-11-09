package dev.wordflow.app;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Recovery crosses the SQLite/SharedPreferences transaction boundary. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class RestoreCoordinatorTest {
    private Context context;
    private WordStore store;
    private WordPrefs prefs;
    private RestoreCoordinator coordinator;

    private static WordBackup.Entry entry(long id, String english, int known, int failed) {
        return new WordBackup.Entry(id, english, "释义" + id, known, failed, 9,
                1_760_000_000_000L + id, 1_750_000_000_000L + id);
    }

    private WordBackup.Snapshot snapshot(List<WordBackup.Entry> words, int seconds, String display) {
        return new WordBackup.Snapshot(words,
                new WordBackup.SettingsData(seconds, display, "sequential", false));
    }

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("wordflow.db");
        clearFiles();
        context.getSharedPreferences("wordflow", Context.MODE_PRIVATE).edit().clear().commit();
        store = new WordStore(context);
        prefs = new WordPrefs(context);
        coordinator = new RestoreCoordinator(context, store, prefs);
    }

    @After public void tearDown() {
        store.close();
        context.deleteDatabase("wordflow.db");
        clearFiles();
        context.getSharedPreferences("wordflow", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void clearFiles() {
        for (String name : new String[]{"restore-safety.json", "restore-pending.json"}) {
            for (String suffix : new String[]{"", ".new", ".bak"}) {
                new File(context.getFilesDir(), name + suffix).delete();
            }
        }
    }

    @Test public void successfulApplyPreservesUndoAndLeavesNoPendingJournal() throws Exception {
        WordBackup.Snapshot original = snapshot(Arrays.asList(entry(10, "before", 4, 2)), 20, "both");
        store.replaceFromBackup(original.words);
        WordBackup.Snapshot next = snapshot(Arrays.asList(entry(20, "after", 2, 5)), 40, "english");
        coordinator.apply(next, true);
        assertFalse(coordinator.hasPendingRecovery());
        assertEquals("after", store.exportBackup().get(0).english);
        assertEquals(40, prefs.seconds());
        assertEquals("english", prefs.display());
        WordBackup.Snapshot undo = new RestoreSafetyStore(context).load();
        assertEquals("before", undo.words.get(0).english);
        assertEquals(4, undo.words.get(0).known);
        assertEquals(2, undo.words.get(0).failed);
        assertEquals(20, undo.settings.seconds);
    }

    @Test public void crashAfterDatabaseReplacedAutomaticallyRestoresBeforeImage() throws Exception {
        WordBackup.Snapshot original = snapshot(Arrays.asList(entry(1, "original", 3, 1)), 25, "both");
        store.replaceFromBackup(original.words);
        assertTrue(prefs.restoreSettings(original.settings));
        new RestoreSafetyStore(context, "restore-pending.json").save(original);
        // Simulate a process crash between SQLite commit and settings commit.
        store.replaceFromBackup(Arrays.asList(entry(2, "partial", 99, 99)));
        RestoreCoordinator restarted = new RestoreCoordinator(context, store, new WordPrefs(context));
        assertTrue(restarted.hasPendingRecovery());
        assertTrue(restarted.recoverPending());
        assertFalse(restarted.hasPendingRecovery());
        WordBackup.Entry restored = store.exportBackup().get(0);
        assertEquals("original", restored.english);
        assertEquals(3, restored.known);
        assertEquals(1, restored.failed);
        assertEquals(1L, restored.id);
        assertEquals(25, prefs.seconds());
    }

    @Test public void crashAfterBothStoresUpdatedStillRollsBackBeforeJournalIsCleared() throws Exception {
        WordBackup.Snapshot original = snapshot(Arrays.asList(entry(3, "old", 5, 0)), 25, "reveal");
        store.replaceFromBackup(original.words);
        prefs.restoreSettings(original.settings);
        new RestoreSafetyStore(context, "restore-pending.json").save(original);
        store.replaceFromBackup(Arrays.asList(entry(4, "new", 0, 8)));
        prefs.restoreSettings(snapshot(Arrays.asList(entry(4, "new", 0, 8)), 60, "chinese").settings);
        assertTrue(new RestoreCoordinator(context, store, prefs).recoverPending());
        assertEquals("old", store.exportBackup().get(0).english);
        assertEquals("reveal", prefs.display());
        assertEquals(25, prefs.seconds());
    }

    @Test public void interruptedUndoDoesNotEraseIndependentUndoFile() throws Exception {
        WordBackup.Snapshot archived = snapshot(Arrays.asList(entry(1, "archive", 8, 3)), 20, "both");
        WordBackup.Snapshot current = snapshot(Arrays.asList(entry(2, "current", 1, 0)), 30, "english");
        store.replaceFromBackup(current.words);
        prefs.restoreSettings(current.settings);
        new RestoreSafetyStore(context).save(archived);
        coordinator.apply(archived, false);
        assertEquals("archive", store.exportBackup().get(0).english);
        assertEquals("archive", new RestoreSafetyStore(context).load().words.get(0).english);
        assertFalse(coordinator.hasPendingRecovery());
    }

    @Test public void databaseErrorTriggersRollbackAndCleansJournal() throws Exception {
        WordBackup.Snapshot original = snapshot(Arrays.asList(entry(10, "safe", 7, 1)), 30, "both");
        store.replaceFromBackup(original.words);
        prefs.restoreSettings(original.settings);
        SQLiteDatabase db = store.getWritableDatabase();
        db.execSQL("CREATE TRIGGER reject_fault BEFORE INSERT ON words "
                + "WHEN NEW.english = 'fault' BEGIN SELECT RAISE(ABORT, 'test fault'); END");
        try {
            coordinator.apply(snapshot(Arrays.asList(entry(20, "fault", 0, 0)), 40, "english"), true);
            fail("Database trigger must reject injected faulty restore");
        } catch (SQLiteConstraintException expected) {
            // Coordinator must recover the pre-image after the transaction fails.
        }
        assertFalse(coordinator.hasPendingRecovery());
        assertEquals("safe", store.exportBackup().get(0).english);
        assertEquals(7, store.exportBackup().get(0).known);
        assertEquals(30, prefs.seconds());
    }

    @Test public void corruptPendingFileCannotSilentlyUnlockNormalUsage() throws Exception {
        store.replaceFromBackup(Arrays.asList(entry(1, "safe", 1, 0)));
        File path = new File(context.getFilesDir(), "restore-pending.json");
        try (FileOutputStream output = new FileOutputStream(path)) {
            output.write("{ corrupt".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(coordinator.hasPendingRecovery());
        try {
            coordinator.recoverPending();
            fail("Corrupt journal must not be discarded");
        } catch (org.json.JSONException expected) {
            assertTrue(coordinator.hasPendingRecovery());
            assertEquals("safe", store.exportBackup().get(0).english);
        }
    }

    @Test public void refusesRestoreWhileLearningSessionMarkedRunning() throws Exception {
        store.replaceFromBackup(Arrays.asList(entry(1, "safe", 2, 1)));
        prefs.running(true);
        try {
            coordinator.apply(snapshot(Arrays.asList(entry(2, "replace", 0, 0)), 50, "both"), true);
            fail("Unsafe restore must fail when study session is active");
        } catch (java.io.IOException expected) {
            assertFalse(coordinator.hasPendingRecovery());
            assertEquals("safe", store.exportBackup().get(0).english);
        }
    }
}

package dev.wordflow.app;

import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class RecoveryStorageTest {
    private Context context;
    private File base;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        base = new File(context.getFilesDir(), "restore-safety.json");
        deleteFiles();
    }

    @After public void tearDown() { deleteFiles(); }

    private void deleteFiles() {
        for (String suffix : new String[]{"", ".new", ".bak"}) {
            new File(base.getAbsolutePath() + suffix).delete();
        }
        context.getSharedPreferences("wordflow", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void localSafetySnapshotKeepsEveryReviewField() throws Exception {
        RestoreSafetyStore safety = new RestoreSafetyStore(context);
        assertFalse(safety.exists());
        safety.save(new WordBackup.Snapshot(Arrays.asList(new WordBackup.Entry(
                21, "remember", "记住", 7, 2, 19, 1_770_000_000_000L, 1_760_000_000_000L)),
                new WordBackup.SettingsData(60, "english", "random", false)));
        assertTrue(safety.exists());
        WordBackup.Snapshot previous = new RestoreSafetyStore(context).load();
        assertEquals(21L, previous.words.get(0).id);
        assertEquals(7, previous.words.get(0).known);
        assertEquals(2, previous.words.get(0).failed);
        assertEquals(19, previous.words.get(0).seen);
        assertEquals(1_770_000_000_000L, previous.words.get(0).due);
        assertEquals(1_760_000_000_000L, previous.words.get(0).lastSeen);
        assertEquals("english", previous.settings.display);
    }

    @Test public void corruptSafetySnapshotIsNeverAccepted() throws Exception {
        RestoreSafetyStore safety = new RestoreSafetyStore(context);
        safety.save(new WordBackup.Snapshot(Arrays.asList(new WordBackup.Entry(
                1, "test", "测试", 1, 0, 1, 100L, 90L)),
                new WordBackup.SettingsData(20, "both", "sequential", false)));
        try (FileOutputStream stream = new FileOutputStream(base)) {
            stream.write("{ invalid json".getBytes(StandardCharsets.UTF_8));
        }
        try {
            safety.load();
            fail("A corrupted safety snapshot cannot be used for restore");
        } catch (org.json.JSONException expected) {
            // Application remains unchanged when the snapshot is unreadable.
        }
    }

    @Test public void restoredSettingsNeverReactivateNotificationSession() {
        WordPrefs prefs = new WordPrefs(context);
        prefs.running(true);
        prefs.paused(true);
        prefs.currentCard("old", "旧");
        assertTrue(prefs.restoreSettings(new WordBackup.SettingsData(90, "chinese", "weak", true)));
        assertFalse(prefs.running());
        assertFalse(prefs.paused());
        assertEquals("", prefs.currentEnglish());
        assertEquals("", prefs.currentMeaning());
        assertEquals("chinese", prefs.display());
        assertEquals("weak", prefs.order());
        assertEquals(90, prefs.seconds());
        assertTrue(prefs.reviewOnly());
    }
}

package dev.wordflow.app;

import org.junit.Test;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class WordBackupTest {
    private WordBackup.Snapshot sample() {
        return new WordBackup.Snapshot(Arrays.asList(
                new WordBackup.Entry(2L, "apple", "苹果,水果", 3, 1, 9, 1760000000000L, 1750000000000L),
                new WordBackup.Entry(7L, "résumé", "简历\n档案", 1, 2, 4, 1770000000000L, 1760000000000L)
        ), new WordBackup.SettingsData(30, "reveal", "weak", true));
    }
    @Test public void fullRoundTripPreservesProgressSettingsAndIdentity() throws Exception {
        WordBackup.Snapshot restored = WordBackup.decode(WordBackup.encode(sample()));
        assertEquals(2, restored.words.size());
        assertEquals(7L, restored.words.get(1).id);
        assertEquals(4, restored.words.get(1).seen);
        assertEquals(2, restored.words.get(1).failed);
        assertEquals(1760000000000L, restored.words.get(1).lastSeen);
        assertEquals(1770000000000L, restored.words.get(1).due);
        assertEquals("简历\n档案", restored.words.get(1).meaning);
        assertEquals("weak", restored.settings.order);
        assertEquals("reveal", restored.settings.display);
        assertEquals(30, restored.settings.seconds);
        assertTrue(restored.settings.reviewOnly);
    }
    @Test public void allowsEmptyWordCollection() throws Exception {
        WordBackup.Snapshot snapshot = new WordBackup.Snapshot(
                Collections.emptyList(), new WordBackup.SettingsData(20, "both", "sequential", false));
        assertEquals(0, WordBackup.decode(WordBackup.encode(snapshot)).words.size());
    }
    @Test(expected = JSONException.class) public void rejectsUnsupportedSchema() throws Exception {
        WordBackup.decode(WordBackup.encode(sample()).replace("\"schema\": 1", "\"schema\": 999"));
    }
    @Test(expected = JSONException.class) public void rejectsDuplicateIdentity() throws Exception {
        WordBackup.Snapshot snapshot = sample();
        snapshot.words.get(1).id = 2;
        WordBackup.encode(snapshot);
    }
    @Test(expected = JSONException.class) public void rejectsDuplicateEnglish() throws Exception {
        WordBackup.Snapshot snapshot = sample();
        snapshot.words.get(1).english = "APPLE";
        WordBackup.encode(snapshot);
    }
    @Test(expected = JSONException.class) public void rejectsInvalidReviewDueDate() throws Exception {
        WordBackup.Snapshot snapshot = sample();
        snapshot.words.get(0).due = -3;
        WordBackup.encode(snapshot);
    }
    @Test(expected = JSONException.class) public void rejectsInvalidSettings() throws Exception {
        WordBackup.Snapshot snapshot = sample();
        snapshot.settings.order = "unknown";
        WordBackup.encode(snapshot);
    }

    @Test public void writesContentDigestAndPreservesOldV05Backups() throws Exception {
        String data = WordBackup.encode(sample());
        JSONObject doc = new JSONObject(data);
        assertEquals(64, doc.getString("contentSha256").length());
        doc.remove("contentSha256");
        assertEquals(2, WordBackup.decode(doc.toString()).words.size());
    }

    @Test(expected = JSONException.class) public void rejectsModifiedWordMeaning() throws Exception {
        String text = WordBackup.encode(sample());
        WordBackup.decode(text.replace("苹果,水果", "篡改后的释义"));
    }

    @Test(expected = JSONException.class) public void rejectsModifiedReviewProgress() throws Exception {
        JSONObject doc = new JSONObject(WordBackup.encode(sample()));
        doc.getJSONArray("words").getJSONObject(0).put("known", 999);
        WordBackup.decode(doc.toString());
    }

    @Test(expected = JSONException.class) public void rejectsModifiedSettings() throws Exception {
        JSONObject doc = new JSONObject(WordBackup.encode(sample()));
        doc.getJSONObject("settings").put("seconds", 50);
        WordBackup.decode(doc.toString());
    }

    @Test(expected = JSONException.class) public void rejectsTamperedChecksum() throws Exception {
        JSONObject doc = new JSONObject(WordBackup.encode(sample()));
        doc.put("contentSha256", "wrong");
        WordBackup.decode(doc.toString());
    }

    @Test(expected = JSONException.class) public void rejectsWhitespaceDuplicateWord() throws Exception {
        WordBackup.Snapshot snapshot = sample();
        snapshot.words.get(1).english = " apple ";
        WordBackup.encode(snapshot);
    }
    @Test(expected = JSONException.class) public void rejectsTruncatedFile() throws Exception {
        WordBackup.decode("{\"format\": \"wordflow.full\",");
    }
}

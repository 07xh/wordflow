package dev.wordflow.app;

import android.content.Context;
import org.json.JSONException;
import java.io.IOException;

/**
 * Crash-safe coordinator between SQLite vocabulary and SharedPreferences.
 *
 * Both stores commit independently. Persist the BEFORE image in AtomicFile
 * prior to changing either store. An interrupted operation is conservatively
 * rolled back when the app next starts, including a crash after both writes
 * but before the pending file is cleared.
 */
public final class RestoreCoordinator {
    private final WordStore store;
    private final WordPrefs prefs;
    private final RestoreSafetyStore undoFile;
    private final RestoreSafetyStore pendingFile;

    public RestoreCoordinator(Context context, WordStore store, WordPrefs prefs) {
        this.store = store;
        this.prefs = prefs;
        undoFile = new RestoreSafetyStore(context);
        pendingFile = new RestoreSafetyStore(context, "restore-pending.json");
    }

    public boolean hasPendingRecovery() {
        return pendingFile.exists();
    }

    /** Do this before exposing the normal UI or permitting any new learning session. */
    public boolean recoverPending() throws IOException, JSONException {
        if (!pendingFile.exists()) return false;
        WordBackup.Snapshot before = pendingFile.load();
        // A failed SQL import cannot partly replace the old database.
        store.replaceFromBackup(before.words);
        if (!prefs.restoreSettings(before.settings))
            throw new IOException("Cannot restore prior settings");
        pendingFile.clear();
        return true;
    }

    /**
     * Restore data. Saving the journal must succeed BEFORE either store changes.
     * The user's undo snapshot remains independent from the crash journal.
     */
    public void apply(WordBackup.Snapshot snapshot, boolean saveUndo) throws Exception {
        WordBackup.validate(snapshot);
        if (hasPendingRecovery()) throw new IOException("Pending recovery must be resolved first");
        if (WordService.isSessionActive() || prefs.running())
            throw new IOException("Stop vocabulary session before restoring");

        WordBackup.Snapshot before = new WordBackup.Snapshot(store.exportBackup(), prefs.backupSettings());
        pendingFile.save(before);
        try {
            if (saveUndo) undoFile.save(before);
            store.replaceFromBackup(snapshot.words);
            if (!prefs.restoreSettings(snapshot.settings))
                throw new IOException("Could not save restored settings");
            pendingFile.clear();
        } catch (Exception original) {
            // If rollback fails leave the journal intact for startup recovery.
            try { recoverPending(); }
            catch (Exception rollback) { original.addSuppressed(rollback); }
            throw original;
        }
    }
}

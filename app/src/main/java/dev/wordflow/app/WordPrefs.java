package dev.wordflow.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class WordPrefs {
    private final SharedPreferences data;
    public WordPrefs(Context c) { data = c.getSharedPreferences("wordflow", Context.MODE_PRIVATE); }
    public int seconds() { return data.getInt("seconds", 20); }
    public void seconds(int value) { data.edit().putInt("seconds", Math.max(10, Math.min(value, 3600))).apply(); }
    public String display() { return data.getString("display", "both"); }
    public void display(String value) { data.edit().putString("display", value).apply(); }
    public String order() { return data.getString("order", "sequential"); }
    public void order(String value) { data.edit().putString("order", value).apply(); }
    public boolean reviewOnly() { return data.getBoolean("review", false); }
    public void reviewOnly(boolean value) { data.edit().putBoolean("review", value).apply(); }
    public boolean paused() { return data.getBoolean("paused", false); }
    public void paused(boolean value) { data.edit().putBoolean("paused", value).apply(); }
    /** Current card snapshot for the home screen; maintained by the active service. */
    public String currentEnglish() { return data.getString("current_english", ""); }
    public String currentMeaning() { return data.getString("current_meaning", ""); }
    public void currentCard(String english, String meaning) {
        data.edit().putString("current_english", english)
                .putString("current_meaning", meaning).apply();
    }
    public void clearCurrentCard() {
        data.edit().remove("current_english").remove("current_meaning").apply();
    }
    public boolean running() { return data.getBoolean("running", false); }
    public void running(boolean value) { data.edit().putBoolean("running", value).apply(); }
    public WordBackup.SettingsData backupSettings() {
        return new WordBackup.SettingsData(seconds(), display(), order(), reviewOnly());
    }

    /** A restored backup must never silently restart a previously running service. */
    public boolean restoreSettings(WordBackup.SettingsData s) {
        return data.edit().putInt("seconds", s.seconds)
                .putString("display", s.display)
                .putString("order", s.order)
                .putBoolean("review", s.reviewOnly)
                .putBoolean("running", false)
                .putBoolean("paused", false)
                .remove("current_english").remove("current_meaning")
                .commit();
    }
}

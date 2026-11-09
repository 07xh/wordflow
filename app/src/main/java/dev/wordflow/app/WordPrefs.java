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
    public boolean running() { return data.getBoolean("running", false); }
    public void running(boolean value) { data.edit().putBoolean("running", value).apply(); }
}

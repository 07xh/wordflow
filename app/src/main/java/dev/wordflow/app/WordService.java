package dev.wordflow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.util.UUID;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** User-initiated vocabulary session; no networking, no auto-start at boot. */
public final class WordService extends Service {
    public static final String START = "dev.wordflow.app.START";
    public static final String PAUSE = "dev.wordflow.app.PAUSE";
    public static final String STOP = "dev.wordflow.app.STOP";
    public static final String NEXT = "dev.wordflow.app.NEXT";
    public static final String KNOWN = "dev.wordflow.app.KNOWN";
    public static final String AGAIN = "dev.wordflow.app.AGAIN";
    public static final String REFRESH = "dev.wordflow.app.REFRESH";
    private static final String CHANNEL = "wordflow_study";
    private static final int NOTIFICATION_ID = 1024;
    private static final String EXTRA_CARD_TOKEN = "card_token";
    private static final String EXTRA_UI_CONTROL = "ui_control";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WordStore store;
    private WordPrefs prefs;
    private WordStore.Word current;
    private long lastId = 0;
    /** Distinct for each displayed card; never reuse after a restart or next word. */
    private String cardToken;
    private boolean revealed;
    private boolean active;
    private boolean foregroundStarted;
    private static volatile boolean sessionActive;

    public static boolean isSessionActive() { return sessionActive; }
    private final Runnable tick = () -> { if (active) advance(); };
    private final Runnable reveal = () -> { if (active && current != null) { revealed = true; updateNotification(); } };

    @Override public void onCreate() {
        super.onCreate();
        store = new WordStore(this);
        prefs = new WordPrefs(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "词序 · 单词学习", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("正在进行的主动单词学习会话");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // START_NOT_STICKY: an unexpected null-intent restart must not begin a session.
        String action = intent == null ? STOP : intent.getAction();
        if (action == null) action = STOP;
        if (STOP.equals(action) || PAUSE.equals(action)) { stopSession(PAUSE.equals(action)); return START_NOT_STICKY; }
        // A PendingIntent from an old notification must not resurrect a stopped session.
        if (!active && !START.equals(action)) {
            prefs.running(false);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (!active) {
            if (store.count() == 0) { prefs.running(false); stopSelfResult(startId); return START_NOT_STICKY; }
            active = true;
            sessionActive = true;
            prefs.running(true);
            prefs.paused(false);
            try {
                showForeground(build("词序正在启动", "准备单词…"));
            } catch (RuntimeException error) {
                Log.e("WordFlow", "Unable to create foreground learning session", error);
                stopSession(false);
                return START_NOT_STICKY;
            }
        }
        // An old card can remain clickable briefly after a notification refresh. Never
        // apply its result to a different word or another learning session.
        if (KNOWN.equals(action) || AGAIN.equals(action) || NEXT.equals(action)) {
            boolean fromUi = NEXT.equals(action) && intent != null
                    && intent.getBooleanExtra(EXTRA_UI_CONTROL, false);
            String token = intent == null ? null : intent.getStringExtra(EXTRA_CARD_TOKEN);
            if (!CardActionGuard.accepts(NEXT.equals(action), fromUi, cardToken, token)) {
                updateNotification();
                return START_NOT_STICKY;
            }
        }
        if (KNOWN.equals(action) || AGAIN.equals(action)) {
            if (current != null) store.answer(current.id, KNOWN.equals(action));
            advance();
        } else if (NEXT.equals(action)) {
            advance();
        } else if (REFRESH.equals(action) && current != null) {
            // Editing/deleting a word in the library must invalidate the old card.
            WordStore.Word latest = store.find(current.id);
            if (latest == null) advance();
            else {
                current = latest;
                prefs.currentCard(latest.english, latest.meaning);
                updateNotification();
                schedule();
            }
        } else if (current == null) {
            advance();
        } else {
            updateNotification();
            schedule();
        }
        return START_NOT_STICKY;
    }

    private void advance() {
        if (!active) return;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(reveal);
        current = store.next(prefs.order(), prefs.reviewOnly(), lastId);
        revealed = false;
        cardToken = current == null ? null : UUID.randomUUID().toString();
        if (current == null) {
            Notification done = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_word)
                    .setContentTitle("本轮学习已完成")
                    .setContentText("当前没有可显示的单词，可到词库继续添加")
                    .setAutoCancel(true).build();
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID + 1, done);
            stopSession(false);
            return;
        }
        lastId = current.id;
        prefs.currentCard(current.english, current.meaning);
        store.shown(lastId);
        updateNotification();
        schedule();
    }
    private void schedule() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(reveal);
        if (!active || current == null) return;
        long duration = Math.max(10_000L, prefs.seconds() * 1000L);
        handler.postDelayed(tick, duration);
        if ("reveal".equals(prefs.display())) handler.postDelayed(reveal, Math.min(4000L, duration / 2));
    }
    private void updateNotification() {
        if (!active) return;
        if (current == null) return;
        CardPresentation.Lines lines = CardPresentation.format(
                current.english, current.meaning, prefs.display(), revealed);
        // startForeground is needed once per session; subsequent changes only update
        // the same notification rather than restarting the foreground transition.
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, build(lines.title, lines.subtitle));
    }
    private Notification build(String title, String subtitle) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_word)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setStyle(new Notification.BigTextStyle().bigText(subtitle))
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false);
        if (current != null) {
            builder.addAction(new Notification.Action.Builder(null, "不会", serviceAction(AGAIN, 11)).build());
            builder.addAction(new Notification.Action.Builder(null, "记住", serviceAction(KNOWN, 12)).build());
            builder.addAction(new Notification.Action.Builder(null, "下一个", serviceAction(NEXT, 13)).build());
        }
        return builder.build();
    }
    private PendingIntent serviceAction(String action, int code) {
        Intent intent = new Intent(this, WordService.class).setAction(action);
        intent.putExtra(EXTRA_CARD_TOKEN, cardToken);
        // PendingIntent identity excludes extras. The unique URI prevents FLAG_UPDATE_CURRENT
        // from silently retargeting buttons belonging to older cards.
        intent.setData(Uri.parse("wordflow://card/" + cardToken + "/" + action));
        return PendingIntent.getService(this, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void showForeground(Notification notification) {
        if (foregroundStarted) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
            return;
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
    }
    private void stopSession(boolean pause) {
        active = false;
        sessionActive = false;
        cardToken = null;
        current = null;
        prefs.clearCurrentCard();
        prefs.running(false);
        prefs.paused(pause);
        handler.removeCallbacksAndMessages(null);
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStarted = false;
        stopSelf();
    }
    @Override public void onDestroy() {
        active = false;
        sessionActive = false;
        cardToken = null;
        current = null;
        prefs.clearCurrentCard();
        prefs.running(false);
        handler.removeCallbacksAndMessages(null);
        store.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

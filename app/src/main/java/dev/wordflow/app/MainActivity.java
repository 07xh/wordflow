package dev.wordflow.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BLUE = Color.rgb(23, 104, 223);
    private static final int TEXT = Color.rgb(25, 38, 63);
    private static final int MUTED = Color.rgb(103, 117, 136);
    private static final int BG = Color.rgb(247, 249, 252);
    private static final int REQUEST_FILE = 501;
    private static final int REQUEST_NOTIFY = 502;
    private static final int REQUEST_EXPORT = 503;
    private static final int REQUEST_BACKUP_EXPORT = 504;
    private static final int REQUEST_BACKUP_IMPORT = 505;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WordStore store;
    private WordPrefs prefs;
    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout wordList;
    private int tab = 0;
    private String searchQuery = "";
    private boolean firstResume = true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new WordStore(this);
        prefs = new WordPrefs(this);
        // A killed process must not leave a false "running" indicator behind.
        if (prefs.running() && !WordService.isSessionActive()) {
            prefs.running(false);
            prefs.clearCurrentCard();
        }
        showTab(0);
    }
    @Override protected void onResume() {
        super.onResume();
        if (!firstResume && root != null && tab == 0) showTab(0);
        firstResume = false;
    }
    @Override protected void onDestroy() {
        // Finish queued import/export work before closing its database helper.
        // Closing the helper immediately could race with a background CSV import.
        io.execute(store::close);
        io.shutdown();
        super.onDestroy();
    }

    private void postUi(Runnable operation) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) operation.run();
        });
    }

    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density * value + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private GradientDrawable outline(int color, int radius) {
        GradientDrawable d = shape(color, radius);
        d.setStroke(dp(1), Color.rgb(226, 232, 242));
        return d;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(shape(primary ? BLUE : Color.rgb(234, 242, 255), 12));
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }
    private LinearLayout card() {
        LinearLayout c = vertical();
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(outline(Color.WHITE, 18));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(12);
        content.addView(c, p);
        return c;
    }
    private void spacer(LinearLayout parent, int height) { parent.addView(new View(this), lp(1, dp(height))); }
    private void section(LinearLayout p, String title, String subtitle) {
        p.addView(text(title, 18, TEXT, true));
        if (subtitle != null) { spacer(p, 5); p.addView(text(subtitle, 13, MUTED, false)); }
        spacer(p, 12);
    }
    private void showTab(int nextTab) {
        tab = nextTab;
        root = vertical(); root.setBackgroundColor(BG);
        // Android 15+ enforces edge-to-edge on target 35: keep controls clear of
        // the status/navigation bars without requiring a deprecated opt-out.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                android.graphics.Insets bars = windowInsets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return windowInsets;
            });
        }
        setContentView(root);
        LinearLayout heading = vertical(); heading.setPadding(dp(20), dp(18), dp(20), dp(10));
        heading.addView(text("词序  WordFlow", 25, TEXT, true));
        spacer(heading, 3);
        heading.addView(text("把碎片时间变成真实记忆", 12, MUTED, false));
        root.addView(heading, lp(-1, -2));
        LinearLayout nav = horizontal(); nav.setPadding(dp(16), dp(5), dp(16), dp(12));
        String[] tabs = {"学习", "词库", "设置"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            Button tabButton = button(tabs[i], i == tab);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) params.leftMargin = dp(8);
            nav.addView(tabButton, params);
            tabButton.setOnClickListener(v -> showTab(index));
        }
        root.addView(nav, lp(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        content = vertical(); content.setPadding(dp(16), dp(6), dp(16), dp(32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (tab == 0) buildHome();
        else if (tab == 1) buildWords();
        else buildSettings();
    }
    private void buildHome() {
        LinearLayout stats = card();
        section(stats, "学习概览", "本机离线保存 · 不需要登录");
        LinearLayout line = horizontal();
        line.addView(statItem("总单词", String.valueOf(store.count())), new LinearLayout.LayoutParams(0, dp(74), 1));
        line.addView(statItem("记住中", String.valueOf(store.learnedCount())), new LinearLayout.LayoutParams(0, dp(74), 1));
        line.addView(statItem("待复习", String.valueOf(store.dueCount())), new LinearLayout.LayoutParams(0, dp(74), 1));
        stats.addView(line);

        LinearLayout currentCard = card();
        section(currentCard, "当前单词", prefs.running() ? "与通知展示同步" : "开始学习后显示当前词条");
        String showingEnglish = prefs.currentEnglish();
        if (prefs.running() && !showingEnglish.isEmpty()) {
            currentCard.addView(text(showingEnglish, 21, TEXT, true));
            spacer(currentCard, 5);
            currentCard.addView(text(prefs.currentMeaning(), 15, MUTED, false));
        } else {
            currentCard.addView(text("暂无进行中的单词", 14, MUTED, false));
        }
        spacer(currentCard, 10);
        Button nextCard = button("下一个单词", false);
        nextCard.setEnabled(prefs.running());
        currentCard.addView(nextCard, lp(-1, dp(44)));
        nextCard.setOnClickListener(v -> {
            if (sendAction(WordService.NEXT)) {
                root.postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed() && tab == 0) showTab(0);
                }, 200);
            }
        });

        LinearLayout session = card();
        section(session, "通知背词", prefs.running() ? "学习会话已启动，可在通知中操作" : "主动开启学习，按设定间隔自动换词");
        Button start = button(prefs.running() ? "更新当前学习" : (prefs.paused() ? "继续学习" : "开始通知背词"), true);
        session.addView(start, lp(-1, dp(48)));
        spacer(session, 10);
        Button pause = button("暂停学习", false);
        session.addView(pause, lp(-1, dp(45)));
        spacer(session, 8);
        Button stop = button("结束学习并移除通知", false);
        session.addView(stop, lp(-1, dp(45)));
        start.setOnClickListener(v -> startSession());
        pause.setOnClickListener(v -> { if (!prefs.running()) { toast("当前没有运行中的学习会话"); return; } if (sendAction(WordService.PAUSE)) { prefs.running(false); prefs.paused(true); showTab(0); } });
        stop.setOnClickListener(v -> { if (sendAction(WordService.STOP)) { prefs.running(false); prefs.paused(false); showTab(0); } });
        spacer(session, 12);
        session.addView(text("默认每 " + prefs.seconds() + " 秒轮换 · " + displayName(prefs.display()) + " · " + orderName(prefs.order()), 12, MUTED, false));

        LinearLayout quick = card();
        section(quick, "快速开始", "首次使用先添加单词，或导入演示词库");
        Button importSample = button("导入 10 个示例单词", false);
        quick.addView(importSample, lp(-1, dp(44)));
        importSample.setOnClickListener(v -> importSample());
        spacer(quick, 8);
        Button add = button("添加新单词", false);
        quick.addView(add, lp(-1, dp(44)));
        add.setOnClickListener(v -> editWord(null));

        LinearLayout note = card();
        section(note, "关于当前版本", null);
        note.addView(text("基础通知适用于大部分 Android 设备。星河岛及厂商流体云尚未接入；系统不会保证通知成为灵动岛实时活动。", 13, MUTED, false));
    }
    private LinearLayout statItem(String label, String value) {
        LinearLayout c = vertical();
        c.setGravity(Gravity.CENTER);
        c.setBackground(shape(Color.rgb(242, 247, 255), 13));
        LinearLayout.LayoutParams p = lp(-1, -2);
        TextView n = text(value, 23, BLUE, true); n.setGravity(Gravity.CENTER);
        c.addView(n, p);
        TextView t = text(label, 12, MUTED, false); t.setGravity(Gravity.CENTER);
        c.addView(t);
        return c;
    }
    private void buildWords() {
        LinearLayout actions = card();
        section(actions, "我的单词", "支持直接添加、编辑与批量导入");
        LinearLayout row = horizontal();
        Button add = button("+ 添加", true);
        Button paste = button("粘贴导入", false);
        Button file = button("TXT / CSV", false);
        row.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1f); p.leftMargin=dp(6);
        row.addView(paste, p);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(44), 1f); p2.leftMargin=dp(6);
        row.addView(file, p2);
        actions.addView(row);
        add.setOnClickListener(v -> editWord(null));
        paste.setOnClickListener(v -> pasteDialog());
        file.setOnClickListener(v -> openDocument());
        spacer(actions, 9);
        actions.addView(text("格式：英文 + 制表符 + 中文；也支持 word,meaning 或 word|meaning", 12, MUTED, false));
        spacer(actions, 12);
        EditText search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(15);
        search.setHint("搜索英文或中文释义");
        search.setText(searchQuery);
        actions.addView(search, lp(-1, dp(48)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                searchQuery = s.toString(); fillWordList();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        LinearLayout listCard = card();
        listCard.addView(text("词库内容 · " + store.count() + " 词", 17, TEXT, true));
        spacer(listCard, 6);
        wordList = vertical();
        listCard.addView(wordList);
        fillWordList();
    }
    private void fillWordList() {
        if (wordList == null || tab != 1) return;
        wordList.removeAllViews();
        List<WordStore.Word> words = store.list(searchQuery);
        if (words.isEmpty()) { wordList.addView(text("暂无匹配单词。可点击上方添加或导入。", 13, MUTED, false)); return; }
        for (WordStore.Word w : words) {
            LinearLayout row = horizontal();
            row.setPadding(0, dp(9), 0, dp(9));
            LinearLayout info = vertical();
            info.addView(text(w.english, 16, TEXT, true));
            info.addView(text(w.meaning, 12, MUTED, false));
            if (w.known > 0 || w.failed > 0) {
                info.addView(text("记住 " + w.known + " 次 · 不会 " + w.failed + " 次", 11, MUTED, false));
            }
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            Button edit = button("编辑", false);
            row.addView(edit, lp(dp(70), dp(40)));
            edit.setOnClickListener(v -> editWord(w));
            row.setOnLongClickListener(v -> { deleteDialog(w); return true; });
            wordList.addView(row, lp(-1, -2));
            View border = new View(this); border.setBackgroundColor(Color.rgb(238, 242, 248));
            wordList.addView(border, lp(-1, dp(1)));
        }
        if (words.size() >= 120) wordList.addView(text("为保证页面流畅，仅展示前 120 词。可通过搜索定位更多单词。", 12, MUTED, false));
    }
    private void buildSettings() {
        LinearLayout general = card();
        section(general, "通知设置", "显示模式及间隔会立即更新，切换轮换规则时换下一词");
        TextView period = text("换词间隔：" + prefs.seconds() + " 秒", 15, TEXT, true);
        general.addView(period);
        SeekBar seek = new SeekBar(this);
        seek.setMax(29);
        seek.setProgress(Math.max(0, Math.min(29, prefs.seconds() / 10 - 1)));
        general.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) period.setText("换词间隔：" + (progress + 1) * 10 + " 秒");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                prefs.seconds((sb.getProgress() + 1) * 10); refreshSession();
            }
        });
        spacer(general, 13);
        general.addView(text("显示模式", 15, TEXT, true));
        Spinner display = spinner(new String[]{"英文 + 中文", "仅英文", "仅中文", "先英文后中文"});
        String[] displayKeys = {"both", "english", "chinese", "reveal"};
        general.addView(display, lp(-1, dp(48)));
        int currentDisplay = indexOf(displayKeys, prefs.display());
        display.setSelection(currentDisplay);
        display.setOnItemSelectedListener(new SimpleItemListener(index -> {
            if (!displayKeys[index].equals(prefs.display())) { prefs.display(displayKeys[index]); refreshSession(); }
        }));
        spacer(general, 12);
        general.addView(text("轮换方式", 15, TEXT, true));
        String[] orderKeys = {"sequential", "random", "weak"};
        Spinner order = spinner(new String[]{"顺序轮换", "随机轮换", "难词优先"});
        general.addView(order, lp(-1, dp(48)));
        order.setSelection(indexOf(orderKeys, prefs.order()));
        order.setOnItemSelectedListener(new SimpleItemListener(index -> {
            if (!orderKeys[index].equals(prefs.order())) { prefs.order(orderKeys[index]); rotateSession(); }
        }));
        spacer(general, 12);
        CheckBox review = new CheckBox(this);
        review.setText("仅复习到期单词（不显示未学新词）");
        review.setTextSize(13); review.setTextColor(TEXT);
        review.setChecked(prefs.reviewOnly());
        general.addView(review);
        review.setOnCheckedChangeListener((b, checked) -> { prefs.reviewOnly(checked); rotateSession(); });

        LinearLayout notice = card();
        section(notice, "复习与数据", null);
        notice.addView(text("当前版本采用简化间隔：首次记住后 1 天，再依次 3 / 7 / 14 / 30 天；答错后 10 分钟到期。后续会接入完整 FSRS，当前不宣称已使用 FSRS。", 13, MUTED, false));
        spacer(notice, 13);
        Button export = button("导出词库 CSV", false);
        notice.addView(export, lp(-1, dp(44)));
        export.setOnClickListener(v -> createExportDocument());
        spacer(notice, 6);
        notice.addView(text("CSV 仅包含词条和释义，供词库交换使用，不可用于迁移学习进度。", 12, MUTED, false));
        spacer(notice, 14);
        Button fullBackup = button("完整备份（JSON）", true);
        notice.addView(fullBackup, lp(-1, dp(44)));
        fullBackup.setOnClickListener(v -> createBackupDocument());
        spacer(notice, 8);
        Button restoreBackup = button("从完整备份恢复", false);
        notice.addView(restoreBackup, lp(-1, dp(44)));
        restoreBackup.setOnClickListener(v -> confirmRestore());
        spacer(notice, 6);
        notice.addView(text("完整备份包括全部单词、学习/答错/显示次数、复习到期时间及显示设置。恢复将覆盖现有词库；备份文件未加密，请自行妥善保存。", 12, MUTED, false));
        spacer(notice, 13);
        Button clear = button("清空所有单词（不可撤销）", false);
        notice.addView(clear, lp(-1, dp(44)));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("确认清空词库？")
                .setMessage("将永久删除全部单词和学习记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (d, which) -> {
                    sendAction(WordService.STOP);
                    prefs.running(false); prefs.paused(false);
                    store.clear(); showTab(0);
                }).show());
    }
    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(a);
        return s;
    }
    private int indexOf(String[] values, String x) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(x)) return i;
        return 0;
    }
    private String displayName(String s) {
        switch (s) { case "english": return "仅英文"; case "chinese": return "仅中文"; case "reveal": return "先英后中"; default: return "英中同时"; }
    }
    private String orderName(String s) {
        switch (s) { case "random": return "随机"; case "weak": return "难词优先"; default: return "顺序"; }
    }
    private void startSession() {
        if (store.count() == 0) { toast("请先添加或导入单词"); return; }
        if (prefs.reviewOnly() && store.dueCount() == 0) { toast("暂无到期单词，请关闭‘仅复习到期单词’后再启动"); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFY);
            return;
        }
        if (!notificationsAvailable()) return;
        if (sendAction(WordService.START)) {
            prefs.running(true);
            toast("正在启动学习，请检查通知栏");
            showTab(0);
            root.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed() && tab == 0) showTab(0);
            }, 200);
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_NOTIFY) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startSession();
            else toast("未获得通知权限，请到系统设置中开启后再启动");
        }
    }
    private boolean notificationsAvailable() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean available = manager.areNotificationsEnabled();
        NotificationChannel channel = manager.getNotificationChannel("wordflow_study");
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)
            available = false;
        if (available) return true;
        new AlertDialog.Builder(this)
                .setTitle("通知已关闭")
                .setMessage("通知背词需要允许应用通知和‘单词学习’通知类别，否则系统可能不显示学习内容。")
                .setNegativeButton("取消", null)
                .setPositiveButton("打开通知设置", (dialog, which) -> {
                    Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    settings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(settings);
                }).show();
        return false;
    }
    private boolean sendAction(String action) {
        Intent intent = new Intent(this, WordService.class).setAction(action);
        if (WordService.NEXT.equals(action)) intent.putExtra("ui_control", true);
        try {
            // Only START requires a new foreground-service launch. Refresh/NEXT/STOP
            // may reach a service already killed by the OS; startForegroundService for
            // those commands would trigger ForegroundServiceDidNotStartInTimeException.
            if (WordService.START.equals(action) && Build.VERSION.SDK_INT >= 26)
                startForegroundService(intent);
            else startService(intent);
            return true;
        } catch (RuntimeException e) { toast("无法启动服务：" + e.getClass().getSimpleName()); return false; }
    }
    private void refreshSession() { if (prefs.running()) sendAction(WordService.REFRESH); }
    private void rotateSession() { if (prefs.running()) sendAction(WordService.NEXT); }

    private void createExportDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "WordFlow-words.csv");
        startActivityForResult(intent, REQUEST_EXPORT);
    }
    private void exportTo(Uri uri) {
        io.execute(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new IllegalStateException("无法创建文件");
                // BOM improves Chinese text recognition by common spreadsheet editors.
                byte[] output = ("\uFEFF" + store.exportCsv()).getBytes(StandardCharsets.UTF_8);
                stream.write(output);
                stream.flush();
                postUi(() -> toast("词库已导出（不包含学习进度）"));
            } catch (Exception error) {
                postUi(() -> toast("词库导出失败，请重试"));
            }
        });
    }


    private void createBackupDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "WordFlow-full-backup.json");
        startActivityForResult(intent, REQUEST_BACKUP_EXPORT);
    }
    private void confirmRestore() {
        if (prefs.running() || WordService.isSessionActive()) {
            new AlertDialog.Builder(this).setTitle("请先结束学习")
                    .setMessage("恢复数据前需要结束正在运行的通知学习会话，以免学习记录同时发生变化。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("从完整备份恢复？")
                .setMessage("将替换当前所有单词和复习记录，并恢复学习设置。请先导出当前数据留存备份。无效文件不会清空原数据。")
                .setNegativeButton("取消", null)
                .setPositiveButton("选择备份文件", (dialog, which) -> {
                    Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    open.addCategory(Intent.CATEGORY_OPENABLE);
                    open.setType("*/*");
                    open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "text/plain", "application/octet-stream"});
                    startActivityForResult(open, REQUEST_BACKUP_IMPORT);
                }).show();
    }
    private void exportFullBackup(Uri uri) {
        io.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("无法创建备份文件");
                WordBackup.Snapshot snapshot =
                        new WordBackup.Snapshot(store.exportBackup(), prefs.backupSettings());
                byte[] bytes = WordBackup.encode(snapshot).getBytes(StandardCharsets.UTF_8);
                output.write(bytes);
                output.flush();
                postUi(() -> toast("完整备份成功：" + snapshot.words.size() + " 个词条"));
            } catch (Exception error) {
                postUi(() -> toast("完整备份失败，请检查存储权限和剩余空间"));
            }
        });
    }
    private void restoreFullBackup(Uri uri) {
        io.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("备份文件不可读");
                // Parse and validate the entire input BEFORE starting a destructive DB transaction.
                WordBackup.Snapshot snapshot = WordBackup.decode(readBounded(input, 25_000_000));
                if (prefs.running() || WordService.isSessionActive())
                    throw new IllegalStateException("请先停止当前学习会话");
                List<WordBackup.Entry> previous = store.exportBackup();
                store.replaceFromBackup(snapshot.words);
                if (!prefs.restoreSettings(snapshot.settings)) {
                    store.replaceFromBackup(previous);
                    throw new IllegalStateException("设置保存失败，已尝试恢复原词库");
                }
                postUi(() -> {
                    toast("已恢复 " + snapshot.words.size() + " 个单词及学习记录");
                    showTab(0);
                });
            } catch (Exception error) {
                postUi(() -> toast("恢复失败：备份无效或无法读取，原有数据未被主动清空"));
            }
        });
    }

    private void editWord(WordStore.Word word) {
        LinearLayout fields = vertical();
        fields.setPadding(dp(18), dp(8), dp(18), 0);
        EditText english = new EditText(this); english.setSingleLine(true); english.setHint("英文单词");
        EditText meaning = new EditText(this); meaning.setSingleLine(false); meaning.setMinLines(2); meaning.setHint("中文释义");
        fields.addView(english, lp(-1, dp(54))); fields.addView(meaning, lp(-1, dp(78)));
        if (word != null) { english.setText(word.english); meaning.setText(word.meaning); }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(word == null ? "添加单词" : "编辑单词")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", null);
        if (word != null) builder.setNeutralButton("删除", (d, w) -> deleteDialog(word));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(click -> {
            String en = english.getText().toString().trim();
            String zh = meaning.getText().toString().trim();
            if (en.isEmpty() || zh.isEmpty()) { toast("英文和中文不能留空"); return; }
            if (en.length() > 120 || zh.length() > 1000) { toast("词条过长"); return; }
            try {
                if (word == null) {
                    if (!store.add(en, zh)) { toast("单词已存在"); return; }
                } else store.update(word.id, en, zh);
                dialog.dismiss(); refreshSession(); showTab(tab); toast("已保存");
            } catch (Exception ex) { toast("保存失败：检查单词是否重复"); }
        }));
        dialog.show();
    }
    private void deleteDialog(WordStore.Word w) {
        new AlertDialog.Builder(this).setTitle("删除 " + w.english + "？")
                .setMessage("该词的学习记录也会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> { store.delete(w.id); refreshSession(); showTab(1); })
                .show();
    }
    private void pasteDialog() {
        EditText input = new EditText(this);
        input.setHint("apple\t苹果\nbanana,香蕉");
        input.setMinLines(5); input.setGravity(Gravity.TOP);
        LinearLayout wrap = vertical(); wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input, lp(-1, dp(180)));
        new AlertDialog.Builder(this).setTitle("批量导入")
                .setMessage("每行一词，支持 TAB / 逗号 / 竖线分隔，重复词自动跳过。")
                .setView(wrap).setNegativeButton("取消", null)
                .setPositiveButton("导入", (d, w) -> importText(input.getText().toString()))
                .show();
    }
    private void openDocument() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        open.setType("*/*");
        open.addCategory(Intent.CATEGORY_OPENABLE);
        open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/comma-separated-values", "text/csv", "application/csv", "application/octet-stream"});
        startActivityForResult(open, REQUEST_FILE);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == REQUEST_EXPORT) { exportTo(uri); return; }
        if (request == REQUEST_BACKUP_EXPORT) { exportFullBackup(uri); return; }
        if (request == REQUEST_BACKUP_IMPORT) { restoreFullBackup(uri); return; }
        if (request != REQUEST_FILE) return;
        io.execute(() -> {
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) throw new IllegalStateException("无法读取文件");
                String input = readBounded(stream);
                List<WordParser.Item> items = WordParser.parse(input);
                int count = store.addBatch(items);
                postUi(() -> { toast("导入 " + count + " 词；跳过 " + (items.size() - count) + " 个重复词"); showTab(1); });
            } catch (Exception e) { postUi(() -> toast("读取失败：文件需为 UTF-8 文本，最大 2 MB")); }
        });
    }
    private static String readBounded(InputStream stream) throws Exception {
        return readBounded(stream, 2_000_000);
    }
    private static String readBounded(InputStream stream, int maxBytes) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        while ((count = stream.read(buffer)) != -1) {
            if (bytes.size() + count > maxBytes) throw new IllegalArgumentException("文件太大");
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }
    private void importText(String text) {
        io.execute(() -> {
            List<WordParser.Item> items = WordParser.parse(text);
            int inserted = store.addBatch(items);
            postUi(() -> { toast("成功导入 " + inserted + " 词"); showTab(1); });
        });
    }
    private void importSample() {
        io.execute(() -> {
            try (InputStream is = getAssets().open("sample_words.txt")) {
                String contents = readBounded(is);
                int n = store.addBatch(WordParser.parse(contents));
                postUi(() -> { toast("导入 " + n + " 个示例单词"); showTab(0); });
            } catch (Exception e) { postUi(() -> toast("无法导入示例词库")); }
        });
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private static final class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void onSelect(int position); }
        private final Callback callback;
        SimpleItemListener(Callback callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { callback.onSelect(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}

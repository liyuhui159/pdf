package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Actual Android DownloadManager queue, not a demo/mockup.
 * System DownloadManager has no public pause/resume API; this screen only
 * exposes operations that work: open, retry from start, cancel, and delete. */
public class DownloadManagerActivity extends Activity {
    private final int BLUE = Color.rgb(47, 107, 255);
    private final int INK = Color.rgb(32, 44, 67);
    private final int MUTED = Color.rgb(101, 114, 134);
    private final int OUTLINE = Color.rgb(220, 229, 241);
    private final int BG = Color.rgb(245, 248, 253);
    private final int GREEN = Color.rgb(26, 136, 83);
    private final int RED = Color.rgb(190, 65, 66);
    private final int ORANGE = Color.rgb(177, 114, 34);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout results;
    private LinearLayout filters;
    private TextView countLabel;
    private ScrollView scroll;
    private DownloadManager downloader;
    private boolean active;
    private int currentFilter = 0;
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (!active || isFinishing()) return;
            renderItems();
            handler.postDelayed(this, 2500);
        }
    };

    private static final class Task {
        long id, done, total;
        int status, reason;
        String title, mime;
        DownloadHistory.Entry history;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable shape(int fill, int stroke, int corner) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(corner));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setPadding(dp(9), 0, dp(9), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(shape(primary ? BLUE : Color.WHITE, primary ? 0 : OUTLINE, 11));
        return b;
    }

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        downloader = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        buildLayout();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        active = false;
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildLayout() {
        LinearLayout root = col();
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setPadding(dp(17), dp(15), dp(17), dp(14));
        header.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(12, 84, 224), Color.rgb(22, 167, 244)});
        header.setBackground(gradient);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_book_search);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(60), dp(60));
        ilp.setMargins(0, 0, dp(12), 0);
        header.addView(logo, ilp);
        LinearLayout hd = col();
        TextView h = label("下载管理", 24, Color.WHITE, true);
        hd.addView(h);
        countLabel = label("读取下载任务中…", 12, Color.WHITE, false);
        countLabel.setPadding(0, dp(6), 0, 0);
        hd.addView(countLabel);
        header.addView(hd);
        root.addView(header);

        TextView description = label("下载任务保存在系统下载器中，退出软件也可以继续下载。", 12, MUTED, false);
        description.setPadding(dp(17), dp(12), dp(15), dp(6));
        root.addView(description);

        filters = new LinearLayout(this);
        filters.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(filters);
        renderTabs();

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        results = col();
        results.setPadding(dp(13), dp(2), dp(13), dp(12));
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(12), dp(9), dp(12), dp(10));
        footer.setBackground(shape(Color.WHITE, OUTLINE, 0));
        Button back = button("⌕  返回搜索", false);
        back.setOnClickListener(v -> finish());
        Button current = button("↓  下载管理", true);
        current.setEnabled(false);
        footer.addView(back, new LinearLayout.LayoutParams(0, dp(49), 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(49), 1);
        second.setMargins(dp(8), 0, 0, 0);
        footer.addView(current, second);
        root.addView(footer);
        setContentView(root);
    }

    private void renderTabs() {
        filters.removeAllViews();
        String[] names = {"全部", "下载中", "已完成", "失败"};
        for (int i = 0; i < names.length; i++) {
            final int choice = i;
            Button b = button(names[i], i == currentFilter);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(43), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            filters.addView(b, lp);
            b.setOnClickListener(v -> {
                currentFilter = choice;
                renderTabs();
                scroll.smoothScrollTo(0, 0);
                renderItems();
            });
        }
    }

    private Task read(DownloadHistory.Entry entry) {
        Task t = new Task();
        t.history = entry;
        t.id = entry.id;
        t.title = entry.name;
        t.mime = entry.mime;
        t.status = -1;
        if (downloader == null) return t;
        try (Cursor cursor = downloader.query(new DownloadManager.Query().setFilterById(entry.id))) {
            if (cursor != null && cursor.moveToFirst()) {
                t.status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                t.reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                t.total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                t.done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int nameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                if (nameIndex >= 0) {
                    String actualName = cursor.getString(nameIndex);
                    if (!TextUtils.isEmpty(actualName)) t.title = actualName;
                }
                int mimeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                if (mimeIndex >= 0 && !TextUtils.isEmpty(cursor.getString(mimeIndex)))
                    t.mime = cursor.getString(mimeIndex);
            }
        } catch (Exception ex) {
            t.status = -1;
        }
        return t;
    }

    private boolean matches(Task t) {
        if (currentFilter == 0) return true;
        if (currentFilter == 2) return t.status == DownloadManager.STATUS_SUCCESSFUL;
        if (currentFilter == 3) return t.status == DownloadManager.STATUS_FAILED || t.status == -1;
        return t.status == DownloadManager.STATUS_PENDING
                || t.status == DownloadManager.STATUS_RUNNING
                || t.status == DownloadManager.STATUS_PAUSED;
    }

    private static String readable(long n) {
        if (n < 0) return "未知";
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", n / 1024f);
        if (n < 1024L * 1024L * 1024L)
            return String.format(Locale.ROOT, "%.1f MB", n / (1024f * 1024f));
        return String.format(Locale.ROOT, "%.2f GB", n / (1024f * 1024f * 1024f));
    }

    private String state(Task t) {
        switch (t.status) {
            case DownloadManager.STATUS_PENDING: return "等待中";
            case DownloadManager.STATUS_RUNNING: return "下载中";
            case DownloadManager.STATUS_PAUSED: return "系统暂停";
            case DownloadManager.STATUS_SUCCESSFUL: return "已完成";
            case DownloadManager.STATUS_FAILED: return "下载失败";
            default: return "记录已失效";
        }
    }

    private int stateColor(Task t) {
        if (t.status == DownloadManager.STATUS_SUCCESSFUL) return GREEN;
        if (t.status == DownloadManager.STATUS_FAILED || t.status == -1) return RED;
        if (t.status == DownloadManager.STATUS_PAUSED) return ORANGE;
        return BLUE;
    }

    private void renderItems() {
        ArrayList<DownloadHistory.Entry> history = DownloadHistory.all(this);
        ArrayList<Task> visible = new ArrayList<>();
        int ongoing = 0, completed = 0, failed = 0;
        for (DownloadHistory.Entry item : history) {
            Task t = read(item);
            if (t.status == DownloadManager.STATUS_SUCCESSFUL) completed++;
            else if (t.status == DownloadManager.STATUS_FAILED || t.status == -1) failed++;
            else ongoing++;
            if (matches(t)) visible.add(t);
        }
        countLabel.setText("共 " + history.size() + " 项  ·  下载中 " + ongoing
                + "  ·  完成 " + completed + "  ·  失败 " + failed);

        int y = scroll.getScrollY();
        results.removeAllViews();
        if (visible.isEmpty()) {
            TextView empty = label(history.isEmpty()
                    ? "暂无下载任务。\n返回搜索界面，点击“解析并下载”后会自动添加到这里。"
                    : "该分类目前没有任务。", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(60), dp(10), dp(60));
            results.addView(empty);
        }
        for (Task t : visible) renderCard(t);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void renderCard(Task t) {
        LinearLayout card = col();
        card.setBackground(shape(Color.WHITE, OUTLINE, 15));
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(10));
        results.addView(card, cp);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading);
        TextView file = label(t.title == null ? "未命名文件" : t.title, 15, INK, true);
        file.setMaxLines(2);
        file.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(file, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = label(state(t), 12, stateColor(t), true);
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        status.setBackground(shape(Color.rgb(242, 247, 253), 0, 10));
        heading.addView(status);

        String extension = "文件";
        if (t.title != null && t.title.contains("."))
            extension = t.title.substring(t.title.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        TextView meta = label(extension + "   ·   " + readable(t.total)
                + (t.history.url.isEmpty() ? "" : "   ·   " + Uri.parse(t.history.url).getHost()),
                12, MUTED, false);
        meta.setPadding(0, dp(6), 0, dp(5));
        card.addView(meta);

        if (t.status == DownloadManager.STATUS_RUNNING
                || t.status == DownloadManager.STATUS_PENDING
                || t.status == DownloadManager.STATUS_PAUSED) {
            int percent = t.total > 0 ? (int) Math.min(100, 100 * t.done / t.total) : -1;
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(percent >= 0 ? percent : 0);
            progress.setIndeterminate(percent < 0);
            progress.setProgressTintList(ColorStateList.valueOf(BLUE));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(221, 233, 248)));
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(6));
            pp.setMargins(0, dp(9), 0, dp(8));
            card.addView(progress, pp);
            TextView stat = label(readable(t.done) + " / " + readable(t.total)
                    + (percent >= 0 ? "   ·   " + percent + "%" : "")
                    + (t.status == DownloadManager.STATUS_PAUSED
                    ? "   ·   原因代码 " + t.reason : ""), 12, MUTED, false);
            card.addView(stat);
        } else if (t.status == DownloadManager.STATUS_FAILED) {
            TextView failure = label("下载失败，系统原因代码：" + t.reason
                    + "\n网络错误、链接过期或网站限制均可能导致失败。", 12, RED, false);
            failure.setPadding(0, dp(6), 0, dp(5));
            card.addView(failure);
        } else if (t.status == -1) {
            TextView missing = label("系统下载器找不到该任务，可从列表移除记录。", 12, RED, false);
            card.addView(missing);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, dp(43));
        row.setMargins(0, dp(12), 0, 0);
        card.addView(actions, row);

        Button remove = button("删除", false);
        actions.addView(remove, new LinearLayout.LayoutParams(0, -1, 1));
        remove.setOnClickListener(v -> confirmDelete(t));

        Button primary;
        if (t.status == DownloadManager.STATUS_SUCCESSFUL) {
            primary = button("打开文件", true);
            primary.setOnClickListener(v -> openDownloaded(t));
        } else if (t.status == DownloadManager.STATUS_FAILED) {
            primary = button("重新下载", true);
            primary.setOnClickListener(v -> retry(t));
        } else if (t.status == -1) {
            primary = button("移除记录", true);
            primary.setOnClickListener(v -> {
                DownloadHistory.remove(this, t.id);
                renderItems();
            });
        } else {
            primary = button("取消任务", true);
            primary.setOnClickListener(v -> confirmDelete(t));
        }
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -1, 1);
        second.setMargins(dp(9), 0, 0, 0);
        actions.addView(primary, second);
    }

    private void openDownloaded(Task t) {
        if (downloader == null) return;
        try {
            Uri uri = downloader.getUriForDownloadedFile(t.id);
            if (uri == null) {
                toast("无法获取文件，请打开系统下载目录查看");
                return;
            }
            String mime = t.mime == null || t.mime.isEmpty()
                    ? "*/*" : t.mime;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "选择阅读软件"));
        } catch (Exception e) {
            toast("没有找到适合打开该文件的应用：" + e.getMessage());
        }
    }

    private void retry(Task t) {
        if (!LinkResolver.webUrl(t.history.url)) {
            toast("下载来源地址无效，无法重试");
            return;
        }
        try {
            // Recreates a fresh DownloadManager task. Android does not offer
            // a public method to resume a failed task from its partial bytes.
            LinkResolver.FileLink link = new LinkResolver.FileLink(
                    t.history.url, t.history.name, t.history.mime, t.history.referer);
            long newId = DownloadJobs.enqueue(this, link, t.history.agent);
            DownloadHistory.remove(this, t.id);
            if (downloader != null) downloader.remove(t.id);
            toast("已重新提交下载任务 #" + newId);
            currentFilter = 0;
            renderTabs();
            renderItems();
        } catch (Exception ex) {
            toast("重试失败：" + ex.getMessage());
        }
    }

    private void confirmDelete(Task t) {
        String detail = t.status == DownloadManager.STATUS_SUCCESSFUL
                ? "删除该任务可能同时删除已下载的文件。确定删除？"
                : "确定取消并删除该下载任务吗？";
        new AlertDialog.Builder(this)
                .setTitle("删除下载任务")
                .setMessage(detail)
                .setNegativeButton("返回", null)
                .setPositiveButton("确定删除", (d, which) -> {
                    try {
                        if (downloader != null) downloader.remove(t.id);
                        DownloadHistory.remove(this, t.id);
                        renderItems();
                        toast("任务已删除");
                    } catch (Exception e) {
                        toast("删除失败：" + e.getMessage());
                    }
                }).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

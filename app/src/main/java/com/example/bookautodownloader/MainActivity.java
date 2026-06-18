package com.example.bookautodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText bookInput;
    private EditText templatesInput;
    private EditText extInput;
    private CheckBox autoDownloadCheck;
    private LinearLayout resultBox;
    private TextView logView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String PREF = "book_downloader_pref_v4";
    private static final String DEFAULT_TEMPLATES = "https://example.com/search?q={keyword}";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        loadSettings();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F7FB"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(root);

        LinearLayout header = card();
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("书名自动检索下载助手");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#1F2A44"));
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = new TextView(this);
        tip.setText("一个APP内完成：多资源站搜索、顺序优先搜索、网页登录分析下载链接。仅用于自有、授权、公版或允许分发的资料源。");
        tip.setTextSize(13);
        tip.setTextColor(Color.parseColor("#5B6475"));
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(8), 0, dp(8));
        header.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        Button webAnalyzer = primaryButton("进入网页登录分析器（适合需要登录的网站）");
        webAnalyzer.setOnClickListener(v -> startActivity(new Intent(this, WebAnalyzeActivity.class)));
        header.addView(webAnalyzer, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header);

        LinearLayout searchCard = card();
        addLabel(searchCard, "书名 / 资料名");
        bookInput = input("例如：西门子 S7-1200 PLC 入门教程", 2);
        searchCard.addView(bookInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(8), 0, 0);
        searchCard.addView(row1);
        Button read = secondaryButton("读取剪贴板");
        read.setOnClickListener(v -> readClipboard(false));
        row1.addView(read, weight(1));
        Button readSearch = primaryButton("读取并搜索全部");
        readSearch.setOnClickListener(v -> readClipboard(true));
        row1.addView(readSearch, weight(1));
        root.addView(searchCard);

        LinearLayout sourceCard = card();
        addLabel(sourceCard, "资源站搜索网址模板，可添加多个");
        TextView sourceTip = new TextView(this);
        sourceTip.setText("每行一个模板，必须包含 {keyword}。保存后下次自动保留。\n例如：https://example.com/search?q={keyword}");
        sourceTip.setTextSize(12);
        sourceTip.setTextColor(Color.parseColor("#697386"));
        sourceTip.setPadding(0, 0, 0, dp(6));
        sourceCard.addView(sourceTip);

        templatesInput = input("https://site1.com/search?q={keyword}\nhttps://site2.com/s/{keyword}", 5);
        sourceCard.addView(templatesInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        sourceCard.addView(row2);
        Button save = secondaryButton("保存模板");
        save.setOnClickListener(v -> saveSettings());
        row2.addView(save, weight(1));
        Button openAll = secondaryButton("打开全部搜索页");
        openAll.setOnClickListener(v -> openAllSearchPages());
        row2.addView(openAll, weight(1));
        root.addView(sourceCard);

        LinearLayout optionCard = card();
        addLabel(optionCard, "搜索和下载设置");
        TextView extTip = new TextView(this);
        extTip.setText("允许识别的文件类型/关键词：");
        extTip.setTextSize(12);
        extTip.setTextColor(Color.parseColor("#697386"));
        optionCard.addView(extTip);
        extInput = input("pdf,zip,rar,7z", 1);
        optionCard.addView(extInput, new LinearLayout.LayoutParams(-1, -2));
        autoDownloadCheck = new CheckBox(this);
        autoDownloadCheck.setText("搜索后自动下载最高匹配结果（建议测试稳定后再开启）");
        autoDownloadCheck.setTextColor(Color.parseColor("#263248"));
        optionCard.addView(autoDownloadCheck);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);
        optionCard.addView(row3);
        Button searchAll = primaryButton("同时搜索全部");
        searchAll.setOnClickListener(v -> searchAll(false));
        row3.addView(searchAll, weight(1));
        Button searchPriority = primaryButton("按顺序优先搜索");
        searchPriority.setOnClickListener(v -> searchAll(true));
        row3.addView(searchPriority, weight(1));
        root.addView(optionCard);

        LinearLayout resultsCard = card();
        addLabel(resultsCard, "搜索结果");
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        TextView empty = new TextView(this);
        empty.setText("还没有搜索结果。先填写书名和模板，然后选择搜索方式。需要登录的网站请使用顶部的网页登录分析器。");
        empty.setTextColor(Color.parseColor("#697386"));
        resultBox.addView(empty);
        resultsCard.addView(resultBox, new LinearLayout.LayoutParams(-1, -2));
        root.addView(resultsCard);

        LinearLayout logCard = card();
        addLabel(logCard, "运行日志");
        logView = new TextView(this);
        logView.setTextSize(12);
        logView.setTextColor(Color.parseColor("#39445A"));
        logCard.addView(logView, new LinearLayout.LayoutParams(-1, -2));
        root.addView(logCard);

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#E6ECF4"));
        box.setBackground(bg);
        return box;
    }

    private void addLabel(LinearLayout root, String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.parseColor("#263248"));
        v.setPadding(0, dp(4), 0, dp(6));
        root.addView(v);
    }

    private EditText input(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setTextSize(16);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setTextColor(Color.parseColor("#151B2D"));
        e.setHintTextColor(Color.parseColor("#9AA4B5"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F9FBFE"));
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor("#D8E0EC"));
        e.setBackground(bg);
        return e;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#2F6BFF"));
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#2F6BFF"));
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#EEF3FF"));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.parseColor("#C9D8FF"));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams weight(int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, w);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        templatesInput.setText(sp.getString("templates", DEFAULT_TEMPLATES));
        extInput.setText(sp.getString("ext", "pdf,zip,rar,7z"));
        autoDownloadCheck.setChecked(sp.getBoolean("auto", false));
    }

    private void saveSettings() {
        String cleaned = joinTemplates(getTemplates(false));
        if (cleaned.length() == 0) cleaned = templatesInput.getText().toString().trim();
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString("templates", cleaned)
                .putString("ext", extInput.getText().toString().trim())
                .putBoolean("auto", autoDownloadCheck.isChecked())
                .apply();
        templatesInput.setText(cleaned);
        toast("已保存模板和设置");
    }

    private ArrayList<Source> getTemplates(boolean showToast) {
        String[] lines = templatesInput.getText().toString().split("\\n");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : lines) {
            String t = normalizeTemplate(line);
            if (t.length() == 0) continue;
            unique.add(t);
        }
        ArrayList<Source> list = new ArrayList<>();
        int index = 1;
        for (String t : unique) {
            if (!t.contains("{keyword}") && !t.contains("{keyword_raw}")) {
                if (showToast) toast("第 " + index + " 个模板缺少 {keyword}");
                index++;
                continue;
            }
            list.add(new Source("站点" + index, t));
            index++;
        }
        return list;
    }

    private String normalizeTemplate(String s) {
        if (s == null) return "";
        return s.trim()
                .replace("｛", "{")
                .replace("｝", "}")
                .replace("{ keyword }", "{keyword}")
                .replace("{关键词}", "{keyword}")
                .replace("{书名}", "{keyword}");
    }

    private String joinTemplates(ArrayList<Source> sources) {
        StringBuilder sb = new StringBuilder();
        for (Source s : sources) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.template);
        }
        return sb.toString();
    }

    private void readClipboard(boolean doSearch) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            toast("剪贴板为空");
            return;
        }
        ClipData data = cm.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) {
            toast("剪贴板为空");
            return;
        }
        CharSequence text = data.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(text)) {
            toast("没有读到文字");
            return;
        }
        bookInput.setText(text.toString().trim());
        log("读取剪贴板：" + text.toString().trim());
        if (doSearch) searchAll(false);
    }

    private String makeSearchUrl(String template) throws Exception {
        String keyword = bookInput.getText().toString().trim();
        if (keyword.length() == 0) throw new Exception("请先输入或读取书名");
        if (!template.contains("{keyword}") && !template.contains("{keyword_raw}")) throw new Exception("网址模板必须包含 {keyword}");
        String enc = URLEncoder.encode(keyword, "UTF-8");
        return template.replace("{keyword_raw}", keyword).replace("{keyword}", enc);
    }

    private void openAllSearchPages() {
        saveSettings();
        ArrayList<Source> sources = getTemplates(true);
        if (sources.isEmpty()) {
            toast("请先添加至少一个正确模板");
            return;
        }
        for (Source s : sources) {
            try {
                String url = makeSearchUrl(s.template);
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                log("打开搜索页：" + url);
            } catch (Exception e) {
                toast(e.getMessage());
                return;
            }
        }
    }

    private void searchAll(boolean priority) {
        saveSettings();
        resultBox.removeAllViews();
        TextView running = new TextView(this);
        running.setText(priority ? "正在按顺序优先搜索..." : "正在同时搜索全部资源站...");
        running.setTextColor(Color.parseColor("#697386"));
        resultBox.addView(running);

        String keyword = bookInput.getText().toString().trim();
        String exts = extInput.getText().toString().trim();
        ArrayList<Source> sources = getTemplates(true);
        if (keyword.length() == 0) {
            toast("请先输入或读取书名");
            return;
        }
        if (sources.isEmpty()) {
            toast("请先添加至少一个正确模板");
            return;
        }
        log(priority ? "开始按顺序优先搜索，共 " + sources.size() + " 个模板" : "开始同时搜索，共 " + sources.size() + " 个模板");

        new Thread(() -> {
            ArrayList<Result> all = new ArrayList<>();
            if (priority) {
                for (Source src : sources) {
                    try {
                        ArrayList<Result> part = searchOne(src, keyword, exts);
                        all.addAll(part);
                        if (!part.isEmpty()) {
                            logSafe(src.name + " 已找到结果，停止后续模板");
                            break;
                        }
                    } catch (Exception e) {
                        logSafe(src.name + " 搜索失败：" + e.getMessage());
                    }
                }
                Collections.sort(all, (a, b) -> b.score - a.score);
                ui.post(() -> showResults(all));
            } else {
                ArrayList<Result> sync = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(sources.size());
                for (Source src : sources) {
                    new Thread(() -> {
                        try {
                            ArrayList<Result> part = searchOne(src, keyword, exts);
                            synchronized (sync) { sync.addAll(part); }
                        } catch (Exception e) {
                            logSafe(src.name + " 搜索失败：" + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }).start();
                }
                try { latch.await(); } catch (InterruptedException ignored) {}
                synchronized (sync) { all.addAll(sync); }
                Collections.sort(all, (a, b) -> b.score - a.score);
                ui.post(() -> showResults(all));
            }
        }).start();
    }

    private ArrayList<Result> searchOne(Source source, String keyword, String exts) throws Exception {
        String url = makeSearchUrl(source.template);
        logSafe(source.name + " 搜索：" + url);
        String html = httpGet(url);
        ArrayList<Result> results = parseLinks(html, url, keyword, exts, source.name);
        logSafe(source.name + " 候选链接：" + results.size() + " 个");
        return results;
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(22000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android BookAutoDownloader");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String charset = "UTF-8";
        String ct = c.getContentType();
        if (ct != null) {
            Matcher m = Pattern.compile("charset=([^;]+)", Pattern.CASE_INSENSITIVE).matcher(ct);
            if (m.find()) charset = m.group(1).trim();
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(in, charset));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && sb.length() < 900000) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private ArrayList<Result> parseLinks(String html, String baseUrl, String keyword, String extText, String sourceName) {
        ArrayList<Result> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Pattern p = Pattern.compile("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                String href = m.group(1).trim();
                String title = strip(m.group(2));
                String abs = new URL(new URL(baseUrl), href).toString();
                if (seen.contains(abs)) continue;
                if (!isCandidate(abs, title, extText)) continue;
                int score = score(keyword, title + " " + abs);
                if (score < 15 && !looksFile(abs, extText)) continue;
                if (title.length() == 0) title = guessName(abs);
                list.add(new Result(sourceName, title, abs, score));
                seen.add(abs);
            } catch (Exception ignored) {}
        }
        Collections.sort(list, (a, b) -> b.score - a.score);
        if (list.size() > 20) return new ArrayList<>(list.subList(0, 20));
        return list;
    }

    private boolean isCandidate(String url, String title, String extText) {
        String s = (url + " " + title).toLowerCase(Locale.ROOT);
        if (s.contains("download") || s.contains("下载") || s.contains("attachment") || s.contains("file")) return true;
        return looksFile(url, extText) || looksFile(title, extText);
    }

    private boolean looksFile(String s, String extText) {
        String lower = s == null ? "" : s.toLowerCase(Locale.ROOT);
        String[] arr = extText.split(",");
        for (String e : arr) {
            e = e.trim().toLowerCase(Locale.ROOT);
            if (e.length() > 0 && lower.contains("." + e)) return true;
        }
        return false;
    }

    private String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private int score(String key, String cand) {
        String a = norm(key), b = norm(cand);
        if (a.length() == 0 || b.length() == 0) return 0;
        int hit = b.contains(a) ? 45 : 0;
        String[] tokens = a.split(" ");
        int n = 0;
        for (String t : tokens) if (t.length() >= 2 && b.contains(t)) n++;
        hit += tokens.length == 0 ? 0 : (30 * n / tokens.length);
        return Math.min(100, hit + (int)(dice(a, b) * 45));
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("[《》【】\\[\\]（）(){}<>.,，。:：;；!！?？_\\-]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private double dice(String a, String b) {
        Set<String> aa = grams(a.replace(" ", ""));
        Set<String> bb = grams(b.replace(" ", ""));
        if (aa.isEmpty() || bb.isEmpty()) return 0;
        int inter = 0;
        for (String x : aa) if (bb.contains(x)) inter++;
        return 2.0 * inter / (aa.size() + bb.size());
    }

    private Set<String> grams(String s) {
        Set<String> r = new HashSet<>();
        if (s.length() <= 1) { if (s.length() == 1) r.add(s); return r; }
        for (int i = 0; i < s.length() - 1; i++) r.add(s.substring(i, i + 2));
        return r;
    }

    private void showResults(ArrayList<Result> results) {
        resultBox.removeAllViews();
        log("最终候选链接：" + results.size() + " 个");
        if (results.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("没有找到可识别的下载链接。可以点“打开全部搜索页”手动处理，或使用顶部的网页登录分析器登录后分析当前页。");
            none.setTextColor(Color.parseColor("#697386"));
            resultBox.addView(none);
            return;
        }
        int shown = 0;
        Set<String> seen = new HashSet<>();
        ArrayList<Result> unique = new ArrayList<>();
        for (Result r : results) if (seen.add(r.url)) unique.add(r);
        for (Result r : unique) {
            if (shown++ >= 25) break;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            clp.setMargins(0, 0, 0, dp(8));
            card.setLayoutParams(clp);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F9FBFE"));
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), Color.parseColor("#D8E0EC"));
            card.setBackground(bg);

            TextView tv = new TextView(this);
            tv.setText("来源：" + r.source + "   匹配度：" + r.score + "%\n" + r.title + "\n" + r.url);
            tv.setTextColor(Color.parseColor("#263248"));
            tv.setTextSize(13);
            card.addView(tv);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, 0);
            Button down = primaryButton("下载");
            down.setOnClickListener(v -> download(r));
            row.addView(down, weight(1));
            Button open = secondaryButton("打开");
            open.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))));
            row.addView(open, weight(1));
            card.addView(row);
            resultBox.addView(card);
        }
        if (autoDownloadCheck.isChecked() && !unique.isEmpty() && unique.get(0).score >= 55) download(unique.get(0));
    }

    private void download(Result r) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(r.url));
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = safeName(guessName(r.url));
            req.setTitle(fileName);
            req.setDescription("书名自动检索下载助手");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            dm.enqueue(req);
            toast("已开始下载到 Download 文件夹");
            log("下载：" + r.url);
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
        }
    }

    private String guessName(String url) {
        try {
            String p = Uri.parse(url).getLastPathSegment();
            if (p != null && p.length() > 0) return Uri.decode(p);
        } catch (Exception ignored) {}
        return "download_file.pdf";
    }

    private String safeName(String s) {
        s = s == null ? "download_file" : s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.length() == 0) s = "download_file";
        if (!s.contains(".")) s += ".pdf";
        return s.length() > 90 ? s.substring(0, 90) : s;
    }

    private void logSafe(String s) { ui.post(() -> log(s)); }

    private void log(String s) {
        String old = logView == null ? "" : logView.getText().toString();
        if (old.length() > 4000) old = old.substring(0, 2500);
        logView.setText(s + "\n" + old);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static class Source {
        String name; String template;
        Source(String n, String t) { name = n; template = t; }
    }

    private static class Result {
        String source; String title; String url; int score;
        Result(String src, String t, String u, int s) { source = src; title = t; url = u; score = s; }
    }
}

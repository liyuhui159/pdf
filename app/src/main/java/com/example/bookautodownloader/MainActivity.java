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
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
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
    private TextView sourceSummary;
    private final ArrayList<SourceManager.Site> sites = new ArrayList<>();
    private EditText extInput;
    private CheckBox autoDownloadCheck;
    private LinearLayout resultBox;
    private TextView logView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String PREF = "book_downloader_pref_v4";
    private static final String DEFAULT_TEMPLATES = "https://gutendex.com/books?search={keyword}\nhttps://www.gutenberg.org/ebooks/search/?query={keyword}";

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
        webAnalyzer.setOnClickListener(v -> {
            Intent intent = new Intent(this, WebAnalyzeActivity.class);
            intent.putExtra("keyword", bookInput == null ? "" : bookInput.getText().toString().trim());
            startActivity(intent);
        });
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
        addLabel(sourceCard, "搜索网站（独立编号，可勾选）");
        TextView sourceTip = new TextView(this);
        sourceTip.setText("点击“管理搜索网站”，可勾选、验证、编辑或添加网站。验证会测试网站及书名搜索，不会自动下载。");
        sourceTip.setTextSize(12);
        sourceTip.setTextColor(Color.parseColor("#697386"));
        sourceTip.setPadding(0, 0, 0, dp(6));
        sourceCard.addView(sourceTip);

        sourceSummary = new TextView(this);
        sourceSummary.setTextSize(14);
        sourceSummary.setTextColor(Color.parseColor("#263248"));
        sourceSummary.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable srcBg = new GradientDrawable();
        srcBg.setColor(Color.parseColor("#F9FBFE"));
        srcBg.setCornerRadius(dp(9));
        srcBg.setStroke(dp(1), Color.parseColor("#D8E0EC"));
        sourceSummary.setBackground(srcBg);
        sourceCard.addView(sourceSummary, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        sourceCard.addView(row2);
        Button manage = primaryButton("管理搜索网站");
        manage.setOnClickListener(v -> SourceManager.open(this, sites,
                bookInput.getText().toString().trim(), updated -> {
            sites.clear();
            sites.addAll(updated);
            saveSettings();
            updateSourceSummary();
            toast("网站列表已保存");
        }));
        row2.addView(manage, weight(1));
        Button openAll = secondaryButton("打开已选网站");
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
        extInput = input("pdf,epub,mobi,txt,zip,rar,7z", 1);
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
        addLabel(resultsCard, "搜索结果（仅来自已勾选网站）");
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        TextView empty = new TextView(this);
        empty.setText("还没有搜索结果。先填写书名，在“管理网站”中勾选要搜索的站点。需要登录的网站可用顶部的网页登录分析器。");
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
        sites.clear();
        sites.addAll(SourceManager.load(this, DEFAULT_TEMPLATES));
        updateSourceSummary();
        String extSaved = sp.getString("ext", "pdf,epub,mobi,txt,zip,rar,7z");
        if ("pdf,zip,rar,7z".equals(extSaved.trim())) extSaved = "pdf,epub,mobi,txt,zip,rar,7z";
        extInput.setText(extSaved);
        autoDownloadCheck.setChecked(sp.getBoolean("auto", false));
    }

    private void updateSourceSummary() {
        if (sourceSummary != null) sourceSummary.setText(SourceManager.summary(sites));
    }

    private void saveSettings() {
        SourceManager.save(this, sites);
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString("ext", extInput.getText().toString().trim())
                .putBoolean("auto", autoDownloadCheck.isChecked())
                .apply();
    }

    private ArrayList<Source> getTemplates(boolean showToast) {
        ArrayList<Source> list = new ArrayList<>();
        for (SourceManager.Site s : SourceManager.selected(sites)) {
            if (!(s.template.contains("{keyword}") || s.template.contains("{keyword_raw}"))) continue;
            String siteName = "#" + String.format(Locale.ROOT, "%02d", s.id) + " " + s.name;
            list.add(new Source(siteName, s.template));
        }
        if (list.isEmpty() && showToast) toast("未勾选任何有效网站，请点“管理网站”选择");
        return list;
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
        return makeSearchUrl(template, bookInput.getText().toString().trim());
    }

    private String makeSearchUrl(String template, String keyword) throws Exception {
        if (keyword.length() == 0) throw new Exception("请先输入或读取书名");
        if (!template.contains("{keyword}") && !template.contains("{keyword_raw}"))
            throw new Exception("网址模板必须包含 {keyword}");
        return template.replace("{keyword_raw}", keyword)
                .replace("{keyword}", URLEncoder.encode(keyword, "UTF-8"));
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
        String url = makeSearchUrl(source.template, keyword);
        logSafe(source.name + " 搜索：" + url);
        String response = httpGet(url);
        ArrayList<Result> results;
        if (url.startsWith("https://gutendex.com/books") || url.startsWith("http://gutendex.com/books")) {
            results = parseGutendex(response, source.name, keyword, exts);
        } else {
            results = parseLinks(response, url, keyword, exts, source.name);
        }
        logSafe(source.name + " 候选链接：" + results.size() + " 个");
        return results;
    }

    /** Gutendex gives actual public-domain file URLs instead of HTML search cards. */
    private ArrayList<Result> parseGutendex(String json, String source, String keyword, String exts) throws Exception {
        ArrayList<Result> out = new ArrayList<>();
        JSONArray books = new JSONObject(json).optJSONArray("results");
        if (books == null) throw new Exception("资源站没有返回有效的书籍列表");
        for (int i = 0; i < books.length() && i < 30; i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            String title = book.optString("title", "");
            JSONObject formats = book.optJSONObject("formats");
            if (formats == null) continue;
            Iterator<String> keys = formats.keys();
            ArrayList<String> urls = new ArrayList<>();
            while (keys.hasNext()) {
                String formatKey = keys.next();
                String mime = formatKey.toLowerCase(Locale.ROOT);
                String file = formats.optString(formatKey, "");
                if (!LinkResolver.webUrl(file)) continue;
                String ext = mime.contains("application/epub") ? "epub"
                        : mime.contains("application/pdf") ? "pdf"
                        : mime.startsWith("text/plain") ? "txt"
                        : mime.contains("application/zip") ? "zip" : "";
                if (!ext.isEmpty() && (exts.trim().isEmpty()
                        || ("," + exts.toLowerCase(Locale.ROOT).replace(" ", "") + ",").contains("," + ext + ","))) {
                    urls.add(file);
                }
            }
            Collections.sort(urls, (a, b) -> Integer.compare(formatPreference(b), formatPreference(a)));
            for (String file : urls) {
                out.add(new Result(source, title, file, Math.max(55, LinkResolver.matchScore(keyword, title))));
            }
        }
        return out;
    }

    private int formatPreference(String url) {
        String s = url.toLowerCase(Locale.ROOT);
        if (s.contains("epub")) return 3;
        if (s.contains("pdf")) return 2;
        return 1;
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
        ArrayList<Result> results = new ArrayList<>();
        for (LinkResolver.Candidate item : LinkResolver.searchLinks(html, baseUrl, keyword)) {
            if (LinkResolver.fileHint(item.url) && !allowedFormat(item.url, extText)) continue;
            results.add(new Result(sourceName, item.title, item.url, Math.min(100, item.score)));
        }
        return results;
    }

    private boolean allowedFormat(String url, String exts) {
        if (exts == null || exts.trim().isEmpty()) return true;
        String text = url.toLowerCase(Locale.ROOT);
        for (String e : exts.toLowerCase(Locale.ROOT).split("[,， ]+")) {
            if (!e.trim().isEmpty() && text.contains("." + e.trim())) return true;
        }
        return false;
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
            Button down = primaryButton("解析并下载");
            down.setOnClickListener(v -> download(r));
            row.addView(down, weight(1));
            Button open = secondaryButton("网页分析");
            open.setOnClickListener(v -> {
                Intent intent = new Intent(this, WebAnalyzeActivity.class);
                intent.putExtra("initial_url", r.url);
                intent.putExtra("keyword", bookInput.getText().toString().trim());
                startActivity(intent);
            });
            row.addView(open, weight(1));
            card.addView(row);
            resultBox.addView(card);
        }
        if (autoDownloadCheck.isChecked() && !unique.isEmpty()
                && LinkResolver.matchScore(bookInput.getText().toString(), unique.get(0).title) >= 70)
            download(unique.get(0));
    }

    /** The old version queued an HTML detail page as a .pdf. Never do that. */
    private void download(Result r) {
        log("正在逐层解析：" + r.title);
        new Thread(() -> {
            LinkResolver.FileLink file = LinkResolver.resolve(r.url, null, r.title,
                    null, url -> CookieManager.getInstance().getCookie(url),
                    this::logSafe);
            ui.post(() -> {
                if (file == null) {
                    toast("未取得真实文件地址，请点击“网页分析”登录或操作");
                    log("未下载：目标仍是网页，或需要网页内 JavaScript 生成下载链接");
                    return;
                }
                try {
                    long id = DownloadJobs.enqueue(this, file, null);
                    toast("已提交下载，正在监测状态");
                    log("已识别文件：" + file.name + "；任务 ID：" + id);
                    DownloadJobs.watch(this, id, this::log);
                } catch (Exception e) {
                    toast("提交下载失败：" + e.getMessage());
                    log("提交下载失败：" + e.getMessage());
                }
            });
        }).start();
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

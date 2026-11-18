package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
    private TextView searchStatus;
    private Button searchButton;
    private ProgressBar searchProgress;
    private ScrollView homeScroll;
    private int searchGeneration = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String PREF = "book_downloader_pref_v4";
    private static final String DEFAULT_TEMPLATES = "https://gutendex.com/books?search={keyword}\nhttps://www.gutenberg.org/ebooks/search/?query={keyword}";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        loadSettings();
        if (getIntent() != null && getIntent().getBooleanExtra("open_sources", false))
            ui.post(this::openSourceManager);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_sources", false)) {
            intent.removeExtra("open_sources");
            ui.post(this::openSourceManager);
        }
    }

    private void buildUi() {
        final int dark = Color.parseColor("#1E2D4B");
        final int muted = Color.parseColor("#687995");
        final int blue = Color.parseColor("#2869F3");

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.parseColor("#F4F7FD"));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(15), dp(16), dp(20));
        homeScroll.addView(root);
        screen.addView(homeScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Branded compact header, optimized for narrow portrait phone screens.
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(15), dp(16));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#124ED8"), Color.parseColor("#119FEF")});
        heroBg.setCornerRadius(dp(20));
        hero.setBackground(heroBg);
        ImageView appLogo = new ImageView(this);
        appLogo.setImageResource(R.drawable.ic_book_search);
        appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(62), dp(62));
        iconParams.setMargins(0, 0, dp(13), 0);
        hero.addView(appLogo, iconParams);
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("书名检索助手");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        heroText.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("搜索 · 网页解析 · 下载管理");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.parseColor("#E3F0FF"));
        subtitle.setPadding(0, dp(5), 0, 0);
        heroText.addView(subtitle);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, 0, 0, dp(13));
        root.addView(hero, hlp);

        LinearLayout searchCard = card();
        addLabel(searchCard, "搜索你需要的电子书");
        TextView searchHint = new TextView(this);
        searchHint.setText("输入书名、作者或资料关键词");
        searchHint.setTextColor(muted);
        searchHint.setTextSize(12);
        searchHint.setPadding(0, 0, 0, dp(10));
        searchCard.addView(searchHint);

        bookInput = input("例如：机械设计手册", 1);
        bookInput.setSingleLine(true);
        bookInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        bookInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAll(false);
                return true;
            }
            return false;
        });
        searchCard.addView(bookInput, new LinearLayout.LayoutParams(-1, dp(52)));

        searchButton = primaryButton("⌕  搜索已选网站");
        searchButton.setTextSize(16);
        searchButton.setTypeface(Typeface.DEFAULT_BOLD);
        searchButton.setOnClickListener(v -> searchAll(false));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(51));
        searchLp.setMargins(0, dp(11), 0, dp(5));
        searchCard.addView(searchButton, searchLp);

        LinearLayout otherSearch = new LinearLayout(this);
        otherSearch.setGravity(Gravity.CENTER_VERTICAL);
        Button priority = secondaryButton("按顺序优先搜索");
        priority.setOnClickListener(v -> searchAll(true));
        otherSearch.addView(priority, navWeight(1));
        Button paste = secondaryButton("粘贴并搜索");
        paste.setOnClickListener(v -> readClipboard(true));
        otherSearch.addView(paste, navWeight(1));
        searchCard.addView(otherSearch);

        searchProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        searchProgress.setIndeterminate(true);
        searchProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(-1, dp(3));
        progLp.setMargins(0, dp(8), 0, 0);
        searchCard.addView(searchProgress, progLp);
        root.addView(searchCard);

        // Source selection remains a small, clear step in the search flow.
        LinearLayout sourceCard = card();
        LinearLayout srcTop = new LinearLayout(this);
        srcTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView srcTitle = new TextView(this);
        srcTitle.setText("搜索网站");
        srcTitle.setTextSize(16);
        srcTitle.setTextColor(dark);
        srcTitle.setTypeface(Typeface.DEFAULT_BOLD);
        srcTop.addView(srcTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button manage = secondaryButton("管理 / 验证  ›");
        manage.setTextSize(13);
        manage.setOnClickListener(v -> openSourceManager());
        srcTop.addView(manage, new LinearLayout.LayoutParams(dp(125), dp(42)));
        sourceCard.addView(srcTop);

        sourceSummary = new TextView(this);
        sourceSummary.setTextSize(13);
        sourceSummary.setTextColor(muted);
        sourceSummary.setPadding(dp(11), dp(10), dp(11), dp(10));
        GradientDrawable srcBg = new GradientDrawable();
        srcBg.setColor(Color.parseColor("#F2F6FF"));
        srcBg.setCornerRadius(dp(11));
        sourceSummary.setBackground(srcBg);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.setMargins(0, dp(7), 0, 0);
        sourceCard.addView(sourceSummary, sumLp);
        sourceSummary.setOnClickListener(v -> openSourceManager());
        root.addView(sourceCard);

        LinearLayout resultsCard = card();
        LinearLayout resultHead = new LinearLayout(this);
        resultHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView resultTitle = new TextView(this);
        resultTitle.setText("搜索结果");
        resultTitle.setTextColor(dark);
        resultTitle.setTextSize(17);
        resultTitle.setTypeface(Typeface.DEFAULT_BOLD);
        resultHead.addView(resultTitle, new LinearLayout.LayoutParams(0, -2, 1));
        searchStatus = new TextView(this);
        searchStatus.setText("准备就绪");
        searchStatus.setTextColor(blue);
        searchStatus.setTextSize(12);
        resultHead.addView(searchStatus);
        resultsCard.addView(resultHead);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        TextView empty = new TextView(this);
        empty.setText("输入关键词，点击搜索即可查看结果。");
        empty.setTextSize(13);
        empty.setTextColor(muted);
        empty.setPadding(dp(3), dp(18), dp(3), dp(12));
        resultBox.addView(empty);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, dp(6), 0, 0);
        resultsCard.addView(resultBox, rlp);
        root.addView(resultsCard);

        LinearLayout quickCard = card();
        addLabel(quickCard, "网页工具");
        TextView quickHint = new TextView(this);
        quickHint.setText("登录后才能下载的资料，可进入网页分析器。");
        quickHint.setTextSize(12);
        quickHint.setTextColor(muted);
        quickHint.setPadding(0, 0, 0, dp(7));
        quickCard.addView(quickHint);
        LinearLayout quickRow = new LinearLayout(this);
        Button web = secondaryButton("◉  网页分析器");
        web.setOnClickListener(v -> openWebAnalyzer());
        quickRow.addView(web, navWeight(1));
        Button downloads = secondaryButton("↓  下载管理");
        downloads.setOnClickListener(v -> openDownloads());
        quickRow.addView(downloads, navWeight(1));
        quickCard.addView(quickRow);
        root.addView(quickCard);

        // Advanced fields are available, but do not crowd the primary journey.
        LinearLayout optionCard = card();
        Button expand = secondaryButton("⚙  高级搜索设置  ▾");
        optionCard.addView(expand, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        advanced.setPadding(0, dp(10), 0, 0);
        TextView extTip = new TextView(this);
        extTip.setText("允许识别的文件类型");
        extTip.setTextColor(muted);
        extTip.setTextSize(12);
        advanced.addView(extTip);
        extInput = input("pdf,epub,mobi,txt,zip,rar,7z", 1);
        extInput.setSingleLine(true);
        LinearLayout.LayoutParams extLp = new LinearLayout.LayoutParams(-1, dp(48));
        extLp.setMargins(0, dp(7), 0, dp(6));
        advanced.addView(extInput, extLp);
        autoDownloadCheck = new CheckBox(this);
        autoDownloadCheck.setText("自动下载最高匹配结果（建议确认来源后再启用）");
        autoDownloadCheck.setTextSize(13);
        autoDownloadCheck.setTextColor(dark);
        advanced.addView(autoDownloadCheck);

        Button openAll = secondaryButton("在外部浏览器打开已选搜索页");
        openAll.setOnClickListener(v -> openAllSearchPages());
        advanced.addView(openAll, new LinearLayout.LayoutParams(-1, dp(44)));
        optionCard.addView(advanced);
        expand.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            expand.setText(show ? "⚙  收起高级设置  ▴" : "⚙  高级搜索设置  ▾");
        });
        root.addView(optionCard);

        LinearLayout logCard = card();
        Button showLog = secondaryButton("查看运行日志  ▾");
        logCard.addView(showLog, new LinearLayout.LayoutParams(-1, dp(42)));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.parseColor("#485772"));
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(4), dp(10), dp(4), dp(4));
        logView.setVisibility(View.GONE);
        logCard.addView(logView);
        showLog.setOnClickListener(v -> {
            boolean show = logView.getVisibility() != View.VISIBLE;
            logView.setVisibility(show ? View.VISIBLE : View.GONE);
            showLog.setText(show ? "收起运行日志  ▴" : "查看运行日志  ▾");
        });
        root.addView(logCard);

        // Fixed bottom navigation: key destinations never disappear on scroll.
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(10), dp(8), dp(10), dp(10));
        nav.setBackgroundColor(Color.WHITE);
        Button home = primaryButton("⌕  搜索");
        home.setOnClickListener(v -> homeScroll.smoothScrollTo(0, 0));
        nav.addView(home, navWeight(1));
        Button sitesButton = bottomButton("▤  网站");
        sitesButton.setOnClickListener(v -> openSourceManager());
        nav.addView(sitesButton, navWeight(1));
        Button filesButton = bottomButton("↓  下载");
        filesButton.setOnClickListener(v -> openDownloads());
        nav.addView(filesButton, navWeight(1));
        Button browser = bottomButton("◉  网页");
        browser.setOnClickListener(v -> openWebAnalyzer());
        nav.addView(browser, navWeight(1));
        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(63)));
        setContentView(screen);
    }

    private LinearLayout.LayoutParams navWeight(int weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(43), weight);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private Button bottomButton(String title) {
        Button b = secondaryButton(title);
        b.setTextSize(12);
        return b;
    }

    private void openSourceManager() {
        SourceManager.open(this, sites, bookInput.getText().toString().trim(), updated -> {
            sites.clear();
            sites.addAll(updated);
            saveSettings();
            updateSourceSummary();
            searchGeneration++;
            setSearchLoading(false, "网站配置已更新");
            resultBox.removeAllViews();
            TextView note = new TextView(this);
            note.setText("网站选择已更新，请重新搜索以获得最新结果。");
            note.setTextSize(13);
            note.setTextColor(Color.parseColor("#687995"));
            note.setPadding(dp(3), dp(15), dp(3), dp(12));
            resultBox.addView(note);
            toast("搜索网站已保存");
        });
    }

    private void openDownloads() {
        startActivity(new Intent(this, DownloadManagerActivity.class));
    }

    private void openWebAnalyzer() {
        Intent intent = new Intent(this, WebAnalyzeActivity.class);
        intent.putExtra("keyword", bookInput.getText().toString().trim());
        startActivity(intent);
    }

    private void setSearchLoading(boolean loading, String status) {
        if (searchButton != null) {
            searchButton.setEnabled(!loading);
            searchButton.setAlpha(loading ? .65f : 1f);
            searchButton.setText(loading ? "正在搜索…" : "⌕  搜索已选网站");
        }
        if (searchProgress != null) searchProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (searchStatus != null) searchStatus.setText(status);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(11));
        box.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor("#E4EAF4"));
        box.setBackground(bg);
        box.setElevation(dp(1));
        return box;
    }

    private void addLabel(LinearLayout root, String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.parseColor("#203151"));
        v.setPadding(0, 0, 0, dp(7));
        root.addView(v);
    }

    private EditText input(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setTextSize(15);
        e.setPadding(dp(13), dp(10), dp(13), dp(10));
        e.setTextColor(Color.parseColor("#1C2B47"));
        e.setHintTextColor(Color.parseColor("#9AA7BC"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F7F9FE"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#DBE5F5"));
        e.setBackground(bg);
        return e;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#2869F3"));
        bg.setCornerRadius(dp(11));
        b.setBackground(bg);
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(Color.parseColor("#2869F3"));
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(7), 0, dp(7), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E2ECFF"));
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), Color.parseColor("#D0DEFA"));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams weight(int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(43), w);
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
        String keyword = bookInput.getText().toString().trim();
        String exts = extInput.getText().toString().trim();
        ArrayList<Source> sources = getTemplates(false);
        if (keyword.length() == 0) {
            toast("请先输入书名或搜索关键词");
            bookInput.requestFocus();
            return;
        }
        if (sources.isEmpty()) {
            toast("请先在网站管理中勾选至少一个有效网站");
            openSourceManager();
            return;
        }
        saveSettings();
        final int request = ++searchGeneration;
        setSearchLoading(true, "检索 " + sources.size() + " 个网站中…");
        resultBox.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText(priority ? "正按顺序寻找匹配书籍…" : "正在多站并行检索，请查看检索结果…");
        loading.setTextSize(13);
        loading.setTextColor(Color.parseColor("#687995"));
        loading.setPadding(dp(4), dp(20), dp(4), dp(20));
        resultBox.addView(loading);
        log(priority ? "开始顺序搜索 " + sources.size() + " 个站点"
                : "开始并行搜索 " + sources.size() + " 个站点");

        new Thread(() -> {
            ArrayList<Result> all = new ArrayList<>();
            if (priority) {
                for (Source src : sources) {
                    if (request != searchGeneration) break;
                    try {
                        ArrayList<Result> part = searchOne(src, keyword, exts);
                        all.addAll(part);
                        if (!part.isEmpty()) {
                            logSafe(src.name + " 已有候选结果，停止后续网站");
                            break;
                        }
                    } catch (Exception e) {
                        logSafe(src.name + " 检索失败：" + e.getMessage());
                    }
                }
            } else {
                ArrayList<Result> sync = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(sources.size());
                for (Source src : sources) {
                    new Thread(() -> {
                        try {
                            ArrayList<Result> part = searchOne(src, keyword, exts);
                            synchronized (sync) { sync.addAll(part); }
                        } catch (Exception e) {
                            logSafe(src.name + " 检索失败：" + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }, "ebook-search-" + src.name).start();
                }
                try {
                    if (!latch.await(45, java.util.concurrent.TimeUnit.SECONDS))
                        logSafe("部分站点响应较慢，已显示收到的结果");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                synchronized (sync) { all.addAll(sync); }
            }
            Collections.sort(all, (a, b) -> b.score - a.score);
            ui.post(() -> {
                if (request != searchGeneration || isFinishing() || isDestroyed()) return;
                setSearchLoading(false, "搜索完成");
                showResults(all);
            });
        }, "ebook-search").start();
    }

    private ArrayList<Result> searchOne(Source source, String keyword, String exts) throws Exception {
        if (ShugeCatalog.isShuge(source.template)) return searchShuge(source, keyword);
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

    private ArrayList<Result> searchShuge(Source source, String keyword) throws Exception {
        // The public catalog's GET /query/ endpoint returns JSON items.
        // The old ?q= appended to www.shuge.org loaded only its home page.
        String url = ShugeCatalog.apiUrl(keyword);
        logSafe(source.name + " 使用书格公开查询：" + url);
        ArrayList<ShugeCatalog.Book> books = ShugeCatalog.parse(httpGet(url), keyword);
        if (books.isEmpty()) {
            String alternate = ShugeCatalog.alternateKeyword(keyword);
            if (!alternate.equals(keyword)) {
                logSafe("书格简体无结果，继续尝试传统字形：" + alternate);
                books = ShugeCatalog.parse(httpGet(ShugeCatalog.apiUrl(alternate)), alternate);
            }
        }
        ArrayList<Result> found = new ArrayList<>();
        for (ShugeCatalog.Book book : books) {
            String target = !book.download.isEmpty() ? book.download : book.details;
            if (!LinkResolver.webUrl(target)) continue;
            int score = Math.max(55, LinkResolver.matchScore(keyword, book.title));
            found.add(new Result(source.name, book.title, target, score, book.details));
        }
        // The public catalog may cover fewer items than the human website.
        // Fall back to the site's ordinary search page instead of returning
        // "no match" solely because the catalog API contains zero records.
        if (found.isEmpty()) {
            String page = makeSearchUrl(ShugeCatalog.browserTemplate(), keyword);
            try {
                String html = httpGet(page);
                ArrayList<Result> manual = parseLinks(html, page, keyword, "",
                        source.name + " · 官网页面");
                for (Result item : manual) found.add(item);
                logSafe("书格官网搜索页候选：" + manual.size() + " 条");
            } catch (Exception ex) {
                logSafe("书格网页回退检索：" + ex.getMessage());
            }
        }
        logSafe(source.name + " 公开目录/官网搜索合计：" + found.size() + " 条书籍链接");
        return found;
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
        Set<String> seen = new HashSet<>();
        ArrayList<Result> unique = new ArrayList<>();
        for (Result r : results) if (seen.add(r.url)) unique.add(r);
        if (searchStatus != null) searchStatus.setText(unique.isEmpty()
                ? "暂无匹配" : "共 " + unique.size() + " 条");
        log("最终候选链接：" + unique.size() + " 个");
        if (unique.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("没有检索到明显的书籍链接。\n可尝试不同的书名，或在“管理 / 验证”中用浏览器检查网站搜索页面。");
            none.setTextSize(13);
            none.setTextColor(Color.parseColor("#687995"));
            none.setPadding(dp(3), dp(17), dp(3), dp(15));
            resultBox.addView(none);
            return;
        }

        int shown = 0;
        for (Result r : unique) {
            if (shown++ >= 20) break;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F8FAFE"));
            bg.setCornerRadius(dp(13));
            bg.setStroke(dp(1), Color.parseColor("#E0E9F7"));
            box.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(7), 0, dp(4));
            resultBox.addView(box, lp);

            TextView title = new TextView(this);
            title.setText(r.title);
            title.setTextSize(15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(Color.parseColor("#1F3052"));
            title.setMaxLines(2);
            box.addView(title);

            TextView meta = new TextView(this);
            meta.setText(r.source + "  ·  "
                    + (ShugeCatalog.isShuge(r.detailUrl) ? "书格资源"
                    : LinkResolver.fileHint(r.url) ? "文件链接" : "详情/下载入口")
                    + "  ·  匹配 " + r.score);
            meta.setTextSize(11);
            meta.setTextColor(Color.parseColor("#3167CC"));
            meta.setPadding(0, dp(5), 0, dp(4));
            box.addView(meta);

            TextView url = new TextView(this);
            url.setText(r.url);
            url.setMaxLines(2);
            url.setTextSize(11);
            url.setTextColor(Color.parseColor("#8190A9"));
            box.addView(url);

            LinearLayout actions = new LinearLayout(this);
            actions.setPadding(0, dp(10), 0, 0);
            box.addView(actions);
            Button down = primaryButton("↓  解析下载");
            down.setOnClickListener(v -> download(r));
            actions.addView(down, weight(1));
            Button preview = secondaryButton("◉  网页查看");
            preview.setOnClickListener(v -> {
                Intent intent = new Intent(this, WebAnalyzeActivity.class);
                intent.putExtra("initial_url",
                        LinkResolver.webUrl(r.detailUrl) ? r.detailUrl : r.url);
                intent.putExtra("keyword", bookInput.getText().toString().trim());
                startActivity(intent);
            });
            actions.addView(preview, weight(1));
        }

        if (autoDownloadCheck.isChecked() && !unique.isEmpty()
                && LinkResolver.matchScore(bookInput.getText().toString(),
                unique.get(0).title) >= 70) {
            download(unique.get(0));
        }
    }

    /** Send cloud/gateway pages to authenticated WebView, not DownloadManager. */
    private void download(Result r) {
        if (CloudLinkHandler.needsWebFlow(r.url)) {
            Intent intent = new Intent(this, CloudLoginActivity.class);
            intent.putExtra(CloudLoginActivity.EXTRA_URL, r.url);
            intent.putExtra(CloudLoginActivity.EXTRA_KEYWORD,
                    bookInput.getText().toString().trim());
            startActivity(intent);
            return;
        }
        log("正在逐层解析：" + r.title);
        new Thread(() -> {
            LinkResolver.FileLink file = LinkResolver.resolve(r.url, null, r.title,
                    null, url -> CookieManager.getInstance().getCookie(url),
                    this::logSafe);
            ui.post(() -> {
                if (file == null) {
                    new AlertDialog.Builder(this)
                            .setTitle("需要网页登录或进一步操作")
                            .setMessage("没有发现可直接下载的文件。可能是网页登录页、动态下载按钮或网盘分享入口。要进入网页登录窗口继续吗？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("打开网页登录", (d, w) -> {
                                Intent intent = new Intent(this, WebAnalyzeActivity.class);
                                intent.putExtra("initial_url", r.url);
                                intent.putExtra("keyword", r.title);
                                startActivity(intent);
                            }).show();
                    log("尚未获得真实文件地址；可登录后再次提取");
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
        String source; String title; String url; String detailUrl; int score;
        Result(String src, String t, String u, int s) { this(src, t, u, s, u); }
        Result(String src, String t, String u, int s, String detail) {
            source = src; title = t; url = u; score = s;
            detailUrl = detail == null ? u : detail;
        }
    }
}

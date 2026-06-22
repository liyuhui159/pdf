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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public class WebAnalyzeActivity extends Activity {
    private static final String PREF = "web_analyze_ai_settings_v1";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_KEYWORD = "keyword";
    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_URL = "https://z-library.mn/?ts=0546";
    private static final int PAGE_TEXT_LIMIT = 12000;
    private static final int AUTO_DOWNLOAD_CONFIDENCE = 60;

    private EditText keywordInput;
    private EditText urlInput;
    private LinearLayout tabStrip;
    private FrameLayout webHolder;
    private LinearLayout resultBox;
    private TextView logView;
    private TextView aiResultView;
    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private int currentIndex = -1;
    private boolean waitRunning = false;
    private int waitTicks = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);
        buildUi();
        String firstUrl = prefs.getString(KEY_LAST_URL, DEFAULT_URL);
        addTab(firstUrl, false);
    }

    @Override
    protected void onDestroy() {
        waitRunning = false;
        handler.removeCallbacksAndMessages(null);
        for (BrowserTab tab : tabs) tab.webView.destroy();
        tabs.clear();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        WebView w = currentWebView();
        if (w != null && w.canGoBack()) {
            w.goBack();
            return;
        }
        super.onBackPressed();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));
        root.setPadding(dp(6), dp(6), dp(6), dp(6));

        TextView title = new TextView(this);
        title.setText("网页登录分析下载链接");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#1F2A44"));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        keywordInput = input("书名 / 资料名，可读取剪贴板", 1);
        keywordInput.setSingleLine(true);
        keywordInput.setText(prefs.getString(KEY_KEYWORD, ""));
        root.addView(keywordInput, new LinearLayout.LayoutParams(-1, dp(40)));

        urlInput = input("搜索网址，可含 {keyword}", 1);
        urlInput.setSingleLine(true);
        urlInput.setText(prefs.getString(KEY_LAST_URL, DEFAULT_URL));
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout row1 = actionRow();
        root.addView(row1);
        Button clip = button("读剪贴板", true);
        clip.setOnClickListener(v -> readClipboard());
        row1.addView(clip, weight());
        Button open = button("打开/搜索", true);
        open.setOnClickListener(v -> openUrl(false));
        row1.addView(open, weight());
        Button newTab = button("新窗口搜索", true);
        newTab.setOnClickListener(v -> openUrl(true));
        row1.addView(newTab, weight());

        LinearLayout row2 = actionRow();
        root.addView(row2);
        Button ai = button("AI判断当前页", true);
        ai.setOnClickListener(v -> analyzeCurrentPageWithAi());
        row2.addView(ai, weight());
        Button analyze = button("分析链接", true);
        analyze.setOnClickListener(v -> analyzePage());
        row2.addView(analyze, weight());
        Button api = button("API设置", false);
        api.setOnClickListener(v -> showApiDialog());
        row2.addView(api, weight());

        LinearLayout row3 = actionRow();
        root.addView(row3);
        Button waitBtn = button("等待并点下载", true);
        waitBtn.setOnClickListener(v -> startWaitDownload());
        row3.addView(waitBtn, weight());
        Button stopBtn = button("停止等待", false);
        stopBtn.setOnClickListener(v -> stopWaitDownload());
        row3.addView(stopBtn, weight());
        Button backBtn = button("网页返回", false);
        backBtn.setOnClickListener(v -> { WebView w = currentWebView(); if (w != null && w.canGoBack()) w.goBack(); });
        row3.addView(backBtn, weight());

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(0, 0, 0, dp(4));
        tabScroll.addView(tabStrip);
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(38)));

        webHolder = new FrameLayout(this);
        webHolder.setBackgroundColor(Color.WHITE);
        root.addView(webHolder, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        aiResultView = new TextView(this);
        aiResultView.setText(apiConfigured() ? "AI已配置：点击“AI判断当前页”后会提示连接状态，符合时会自动尝试下载。" : "AI未配置：请点“API设置”填写API Key。 ");
        aiResultView.setTextSize(12);
        aiResultView.setMaxLines(4);
        aiResultView.setTextColor(Color.parseColor("#263248"));
        aiResultView.setPadding(dp(8), dp(5), dp(8), dp(5));
        aiResultView.setBackground(boxBg(Color.WHITE, "#D8E0EC"));
        root.addView(aiResultView, new LinearLayout.LayoutParams(-1, -2));

        ScrollView rs = new ScrollView(this);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        rs.addView(resultBox);
        root.addView(rs, new LinearLayout.LayoutParams(-1, dp(92)));

        logView = new TextView(this);
        logView.setTextSize(10);
        logView.setMaxLines(2);
        logView.setTextColor(Color.parseColor("#39445A"));
        root.addView(logView, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        return row;
    }

    private boolean apiConfigured() {
        return prefs != null && prefs.getString(KEY_API_KEY, "").trim().length() > 0;
    }

    private void addTab(String url, boolean loadNow) {
        WebView w = createWebView();
        BrowserTab tab = new BrowserTab(w, normalizeUrl(url));
        tabs.add(tab);
        switchTo(tabs.size() - 1);
        if (loadNow || tab.url.length() > 0) w.loadUrl(tab.url.length() > 0 ? tab.url : DEFAULT_URL);
    }

    private WebView createWebView() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                BrowserTab tab = findTab(view);
                if (tab != null) {
                    tab.url = url == null ? tab.url : url;
                    prefs.edit().putString(KEY_LAST_URL, tab.url).apply();
                    updateTabs();
                }
                log("加载完成：" + shorten(url, 90));
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) {
                BrowserTab tab = findTab(view);
                if (tab != null && title != null && title.trim().length() > 0) {
                    tab.title = title.trim();
                    updateTabs();
                }
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView child = createWebView();
                BrowserTab tab = new BrowserTab(child, "about:blank");
                tab.title = "新窗口";
                tabs.add(tab);
                switchTo(tabs.size() - 1);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(child);
                resultMsg.sendToTarget();
                return true;
            }
        });
        w.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                downloadWithCookies(url, name, mimetype, userAgent);
            }
        });
        return w;
    }

    private void switchTo(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentIndex = index;
        webHolder.removeAllViews();
        BrowserTab tab = tabs.get(index);
        webHolder.addView(tab.webView, new FrameLayout.LayoutParams(-1, -1));
        urlInput.setText(tab.url);
        aiResultView.setText(tab.aiResult.length() > 0 ? tab.aiResult : (apiConfigured() ? "AI已配置：点击“AI判断当前页”后会提示连接状态，符合时会自动尝试下载。" : "AI未配置：请点“API设置”填写API Key。"));
        updateTabs();
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1) {
            toast("至少保留一个窗口");
            return;
        }
        BrowserTab tab = tabs.remove(index);
        webHolder.removeView(tab.webView);
        tab.webView.destroy();
        switchTo(Math.min(index, tabs.size() - 1));
    }

    private void updateTabs() {
        tabStrip.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(8), 0, dp(2), 0);
            item.setBackground(boxBg(i == currentIndex ? Color.parseColor("#2F6BFF") : Color.WHITE, i == currentIndex ? "#2F6BFF" : "#D8E0EC"));
            final int index = i;
            TextView name = new TextView(this);
            name.setText((i + 1) + " " + shorten(tab.displayTitle(), 12));
            name.setTextSize(12);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(i == currentIndex ? Color.WHITE : Color.parseColor("#263248"));
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setOnClickListener(v -> switchTo(index));
            item.addView(name, new LinearLayout.LayoutParams(dp(105), dp(32)));
            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(18);
            close.setTypeface(Typeface.DEFAULT_BOLD);
            close.setGravity(Gravity.CENTER);
            close.setTextColor(i == currentIndex ? Color.WHITE : Color.parseColor("#D64545"));
            close.setOnClickListener(v -> closeTab(index));
            item.addView(close, new LinearLayout.LayoutParams(dp(28), dp(32)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
            lp.setMargins(0, 0, dp(6), 0);
            tabStrip.addView(item, lp);
        }
    }

    private WebView currentWebView() { return currentIndex >= 0 && currentIndex < tabs.size() ? tabs.get(currentIndex).webView : null; }
    private BrowserTab currentTab() { return currentIndex >= 0 && currentIndex < tabs.size() ? tabs.get(currentIndex) : null; }
    private BrowserTab findTab(WebView webView) { for (BrowserTab tab : tabs) if (tab.webView == webView) return tab; return null; }

    private EditText input(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setTextSize(14);
        e.setPadding(dp(9), 0, dp(9), 0);
        e.setTextColor(Color.parseColor("#151B2D"));
        e.setHintTextColor(Color.parseColor("#9AA4B5"));
        e.setBackground(boxBg(Color.WHITE, "#D8E0EC"));
        return e;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setTextColor(primary ? Color.WHITE : Color.parseColor("#2F6BFF"));
        b.setBackground(boxBg(primary ? Color.parseColor("#2F6BFF") : Color.parseColor("#EEF3FF"), primary ? "#2F6BFF" : "#C9D8FF"));
        return b;
    }

    private GradientDrawable boxBg(int color, String stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor(stroke));
        return bg;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void readClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) { toast("剪贴板为空"); return; }
        ClipData data = cm.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) { toast("剪贴板为空"); return; }
        CharSequence text = data.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().length() == 0) { toast("未读到文字"); return; }
        keywordInput.setText(text.toString().trim());
        prefs.edit().putString(KEY_KEYWORD, text.toString().trim()).apply();
        log("读取剪贴板：" + text.toString().trim());
    }

    private void openUrl(boolean newTab) {
        try {
            String keyword = keywordInput.getText().toString().trim();
            prefs.edit().putString(KEY_KEYWORD, keyword).apply();
            String tpl = urlInput.getText().toString().trim().replace("｛", "{").replace("｝", "}");
            if (tpl.length() == 0) { toast("请填写网址"); return; }
            String url = tpl;
            if (tpl.contains("{keyword}")) url = tpl.replace("{keyword}", URLEncoder.encode(keyword, "UTF-8"));
            url = normalizeUrl(url);
            prefs.edit().putString(KEY_LAST_URL, url).apply();
            if (newTab || currentWebView() == null) addTab(url, true);
            else {
                BrowserTab tab = currentTab();
                if (tab != null) tab.url = url;
                currentWebView().loadUrl(url);
                updateTabs();
            }
            log("打开：" + url);
        } catch (Exception e) {
            toast("打开失败：" + e.getMessage());
        }
    }

    private void analyzeCurrentPageWithAi() {
        WebView w = currentWebView();
        BrowserTab tab = currentTab();
        if (w == null || tab == null) { toast("没有打开的网页窗口"); return; }
        String keyword = keywordInput.getText().toString().trim();
        if (keyword.length() == 0) { toast("请先输入书名"); return; }
        prefs.edit().putString(KEY_KEYWORD, keyword).apply();
        String apiKey = prefs.getString(KEY_API_KEY, "").trim();
        if (apiKey.length() == 0) {
            showApiDialog();
            toast("请先填写API Key");
            return;
        }
        if (!isNetworkAvailable()) {
            showLocalAiResult(tab, keyword, "当前无网络，先做本地关键词判断。");
            return;
        }
        aiResultView.setText("正在连接AI并读取当前网页文字...");
        w.evaluateJavascript("(function(){return document.body ? document.body.innerText : document.documentElement.innerText;})()", value -> {
            String pageText = decodeJsString(value);
            if (pageText.trim().length() < 8) {
                showLocalAiResult(tab, keyword, "没有读到足够网页文字，可能页面未加载完成或内容在图片里。");
                return;
            }
            aiResultView.setText("AI连接中：已读取 " + pageText.length() + " 字，正在判断...");
            callAiJudge(tab, keyword, tab.url, pageText);
        });
    }

    private void callAiJudge(BrowserTab tab, String keyword, String pageUrl, String pageText) {
        String apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL).trim();
        String apiKey = prefs.getString(KEY_API_KEY, "").trim();
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).trim();
        if (model.length() == 0) model = DEFAULT_MODEL;
        final String finalModel = model;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", finalModel);
                body.put("temperature", 0);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", "你是图书搜索结果判断器。判断当前网页是否包含用户目标书名对应的同一本书、同一资料或明显可下载结果。只返回JSON：{\"is_match\":true/false,\"confidence\":0-100,\"matched_title\":\"\",\"reason\":\"\"}"));
                messages.put(new JSONObject().put("role", "user").put("content", "目标书名：" + keyword + "\n当前网址：" + pageUrl + "\n网页文本：\n" + limit(pageText, PAGE_TEXT_LIMIT)));
                body.put("messages", messages);

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = conn.getResponseCode();
                String raw = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code < 200 || code >= 300) throw new IllegalStateException("API错误 " + code + "：" + raw);
                AiResult result = parseAiResponse(raw);
                runOnUiThread(() -> showAiResult(tab, keyword, result));
            } catch (Exception e) {
                runOnUiThread(() -> showLocalAiResult(tab, keyword, "AI连接失败：" + e.getMessage() + "\n已退回本地关键词判断。"));
            }
        }).start();
    }

    private AiResult parseAiResponse(String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        String content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        JSONObject json = extractJsonObject(content);
        AiResult r = new AiResult();
        r.match = json.optBoolean("is_match", false);
        r.confidence = json.optInt("confidence", r.match ? 80 : 20);
        r.matchedTitle = json.optString("matched_title", "");
        r.reason = json.optString("reason", content);
        return r;
    }

    private JSONObject extractJsonObject(String content) throws Exception {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) return new JSONObject(content.substring(start, end + 1));
        return new JSONObject(content);
    }

    private void showAiResult(BrowserTab tab, String keyword, AiResult r) {
        String text = "AI已连接\n" +
                (r.match ? "符合" : "不符合") + "  置信度：" + r.confidence + "%\n" +
                "目标书名：" + keyword + "\n" +
                (r.matchedTitle.length() > 0 ? "网页命中：" + r.matchedTitle + "\n" : "") +
                "原因：" + r.reason;
        tab.aiResult = text;
        if (tab == currentTab()) aiResultView.setText(text);
        log("AI已连接，判断完成：" + (r.match ? "符合" : "不符合") + "，置信度 " + r.confidence + "%");
        toast("AI已连接，判断完成");
        if (r.match && r.confidence >= AUTO_DOWNLOAD_CONFIDENCE) {
            log("AI判断符合，开始自动分析并尝试下载最高匹配链接");
            toast("AI判断符合，开始自动下载");
            autoDownloadAfterAiMatch(tab, keyword);
        }
    }

    private void showLocalAiResult(BrowserTab tab, String keyword, String prefix) {
        WebView w = tab.webView;
        w.evaluateJavascript("(function(){return document.body ? document.body.innerText : '';})()", value -> {
            String pageText = decodeJsString(value);
            boolean contains = normalizeForCompare(pageText).contains(normalizeForCompare(keyword));
            String text = prefix + "\n本地判断：" + (contains ? "可能符合" : "暂未发现完整书名") + "\n目标书名：" + keyword;
            tab.aiResult = text;
            if (tab == currentTab()) aiResultView.setText(text);
        });
    }

    private void autoDownloadAfterAiMatch(BrowserTab tab, String keyword) {
        WebView w = tab.webView;
        w.evaluateJavascript(linkExtractJs(), value -> {
            ArrayList<Item> list = collectItems(value, keyword);
            if (!list.isEmpty()) {
                Item direct = null;
                for (Item it : list) {
                    if (isDirectFile(it.url)) { direct = it; break; }
                }
                if (direct != null && direct.score >= 15) {
                    String ua = w.getSettings().getUserAgentString();
                    log("AI符合，已找到直链，自动下载：" + direct.title);
                    downloadWithCookies(direct.url, safeName(direct.title), null, ua);
                    return;
                }
                Item best = list.get(0);
                log("AI符合，但当前页不是直链，自动进入最高匹配页面：" + best.title);
                w.loadUrl(best.url);
                handler.postDelayed(this::startWaitDownload, 4500);
                return;
            }
            log("AI符合，但没有识别到候选链接，开始等待并点击当前页下载按钮");
            startWaitDownload();
        });
    }

    private void showApiDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), 0);
        EditText apiUrl = input("API URL", 1);
        apiUrl.setSingleLine(true);
        apiUrl.setText(prefs.getString(KEY_API_URL, DEFAULT_API_URL));
        EditText model = input("模型名", 1);
        model.setSingleLine(true);
        model.setText(prefs.getString(KEY_MODEL, DEFAULT_MODEL));
        EditText apiKey = input("API Key", 1);
        apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setText(prefs.getString(KEY_API_KEY, ""));
        box.addView(dialogLabel("OpenAI-compatible Chat Completions URL"));
        box.addView(apiUrl, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(dialogLabel("模型"));
        box.addView(model, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(dialogLabel("API Key（只保存在本机）"));
        box.addView(apiKey, new LinearLayout.LayoutParams(-1, dp(48)));
        new AlertDialog.Builder(this)
                .setTitle("AI API 设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                            .putString(KEY_API_URL, apiUrl.getText().toString().trim())
                            .putString(KEY_MODEL, model.getText().toString().trim())
                            .putString(KEY_API_KEY, apiKey.getText().toString().trim())
                            .apply();
                    aiResultView.setText("AI已配置：点击“AI判断当前页”会连接AI，连接成功会提示；符合时会自动下载。 ");
                    toast("API设置已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView dialogLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#263248"));
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void startWaitDownload() {
        if (waitRunning) { toast("已经在等待下载按钮"); return; }
        waitRunning = true;
        waitTicks = 0;
        toast("开始监测下载按钮，最多等待3分钟");
        log("开始等待慢速下载按钮变为可点击。不会绕过验证码/限制，只会等待并点击页面上已可用的下载按钮。");
        handler.post(waitRunnable);
    }

    private void stopWaitDownload() {
        waitRunning = false;
        handler.removeCallbacks(waitRunnable);
        toast("已停止等待");
        log("已停止等待下载按钮");
    }

    private final Runnable waitRunnable = new Runnable() {
        @Override public void run() {
            if (!waitRunning) return;
            waitTicks++;
            checkDownloadButtonOnce();
            if (waitRunning && waitTicks < 180) handler.postDelayed(this, 1000);
            if (waitTicks >= 180) {
                waitRunning = false;
                log("等待超时：3分钟内没有检测到可用下载按钮。建议手动点击网页按钮或用外部浏览器打开。");
            }
        }
    };

    private void checkDownloadButtonOnce() {
        WebView w = currentWebView();
        if (w == null) return;
        String js = "(function(){" +
                "function c(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function visible(e){try{var r=e.getBoundingClientRect();var st=getComputedStyle(e);return r.width>0&&r.height>0&&st.display!='none'&&st.visibility!='hidden';}catch(x){return true;}}" +
                "var body=c(document.body?document.body.innerText:'');" +
                "var m=body.match(/(\\d+)\\s*(秒|s|sec|second|seconds)/i);" +
                "var sec=m?m[1]:'';" +
                "var nodes=[].slice.call(document.querySelectorAll('a,button,input[type=button],input[type=submit]'));" +
                "for(var i=0;i<nodes.length;i++){var e=nodes[i];var t=c(e.innerText||e.textContent||e.value||e.title||e.getAttribute('aria-label')||'');var u=e.href||e.getAttribute('href')||'';var all=(t+' '+u).toLowerCase();" +
                "if(!visible(e)||e.disabled||e.getAttribute('disabled')!==null)continue;" +
                "if(all.indexOf('captcha')>=0||all.indexOf('验证码')>=0)continue;" +
                "if(all.indexOf('download')>=0||all.indexOf('下载')>=0||all.indexOf('slow')>=0||all.indexOf('free')>=0||all.indexOf('.pdf')>=0||all.indexOf('.epub')>=0){e.click();return JSON.stringify({clicked:true,text:t,url:u,sec:sec});}" +
                "}" +
                "return JSON.stringify({clicked:false,sec:sec});" +
                "})()";
        w.evaluateJavascript(js, value -> {
            try {
                String json = unwrapJsonString(value);
                JSONObject o = new JSONObject(json);
                boolean clicked = o.optBoolean("clicked", false);
                String sec = o.optString("sec", "");
                if (clicked) {
                    waitRunning = false;
                    handler.removeCallbacks(waitRunnable);
                    String text = o.optString("text", "下载按钮");
                    log("已点击可用下载按钮：" + text);
                    toast("已点击下载按钮；如未开始下载，请看网页是否还需确认");
                } else if (waitTicks % 5 == 0) {
                    if (sec.length() > 0) log("等待中，页面显示倒计时约 " + sec + " 秒");
                    else log("等待中：未发现可点击下载按钮");
                }
            } catch (Exception ignored) {}
        });
    }

    private String unwrapJsonString(String value) {
        String json = value == null ? "" : value;
        if (json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return json;
    }

    private void analyzePage() {
        WebView w = currentWebView();
        if (w == null) { toast("没有打开的网页窗口"); return; }
        w.evaluateJavascript(linkExtractJs(), value -> {
            items.clear();
            items.addAll(collectItems(value, keywordInput.getText().toString().trim()));
            showItems();
            log("当前页候选链接：" + items.size() + " 个");
        });
    }

    private String linkExtractJs() {
        return "(function(){" +
                "function clean(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function goodText(s){s=clean(s); if(!s)return ''; if(s.length>180)s=s.substring(0,180); return s;}" +
                "function titleOf(a){" +
                "var n=a; for(var d=0; n&&d<6; d++,n=n.parentElement){" +
                "var hs=n.querySelectorAll('h1,h2,h3,h4,.title,[class*=title],[class*=book],[class*=name]');" +
                "for(var i=0;i<hs.length;i++){var t=goodText(hs[i].innerText||hs[i].textContent); if(t&&t.length>3)return t;}" +
                "}" +
                "var t=goodText(a.innerText||a.textContent||a.title||a.getAttribute('aria-label'));" +
                "if(t&&t.length>3)return t;" +
                "n=a; var best=''; for(var j=0;n&&j<5;j++,n=n.parentElement){var tx=goodText(n.innerText||n.textContent); if(tx.length>best.length)best=tx;}" +
                "return best;" +
                "}" +
                "var arr=[].slice.call(document.querySelectorAll('a'));" +
                "return JSON.stringify(arr.map(function(a){return {t:titleOf(a),lt:clean(a.innerText||a.textContent||a.title||''),u:a.href||'',cls:a.className||''};}).filter(function(x){return x.u;}));" +
                "})()";
    }

    private ArrayList<Item> collectItems(String value, String key) {
        ArrayList<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(unwrapJsonString(value));
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String title = o.optString("t", "");
                String linkText = o.optString("lt", "");
                String url = o.optString("u", "");
                if (url.length() == 0 || seen.contains(url)) continue;
                if (!isCandidate(title, linkText, url)) continue;
                seen.add(url);
                String displayTitle = pickTitle(title, linkText, url);
                int score = score(key, displayTitle + " " + linkText + " " + url);
                String type = isDirectFile(url) ? "直链文件" : "页面/详情/下载按钮";
                out.add(new Item(displayTitle, linkText, url, score, type));
            }
            out.sort((a, b) -> b.score - a.score);
        } catch (Exception e) {
            toast("分析失败：" + e.getMessage());
            log("分析失败：" + e.getMessage());
        }
        return out;
    }

    private String pickTitle(String title, String linkText, String url) {
        String t = clean(title);
        if (t.length() < 4) t = clean(linkText);
        if (t.length() < 4) t = guessName(url);
        t = t.replace("Read more", "").replace("Download", "").replace("下载", "").trim();
        if (t.length() > 120) t = t.substring(0, 120) + "...";
        return t.length() == 0 ? guessName(url) : t;
    }

    private String clean(String s) { return s == null ? "" : s.replaceAll("\\s+", " ").trim(); }

    private boolean isCandidate(String title, String linkText, String url) {
        String s = (title + " " + linkText + " " + url).toLowerCase(Locale.ROOT);
        return s.contains(".pdf") || s.contains(".epub") || s.contains(".zip") || s.contains(".rar") || s.contains(".7z") ||
                s.contains("download") || s.contains("下载") || s.contains("attachment") || s.contains("file") || s.contains("md5") || s.contains("/book/");
    }

    private boolean isDirectFile(String url) {
        String s = url.toLowerCase(Locale.ROOT);
        return s.contains(".pdf") || s.contains(".epub") || s.contains(".zip") || s.contains(".rar") || s.contains(".7z");
    }

    private void showItems() {
        resultBox.removeAllViews();
        if (items.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("当前页没有识别到明显下载链接。可进入详情页，或用“等待并点下载”。");
            none.setTextSize(12);
            none.setTextColor(Color.parseColor("#5B6475"));
            resultBox.addView(none);
            return;
        }
        int max = Math.min(items.size(), 8);
        for (int i = 0; i < max; i++) {
            Item it = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(6), dp(8), dp(6));
            card.setBackground(boxBg(Color.WHITE, "#D8E0EC"));

            TextView tv = new TextView(this);
            tv.setText(it.type + "  匹配度：" + it.score + "%\n" + it.title + "\n" + it.url);
            tv.setTextSize(11);
            tv.setTextColor(Color.parseColor("#263248"));
            card.addView(tv);

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(4), 0, 0);
            Button down = button(isDirectFile(it.url) ? "直接下载" : "尝试下载", true);
            down.setOnClickListener(v -> {
                WebView w = currentWebView();
                String ua = w == null ? null : w.getSettings().getUserAgentString();
                downloadWithCookies(it.url, safeName(it.title), null, ua);
            });
            row1.addView(down, weight());
            Button openIn = button("当前打开", false);
            openIn.setOnClickListener(v -> { WebView w = currentWebView(); if (w != null) w.loadUrl(it.url); });
            row1.addView(openIn, weight());
            Button openNew = button("新窗口", false);
            openNew.setOnClickListener(v -> addTab(it.url, true));
            row1.addView(openNew, weight());
            card.addView(row1);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(6));
            resultBox.addView(card, lp);
        }
    }

    private void copyLink(String url) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("download-link", url));
        toast("已复制链接");
    }

    private void downloadWithCookies(String url, String fileName, String mimetype, String userAgent) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("Cookie", cookies);
            if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
            String safe = safeName(fileName);
            req.setTitle(safe);
            req.setDescription("书名自动检索下载助手");
            if (mimetype != null) req.setMimeType(mimetype);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safe);
            if (dm != null) dm.enqueue(req);
            toast("已交给系统下载器；若无进度，请用网页打开确认");
            log("下载尝试：" + url);
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
            log("下载失败：" + e.getMessage());
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

    private int score(String key, String cand) {
        String a = norm(key), b = norm(cand);
        if (a.length() == 0 || b.length() == 0) return 0;
        int hit = b.contains(a) ? 55 : 0;
        String[] tokens = a.split(" ");
        int n = 0;
        for (String t : tokens) if (t.length() >= 2 && b.contains(t)) n++;
        hit += tokens.length == 0 ? 0 : (35 * n / tokens.length);
        return Math.min(100, hit);
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[《》【】\\[\\]（）(){}<>.,，。:：;；!！?？_\\-]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeUrl(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        if (url.length() == 0) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:")) url = "https://" + url;
        return url;
    }

    private String decodeJsString(String value) {
        if (value == null || "null".equals(value)) return "";
        try { return new JSONArray("[" + value + "]").getString(0); } catch (Exception e) { return value; }
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) { return true; }
    }

    private String normalizeForCompare(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。、《》：；！￥（）【】]+", "");
    }

    private String limit(String value, int max) { return value == null || value.length() <= max ? (value == null ? "" : value) : value.substring(0, max); }
    private String shorten(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max) + "..."; }
    private void log(String s) { logView.setText(s + "\n" + logView.getText().toString()); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static class BrowserTab {
        WebView webView;
        String url;
        String title = "窗口";
        String aiResult = "";
        BrowserTab(WebView w, String u) { webView = w; url = u; }
        String displayTitle() { return title == null || title.trim().length() == 0 ? url : title; }
    }

    private static class AiResult {
        boolean match;
        int confidence;
        String matchedTitle = "";
        String reason = "";
    }

    private static class Item {
        String title;
        String linkText;
        String url;
        int score;
        String type;
        Item(String t, String l, String u, int s, String ty) { title = t; linkText = l; url = u; score = s; type = ty; }
    }
}

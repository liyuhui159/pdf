package com.example.bookautodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class WebAnalyzeActivity extends Activity {
    private EditText keywordInput;
    private EditText urlInput;
    private WebView webView;
    private LinearLayout resultBox;
    private TextView logView;
    private final ArrayList<Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        setupWebView();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("网页登录分析下载链接");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#1F2A44"));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = new TextView(this);
        tip.setText("用于需要登录的网站：先在下面网页里手动登录/搜索，再点“分析当前页链接”。仅用于有权下载和分发的资料源。");
        tip.setTextSize(12);
        tip.setTextColor(Color.parseColor("#5B6475"));
        tip.setPadding(0, dp(6), 0, dp(6));
        root.addView(tip);

        keywordInput = input("书名 / 资料名，可读取剪贴板", 1);
        root.addView(keywordInput, new LinearLayout.LayoutParams(-1, -2));

        urlInput = input("搜索网址，可含 {keyword}，例如：https://example.com/search?q={keyword}", 2);
        urlInput.setText("https://example.com/search?q={keyword}");
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, dp(6), 0, dp(6));
        root.addView(row1);
        Button clip = button("读剪贴板", true);
        clip.setOnClickListener(v -> readClipboard());
        row1.addView(clip, weight());
        Button open = button("打开/搜索", true);
        open.setOnClickListener(v -> openUrl());
        row1.addView(open, weight());
        Button analyze = button("分析当前页链接", true);
        analyze.setOnClickListener(v -> analyzePage());
        row1.addView(analyze, weight());

        webView = new WebView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        root.addView(webView, wlp);

        ScrollView rs = new ScrollView(this);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        rs.addView(resultBox);
        root.addView(rs, new LinearLayout.LayoutParams(-1, dp(190)));

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.parseColor("#39445A"));
        root.addView(logView, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
    }

    private EditText input(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setTextSize(15);
        e.setPadding(dp(9), dp(7), dp(9), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.parseColor("#D8E0EC"));
        e.setBackground(bg);
        return e;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Color.parseColor("#2F6BFF"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? Color.parseColor("#2F6BFF") : Color.parseColor("#EEF3FF"));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
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
        log("读取剪贴板：" + text.toString().trim());
    }

    private void openUrl() {
        try {
            String keyword = keywordInput.getText().toString().trim();
            String tpl = urlInput.getText().toString().trim().replace("｛", "{").replace("｝", "}");
            if (tpl.length() == 0) { toast("请填写网址"); return; }
            String url = tpl;
            if (tpl.contains("{keyword}")) {
                url = tpl.replace("{keyword}", URLEncoder.encode(keyword, "UTF-8"));
            }
            webView.loadUrl(url);
            log("打开：" + url);
        } catch (Exception e) {
            toast("打开失败：" + e.getMessage());
        }
    }

    private void analyzePage() {
        String js = "(function(){var a=[].slice.call(document.querySelectorAll('a'));return JSON.stringify(a.map(function(x){return {t:(x.innerText||x.textContent||x.title||'').trim(),u:x.href||''};}).filter(function(x){return x.u;}));})()";
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                parseJsResult(value);
            }
        });
    }

    private void parseJsResult(String value) {
        items.clear();
        resultBox.removeAllViews();
        try {
            String json = value;
            if (json != null && json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
                json = json.substring(1, json.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            }
            JSONArray arr = new JSONArray(json);
            String key = keywordInput.getText().toString().trim();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String title = o.optString("t", "");
                String url = o.optString("u", "");
                if (!isCandidate(title, url)) continue;
                int score = score(key, title + " " + url);
                items.add(new Item(title.length() == 0 ? guessName(url) : title, url, score));
            }
            items.sort((a, b) -> b.score - a.score);
            showItems();
            log("当前页候选下载链接：" + items.size() + " 个");
        } catch (Exception e) {
            toast("分析失败：" + e.getMessage());
            log("分析失败：" + e.getMessage());
        }
    }

    private boolean isCandidate(String title, String url) {
        String s = (title + " " + url).toLowerCase(Locale.ROOT);
        return s.contains(".pdf") || s.contains(".zip") || s.contains(".rar") || s.contains(".7z") ||
                s.contains("download") || s.contains("下载") || s.contains("attachment") || s.contains("file");
    }

    private void showItems() {
        resultBox.removeAllViews();
        if (items.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("当前页没有识别到明显下载链接。请先登录、进入详情页或点击到下载页面后再分析。");
            resultBox.addView(none);
            return;
        }
        int max = Math.min(items.size(), 20);
        for (int i = 0; i < max; i++) {
            Item it = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView tv = new TextView(this);
            tv.setText("匹配度：" + it.score + "%\n" + it.title + "\n" + it.url);
            tv.setTextSize(12);
            card.addView(tv);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button down = button("下载", true);
            down.setOnClickListener(v -> download(it.url));
            row.addView(down, weight());
            Button open = button("打开", false);
            open.setOnClickListener(v -> webView.loadUrl(it.url));
            row.addView(open, weight());
            card.addView(row);
            resultBox.addView(card);
        }
    }

    private void download(String url) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = safeName(guessName(url));
            req.setTitle(fileName);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            dm.enqueue(req);
            toast("已开始下载到 Download 文件夹");
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

    private int score(String key, String cand) {
        String a = norm(key), b = norm(cand);
        if (a.length() == 0 || b.length() == 0) return 0;
        int hit = b.contains(a) ? 50 : 0;
        String[] tokens = a.split(" ");
        int n = 0;
        for (String t : tokens) if (t.length() >= 2 && b.contains(t)) n++;
        hit += tokens.length == 0 ? 0 : (35 * n / tokens.length);
        return Math.min(100, hit);
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[《》【】\\[\\]（）(){}<>.,，。:：;；!！?？_\\-]+", " ").replaceAll("\\s+", " ").trim();
    }

    private void log(String s) { logView.setText(s + "\n" + logView.getText().toString()); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static class Item {
        String title;
        String url;
        int score;
        Item(String t, String u, int s) { title = t; url = u; score = s; }
    }
}

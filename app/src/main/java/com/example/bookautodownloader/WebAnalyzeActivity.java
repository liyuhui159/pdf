package com.example.bookautodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
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
import java.util.LinkedHashSet;
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
        tip.setText("先手动登录/搜索/进入详情页，再点“分析当前页链接”。结果会尽量显示每本书的书名。下载不动时用“网页打开”或“浏览器打开”手动确认。");
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
        root.addView(rs, new LinearLayout.LayoutParams(-1, dp(235)));

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
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                downloadWithCookies(url, name, mimetype, userAgent);
            }
        });
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
        bg.setStroke(dp(1), primary ? Color.parseColor("#2F6BFF") : Color.parseColor("#C9D8FF"));
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
            if (tpl.contains("{keyword}")) url = tpl.replace("{keyword}", URLEncoder.encode(keyword, "UTF-8"));
            webView.loadUrl(url);
            log("打开：" + url);
        } catch (Exception e) {
            toast("打开失败：" + e.getMessage());
        }
    }

    private void analyzePage() {
        String js = "(function(){" +
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
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) { parseJsResult(value); }
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
                items.add(new Item(displayTitle, linkText, url, score, type));
            }
            items.sort((a, b) -> b.score - a.score);
            showItems();
            log("当前页候选链接：" + items.size() + " 个");
        } catch (Exception e) {
            toast("分析失败：" + e.getMessage());
            log("分析失败：" + e.getMessage());
        }
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
            none.setText("当前页没有识别到明显下载链接。可以进入书籍详情页、点一次页面上的 Download 按钮，或用浏览器打开后再下载。");
            none.setTextColor(Color.parseColor("#5B6475"));
            resultBox.addView(none);
            return;
        }
        int max = Math.min(items.size(), 25);
        for (int i = 0; i < max; i++) {
            Item it = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), Color.parseColor("#D8E0EC"));
            card.setBackground(bg);

            TextView tv = new TextView(this);
            tv.setText("书名/标题：" + it.title + "\n类型：" + it.type + "    匹配度：" + it.score + "%\n链接文字：" + it.linkText + "\n链接：" + it.url);
            tv.setTextSize(12);
            tv.setTextColor(Color.parseColor("#263248"));
            card.addView(tv);

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(6), 0, 0);
            Button down = button(isDirectFile(it.url) ? "直接下载" : "尝试下载", true);
            down.setOnClickListener(v -> downloadWithCookies(it.url, safeName(it.title), null, webView.getSettings().getUserAgentString()));
            row1.addView(down, weight());
            Button openIn = button("网页打开", false);
            openIn.setOnClickListener(v -> webView.loadUrl(it.url));
            row1.addView(openIn, weight());
            card.addView(row1);

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, dp(4), 0, dp(8));
            Button browser = button("浏览器打开", false);
            browser.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url))));
            row2.addView(browser, weight());
            Button copy = button("复制链接", false);
            copy.setOnClickListener(v -> copyLink(it.url));
            row2.addView(copy, weight());
            card.addView(row2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(8));
            resultBox.addView(card, lp);
        }
    }

    private void copyLink(String url) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("download-link", url));
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
            dm.enqueue(req);
            toast("已交给系统下载器；若无进度，请用“网页打开/浏览器打开”");
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

    private void log(String s) { logView.setText(s + "\n" + logView.getText().toString()); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static class Item {
        String title;
        String linkText;
        String url;
        int score;
        String type;
        Item(String t, String l, String u, int s, String ty) { title = t; linkText = l; url = u; score = s; type = ty; }
    }
}

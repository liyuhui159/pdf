package com.example.bookautodownloader;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;

public class WebAnalyzeActivity extends Activity {
    private EditText keywordInput;
    private EditText urlInput;
    private WebView webView;
    private LinearLayout resultBox;
    private TextView logView;
    private final ArrayList<Item> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean waitRunning = false;
    private int waitTicks = 0;
    private String lastPageUrl = "";
    private String activeBlobToken = "";
    private final BlobBridge blobBridge = new BlobBridge();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        setupWebView();
        requestLegacyStoragePermissionIfNeeded();

        Intent intent = getIntent();
        String initialKeyword = intent == null ? null : intent.getStringExtra("keyword");
        String initialUrl = intent == null ? null : intent.getStringExtra("url");
        if (initialKeyword != null && !initialKeyword.trim().isEmpty()) {
            keywordInput.setText(initialKeyword.trim());
        }
        if (initialUrl != null && !initialUrl.trim().isEmpty()) {
            urlInput.setText(initialUrl.trim());
            webView.loadUrl(initialUrl.trim());
            log("打开传入页面：" + initialUrl.trim());
        }
    }

    @Override
    protected void onDestroy() {
        waitRunning = false;
        closeBlobOutput(false, "页面已关闭");
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBlob");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
        }
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
        tip.setText("先登录并进入书籍详情页，再分析链接。页面链接会先在网页中打开；只有识别到真实文件地址后才交给系统下载器。支持 Cookie、Referer、新窗口和 blob 文件。");
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
        row1.setPadding(0, dp(6), 0, dp(4));
        root.addView(row1);
        Button clip = button("读剪贴板", true);
        clip.setOnClickListener(v -> readClipboard());
        row1.addView(clip, weight());
        Button open = button("打开/搜索", true);
        open.setOnClickListener(v -> openUrl());
        row1.addView(open, weight());
        Button analyze = button("分析当前页", true);
        analyze.setOnClickListener(v -> analyzePage());
        row1.addView(analyze, weight());

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(2), 0, dp(6));
        root.addView(row2);
        Button waitBtn = button("等待并点下载", true);
        waitBtn.setOnClickListener(v -> startWaitDownload());
        row2.addView(waitBtn, weight());
        Button stopBtn = button("停止等待", false);
        stopBtn.setOnClickListener(v -> stopWaitDownload());
        row2.addView(stopBtn, weight());
        Button backBtn = button("网页返回", false);
        backBtn.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        row2.addView(backBtn, weight());

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
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleSpecialUrl(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleSpecialUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                lastPageUrl = url == null ? "" : url;
                if (url != null) log("页面加载完成：" + url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView popup = new WebView(WebAnalyzeActivity.this);
                popup.getSettings().setJavaScriptEnabled(true);
                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        popup.destroy();
                        if (!handleSpecialUrl(url)) webView.loadUrl(url);
                        return true;
                    }

                    @SuppressWarnings("deprecation")
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        popup.destroy();
                        if (!handleSpecialUrl(url)) webView.loadUrl(url);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                startResolvedDownload(url, name, mimetype, userAgent, contentLength);
            }
        });
    }

    private boolean handleSpecialUrl(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")) {
            return false;
        }
        if (lower.startsWith("blob:")) {
            startBlobDownload(url, "download_file", "application/octet-stream");
            return true;
        }
        if (lower.startsWith("data:")) {
            saveDataUrl(url, "download_file");
            return true;
        }
        try {
            if (lower.startsWith("intent://")) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && !fallback.isEmpty()) {
                    webView.loadUrl(fallback);
                } else {
                    startActivity(intent);
                }
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception e) {
            log("无法处理外部链接：" + e.getMessage());
        }
        return true;
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void readClipboard() {
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
        if (text == null || text.toString().trim().isEmpty()) {
            toast("未读到文字");
            return;
        }
        keywordInput.setText(text.toString().trim());
        log("读取剪贴板：" + text.toString().trim());
    }

    private void openUrl() {
        try {
            String keyword = keywordInput.getText().toString().trim();
            String tpl = urlInput.getText().toString().trim().replace("｛", "{").replace("｝", "}");
            if (tpl.isEmpty()) {
                toast("请填写网址");
                return;
            }
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

    private void startWaitDownload() {
        if (waitRunning) {
            toast("已经在等待下载按钮");
            return;
        }
        waitRunning = true;
        waitTicks = 0;
        toast("开始监测下载按钮，最多等待3分钟");
        log("等待页面倒计时结束并点击已启用的下载按钮。不会绕过验证码或登录限制。");
        handler.post(waitRunnable);
    }

    private void stopWaitDownload() {
        waitRunning = false;
        handler.removeCallbacks(waitRunnable);
        toast("已停止等待");
        log("已停止等待下载按钮");
    }

    private final Runnable waitRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waitRunning) return;
            waitTicks++;
            checkDownloadButtonOnce();
            if (waitRunning && waitTicks < 180) {
                handler.postDelayed(this, 1000);
            } else if (waitTicks >= 180) {
                waitRunning = false;
                log("等待超时：3分钟内没有检测到可用下载按钮。");
            }
        }
    };

    private void checkDownloadButtonOnce() {
        String js = "(function(){" +
                "function c(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function visible(e){try{var r=e.getBoundingClientRect(),st=getComputedStyle(e);return r.width>0&&r.height>0&&st.display!='none'&&st.visibility!='hidden';}catch(x){return true;}}" +
                "var body=c(document.body?document.body.innerText:'');" +
                "var m=body.match(/(\\d+)\\s*(秒|s|sec|second|seconds)/i),sec=m?m[1]:'';" +
                "var nodes=[].slice.call(document.querySelectorAll('a,button,input[type=button],input[type=submit]'));" +
                "for(var i=0;i<nodes.length;i++){var e=nodes[i],t=c(e.innerText||e.textContent||e.value||e.title||e.getAttribute('aria-label')||''),u=e.href||e.getAttribute('href')||'',all=(t+' '+u).toLowerCase();" +
                "if(!visible(e)||e.disabled||e.getAttribute('disabled')!==null||e.getAttribute('aria-disabled')==='true')continue;" +
                "if(all.indexOf('captcha')>=0||all.indexOf('验证码')>=0||all.indexOf('登录')>=0||all.indexOf('login')>=0)continue;" +
                "var direct=/\\.(pdf|epub|mobi|azw3|zip|rar|7z)([?#]|$)/i.test(u);" +
                "var named=/(download|下载|slow download|free download|保存文件)/i.test(t);" +
                "if(direct||named){e.click();return JSON.stringify({clicked:true,text:t,url:u,sec:sec});}" +
                "}" +
                "return JSON.stringify({clicked:false,sec:sec});" +
                "})()";
        webView.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                try {
                    JSONObject o = new JSONObject(unwrapJsonString(value));
                    if (o.optBoolean("clicked", false)) {
                        waitRunning = false;
                        handler.removeCallbacks(waitRunnable);
                        String text = o.optString("text", "下载按钮");
                        log("已点击可用下载按钮：" + text);
                        toast("已点击下载按钮，正在等待真实文件地址");
                    } else if (waitTicks % 5 == 0) {
                        String sec = o.optString("sec", "");
                        log(sec.isEmpty() ? "等待中：未发现可点击下载按钮" : "等待中，页面倒计时约 " + sec + " 秒");
                    }
                } catch (Exception e) {
                    if (waitTicks % 10 == 0) log("按钮检测返回异常：" + e.getMessage());
                }
            }
        });
    }

    private String unwrapJsonString(String value) {
        String json = value == null ? "" : value;
        if (json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return json;
    }

    private void analyzePage() {
        String js = "(function(){" +
                "function clean(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function goodText(s){s=clean(s);if(!s)return '';if(s.length>180)s=s.substring(0,180);return s;}" +
                "function titleOf(a){var n=a;for(var d=0;n&&d<6;d++,n=n.parentElement){var hs=n.querySelectorAll('h1,h2,h3,h4,.title,[class*=title],[class*=book],[class*=name]');for(var i=0;i<hs.length;i++){var t=goodText(hs[i].innerText||hs[i].textContent);if(t&&t.length>3)return t;}}var t=goodText(a.innerText||a.textContent||a.title||a.getAttribute('aria-label'));if(t&&t.length>3)return t;n=a;var best='';for(var j=0;n&&j<5;j++,n=n.parentElement){var tx=goodText(n.innerText||n.textContent);if(tx.length>best.length)best=tx;}return best;}" +
                "var arr=[].slice.call(document.querySelectorAll('a'));" +
                "return JSON.stringify(arr.map(function(a){return {t:titleOf(a),lt:clean(a.innerText||a.textContent||a.title||''),u:a.href||'',cls:a.className||''};}).filter(function(x){return x.u;}));" +
                "})()";
        webView.evaluateJavascript(js, value -> parseJsResult(value));
    }

    private void parseJsResult(String value) {
        items.clear();
        resultBox.removeAllViews();
        try {
            JSONArray arr = new JSONArray(unwrapJsonString(value));
            String key = keywordInput.getText().toString().trim();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String title = o.optString("t", "");
                String linkText = o.optString("lt", "");
                String url = o.optString("u", "");
                if (url.isEmpty() || seen.contains(url)) continue;
                if (!isCandidate(title, linkText, url)) continue;
                seen.add(url);
                String displayTitle = pickTitle(title, linkText, url);
                int score = score(key, displayTitle + " " + linkText + " " + url);
                String type = isDirectFile(url) || url.startsWith("blob:") || url.startsWith("data:")
                        ? "真实文件/文件流" : "详情页/下载页";
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
        return t.isEmpty() ? guessName(url) : t;
    }

    private String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private boolean isCandidate(String title, String linkText, String url) {
        String s = (title + " " + linkText + " " + url).toLowerCase(Locale.ROOT);
        return s.contains(".pdf") || s.contains(".epub") || s.contains(".mobi") ||
                s.contains(".azw3") || s.contains(".zip") || s.contains(".rar") ||
                s.contains(".7z") || s.contains("download") || s.contains("下载") ||
                s.contains("attachment") || s.contains("blob:") || s.contains("/book/");
    }

    private boolean isDirectFile(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return true;
        try {
            String path = Uri.parse(url).getPath();
            if (path == null) return false;
            return path.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|epub|mobi|azw3|zip|rar|7z)$");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showItems() {
        resultBox.removeAllViews();
        if (items.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("当前页没有识别到明显下载链接。请进入书籍详情页，或直接点击网页中的下载按钮。");
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
            tv.setText("书名/标题：" + it.title + "\n类型：" + it.type + "    匹配度：" + it.score +
                    "%\n链接文字：" + it.linkText + "\n链接：" + it.url);
            tv.setTextSize(12);
            tv.setTextColor(Color.parseColor("#263248"));
            card.addView(tv);

            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setPadding(0, dp(6), 0, 0);
            boolean direct = isDirectFile(it.url);
            Button action = button(direct ? "下载文件" : "进入下载页", true);
            action.setOnClickListener(v -> {
                if (isDirectFile(it.url)) {
                    startResolvedDownload(it.url, safeName(it.title), null,
                            webView.getSettings().getUserAgentString(), -1);
                } else {
                    webView.loadUrl(it.url);
                    log("进入候选下载页：" + it.url);
                }
            });
            row1.addView(action, weight());
            Button openIn = button("网页打开", false);
            openIn.setOnClickListener(v -> webView.loadUrl(it.url));
            row1.addView(openIn, weight());
            card.addView(row1);

            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.setPadding(0, dp(4), 0, dp(8));
            Button browser = button("浏览器打开", false);
            browser.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url)));
                } catch (Exception e) {
                    toast("没有可打开该链接的应用");
                }
            });
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
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("download-link", url));
        toast("已复制链接");
    }

    private void startResolvedDownload(String url, String fileName, String mimetype,
                                       String userAgent, long contentLength) {
        if (url == null || url.trim().isEmpty()) {
            toast("下载地址为空");
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:")) {
            startBlobDownload(url, fileName, mimetype);
            return;
        }
        if (lower.startsWith("data:")) {
            saveDataUrl(url, fileName);
            return;
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            toast("该地址不是可直接下载的网页文件");
            return;
        }
        downloadWithSession(url, fileName, mimetype, userAgent, contentLength);
    }

    private void downloadWithSession(String url, String fileName, String mimetype,
                                     String userAgent, long contentLength) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("系统下载服务不可用");

            String finalName = chooseDownloadName(url, fileName, mimetype);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(false);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setTitle(finalName);
            req.setDescription("书名自动检索下载助手");
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalName);
            if (mimetype != null && !mimetype.trim().isEmpty()) req.setMimeType(mimetype);

            CookieManager.getInstance().flush();
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) req.addRequestHeader("Cookie", cookies);
            if (userAgent != null && !userAgent.isEmpty()) req.addRequestHeader("User-Agent", userAgent);
            String referer = webView.getUrl();
            if (referer == null || referer.isEmpty()) referer = lastPageUrl;
            if (referer != null && !referer.isEmpty()) req.addRequestHeader("Referer", referer);
            req.addRequestHeader("Accept", "*/*");

            long id = dm.enqueue(req);
            toast("已提交下载：" + finalName);
            log("下载任务已创建，ID=" + id + "，文件=" + finalName +
                    (contentLength > 0 ? "，大小约 " + readableBytes(contentLength) : ""));
            monitorDownload(dm, id, finalName, 0);
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
            log("下载失败：" + e.getMessage());
        }
    }

    private void monitorDownload(DownloadManager dm, long id, String fileName, int tick) {
        handler.postDelayed(() -> {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            try (Cursor cursor = dm.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    if (tick < 10) monitorDownload(dm, id, fileName, tick + 1);
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    toast("下载完成：" + fileName);
                    log("下载完成：" + fileName + (localUri == null ? "" : "\n位置：" + localUri));
                } else if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    toast("下载失败：" + downloadReason(reason));
                    log("下载失败：" + fileName + "，原因=" + downloadReason(reason) + "（" + reason + "）");
                } else {
                    if (tick % 3 == 0) {
                        String progress = total > 0 ? (100 * done / total) + "%" : readableBytes(done);
                        log("下载中：" + fileName + "，进度 " + progress);
                    }
                    monitorDownload(dm, id, fileName, tick + 1);
                }
            } catch (Exception e) {
                if (tick < 5) monitorDownload(dm, id, fileName, tick + 1);
                else log("无法读取下载状态：" + e.getMessage());
            }
        }, tick < 3 ? 1000 : 2500);
    }

    private String downloadReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "服务器不支持续传或连接中断";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "存储设备不可用";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "同名文件已存在";
            case DownloadManager.ERROR_FILE_ERROR:
                return "文件写入失败";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP 数据错误";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "存储空间不足";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "重定向次数过多";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "服务器返回未处理的 HTTP 状态";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "未知错误，可能是登录信息、Referer 或链接已失效";
        }
    }

    private void startBlobDownload(String blobUrl, String fileName, String mimetype) {
        if (webView == null) return;
        closeBlobOutput(false, null);
        activeBlobToken = UUID.randomUUID().toString();
        webView.removeJavascriptInterface("AndroidBlob");
        webView.addJavascriptInterface(blobBridge, "AndroidBlob");
        String token = JSONObject.quote(activeBlobToken);
        String url = JSONObject.quote(blobUrl);
        String name = JSONObject.quote(chooseDownloadName(blobUrl, fileName, mimetype));
        String mime = JSONObject.quote(mimetype == null ? "application/octet-stream" : mimetype);
        String js = "(async function(){try{" +
                "var r=await fetch(" + url + ");var b=await r.blob();" +
                "AndroidBlob.begin(" + token + "," + name + ",b.type||" + mime + ",b.size);" +
                "async function sendBytes(bytes){var step=98304;for(var p=0;p<bytes.length;p+=step){var part=bytes.subarray(p,Math.min(bytes.length,p+step)),bin='';for(var i=0;i<part.length;i++)bin+=String.fromCharCode(part[i]);AndroidBlob.chunk(" + token + ",btoa(bin));await new Promise(function(ok){setTimeout(ok,0);});}}" +
                "if(b.stream&&b.stream().getReader){var reader=b.stream().getReader();while(true){var x=await reader.read();if(x.done)break;await sendBytes(x.value);}}else{await sendBytes(new Uint8Array(await b.arrayBuffer()));}" +
                "AndroidBlob.finish(" + token + ");" +
                "}catch(e){AndroidBlob.fail(" + token + ",String(e));}})();";
        log("开始接收网页 blob 文件：" + chooseDownloadName(blobUrl, fileName, mimetype));
        webView.evaluateJavascript(js, null);
    }

    private void saveDataUrl(String dataUrl, String fileName) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("data URL 格式错误");
            String meta = dataUrl.substring(5, comma);
            String data = dataUrl.substring(comma + 1);
            String mime = meta.split(";")[0];
            byte[] bytes = meta.contains(";base64")
                    ? Base64.decode(data, Base64.DEFAULT)
                    : URLDecoder.decode(data, "UTF-8").getBytes("UTF-8");
            String name = chooseDownloadName(dataUrl, fileName, mime);
            Uri uri = openDownloadOutput(name, mime);
            if (uri == null) throw new IllegalStateException("无法创建下载文件");
            try (OutputStream out = openOutputStreamForUri(uri)) {
                out.write(bytes);
            }
            finishPendingUri(uri);
            toast("下载完成：" + name);
            log("data 文件已保存：" + name);
        } catch (Exception e) {
            toast("data 文件保存失败：" + e.getMessage());
            log("data 文件保存失败：" + e.getMessage());
        }
    }

    private Uri openDownloadOutput(String fileName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE,
                    mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            return getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建 Download 文件夹");
        File file = uniqueFile(dir, fileName);
        return Uri.fromFile(file);
    }

    private OutputStream openOutputStreamForUri(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileOutputStream(new File(uri.getPath()));
        }
        OutputStream out = getContentResolver().openOutputStream(uri);
        if (out == null) throw new IllegalStateException("无法打开文件输出流");
        return out;
    }

    private void finishPendingUri(Uri uri) {
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                "content".equalsIgnoreCase(uri.getScheme())) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        }
    }

    private final class BlobBridge {
        private OutputStream out;
        private Uri uri;
        private String name;
        private long expected;
        private long written;

        @JavascriptInterface
        public void begin(String token, String fileName, String mime, long size) {
            if (!token.equals(activeBlobToken)) return;
            try {
                name = chooseDownloadName("blob:", fileName, mime);
                expected = size;
                written = 0;
                uri = openDownloadOutput(name, mime);
                if (uri == null) throw new IllegalStateException("无法创建下载文件");
                out = openOutputStreamForUri(uri);
                logSafe("blob 文件开始写入：" + name +
                        (expected > 0 ? "，大小约 " + readableBytes(expected) : ""));
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void chunk(String token, String base64) {
            if (!token.equals(activeBlobToken) || out == null) return;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                out.write(bytes);
                written += bytes.length;
                if (expected > 0 && written % (1024 * 1024) < bytes.length) {
                    logSafe("blob 下载进度：" + (100 * written / expected) + "%");
                }
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void finish(String token) {
            if (!token.equals(activeBlobToken)) return;
            try {
                if (out != null) out.close();
                out = null;
                finishPendingUri(uri);
                String completedName = name;
                uri = null;
                activeBlobToken = "";
                handler.post(() -> {
                    webView.removeJavascriptInterface("AndroidBlob");
                    toast("下载完成：" + completedName);
                    log("blob 文件已保存：" + completedName + "，共 " + readableBytes(written));
                });
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void fail(String token, String message) {
            if (!token.equals(activeBlobToken)) return;
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            out = null;
            uri = null;
            activeBlobToken = "";
            handler.post(() -> {
                if (webView != null) webView.removeJavascriptInterface("AndroidBlob");
                toast("blob 下载失败：" + message);
                log("blob 下载失败：" + message);
            });
        }

        private void closeSilently(boolean keepFile, String message) {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            if (!keepFile && uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            out = null;
            uri = null;
            name = null;
            expected = 0;
            written = 0;
            if (message != null) logSafe(message);
        }
    }

    private void closeBlobOutput(boolean keepFile, String message) {
        blobBridge.closeSilently(keepFile, message);
        activeBlobToken = "";
    }

    private String chooseDownloadName(String url, String fileName, String mime) {
        String name = safeName(fileName);
        if (name.equals("download_file") || name.equals("download_file.pdf")) {
            name = safeName(guessName(url));
        }
        if (!hasKnownExtension(name)) {
            String ext = extensionFromMime(mime);
            if (ext.isEmpty()) ext = extensionFromUrl(url);
            if (ext.isEmpty()) ext = ".bin";
            name += ext;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return uniqueFile(dir, name).getName();
    }

    private File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            file = new File(dir, base + " (" + i + ")" + ext);
            if (!file.exists()) return file;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private boolean hasKnownExtension(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|epub|mobi|azw3|zip|rar|7z|bin)$");
    }

    private String extensionFromUrl(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path == null) return "";
            String lower = path.toLowerCase(Locale.ROOT);
            String[] exts = {".pdf", ".epub", ".mobi", ".azw3", ".zip", ".rar", ".7z"};
            for (String ext : exts) if (lower.endsWith(ext)) return ext;
        } catch (Exception ignored) {
        }
        return "";
    }

    private String extensionFromMime(String mime) {
        if (mime == null) return "";
        String m = mime.toLowerCase(Locale.ROOT);
        if (m.contains("pdf")) return ".pdf";
        if (m.contains("epub")) return ".epub";
        if (m.contains("zip")) return ".zip";
        if (m.contains("rar")) return ".rar";
        if (m.contains("7z")) return ".7z";
        return "";
    }

    private String guessName(String url) {
        try {
            String p = Uri.parse(url).getLastPathSegment();
            if (p != null && !p.isEmpty()) return Uri.decode(p);
        } catch (Exception ignored) {
        }
        return "download_file";
    }

    private String safeName(String s) {
        s = s == null ? "download_file" : s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.isEmpty()) s = "download_file";
        if (s.length() > 120) s = s.substring(0, 120);
        return s;
    }

    private String readableBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private int score(String key, String cand) {
        String a = norm(key), b = norm(cand);
        if (a.isEmpty() || b.isEmpty()) return 0;
        int hit = b.contains(a) ? 55 : 0;
        String[] tokens = a.split(" ");
        int n = 0;
        for (String t : tokens) if (t.length() >= 2 && b.contains(t)) n++;
        hit += tokens.length == 0 ? 0 : (35 * n / tokens.length);
        return Math.min(100, hit);
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("[《》【】\\[\\]（）(){}<>.,，。:：;；!！?？_\\-]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private void logSafe(String s) {
        handler.post(() -> log(s));
    }

    private void log(String s) {
        String old = logView == null ? "" : logView.getText().toString();
        if (old.length() > 7000) old = old.substring(0, 4500);
        if (logView != null) logView.setText(s + "\n" + old);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static class Item {
        String title;
        String linkText;
        String url;
        int score;
        String type;

        Item(String t, String l, String u, int s, String ty) {
            title = t;
            linkText = l;
            url = u;
            score = s;
            type = ty;
        }
    }
}

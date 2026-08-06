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
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

/**
 * WebView download activity used by the WebAnalyzeActivity manifest alias.
 * It keeps login cookies, distinguishes pages from real files, supports
 * DownloadManager, blob/data URLs, popup links and visible download status.
 */
public class WebAnalyzeActivityV2 extends Activity {
    private static final String BRIDGE_NAME = "AndroidBlob";
    private static final int LEGACY_STORAGE_REQUEST = 2101;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<LinkItem> linkItems = new ArrayList<>();
    private final BlobBridge blobBridge = new BlobBridge();

    private EditText keywordInput;
    private EditText urlInput;
    private FrameLayout webContainer;
    private WebView webView;
    private LinearLayout resultBox;
    private TextView logView;

    private boolean waitRunning;
    private int waitTicks;
    private String lastPageUrl = "";
    private String activeBlobToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView(webView, true);
        requestLegacyStoragePermission();
        openIntentData();
    }

    @Override
    protected void onDestroy() {
        waitRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        blobBridge.abort(true);
        if (webView != null) {
            webView.removeJavascriptInterface(BRIDGE_NAME);
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

    private void requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    LEGACY_STORAGE_REQUEST
            );
        }
    }

    private void openIntentData() {
        Intent intent = getIntent();
        if (intent == null) return;
        String keyword = intent.getStringExtra("keyword");
        String url = intent.getStringExtra("url");
        if (keyword != null && !keyword.trim().isEmpty()) {
            keywordInput.setText(keyword.trim());
        }
        if (url != null && !url.trim().isEmpty()) {
            urlInput.setText(url.trim());
            webView.loadUrl(url.trim());
            log("打开传入页面：" + url.trim());
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("网页找书与文件下载");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#1F2A44"));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tip = new TextView(this);
        tip.setText("先登录并进入书籍详情页。详情页只在网页中打开，识别到真实文件后才下载；下载请求会保留 Cookie、User-Agent 和 Referer。");
        tip.setTextSize(12);
        tip.setTextColor(Color.parseColor("#5B6475"));
        tip.setPadding(0, dp(5), 0, dp(6));
        root.addView(tip);

        keywordInput = input("书名 / 资料名，可读取剪贴板", 1);
        root.addView(keywordInput);

        urlInput = input("搜索网址，可含 {keyword}", 2);
        urlInput.setText("https://example.com/search?q={keyword}");
        root.addView(urlInput);

        LinearLayout firstRow = horizontalRow();
        root.addView(firstRow);
        Button clipboard = button("读剪贴板", true);
        clipboard.setOnClickListener(v -> readClipboard());
        firstRow.addView(clipboard, weighted());
        Button open = button("打开/搜索", true);
        open.setOnClickListener(v -> openUrl());
        firstRow.addView(open, weighted());
        Button analyze = button("分析当前页", true);
        analyze.setOnClickListener(v -> analyzePage());
        firstRow.addView(analyze, weighted());

        LinearLayout secondRow = horizontalRow();
        root.addView(secondRow);
        Button wait = button("等待并点下载", true);
        wait.setOnClickListener(v -> startWaitDownload());
        secondRow.addView(wait, weighted());
        Button stop = button("停止等待", false);
        stop.setOnClickListener(v -> stopWaitDownload());
        secondRow.addView(stop, weighted());
        Button back = button("网页返回", false);
        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        secondRow.addView(back, weighted());

        webContainer = new FrameLayout(this);
        webView = new WebView(this);
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        ScrollView resultScroll = new ScrollView(this);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultScroll.addView(resultBox);
        root.addView(resultScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(225)
        ));

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Color.parseColor("#39445A"));
        root.addView(logView);

        setContentView(root);
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private void configureWebView(WebView view, boolean mainView) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        if (mainView) {
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            view.addJavascriptInterface(blobBridge, BRIDGE_NAME);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return routeUrl(request.getUrl().toString(), v != webView);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return routeUrl(url, v != webView);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                if (v == webView) {
                    lastPageUrl = url == null ? "" : url;
                    if (url != null) log("页面加载完成：" + url);
                }
            }
        });

        view.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                downloadAny(url, name, mimetype, userAgent, contentLength);
            }
        });

        if (mainView) {
            view.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(WebView source, boolean isDialog,
                                              boolean isUserGesture, Message resultMsg) {
                    if (!isUserGesture) return false;
                    WebView popup = new WebView(WebAnalyzeActivityV2.this);
                    configureWebView(popup, false);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(2), dp(2));
                    webContainer.addView(popup, params);
                    popup.setWebChromeClient(new WebChromeClient());
                    WebView.WebViewTransport transport =
                            (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(popup);
                    resultMsg.sendToTarget();
                    mainHandler.postDelayed(() -> removePopup(popup), 15000);
                    return true;
                }
            });
        }
    }

    private void removePopup(WebView popup) {
        if (popup == null) return;
        try {
            webContainer.removeView(popup);
            popup.stopLoading();
            popup.destroy();
        } catch (Exception ignored) {
        }
    }

    private boolean routeUrl(String url, boolean fromPopup) {
        if (url == null || url.trim().isEmpty()) return true;
        String lower = url.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            if (fromPopup) {
                webView.loadUrl(url);
                log("新窗口转入当前页面：" + url);
                return true;
            }
            return false;
        }
        if (lower.startsWith("about:")) return false;
        if (lower.startsWith("blob:") || lower.startsWith("data:")) {
            downloadAny(url, "download_file", null,
                    webView.getSettings().getUserAgentString(), -1);
            return true;
        }

        try {
            if (lower.startsWith("intent://")) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && !fallback.trim().isEmpty()) {
                    webView.loadUrl(fallback);
                } else {
                    startActivity(intent);
                }
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception e) {
            toast("无法打开外部链接");
            log("外部链接处理失败：" + e.getMessage());
        }
        return true;
    }

    private void readClipboard() {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) {
            toast("剪贴板为空");
            return;
        }
        ClipData data = manager.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) {
            toast("剪贴板为空");
            return;
        }
        CharSequence value = data.getItemAt(0).coerceToText(this);
        if (value == null || value.toString().trim().isEmpty()) {
            toast("没有读到文字");
            return;
        }
        keywordInput.setText(value.toString().trim());
        log("读取剪贴板：" + value.toString().trim());
    }

    private void openUrl() {
        try {
            String template = urlInput.getText().toString().trim()
                    .replace("｛", "{")
                    .replace("｝", "}");
            if (template.isEmpty()) {
                toast("请填写网址");
                return;
            }
            String keyword = keywordInput.getText().toString().trim();
            String url = template.contains("{keyword}")
                    ? template.replace("{keyword}", URLEncoder.encode(keyword, "UTF-8"))
                    : template;
            webView.loadUrl(url);
            log("打开：" + url);
        } catch (Exception e) {
            toast("打开失败：" + e.getMessage());
        }
    }

    private void analyzePage() {
        String script = "(function(){" +
                "function c(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function title(a){var n=a;for(var d=0;n&&d<5;d++,n=n.parentElement){" +
                "var h=n.querySelector('h1,h2,h3,h4,.title,[class*=title],[class*=book],[class*=name]');" +
                "if(h&&c(h.innerText||h.textContent).length>2)return c(h.innerText||h.textContent);}" +
                "return c(a.innerText||a.textContent||a.title||a.getAttribute('aria-label'));}" +
                "return JSON.stringify([].slice.call(document.querySelectorAll('a')).map(function(a){" +
                "return {t:title(a),l:c(a.innerText||a.textContent||a.title),u:a.href||''};" +
                "}).filter(function(x){return x.u;}));})()";
        webView.evaluateJavascript(script, this::parseAnalysisResult);
    }

    private void parseAnalysisResult(String raw) {
        resultBox.removeAllViews();
        linkItems.clear();
        try {
            JSONArray array = new JSONArray(unwrapJavascriptString(raw));
            String keyword = keywordInput.getText().toString().trim();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String title = clean(object.optString("t", ""));
                String label = clean(object.optString("l", ""));
                String url = object.optString("u", "");
                if (url.isEmpty() || !seen.add(url)) continue;
                if (!isCandidate(title, label, url)) continue;
                if (title.length() < 3) title = label;
                if (title.length() < 3) title = guessName(url);
                int score = score(keyword, title + " " + label + " " + url);
                linkItems.add(new LinkItem(title, label, url, score, isDirectFile(url)));
            }
            linkItems.sort((a, b) -> Integer.compare(b.score, a.score));
            showAnalysisResults();
            log("当前页候选链接：" + linkItems.size() + " 个");
        } catch (Exception e) {
            toast("分析失败：" + e.getMessage());
            log("分析失败：" + e.getMessage());
        }
    }

    private void showAnalysisResults() {
        resultBox.removeAllViews();
        if (linkItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有识别到下载链接。请进入书籍详情页，或直接点击网页中的下载按钮。");
            empty.setTextColor(Color.parseColor("#5B6475"));
            resultBox.addView(empty);
            return;
        }

        int count = Math.min(25, linkItems.size());
        for (int i = 0; i < count; i++) {
            LinkItem item = linkItems.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.WHITE);
            background.setCornerRadius(dp(9));
            background.setStroke(dp(1), Color.parseColor("#D8E0EC"));
            card.setBackground(background);

            TextView info = new TextView(this);
            info.setText("标题：" + item.title +
                    "\n类型：" + (item.direct ? "真实文件" : "详情页/下载页") +
                    "    匹配度：" + item.score + "%" +
                    "\n链接：" + item.url);
            info.setTextSize(12);
            info.setTextColor(Color.parseColor("#263248"));
            card.addView(info);

            LinearLayout actions = horizontalRow();
            Button primary = button(item.direct ? "下载文件" : "进入下载页", true);
            primary.setOnClickListener(v -> {
                if (item.direct) {
                    downloadAny(item.url, item.title, null,
                            webView.getSettings().getUserAgentString(), -1);
                } else {
                    webView.loadUrl(item.url);
                    log("进入下载页：" + item.url);
                }
            });
            actions.addView(primary, weighted());

            Button copy = button("复制链接", false);
            copy.setOnClickListener(v -> copyLink(item.url));
            actions.addView(copy, weighted());
            card.addView(actions);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, dp(7));
            resultBox.addView(card, params);
        }
    }

    private void copyLink(String url) {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("download-link", url));
        }
        toast("已复制链接");
    }

    private void startWaitDownload() {
        if (waitRunning) {
            toast("已经在等待下载按钮");
            return;
        }
        waitRunning = true;
        waitTicks = 0;
        log("开始等待网页中的下载按钮变为可点击，最多3分钟。");
        mainHandler.post(waitRunnable);
    }

    private void stopWaitDownload() {
        waitRunning = false;
        mainHandler.removeCallbacks(waitRunnable);
        toast("已停止等待");
        log("已停止等待下载按钮");
    }

    private final Runnable waitRunnable = new Runnable() {
        @Override
        public void run() {
            if (!waitRunning) return;
            waitTicks++;
            clickDownloadButtonOnce();
            if (waitRunning && waitTicks < 180) {
                mainHandler.postDelayed(this, 1000);
            } else if (waitTicks >= 180) {
                waitRunning = false;
                log("等待超时：没有检测到可用下载按钮。");
            }
        }
    };

    private void clickDownloadButtonOnce() {
        String script = "(function(){" +
                "function c(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function visible(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);" +
                "return r.width>0&&r.height>0&&s.display!='none'&&s.visibility!='hidden';}" +
                "var nodes=[].slice.call(document.querySelectorAll('a,button,input[type=button],input[type=submit]'));" +
                "for(var i=0;i<nodes.length;i++){var e=nodes[i],t=c(e.innerText||e.textContent||e.value||e.title)," +
                "u=e.href||e.getAttribute('href')||'',all=(t+' '+u).toLowerCase();" +
                "if(!visible(e)||e.disabled||e.getAttribute('disabled')!==null||e.getAttribute('aria-disabled')==='true')continue;" +
                "if(/captcha|验证码|登录|login/.test(all))continue;" +
                "if(/download|下载|保存文件|free download|slow download/.test(t)||" +
                "/\\.(pdf|epub|mobi|azw3|zip|rar|7z)([?#]|$)/i.test(u)){" +
                "e.click();return JSON.stringify({clicked:true,text:t,url:u});}}" +
                "return JSON.stringify({clicked:false});})()";
        webView.evaluateJavascript(script, value -> {
            try {
                JSONObject result = new JSONObject(unwrapJavascriptString(value));
                if (result.optBoolean("clicked", false)) {
                    waitRunning = false;
                    mainHandler.removeCallbacks(waitRunnable);
                    String text = result.optString("text", "下载按钮");
                    toast("已点击下载按钮");
                    log("已点击可用下载按钮：" + text);
                } else if (waitTicks % 5 == 0) {
                    log("等待中：尚未发现可点击下载按钮");
                }
            } catch (Exception e) {
                if (waitTicks % 10 == 0) log("下载按钮检测异常：" + e.getMessage());
            }
        });
    }

    private void downloadAny(String url, String suggestedName, String mime,
                             String userAgent, long contentLength) {
        if (url == null || url.trim().isEmpty()) {
            toast("下载地址为空");
            return;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:")) {
            downloadBlob(url, suggestedName, mime);
        } else if (lower.startsWith("data:")) {
            saveDataUrl(url, suggestedName);
        } else if (lower.startsWith("http://") || lower.startsWith("https://")) {
            downloadHttp(url, suggestedName, mime, userAgent, contentLength);
        } else {
            toast("不是可下载的文件地址");
        }
    }

    private void downloadHttp(String url, String suggestedName, String mime,
                              String userAgent, long contentLength) {
        try {
            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("系统下载服务不可用");

            String name = chooseName(url, suggestedName, mime);
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI
                            | DownloadManager.Request.NETWORK_MOBILE
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setTitle(name);
            request.setDescription("书名自动检索下载助手");
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    name
            );
            if (mime != null && !mime.trim().isEmpty()) request.setMimeType(mime);

            CookieManager.getInstance().flush();
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }
            String ua = userAgent;
            if (ua == null || ua.isEmpty()) {
                ua = webView.getSettings().getUserAgentString();
            }
            request.addRequestHeader("User-Agent", ua);
            String referer = webView.getUrl();
            if (referer == null || referer.isEmpty()) referer = lastPageUrl;
            if (referer != null && !referer.isEmpty()) {
                request.addRequestHeader("Referer", referer);
            }
            request.addRequestHeader("Accept", "*/*");

            long id = manager.enqueue(request);
            toast("已提交下载：" + name);
            log("下载任务 ID=" + id + "，文件=" + name
                    + (contentLength > 0 ? "，大小约 " + readableBytes(contentLength) : ""));
            monitorDownload(manager, id, name, 0);
        } catch (Exception e) {
            toast("下载失败：" + e.getMessage());
            log("下载创建失败：" + e.getMessage());
        }
    }

    private void monitorDownload(DownloadManager manager, long id,
                                 String name, int tick) {
        mainHandler.postDelayed(() -> {
            DownloadManager.Query query =
                    new DownloadManager.Query().setFilterById(id);
            try (Cursor cursor = manager.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) {
                    if (tick < 10) monitorDownload(manager, id, name, tick + 1);
                    return;
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS
                ));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                ));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                ));

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String localUri = cursor.getString(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_LOCAL_URI
                    ));
                    toast("下载完成：" + name);
                    log("下载完成：" + name
                            + (localUri == null ? "" : "\n位置：" + localUri));
                    return;
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_REASON
                    ));
                    String message = downloadReason(reason);
                    toast("下载失败：" + message);
                    log("下载失败：" + name + "，原因=" + message + "（" + reason + "）");
                    return;
                }

                if (tick % 3 == 0) {
                    String progress = total > 0
                            ? (100 * done / total) + "%"
                            : readableBytes(done);
                    log("下载中：" + name + "，进度 " + progress);
                }
                monitorDownload(manager, id, name, tick + 1);
            } catch (Exception e) {
                if (tick < 5) {
                    monitorDownload(manager, id, name, tick + 1);
                } else {
                    log("无法读取下载状态：" + e.getMessage());
                }
            }
        }, tick < 3 ? 1000 : 2500);
    }

    private String downloadReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "连接中断或服务器不支持续传";
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
                return "服务器返回了异常 HTTP 状态";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "未知错误，常见原因是登录会话失效、链接过期或防盗链";
        }
    }

    private void downloadBlob(String blobUrl, String suggestedName, String mime) {
        blobBridge.abort(true);
        activeBlobToken = UUID.randomUUID().toString();
        String token = JSONObject.quote(activeBlobToken);
        String quotedUrl = JSONObject.quote(blobUrl);
        String name = chooseName(blobUrl, suggestedName, mime);
        String quotedName = JSONObject.quote(name);
        String quotedMime = JSONObject.quote(
                mime == null ? "application/octet-stream" : mime
        );

        String script = "(async function(){try{" +
                "var response=await fetch(" + quotedUrl + ");" +
                "var blob=await response.blob();" +
                BRIDGE_NAME + ".begin(" + token + "," + quotedName + ",blob.type||" + quotedMime + ",blob.size);" +
                "async function send(bytes){var step=65536;for(var p=0;p<bytes.length;p+=step){" +
                "var part=bytes.subarray(p,Math.min(bytes.length,p+step)),bin='';" +
                "for(var i=0;i<part.length;i++)bin+=String.fromCharCode(part[i]);" +
                BRIDGE_NAME + ".chunk(" + token + ",btoa(bin));" +
                "await new Promise(function(ok){setTimeout(ok,0);});}}" +
                "if(blob.stream&&blob.stream().getReader){var reader=blob.stream().getReader();" +
                "while(true){var item=await reader.read();if(item.done)break;await send(item.value);}}" +
                "else{await send(new Uint8Array(await blob.arrayBuffer()));}" +
                BRIDGE_NAME + ".finish(" + token + ");" +
                "}catch(e){" + BRIDGE_NAME + ".fail(" + token + ",String(e));}})();";

        log("开始接收 blob 文件：" + name);
        webView.evaluateJavascript(script, null);
    }

    private void saveDataUrl(String dataUrl, String suggestedName) {
        Uri outputUri = null;
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("data URL 格式错误");
            String metadata = dataUrl.substring(5, comma);
            String data = dataUrl.substring(comma + 1);
            String mime = metadata.split(";")[0];
            byte[] bytes = metadata.contains(";base64")
                    ? Base64.decode(data, Base64.DEFAULT)
                    : URLDecoder.decode(data, "UTF-8").getBytes("UTF-8");
            String name = chooseName(dataUrl, suggestedName, mime);
            outputUri = createDownloadUri(name, mime);
            try (OutputStream output = openOutput(outputUri)) {
                output.write(bytes);
            }
            publishPendingUri(outputUri);
            toast("下载完成：" + name);
            log("data 文件已保存：" + name);
        } catch (Exception e) {
            deleteUriQuietly(outputUri);
            toast("data 文件保存失败：" + e.getMessage());
            log("data 文件保存失败：" + e.getMessage());
        }
    }

    private Uri createDownloadUri(String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE,
                    mime == null || mime.isEmpty()
                            ? "application/octet-stream"
                            : mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
            );
            if (uri == null) throw new IllegalStateException("无法创建下载文件");
            return uri;
        }

        File directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        );
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建 Download 文件夹");
        }
        return Uri.fromFile(uniqueFile(directory, name));
    }

    private OutputStream openOutput(Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("输出地址为空");
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileOutputStream(new File(uri.getPath()));
        }
        OutputStream output = getContentResolver().openOutputStream(uri);
        if (output == null) throw new IllegalStateException("无法打开文件输出流");
        return output;
    }

    private void publishPendingUri(Uri uri) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || !"content".equalsIgnoreCase(uri.getScheme())) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
    }

    private void deleteUriQuietly(Uri uri) {
        if (uri == null) return;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                File file = new File(uri.getPath());
                if (file.exists()) file.delete();
            } else {
                getContentResolver().delete(uri, null, null);
            }
        } catch (Exception ignored) {
        }
    }

    private final class BlobBridge {
        private OutputStream output;
        private Uri outputUri;
        private String fileName;
        private long expectedBytes;
        private long writtenBytes;

        @JavascriptInterface
        public void begin(String token, String name, String mime, long size) {
            if (!validToken(token)) return;
            try {
                fileName = chooseName("blob:", name, mime);
                expectedBytes = size;
                writtenBytes = 0;
                outputUri = createDownloadUri(fileName, mime);
                output = openOutput(outputUri);
                logOnMain("blob 文件开始写入：" + fileName
                        + (size > 0 ? "，大小约 " + readableBytes(size) : ""));
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void chunk(String token, String base64) {
            if (!validToken(token) || output == null) return;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                output.write(bytes);
                writtenBytes += bytes.length;
                if (expectedBytes > 0
                        && writtenBytes % (1024 * 1024) < bytes.length) {
                    logOnMain("blob 下载进度："
                            + (100 * writtenBytes / expectedBytes) + "%");
                }
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void finish(String token) {
            if (!validToken(token)) return;
            try {
                if (output != null) output.close();
                output = null;
                publishPendingUri(outputUri);
                String completedName = fileName;
                long completedBytes = writtenBytes;
                clearState(false);
                activeBlobToken = "";
                mainHandler.post(() -> {
                    toast("下载完成：" + completedName);
                    log("blob 文件已保存：" + completedName
                            + "，共 " + readableBytes(completedBytes));
                });
            } catch (Exception e) {
                fail(token, e.getMessage());
            }
        }

        @JavascriptInterface
        public void fail(String token, String message) {
            if (!validToken(token)) return;
            abort(true);
            activeBlobToken = "";
            mainHandler.post(() -> {
                toast("blob 下载失败：" + message);
                log("blob 下载失败：" + message);
            });
        }

        private boolean validToken(String token) {
            return token != null && token.equals(activeBlobToken)
                    && !activeBlobToken.isEmpty();
        }

        private void abort(boolean deletePartial) {
            try {
                if (output != null) output.close();
            } catch (Exception ignored) {
            }
            if (deletePartial) deleteUriQuietly(outputUri);
            clearState(false);
        }

        private void clearState(boolean keepUri) {
            output = null;
            if (!keepUri) outputUri = null;
            fileName = null;
            expectedBytes = 0;
            writtenBytes = 0;
        }
    }

    private String chooseName(String url, String suggestedName, String mime) {
        String name = safeName(suggestedName);
        if (name.equals("download_file") || name.equals("download_file.pdf")) {
            name = safeName(guessName(url));
        }
        if (!hasKnownExtension(name)) {
            String extension = extensionFromMime(mime);
            if (extension.isEmpty()) extension = extensionFromUrl(url);
            if (extension.isEmpty()) extension = ".bin";
            name += extension;
        }
        File directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        );
        return uniqueFile(directory, name).getName();
    }

    private File uniqueFile(File directory, String name) {
        File file = new File(directory, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            file = new File(directory, base + " (" + i + ")" + extension);
            if (!file.exists()) return file;
        }
        return new File(directory,
                base + "_" + System.currentTimeMillis() + extension);
    }

    private String safeName(String value) {
        String result = value == null
                ? "download_file"
                : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (result.isEmpty()) result = "download_file";
        if (result.length() > 110) result = result.substring(0, 110);
        return result;
    }

    private String guessName(String url) {
        try {
            String segment = Uri.parse(url).getLastPathSegment();
            if (segment != null && !segment.isEmpty()) return Uri.decode(segment);
        } catch (Exception ignored) {
        }
        return "download_file";
    }

    private boolean hasKnownExtension(String name) {
        return name.toLowerCase(Locale.ROOT)
                .matches(".*\\.(pdf|epub|mobi|azw3|zip|rar|7z|bin)$");
    }

    private String extensionFromUrl(String url) {
        try {
            String path = Uri.parse(url).getPath();
            if (path == null) return "";
            String lower = path.toLowerCase(Locale.ROOT);
            String[] extensions = {
                    ".pdf", ".epub", ".mobi", ".azw3",
                    ".zip", ".rar", ".7z"
            };
            for (String extension : extensions) {
                if (lower.endsWith(extension)) return extension;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String extensionFromMime(String mime) {
        if (mime == null) return "";
        String lower = mime.toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) return ".pdf";
        if (lower.contains("epub")) return ".epub";
        if (lower.contains("zip")) return ".zip";
        if (lower.contains("rar")) return ".rar";
        if (lower.contains("7z")) return ".7z";
        return "";
    }

    private boolean isDirectFile(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return true;
        try {
            String path = Uri.parse(url).getPath();
            return path != null && path.toLowerCase(Locale.ROOT)
                    .matches(".*\\.(pdf|epub|mobi|azw3|zip|rar|7z)$");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isCandidate(String title, String label, String url) {
        String text = (title + " " + label + " " + url)
                .toLowerCase(Locale.ROOT);
        return isDirectFile(url)
                || text.contains("download")
                || text.contains("下载")
                || text.contains("attachment")
                || text.contains("/book/")
                || text.contains("blob:");
    }

    private int score(String keyword, String candidate) {
        String a = normalize(keyword);
        String b = normalize(candidate);
        if (a.isEmpty() || b.isEmpty()) return 0;
        int result = b.contains(a) ? 55 : 0;
        String[] tokens = a.split(" ");
        int hits = 0;
        for (String token : tokens) {
            if (token.length() >= 2 && b.contains(token)) hits++;
        }
        result += tokens.length == 0 ? 0 : 35 * hits / tokens.length;
        return Math.min(100, result);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[《》【】\\[\\]（）(){}<>.,，。:：;；!！?？_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String unwrapJavascriptString(String value) {
        String result = value == null ? "" : value;
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return result;
    }

    private String readableBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private EditText input(String hint, int minLines) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setMinLines(minLines);
        editText.setTextSize(15);
        editText.setPadding(dp(9), dp(7), dp(9), dp(7));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), Color.parseColor("#D8E0EC"));
        editText.setBackground(background);
        return editText;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : Color.parseColor("#2F6BFF"));
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary
                ? Color.parseColor("#2F6BFF")
                : Color.parseColor("#EEF3FF"));
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), primary
                ? Color.parseColor("#2F6BFF")
                : Color.parseColor("#C9D8FF"));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void logOnMain(String message) {
        mainHandler.post(() -> log(message));
    }

    private void log(String message) {
        String old = logView == null ? "" : logView.getText().toString();
        if (old.length() > 7000) old = old.substring(0, 4500);
        if (logView != null) logView.setText(message + "\n" + old);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class LinkItem {
        final String title;
        final String label;
        final String url;
        final int score;
        final boolean direct;

        LinkItem(String title, String label, String url,
                 int score, boolean direct) {
            this.title = title;
            this.label = label;
            this.url = url;
            this.score = score;
            this.direct = direct;
        }
    }
}

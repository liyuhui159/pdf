package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.autofill.AutofillManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * User-controlled cloud login and authorized-link discovery.
 * NO app-side storage of usernames/passwords, NO DOM extraction of form fields,
 * NO interception of login requests or tokens, NO attempt to evade access controls.
 * Android's system Autofill provider owns credential saving and suggestions.
 */
public final class CloudLoginActivity extends Activity {
    public static final String EXTRA_URL = "cloud_url";
    public static final String EXTRA_KEYWORD = "keyword";

    private static final int BLUE = Color.rgb(40, 104, 244);
    private static final int INK = Color.rgb(29, 44, 71);
    private static final int MUTED = Color.rgb(103, 118, 142);
    private static final int LIGHT = Color.rgb(225, 235, 253);
    private static final int RED = Color.rgb(184, 60, 64);

    private WebView browser;
    private TextView status;
    private TextView address;
    private LinearLayout linksContainer;
    private ScrollView linksScroll;
    private ProgressBar progress;
    private String originalUrl;
    private String keyword;
    private boolean destroyed;

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable surface(int color, int edge) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(10));
        if (edge != 0) d.setStroke(dp(1), edge);
        return d;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(surface(primary ? BLUE : LIGHT, primary ? BLUE : Color.rgb(205, 221, 251)));
        return b;
    }

    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams flex(int weight, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), weight);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requested = getIntent().getStringExtra(EXTRA_URL);
        if (!LinkResolver.webUrl(requested)) {
            Toast.makeText(this, "登录网址无效", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        originalUrl = requested;
        keyword = getIntent().getStringExtra(EXTRA_KEYWORD);
        if (keyword == null) keyword = "";
        build();
        browser.loadUrl(originalUrl);
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 249, 254));
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout heading = row();
        TextView title = label("网盘登录与下载", 19, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("关闭", false);
        back.setOnClickListener(v -> finish());
        heading.addView(back, new LinearLayout.LayoutParams(dp(64), dp(38)));
        root.addView(heading);

        TextView tip = label("当前渠道：" + CloudLinkHandler.service(originalUrl)
                + "\n账号密码由安卓系统密码管理器保管；仅在你同意时自动填充。",
                12, MUTED);
        tip.setPadding(dp(5), dp(8), dp(5), dp(8));
        root.addView(tip);

        address = label(originalUrl, 11, MUTED);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        address.setPadding(dp(10), dp(11), dp(10), dp(11));
        address.setBackground(surface(Color.WHITE, Color.rgb(220, 231, 249)));
        root.addView(address, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout actions = row();
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(43));
        ap.setMargins(0, dp(7), 0, dp(5));
        root.addView(actions, ap);
        Button autofill = button("系统填充账号", true);
        autofill.setOnClickListener(v -> offerAutofill());
        actions.addView(autofill, flex(1, 40));
        Button saveSession = button("保存登录状态", false);
        saveSession.setOnClickListener(v -> {
            CookieManager.getInstance().flush();
            toast("网站会话已保留。下次若未过期可复用；账号密码由系统密码管理器管理");
        });
        actions.addView(saveSession, flex(1, 40));
        Button external = button("官方应用", false);
        external.setOnClickListener(v -> openExternal());
        actions.addView(external, flex(1, 40));

        LinearLayout toolbar = row();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(39));
        tp.setMargins(0, 0, 0, dp(4));
        root.addView(toolbar, tp);
        Button prev = button("后退", false);
        prev.setOnClickListener(v -> { if (browser.canGoBack()) browser.goBack(); });
        toolbar.addView(prev, flex(1, 37));
        Button next = button("前进", false);
        next.setOnClickListener(v -> { if (browser.canGoForward()) browser.goForward(); });
        toolbar.addView(next, flex(1, 37));
        Button refresh = button("刷新", false);
        refresh.setOnClickListener(v -> browser.reload());
        toolbar.addView(refresh, flex(1, 37));
        Button returnShare = button("返回分享", false);
        returnShare.setOnClickListener(v -> browser.loadUrl(originalUrl));
        toolbar.addView(returnShare, flex(1, 37));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        status = label("正在打开网站，请在官方网页登录并选择文件…", 12, BLUE);
        status.setPadding(dp(5), dp(7), dp(5), dp(6));
        root.addView(status);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21)
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        if (Build.VERSION.SDK_INT >= 26) {
            browser.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));

        Button extract = button("登录完成 · 识别可下载链接", true);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(46));
        ep.setMargins(0, dp(7), 0, dp(4));
        root.addView(extract, ep);
        extract.setOnClickListener(v -> inspectVisibleLinks());

        linksScroll = new ScrollView(this);
        linksScroll.setVisibility(View.GONE);
        linksScroll.setBackground(surface(Color.WHITE, Color.rgb(220, 231, 249)));
        linksContainer = new LinearLayout(this);
        linksContainer.setOrientation(LinearLayout.VERTICAL);
        linksContainer.setPadding(dp(8), dp(7), dp(8), dp(7));
        linksScroll.addView(linksContainer);
        root.addView(linksScroll, new LinearLayout.LayoutParams(-1, dp(175)));

        setContentView(root);

        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!LinkResolver.webUrl(url)) return;
                address.setText(url);
                // Persists only website cookies/session; never accesses form inputs.
                CookieManager.getInstance().flush();
                status.setText(CloudLinkHandler.looksLikeWebLogin(url)
                        ? "请在网站页面完成登录；支持系统密码管理器自动填充"
                        : "网页已加载。选择网盘内的文件或点击“识别可下载链接”");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req,
                                                   WebResourceError error) {
                if (req != null && req.isForMainFrame()) {
                    status.setTextColor(RED);
                    status.setText("网页加载失败：" + error.getDescription()
                            + "。可以尝试在官方浏览器或网盘客户端打开");
                }
            }
            @Override public void onReceivedHttpError(WebView view,
                    WebResourceRequest req, WebResourceResponse response) {
                if (req != null && req.isForMainFrame() && response.getStatusCode() >= 400) {
                    status.setTextColor(RED);
                    status.setText("HTTP " + response.getStatusCode()
                            + "：需要登录、授权，或网站限制了内置浏览器");
                }
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int pct) {
                progress.setProgress(pct);
            }
        });
        browser.setDownloadListener((url, agent, disposition, type, len) -> {
            if (!LinkResolver.webUrl(url)) {
                status.setText("网页提供了临时 blob:/data: 链接，只能使用网站自身下载功能");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("网站发起了下载")
                    .setMessage("文件：" + URLUtil.guessFileName(url, disposition, type)
                            + "\n要验证文件类型并加入下载管理吗？")
                    .setNegativeButton("继续浏览", null)
                    .setPositiveButton("验证并下载", (d,w) -> resolveAndDownload(url))
                    .show();
        });
    }

    private void offerAutofill() {
        if (Build.VERSION.SDK_INT < 26) {
            toast("Android 8.0 以下需要依赖键盘或密码管理器自己的填充功能");
            return;
        }
        AutofillManager manager = getSystemService(AutofillManager.class);
        if (manager == null || !manager.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("请先启用系统自动填充")
                    .setMessage("在手机“设置 → 密码与账号 → 自动填充服务”中选择 Google 密码管理器或其他已安装的密码管理器。"
                            + "\n之后在网盘登录框中选择保存账号与密码。应用不会读取或保存明文密码。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        browser.requestFocus();
        manager.requestAutofill(browser);
        toast("请点击网页中的账号或密码框，选择系统提示的已保存凭据");
    }

    private void openExternal() {
        String target = browser == null ? originalUrl : browser.getUrl();
        if (!LinkResolver.webUrl(target)) target = originalUrl;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            startActivity(Intent.createChooser(intent, "使用网盘客户端或系统浏览器打开"));
        } catch (Exception e) {
            toast("无法打开外部应用：" + e.getMessage());
        }
    }

    /**
     * Only collects links already rendered in the current, user-authorized DOM.
     * It does not inspect credentials, login forms, scripts, cookies or API tokens.
     */
    private static final String EXTRACT_SCRIPT = "(function(){"
            + "var a=[].slice.call(document.querySelectorAll('a[href],a[download],[data-href],[data-url]'));"
            + "var out=[];var seen={};"
            + "a.forEach(function(e){var raw=e.href||e.getAttribute('data-href')||e.getAttribute('data-url')||'';"
            + "var u='';try{u=new URL(raw,document.baseURI).href;}catch(x){}"
            + "if(!/^https?:\\/\\//.test(u)||seen[u])return;"
            + "var name=(e.innerText||e.textContent||e.getAttribute('download')||e.title||'').trim().slice(0,90);"
            + "var download=/\\.(?:pdf|epub|zip|rar|7z|txt|mobi|azw3)(?:$|[?#])/i.test(u)"
            + "||/下载|download|获取文件|保存文件/i.test(name)||e.hasAttribute('download');"
            + "if(download){seen[u]=1;out.push({title:name||'网页文件链接',url:u});}"
            + "});return JSON.stringify(out.slice(0,60));})()";

    private void inspectVisibleLinks() {
        if (browser == null || !LinkResolver.webUrl(browser.getUrl())) {
            toast("请先完成网页登录"); return;
        }
        CookieManager.getInstance().flush();
        status.setTextColor(BLUE);
        status.setText("正在分析当前网页公开显示的下载链接…");
        browser.evaluateJavascript(EXTRACT_SCRIPT, value -> {
            linksScroll.setVisibility(View.VISIBLE);
            linksContainer.removeAllViews();
            try {
                String raw = value == null ? "[]" : value;
                if (raw.startsWith("\"")) raw = new JSONArray("[" + raw + "]").getString(0);
                JSONArray items = new JSONArray(raw);
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                int count = 0;
                for (int i=0;i<items.length() && i<60;i++) {
                    JSONObject obj = items.optJSONObject(i);
                    if (obj == null) continue;
                    String url = obj.optString("url", "");
                    if (!LinkResolver.webUrl(url) || !seen.add(url)) continue;
                    String name = obj.optString("title", "");
                    if (name.isEmpty()) name = "文件 / 下载入口";
                    renderLink(name, url);
                    count++;
                }
                status.setText(count > 0
                        ? "发现 " + count + " 个可操作的候选链接（并非全部是真实文件）"
                        : "未发现普通文件链接。网盘可能要求在官方客户端内下载，或需先在网页点选文件");
                if (count == 0) showNoLinks();
            } catch (Exception e) {
                status.setText("链接分析失败：" + e.getMessage());
            }
        });
    }

    private void showNoLinks() {
        TextView note = label("未读取到可验证文件直链。请先在网页中打开分享文件、完成登录并点击网站允许的下载按钮；如果仅支持官方客户端，则点击“官方应用”。", 12, MUTED);
        note.setPadding(dp(6), dp(9), dp(6), dp(9));
        linksContainer.addView(note);
    }

    private void renderLink(String name, String url) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackground(surface(Color.rgb(245, 249, 255), Color.rgb(220, 231, 249)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(5));
        linksContainer.addView(box,lp);
        TextView title = label(name, 13, INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(title);
        TextView detail = label(url, 10, MUTED);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(detail);
        LinearLayout buttons = row();
        box.addView(buttons);
        Button inspect = button("验证并下载", true);
        inspect.setOnClickListener(v -> resolveAndDownload(url));
        buttons.addView(inspect, flex(1, 38));
        Button open = button("网页打开", false);
        open.setOnClickListener(v -> browser.loadUrl(url));
        buttons.addView(open, flex(1, 38));
    }

    private void resolveAndDownload(String url) {
        if (!LinkResolver.webUrl(url)) return;
        String ref = browser != null ? browser.getUrl() : originalUrl;
        String agent = browser != null ? browser.getSettings().getUserAgentString() : "";
        String word = keyword;
        // Even a cloud host can return a real authorized file after login.
        // The resolver checks content type and file magic before enqueueing.
        status.setText("正在验证是否为真正的文件响应…");
        new Thread(() -> {
            LinkResolver.FileLink file = LinkResolver.resolve(url, ref, word, agent,
                    target -> CookieManager.getInstance().getCookie(target),
                    message -> {});
            runOnUiThread(() -> {
                if (destroyed || isFinishing()) return;
                if (file == null) {
                    status.setText("这仍是网页/受保护地址，无法交给系统下载器。请使用页面中的下载按钮或官方应用");
                    return;
                }
                try {
                    long job = DownloadJobs.enqueue(this, file, agent);
                    status.setText("真实文件已加入下载管理（任务 " + job + "）");
                    DownloadJobs.watch(this, job, s -> status.setText(s));
                    new AlertDialog.Builder(this)
                            .setTitle("已提交下载")
                            .setMessage("已验证真实文件响应并提交系统下载器。需要查看进度吗？")
                            .setNegativeButton("继续浏览", null)
                            .setPositiveButton("下载管理", (d,w) ->
                                    startActivity(new Intent(this, DownloadManagerActivity.class)))
                            .show();
                } catch (Exception e) { status.setText("下载提交失败：" + e.getMessage()); }
            });
        }, "cloud-authorized-download").start();
    }

    private void toast(String text) {
        Toast.makeText(this,text,Toast.LENGTH_LONG).show();
    }

    @Override protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        CookieManager.getInstance().flush();
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}

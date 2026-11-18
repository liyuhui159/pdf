package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * A real, in-app mini-browser for testing a selected ebook search source.
 * "Opened" means navigation completed. It is NOT equated to "book search works":
 * confirmation is explicitly user-initiated after inspecting the page.
 */
public final class SiteBrowserDialog {
    public interface Updated { void onUpdated(); }

    private static final int BLUE = Color.rgb(35, 100, 242);
    private static final int INK = Color.rgb(30, 44, 69);
    private static final int MUTED = Color.rgb(98, 112, 135);
    private static final int BORDER = Color.rgb(221, 231, 244);
    private static final int BG = Color.rgb(245, 249, 255);
    private static final int GREEN = Color.rgb(26, 134, 86);
    private static final int RED = Color.rgb(184, 62, 64);

    private SiteBrowserDialog() {}

    private static int dp(Context c, float value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static GradientDrawable bg(Context c, int color, int edge, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, r));
        if (edge != 0) d.setStroke(dp(c, 1), edge);
        return d;
    }

    private static TextView text(Context c, String title, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static Button button(Context c, String title, boolean filled) {
        Button b = new Button(c);
        b.setText(title);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 3), 0, dp(c, 3), 0);
        b.setTextColor(filled ? Color.WHITE : BLUE);
        b.setBackground(bg(c, filled ? BLUE : Color.WHITE, filled ? BLUE : BORDER, 10));
        return b;
    }

    private static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private static LinearLayout.LayoutParams flex(Context c, float f, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, height), f);
        p.setMargins(dp(c, 3), 0, dp(c, 3), 0);
        return p;
    }

    public static void show(Activity activity, SourceManager.Site site,
                            String keyword, Updated callback) {
        final String testUrl;
        final String sample = keyword == null || keyword.trim().isEmpty()
                ? "Pride and Prejudice" : keyword.trim();
        try {
            testUrl = SiteVerifier.testUrl(site.template, sample);
        } catch (Exception ex) {
            Toast.makeText(activity, "模板错误：" + ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 8));

        TextView heading = text(activity, "网页验证 · #" +
                String.format(Locale.ROOT, "%02d", site.id) + "  " + site.name,
                17, INK, true);
        heading.setPadding(dp(activity, 8), dp(activity, 4),
                dp(activity, 4), dp(activity, 7));
        root.addView(heading);

        TextView hint = text(activity, "真实 WebView 浏览器 · 测试书名：" + sample, 11, MUTED, false);
        hint.setPadding(dp(activity, 8), 0, 0, dp(activity, 7));
        root.addView(hint);

        EditText address = new EditText(activity);
        address.setSingleLine(true);
        address.setTextSize(12);
        address.setSelectAllOnFocus(true);
        address.setTextColor(INK);
        address.setHint("https://example.org/search?q=...");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        address.setBackground(bg(activity, Color.WHITE, BORDER, 10));
        address.setText(testUrl);
        root.addView(address, new LinearLayout.LayoutParams(-1, dp(activity, 43)));

        LinearLayout nav = horizontal(activity);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(activity, 41));
        navParams.setMargins(0, dp(activity, 7), 0, dp(activity, 6));
        root.addView(nav, navParams);
        Button back = button(activity, "‹ 后退", false);
        Button next = button(activity, "前进 ›", false);
        Button reload = button(activity, "刷新", false);
        Button go = button(activity, "访问网址", true);
        nav.addView(back, flex(activity, 1, 39));
        nav.addView(next, flex(activity, 1, 39));
        nav.addView(reload, flex(activity, 0.9f, 39));
        nav.addView(go, flex(activity, 1.25f, 39));

        ProgressBar progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(activity, 3)));

        TextView state = text(activity, "即将打开网址…", 12, BLUE, false);
        state.setPadding(dp(activity, 7), dp(activity, 8), dp(activity, 7), dp(activity, 8));
        root.addView(state);
        WebView web = new WebView(activity);
        web.setBackgroundColor(Color.WHITE);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setSupportZoom(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView help = text(activity,
                "网页显示成功不代表搜索功能有效。请查看实际书籍列表，再手动确认。",
                11, MUTED, false);
        help.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 6), dp(activity, 6));
        root.addView(help);

        LinearLayout decisions = horizontal(activity);
        root.addView(decisions, new LinearLayout.LayoutParams(-1, dp(activity, 47)));
        Button auto = button(activity, "自动检测", false);
        Button confirmed = button(activity, "确认搜索正常", true);
        Button invalid = button(activity, "标记不可用", false);
        decisions.addView(auto, flex(activity, 1.0f, 43));
        decisions.addView(confirmed, flex(activity, 1.45f, 43));
        decisions.addView(invalid, flex(activity, 1.1f, 43));

        final boolean[] mainError = { false };
        final boolean[] loaded = { false };
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loaded[0] = false;
                mainError[0] = false;
                state.setTextColor(BLUE);
                state.setText("正在打开网页…");
                if (LinkResolver.webUrl(url)) address.setText(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (LinkResolver.webUrl(url)) address.setText(url);
                if (!mainError[0]) {
                    loaded[0] = true;
                    state.setTextColor(GREEN);
                    state.setText("网页已加载，请检查书名搜索结果是否正确");
                }
                progress.setProgress(100);
                back.setEnabled(view.canGoBack());
                next.setEnabled(view.canGoForward());
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest req,
                                                   WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    loaded[0] = false;
                    mainError[0] = true;
                    state.setTextColor(RED);
                    state.setText("主网页加载失败：" + err.getDescription());
                }
            }

            @Override public void onReceivedHttpError(WebView view,
                    WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame()
                        && response != null && response.getStatusCode() >= 400) {
                    mainError[0] = true;
                    loaded[0] = false;
                    state.setTextColor(RED);
                    state.setText("网页返回 HTTP " + response.getStatusCode()
                            + "，可能需要登录或限制访问");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
            }
        });
        web.setDownloadListener((url, ua, cd, mime, size) -> {
            state.setTextColor(BLUE);
            state.setText("检测到网页下载行为。下载文件请使用应用的网页登录分析器。");
        });

        Runnable navigate = () -> {
            String url = address.getText().toString().trim();
            if (!LinkResolver.webUrl(url)) {
                Toast.makeText(activity, "仅支持 http/https 网址", Toast.LENGTH_SHORT).show();
                return;
            }
            web.loadUrl(url);
        };
        go.setOnClickListener(v -> navigate.run());
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) {
                navigate.run();
                return true;
            }
            return false;
        });
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        next.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        reload.setOnClickListener(v -> web.reload());
        back.setEnabled(false);
        next.setEnabled(false);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setNegativeButton("关闭窗口", (d, w) -> {})
                .create();

        auto.setOnClickListener(v -> {
            auto.setEnabled(false);
            auto.setText("检测中…");
            String snapshot = site.template;
            new Thread(() -> {
                SiteVerifier.Result result = SiteVerifier.verify(snapshot, sample);
                activity.runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    auto.setEnabled(true);
                    auto.setText("自动检测");
                    if (!snapshot.equals(site.template)) {
                        state.setText("网址已修改，请重新开始验证");
                        return;
                    }
                    site.checkLevel = result.level;
                    site.checkMessage = result.message
                            + (result.httpCode > 0 ? " · HTTP " + result.httpCode : "")
                            + " · " + result.durationMs + "ms";
                    site.checkedAt = System.currentTimeMillis();
                    if (result.level == SiteVerifier.CONFIRMED) site.guessed = false;
                    state.setTextColor(result.level == SiteVerifier.CONFIRMED
                            ? GREEN : result.level == SiteVerifier.FAILED ? RED : MUTED);
                    state.setText("网络检测：" + site.checkMessage);
                    callback.onUpdated();
                });
            }, "site-auto-verifier").start();
        });

        confirmed.setOnClickListener(v -> {
            if (!loaded[0] || mainError[0]) {
                Toast.makeText(activity, "请先等待网页成功加载，再检查搜索结果", Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle("确认书名搜索可用？")
                    .setMessage("你是否已经在网页中看到与测试书名有关的书籍搜索结果？"
                            + "\n\n仅能打开网站首页，不代表搜索接口正确。")
                    .setNegativeButton("继续查看", null)
                    .setPositiveButton("已看到结果", (d,w) -> {
                        site.checkLevel = SiteVerifier.CONFIRMED;
                        site.checkMessage = "已在浏览器中人工确认书籍搜索结果";
                        site.checkedAt = System.currentTimeMillis();
                        site.guessed = false;
                        callback.onUpdated();
                        dialog.dismiss();
                    }).show();
        });

        invalid.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("标记网站不可用？")
                .setMessage("将这个网站标记为不可用，便于你返回网站管理界面编辑搜索网址。"
                        + "\n此操作不会自动取消网站的勾选。")
                .setNegativeButton("取消", null)
                .setPositiveButton("标记", (d,w) -> {
                    site.checkLevel = SiteVerifier.FAILED;
                    site.checkMessage = "人工检查：网址无法正常完成搜索";
                    site.checkedAt = System.currentTimeMillis();
                    callback.onUpdated();
                    dialog.dismiss();
                }).show());

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                int height = activity.getResources().getDisplayMetrics().heightPixels;
                int width = activity.getResources().getDisplayMetrics().widthPixels;
                dialog.getWindow().setLayout((int)(width * 0.97f), (int)(height * 0.90f));
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
        dialog.setOnDismissListener(d -> {
            web.stopLoading();
            web.loadUrl("about:blank");
            root.removeView(web);
            web.destroy();
        });
        dialog.show();
        web.loadUrl(testUrl);
    }
}

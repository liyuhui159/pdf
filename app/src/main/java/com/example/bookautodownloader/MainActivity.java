package com.example.bookautodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText bookInput;
    private EditText templateInput;
    private EditText extInput;
    private CheckBox autoDownloadCheck;
    private LinearLayout resultBox;
    private TextView logView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String PREF = "book_downloader_pref";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        loadSettings();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(18));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("书名自动检索下载助手");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = new TextView(this);
        tip.setText("流程：复制书名 → 打开本APP → 读取剪贴板 → 按你填写的合法资源站搜索 → 显示匹配下载链接。仅用于自有、授权、公版或允许分发的资料源。");
        tip.setTextSize(13);
        tip.setPadding(0, dp(10), 0, dp(10));
        root.addView(tip);

        addLabel(root, "书名 / 资料名");
        bookInput = new EditText(this);
        bookInput.setMinLines(2);
        bookInput.setHint("例如：西门子 S7-1200 PLC 入门教程");
        root.addView(bookInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row1);
        Button read = new Button(this);
        read.setText("读取剪贴板");
        read.setOnClickListener(v -> readClipboard(false));
        row1.addView(read, weight());
        Button readSearch = new Button(this);
        readSearch.setText("读取并搜索");
        readSearch.setOnClickListener(v -> readClipboard(true));
        row1.addView(readSearch, weight());

        addLabel(root, "资源站搜索网址模板");
        templateInput = new EditText(this);
        templateInput.setMinLines(2);
        templateInput.setHint("必须包含 {keyword}\n例如：https://example.com/search?q={keyword}");
        root.addView(templateInput, new LinearLayout.LayoutParams(-1, -2));

        addLabel(root, "允许识别的文件类型");
        extInput = new EditText(this);
        extInput.setSingleLine(true);
        extInput.setHint("pdf,zip,rar,7z");
        root.addView(extInput, new LinearLayout.LayoutParams(-1, -2));

        autoDownloadCheck = new CheckBox(this);
        autoDownloadCheck.setText("搜索后自动下载最高匹配结果");
        root.addView(autoDownloadCheck);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row2);
        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(v -> saveSettings());
        row2.addView(save, weight());
        Button open = new Button(this);
        open.setText("打开搜索页");
        open.setOnClickListener(v -> openSearchPage());
        row2.addView(open, weight());

        Button search = new Button(this);
        search.setText("开始搜索并匹配下载链接");
        search.setOnClickListener(v -> search());
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        addLabel(root, "搜索结果");
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultBox, new LinearLayout.LayoutParams(-1, -2));

        addLabel(root, "运行日志");
        logView = new TextView(this);
        logView.setTextSize(12);
        root.addView(logView, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    private void addLabel(LinearLayout root, String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(16);
        v.setPadding(0, dp(12), 0, dp(4));
        root.addView(v);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadSettings() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        templateInput.setText(sp.getString("template", "https://example.com/search?q={keyword}"));
        extInput.setText(sp.getString("ext", "pdf,zip,rar,7z"));
        autoDownloadCheck.setChecked(sp.getBoolean("auto", false));
    }

    private void saveSettings() {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString("template", templateInput.getText().toString().trim())
                .putString("ext", extInput.getText().toString().trim())
                .putBoolean("auto", autoDownloadCheck.isChecked())
                .apply();
        toast("已保存");
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
        if (doSearch) search();
    }

    private String makeSearchUrl() throws Exception {
        String keyword = bookInput.getText().toString().trim();
        String tpl = templateInput.getText().toString().trim();
        if (keyword.length() == 0) throw new Exception("请先输入或读取书名");
        if (!tpl.contains("{keyword}")) throw new Exception("网址模板必须包含 {keyword}");
        String enc = URLEncoder.encode(keyword, "UTF-8");
        return tpl.replace("{keyword}", enc).replace("{keyword_raw}", keyword);
    }

    private void openSearchPage() {
        try {
            String url = makeSearchUrl();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            log("打开搜索页：" + url);
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void search() {
        saveSettings();
        resultBox.removeAllViews();
        String keyword = bookInput.getText().toString().trim();
        String exts = extInput.getText().toString().trim();
        final String url;
        try {
            url = makeSearchUrl();
        } catch (Exception e) {
            toast(e.getMessage());
            return;
        }
        log("开始搜索：" + url);
        new Thread(() -> {
            try {
                String html = httpGet(url);
                ArrayList<Result> results = parseLinks(html, url, keyword, exts);
                ui.post(() -> showResults(results));
            } catch (Exception e) {
                ui.post(() -> {
                    log("搜索失败：" + e.getMessage());
                    toast("搜索失败，可能网站需要登录/验证码/禁止抓取");
                });
            }
        }).start();
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android BookAutoDownloader");
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && sb.length() < 800000) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private ArrayList<Result> parseLinks(String html, String baseUrl, String keyword, String extText) {
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
                list.add(new Result(title, abs, score));
                seen.add(abs);
            } catch (Exception ignored) {}
        }
        list.sort((a, b) -> b.score - a.score);
        if (list.size() > 20) return new ArrayList<>(list.subList(0, 20));
        return list;
    }

    private boolean isCandidate(String url, String title, String extText) {
        String s = (url + " " + title).toLowerCase(Locale.ROOT);
        if (s.contains("download") || s.contains("下载") || s.contains("attachment")) return true;
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
        log("找到候选链接：" + results.size() + " 个");
        if (results.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("没有找到可识别的下载链接。可以点“打开搜索页”手动处理。");
            resultBox.addView(none);
            return;
        }
        for (Result r : results) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView tv = new TextView(this);
            tv.setText("匹配度 " + r.score + "%\n" + r.title + "\n" + r.url);
            card.addView(tv);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button down = new Button(this);
            down.setText("下载");
            down.setOnClickListener(v -> download(r));
            row.addView(down, weight());
            Button open = new Button(this);
            open.setText("打开");
            open.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))));
            row.addView(open, weight());
            card.addView(row);
            resultBox.addView(card);
        }
        if (autoDownloadCheck.isChecked() && results.get(0).score >= 55) download(results.get(0));
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

    private void log(String s) {
        String old = logView.getText().toString();
        if (old.length() > 4000) old = old.substring(0, 2500);
        logView.setText(s + "\n" + old);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static class Result {
        String title;
        String url;
        int score;
        Result(String t, String u, int s) { title = t; url = u; score = s; }
    }
}

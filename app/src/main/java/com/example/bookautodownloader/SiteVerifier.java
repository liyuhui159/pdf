package com.example.bookautodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A one-site, user-initiated search check. HTTP 200 alone is NOT proof that an
 * ebook search endpoint works. The result distinguishes confirmed results,
 * reachable pages with insufficient evidence, protected pages, and failures.
 *
 * This uses the same non-authenticated HTTP access style as MainActivity search.
 * No bypassing login, challenge, paywall or rate limits.
 */
public final class SiteVerifier {
    public static final int CONFIRMED = 1;
    public static final int UNCERTAIN = 2;
    public static final int FAILED = 3;

    private static final Pattern JSON_COUNT = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");
    private static final Pattern JSON_RESULTS = Pattern.compile("\"results\"\\s*:\\s*\\[");
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*['\"]?([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final int BODY_LIMIT = 350000;

    private SiteVerifier() {}

    public static final class Result {
        public final int level, httpCode;
        public final String message, finalUrl, keyword;
        public final long durationMs;

        public Result(int level, int httpCode, String message,
                      String finalUrl, String keyword, long durationMs) {
            this.level = level;
            this.httpCode = httpCode;
            this.message = message;
            this.finalUrl = finalUrl;
            this.keyword = keyword;
            this.durationMs = durationMs;
        }
    }

    public static String testUrl(String template, String keyword) throws Exception {
        if (template == null || !LinkResolver.webUrl(template)
                || !(template.contains("{keyword}") || template.contains("{keyword_raw}")))
            throw new IllegalArgumentException("搜索网址模板无效，需要 {keyword}");
        String phrase = keyword == null || keyword.trim().isEmpty()
                ? "Pride and Prejudice" : keyword.trim();
        return template.replace("{keyword_raw}", phrase)
                .replace("{keyword}", URLEncoder.encode(phrase, "UTF-8"));
    }

    public static Result verify(String template, String keyword) {
        String phrase = keyword == null || keyword.trim().isEmpty()
                ? "Pride and Prejudice" : keyword.trim();
        long start = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            String requested = testUrl(template, phrase);
            connection = (HttpURLConnection) new URL(requested).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/json,*/*");
            int code = connection.getResponseCode();
            String url = connection.getURL().toString();
            String contentType = connection.getContentType();
            String body = "";
            InputStream stream = (code >= 400) ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while (out.size() < BODY_LIMIT && (n = in.read(buffer, 0,
                            Math.min(buffer.length, BODY_LIMIT - out.size()))) != -1) {
                        out.write(buffer, 0, n);
                    }
                    String encoding = "UTF-8";
                    if (contentType != null) {
                        Matcher found = CHARSET.matcher(contentType);
                        if (found.find()) encoding = found.group(1);
                    }
                    Charset cs;
                    try { cs = Charset.forName(encoding); }
                    catch (Exception ex) { cs = StandardCharsets.UTF_8; }
                    body = new String(out.toByteArray(), cs);
                }
            }
            Result analysis = inspect(code, contentType, body, phrase, url);
            return new Result(analysis.level, code, analysis.message, url, phrase, elapsed(start));
        } catch (java.net.UnknownHostException e) {
            return new Result(FAILED, 0, "域名无法解析，请检查网址或网络", template, phrase, elapsed(start));
        } catch (java.net.SocketTimeoutException e) {
            return new Result(FAILED, 0, "连接或读取超时（10/12 秒）", template, phrase, elapsed(start));
        } catch (javax.net.ssl.SSLException e) {
            return new Result(FAILED, 0, "HTTPS 证书或安全连接失败", template, phrase, elapsed(start));
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (error.length() > 90) error = error.substring(0, 90);
            return new Result(FAILED, 0, "请求失败：" + error, template, phrase, elapsed(start));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static long elapsed(long start) {
        return Math.max(0, (System.nanoTime() - start) / 1000000);
    }

    /**
     * Pure response classification for tests and UI diagnostics.
     * A live result is only CONFIRMED when the response supplies recognizable
     * book hits; generic HTTP 200 pages are UNCERTAIN.
     */
    public static Result inspect(int status, String contentType, String body,
                                 String keyword, String finalUrl) {
        String content = body == null ? "" : body;
        String text = content.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String url = finalUrl == null ? "" : finalUrl;
        String urlLower = url.toLowerCase(Locale.ROOT);

        if (status == 401 || status == 403 || status == 429)
            return new Result(UNCERTAIN, status,
                    status == 429 ? "被限流（HTTP 429），稍后再试"
                            : "需要授权或访问受限（HTTP " + status + "）", url, keyword, 0);
        if (status < 200 || status >= 300)
            return new Result(FAILED, status, "网站返回 HTTP " + status, url, keyword, 0);
        if (urlLower.contains("/login") || urlLower.contains("/signin")
                || urlLower.contains("/sign-in"))
            return new Result(UNCERTAIN, status, "跳转到了登录页面，需要登录后再试", url, keyword, 0);
        if (content.isEmpty())
            return new Result(UNCERTAIN, status, "网站可访问，但返回内容为空", url, keyword, 0);
        if (text.contains("captcha") || text.contains("verify you are human")
                || text.contains("checking your browser") || text.contains("just a moment...")
                || text.contains("请先登录") || text.contains("登录后访问")
                || text.contains("access denied") || text.contains("cloudflare challenge"))
            return new Result(UNCERTAIN, status, "页面需要验证/登录或阻止了自动访问", url, keyword, 0);

        if (type.contains("json") || text.trim().startsWith("{")) {
            Matcher m = JSON_COUNT.matcher(text);
            if (JSON_RESULTS.matcher(text).find()) {
                if (m.find()) {
                    try {
                        int n = Integer.parseInt(m.group(1));
                        return new Result(n > 0 ? CONFIRMED : UNCERTAIN, status,
                                n > 0 ? "搜索接口可用，返回 " + n + " 条书籍记录"
                                        : "接口正常，但当前测试书名暂无结果", url, keyword, 0);
                    } catch (NumberFormatException ignored) {}
                }
                if (Pattern.compile("\"results\"\\s*:\\s*\\[\\s*\\{").matcher(text).find())
                    return new Result(CONFIRMED, status, "搜索接口返回了书籍列表", url, keyword, 0);
                return new Result(UNCERTAIN, status, "搜索接口可访问，但没有发现书籍记录", url, keyword, 0);
            }
            return new Result(UNCERTAIN, status, "返回了 JSON，但未识别出书籍搜索结果", url, keyword, 0);
        }

        if (!(type.contains("html") || text.contains("<html") || text.contains("<!doctype")))
            return new Result(UNCERTAIN, status, "站点可访问，但返回内容不是搜索结果页", url, keyword, 0);

        Document doc = Jsoup.parse(content, url);
        String visible = doc.body() == null ? "" : doc.body().text().toLowerCase(Locale.ROOT);
        if (visible.contains("no results") || visible.contains("no books found")
                || visible.contains("没有找到") || visible.contains("暂无结果")
                || visible.contains("0 results"))
            return new Result(UNCERTAIN, status, "搜索页可访问，当前测试书名无匹配结果", url, keyword, 0);

        int matches = 0;
        for (Element link : doc.select("a[href]")) {
            String href = link.attr("href").toLowerCase(Locale.ROOT);
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:")) continue;
            String label = link.text();
            if (LinkResolver.matchScore(keyword, label) >= 65
                    && !label.trim().equalsIgnoreCase(keyword.trim())) matches++;
            if (matches >= 3) break;
        }
        if (matches > 0)
            return new Result(CONFIRMED, status,
                    "搜索页面可访问，发现 " + matches + " 条匹配书名链接", url, keyword, 0);

        if (doc.select(".booklink,.book,.book-item,.search-results,.search-result,"
                + "[data-testid*=book],[class*=search-result]").size() > 0)
            return new Result(UNCERTAIN, status, "已打开搜索页面，但未验证到匹配书籍", url, keyword, 0);

        return new Result(UNCERTAIN, status,
                "HTTP " + status + " 可访问，但未确认搜索结果；请检查网址参数", url, keyword, 0);
    }
}

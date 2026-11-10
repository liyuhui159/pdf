package com.example.bookautodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Only follows ordinary HTTP(S) links exposed by a page. No DRM, CAPTCHA,
 * authentication or access-control circumvention. Network methods run off the UI thread.
 */
public final class LinkResolver {
    public interface Cookies { String forUrl(String url); }
    public interface Log { void message(String line); }

    public static final class Candidate {
        public final String title, url;
        public final int score;
        public Candidate(String title, String url, int score) {
            this.title = title; this.url = url; this.score = score;
        }
    }

    public static final class FileLink {
        public final String url, name, mime, referer;
        FileLink(String url, String name, String mime, String referer) {
            this.url = url; this.name = name; this.mime = mime; this.referer = referer;
        }
    }

    private static final class Step {
        final String url, referer;
        final int depth;
        Step(String url, String referer, int depth) {
            this.url = url; this.referer = referer; this.depth = depth;
        }
    }

    private static final class Page {
        final FileLink file;
        final String html, url;
        Page(FileLink file, String html, String url) {
            this.file = file; this.html = html; this.url = url;
        }
    }

    private static final String EXT = "(?:pdf|epub|mobi|azw3|txt|doc|docx|zip|rar|7z)";
    private static final Pattern FILE_PATH = Pattern.compile("(?i)\\." + EXT + "(?:$|[?#&])");
    private static final Pattern FILE_NAME = Pattern.compile("(?i)filename\\*?\\s*=\\s*(?:UTF-8''|\"?)([^\";]+)");
    private static final Pattern META_REFRESH = Pattern.compile("(?i)url\\s*=\\s*['\"]?([^'\";]+)");
    private static final int MAX_PAGES = 12;
    private static final int MAX_DEPTH = 3;

    private LinkResolver() {}

    public static boolean webUrl(String url) {
        try {
            URL u = new URL(url);
            return ("https".equalsIgnoreCase(u.getProtocol()) || "http".equalsIgnoreCase(u.getProtocol()))
                    && u.getHost() != null && !u.getHost().isEmpty();
        } catch (Exception ex) { return false; }
    }

    public static boolean fileHint(String url) {
        return url != null && FILE_PATH.matcher(url).find();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String normalized(String s) {
        return clean(s).toLowerCase(Locale.ROOT).replaceAll("[《》【】（）()\\[\\]{}.,，。:：;；!?！？_\\-]+", " ");
    }

    public static int matchScore(String key, String text) {
        String a = normalized(key).trim(), b = normalized(text);
        if (a.isEmpty()) return 0;
        if (b.contains(a)) return 95;
        String[] parts = a.split("\\s+");
        int matches = 0;
        for (String part : parts) if (part.length() >= 2 && b.contains(part)) matches++;
        if (matches > 0) return Math.min(85, 35 + 50 * matches / parts.length);
        // Chinese titles without spaces: give partial character overlap modest weight.
        if (a.length() >= 4) {
            int hits = 0;
            for (int i = 0; i < a.length() - 1; i++)
                if (b.contains(a.substring(i, i + 2))) hits++;
            if (hits > 0) return Math.min(45, 45 * hits / (a.length() - 1));
        }
        return 0;
    }

    private static String linkUrl(Element el, String base) {
        String raw = el.hasAttr("href") ? el.attr("href")
                : el.hasAttr("data-href") ? el.attr("data-href")
                : el.hasAttr("data-url") ? el.attr("data-url")
                : el.attr("src");
        if (raw == null || raw.trim().isEmpty()) return "";
        try {
            String full = new URL(new URL(base), raw.trim()).toString();
            return webUrl(full) ? full : "";
        } catch (Exception ex) { return ""; }
    }

    private static String titleOf(Element el) {
        String title = clean(el.text());
        if (title.isEmpty()) title = clean(el.attr("title"));
        if (title.isEmpty()) title = clean(el.attr("aria-label"));
        if (title.length() < 4) {
            Element parent = el.parent();
            if (parent != null) {
                Element h = parent.selectFirst("h1,h2,h3,h4,.title,[class*=title]");
                if (h != null) title = clean(h.text());
                if (title.length() < 4) title = clean(parent.text());
            }
        }
        if (title.length() > 150) title = title.substring(0, 150);
        return title;
    }

    private static boolean downloadHint(String text) {
        String s = normalized(text);
        return s.contains("download") || s.contains("下载") || s.contains("attachment")
                || s.contains("get file") || s.contains("获取文件") || s.contains("epub");
    }

    private static List<Candidate> links(String html, String base, String keyword, boolean searchPage) {
        Document doc = Jsoup.parse(html, base);
        ArrayList<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Elements els = doc.select("a[href],area[href],[data-href],[data-url],iframe[src],embed[src]");
        for (Element e : els) {
            String url = linkUrl(e, base);
            if (url.isEmpty() || !seen.add(url)) continue;
            String label = titleOf(e);
            int score = matchScore(keyword, label + " " + url);
            boolean file = fileHint(url);
            boolean dl = downloadHint(label + " " + url);
            String path = url.toLowerCase(Locale.ROOT);
            boolean detail = path.contains("/book/") || path.contains("/ebooks/")
                    || path.contains("/detail/") || path.contains("/item/");
            if (searchPage && score < 22 && !file) continue;
            if (!searchPage && !dl && !file && !detail && score < 30) continue;
            if (url.equals(base)) continue;
            if (label.isEmpty()) label = file ? "文件链接" : "详情链接";
            int priority = score + (file ? 70 : 0) + (dl ? 40 : 0) + (detail ? 10 : 0);
            result.add(new Candidate(label, url, priority));
        }
        Element refresh = doc.selectFirst("meta[http-equiv=refresh]");
        if (refresh != null) {
            Matcher m = META_REFRESH.matcher(refresh.attr("content"));
            if (m.find()) {
                String url = linkUrl(new Element("a").attr("href", m.group(1)), base);
                if (!url.isEmpty() && seen.add(url))
                    result.add(new Candidate("页面跳转", url, 85));
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.score, a.score));
        return result;
    }

    public static List<Candidate> searchLinks(String html, String pageUrl, String keyword) {
        List<Candidate> all = links(html, pageUrl, keyword, true);
        return new ArrayList<>(all.subList(0, Math.min(30, all.size())));
    }

    private static boolean sameHost(String a, String b) {
        try { return new URL(a).getHost().equalsIgnoreCase(new URL(b).getHost()); }
        catch (Exception e) { return false; }
    }

    private static String inferExtension(String mime) {
        if (mime == null) return "";
        String m = mime.toLowerCase(Locale.ROOT);
        if (m.contains("application/pdf")) return ".pdf";
        if (m.contains("application/epub")) return ".epub";
        if (m.contains("application/zip")) return ".zip";
        if (m.contains("application/x-rar")) return ".rar";
        if (m.contains("application/x-7z")) return ".7z";
        if (m.contains("application/msword")) return ".doc";
        if (m.contains("wordprocessingml")) return ".docx";
        return "";
    }

    private static String filename(String url, String disposition, String mime) {
        String name = "";
        if (disposition != null) {
            Matcher m = FILE_NAME.matcher(disposition);
            if (m.find()) {
                name = m.group(1).trim();
                try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
            }
        }
        if (name.isEmpty()) {
            try {
                String path = new URL(url).getPath();
                name = path.substring(path.lastIndexOf('/') + 1);
                name = URLDecoder.decode(name, "UTF-8");
            } catch (Exception ignored) {}
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() < 2 || !name.contains(".")) {
            if (name.length() < 2) name = "ebook";
            name += inferExtension(mime);
        }
        if (name.length() > 110) name = name.substring(0, 110);
        return name;
    }

    private static boolean fileResponse(String type, String disposition, String url, byte[] prefix, int count) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String d = disposition == null ? "" : disposition.toLowerCase(Locale.ROOT);
        String head = new String(prefix, 0, count, StandardCharsets.ISO_8859_1).trim().toLowerCase(Locale.ROOT);
        if (t.contains("text/html") || t.contains("application/xhtml")
                || head.startsWith("<!doctype html") || head.startsWith("<html")) return false;
        if (t.contains("application/pdf") || t.contains("application/epub")
                || t.contains("application/zip") || t.contains("application/x-rar")
                || t.contains("application/x-7z") || t.contains("application/msword")
                || t.contains("wordprocessingml")) return true;
        if (head.startsWith("%pdf-") || (count > 3 && prefix[0] == 0x50 && prefix[1] == 0x4b)) return true;
        if (d.contains("attachment") && (fileHint(d) || fileHint(url))) return true;
        return (t.contains("application/octet-stream") || t.contains("text/plain")) && fileHint(url);
    }

    private static Page get(String url, String referer, String agent, Cookies cookies, Log log) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(9000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", agent == null || agent.isEmpty()
                ? "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36" : agent);
        if (referer != null && webUrl(referer)) c.setRequestProperty("Referer", referer);
        if (cookies != null) {
            String cookie = cookies.forUrl(url);
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        }
        c.setRequestProperty("Accept", "text/html,application/pdf,application/epub+zip,application/octet-stream,*/*");
        try {
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                log.message("HTTP " + status + "：" + url);
                return new Page(null, null, url);
            }
            String actual = c.getURL().toString();
            if (!webUrl(actual)) return new Page(null, null, actual);
            String type = c.getContentType(), disp = c.getHeaderField("Content-Disposition");
            try (InputStream in = c.getInputStream()) {
                byte[] head = new byte[128];
                int count = in.read(head);
                if (count < 0) count = 0;
                if (fileResponse(type, disp, actual, head, count)) {
                    String name = filename(actual, disp, type);
                    log.message("已验证文件响应：" + name);
                    return new Page(new FileLink(actual, name, type, referer), null, actual);
                }
                String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
                String first = new String(head, 0, count, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                boolean html = t.contains("html") || t.contains("text/plain")
                        || first.startsWith("<!doctype") || first.startsWith("<html");
                if (!html) return new Page(null, null, actual);
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                data.write(head, 0, count);
                byte[] buf = new byte[8192];
                int n;
                while (data.size() < 350000 && (n = in.read(buf, 0, Math.min(buf.length, 350000 - data.size()))) != -1)
                    data.write(buf, 0, n);
                String charset = "UTF-8";
                if (type != null) {
                    Matcher m = Pattern.compile("(?i)charset\\s*=\\s*['\"]?([a-zA-Z0-9_-]+)").matcher(type);
                    if (m.find()) charset = m.group(1);
                }
                Charset cs;
                try { cs = Charset.forName(charset); } catch (Exception ex) { cs = StandardCharsets.UTF_8; }
                return new Page(null, new String(data.toByteArray(), cs), actual);
            }
        } finally { c.disconnect(); }
    }

    /**
     * Resolves at most three levels of visible links and validates that the
     * final response is a file, not an HTML login or download page.
     */
    public static FileLink resolve(String start, String sourcePage, String keyword,
                                   String agent, Cookies cookies, Log logger) {
        Log log = logger == null ? s -> {} : logger;
        if (!webUrl(start)) { log.message("仅支持 http/https 链接"); return null; }
        ArrayDeque<Step> queue = new ArrayDeque<>();
        queue.add(new Step(start, sourcePage, 0));
        Set<String> visited = new HashSet<>();
        int explored = 0;
        while (!queue.isEmpty() && explored < MAX_PAGES) {
            Step step = queue.removeFirst();
            if (step.depth > MAX_DEPTH || !visited.add(step.url)) continue;
            explored++;
            try {
                Page page = get(step.url, step.referer, agent, cookies, log);
                if (page.file != null) return page.file;
                if (page.html == null || step.depth == MAX_DEPTH) continue;
                List<Candidate> candidates = links(page.html, page.url, keyword, false);
                int added = 0;
                for (Candidate link : candidates) {
                    if (visited.contains(link.url)) continue;
                    // Allow external file hosts, but do not spider unrelated sites.
                    if (!sameHost(link.url, page.url) && !fileHint(link.url)) continue;
                    queue.addLast(new Step(link.url, page.url, step.depth + 1));
                    if (++added == 10) break;
                }
                log.message("解析详情页，发现 " + added + " 个下一步链接");
            } catch (Exception ex) {
                log.message("链接解析失败：" + ex.getClass().getSimpleName() + "：" + ex.getMessage());
            }
        }
        log.message("未取得可验证的文件直链；可能需要登录、JavaScript 或手动点击网页按钮");
        return null;
    }
}

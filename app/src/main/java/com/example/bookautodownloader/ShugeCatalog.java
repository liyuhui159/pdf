package com.example.bookautodownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Book metadata from Shuge's documented read-only public API v1.1.
 * The website's search form and JSON API are intentionally separate:
 * browsers display human-friendly pages, automated search uses catalog JSON.
 */
public final class ShugeCatalog {
    private ShugeCatalog() {}

    public static final class Book {
        public final String title, details, download, resourceId;
        Book(String title, String details, String download, String resourceId) {
            this.title=title;this.details=details;this.download=download;this.resourceId=resourceId;
        }
    }

    public static boolean isShuge(String address) {
        if (address == null || address.isEmpty()) return false;
        try {
            String host = new URL(address).getHost().toLowerCase(Locale.ROOT);
            return host.equals("shuge.org") || host.endsWith(".shuge.org");
        } catch (Exception e) { return false; }
    }

    public static String browserTemplate() {
        // WordPress site's human-readable search route. The JSON API is
        // reserved for automated data retrieval, not shown in a WebView.
        return "https://www.shuge.org/?s={keyword}";
    }

    public static String apiUrl(String keyword) throws Exception {
        return "https://api.shuge.org/query/?q="
                + URLEncoder.encode(keyword, "UTF-8") + "&per_page=100";
    }

    /** Repair old guessed URLs (?q={keyword}), the exact error in V11. */
    public static boolean incorrectHomeTemplate(String raw) {
        if (!isShuge(raw)) return false;
        try {
            URL u = new URL(raw);
            String path = u.getPath();
            if (path == null || !(path.isEmpty() || path.equals("/")
                    || path.equals("/collections") || path.equals("/collections/"))) return false;
            String q = u.getQuery();
            return q == null || q.matches("(?i)(?:q|query|keyword|search)=(?:\\{keyword\\}|[^&]*)")
                    || q.contains("q=%7Bkeyword%7D");
        } catch (Exception ex) { return false; }
    }

    public static ArrayList<Book> parse(String body, String keyword) throws Exception {
        JSONObject root = new JSONObject(body);
        if (!root.optBoolean("success", false))
            throw new Exception("书格 API 未返回成功状态");
        JSONArray records = root.optJSONArray("items");
        if (records == null) throw new Exception("书格 API 缺少 items 列表");
        ArrayList<Book> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null) continue;
            String title = item.optString("resource_name", "").trim();
            if (title.isEmpty()) continue;
            String detail = item.optString("intro_url", "").trim();
            String download = item.optString("download_url", "").trim();
            String id = item.optString("resource_id", "");
            if (!LinkResolver.webUrl(detail)) detail = "";
            if (!LinkResolver.webUrl(download)) download = "";
            if (detail.isEmpty() && download.isEmpty()) continue;
            String key = id.isEmpty() ? title : id;
            if (!seen.add(key)) continue;
            result.add(new Book(title, detail, download, id));
        }
        return result;
    }

    public static int totalItems(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!root.optBoolean("success", false)) throw new Exception("API 返回失败状态");
        JSONObject pg = root.optJSONObject("pagination");
        if (pg != null) return pg.optInt("total_items", -1);
        JSONArray items = root.optJSONArray("items");
        return items == null ? -1 : items.length();
    }
}

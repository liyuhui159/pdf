package com.example.bookautodownloader;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts pasted site URLs into editable search templates. Never assumes every site uses the same search API. */
public final class SearchTemplate {
    private static final Set<String> QUERY_KEYS = new HashSet<>(Arrays.asList(
            "q", "query", "search", "keyword", "keywords", "wd", "word", "s", "text",
            "searchterm", "search_query", "searchkey", "term", "kw", "k", "searchtext"));
    private static final Pattern ENCODED_PLACEHOLDER =
            Pattern.compile("(?i)%7b(keyword|keyword_raw)%7d");

    private SearchTemplate() {}

    public static final class Conversion {
        public final String template;
        public final boolean guessed;
        public final String note;
        Conversion(String template, boolean guessed, String note) {
            this.template = template;
            this.guessed = guessed;
            this.note = note;
        }
    }

    public static Conversion convert(String raw) {
        if (raw == null) throw new IllegalArgumentException("请粘贴网站地址");
        String s = raw.trim().replace("｛", "{").replace("｝", "}")
                .replace("{ keyword }", "{keyword}")
                .replace("{关键词}", "{keyword}").replace("{书名}", "{keyword}");
        if (s.isEmpty()) throw new IllegalArgumentException("请粘贴网站地址");
        if (s.contains("\n") || s.contains("\r") || s.contains(" "))
            throw new IllegalArgumentException("网址不能包含空格或换行，请单独添加");
        if (!s.contains("://") && s.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}([/?#].*)?$"))
            s = "https://" + s;
        if (s.toLowerCase(Locale.ROOT).startsWith("http://")
                || s.toLowerCase(Locale.ROOT).startsWith("https://")) {
            // otherwise check the URL object below
        } else throw new IllegalArgumentException("请输入 http/https 网站地址");
        try {
            URL u = new URL(s);
            if (u.getHost() == null || u.getHost().isEmpty())
                throw new IllegalArgumentException("网站域名无效");
            // Remove fragments, which are not submitted to a server.
            int sharp = s.indexOf('#');
            if (sharp >= 0) s = s.substring(0, sharp);
        } catch (Exception e) {
            throw new IllegalArgumentException("网址格式不正确");
        }
        Matcher encoded = ENCODED_PLACEHOLDER.matcher(s);
        s = encoded.replaceAll("{$1}");
        if (s.contains("{keyword}") || s.contains("{keyword_raw}"))
            return new Conversion(s, false, "已包含搜索关键词占位符");
        try {
            URI u = new URI(s);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            String query = u.getRawQuery();

            // Known public-domain catalogues have documented search endpoints.
            if (host.equals("gutendex.com") || host.endsWith(".gutendex.com")) {
                if (query == null && (path.isEmpty() || path.equals("/") || path.equals("/books")))
                    return new Conversion(u.getScheme() + "://" + u.getRawAuthority()
                            + "/books?search={keyword}", false, "已匹配 Gutendex 书名检索接口");
            }
            if (host.equals("gutenberg.org") || host.endsWith(".gutenberg.org")) {
                if (query == null && (path.isEmpty() || path.equals("/")
                        || path.equals("/ebooks") || path.equals("/ebooks/")))
                    return new Conversion(u.getScheme() + "://" + u.getRawAuthority()
                            + "/ebooks/search/?query={keyword}", false, "已匹配 Project Gutenberg 检索页面");
            }

            if (query != null && !query.isEmpty()) {
                String[] fields = query.split("&", -1);
                for (int i = 0; i < fields.length; i++) {
                    int eq = fields[i].indexOf('=');
                    String key = eq < 0 ? fields[i] : fields[i].substring(0, eq);
                    if (QUERY_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                        fields[i] = key + "={keyword}";
                        String prefix = s.substring(0, s.indexOf('?') + 1);
                        StringBuilder rebuilt = new StringBuilder(prefix);
                        for (int j = 0; j < fields.length; j++) {
                            if (j > 0) rebuilt.append('&');
                            rebuilt.append(fields[j]);
                        }
                        return new Conversion(rebuilt.toString(), false,
                                "已将原有搜索参数替换为书名关键词");
                    }
                }
            }
            // Unknown sites do not share a guaranteed query parameter. Make the
            // conventional ?q= guess editable and explicitly identify it as a guess.
            String sep = query == null ? "?" : (s.endsWith("?") || s.endsWith("&") ? "" : "&");
            return new Conversion(s + sep + "q={keyword}", true,
                    "通用补全（?q=）仅供尝试：各网站搜索参数可能不同，可点击编辑修正");
        } catch (Exception ex) {
            throw new IllegalArgumentException("网址含有无法识别的字符，请编辑后添加");
        }
    }

    public static String defaultName(String template) {
        try {
            String h = new URL(template).getHost().replaceFirst("(?i)^www\\.", "");
            if (h.equalsIgnoreCase("gutendex.com")) return "Gutendex 公版书库";
            if (h.equalsIgnoreCase("gutenberg.org")) return "Project Gutenberg";
            return h;
        } catch (Exception ex) {
            return "自定义资源站";
        }
    }
}

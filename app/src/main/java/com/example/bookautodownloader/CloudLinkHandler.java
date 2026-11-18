package com.example.bookautodownloader;

import java.net.URL;
import java.util.Locale;

/** Pure URL classification for user-authorized cloud drives and their landing pages. */
public final class CloudLinkHandler {
    private CloudLinkHandler() {}

    private static boolean hostMatches(String host, String exact) {
        return host.equals(exact) || host.endsWith("." + exact);
    }

    public static String host(String value) {
        if (value == null) return "";
        try {
            URL url = new URL(value);
            if (!("https".equalsIgnoreCase(url.getProtocol())
                    || "http".equalsIgnoreCase(url.getProtocol()))) return "";
            return url.getHost().toLowerCase(Locale.ROOT);
        } catch (Exception e) { return ""; }
    }

    public static boolean isCloud(String url) {
        String h = host(url);
        return hostMatches(h, "pan.baidu.com")
                || hostMatches(h, "yun.baidu.com")
                || hostMatches(h, "alipan.com")
                || hostMatches(h, "aliyundrive.com")
                || hostMatches(h, "pan.quark.cn")
                || hostMatches(h, "drive.uc.cn")
                || hostMatches(h, "cloud.189.cn")
                || hostMatches(h, "123pan.com")
                || hostMatches(h, "123684.com")
                || hostMatches(h, "pan.xunlei.com");
    }

    public static boolean isGateway(String url) {
        String h = host(url);
        return h.equals("s.shuge.org") || h.equals("f.shuge.org")
                || h.equals("dl.shuge.org");
    }

    public static boolean needsWebFlow(String url) {
        return isCloud(url) || isGateway(url);
    }

    public static String service(String url) {
        String h = host(url);
        if (hostMatches(h, "baidu.com")) return "百度网盘";
        if (hostMatches(h, "alipan.com") || hostMatches(h, "aliyundrive.com")) return "阿里云盘";
        if (hostMatches(h, "quark.cn")) return "夸克网盘";
        if (hostMatches(h, "uc.cn")) return "UC网盘";
        if (hostMatches(h, "189.cn")) return "天翼云盘";
        if (hostMatches(h, "123pan.com") || hostMatches(h, "123684.com")) return "123云盘";
        if (hostMatches(h, "xunlei.com")) return "迅雷云盘";
        if (isGateway(url)) return "书格资源分发页";
        return "当前站点";
    }

    public static String advice(String url) {
        if (isGateway(url))
            return "先在书格资源页选择下载渠道。遇到网盘登录时在网页中完成，下载链接会在网页允许的情况下被检测。";
        if (isCloud(url))
            return "请使用网盘官方页面登录。应用可保存网站登录会话；账号密码交由安卓系统密码管理器自动填充。";
        return "登录后点击识别当前页；只有网站正常返回的文件链接才能交由系统下载器。";
    }

    public static boolean looksLikeWebLogin(String url) {
        if (!LinkResolver.webUrl(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/login") || lower.contains("/signin")
                || lower.contains("/passport") || lower.contains("/account")
                || lower.contains("passport.baidu.com") || lower.contains("login.");
    }
}

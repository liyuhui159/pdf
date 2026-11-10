package com.example.bookautodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Enqueues verified file links and reports actual Android DownloadManager status. */
public final class DownloadJobs {
    public interface Messages { void show(String message); }
    private DownloadJobs() {}

    private static String safeFileName(String fileName) {
        String s = fileName == null ? "ebook" : fileName;
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "ebook";
        int dot = s.lastIndexOf('.');
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String basename = dot > 0 ? s.substring(0, dot) : s;
        String extension = dot > 0 ? s.substring(dot) : "";
        if (basename.length() > 75) basename = basename.substring(0, 75);
        return basename + "_" + stamp + extension;
    }

    public static long enqueue(Context context, LinkResolver.FileLink file, String userAgent) {
        if (file == null || !LinkResolver.webUrl(file.url))
            throw new IllegalArgumentException("下载地址无效");
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) throw new IllegalStateException("系统下载器不可用");
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(file.url));
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String name = safeFileName(file.name);
        request.setTitle(name);
        request.setDescription("电子书下载：正在等待系统下载器完成");
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        if (file.mime != null && !file.mime.trim().isEmpty()) request.setMimeType(file.mime);
        if (userAgent != null && !userAgent.trim().isEmpty()) request.addRequestHeader("User-Agent", userAgent);
        if (file.referer != null && LinkResolver.webUrl(file.referer))
            request.addRequestHeader("Referer", file.referer);
        // Cookies are scoped to the actual file host; never copy other-site cookies.
        String cookie = CookieManager.getInstance().getCookie(file.url);
        if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
        return manager.enqueue(request);
    }

    public static void watch(Activity activity, long id, Messages messages) {
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable task = new Runnable() {
            int count = 0, lastPercent = -1, lastStatus = -1;
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        messages.show("下载任务不存在或已被删除（ID：" + id + "）");
                        return;
                    }
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    long soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        messages.show("下载完成：请在手机 Download 文件夹查看（任务 ID：" + id + "）");
                        return;
                    }
                    if (status == DownloadManager.STATUS_FAILED) {
                        messages.show("下载失败，系统原因代码：" + reason
                                + "。可能是链接过期、无权限、被重定向到登录页或服务器拒绝下载");
                        return;
                    }
                    int progress = total > 0 ? (int) (100 * soFar / total) : -1;
                    if (status != lastStatus || (progress >= 0 && progress / 10 != lastPercent / 10)) {
                        String state = status == DownloadManager.STATUS_PAUSED
                                ? "暂停，原因代码 " + reason
                                : status == DownloadManager.STATUS_RUNNING ? "下载中" : "等待中";
                        messages.show(state + (progress >= 0 ? "：" + progress + "%" : ""));
                        lastStatus = status; lastPercent = progress;
                    }
                    if (++count < 900) handler.postDelayed(this, 2000);
                    else messages.show("下载监测已停止；系统下载器仍可继续运行，请查看通知栏");
                } catch (Exception ex) {
                    messages.show("读取下载状态失败：" + ex.getMessage());
                }
            }
        };
        handler.postDelayed(task, 1000);
    }
}

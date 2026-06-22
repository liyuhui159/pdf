package com.example.bookautodownloader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.Base64;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AiScreenshotClicker {
    public interface Callback {
        void done(boolean clicked, String reason);
    }

    public static void clickDownloadByVision(Activity activity, WebView webView, String keyword, String apiUrl, String apiKey, String model, Callback callback) {
        if (activity == null || webView == null) {
            if (callback != null) callback.done(false, "没有可分析的网页窗口");
            return;
        }
        if (apiKey == null || apiKey.trim().length() == 0) {
            if (callback != null) callback.done(false, "未配置API Key");
            return;
        }
        int width = webView.getWidth();
        int height = webView.getHeight();
        if (width <= 0 || height <= 0) {
            if (callback != null) callback.done(false, "网页画面尺寸无效");
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        webView.draw(canvas);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, baos);
        bitmap.recycle();
        String base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String finalApiUrl = (apiUrl == null || apiUrl.trim().length() == 0) ? "https://api.openai.com/v1/chat/completions" : apiUrl.trim();
        String finalModel = (model == null || model.trim().length() == 0) ? "gpt-4.1-mini" : model.trim();
        String finalKeyword = keyword == null ? "" : keyword.trim();

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", finalModel);
                body.put("temperature", 0);
                body.put("max_tokens", 300);

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "你是手机网页截图点击决策器。只根据截图判断应该点击哪里。必须只返回JSON，不要Markdown。格式：{\"click\":true/false,\"x\":数字,\"y\":数字,\"confidence\":0-100,\"reason\":\"\"}。x/y是截图像素坐标，原点在截图左上角。"));

                JSONArray content = new JSONArray();
                content.put(new JSONObject().put("type", "text").put("text",
                        "目标书名：" + finalKeyword + "\n" +
                        "任务：从这张手机网页截图中，找到与目标书名对应的下载按钮/下载图标/向下箭头按钮，并返回点击坐标。\n" +
                        "优先点击当前书籍卡片右侧的下载图标，不要点击收藏、书签、更多、标签、头像、个人中心、通知、播放/预览。\n" +
                        "如果看不到下载按钮，返回 click=false。"));
                content.put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64)));
                messages.put(new JSONObject().put("role", "user").put("content", content));
                body.put("messages", messages);

                HttpURLConnection conn = (HttpURLConnection) new URL(finalApiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                String raw = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code < 200 || code >= 300) throw new IllegalStateException("视觉API错误 " + code + "：" + raw);

                JSONObject root = new JSONObject(raw);
                String answer = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                JSONObject result = extractJson(answer);
                boolean click = result.optBoolean("click", false);
                int x = result.optInt("x", -1);
                int y = result.optInt("y", -1);
                int confidence = result.optInt("confidence", 0);
                String reason = result.optString("reason", "");

                activity.runOnUiThread(() -> {
                    if (click && confidence >= 50 && x >= 0 && y >= 0 && x <= webView.getWidth() && y <= webView.getHeight()) {
                        performClick(webView, x, y);
                        Toast.makeText(activity, "AI图片分析已点击下载位置", Toast.LENGTH_SHORT).show();
                        if (callback != null) callback.done(true, "AI图片分析点击：" + reason);
                    } else {
                        if (callback != null) callback.done(false, "AI图片分析未找到可靠下载按钮：" + reason);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (callback != null) callback.done(false, "AI图片分析失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private static void performClick(WebView webView, int x, int y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private static JSONObject extractJson(String content) throws Exception {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) return new JSONObject(content.substring(start, end + 1));
        return new JSONObject(content);
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}

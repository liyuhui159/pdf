package com.example.bookautodownloader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** Persist DownloadManager IDs because its query API cannot list only this app's
 * downloads without the IDs. Files continue downloading after app exit. */
public final class DownloadHistory {
    private static final String PREF = "ebook_download_history_v1";
    private static final String KEY = "tasks";
    private DownloadHistory() {}

    public static final class Entry {
        public long id, created;
        public String name, url, mime, referer, agent;
        Entry(long id, long created, String name, String url, String mime,
              String referer, String agent) {
            this.id = id;
            this.created = created;
            this.name = name;
            this.url = url;
            this.mime = mime;
            this.referer = referer;
            this.agent = agent;
        }
    }

    public static synchronized ArrayList<Entry> all(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        ArrayList<Entry> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject x = arr.optJSONObject(i);
                if (x == null) continue;
                long id = x.optLong("id", -1);
                if (id < 0) continue;
                result.add(new Entry(id, x.optLong("created", 0),
                        x.optString("name", "电子书"), x.optString("url", ""),
                        x.optString("mime", ""), x.optString("referer", ""),
                        x.optString("agent", "")));
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static synchronized void record(Context c, long id, String name, String url,
                                            String mime, String referer, String agent) {
        ArrayList<Entry> all = all(c);
        for (Entry e : all) if (e.id == id) return;
        all.add(0, new Entry(id, System.currentTimeMillis(),
                name, url, mime == null ? "" : mime,
                referer == null ? "" : referer, agent == null ? "" : agent));
        while (all.size() > 200) all.remove(all.size() - 1);
        save(c, all);
    }

    public static synchronized void remove(Context c, long id) {
        ArrayList<Entry> all = all(c);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).id == id) all.remove(i);
        }
        save(c, all);
    }

    private static void save(Context c, ArrayList<Entry> all) {
        JSONArray arr = new JSONArray();
        for (Entry e : all) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", e.id);
                item.put("created", e.created);
                item.put("name", e.name);
                item.put("url", e.url);
                item.put("mime", e.mime);
                item.put("referer", e.referer);
                item.put("agent", e.agent);
                arr.put(item);
            } catch (Exception ignored) {}
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }
}
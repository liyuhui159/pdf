package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Modal editor for independently numbered, enabled/disabled ebook search sites. */
public final class SourceManager {
    private static final String PREF = "book_downloader_pref_v4";
    private static final String KEY = "sites_json_v8";
    private static final int BLUE = Color.rgb(47, 107, 255);
    private static final int DARK = Color.rgb(38, 50, 72);

    private SourceManager() {}

    public interface Saved { void onSave(ArrayList<Site> sites); }

    public static class Site {
        public int id;
        public String name, template;
        public boolean enabled, guessed;

        public Site(int id, String name, String template, boolean enabled, boolean guessed) {
            this.id = id; this.name = name; this.template = template;
            this.enabled = enabled; this.guessed = guessed;
        }
        public Site copy() { return new Site(id, name, template, enabled, guessed); }
    }

    public static ArrayList<Site> load(Context context, String defaultTemplates) {
        SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        ArrayList<Site> list = new ArrayList<>();
        if (sp.contains(KEY)) {
            try {
                JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String template = obj.optString("template", "");
                    if (!LinkResolver.webUrl(template) || !(template.contains("{keyword}") || template.contains("{keyword_raw}"))) continue;
                    list.add(new Site(obj.optInt("id", i + 1),
                            obj.optString("name", SearchTemplate.defaultName(template)),
                            template, obj.optBoolean("enabled", true),
                            obj.optBoolean("guessed", false)));
                }
                return list;
            } catch (Exception ignored) {
                // Corrupted JSON: migrate the original plain-text template list instead.
            }
        }
        String legacy = sp.getString("templates", defaultTemplates);
        if (legacy == null || legacy.trim().equals("https://example.com/search?q={keyword}"))
            legacy = defaultTemplates;
        Set<String> seen = new HashSet<>();
        for (String line : legacy.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            try {
                SearchTemplate.Conversion conv = SearchTemplate.convert(line);
                if (seen.add(conv.template)) {
                    list.add(new Site(list.size() + 1, SearchTemplate.defaultName(conv.template),
                            conv.template, true, conv.guessed));
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return list;
    }

    public static void save(Context context, ArrayList<Site> sites) {
        JSONArray arr = new JSONArray();
        StringBuilder compatible = new StringBuilder();
        for (Site s : sites) {
            if (s == null || !LinkResolver.webUrl(s.template)
                    || !(s.template.contains("{keyword}") || s.template.contains("{keyword_raw}"))) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("template", s.template);
                o.put("enabled", s.enabled);
                o.put("guessed", s.guessed);
                arr.put(o);
            } catch (Exception ignored) {}
            if (s.enabled) {
                if (compatible.length() > 0) compatible.append('\n');
                compatible.append(s.template);
            }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, arr.toString())
                .putString("templates", compatible.toString())
                .apply();
    }

    public static ArrayList<Site> selected(ArrayList<Site> sites) {
        ArrayList<Site> active = new ArrayList<>();
        for (Site site : sites)
            if (site.enabled && LinkResolver.webUrl(site.template))
                active.add(site.copy());
        return active;
    }

    public static String summary(ArrayList<Site> sites) {
        int chosen = 0;
        StringBuilder names = new StringBuilder();
        for (Site site : sites) {
            if (!site.enabled) continue;
            chosen++;
            if (chosen <= 3) {
                if (names.length() > 0) names.append("、");
                names.append("#").append(String.format(java.util.Locale.ROOT, "%02d", site.id))
                        .append(" ").append(site.name);
            }
        }
        if (chosen == 0) return "暂无启用网站。点击“管理网站”勾选或新增。";
        return "已勾选 " + chosen + " / " + sites.size() + " 个网站\n"
                + names + (chosen > 3 ? " 等" : "");
    }

    private static int dp(Context c, int n) {
        return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
    }

    private static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static TextView text(Context c, String value, int size) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(DARK);
        return t;
    }

    private static Button button(Context c, String value) {
        Button b = new Button(c);
        b.setText(value);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(BLUE);
        b.setMinHeight(dp(c, 40));
        return b;
    }

    private static EditText edit(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setTextSize(14);
        e.setSingleLine(false);
        e.setMaxLines(3);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextColor(DARK);
        e.setSelectAllOnFocus(false);
        return e;
    }

    private static int nextId(ArrayList<Site> list) {
        int max = 0;
        for (Site s : list) max = Math.max(max, s.id);
        return max + 1;
    }

    private static String number(int n) {
        return String.format(java.util.Locale.ROOT, "%02d", n);
    }

    public static void open(Activity activity, ArrayList<Site> current,
                            Saved onSaved) {
        final ArrayList<Site> working = new ArrayList<>();
        for (Site site : current) working.add(site.copy());

        LinearLayout root = vertical(activity);
        root.setPadding(dp(activity, 12), dp(activity, 4), dp(activity, 12), dp(activity, 4));
        TextView caption = text(activity,
                "☑ 勾选的网站才参与搜索；每个站点独立编号。粘贴网址后会自动补全 {keyword}，不确定的参数可手动编辑。",
                12);
        caption.setTextColor(Color.rgb(90, 105, 130));
        root.addView(caption);

        LinearLayout controls = new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(controls);
        Button add = button(activity, "＋ 添加网站");
        Button paste = button(activity, "粘贴并添加");
        controls.addView(add, new LinearLayout.LayoutParams(0, dp(activity, 49), 1));
        controls.addView(paste, new LinearLayout.LayoutParams(0, dp(activity, 49), 1));

        LinearLayout toggleRow = new LinearLayout(activity);
        root.addView(toggleRow);
        Button all = button(activity, "全选");
        Button none = button(activity, "全不选");
        toggleRow.addView(all, new LinearLayout.LayoutParams(0, dp(activity, 43), 1));
        toggleRow.addView(none, new LinearLayout.LayoutParams(0, dp(activity, 43), 1));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = vertical(activity);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .49f)));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.removeAllViews();
            if (working.isEmpty()) {
                TextView empty = text(activity, "暂无网站。点击上方添加或粘贴网址。", 14);
                empty.setPadding(dp(activity, 10), dp(activity, 22), dp(activity, 10), dp(activity, 22));
                list.addView(empty);
                return;
            }
            for (Site site : working) {
                LinearLayout card = vertical(activity);
                card.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.WHITE);
                bg.setCornerRadius(dp(activity, 12));
                bg.setStroke(dp(activity, 1), Color.rgb(217, 226, 240));
                card.setBackground(bg);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(0, 0, 0, dp(activity, 9));
                list.addView(card, cp);

                CheckBox checked = new CheckBox(activity);
                checked.setText("#" + number(site.id) + "  " + site.name);
                checked.setTextSize(15);
                checked.setTextColor(DARK);
                checked.setTypeface(Typeface.DEFAULT_BOLD);
                checked.setChecked(site.enabled);
                checked.setOnCheckedChangeListener((buttonView, isChecked) -> site.enabled = isChecked);
                card.addView(checked);

                TextView url = text(activity, site.template, 11);
                url.setTextColor(Color.rgb(91, 103, 125));
                url.setMaxLines(3);
                url.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
                card.addView(url);
                if (site.guessed) {
                    TextView hint = text(activity, "⚠ 通用 q= 参数尚未验证，可点编辑调整", 11);
                    hint.setTextColor(Color.rgb(175, 103, 30));
                    card.addView(hint);
                }

                LinearLayout tools = new LinearLayout(activity);
                tools.setGravity(Gravity.END);
                card.addView(tools);
                Button modify = button(activity, "编辑");
                Button delete = button(activity, "删除");
                tools.addView(modify);
                tools.addView(delete);
                modify.setOnClickListener(v -> editSite(activity, site, refresh[0]));
                delete.setOnClickListener(v -> {
                    working.remove(site);
                    refresh[0].run();
                });
            }
        };
        refresh[0].run();
        add.setOnClickListener(v -> showAddDialog(activity, working, refresh[0], "", ""));
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Toast.makeText(activity, "剪贴板没有网址", Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence raw = clip.getItemAt(0).coerceToText(activity);
            if (raw == null) return;
            // Pasted URL(s) can be reviewed before they are added.
            showAddDialog(activity, working, refresh[0], "", raw.toString().trim());
        });
        all.setOnClickListener(v -> { for (Site s : working) s.enabled = true; refresh[0].run(); });
        none.setOnClickListener(v -> { for (Site s : working) s.enabled = false; refresh[0].run(); });

        new AlertDialog.Builder(activity)
                .setTitle("管理搜索网站（可多选）")
                .setView(root)
                .setPositiveButton("保存并关闭", (dialog, which) -> onSaved.onSave(working))
                .setNegativeButton("取消", null)
                .show();
    }

    private static void showAddDialog(Activity activity, ArrayList<Site> working,
                                      Runnable refresh, String oldName, String initialUrl) {
        LinearLayout layout = vertical(activity);
        layout.setPadding(dp(activity, 22), dp(activity, 12), dp(activity, 22), 0);
        layout.addView(text(activity, "网站名称（可不填，自动采用域名）", 12));
        EditText name = edit(activity, "例如：机械工程图书馆", oldName);
        layout.addView(name);
        layout.addView(text(activity, "粘贴网站首页、搜索页面或完整搜索链接", 12));
        EditText url = edit(activity, "https://example.org/", initialUrl);
        layout.addView(url);
        Button auto = button(activity, "智能补全搜索后缀 {keyword}");
        layout.addView(auto);
        TextView preview = text(activity, "填入网址后显示自动生成的搜索模板", 12);
        preview.setTextColor(Color.rgb(95, 106, 122));
        layout.addView(preview);

        final boolean[] manualChanged = { false };
        final Runnable updatePreview = () -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                preview.setText("预览：" + c.template + "\n" + c.note);
                preview.setTextColor(c.guessed ? Color.rgb(175, 103, 30) : BLUE);
            } catch (Exception ex) {
                preview.setText("输入有效网址后将自动补全关键词");
            }
        };
        url.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int cnt, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                manualChanged[0] = true;
                updatePreview.run();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        auto.setOnClickListener(v -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                url.setText(c.template);
                url.setSelection(url.length());
                preview.setText(c.note);
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        updatePreview.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("新增资源站")
                .setView(layout)
                .setPositiveButton("添加", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String raw = url.getText().toString().trim();
                if (raw.contains("\n") || raw.contains("\r")) {
                    Toast.makeText(activity, "请每次添加一个网址", Toast.LENGTH_SHORT).show();
                    return;
                }
                SearchTemplate.Conversion c = SearchTemplate.convert(raw);
                for (Site s : working) {
                    if (s.template.equalsIgnoreCase(c.template)) {
                        Toast.makeText(activity, "该网址已存在：#" + number(s.id), Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                String label = name.getText().toString().trim();
                if (label.isEmpty()) label = SearchTemplate.defaultName(c.template);
                working.add(new Site(nextId(working), label, c.template, true, c.guessed));
                refresh.run();
                dialog.dismiss();
                if (c.guessed) Toast.makeText(activity, "已添加，通用 q= 搜索参数建议打开网页验证", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }

    private static void editSite(Activity activity, Site site, Runnable refresh) {
        LinearLayout layout = vertical(activity);
        layout.setPadding(dp(activity, 22), dp(activity, 14), dp(activity, 22), 0);
        layout.addView(text(activity, "显示名称", 12));
        EditText name = edit(activity, "名称", site.name);
        layout.addView(name);
        layout.addView(text(activity, "完整搜索网址（可自行改写后缀）", 12));
        EditText url = edit(activity, "{keyword} 替换为当前输入的书名", site.template);
        layout.addView(url);
        Button help = button(activity, "补全缺失的 {keyword}");
        layout.addView(help);
        TextView hint = text(activity, "保存时只检查网址和占位符，不会验证第三方搜索接口是否可用。", 12);
        hint.setTextColor(Color.rgb(95, 106, 122));
        layout.addView(hint);
        help.setOnClickListener(v -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                url.setText(c.template);
                url.setSelection(url.length());
                hint.setText(c.note);
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("编辑网站 #" + number(site.id))
                .setView(layout)
                .setPositiveButton("保存修改", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                site.template = c.template;
                site.name = name.getText().toString().trim().isEmpty()
                        ? SearchTemplate.defaultName(c.template) : name.getText().toString().trim();
                site.guessed = c.guessed;
                refresh.run();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}

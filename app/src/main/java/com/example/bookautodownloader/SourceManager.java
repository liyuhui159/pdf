package com.example.bookautodownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Set;

/** Modal editor for independently numbered, enabled/disabled ebook search sites. */
public final class SourceManager {
    private static final String PREF = "book_downloader_pref_v4";
    private static final String KEY = "sites_json_v8";
    private static final int BLUE = Color.rgb(47, 107, 255);
    private static final int DARK = Color.rgb(38, 50, 72);
    private static final int MUTED = Color.rgb(104, 118, 139);
    private static final int BORDER = Color.rgb(220, 228, 241);
    private static final int LIGHT = Color.rgb(245, 248, 253);
    private static final int GREEN = Color.rgb(31, 126, 83);
    private static final int AMBER = Color.rgb(161, 100, 29);
    private static final int RED = Color.rgb(177, 72, 70);

    private SourceManager() {}

    public interface Saved { void onSave(ArrayList<Site> sites); }

    public static class Site {
        public int id;
        public String name, template;
        public boolean enabled, guessed, testing;
        public int checkLevel;
        public String checkMessage = "";
        public long checkedAt;

        public Site(int id, String name, String template, boolean enabled, boolean guessed) {
            this.id = id; this.name = name; this.template = template;
            this.enabled = enabled; this.guessed = guessed;
        }
        public Site copy() {
            Site copy = new Site(id, name, template, enabled, guessed);
            copy.checkLevel = checkLevel;
            copy.checkMessage = checkMessage;
            copy.checkedAt = checkedAt;
            return copy;
        }
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
                    Site site = new Site(obj.optInt("id", i + 1),
                            obj.optString("name", SearchTemplate.defaultName(template)),
                            template, obj.optBoolean("enabled", true),
                            obj.optBoolean("guessed", false));
                    site.checkLevel = obj.optInt("checkLevel", 0);
                    site.checkMessage = obj.optString("checkMessage", "");
                    site.checkedAt = obj.optLong("checkedAt", 0L);
                    list.add(site);
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
                o.put("checkLevel", s.checkLevel);
                o.put("checkMessage", s.checkMessage);
                o.put("checkedAt", s.checkedAt);
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

    private static GradientDrawable background(Context c, int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    private static Button action(Context c, String value, int kind) {
        // kind: 0 = blue filled, 1 = neutral outlined, 2 = subtle danger
        Button b = new Button(c);
        b.setText(value);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, kind == 0 ? Typeface.BOLD : Typeface.NORMAL);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 7), 0, dp(c, 7), 0);
        b.setTextColor(kind == 0 ? Color.WHITE : kind == 2 ? RED : BLUE);
        b.setBackground(background(c,
                kind == 0 ? BLUE : kind == 2 ? Color.rgb(255, 247, 247) : Color.WHITE,
                kind == 0 ? BLUE : kind == 2 ? Color.rgb(245, 220, 220) : BORDER, 10));
        return b;
    }

    private static Button button(Context c, String value) {
        return action(c, value, 1);
    }

    private static LinearLayout.LayoutParams weighted(Context c, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, 44), weight);
        p.setMargins(dp(c, 3), 0, dp(c, 3), 0);
        return p;
    }

    private static String checkedTime(long time) {
        if (time == 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
    }

    private static void setStatus(TextView view, Site site) {
        final int shade;
        final String label;
        if (site.testing) {
            label = "正在验证网站和书名搜索…";
            shade = BLUE;
        } else if (site.checkLevel == SiteVerifier.CONFIRMED) {
            label = "验证通过 · " + site.checkMessage;
            shade = GREEN;
        } else if (site.checkLevel == SiteVerifier.UNCERTAIN) {
            label = "待确认 · " + site.checkMessage;
            shade = AMBER;
        } else if (site.checkLevel == SiteVerifier.FAILED) {
            label = "验证失败 · " + site.checkMessage;
            shade = RED;
        } else {
            label = "未验证 · 点击“验证”检查搜索接口";
            shade = MUTED;
        }
        view.setText(label + (site.checkedAt > 0 && !site.testing
                ? "\n上次校验：" + checkedTime(site.checkedAt) : ""));
        view.setTextColor(shade);
        view.setBackground(background(view.getContext(),
                site.testing ? Color.rgb(237, 244, 255)
                : site.checkLevel == SiteVerifier.CONFIRMED ? Color.rgb(235, 248, 241)
                : site.checkLevel == SiteVerifier.UNCERTAIN ? Color.rgb(255, 249, 236)
                : site.checkLevel == SiteVerifier.FAILED ? Color.rgb(255, 242, 242)
                : LIGHT,
                site.testing ? Color.rgb(208, 224, 255)
                : site.checkLevel == SiteVerifier.CONFIRMED ? Color.rgb(205, 235, 219)
                : site.checkLevel == SiteVerifier.UNCERTAIN ? Color.rgb(245, 231, 196)
                : site.checkLevel == SiteVerifier.FAILED ? Color.rgb(245, 214, 214)
                : BORDER, 8));
        view.setPadding(dp(view.getContext(), 9), dp(view.getContext(), 7),
                dp(view.getContext(), 9), dp(view.getContext(), 7));
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
                            String searchKeyword, Saved onSaved) {
        final ArrayList<Site> working = new ArrayList<>();
        for (Site site : current) working.add(site.copy());

        final String probeWord = searchKeyword == null || searchKeyword.trim().isEmpty()
                ? "Pride and Prejudice" : searchKeyword.trim();
        LinearLayout root = vertical(activity);
        root.setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 12), dp(activity, 5));
        root.setBackgroundColor(LIGHT);

        TextView caption = text(activity,
                "勾选后参与搜索；验证会用书名“" + probeWord
                        + "”测试搜索接口，不会下载文件。", 12);
        caption.setTextColor(MUTED);
        caption.setPadding(dp(activity, 7), dp(activity, 1), dp(activity, 7), dp(activity, 9));
        root.addView(caption);

        LinearLayout controls = new LinearLayout(activity);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(controls);
        Button add = action(activity, "＋ 新增网站", 0);
        Button paste = action(activity, "粘贴网址", 1);
        controls.addView(add, weighted(activity, 1));
        controls.addView(paste, weighted(activity, 1));

        LinearLayout toggles = new LinearLayout(activity);
        toggles.setGravity(Gravity.CENTER_VERTICAL);
        toggles.setPadding(dp(activity, 5), dp(activity, 7), dp(activity, 5), dp(activity, 7));
        root.addView(toggles);
        TextView selected = text(activity, "", 12);
        selected.setTextColor(MUTED);
        toggles.addView(selected, new LinearLayout.LayoutParams(0, dp(activity, 32), 1));
        selected.setGravity(Gravity.CENTER_VERTICAL);
        Button all = action(activity, "全选", 1);
        Button none = action(activity, "清空选择", 1);
        all.setTextSize(12);
        none.setTextSize(12);
        toggles.addView(all, new LinearLayout.LayoutParams(dp(activity, 65), dp(activity, 34)));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(activity, 85), dp(activity, 34));
        np.setMargins(dp(activity, 7), 0, 0, 0);
        toggles.addView(none, np);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        LinearLayout list = vertical(activity);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .53f)));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            int active = 0;
            for (Site s : working) if (s.enabled) active++;
            selected.setText("已勾选 " + active + " / " + working.size());

            list.removeAllViews();
            if (working.isEmpty()) {
                TextView empty = text(activity, "还没有网站，点击“新增网站”或“粘贴网址”添加。", 13);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(activity, 10), dp(activity, 28), dp(activity, 10), dp(activity, 28));
                list.addView(empty);
                return;
            }
            for (Site site : working) {
                LinearLayout card = vertical(activity);
                card.setPadding(dp(activity, 10), dp(activity, 9),
                        dp(activity, 10), dp(activity, 10));
                card.setBackground(background(activity, Color.WHITE, BORDER, 13));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
                cp.setMargins(dp(activity, 2), 0, dp(activity, 2), dp(activity, 9));
                list.addView(card, cp);

                CheckBox checkbox = new CheckBox(activity);
                checkbox.setText("#" + number(site.id) + "   " + site.name);
                checkbox.setTextSize(14);
                checkbox.setTextColor(DARK);
                checkbox.setTypeface(Typeface.DEFAULT_BOLD);
                checkbox.setButtonTintList(ColorStateList.valueOf(BLUE));
                checkbox.setPadding(0, 0, 0, dp(activity, 3));
                checkbox.setChecked(site.enabled);
                checkbox.setOnCheckedChangeListener((bv, enabled) -> {
                    site.enabled = enabled;
                    int count = 0;
                    for (Site x : working) if (x.enabled) count++;
                    selected.setText("已勾选 " + count + " / " + working.size());
                });
                card.addView(checkbox, new LinearLayout.LayoutParams(-1, -2));

                TextView url = text(activity, site.template, 12);
                url.setTextColor(MUTED);
                url.setPadding(dp(activity, 8), dp(activity, 7), dp(activity, 8), dp(activity, 7));
                url.setMaxLines(3);
                url.setBackground(background(activity, LIGHT, LIGHT, 8));
                card.addView(url, new LinearLayout.LayoutParams(-1, -2));

                if (site.guessed) {
                    TextView tip = text(activity, "提示：q= 是通用推测，请验证搜索接口", 11);
                    tip.setTextColor(AMBER);
                    tip.setPadding(dp(activity, 6), dp(activity, 5), 0, 0);
                    card.addView(tip);
                }

                TextView state = text(activity, "", 12);
                setStatus(state, site);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
                sp.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
                card.addView(state, sp);

                LinearLayout tools = new LinearLayout(activity);
                tools.setGravity(Gravity.CENTER_VERTICAL);
                card.addView(tools);
                Button verify = action(activity, site.testing ? "验证中…" : "验证", 0);
                verify.setEnabled(!site.testing);
                verify.setAlpha(site.testing ? .6f : 1f);
                Button modify = action(activity, "编辑", 1);
                Button delete = action(activity, "删除", 2);
                tools.addView(verify, weighted(activity, 1.15f));
                tools.addView(modify, weighted(activity, 1));
                tools.addView(delete, weighted(activity, 1));

                verify.setOnClickListener(v -> {
                    if (site.testing) return;
                    final String snapshot = site.template;
                    site.testing = true;
                    refresh[0].run();
                    new Thread(() -> {
                        SiteVerifier.Result result = SiteVerifier.verify(snapshot, probeWord);
                        activity.runOnUiThread(() -> {
                            site.testing = false;
                            if (!working.contains(site) || !snapshot.equals(site.template)) return;
                            site.checkLevel = result.level;
                            site.checkMessage = result.message
                                    + (result.httpCode > 0 ? " · HTTP " + result.httpCode : "")
                                    + " · " + result.durationMs + "ms";
                            site.checkedAt = System.currentTimeMillis();
                            if (result.level == SiteVerifier.CONFIRMED) site.guessed = false;
                            refresh[0].run();
                        });
                    }, "check-book-site-" + site.id).start();
                });
                modify.setOnClickListener(v -> editSite(activity, site, refresh[0]));
                delete.setOnClickListener(v -> new AlertDialog.Builder(activity)
                        .setTitle("删除网站 #" + number(site.id))
                        .setMessage("确定删除“" + site.name + "”？点击管理器的“保存并关闭”后才会永久保存更改。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, which) -> {
                            working.remove(site);
                            refresh[0].run();
                        }).show());
            }
        };
        refresh[0].run();

        add.setOnClickListener(v -> showAddDialog(activity, working, refresh[0], "", "", false));
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Toast.makeText(activity, "剪贴板里没有网址", Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence raw = clip.getItemAt(0).coerceToText(activity);
            if (raw == null) return;
            String pasted = raw.toString().trim();
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(pasted);
                showAddDialog(activity, working, refresh[0], "", c.template, c.guessed);
            } catch (Exception ex) {
                showAddDialog(activity, working, refresh[0], "", pasted, false);
            }
        });
        all.setOnClickListener(v -> {
            for (Site site : working) site.enabled = true;
            refresh[0].run();
        });
        none.setOnClickListener(v -> {
            for (Site site : working) site.enabled = false;
            refresh[0].run();
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("管理搜索网站")
                .setView(root)
                .setPositiveButton("保存并关闭", (d, which) -> onSaved.onSave(working))
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(BLUE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * .94f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        });
        dialog.show();
    }

    private static void showAddDialog(Activity activity, ArrayList<Site> working,
                                      Runnable refresh, String oldName, String initialUrl, boolean initialGuessed) {
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

        final boolean[] injecting = { false };
        final String[] generated = { initialUrl };
        final boolean[] guessedGenerated = { initialGuessed };
        final Runnable updatePreview = () -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                boolean guessed = c.guessed || (guessedGenerated[0] && c.template.equals(generated[0]));
                preview.setText("预览：" + c.template + "\n"
                        + (guessed ? "通用 q= 参数仅供尝试，请确认网站实际搜索地址" : c.note));
                preview.setTextColor(guessed ? Color.rgb(175, 103, 30) : BLUE);
            } catch (Exception ex) {
                preview.setText("输入有效网址后将自动补全关键词");
            }
        };
        url.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int cnt, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                updatePreview.run();
                // A large single insertion is usually a clipboard paste.
                // Avoid changing URLs while the user types one character at a time.
                if (injecting[0] || count < 8) return;
                String pasted = s.toString();
                if (pasted.contains("{keyword}") || pasted.contains("{keyword_raw}")) return;
                try {
                    SearchTemplate.Conversion c = SearchTemplate.convert(pasted);
                    if (c.template.equals(pasted)) return;
                    url.post(() -> {
                        if (!url.getText().toString().equals(pasted)) return;
                        injecting[0] = true;
                        generated[0] = c.template;
                        guessedGenerated[0] = c.guessed;
                        url.setText(c.template);
                        url.setSelection(url.length());
                        injecting[0] = false;
                        updatePreview.run();
                    });
                } catch (Exception ignored) {}
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        auto.setOnClickListener(v -> {
            try {
                SearchTemplate.Conversion c = SearchTemplate.convert(url.getText().toString());
                generated[0] = c.template;
                guessedGenerated[0] = c.guessed || guessedGenerated[0] && generated[0].equals(c.template);
                injecting[0] = true;
                url.setText(c.template);
                url.setSelection(url.length());
                injecting[0] = false;
                updatePreview.run();
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
                boolean guessed = c.guessed || (guessedGenerated[0] && c.template.equals(generated[0]));
                working.add(new Site(nextId(working), label, c.template, true, guessed));
                refresh.run();
                dialog.dismiss();
                if (guessed) Toast.makeText(activity, "已添加，通用 q= 搜索参数建议打开网页验证", Toast.LENGTH_LONG).show();
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
                site.checkLevel = 0;
                site.checkMessage = "";
                site.checkedAt = 0L;
                site.testing = false;
                refresh.run();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        dialog.show();
    }
}

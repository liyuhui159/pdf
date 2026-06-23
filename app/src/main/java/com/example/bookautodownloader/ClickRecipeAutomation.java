package com.example.bookautodownloader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class ClickRecipeAutomation {
    private static final String KEY_CLICK_RECIPE = "click_recipe_steps_v2";
    private static final int STEP_DELAY_MS = 4500;

    public interface WebViewProvider {
        WebView getWebView();
    }

    public interface KeywordProvider {
        String getKeyword();
    }

    public interface Logger {
        void log(String text);
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler;
    private final WebViewProvider provider;
    private final KeywordProvider keywordProvider;
    private final Logger logger;
    private final ArrayList<ClickStep> recordingSteps = new ArrayList<>();
    private boolean recording;

    public ClickRecipeAutomation(Activity activity, SharedPreferences prefs, Handler handler, WebViewProvider provider, KeywordProvider keywordProvider, Logger logger) {
        this.activity = activity;
        this.prefs = prefs;
        this.handler = handler;
        this.provider = provider;
        this.keywordProvider = keywordProvider;
        this.logger = logger;
    }

    public void attach(WebView webView) {
        if (webView == null) return;
        webView.addJavascriptInterface(new Bridge(webView), "AndroidClickRecorder");
    }

    public void onPageFinished(WebView webView) {
        if (recording) installRecorder(webView);
    }

    public void startRecording() {
        WebView webView = provider.getWebView();
        if (webView == null) {
            toast("没有打开的网页窗口");
            return;
        }
        recordingSteps.clear();
        recording = true;
        installRecorder(webView);
        toast("开始录制：点击书名、下载按钮等关键入口");
        log("开始录制点击步骤：会学习类似按钮，不会只记固定坐标");
    }

    public void saveRecording() {
        recording = false;
        if (recordingSteps.isEmpty()) {
            toast("还没有录到点击步骤");
            log("录制为空，未保存");
            return;
        }
        JSONArray arr = new JSONArray();
        for (ClickStep step : recordingSteps) arr.put(step.toJson());
        prefs.edit().putString(KEY_CLICK_RECIPE, arr.toString()).apply();
        toast("已保存录制步骤：" + recordingSteps.size() + " 步");
        log("已保存录制步骤：" + recordingSteps.size() + " 步");
    }

    public void runRecording() {
        ArrayList<ClickStep> steps = loadSteps();
        if (steps.isEmpty()) {
            toast("还没有保存录制步骤");
            return;
        }
        toast("开始执行录制步骤：" + steps.size() + " 步");
        log("开始执行录制步骤：" + steps.size() + " 步");
        runStep(steps, 0);
    }

    private void runStep(ArrayList<ClickStep> steps, int index) {
        if (index >= steps.size()) {
            toast("录制步骤执行完成");
            log("录制步骤执行完成");
            return;
        }
        WebView webView = provider.getWebView();
        if (webView == null) {
            toast("没有打开的网页窗口");
            return;
        }
        ClickStep step = steps.get(index);
        String keyword = keywordProvider == null ? "" : keywordProvider.getKeyword();
        webView.evaluateJavascript(clickStepJs(step, keyword), value -> {
            boolean clicked = value != null && value.contains("true");
            if (clicked) {
                log("录制回放：第 " + (index + 1) + " 步已点击类似入口 " + step.shortName());
                handler.postDelayed(() -> runStep(steps, index + 1), STEP_DELAY_MS);
            } else {
                log("录制回放：第 " + (index + 1) + " 步未找到类似入口 " + step.shortName());
                toast("第 " + (index + 1) + " 步未找到类似入口");
            }
        });
    }

    private ArrayList<ClickStep> loadSteps() {
        ArrayList<ClickStep> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_CLICK_RECIPE, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(ClickStep.fromJson(arr.getJSONObject(i)));
        } catch (Exception e) {
            log("读取录制步骤失败：" + e.getMessage());
        }
        return out;
    }

    private void installRecorder(WebView webView) {
        if (webView == null) return;
        webView.evaluateJavascript(recorderJs(), null);
    }

    private String recorderJs() {
        return "(function(){" +
                "window.__bookClickRecorderInstalled=false;" +
                "if(window.__bookClickRecorderInstalled)return 'installed';" +
                "window.__bookClickRecorderInstalled=true;" +
                "function clean(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function low(s){return clean(s).toLowerCase();}" +
                "function isTextInput(e){var tag=(e.tagName||'').toLowerCase();var type=low(e.type||'');return (tag=='textarea')||(tag=='input'&&!/button|submit|reset|image/.test(type));}" +
                "function closestClick(e){for(var n=e;n&&n!==document;n=n.parentElement){var tag=(n.tagName||'').toLowerCase();var role=n.getAttribute&&n.getAttribute('role');var cur='';try{cur=getComputedStyle(n).cursor;}catch(x){} if(tag=='a'||tag=='button'||tag=='input'||role=='button'||n.onclick||cur=='pointer'||n.getAttribute('href'))return n;}return e;}" +
                "function path(e){var p=[];for(var n=e;n&&n.nodeType===1&&n!==document.body;n=n.parentElement){var tag=(n.tagName||'').toLowerCase();var i=1,s=n;while((s=s.previousElementSibling)!=null){if((s.tagName||'').toLowerCase()==tag)i++;}p.unshift(tag+':nth-of-type('+i+')');if(p.length>6)break;}return p.join('>');}" +
                "function actionOf(e,info){var s=low(info.text+' '+info.href+' '+info.cls+' '+info.title+' '+info.aria+' '+info.role);if(/download|downloaded|下载|pdf|epub|mobi|azw|向下|down/.test(s))return 'download';if((info.tag=='a'||info.href)&&clean(info.text).length>0)return 'detail';if(/search|搜索/.test(s))return 'search';return 'click';}" +
                "document.addEventListener('click',function(ev){try{var e=closestClick(ev.target);if(isTextInput(e))return;var info={tag:(e.tagName||'').toLowerCase(),text:clean(e.innerText||e.textContent||e.value||'').substring(0,120),href:e.href||e.getAttribute('href')||'',id:e.id||'',cls:String(e.className||''),title:e.title||e.getAttribute('title')||'',aria:e.getAttribute('aria-label')||'',role:e.getAttribute('role')||'',path:path(e),page:location.href};info.action=actionOf(e,info);AndroidClickRecorder.record(JSON.stringify(info));}catch(x){}},true);" +
                "return 'ok';" +
                "})()";
    }

    private String clickStepJs(ClickStep step, String keyword) {
        String stepJson = JSONObject.quote(step.toJson().toString());
        String keyJson = JSONObject.quote(keyword == null ? "" : keyword);
        return "(function(raw,key){" +
                "var step=JSON.parse(raw);" +
                "function clean(s){return (s||'').replace(/\\s+/g,' ').trim();}" +
                "function norm(s){return clean(s).toLowerCase().replace(/[\\s\\.,，。:：;；!！?？_\\-《》【】（）(){}<>/\\\\\"'`~@#$%^&*+=|]/g,'');}" +
                "function visible(e){try{var r=e.getBoundingClientRect();var st=getComputedStyle(e);return r.width>3&&r.height>3&&st.display!='none'&&st.visibility!='hidden'&&r.bottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth;}catch(x){return true;}}" +
                "function info(e){return clean((e.innerText||'')+' '+(e.textContent||'')+' '+(e.value||'')+' '+(e.title||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.className||'')+' '+(e.href||e.getAttribute('href')||''));}" +
                "function clickable(e){for(var n=e;n&&n!==document.body;n=n.parentElement){var tag=(n.tagName||'').toLowerCase();var role=n.getAttribute&&n.getAttribute('role');var cur='';try{cur=getComputedStyle(n).cursor;}catch(x){} if(tag=='a'||tag=='button'||tag=='input'||role=='button'||n.onclick||cur=='pointer'||n.getAttribute('href'))return n;}return e;}" +
                "function fire(e){e=clickable(e);try{e.scrollIntoView({block:'center',inline:'center'});}catch(x){}var r=e.getBoundingClientRect();var x=r.left+r.width/2,y=r.top+r.height/2;['mousedown','mouseup','click'].forEach(function(t){try{e.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window,clientX:x,clientY:y}));}catch(x){}});try{e.click();}catch(x){}return true;}" +
                "function nodes(sel){return [].slice.call(document.querySelectorAll(sel)).filter(visible);}" +
                "function isBad(s){s=(s||'').toLowerCase();return /profile|notification|bookmark|favorite|tags|help|login|share|收藏|书签|标签|通知|帮助|登录|广告|ad/.test(s);}" +
                "function scoreNode(e,targetText,targetHref,wantKeyword){var s=info(e), ns=norm(s), score=0, href=e.href||e.getAttribute('href')||'';if(isBad(s))return -999;if(targetHref&&href){if(href==targetHref)score+=30;else if(href.indexOf(targetHref)>=0||targetHref.indexOf(href)>=0)score+=12;}if(targetText){var nt=norm(e.innerText||e.textContent||e.value||'');if(nt==targetText)score+=28;else if(ns.indexOf(targetText)>=0)score+=18;}if(wantKeyword){var nk=norm(wantKeyword);if(nk&&ns.indexOf(nk)>=0)score+=45;}if(step.id&&e.id==step.id)score+=10;if(step.aria&&clean(e.getAttribute('aria-label')||'')==step.aria)score+=8;if(step.title&&clean(e.title||e.getAttribute('title')||'')==step.title)score+=8;return score;}" +
                "function bestGeneric(sel,accept,bonusText){var arr=nodes(sel), best=null,bestScore=-999;for(var i=0;i<arr.length;i++){var e=arr[i], s=info(e);if(!accept(s,e))continue;var score=scoreNode(e,norm(step.text),clean(step.href),bonusText?key:'');if(score>bestScore){bestScore=score;best=e;}}return bestScore>=12?best:null;}" +
                "var action=(step.action||'click').toLowerCase();" +
                "if(action=='download'){var d=bestGeneric('a,button,input,[role=button],[onclick],[href],svg,i',function(s,e){s=s.toLowerCase();return /download|downloaded|下载|免费下载|pdf|epub|mobi|azw|向下|down|dl/.test(s);},false);if(d)return fire(d);}" +
                "if(action=='detail'){var detail=bestGeneric('a,[href],h1,h2,h3,h4,.title,[class*=title],[class*=book],[class*=name],span,div',function(s,e){var tag=(e.tagName||'').toLowerCase();return (tag=='a'||e.getAttribute('href')||/title|book|name|result|item|card/.test(String(e.className||'').toLowerCase()))&&clean(s).length>0;},true);if(detail)return fire(detail);}" +
                "if(action=='search'){var sea=bestGeneric('button,input,[role=button],[onclick],a',function(s,e){return /search|搜索/.test(s.toLowerCase());},false);if(sea)return fire(sea);}" +
                "var targetText=norm(step.text), targetHref=clean(step.href), arr=nodes('a,button,input,[role=button],[onclick],[href],h1,h2,h3,h4,span,div,svg,i'), best=null,bestScore=-999;" +
                "for(var i=0;i<arr.length;i++){var score=scoreNode(arr[i],targetText,targetHref,'');if(score>bestScore){bestScore=score;best=arr[i];}}" +
                "if(best&&bestScore>=18)return fire(best);" +
                "if(step.path){try{var p=document.querySelector(step.path);if(p&&visible(p))return fire(p);}catch(x){}}" +
                "return false;" +
                "})(" + stepJson + "," + keyJson + ")";
    }

    private void recordFromPage(WebView webView, String json) {
        if (!recording) return;
        if (provider.getWebView() != webView) return;
        try {
            ClickStep step = ClickStep.fromJson(new JSONObject(json));
            if (step.isEmpty()) return;
            recordingSteps.add(step);
            toast("已录制第 " + recordingSteps.size() + " 步：" + step.shortName());
            log("已录制第 " + recordingSteps.size() + " 步：" + step.shortName());
        } catch (Exception e) {
            log("录制点击失败：" + e.getMessage());
        }
    }

    private void log(String text) {
        if (logger != null) logger.log(text);
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private class Bridge {
        private final WebView webView;
        Bridge(WebView webView) { this.webView = webView; }
        @JavascriptInterface public void record(String json) {
            activity.runOnUiThread(() -> recordFromPage(webView, json));
        }
    }

    private static class ClickStep {
        String action = "click";
        String tag = "";
        String text = "";
        String href = "";
        String id = "";
        String cls = "";
        String title = "";
        String aria = "";
        String role = "";
        String path = "";
        String page = "";

        static ClickStep fromJson(JSONObject o) {
            ClickStep s = new ClickStep();
            s.action = o.optString("action", "");
            s.tag = o.optString("tag", "");
            s.text = o.optString("text", "");
            s.href = o.optString("href", "");
            s.id = o.optString("id", "");
            s.cls = o.optString("cls", "");
            s.title = o.optString("title", "");
            s.aria = o.optString("aria", "");
            s.role = o.optString("role", "");
            s.path = o.optString("path", "");
            s.page = o.optString("page", "");
            if (s.action.length() == 0) s.action = inferAction(s);
            return s;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("action", action);
                o.put("tag", tag);
                o.put("text", text);
                o.put("href", href);
                o.put("id", id);
                o.put("cls", cls);
                o.put("title", title);
                o.put("aria", aria);
                o.put("role", role);
                o.put("path", path);
                o.put("page", page);
            } catch (Exception ignored) {}
            return o;
        }

        boolean isEmpty() {
            return text.length() == 0 && href.length() == 0 && id.length() == 0 && aria.length() == 0 && title.length() == 0;
        }

        String shortName() {
            String label = actionName(action) + "：";
            if (text.length() > 0) return label + (text.length() > 18 ? text.substring(0, 18) + "..." : text);
            if (title.length() > 0) return label + title;
            if (aria.length() > 0) return label + aria;
            if (href.length() > 0) return label + (href.length() > 32 ? href.substring(0, 32) + "..." : href);
            return label + (tag.length() > 0 ? tag : "网页元素");
        }

        private static String inferAction(ClickStep s) {
            String all = (s.text + " " + s.href + " " + s.cls + " " + s.title + " " + s.aria).toLowerCase(Locale.ROOT);
            if (all.contains("download") || all.contains("下载") || all.contains("pdf") || all.contains("epub")) return "download";
            if ("a".equals(s.tag) || s.href.length() > 0) return "detail";
            if (all.contains("search") || all.contains("搜索")) return "search";
            return "click";
        }

        private static String actionName(String action) {
            if ("download".equals(action)) return "下载入口";
            if ("detail".equals(action)) return "详情/书名入口";
            if ("search".equals(action)) return "搜索入口";
            return "点击入口";
        }
    }
}

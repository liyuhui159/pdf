package com.example.bookautodownloader;

import org.json.JSONObject;

/** A user-initiated DOM search, using a site's actual form or visible search button.
 * It does not call hidden APIs, bypass captchas or fetch protected content.
 */
public final class NativeSiteSearch {
    private NativeSiteSearch() {}

    public static String script(String keyword) {
        return "(function() { try {"
            + "var kw=" + JSONObject.quote(keyword == null ? "" : keyword) + ";"
            + "function vis(e){if(!e)return false;var r=e.getBoundingClientRect();"
            + "var st=window.getComputedStyle(e);return r.width>0&&r.height>0"
            + "&&st.visibility!=='hidden'&&st.display!=='none';}"
            + "var els=[].slice.call(document.querySelectorAll("
            + "'input[type=search],input[name=s],input[name=q],input[name=query],"
            + "input[name=keyword],input[name=search],input[placeholder*=搜索],"
            + "input[placeholder*=书名],input[placeholder*=Search],"
            + "input[placeholder*=search],input[aria-label*=搜索]'));"
            + "if(!els.length)return 'no-search-field';"
            + "var e=els.find(function(x){return vis(x)&&!x.disabled;})||els[0];"
            + "if(e.disabled)return 'disabled';"
            + "var setter=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');"
            + "if(setter&&setter.set)setter.set.call(e,kw);else e.value=kw;"
            + "e.dispatchEvent(new Event('input',{bubbles:true}));"
            + "e.dispatchEvent(new Event('change',{bubbles:true}));"
            + "var form=e.form||e.closest('form');"
            + "if(form){var btn=form.querySelector('button[type=submit],input[type=submit]');"
            + "if(btn&&vis(btn)){btn.click();return 'form-button';}"
            + "if(form.requestSubmit){form.requestSubmit();return 'form-submit';}"
            + "form.submit();return 'form-submit';}"
            + "var holder=e.parentElement;"
            + "for(var k=0;k<4&&holder;k++,holder=holder.parentElement){"
            + "var buttons=[].slice.call(holder.querySelectorAll('button,input[type=submit],"
            + "[role=button]'));"
            + "var b=buttons.find(function(x){return vis(x)&&/搜索|检索|search|查找|查询|🔍/i"
            + ".test((x.innerText||x.value||x.getAttribute('aria-label')||'').trim());});"
            + "if(b){b.click();return 'clicked-search';}}"
            + "e.focus();e.dispatchEvent(new KeyboardEvent('keydown',"
            + "{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));"
            + "return 'filled-no-submit';"
            + "} catch(err){return 'error:'+String(err.message||err);}})();";
    }
}

package com.example.bookautodownloader;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class ShugeCatalogTest {
    private static final String EXAMPLE = "{"
            + "\"success\":true,\"api_version\":\"1.1\",\"type\":\"query_result\","
            + "\"pagination\":{\"page\":1,\"per_page\":20,\"total_items\":1},"
            + "\"items\":[{\"resource_id\":\"ZH00000442\",\"resource_name\":\"祭侄文稿\","
            + "\"download_url\":\"https://s.shuge.org/jzwgj\","
            + "\"intro_url\":\"https://www.shuge.org/view/ji_zhi_wen_gao/\"}]}";

    @Test public void repairSavedShugeHomepageSearch() {
        assertTrue(ShugeCatalog.incorrectHomeTemplate("https://new.shuge.org/?q={keyword}"));
        assertTrue(ShugeCatalog.incorrectHomeTemplate("https://www.shuge.org/"));
        assertFalse(ShugeCatalog.incorrectHomeTemplate("https://www.shuge.org/?s={keyword}"));
        assertFalse(ShugeCatalog.incorrectHomeTemplate("https://api.shuge.org/query/?q={keyword}"));
        assertEquals("https://www.shuge.org/?s={keyword}",
                SearchTemplate.convert("https://new.shuge.org/?q={keyword}").template);
        assertEquals("https://www.shuge.org/?s={keyword}",
                SearchTemplate.convert("https://new.shuge.org/").template);
        assertFalse(SearchTemplate.convert("https://new.shuge.org/").guessed);
    }

    @Test public void officialApiQueryUtf8() throws Exception {
        assertEquals("https://api.shuge.org/query/?q=%E8%A5%BF%E6%B8%B8%E8%AE%B0&per_page=100",
                ShugeCatalog.apiUrl("西游记"));
        assertEquals("西遊記", ShugeCatalog.alternateKeyword("西游记"));
        assertTrue(ShugeCatalog.isShuge("https://new.shuge.org/"));
        assertFalse(ShugeCatalog.isShuge("https://fake-shuge.org/"));
    }

    @Test public void parseDocumentedCatalogItems() throws Exception {
        ArrayList<ShugeCatalog.Book> books = ShugeCatalog.parse(EXAMPLE,"祭侄文稿");
        assertEquals(1,books.size());
        assertEquals("祭侄文稿", books.get(0).title);
        assertEquals("https://www.shuge.org/view/ji_zhi_wen_gao/",books.get(0).details);
        assertEquals("https://s.shuge.org/jzwgj",books.get(0).download);
        assertEquals(1,ShugeCatalog.totalItems(EXAMPLE));
        assertEquals(SiteVerifier.CONFIRMED,
                SiteVerifier.inspect(200,"application/json",EXAMPLE,
                        "祭侄文稿","https://api.shuge.org/query/").level);
    }

    @Test public void emptyApiIsNotTreatedAsSuccessfulSearch() throws Exception {
        String json="{\"success\":true,\"pagination\":{\"total_items\":0},\"items\":[]}";
        assertEquals(0,ShugeCatalog.parse(json,"不存在的书名").size());
        assertEquals(0,ShugeCatalog.totalItems(json));
        assertEquals(SiteVerifier.UNCERTAIN,SiteVerifier.inspect(
                200,"application/json",json,"不存在","https://api.shuge.org/query/").level);
    }

    @Test public void nativeSearchScriptEscapesQuotes() {
        String js=NativeSiteSearch.script("西游记\";alert(1)//");
        assertTrue(js.startsWith("(function()"));
        assertTrue(js.contains("dispatchEvent"));
        assertTrue(js.contains("requestSubmit"));
        assertTrue(js.contains("\\\""));
    }
}

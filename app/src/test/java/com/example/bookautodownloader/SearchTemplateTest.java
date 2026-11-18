package com.example.bookautodownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchTemplateTest {
    @Test public void gutendexBaseUsesDocumentedSearchEndpoint() {
        SearchTemplate.Conversion c = SearchTemplate.convert("https://gutendex.com/");
        assertEquals("https://gutendex.com/books?search={keyword}", c.template);
        assertFalse(c.guessed);
    }

    @Test public void gutenbergHomeUsesCorrectSearchPage() {
        SearchTemplate.Conversion c = SearchTemplate.convert("www.gutenberg.org");
        assertEquals("https://www.gutenberg.org/ebooks/search/?query={keyword}", c.template);
        assertFalse(c.guessed);
    }

    @Test public void knownSearchQueryKeepsOtherParameters() {
        SearchTemplate.Conversion c = SearchTemplate.convert("https://books.example.org/find?type=all&q=mechanics&lang=zh");
        assertEquals("https://books.example.org/find?type=all&q={keyword}&lang=zh", c.template);
        assertFalse(c.guessed);
    }

    @Test public void unknownHomeGetsEditableGuess() {
        SearchTemplate.Conversion c = SearchTemplate.convert("https://fiction.example.org");
        assertEquals("https://fiction.example.org?q={keyword}", c.template);
        assertTrue(c.guessed);
    }

    @Test public void unknownLinkWithExistingParametersAddsAmpersand() {
        SearchTemplate.Conversion c = SearchTemplate.convert("https://books.example.org/library?sort=asc");
        assertEquals("https://books.example.org/library?sort=asc&q={keyword}", c.template);
        assertTrue(c.guessed);
    }

    @Test public void existingTemplateRemainsUnmodified() {
        String raw = "https://books.example.org/search?keyword={keyword}";
        assertEquals(raw, SearchTemplate.convert(raw).template);
        assertEquals("https://books.example.org/search?keyword={keyword}",
                SearchTemplate.convert("https://books.example.org/search?keyword=%7Bkeyword%7D").template);
    }

    @Test public void unsupportedSchemesAndEmptyTextAreRejected() {
        try {
            SearchTemplate.convert("javascript:alert(1)");
            fail("must reject JavaScript links");
        } catch (IllegalArgumentException expected) { }
        try {
            SearchTemplate.convert("   ");
            fail("must reject blanks");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void guessedSiteGetsDomainName() {
        assertEquals("fiction.example.org", SearchTemplate.defaultName("https://fiction.example.org?q={keyword}"));
    }
}

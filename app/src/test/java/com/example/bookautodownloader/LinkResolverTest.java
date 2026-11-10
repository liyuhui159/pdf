package com.example.bookautodownloader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LinkResolverTest {
    @Test public void relativeBookLinkAndFileLink() {
        String html = "<html><body><a href='/ebooks/1234'><h2>Mechanical Design Handbook</h2></a>"
                + "<a href='/about'>About</a>"
                + "<a href='/files/manual.pdf'>PDF file</a></body></html>";
        List<LinkResolver.Candidate> links = LinkResolver.searchLinks(
                html, "https://example.org/search?q=mechanical", "Mechanical Design");
        assertTrue(links.stream().anyMatch(x -> x.url.equals("https://example.org/ebooks/1234")));
        assertTrue(links.stream().anyMatch(x -> x.url.equals("https://example.org/files/manual.pdf")));
        assertFalse(links.stream().anyMatch(x -> x.url.equals("https://example.org/about")));
    }

    @Test public void dataHrefIsParsedAndDangerousSchemesIgnored() {
        String html = "<a data-href='/download/file.epub'>下载电子书</a>"
                + "<a href='javascript:alert(1)'>下载书籍</a>"
                + "<a href='file:///sdcard/example.pdf'>PDF</a>";
        List<LinkResolver.Candidate> links =
                LinkResolver.searchLinks(html, "https://example.org/books", "电子书");
        assertEquals(1, links.size());
        assertEquals("https://example.org/download/file.epub", links.get(0).url);
    }

    @Test public void matchingChineseTitleAndFileHint() {
        assertTrue(LinkResolver.matchScore("机械设计手册", "《机械设计手册》第五版") >= 90);
        assertTrue(LinkResolver.fileHint("https://site.org/download/123.pdf?token=abc"));
        assertFalse(LinkResolver.fileHint("https://site.org/detail/book?id=123"));
    }

    @Test public void urlProtocolValidation() {
        assertTrue(LinkResolver.webUrl("https://example.org/book"));
        assertFalse(LinkResolver.webUrl("javascript:alert(1)"));
        assertFalse(LinkResolver.webUrl("content://downloads/12"));
    }
}

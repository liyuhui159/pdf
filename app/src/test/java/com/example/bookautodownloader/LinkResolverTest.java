package com.example.bookautodownloader;

import org.junit.Test;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
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

    private static void send(com.sun.net.httpserver.HttpExchange x, String mime, String data)
            throws java.io.IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", mime);
        x.sendResponseHeaders(200, bytes.length);
        try (OutputStream stream = x.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @Test public void followsDetailsAndValidatesActualFile() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/book", x ->
                send(x, "text/html; charset=utf-8",
                        "<html><a href='/download'>下载 PDF</a></html>"));
        server.createContext("/download", x ->
                send(x, "text/html; charset=utf-8",
                        "<html><a href='/files/real.pdf'>Download PDF</a></html>"));
        server.createContext("/files/real.pdf", x ->
                send(x, "application/pdf", "%PDF-1.7\nTest content"));
        server.createContext("/fake.pdf", x ->
                send(x, "text/html", "<html>请先登录，无法直接下载</html>"));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            LinkResolver.FileLink file = LinkResolver.resolve(base + "/book", null, "测试书籍",
                    "JUnit", url -> null, message -> {});
            assertNotNull("Must follow HTML pages to real file", file);
            assertEquals(base + "/files/real.pdf", file.url);
            assertEquals("real.pdf", file.name);

            assertNull("HTML disguised as a .pdf must not be enqueued",
                    LinkResolver.resolve(base + "/fake.pdf", null, "测试书籍",
                            "JUnit", url -> null, message -> {}));
        } finally { server.stop(0); }
    }
}

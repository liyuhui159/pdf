package com.example.bookautodownloader;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
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

    private static void respond(Socket client, String mime, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: " + mime
                + "\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @Test public void followsDetailsAndValidatesActualFile() throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread worker = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(8000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String first = in.readLine();
                    if (first == null) continue;
                    String route = first.split(" ")[1];
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {}
                    if (route.equals("/book")) {
                        respond(client, "text/html; charset=utf-8",
                                "<html><a href='/download'>下载 PDF</a></html>");
                    } else if (route.equals("/download")) {
                        respond(client, "text/html; charset=utf-8",
                                "<html><a href='/files/real.pdf'>Download PDF</a></html>");
                    } else if (route.equals("/files/real.pdf")) {
                        respond(client, "application/pdf", "%PDF-1.7\nTest content");
                    } else if (route.equals("/fake.pdf")) {
                        respond(client, "text/html", "<html>请先登录，无法直接下载</html>");
                    } else {
                        respond(client, "text/html", "<html>No file</html>");
                    }
                } catch (IOException ex) {
                    if (server.isClosed()) return;
                    throw new RuntimeException(ex);
                }
            }
        }, "ebook-test-http-server");
        worker.setDaemon(true);
        worker.start();
        String base = "http://127.0.0.1:" + server.getLocalPort();
        try {
            LinkResolver.FileLink file = LinkResolver.resolve(base + "/book", null, "测试书籍",
                    "JUnit", url -> null, message -> {});
            assertNotNull("Must follow HTML pages to real file", file);
            assertEquals(base + "/files/real.pdf", file.url);
            assertEquals("real.pdf", file.name);
            assertNull("HTML disguised as PDF must not be treated as downloadable file",
                    LinkResolver.resolve(base + "/fake.pdf", null, "测试书籍",
                            "JUnit", url -> null, message -> {}));
        } finally {
            server.close();
            worker.join(1000);
        }
    }
}

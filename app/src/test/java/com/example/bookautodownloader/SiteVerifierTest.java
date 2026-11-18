package com.example.bookautodownloader;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class SiteVerifierTest {
    @Test public void urlBuildEncodesCurrentBookTitle() throws Exception {
        assertEquals("https://example.org/search?q=%E6%9C%BA%E6%A2%B0%E8%AE%BE%E8%AE%A1",
                SiteVerifier.testUrl("https://example.org/search?q={keyword}", "机械设计"));
    }

    @Test public void confirmedJsonBookSearch() {
        SiteVerifier.Result r = SiteVerifier.inspect(200, "application/json",
                "{\"count\":12,\"results\":[{\"title\":\"Mechanical Design\"}]}",
                "Mechanical Design", "https://gutendex.com/books?search=Mechanical");
        assertEquals(SiteVerifier.CONFIRMED, r.level);
        assertTrue(r.message.contains("12"));
    }

    @Test public void zeroHitsMeansReachableButNotProven() {
        SiteVerifier.Result r = SiteVerifier.inspect(200, "application/json",
                "{\"count\":0,\"results\":[]}", "机械设计", "https://gutendex.com/books?search=foo");
        assertEquals(SiteVerifier.UNCERTAIN, r.level);
    }

    @Test public void htmlSearchResultCanBeConfirmed() {
        SiteVerifier.Result r = SiteVerifier.inspect(200, "text/html; charset=utf-8",
                "<html><body><a href='/ebooks/123'>机械设计手册</a></body></html>",
                "机械设计", "https://example.org/ebooks/search?q=mechanical");
        assertEquals(SiteVerifier.CONFIRMED, r.level);
    }

    @Test public void arbitrary200PageIsNotSearchConfirmation() {
        SiteVerifier.Result r = SiteVerifier.inspect(200, "text/html",
                "<html><body>Welcome to my homepage</body></html>",
                "机械设计", "https://example.org/?q=mechanical");
        assertEquals(SiteVerifier.UNCERTAIN, r.level);
    }

    @Test public void loginCaptchaAndStatusAreDistinct() {
        assertEquals(SiteVerifier.UNCERTAIN,
                SiteVerifier.inspect(200, "text/html",
                        "<html><body>Verify you are human CAPTCHA</body></html>",
                        "book", "https://example.org/search").level);
        assertEquals(SiteVerifier.UNCERTAIN,
                SiteVerifier.inspect(403, "text/html", "",
                        "book", "https://example.org/search").level);
        assertEquals(SiteVerifier.FAILED,
                SiteVerifier.inspect(404, "text/html", "",
                        "book", "https://example.org/search").level);
        assertEquals(SiteVerifier.UNCERTAIN,
                SiteVerifier.inspect(200, "text/html",
                        "<html>Login first</html>", "book", "https://example.org/login").level);
    }

    @Test public void liveHttpSearchMakesEncodedQueryAndReadsJson() throws Exception {
        final String[] request = {""};
        try (ServerSocket server = new ServerSocket(0)) {
            Thread worker = new Thread(() -> {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(7000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            client.getInputStream(), StandardCharsets.US_ASCII));
                    request[0] = in.readLine();
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) {}
                    byte[] bytes = "{\"count\":1,\"results\":[{\"title\":\"Test\"}]}"
                            .getBytes(StandardCharsets.UTF_8);
                    String header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                            + bytes.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = client.getOutputStream();
                    out.write(header.getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                    out.flush();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            worker.setDaemon(true);
            worker.start();
            String template = "http://127.0.0.1:" + server.getLocalPort() + "/books?search={keyword}";
            SiteVerifier.Result result = SiteVerifier.verify(template, "mechanical design");
            worker.join(5000);
            assertEquals(SiteVerifier.CONFIRMED, result.level);
            assertEquals(200, result.httpCode);
            assertTrue(request[0].contains("search=mechanical+design"));
        }
    }
}

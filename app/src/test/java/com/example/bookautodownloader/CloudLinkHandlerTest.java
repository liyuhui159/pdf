package com.example.bookautodownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class CloudLinkHandlerTest {
    @Test public void recognizesCloudProvidersWithoutMatchingLookalikeDomains() {
        assertTrue(CloudLinkHandler.isCloud("https://pan.baidu.com/s/1abc"));
        assertTrue(CloudLinkHandler.isCloud("https://pan.quark.cn/s/share"));
        assertTrue(CloudLinkHandler.isCloud("https://www.alipan.com/s/share"));
        assertTrue(CloudLinkHandler.isCloud("https://cloud.189.cn/t/file"));
        assertTrue(CloudLinkHandler.isCloud("https://www.123pan.com/s/folder"));
        assertFalse(CloudLinkHandler.isCloud("https://evilpan.baidu.com.attacker.net/s/1"));
        assertFalse(CloudLinkHandler.isCloud("javascript:alert(1)"));
    }

    @Test public void redirectsShugeShortLinksToUserControlledWebLogin() {
        assertTrue(CloudLinkHandler.isGateway("https://f.shuge.org/dl/3/?t=abc"));
        assertTrue(CloudLinkHandler.isGateway("https://s.shuge.org/abcd"));
        assertTrue(CloudLinkHandler.needsWebFlow("https://s.shuge.org/abcd"));
        assertFalse(CloudLinkHandler.needsWebFlow("https://www.gutenberg.org/files/123/123.pdf"));
        assertEquals("百度网盘", CloudLinkHandler.service("https://pan.baidu.com/s/1abc"));
    }

    @Test public void identifiesLoginPagesWithoutReadingPasswordOrTokens() {
        assertTrue(CloudLinkHandler.looksLikeWebLogin("https://passport.baidu.com/login"));
        assertTrue(CloudLinkHandler.looksLikeWebLogin("https://drive.example.com/signin"));
        assertFalse(CloudLinkHandler.looksLikeWebLogin("https://drive.example.com/files/manual.pdf"));
    }
}

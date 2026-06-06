package com.fuoverflow.common.forum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostBodyFormatterTest {
    @Test
    void rendersMarkdownBold() {
        String html = PostBodyFormatter.toHtml("**hello**");
        assertTrue(html.contains("<strong>hello</strong>") || html.contains("<b>hello</b>"));
    }

    @Test
    void escapesRawHtmlInput() {
        String html = PostBodyFormatter.toHtml("<script>alert(1)</script>");
        assertTrue(html.contains("&lt;script&gt;"));
    }
}

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
    void sanitizesWysiwygHtmlBold() {
        String html = PostBodyFormatter.toHtml("<p><strong>hello</strong></p>");
        assertTrue(html.contains("<strong>hello</strong>"));
    }

    @Test
    void stripsExternalImagesFromWysiwygHtml() {
        String html = PostBodyFormatter.toHtml(
                "<p>text</p><img src=\"https://evil.example/x.png\" alt=\"x\" />");
        assertTrue(!html.contains("<img"));
    }

    @Test
    void escapesRawHtmlInputInMarkdownMode() {
        String html = PostBodyFormatter.toHtml("<script>alert(1)</script>");
        assertTrue(html.contains("&lt;script&gt;"));
    }
}

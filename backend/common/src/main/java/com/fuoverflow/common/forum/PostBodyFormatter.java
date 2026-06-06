package com.fuoverflow.common.forum;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

public final class PostBodyFormatter {
    private static final int MAX_BODY_LENGTH = 32_000;
    private static final Parser MARKDOWN_PARSER;
    private static final HtmlRenderer MARKDOWN_RENDERER;
    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES);

    static {
        MutableDataSet options = new MutableDataSet();
        MARKDOWN_PARSER = Parser.builder(options).build();
        MARKDOWN_RENDERER = HtmlRenderer.builder(options).build();
    }

    private PostBodyFormatter() {
    }

    public static String toMarkdown(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_BODY_LENGTH);
        }
        return trimmed;
    }

    public static String toHtml(String body) {
        String markdown = toMarkdown(body);
        if (markdown.isBlank()) {
            return "<p></p>";
        }
        if (containsRawHtml(markdown)) {
            return escapePlainText(markdown);
        }
        String rendered = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
        String sanitized = HTML_POLICY.sanitize(rendered);
        return sanitized.isBlank() ? "<p></p>" : sanitized;
    }

    private static boolean containsRawHtml(String body) {
        return body.contains("<") && body.contains(">");
    }

    private static String escapePlainText(String body) {
        String escaped = body
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String[] paragraphs = escaped.split("\\R\\R+");
        StringBuilder html = new StringBuilder();
        for (String paragraph : paragraphs) {
            String withBreaks = paragraph.replace("\n", "<br>");
            html.append("<p>").append(withBreaks).append("</p>");
        }
        return html.toString();
    }
}

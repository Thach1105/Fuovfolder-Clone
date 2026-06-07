package com.fuoverflow.common.forum;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PostBodyFormatter {
    public static final String LEGACY_UPLOADS_PREFIX = "/uploads/";
    private static final int MAX_BODY_LENGTH = 32_000;
    private static final Parser MARKDOWN_PARSER;
    private static final HtmlRenderer MARKDOWN_RENDERER;
    private static final PolicyFactory HTML_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES);
    private static final Pattern IMG_TAG = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern SUBMITTED_HTML = Pattern.compile(
            "(?is)^\\s*<(p|h[1-6]|ul|ol|blockquote|div|figure|pre|table)\\b");

    private static volatile List<String> allowedImageUrlPrefixes = List.of(LEGACY_UPLOADS_PREFIX);

    static {
        MutableDataSet options = new MutableDataSet();
        MARKDOWN_PARSER = Parser.builder(options).build();
        MARKDOWN_RENDERER = HtmlRenderer.builder(options).build();
    }

    private PostBodyFormatter() {
    }

    public static void configureAllowedImageUrlPrefixes(Collection<String> prefixes) {
        allowedImageUrlPrefixes = List.copyOf(prefixes);
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
        String source = toMarkdown(body);
        if (source.isBlank()) {
            return "<p></p>";
        }
        if (isSubmittedHtml(source)) {
            return sanitizeHtml(source);
        }
        if (containsRawHtml(source)) {
            return escapePlainText(source);
        }
        String rendered = MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(source));
        return sanitizeHtml(rendered);
    }

    private static String sanitizeHtml(String html) {
        String sanitized = HTML_POLICY.sanitize(html);
        sanitized = stripDisallowedImages(sanitized);
        return sanitized.isBlank() ? "<p></p>" : sanitized;
    }

    private static boolean isSubmittedHtml(String body) {
        return SUBMITTED_HTML.matcher(body.trim()).find();
    }

    private static String stripDisallowedImages(String html) {
        Matcher matcher = IMG_TAG.matcher(html);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group();
            String src = extractSrc(tag);
            if (src != null && isAllowedImageUrl(src)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(tag));
            } else {
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String extractSrc(String imgTag) {
        Pattern srcPattern = Pattern.compile("(?i)\\ssrc\\s*=\\s*(\"([^\"]+)\"|'([^']+)'|([^\\s>]+))");
        Matcher matcher = srcPattern.matcher(imgTag);
        if (!matcher.find()) {
            return null;
        }
        if (matcher.group(2) != null) {
            return matcher.group(2);
        }
        if (matcher.group(3) != null) {
            return matcher.group(3);
        }
        return matcher.group(4);
    }

    private static boolean isAllowedImageUrl(String src) {
        for (String prefix : allowedImageUrlPrefixes) {
            if (src.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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

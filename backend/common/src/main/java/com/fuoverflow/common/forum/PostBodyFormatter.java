package com.fuoverflow.common.forum;

public final class PostBodyFormatter {
    private PostBodyFormatter() {
    }

    public static String toMarkdown(String body) {
        if (body == null) {
            return "";
        }
        return body.trim();
    }

    public static String toHtml(String body) {
        if (body == null || body.isBlank()) {
            return "<p></p>";
        }
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

package com.fuoverflow.forum.support;

import com.fuoverflow.forum.domain.CrawledForumListing;
import com.fuoverflow.forum.domain.CrawledPost;
import com.fuoverflow.forum.domain.CrawledThreadPage;
import com.fuoverflow.forum.domain.CrawledThreadSummary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses XenForo 2.x forum HTML (forum listing pages and thread pages) into the
 * crawl domain records. Selectors target the public markup served by fuoverflow.com.
 * The parser is deliberately defensive: missing optional fields yield nulls rather
 * than exceptions so a single malformed item does not abort a whole sync run.
 */
@Component
public class XenForoForumParser {
    private static final Pattern THREAD_ID = Pattern.compile("\\.(\\d+)/?$");
    private static final Pattern POST_ID = Pattern.compile("post-(\\d+)");

    public CrawledForumListing parseForumListing(String html, String forumSlug, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl == null ? "" : baseUrl);
        String title = textOrNull(doc.selectFirst("h1.p-title-value"));
        List<CrawledThreadSummary> threads = new ArrayList<>();
        for (Element item : doc.select("div.structItem--thread")) {
            CrawledThreadSummary summary = parseThreadSummary(item);
            if (summary != null) {
                threads.add(summary);
            }
        }
        int currentPage = parseCurrentPage(doc);
        int lastPage = parseLastPage(doc, currentPage);
        return new CrawledForumListing(forumSlug, title, threads, currentPage, lastPage);
    }

    private CrawledThreadSummary parseThreadSummary(Element item) {
        Element titleCell = item.selectFirst("div.structItem-title");
        if (titleCell == null) {
            return null;
        }
        Element link = titleCell.selectFirst("a[data-tp-primary]");
        if (link == null) {
            link = titleCell.select("a[href*=/threads/]").last();
        }
        if (link == null) {
            return null;
        }
        String path = link.attr("href");
        String externalId = extractThreadId(path);
        if (externalId == null) {
            return null;
        }
        String title = cleanText(link.text());
        String slug = deriveThreadSlug(path);
        String author = item.hasAttr("data-author")
                ? item.attr("data-author")
                : textOrNull(item.selectFirst(".structItem-parts .username"));
        Instant startedAt = parseTime(item.selectFirst(".structItem-startDate time"));
        Instant lastPostAt = parseTime(item.selectFirst(".structItem-cell--latest time"));
        Integer replyCount = parseMeta(item, List.of("trả lời", "answers", "replies"));
        Long viewCount = toLong(parseMeta(item, List.of("xem", "views")));
        return new CrawledThreadSummary(
                externalId, title, slug, path, author, startedAt, lastPostAt, replyCount, viewCount);
    }

    public CrawledThreadPage parseThreadPage(String html, String externalThreadId, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl == null ? "" : baseUrl);
        String title = parseThreadTitle(doc);
        List<CrawledPost> posts = new ArrayList<>();
        for (Element article : doc.select("article.message--post")) {
            CrawledPost post = parsePost(article);
            if (post != null) {
                posts.add(post);
            }
        }
        int currentPage = parseCurrentPage(doc);
        int lastPage = parseLastPage(doc, currentPage);
        return new CrawledThreadPage(externalThreadId, title, posts, currentPage, lastPage);
    }

    private CrawledPost parsePost(Element article) {
        String externalId = extractPostId(article.attr("data-content"));
        if (externalId == null) {
            externalId = extractPostId(article.attr("id"));
        }
        if (externalId == null) {
            return null;
        }
        String author = article.hasAttr("data-author")
                ? article.attr("data-author")
                : textOrNull(article.selectFirst(".message-name .username"));
        Element userLink = article.selectFirst(".message-name a[data-user-id]");
        String authorUserId = userLink != null ? emptyToNull(userLink.attr("data-user-id")) : null;
        Instant postedAt = parseTime(article.selectFirst("header.message-attribution time"));
        Element body = article.selectFirst(".message-userContent .bbWrapper");
        if (body == null) {
            body = article.selectFirst(".message-body .bbWrapper");
        }
        String bodyHtml = body != null ? body.html() : "";
        String bodyText = body != null ? cleanText(body.text()) : "";
        Element permalink = article.selectFirst("header.message-attribution a[href*=/post-]");
        String sourceUrl = permalink != null ? emptyToNull(permalink.attr("abs:href")) : null;
        if (sourceUrl == null && permalink != null) {
            sourceUrl = emptyToNull(permalink.attr("href"));
        }
        return new CrawledPost(externalId, author, authorUserId, bodyHtml, bodyText, postedAt, sourceUrl);
    }

    private String parseThreadTitle(Document doc) {
        Element h1 = doc.selectFirst("h1.p-title-value");
        if (h1 == null) {
            return null;
        }
        Element clone = h1.clone();
        clone.select(".label, .label-append, img").remove();
        return cleanText(clone.text());
    }

    private int parseCurrentPage(Document doc) {
        Element current = doc.selectFirst(".pageNav-page--current a");
        if (current != null) {
            Integer n = parseInt(current.text());
            if (n != null) {
                return n;
            }
        }
        return 1;
    }

    private int parseLastPage(Document doc, int currentPage) {
        int max = currentPage;
        for (Element a : doc.select(".pageNav-main .pageNav-page a")) {
            Integer n = parseInt(a.text());
            if (n != null && n > max) {
                max = n;
            }
        }
        return max;
    }

    private Integer parseMeta(Element item, List<String> labels) {
        for (Element dl : item.select(".structItem-cell--meta dl.pairs")) {
            Element dt = dl.selectFirst("dt");
            Element dd = dl.selectFirst("dd");
            if (dt == null || dd == null) {
                continue;
            }
            String key = dt.text().trim().toLowerCase();
            for (String label : labels) {
                if (key.contains(label)) {
                    return parseCount(dd.text());
                }
            }
        }
        return null;
    }

    private static String extractThreadId(String path) {
        if (path == null) {
            return null;
        }
        String cleaned = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        Matcher m = THREAD_ID.matcher(cleaned);
        return m.find() ? m.group(1) : null;
    }

    private static String extractPostId(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = POST_ID.matcher(value);
        return m.find() ? m.group(1) : null;
    }

    private static String deriveThreadSlug(String path) {
        if (path == null) {
            return null;
        }
        String cleaned = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        cleaned = cleaned.replaceFirst("^/threads/", "").replaceAll("/+$", "");
        int idx = cleaned.indexOf('/');
        if (idx >= 0) {
            cleaned = cleaned.substring(0, idx);
        }
        return emptyToNull(cleaned);
    }

    private static Instant parseTime(Element timeEl) {
        if (timeEl == null) {
            return null;
        }
        String ts = timeEl.attr("data-timestamp");
        if (ts != null && !ts.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(ts.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer parseCount(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replace("\u00a0", "").trim().toLowerCase();
        if (value.isEmpty()) {
            return null;
        }
        double multiplier = 1;
        if (value.endsWith("k")) {
            multiplier = 1000;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 1_000_000;
            value = value.substring(0, value.length() - 1);
        }
        // Thousands separators are dropped; a remaining dot is treated as a decimal
        // point for abbreviated values such as "1.5K".
        value = value.replace(",", "").replaceAll("[^0-9.]", "");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(value) * multiplier);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String textOrNull(Element el) {
        return el == null ? null : cleanText(el.text());
    }

    private static String cleanText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.replace("\u00a0", " ").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

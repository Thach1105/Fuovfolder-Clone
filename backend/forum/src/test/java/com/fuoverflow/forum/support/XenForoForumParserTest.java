package com.fuoverflow.forum.support;

import com.fuoverflow.forum.domain.CrawledForumListing;
import com.fuoverflow.forum.domain.CrawledThreadPage;
import com.fuoverflow.forum.domain.CrawledThreadSummary;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XenForoForumParserTest {
    private static final String BASE_URL = "https://fuoverflow.com";

    private final XenForoForumParser parser = new XenForoForumParser();

    @Test
    void parsesForumListingWithThreadsAndPagination() throws IOException {
        String html = loadFixture("fixtures/forum-list.html");

        CrawledForumListing listing = parser.parseForumListing(html, "hoi-dap", BASE_URL);

        assertThat(listing.forumSlug()).isEqualTo("hoi-dap");
        assertThat(listing.forumTitle()).contains("Hỏi Đáp");
        assertThat(listing.lastPage()).isGreaterThanOrEqualTo(2);
        assertThat(listing.threads()).isNotEmpty();

        CrawledThreadSummary first = listing.threads().getFirst();
        assertThat(first.externalId()).isEqualTo("6575");
        assertThat(first.slug()).isEqualTo("do-an-theo-chuyen-nganh-hep-ic-design.6575");
        assertThat(first.title()).contains("IC design");
        assertThat(first.authorHandle()).isNotBlank();
        assertThat(first.startedAt()).isNotNull();
        assertThat(first.lastPostAt()).isNotNull();
        assertThat(first.replyCount()).isNotNull();
        assertThat(first.viewCount()).isNotNull();
    }

    @Test
    void parsesThreadPagePostsAndTitle() throws IOException {
        String html = loadFixture("fixtures/thread-detail.html");

        CrawledThreadPage page = parser.parseThreadPage(html, "6575", BASE_URL);

        assertThat(page.title()).isEqualTo("Đồ án theo chuyên ngành hẹp IC design");
        assertThat(page.posts()).isNotEmpty();

        var firstPost = page.posts().getFirst();
        assertThat(firstPost.externalId()).isEqualTo("16144");
        assertThat(firstPost.authorHandle()).isEqualTo("namhaylamne");
        assertThat(firstPost.authorUserId()).isEqualTo("43133");
        assertThat(firstPost.bodyText()).contains("IC design");
        assertThat(firstPost.bodyHtml()).contains("IC design");
        assertThat(firstPost.postedAt()).isNotNull();
        assertThat(firstPost.sourceUrl()).startsWith("https://fuoverflow.com/threads/");
    }

    private String loadFixture(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("fixture %s present", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

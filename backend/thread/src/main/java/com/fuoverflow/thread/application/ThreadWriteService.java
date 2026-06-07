package com.fuoverflow.thread.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.forum.CategoryLookup;
import com.fuoverflow.common.forum.PollOptionWriter;
import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.common.forum.PostCreator;
import com.fuoverflow.thread.api.dto.CreateThreadRequest;
import com.fuoverflow.thread.api.dto.ThreadDetailResponse;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ThreadWriteService {
    private static final Set<String> ALLOWED_TYPES = Set.of("discussion", "article", "poll", "question");

    private final ThreadRepository threadRepository;
    private final CategoryLookup categoryLookup;
    private final PostCreator postCreator;
    private final PollOptionWriter pollOptionWriter;
    private final UserLookupService userLookupService;
    private final ThreadBookmarkService bookmarkService;

    public ThreadWriteService(
            ThreadRepository threadRepository,
            CategoryLookup categoryLookup,
            PostCreator postCreator,
            PollOptionWriter pollOptionWriter,
            UserLookupService userLookupService,
            ThreadBookmarkService bookmarkService) {
        this.threadRepository = threadRepository;
        this.categoryLookup = categoryLookup;
        this.postCreator = postCreator;
        this.pollOptionWriter = pollOptionWriter;
        this.userLookupService = userLookupService;
        this.bookmarkService = bookmarkService;
    }

    @Transactional
    public ThreadDetailResponse create(UUID authorUserId, CreateThreadRequest request) {
        String threadType = normalizeType(request.threadType());
        CategoryLookup.CategoryInfo category = categoryLookup.requireActive(request.categoryId());
        if (category.parentId() == null) {
            throw new BadRequestException("CATEGORY_NOT_LEAF", "Chỉ có thể đăng bài trong mục môn học");
        }

        String authorHandle = userLookupService.findAuthUserById(authorUserId)
                .map(user -> user.displayName() != null ? user.displayName() : user.username())
                .orElse("Thành viên");

        if ("poll".equals(threadType)) {
            List<String> options = request.pollOptions() == null ? List.of() : request.pollOptions();
            if (options.size() < 2) {
                throw new BadRequestException("POLL_OPTIONS_REQUIRED", "Bình chọn cần ít nhất 2 lựa chọn");
            }
        }

        Instant now = Instant.now();
        UUID threadId = UUID.randomUUID();
        String slug = buildSlug(request.title(), threadId);

        ThreadEntity thread = ThreadEntity.createUserThread(
                threadId,
                category.forumId(),
                category.id(),
                authorUserId,
                request.title().trim(),
                slug,
                threadType,
                authorHandle,
                clean(request.campus()),
                clean(request.semester()),
                clean(request.materialType()),
                clean(request.tags()),
                now);

        UUID postId = postCreator.createInitialPost(
                threadId,
                authorUserId,
                authorHandle,
                request.body(),
                request.attachmentFileIds());
        thread.setLastPostId(postId);
        threadRepository.save(thread);

        if ("poll".equals(threadType) && request.pollOptions() != null) {
            pollOptionWriter.write(threadId, request.pollOptions());
        }

        if (request.watchThread() == null || request.watchThread()) {
            bookmarkService.watch(authorUserId, threadId);
        }

        return ThreadMapper.toDetail(thread);
    }

    private static String normalizeType(String threadType) {
        String normalized = threadType == null ? "" : threadType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new BadRequestException("THREAD_TYPE_INVALID", "Loại bài đăng không hợp lệ");
        }
        return normalized;
    }

    private static String buildSlug(String title, UUID threadId) {
        String base = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        if (base.isBlank()) {
            base = "thread";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base + "-" + threadId.toString().substring(0, 8);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

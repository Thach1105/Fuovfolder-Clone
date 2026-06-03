package com.fuoverflow.app.validation;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.validation.ReferenceGuard;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumRepository;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReferenceGuardImpl implements ReferenceGuard {
    private final UserRepository userRepository;
    private final ForumRepository forumRepository;
    private final CategoryRepository categoryRepository;

    public ReferenceGuardImpl(UserRepository userRepository,
                               ForumRepository forumRepository,
                               CategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.forumRepository = forumRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void requireActiveUser(UUID userId) {
        if (userId == null) {
            throw new BadRequestException("INVALID_USER_ID", "User ID cannot be null");
        }
        // TODO: Check user exists and is active
        // For now, we'll implement basic existence check when UserRepository is available
    }

    @Override
    public void requireForumExists(UUID forumId) {
        if (forumId == null) {
            throw new BadRequestException("INVALID_FORUM_ID", "Forum ID cannot be null");
        }
        if (!forumRepository.existsByIdAndNotDeleted(forumId)) {
            throw new BadRequestException("FORUM_NOT_FOUND", "Forum does not exist or has been deleted");
        }
    }

    @Override
    public void requireCategoryExists(UUID categoryId) {
        if (categoryId == null) {
            throw new BadRequestException("INVALID_CATEGORY_ID", "Category ID cannot be null");
        }
        if (!categoryRepository.existsByIdAndNotDeleted(categoryId)) {
            throw new BadRequestException("CATEGORY_NOT_FOUND", "Category does not exist or has been deleted");
        }
    }

    @Override
    public void requireThreadOpen(UUID threadId) {
        // TODO: Implement when Thread module is ready
        throw new UnsupportedOperationException("Thread validation not yet implemented");
    }

    @Override
    public void requireCoursePublished(UUID courseId) {
        // TODO: Implement when Course module is ready
        throw new UnsupportedOperationException("Course validation not yet implemented");
    }

    @Override
    public void requireMaterialExists(UUID materialId) {
        // TODO: Implement when Material module is ready
        throw new UnsupportedOperationException("Material validation not yet implemented");
    }

    @Override
    public void requireMembershipPlanActive(UUID planId) {
        // TODO: Implement when Membership module is ready
        throw new UnsupportedOperationException("Membership validation not yet implemented");
    }

    @Override
    public void requireRoleExists(UUID roleId) {
        // TODO: Implement when Role module is ready
        throw new UnsupportedOperationException("Role validation not yet implemented");
    }
}

package com.fuoverflow.common.validation;

import java.util.UUID;

/**
 * Central application-level reference validator.
 *
 * Database foreign key constraints are intentionally not used; services should
 * call this guard before writing records that reference other entities.
 */
public interface ReferenceGuard {
    void requireActiveUser(UUID userId);
    void requireForumExists(UUID forumId);
    void requireCategoryExists(UUID categoryId);
    void requireThreadOpen(UUID threadId);
    void requireCoursePublished(UUID courseId);
    void requireMaterialExists(UUID materialId);
    void requireMembershipPlanActive(UUID planId);
    void requireRoleExists(UUID roleId);
}

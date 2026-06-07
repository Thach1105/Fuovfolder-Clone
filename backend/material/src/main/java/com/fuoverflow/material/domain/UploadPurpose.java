package com.fuoverflow.material.domain;

import com.fuoverflow.common.storage.FileKind;

import java.util.Set;

public enum UploadPurpose {
    AVATAR("avatar", "avatars", FileKind.IMAGE, Set.of("user.profile:update"), true),
    FORUM_IMAGE("forum_image", "forum/images", FileKind.IMAGE, Set.of("forum.post:create"), true),
    FORUM_ATTACHMENT("forum_attachment", "forum/attachments", FileKind.DOCUMENT, Set.of("forum.post:create"), false),
    SOURCE_QUESTION("source_question", "source/questions", FileKind.IMAGE, Set.of("source.media.admin:create"), true),
    SOURCE_COVER("source_cover", "source/covers", FileKind.IMAGE, Set.of("source.catalog.admin:update"), true),
    COURsera_COVER("coursera_cover", "coursera/covers", FileKind.IMAGE, Set.of("coursera.catalog.admin:update"), true),
    MEMBERSHIP_PLAN("membership_plan", "membership/plans", FileKind.IMAGE, Set.of("membership.admin:update"), true),
    AWARD_ICON("award_icon", "awards/icons", FileKind.IMAGE, Set.of("admin.panel:access"), true);

    private final String slug;
    private final String folder;
    private final FileKind fileKind;
    private final Set<String> requiredPermissions;
    private final boolean publicReadable;

    UploadPurpose(String slug, String folder, FileKind fileKind, Set<String> requiredPermissions, boolean publicReadable) {
        this.slug = slug;
        this.folder = folder;
        this.fileKind = fileKind;
        this.requiredPermissions = requiredPermissions;
        this.publicReadable = publicReadable;
    }

    public String slug() {
        return slug;
    }

    public String folder() {
        return folder;
    }

    public FileKind fileKind() {
        return fileKind;
    }

    public Set<String> requiredPermissions() {
        return requiredPermissions;
    }

    public boolean publicReadable() {
        return publicReadable;
    }

    public static UploadPurpose fromSlug(String value) {
        for (UploadPurpose purpose : values()) {
            if (purpose.slug.equals(value)) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("Unknown upload purpose: " + value);
    }
}

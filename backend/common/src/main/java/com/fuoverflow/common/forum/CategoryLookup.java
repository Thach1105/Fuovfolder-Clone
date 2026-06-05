package com.fuoverflow.common.forum;

import java.util.UUID;

public interface CategoryLookup {
    CategoryInfo requireActive(UUID categoryId);

    CategoryInfo requireActive(String forumSlug, String categorySlug);

    record CategoryInfo(UUID id, UUID forumId, UUID parentId, String slug, String title) {
    }
}

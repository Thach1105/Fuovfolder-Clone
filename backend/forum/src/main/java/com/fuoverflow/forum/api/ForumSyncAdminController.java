package com.fuoverflow.forum.api;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.forum.api.dto.StartSyncRequest;
import com.fuoverflow.forum.api.dto.SyncRunPageResponse;
import com.fuoverflow.forum.api.dto.SyncRunResponse;
import com.fuoverflow.forum.application.ForumSyncService;
import com.fuoverflow.forum.application.SyncRunQueryService;
import com.fuoverflow.forum.config.ForumCrawlerProperties;
import com.fuoverflow.forum.domain.SyncMode;
import com.fuoverflow.forum.domain.SyncScope;
import com.fuoverflow.forum.persistence.ForumSyncRunEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forum-sync")
@RequirePermission("admin.panel:access")
public class ForumSyncAdminController {
    private final ForumSyncService syncService;
    private final SyncRunQueryService syncRunQueryService;
    private final ForumCrawlerProperties properties;

    public ForumSyncAdminController(
            ForumSyncService syncService,
            SyncRunQueryService syncRunQueryService,
            ForumCrawlerProperties properties) {
        this.syncService = syncService;
        this.syncRunQueryService = syncRunQueryService;
        this.properties = properties;
    }

    @PostMapping
    @RequirePermission("forum.sync:create")
    public ApiResponse<SyncRunResponse> trigger(@RequestBody(required = false) StartSyncRequest request) {
        SyncMode mode = SyncMode.from(request != null ? request.mode() : null);
        SyncScope scope = SyncScope.from(request != null ? request.scope() : null);
        String cookie = request != null ? request.cookie() : null;
        if (mode == SyncMode.AUTHENTICATED
                && (cookie == null || cookie.isBlank())
                && (properties.authCookie() == null || properties.authCookie().isBlank())) {
            throw new BadRequestException("SYNC_COOKIE_REQUIRED",
                    "Authenticated sync requires a session cookie in the request or configuration");
        }
        ForumSyncRunEntity run = syncService.run(mode, scope, cookie);
        return ApiResponse.ok(SyncRunQueryService.toResponse(run));
    }

    @GetMapping("/runs")
    @RequirePermission("forum.sync:read")
    public ApiResponse<SyncRunPageResponse> runs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(syncRunQueryService.list(page, size));
    }
}

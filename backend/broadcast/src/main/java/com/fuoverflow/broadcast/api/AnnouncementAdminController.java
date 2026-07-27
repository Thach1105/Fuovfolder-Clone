package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequirePermission("admin.panel:access")
public class AnnouncementAdminController {

    private final AnnouncementService announcementService;

    public AnnouncementAdminController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @RequirePermission("announcement.admin:read")
    public ApiResponse<List<AnnouncementResponse>> list(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(announcementService.listByStatus(status));
    }

    @GetMapping("/{id}")
    @RequirePermission("announcement.admin:read")
    public ApiResponse<AnnouncementResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.getById(id));
    }

    @PostMapping
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> create(
            @Valid @RequestBody AnnouncementRequest request,
            @RequestParam(defaultValue = "DRAFT") String status) {
        return ApiResponse.ok(announcementService.create(request, status, currentUserId()));
    }

    @PutMapping("/{id}")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok(announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/activate")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> activate(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.deactivate(id));
    }

    @GetMapping("/{id}/preview")
    @RequirePermission("announcement.admin:read")
    public ApiResponse<AnnouncementActiveResponse> preview(@PathVariable UUID id) {
        AnnouncementResponse full = announcementService.getById(id);
        return ApiResponse.ok(new AnnouncementActiveResponse(
                full.id(), full.contentHtml(), full.backgroundColor(),
                full.linkUrl(), full.linkLabel(), full.priority(),
                full.scrollSpeed(), full.stepSeconds()));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication required");
        }
        return UUID.fromString(auth.getName());
    }
}

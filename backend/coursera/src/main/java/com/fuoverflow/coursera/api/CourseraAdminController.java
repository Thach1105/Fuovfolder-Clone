package com.fuoverflow.coursera.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.coursera.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.coursera.api.dto.AdminRequestDetailResponse;
import com.fuoverflow.coursera.api.dto.CreateCatalogItemRequest;
import com.fuoverflow.coursera.api.dto.CourseraOverviewResponse;
import com.fuoverflow.coursera.api.dto.RequestPageResponse;
import com.fuoverflow.coursera.api.dto.UpdateCatalogItemRequest;
import com.fuoverflow.coursera.api.dto.UpdateRequestStatusBody;
import com.fuoverflow.coursera.application.CourseraCatalogAdminService;
import com.fuoverflow.coursera.application.CourseraRequestAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/coursera")
@RequirePermission("admin.panel:access")
public class CourseraAdminController {
    private final CourseraCatalogAdminService catalogAdminService;
    private final CourseraRequestAdminService requestAdminService;

    public CourseraAdminController(
            CourseraCatalogAdminService catalogAdminService,
            CourseraRequestAdminService requestAdminService) {
        this.catalogAdminService = catalogAdminService;
        this.requestAdminService = requestAdminService;
    }

    @GetMapping("/overview")
    @RequirePermission("coursera.request.admin:read")
    public ApiResponse<CourseraOverviewResponse> overview() {
        return ApiResponse.ok(requestAdminService.overview());
    }

    @GetMapping("/catalog")
    @RequirePermission("coursera.catalog.admin:read")
    public ApiResponse<List<AdminCatalogItemResponse>> listCatalog() {
        return ApiResponse.ok(catalogAdminService.listAll());
    }

    @GetMapping("/catalog/{id}")
    @RequirePermission("coursera.catalog.admin:read")
    public ApiResponse<AdminCatalogItemResponse> getCatalog(@PathVariable UUID id) {
        return ApiResponse.ok(catalogAdminService.get(id));
    }

    @PostMapping("/catalog")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("coursera.catalog.admin:create")
    public ApiResponse<AdminCatalogItemResponse> createCatalog(@Valid @RequestBody CreateCatalogItemRequest request) {
        return ApiResponse.ok(catalogAdminService.create(request));
    }

    @PutMapping("/catalog/{id}")
    @RequirePermission("coursera.catalog.admin:update")
    public ApiResponse<AdminCatalogItemResponse> updateCatalog(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCatalogItemRequest request) {
        return ApiResponse.ok(catalogAdminService.update(id, request));
    }

    @DeleteMapping("/catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("coursera.catalog.admin:delete")
    public void deleteCatalog(@PathVariable UUID id) {
        catalogAdminService.delete(id);
    }

    @GetMapping("/requests")
    @RequirePermission("coursera.request.admin:read")
    public ApiResponse<RequestPageResponse> listRequests(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID catalogItemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(requestAdminService.listAll(userId, catalogItemId, status, period, page, size));
    }

    @GetMapping("/requests/{id}")
    @RequirePermission("coursera.request.admin:read")
    public ApiResponse<AdminRequestDetailResponse> getRequest(@PathVariable UUID id) {
        return ApiResponse.ok(requestAdminService.getDetail(id));
    }

    @PatchMapping("/requests/{id}/status")
    @RequirePermission("coursera.request.admin:update")
    public ApiResponse<AdminRequestDetailResponse> updateStatus(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequestStatusBody body) {
        UUID actorId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(requestAdminService.updateStatus(actorId, id, body));
    }
}

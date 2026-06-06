package com.fuoverflow.coursera.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.coursera.api.dto.CreateCourseraRequestBody;
import com.fuoverflow.coursera.api.dto.RequestDetailResponse;
import com.fuoverflow.coursera.api.dto.RequestPageResponse;
import com.fuoverflow.coursera.api.dto.RequestStatsResponse;
import com.fuoverflow.coursera.application.CourseraRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coursera/requests")
public class CourseraRequestController {
    private final CourseraRequestService requestService;

    public CourseraRequestController(CourseraRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("coursera.request:create")
    public ApiResponse<RequestDetailResponse> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCourseraRequestBody body) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(requestService.create(userId, body, idempotencyKey));
    }

    @GetMapping
    @RequirePermission("coursera.request:read")
    public ApiResponse<RequestPageResponse> list(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(requestService.listMine(userId, status, page, size));
    }

    @GetMapping("/stats")
    @RequirePermission("coursera.request:read")
    public ApiResponse<RequestStatsResponse> stats(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(requestService.stats(userId));
    }

    @GetMapping("/{id}")
    @RequirePermission("coursera.request:read")
    public ApiResponse<RequestDetailResponse> get(Authentication authentication, @PathVariable UUID id) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(requestService.getMine(userId, id));
    }
}

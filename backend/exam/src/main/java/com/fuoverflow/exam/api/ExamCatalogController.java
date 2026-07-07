package com.fuoverflow.exam.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicPeItemResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.application.ExamCatalogQueryService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam/catalog")
public class ExamCatalogController {
    private final ExamCatalogQueryService queryService;

    public ExamCatalogController(ExamCatalogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission(value = "exam.catalog:read", allowAnonymous = true)
    public ApiResponse<List<PublicSubjectCardResponse>> list() {
        return ApiResponse.ok(queryService.listActive());
    }

    @GetMapping("/{idOrCode}")
    @RequirePermission(value = "exam.catalog:read", allowAnonymous = true)
    public ApiResponse<PublicSubjectDetailResponse> getDetail(
            @PathVariable String idOrCode,
            Authentication authentication) {
        return ApiResponse.ok(queryService.getDetail(idOrCode, resolveUserId(authentication)));
    }

    @GetMapping("/{idOrCode}/fe")
    @RequirePermission(value = "exam.catalog:read", allowAnonymous = true)
    public ApiResponse<PublicFeQuestionListResponse> feQuestions(
            @PathVariable String idOrCode,
            Authentication authentication) {
        return ApiResponse.ok(queryService.listFeQuestions(idOrCode, resolveUserId(authentication)));
    }

    @GetMapping("/{idOrCode}/pe")
    @RequirePermission("exam.content:read")
    public ApiResponse<List<PublicPeItemResponse>> peItems(
            @PathVariable String idOrCode,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(queryService.listPeItems(idOrCode, userId));
    }

    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return UUID.fromString(authentication.getName());
    }
}

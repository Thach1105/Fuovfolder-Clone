package com.fuoverflow.source.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.source.api.dto.CatalogItemDetailResponse;
import com.fuoverflow.source.api.dto.CatalogItemResponse;
import com.fuoverflow.source.api.dto.CatalogPageResponse;
import com.fuoverflow.source.api.dto.PublicQuestionResponse;
import com.fuoverflow.source.application.SourceCatalogQueryService;
import com.fuoverflow.source.application.SourceQuestionQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source/catalog")
public class SourceCatalogController {
    private final SourceCatalogQueryService queryService;
    private final SourceQuestionQueryService questionQueryService;

    public SourceCatalogController(
            SourceCatalogQueryService queryService,
            SourceQuestionQueryService questionQueryService) {
        this.queryService = queryService;
        this.questionQueryService = questionQueryService;
    }

    @GetMapping
    public ApiResponse<CatalogPageResponse> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        return ApiResponse.ok(queryService.browse(q, featured, sort, page, size));
    }

    @GetMapping("/featured")
    public ApiResponse<List<CatalogItemResponse>> featured(
            @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(queryService.featured(limit));
    }

    @GetMapping("/{idOrCode}")
    public ApiResponse<CatalogItemDetailResponse> get(
            @PathVariable String idOrCode,
            Authentication authentication) {
        UUID userId = authentication != null && authentication.isAuthenticated()
                ? UUID.fromString(authentication.getName())
                : null;
        return ApiResponse.ok(queryService.getDetail(idOrCode, userId));
    }

    @GetMapping("/{idOrCode}/questions")
    public ApiResponse<List<PublicQuestionResponse>> listQuestions(
            @PathVariable String idOrCode,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(questionQueryService.listForUser(idOrCode, userId));
    }
}

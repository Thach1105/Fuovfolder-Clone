package com.fuoverflow.coursera.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.coursera.api.dto.CatalogItemResponse;
import com.fuoverflow.coursera.application.CourseraCatalogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coursera/catalog")
public class CourseraCatalogController {
    private final CourseraCatalogQueryService queryService;

    public CourseraCatalogController(CourseraCatalogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission(value = "coursera.catalog:read", allowAnonymous = true)
    public ApiResponse<List<CatalogItemResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean featured) {
        return ApiResponse.ok(queryService.listActive(q, featured));
    }

    @GetMapping("/{id}")
    @RequirePermission(value = "coursera.catalog:read", allowAnonymous = true)
    public ApiResponse<CatalogItemResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(queryService.getActive(id));
    }
}

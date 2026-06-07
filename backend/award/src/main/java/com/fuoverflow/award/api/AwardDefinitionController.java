package com.fuoverflow.award.api;

import com.fuoverflow.award.api.dto.AwardDefinitionResponse;
import com.fuoverflow.award.application.AwardDefinitionQueryService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/awards/definitions")
public class AwardDefinitionController {
    private final AwardDefinitionQueryService queryService;

    public AwardDefinitionController(AwardDefinitionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission(value = "forum.post:read", allowAnonymous = true)
    public ApiResponse<List<AwardDefinitionResponse>> listActive() {
        return ApiResponse.ok(queryService.listActive());
    }
}

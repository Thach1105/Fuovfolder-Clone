package com.fuoverflow.source.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.source.api.dto.CheckScoreConfigRequest;
import com.fuoverflow.source.api.dto.CheckScoreConfigResponse;
import com.fuoverflow.source.api.dto.CheckScoreResponse;
import com.fuoverflow.source.api.dto.CheckScoreSubjectsResponse;
import com.fuoverflow.source.application.CheckScoreService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class CheckScoreController {
    private final CheckScoreService service;

    public CheckScoreController(CheckScoreService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/check-score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("points:read")
    public ApiResponse<CheckScoreResponse> check(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "voucherCode", required = false) String voucherCode) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(service.check(userId, file, voucherCode), "Chấm điểm thành công");
    }

    @GetMapping("/api/v1/check-score/subjects")
    @RequirePermission("points:read")
    public ApiResponse<CheckScoreSubjectsResponse> subjects() {
        return ApiResponse.ok(service.subjects());
    }

    @RestController
    @RequestMapping("/api/v1/admin/check-score")
    @RequirePermission("admin.panel:access")
    static class AdminCheckScoreController {
        private final CheckScoreService service;

        AdminCheckScoreController(CheckScoreService service) {
            this.service = service;
        }

        @GetMapping("/config")
        public ApiResponse<CheckScoreConfigResponse> config() {
            return ApiResponse.ok(service.config());
        }

        @PutMapping("/config")
        public ApiResponse<CheckScoreConfigResponse> updateConfig(
                @Valid @RequestBody CheckScoreConfigRequest request) {
            return ApiResponse.ok(service.updateConfig(
                    request.authorizeKey(),
                    request.xsrfCookie(),
                    request.checkScoreUrl()));
        }
    }
}

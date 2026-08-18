package com.fuoverflow.grading.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.grading.api.dto.ImportPaperRequest;
import com.fuoverflow.grading.api.dto.PaperPreviewResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.api.dto.SetAnswerRequest;
import com.fuoverflow.grading.application.PaperAnswerService;
import com.fuoverflow.grading.application.PaperImportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/grading/papers")
public class GradingPaperAdminController {

    private final PaperImportService importService;
    private final PaperAnswerService answerService;

    public GradingPaperAdminController(PaperImportService importService,
                                       PaperAnswerService answerService) {
        this.importService = importService;
        this.answerService = answerService;
    }

    /** Converts and validates without storing anything, so the admin can review first. */
    @PostMapping("/preview")
    @RequirePermission("grading.paper.admin:create")
    public ApiResponse<PaperPreviewResponse> preview(@Valid @RequestBody ImportPaperRequest request) {
        return ApiResponse.ok(importService.preview(request.payload()));
    }

    @PostMapping("/import")
    @RequirePermission("grading.paper.admin:create")
    public ApiResponse<PaperSummaryResponse> importPaper(
            Authentication authentication,
            @Valid @RequestBody ImportPaperRequest request) {
        UUID adminId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(
                importService.importPayload(adminId, request.payload(), request.payloadId(),
                        request.confirmedFingerprint()),
                "Đã lưu đề, hãy hoàn thiện đáp án trước khi phát hành");
    }

    @PutMapping("/{paperId}/questions/{qid}/answer")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<Void> setAnswer(
            @PathVariable UUID paperId,
            @PathVariable long qid,
            @Valid @RequestBody SetAnswerRequest request) {
        answerService.setAnswer(paperId, qid, request.qaids());
        return ApiResponse.ok(null, "Đã lưu đáp án");
    }

    @PostMapping("/{paperId}/publish")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<PaperSummaryResponse> publish(@PathVariable UUID paperId) {
        return ApiResponse.ok(answerService.publish(paperId), "Đã phát hành đề");
    }

    @DeleteMapping("/{paperId}")
    @RequirePermission("grading.paper.admin:delete")
    public ApiResponse<Void> delete(@PathVariable UUID paperId) {
        answerService.softDelete(paperId);
        return ApiResponse.ok(null, "Đã xoá đề");
    }
}

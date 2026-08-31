package com.fuoverflow.grading.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.grading.api.dto.ApplyAnswersRequest;
import com.fuoverflow.grading.api.dto.ApplyAnswersResponse;
import com.fuoverflow.grading.api.dto.ImportPaperRequest;
import com.fuoverflow.grading.api.dto.PaperDetailResponse;
import com.fuoverflow.grading.api.dto.PaperPreviewResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.api.dto.SetAnswerRequest;
import com.fuoverflow.grading.application.PaperAnswerImportService;
import com.fuoverflow.grading.application.PaperAnswerService;
import com.fuoverflow.grading.application.PaperImportService;
import com.fuoverflow.grading.application.PaperQueryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/grading/papers")
public class GradingPaperAdminController {

    private final PaperImportService importService;
    private final PaperAnswerService answerService;
    private final PaperQueryService queryService;
    private final PaperAnswerImportService answerImportService;

    public GradingPaperAdminController(PaperImportService importService,
                                       PaperAnswerService answerService,
                                       PaperQueryService queryService,
                                       PaperAnswerImportService answerImportService) {
        this.importService = importService;
        this.answerService = answerService;
        this.queryService = queryService;
        this.answerImportService = answerImportService;
    }

    @GetMapping
    @RequirePermission("grading.paper.admin:read")
    public ApiResponse<List<PaperSummaryResponse>> list(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.list(subject, status));
    }

    @GetMapping("/{paperId}")
    @RequirePermission("grading.paper.admin:read")
    public ApiResponse<PaperDetailResponse> detail(@PathVariable UUID paperId) {
        return ApiResponse.ok(queryService.detail(paperId));
    }

    @GetMapping(value = "/{paperId}/questions/{qid}/image", produces = MediaType.IMAGE_PNG_VALUE)
    @RequirePermission("grading.paper.admin:read")
    public ResponseEntity<byte[]> questionImage(@PathVariable UUID paperId, @PathVariable long qid) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(queryService.questionImage(paperId, qid));
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

    /** Applies an answer key from an external source. Use dryRun to review before writing. */
    @PostMapping("/{paperId}/answers/apply")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<ApplyAnswersResponse> applyAnswers(
            @PathVariable UUID paperId,
            @Valid @RequestBody ApplyAnswersRequest request) {
        return ApiResponse.ok(answerImportService.apply(paperId, request), "Đã nhập bộ đáp án");
    }

    @PostMapping("/{paperId}/questions/{qid}/confirm")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<Void> confirmSuggestion(@PathVariable UUID paperId, @PathVariable long qid) {
        answerImportService.confirmSuggestion(paperId, qid);
        return ApiResponse.ok(null, "Đã xác nhận đáp án");
    }
}

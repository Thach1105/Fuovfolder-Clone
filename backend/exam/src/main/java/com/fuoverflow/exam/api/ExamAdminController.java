package com.fuoverflow.exam.api;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.exam.api.dto.AddPeResourceRequest;
import com.fuoverflow.exam.api.dto.AdminCommentPageResponse;
import com.fuoverflow.exam.api.dto.AdminFeQuestionResponse;
import com.fuoverflow.exam.api.dto.AdminPaperResponse;
import com.fuoverflow.exam.api.dto.AdminPeItemResponse;
import com.fuoverflow.exam.api.dto.AdminSubjectResponse;
import com.fuoverflow.exam.api.dto.AdminWebhookEventResponse;
import com.fuoverflow.exam.api.dto.CreateFeQuestionRequest;
import com.fuoverflow.exam.api.dto.CreatePeItemRequest;
import com.fuoverflow.exam.api.dto.CreateSubjectRequest;
import com.fuoverflow.exam.api.dto.MediaUploadResponse;
import com.fuoverflow.exam.api.dto.ReorderRequest;
import com.fuoverflow.exam.api.dto.UpdateFeQuestionRequest;
import com.fuoverflow.exam.api.dto.UpdatePeItemRequest;
import com.fuoverflow.exam.api.dto.UpdateSubjectRequest;
import com.fuoverflow.exam.application.ExamCommentService;
import com.fuoverflow.exam.application.ExamFeQuestionAdminService;
import com.fuoverflow.exam.application.ExamMediaService;
import com.fuoverflow.exam.application.ExamPaperAdminService;
import com.fuoverflow.exam.application.ExamPeItemAdminService;
import com.fuoverflow.exam.application.ExamSubjectAdminService;
import com.fuoverflow.material.domain.UploadPurpose;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exam")
@RequirePermission("admin.panel:access")
public class ExamAdminController {
    private final ExamSubjectAdminService subjectService;
    private final ExamFeQuestionAdminService feQuestionService;
    private final ExamPeItemAdminService peItemService;
    private final ExamPaperAdminService paperService;
    private final ExamCommentService commentService;
    private final ExamMediaService mediaService;

    public ExamAdminController(
            ExamSubjectAdminService subjectService,
            ExamFeQuestionAdminService feQuestionService,
            ExamPeItemAdminService peItemService,
            ExamPaperAdminService paperService,
            ExamCommentService commentService,
            ExamMediaService mediaService) {
        this.subjectService = subjectService;
        this.feQuestionService = feQuestionService;
        this.peItemService = peItemService;
        this.paperService = paperService;
        this.commentService = commentService;
        this.mediaService = mediaService;
    }

    // --- subjects -------------------------------------------------------------

    @GetMapping("/subjects")
    @RequirePermission("exam.subject.admin:read")
    public ApiResponse<List<AdminSubjectResponse>> listSubjects() {
        return ApiResponse.ok(subjectService.listAll());
    }

    @GetMapping("/subjects/{id}")
    @RequirePermission("exam.subject.admin:read")
    public ApiResponse<AdminSubjectResponse> getSubject(@PathVariable UUID id) {
        return ApiResponse.ok(subjectService.get(id));
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("exam.subject.admin:create")
    public ApiResponse<AdminSubjectResponse> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return ApiResponse.ok(subjectService.create(request));
    }

    @PutMapping("/subjects/{id}")
    @RequirePermission("exam.subject.admin:update")
    public ApiResponse<AdminSubjectResponse> updateSubject(
            @PathVariable UUID id, @Valid @RequestBody UpdateSubjectRequest request) {
        return ApiResponse.ok(subjectService.update(id, request));
    }

    @DeleteMapping("/subjects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.subject.admin:delete")
    public void deleteSubject(@PathVariable UUID id) {
        subjectService.delete(id);
    }

    // --- FE questions ---------------------------------------------------------

    @GetMapping("/subjects/{subjectId}/fe")
    @RequirePermission("exam.question.admin:read")
    public ApiResponse<List<AdminFeQuestionResponse>> listFe(@PathVariable UUID subjectId) {
        return ApiResponse.ok(feQuestionService.list(subjectId));
    }

    @PostMapping("/subjects/{subjectId}/fe")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("exam.question.admin:create")
    public ApiResponse<AdminFeQuestionResponse> createFe(
            @PathVariable UUID subjectId, @Valid @RequestBody CreateFeQuestionRequest request) {
        return ApiResponse.ok(feQuestionService.create(subjectId, request));
    }

    @PutMapping("/subjects/{subjectId}/fe/{questionId}")
    @RequirePermission("exam.question.admin:update")
    public ApiResponse<AdminFeQuestionResponse> updateFe(
            @PathVariable UUID subjectId,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateFeQuestionRequest request) {
        return ApiResponse.ok(feQuestionService.update(subjectId, questionId, request));
    }

    @DeleteMapping("/subjects/{subjectId}/fe/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.question.admin:delete")
    public void deleteFe(@PathVariable UUID subjectId, @PathVariable UUID questionId) {
        feQuestionService.delete(subjectId, questionId);
    }

    @PutMapping("/subjects/{subjectId}/fe/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.question.admin:update")
    public void reorderFe(@PathVariable UUID subjectId, @Valid @RequestBody ReorderRequest request) {
        feQuestionService.reorder(subjectId, request);
    }

    // --- PE items + resources -------------------------------------------------

    @GetMapping("/subjects/{subjectId}/pe")
    @RequirePermission("exam.pe.admin:read")
    public ApiResponse<List<AdminPeItemResponse>> listPe(@PathVariable UUID subjectId) {
        return ApiResponse.ok(peItemService.list(subjectId));
    }

    @PostMapping("/subjects/{subjectId}/pe")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("exam.pe.admin:create")
    public ApiResponse<AdminPeItemResponse> createPe(
            @PathVariable UUID subjectId, @Valid @RequestBody CreatePeItemRequest request) {
        return ApiResponse.ok(peItemService.create(subjectId, request));
    }

    @PutMapping("/subjects/{subjectId}/pe/{itemId}")
    @RequirePermission("exam.pe.admin:update")
    public ApiResponse<AdminPeItemResponse> updatePe(
            @PathVariable UUID subjectId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdatePeItemRequest request) {
        return ApiResponse.ok(peItemService.update(subjectId, itemId, request));
    }

    @DeleteMapping("/subjects/{subjectId}/pe/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.pe.admin:delete")
    public void deletePe(@PathVariable UUID subjectId, @PathVariable UUID itemId) {
        peItemService.delete(subjectId, itemId);
    }

    @PostMapping("/subjects/{subjectId}/pe/{itemId}/resources")
    @RequirePermission("exam.pe.admin:update")
    public ApiResponse<AdminPeItemResponse> addResource(
            @PathVariable UUID subjectId,
            @PathVariable UUID itemId,
            @Valid @RequestBody AddPeResourceRequest request) {
        return ApiResponse.ok(peItemService.addResource(subjectId, itemId, request));
    }

    @DeleteMapping("/subjects/{subjectId}/pe/{itemId}/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.pe.admin:update")
    public void deleteResource(
            @PathVariable UUID subjectId,
            @PathVariable UUID itemId,
            @PathVariable UUID resourceId) {
        peItemService.deleteResource(subjectId, itemId, resourceId);
    }

    // --- papers (webhook-ingested + legacy) -----------------------------------

    @GetMapping("/papers")
    @RequirePermission("exam.paper.admin:read")
    public ApiResponse<List<AdminPaperResponse>> listPapers(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(paperService.list(subjectId, status));
    }

    @GetMapping("/papers/{paperId}")
    @RequirePermission("exam.paper.admin:read")
    public ApiResponse<AdminPaperResponse> getPaper(@PathVariable UUID paperId) {
        return ApiResponse.ok(paperService.get(paperId));
    }

    @PostMapping("/papers/{paperId}/publish")
    @RequirePermission("exam.paper.admin:publish")
    public ApiResponse<AdminPaperResponse> publishPaper(@PathVariable UUID paperId) {
        return ApiResponse.ok(paperService.publish(paperId));
    }

    @DeleteMapping("/papers/{paperId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.paper.admin:delete")
    public void deletePaper(@PathVariable UUID paperId) {
        paperService.delete(paperId);
    }

    @GetMapping("/webhook-events/{receiptId}")
    @RequirePermission("exam.webhook.admin:read")
    public ApiResponse<AdminWebhookEventResponse> getWebhookEvent(@PathVariable UUID receiptId) {
        return ApiResponse.ok(paperService.getWebhookEvent(receiptId));
    }

    // --- media upload ---------------------------------------------------------

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("exam.media.admin:create")
    public ApiResponse<MediaUploadResponse> uploadMedia(
            Authentication authentication,
            @RequestParam("purpose") String purpose,
            @RequestPart("file") MultipartFile file) {
        UUID adminUserId = UUID.fromString(authentication.getName());
        UploadPurpose uploadPurpose = resolvePurpose(purpose);
        if (uploadPurpose == UploadPurpose.EXAM_FE_IMAGE) {
            ExamMediaService.BlurUploadResult result = mediaService.uploadWithBlur(file, uploadPurpose, adminUserId);
            return ApiResponse.ok(new MediaUploadResponse(result.objectKey(), result.blurObjectKey(), result.publicUrl()));
        }
        StoredObject stored = mediaService.upload(file, uploadPurpose, adminUserId);
        return ApiResponse.ok(new MediaUploadResponse(stored.objectKey(), null, stored.publicUrl()));
    }

    // --- comment moderation ---------------------------------------------------

    @GetMapping("/comments")
    @RequirePermission("exam.comment.admin:delete")
    public ApiResponse<AdminCommentPageResponse> listComments(
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) UUID examSubjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(commentService.adminList(subjectType, examSubjectId, page, size));
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.comment.admin:delete")
    public void moderateComment(@PathVariable UUID commentId) {
        commentService.adminDelete(commentId);
    }

    private static UploadPurpose resolvePurpose(String purpose) {
        return switch (purpose) {
            case "exam_fe_image" -> UploadPurpose.EXAM_FE_IMAGE;
            case "exam_pe_image" -> UploadPurpose.EXAM_PE_IMAGE;
            case "exam_pe_resource" -> UploadPurpose.EXAM_PE_RESOURCE;
            default -> throw new BadRequestException("INVALID_UPLOAD_PURPOSE", "Unknown upload purpose: " + purpose);
        };
    }
}

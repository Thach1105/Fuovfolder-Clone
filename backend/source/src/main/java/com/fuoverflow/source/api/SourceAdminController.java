package com.fuoverflow.source.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.source.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.source.api.dto.AdminPurchasePageResponse;
import com.fuoverflow.source.api.dto.AdminPurchaseResponse;
import com.fuoverflow.source.api.dto.AdminQuestionResponse;
import com.fuoverflow.source.api.dto.CreateCatalogItemRequest;
import com.fuoverflow.source.api.dto.CreateQuestionRequest;
import com.fuoverflow.source.api.dto.MediaUploadResponse;
import com.fuoverflow.source.api.dto.RefundPurchaseRequest;
import com.fuoverflow.source.api.dto.ReorderQuestionsRequest;
import com.fuoverflow.source.api.dto.SetRelatedItemsRequest;
import com.fuoverflow.source.api.dto.SourceOverviewResponse;
import com.fuoverflow.source.api.dto.UpdateCatalogItemRequest;
import com.fuoverflow.source.api.dto.UpdateQuestionRequest;
import com.fuoverflow.source.application.SourceCatalogAdminService;
import com.fuoverflow.source.application.SourceMediaService;
import com.fuoverflow.source.application.SourcePurchaseAdminService;
import com.fuoverflow.source.application.SourceQuestionAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/admin/source")
public class SourceAdminController {
    private final SourceCatalogAdminService catalogAdminService;
    private final SourcePurchaseAdminService purchaseAdminService;
    private final SourceQuestionAdminService questionAdminService;
    private final SourceMediaService mediaService;

    public SourceAdminController(
            SourceCatalogAdminService catalogAdminService,
            SourcePurchaseAdminService purchaseAdminService,
            SourceQuestionAdminService questionAdminService,
            SourceMediaService mediaService) {
        this.catalogAdminService = catalogAdminService;
        this.purchaseAdminService = purchaseAdminService;
        this.questionAdminService = questionAdminService;
        this.mediaService = mediaService;
    }

    @GetMapping("/overview")
    public ApiResponse<SourceOverviewResponse> overview() {
        return ApiResponse.ok(purchaseAdminService.overview());
    }

    @GetMapping("/catalog")
    public ApiResponse<List<AdminCatalogItemResponse>> listCatalog() {
        return ApiResponse.ok(catalogAdminService.listAll());
    }

    @GetMapping("/catalog/{id}")
    public ApiResponse<AdminCatalogItemResponse> getCatalog(@PathVariable UUID id) {
        return ApiResponse.ok(catalogAdminService.get(id));
    }

    @PostMapping("/catalog")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCatalogItemResponse> createCatalog(@Valid @RequestBody CreateCatalogItemRequest request) {
        return ApiResponse.ok(catalogAdminService.create(request));
    }

    @PutMapping("/catalog/{id}")
    public ApiResponse<AdminCatalogItemResponse> updateCatalog(
            @PathVariable UUID id, @Valid @RequestBody UpdateCatalogItemRequest request) {
        return ApiResponse.ok(catalogAdminService.update(id, request));
    }

    @DeleteMapping("/catalog/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCatalog(@PathVariable UUID id) {
        catalogAdminService.delete(id);
    }

    @PutMapping("/catalog/{id}/related")
    public ApiResponse<AdminCatalogItemResponse> setRelated(
            @PathVariable UUID id, @Valid @RequestBody SetRelatedItemsRequest request) {
        catalogAdminService.setRelated(id, request.relatedIds());
        return ApiResponse.ok(catalogAdminService.get(id));
    }

    @GetMapping("/catalog/{itemId}/questions")
    public ApiResponse<List<AdminQuestionResponse>> listQuestions(@PathVariable UUID itemId) {
        return ApiResponse.ok(questionAdminService.list(itemId));
    }

    @GetMapping("/catalog/{itemId}/questions/{questionId}")
    public ApiResponse<AdminQuestionResponse> getQuestion(
            @PathVariable UUID itemId,
            @PathVariable UUID questionId) {
        return ApiResponse.ok(questionAdminService.get(itemId, questionId));
    }

    @PostMapping("/catalog/{itemId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminQuestionResponse> createQuestion(
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateQuestionRequest request) {
        return ApiResponse.ok(questionAdminService.create(itemId, request));
    }

    @PutMapping("/catalog/{itemId}/questions/{questionId}")
    public ApiResponse<AdminQuestionResponse> updateQuestion(
            @PathVariable UUID itemId,
            @PathVariable UUID questionId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        return ApiResponse.ok(questionAdminService.update(itemId, questionId, request));
    }

    @DeleteMapping("/catalog/{itemId}/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable UUID itemId, @PathVariable UUID questionId) {
        questionAdminService.delete(itemId, questionId);
    }

    @PutMapping("/catalog/{itemId}/questions/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderQuestions(
            @PathVariable UUID itemId,
            @Valid @RequestBody ReorderQuestionsRequest request) {
        questionAdminService.reorder(itemId, request);
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaUploadResponse> uploadMedia(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(new MediaUploadResponse(mediaService.uploadQuestionImage(file)));
    }

    @GetMapping("/purchases")
    public ApiResponse<AdminPurchasePageResponse> listPurchases(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID catalogItemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(purchaseAdminService.listAll(userId, catalogItemId, status, code, page, size));
    }

    @GetMapping("/purchases/{id}")
    public ApiResponse<AdminPurchaseResponse> getPurchase(@PathVariable UUID id) {
        return ApiResponse.ok(purchaseAdminService.getDetail(id));
    }

    @PostMapping("/purchases/{id}/refund")
    public ApiResponse<AdminPurchaseResponse> refund(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RefundPurchaseRequest request) {
        UUID actorId = UUID.fromString(authentication.getName());
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(purchaseAdminService.refund(actorId, id, reason));
    }
}

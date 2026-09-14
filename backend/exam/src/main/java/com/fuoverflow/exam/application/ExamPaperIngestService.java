package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import com.fuoverflow.exam.support.ExamPaperFingerprint;
import com.fuoverflow.material.domain.UploadPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a validated {@link IngestPaper} into a published paper plus its FE questions or PE content.
 *
 * <p>Two rules drive the shape of this class. First, a redelivery must not duplicate: a paper is
 * identified by content fingerprint, falling back to exam code, and an existing paper is rebuilt in
 * place — published or not, because the sender is the source of truth and a correction has to be
 * able to land. Second, a partial build must not survive: every object key written during a run is
 * tracked, and a failure deletes them along with a paper this run created, so a retry starts from a
 * clean slate instead of accumulating orphans.
 */
@Service
public class ExamPaperIngestService {
    private static final Logger log = LoggerFactory.getLogger(ExamPaperIngestService.class);

    private final ExamSubjectRepository subjectRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamIngestStorage ingestStorage;
    private final ExamResourceFetcher resourceFetcher;
    private final ExamMediaService mediaService;
    private final ExamProperties examProperties;
    private final ObjectMapper objectMapper;

    public ExamPaperIngestService(
            ExamSubjectRepository subjectRepository,
            ExamPaperRepository paperRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamIngestStorage ingestStorage,
            ExamResourceFetcher resourceFetcher,
            ExamMediaService mediaService,
            ExamProperties examProperties,
            ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.paperRepository = paperRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.ingestStorage = ingestStorage;
        this.resourceFetcher = resourceFetcher;
        this.mediaService = mediaService;
        this.examProperties = examProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestOutcome ingest(IngestPaper paper, String ingestSource) {
        Instant now = Instant.now();
        ExamSubjectEntity subject = resolveOrCreateSubject(paper.subjectCode(), now);
        String fingerprint = ExamPaperFingerprint.of(paper);

        Optional<ExamPaperEntity> existing = paperRepository
                .findByFingerprintAndDeletedAtIsNull(fingerprint)
                .or(() -> paperRepository.findByExamCodeIgnoreCaseAndDeletedAtIsNull(paper.examCode()));

        boolean created = existing.isEmpty();
        ExamPaperEntity entity = existing.orElseGet(() -> ExamPaperEntity.draft(
                UUID.randomUUID(),
                subject.getId(),
                paper.paperType(),
                paper.examCode(),
                paper.term(),
                paper.retakeLabel(),
                paper.title(),
                paper.description(),
                paper.durationMinutes(),
                paper.totalMark(),
                paper.declaredQuestionCount(),
                fingerprint,
                ingestSource,
                paper.externalPaperId(),
                (int) paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                        subject.getId(), paper.paperType().dbValue()),
                paper.campus(),
                now));

        List<String> staleQuestionImageKeys = new ArrayList<>();
        List<String> staleQuestionBlurKeys = new ArrayList<>();
        List<String> stalePeImageKeys = new ArrayList<>();
        List<String> staleResourceKeys = new ArrayList<>();
        if (!created) {
            assertSameSubjectAndType(entity, subject, paper);
            applyMetadata(entity, paper, fingerprint, ingestSource, now);
            clearExistingContent(entity, now,
                    staleQuestionImageKeys, staleQuestionBlurKeys, stalePeImageKeys, staleResourceKeys);
        }

        List<String> storedImageKeys = new ArrayList<>();
        List<String> storedBlurKeys = new ArrayList<>();
        List<String> storedResourceKeys = new ArrayList<>();
        try {
            paperRepository.save(entity);
            if (paper.paperType() == ExamPaperType.FE) {
                buildFeQuestions(paper, entity, subject, now, storedImageKeys, storedBlurKeys);
            } else {
                buildPeContent(paper, entity, subject, now,
                        storedImageKeys, storedBlurKeys, storedResourceKeys);
            }
        } catch (RuntimeException e) {
            rollbackStoredObjects(storedImageKeys, storedBlurKeys, storedResourceKeys);
            if (created) {
                paperRepository.delete(entity);
            }
            throw e;
        }

        if (hasContent(paper)) {
            entity.publish(now);
            paperRepository.save(entity);
        }

        // Reached once the rebuild above has fully succeeded, but the delete must still wait for this
        // transaction to actually commit: a thrown exception rolls back and restores the old
        // question/item rows, and so does an optimistic-lock failure or a dropped connection at flush
        // time — none of which can restore object-store bytes already deleted. So the previous
        // delivery's media is scheduled here, last, and only removed once the commit that keeps the
        // new rows around has actually happened. A paper that is already live must never lose its
        // working images to a rebuild whose commit never lands.
        scheduleStaleMediaDeletion(
                staleQuestionImageKeys, staleQuestionBlurKeys, stalePeImageKeys, staleResourceKeys);

        return new IngestOutcome(entity.getId(), created ? Outcome.CREATED : Outcome.UPDATED);
    }

    // --- subject --------------------------------------------------------------

    /**
     * A code we have never seen becomes a subject titled with the code itself, and active: the paper
     * the delivery carries is published immediately, and an inactive subject would keep it out of
     * the public catalog anyway. An admin replaces the placeholder title when they get to it.
     */
    private ExamSubjectEntity resolveOrCreateSubject(String subjectCode, Instant now) {
        return subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(subjectCode)
                .orElseGet(() -> {
                    log.info("Creating active exam subject {} from webhook ingest", subjectCode);
                    return subjectRepository.save(ExamSubjectEntity.create(
                            UUID.randomUUID(), subjectCode, subjectCode, null, null, null, null,
                            examProperties.defaultFePreviewImageCountOrDefault(), true, 0, now));
                });
    }

    // --- rebuild --------------------------------------------------------------

    /**
     * A redelivery is matched by content fingerprint (or exam code) alone, so it never carries the
     * matched paper's id back to the sender for confirmation. If the incoming subject or paper type
     * disagrees with what is already on file, silently moving the paper would also leave its
     * {@code sortOrder} stale (computed against the old subject/type at creation) — and since the
     * exam code itself encodes both subject and type, a mismatch means the sender contradicted
     * itself between deliveries rather than that the paper genuinely moved. Reject instead of
     * migrating.
     */
    private static void assertSameSubjectAndType(
            ExamPaperEntity entity, ExamSubjectEntity subject, IngestPaper paper) {
        if (!entity.getSubjectId().equals(subject.getId())
                || !entity.getPaperType().equals(paper.paperType().dbValue())) {
            throw new BadRequestException("EXAM_PAPER_IDENTITY_MISMATCH",
                    "Mã đề \"" + paper.examCode() + "\" đã thuộc một môn học hoặc loại đề khác trong hệ "
                            + "thống; không thể ghi đè bằng dữ liệu môn học/loại đề khác.");
        }
    }

    private void applyMetadata(ExamPaperEntity entity, IngestPaper paper,
                               String fingerprint, String ingestSource, Instant now) {
        entity.setTitle(paper.title());
        entity.setTerm(paper.term());
        entity.setRetakeLabel(paper.retakeLabel());
        entity.setCampus(paper.campus());
        entity.setDescription(paper.description());
        entity.setDurationMinutes(paper.durationMinutes());
        entity.setTotalMark(paper.totalMark());
        entity.setDeclaredQuestionCount(paper.declaredQuestionCount());
        entity.setExternalPaperId(paper.externalPaperId());
        entity.setIngestSource(ingestSource);
        entity.setFingerprint(fingerprint);
        entity.setUpdatedAt(now);
    }

    /**
     * Soft-deletes what the previous delivery built so the rebuild below does not leave the old
     * questions in place next to the new ones, which would read as a duplicated paper to a member.
     *
     * <p>The object keys those rows point at are only collected here, not deleted: deleting them
     * now would be irreversible before we know the rebuild will succeed, and for an already
     * published paper that would mean permanently losing live images to a rebuild that later fails.
     * The caller deletes the collected keys once the rebuild is done.
     */
    private void clearExistingContent(ExamPaperEntity entity, Instant now,
                                      List<String> staleQuestionImageKeys, List<String> staleQuestionBlurKeys,
                                      List<String> stalePeImageKeys, List<String> staleResourceKeys) {
        for (ExamFeQuestionEntity question :
                feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(entity.getId())) {
            staleQuestionImageKeys.addAll(
                    ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls()));
            staleQuestionBlurKeys.addAll(
                    ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls()));
            question.setDeletedAt(now);
            question.setUpdatedAt(now);
            feQuestionRepository.save(question);
        }

        for (ExamPeItemEntity item :
                peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(entity.getId())) {
            stalePeImageKeys.addAll(ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls()));
            for (ExamPeResourceEntity resource :
                    peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId())) {
                staleResourceKeys.add(resource.getObjectKey());
                resource.setDeletedAt(now);
                peResourceRepository.save(resource);
            }
            item.setDeletedAt(now);
            item.setUpdatedAt(now);
            peItemRepository.save(item);
        }
    }

    // --- FE -------------------------------------------------------------------

    private void buildFeQuestions(IngestPaper paper, ExamPaperEntity entity, ExamSubjectEntity subject,
                                  Instant now, List<String> storedImageKeys, List<String> storedBlurKeys) {
        for (IngestQuestion question : paper.questions()) {
            List<String> imageKeys = new ArrayList<>();
            List<String> blurKeys = new ArrayList<>();
            for (IngestAsset image : question.images()) {
                ExamIngestStorage.StoredImage stored = ingestStorage.storeImage(
                        image.content(), image.mimeType(), UploadPurpose.EXAM_FE_IMAGE, true);
                imageKeys.add(stored.objectKey());
                blurKeys.add(stored.blurObjectKey());
                storedImageKeys.add(stored.objectKey());
                storedBlurKeys.add(stored.blurObjectKey());
            }

            feQuestionRepository.save(ExamFeQuestionEntity.create(
                    UUID.randomUUID(),
                    subject.getId(),
                    question.questionText(),
                    ExamJsonUtil.serialize(objectMapper, imageKeys),
                    ExamJsonUtil.serialize(objectMapper, blurKeys),
                    question.displayNo() - 1,
                    now,
                    entity.getId()));
        }
    }

    // --- PE -------------------------------------------------------------------

    private void buildPeContent(IngestPaper paper, ExamPaperEntity entity, ExamSubjectEntity subject,
                                Instant now, List<String> storedImageKeys, List<String> storedBlurKeys,
                                List<String> storedResourceKeys) {
        List<String> imageKeys = new ArrayList<>();
        for (IngestAsset image : paper.images()) {
            ExamIngestStorage.StoredImage stored = ingestStorage.storeImage(
                    image.content(), image.mimeType(), UploadPurpose.EXAM_PE_IMAGE, false);
            imageKeys.add(stored.objectKey());
            storedImageKeys.add(stored.objectKey());
            storedBlurKeys.add(null);
        }

        UUID itemId = UUID.randomUUID();
        ExamPeItemEntity item = ExamPeItemEntity.create(
                itemId,
                subject.getId(),
                paper.title(),
                paper.description(),
                ExamJsonUtil.serialize(objectMapper, imageKeys),
                0,
                now);
        item.setPaperId(entity.getId());
        peItemRepository.save(item);

        for (IngestResource resource : paper.resources()) {
            byte[] content = resourceFetcher.fetch(resource);
            String objectKey = ingestStorage.storeResource(
                    content, resource.mimeType(), resource.filename());
            storedResourceKeys.add(objectKey);

            peResourceRepository.save(ExamPeResourceEntity.create(
                    UUID.randomUUID(),
                    itemId,
                    resource.folderLabel(),
                    objectKey,
                    resource.filename(),
                    resource.mimeType(),
                    content.length,
                    resource.sortOrder(),
                    now));
        }
    }

    // --- cleanup --------------------------------------------------------------

    /**
     * The EXAM_PAPER_EMPTY rule {@code ExamPaperAdminService.publish} enforces, read off the payload
     * the content was just built from instead of a repository round trip. The validator already
     * rejects an empty paper, so this is a guard: a paper that somehow has nothing stays a draft and
     * shows up in the admin queue rather than going live blank.
     */
    private static boolean hasContent(IngestPaper paper) {
        return paper.paperType() == ExamPaperType.FE
                ? !paper.questions().isEmpty()
                : !paper.images().isEmpty() || !paper.resources().isEmpty();
    }

    private void rollbackStoredObjects(List<String> imageKeys, List<String> blurKeys,
                                       List<String> resourceKeys) {
        try {
            mediaService.deletePairedAll(imageKeys, blurKeys);
            mediaService.deleteStoredReferences(resourceKeys);
        } catch (RuntimeException cleanupFailure) {
            // Never mask the original failure with a cleanup problem; the orphan is logged instead.
            log.warn("Failed to clean up partially ingested objects: {}", cleanupFailure.getMessage());
        }
    }

    /**
     * Defers the stale-media delete to this transaction's {@code afterCommit}, so a rollback (thrown
     * exception, optimistic-lock failure, dropped connection) never runs it — see
     * {@link #clearExistingContent} for why the delete cannot happen any earlier than this either.
     * A caller with no active transaction (a direct unit-test invocation, or any future
     * non-transactional caller) has nothing to defer to, so it falls back to deleting inline.
     */
    private void scheduleStaleMediaDeletion(List<String> questionImageKeys, List<String> questionBlurKeys,
                                            List<String> peImageKeys, List<String> resourceKeys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStaleMedia(questionImageKeys, questionBlurKeys, peImageKeys, resourceKeys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStaleMedia(questionImageKeys, questionBlurKeys, peImageKeys, resourceKeys);
            }
        });
    }

    /**
     * Deletes the media a superseded delivery owned, called only after the rebuild has fully
     * succeeded — see {@link #clearExistingContent} for why this cannot happen any earlier.
     */
    private void deleteStaleMedia(List<String> questionImageKeys, List<String> questionBlurKeys,
                                  List<String> peImageKeys, List<String> resourceKeys) {
        try {
            mediaService.deletePairedAll(questionImageKeys, questionBlurKeys);
            mediaService.deleteStoredReferences(peImageKeys);
            mediaService.deleteStoredReferences(resourceKeys);
        } catch (RuntimeException cleanupFailure) {
            // Never mask a successful ingest with a cleanup problem; the orphan is logged instead.
            log.warn("Failed to clean up superseded exam media: {}", cleanupFailure.getMessage());
        }
    }

    public enum Outcome {
        CREATED, UPDATED
    }

    public record IngestOutcome(UUID paperId, Outcome outcome) {
    }
}

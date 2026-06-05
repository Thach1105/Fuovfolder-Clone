package com.fuoverflow.source.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.source.api.dto.CreateQuestionRequest;
import com.fuoverflow.source.api.dto.QuestionOptionRequest;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceQuestionEntity;
import com.fuoverflow.source.persistence.SourceQuestionOptionEntity;
import com.fuoverflow.source.persistence.SourceQuestionOptionRepository;
import com.fuoverflow.source.persistence.SourceQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceQuestionAdminServiceTest {
    @Mock
    private SourceQuestionRepository questionRepository;
    @Mock
    private SourceQuestionOptionRepository optionRepository;
    @Mock
    private SourceCatalogItemRepository catalogRepository;
    @Mock
    private SourceMediaService mediaService;
    @Mock
    private SourceMediaUrlResolver urlResolver;

    private SourceQuestionAdminService adminService;
    private UUID catalogId;

    @BeforeEach
    void setUp() {
        adminService = new SourceQuestionAdminService(
                questionRepository,
                optionRepository,
                catalogRepository,
                mediaService,
                urlResolver);
        catalogId = UUID.randomUUID();
        lenient().when(urlResolver.normalizeForStorage(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(urlResolver.resolveAdmin(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_syncsQuestionCountAndSetsMultipleCorrect() {
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalogItem()));
        when(questionRepository.countByCatalogItemIdAndDeletedAtIsNull(catalogId)).thenReturn(0L, 1L);
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(optionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findByIdAndCatalogItemIdAndDeletedAtIsNull(any(), any()))
                .thenAnswer(inv -> {
                    UUID questionId = inv.getArgument(0);
                    SourceQuestionEntity question = SourceQuestionEntity.create(
                            questionId,
                            catalogId,
                            "Q?",
                            null,
                            null,
                            true,
                            0,
                            Instant.now());
                    return Optional.of(question);
                });
        when(optionRepository.findByQuestionIdOrderBySortOrderAsc(any())).thenReturn(List.of(
                optionEntity("A", true),
                optionEntity("B", true),
                optionEntity("C", false)));

        var response = adminService.create(catalogId, new CreateQuestionRequest(
                "Q?",
                null,
                null,
                null,
                List.of(
                        new QuestionOptionRequest("A", null, true, 0),
                        new QuestionOptionRequest("B", null, true, 1),
                        new QuestionOptionRequest("C", null, false, 2))));

        assertTrue(response.multipleCorrect());
        ArgumentCaptor<SourceCatalogItemEntity> catalogCaptor = ArgumentCaptor.forClass(SourceCatalogItemEntity.class);
        verify(catalogRepository).save(catalogCaptor.capture());
        assertEquals(1, catalogCaptor.getValue().getQuestionCount());
    }

    @Test
    void create_rejectsFewerThanTwoOptions() {
        when(catalogRepository.findByIdAndDeletedAtIsNull(catalogId)).thenReturn(Optional.of(catalogItem()));

        assertThrows(BadRequestException.class, () -> adminService.create(catalogId, new CreateQuestionRequest(
                "Q?",
                null,
                null,
                null,
                List.of(new QuestionOptionRequest("A", null, true, 0)))));
    }

    private SourceCatalogItemEntity catalogItem() {
        return SourceCatalogItemEntity.create(
                catalogId,
                "MLN111",
                "Title",
                null,
                100,
                60,
                0,
                0,
                0,
                null,
                null,
                true,
                false,
                0,
                Instant.now());
    }

    private SourceQuestionOptionEntity optionEntity(String text, boolean correct) {
        return SourceQuestionOptionEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                text,
                null,
                correct,
                0,
                Instant.now());
    }
}

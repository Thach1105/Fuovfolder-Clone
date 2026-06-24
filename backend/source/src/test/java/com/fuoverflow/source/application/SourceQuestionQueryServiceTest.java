package com.fuoverflow.source.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceQuestionOptionRepository;
import com.fuoverflow.source.persistence.SourceQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceQuestionQueryServiceTest {
    @Mock
    private SourceQuestionRepository questionRepository;
    @Mock
    private SourceQuestionOptionRepository optionRepository;
    @Mock
    private SourceCatalogItemRepository catalogRepository;
    @Mock
    private SourceAccessGuard accessGuard;
    @Mock
    private SourceMediaUrlResolver urlResolver;
    @Mock
    private ObjectMapper objectMapper;

    private SourceQuestionQueryService queryService;
    private UUID userId;
    private UUID catalogId;

    @BeforeEach
    void setUp() {
        queryService = new SourceQuestionQueryService(
                questionRepository,
                optionRepository,
                catalogRepository,
                accessGuard,
                urlResolver,
                objectMapper);
        userId = UUID.randomUUID();
        catalogId = UUID.randomUUID();
    }

    @Test
    void listForUser_requiresActiveAccess() {
        when(catalogRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111"))
                .thenReturn(Optional.of(catalogItem()));
        doThrow(new ForbiddenException("NO_ACTIVE_ACCESS", "No access"))
                .when(accessGuard).requireActiveAccess(userId, catalogId);

        assertThrows(ForbiddenException.class, () -> queryService.listForUser("MLN111", userId));
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
                null,
                true,
                false,
                0,
                Instant.now());
    }
}

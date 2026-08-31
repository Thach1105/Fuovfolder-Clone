package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.PaperStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradingPaperEntityTest {

    private GradingPaperEntity newDraft() {
        return GradingPaperEntity.draft(
                "CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 50,
                60, new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    @Test
    void draftStartsInDraftStatusWithIdAndTimestamps() {
        GradingPaperEntity paper = newDraft();

        assertEquals(PaperStatus.DRAFT, paper.getStatus());
        assertNotNull(paper.getId());
        assertNotNull(paper.getCreatedAt());
        assertNotNull(paper.getUpdatedAt());
        assertNull(paper.getPublishedAt());
        assertNull(paper.getDeletedAt());
    }

    @Test
    void markReadySetsStatusAndPublishedAt() {
        GradingPaperEntity paper = newDraft();

        paper.markReady();

        assertEquals(PaperStatus.READY, paper.getStatus());
        assertNotNull(paper.getPublishedAt());
    }

    @Test
    void softDeleteStampsDeletedAt() {
        GradingPaperEntity paper = newDraft();

        paper.softDelete();

        assertNotNull(paper.getDeletedAt());
    }
}

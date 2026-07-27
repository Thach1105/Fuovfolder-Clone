package com.fuoverflow.broadcast.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementEntityTest {

    @Test
    void create_setsAllFields() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        UUID adminId = UUID.randomUUID();

        AnnouncementEntity entity = AnnouncementEntity.create(
                "Test Title", "<b>Hello</b>", "#ff0000",
                "https://example.com", "Click", 10, 60, 300,
                "SCHEDULED", start, end, adminId);

        assertEquals("Test Title", entity.getTitle());
        assertEquals("<b>Hello</b>", entity.getContentHtml());
        assertEquals("#ff0000", entity.getBackgroundColor());
        assertEquals("https://example.com", entity.getLinkUrl());
        assertEquals("Click", entity.getLinkLabel());
        assertEquals(10, entity.getPriority());
        assertEquals(60, entity.getScrollSpeed());
        assertEquals(300, entity.getStepSeconds());
        assertEquals("SCHEDULED", entity.getStatus());
        assertEquals(start, entity.getStartAt());
        assertEquals(end, entity.getEndAt());
        assertEquals(adminId, entity.getCreatedBy());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void updateContent_changesFieldsAndTimestamp() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Old</b>", "#000000",
                null, null, 0, 50, 300,
                "ACTIVE", start, end, UUID.randomUUID());

        Instant beforeUpdate = entity.getUpdatedAt();
        entity.updateContent("<b>New</b>", "#ffffff", "https://new.com", "New Link", 5, 80, 600);

        assertEquals("<b>New</b>", entity.getContentHtml());
        assertEquals("#ffffff", entity.getBackgroundColor());
        assertEquals("https://new.com", entity.getLinkUrl());
        assertEquals("New Link", entity.getLinkLabel());
        assertEquals(5, entity.getPriority());
        assertEquals(80, entity.getScrollSpeed());
        assertEquals(600, entity.getStepSeconds());
        assertTrue(entity.getUpdatedAt().compareTo(beforeUpdate) >= 0);
    }

    @Test
    void updateStatus_changesStatusAndTimestamp() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000000",
                null, null, 0, 50, 300,
                "SCHEDULED", start, end, UUID.randomUUID());

        entity.updateStatus("ACTIVE");
        assertEquals("ACTIVE", entity.getStatus());
    }
}

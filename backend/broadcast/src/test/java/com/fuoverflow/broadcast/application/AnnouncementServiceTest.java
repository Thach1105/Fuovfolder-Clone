package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {

    private AnnouncementRepository repo;
    private BroadcastPublisher publisher;
    private AnnouncementService service;
    private final UUID adminId = UUID.randomUUID();
    private final Instant futureStart = Instant.now().plus(1, ChronoUnit.HOURS);
    private final Instant futureEnd = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        repo = mock(AnnouncementRepository.class);
        publisher = mock(BroadcastPublisher.class);
        service = new AnnouncementService(repo, publisher, new ObjectMapper());
    }

    private AnnouncementRequest validRequest() {
        return new AnnouncementRequest(
                "Test", "<b>Hello</b>", "#ff0000",
                "https://example.com", "Click", 10, 50, 300,
                futureStart, futureEnd);
    }

    @Test
    void create_scheduled_savesWithCorrectStatus() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        AnnouncementResponse result = service.create(validRequest(), "SCHEDULED", adminId);

        assertEquals("SCHEDULED", result.status());
        assertEquals("Test", result.title());
        verify(publisher, never()).publish(any());
    }

    @Test
    void create_active_publishesSync() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        service.create(validRequest(), "ACTIVE", adminId);

        verify(publisher).publish(any());
    }

    @Test
    void create_endBeforeStart_throws() {
        AnnouncementRequest bad = new AnnouncementRequest(
                "Test", "<b>Hello</b>", "#ff0000",
                null, null, 0, 50, 300,
                futureEnd, futureStart);

        assertThrows(BadRequestException.class, () -> service.create(bad, "DRAFT", adminId));
    }

    @Test
    void delete_activeStatus_throws() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));

        assertThrows(BadRequestException.class, () -> service.delete(UUID.randomUUID()));
    }

    @Test
    void delete_draftStatus_succeeds() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "DRAFT", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));

        service.delete(UUID.randomUUID());

        verify(repo).delete(entity);
    }

    @Test
    void activate_changesStatusAndPublishes() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "SCHEDULED", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of(entity));

        AnnouncementResponse result = service.activate(UUID.randomUUID());

        assertEquals("ACTIVE", result.status());
        verify(publisher).publish(any());
    }

    @Test
    void deactivate_changesStatusAndPublishes() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        AnnouncementResponse result = service.deactivate(UUID.randomUUID());

        assertEquals("EXPIRED", result.status());
        verify(publisher).publish(any());
    }

    @Test
    void getById_notFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }

    @Test
    void getActiveForUsers_mapsToActiveResponse() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#123456",
                "https://link.com", "Go", 5, 60, 120,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of(entity));

        List<AnnouncementActiveResponse> result = service.getActiveForUsers();

        assertEquals(1, result.size());
        AnnouncementActiveResponse r = result.getFirst();
        assertEquals("<b>Test</b>", r.contentHtml());
        assertEquals("#123456", r.backgroundColor());
        assertEquals("https://link.com", r.linkUrl());
        assertEquals("Go", r.linkLabel());
        assertEquals(5, r.priority());
        assertEquals(60, r.scrollSpeed());
        assertEquals(120, r.stepSeconds());
    }
}

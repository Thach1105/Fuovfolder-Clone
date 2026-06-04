package com.fuoverflow.coursera.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coursera_request_status_events")
public class CourseraRequestStatusEventEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CourseraRequestStatusEventEntity create(
            UUID id, UUID requestId, String fromStatus, String toStatus, UUID actorUserId, String note, Instant now) {
        CourseraRequestStatusEventEntity e = new CourseraRequestStatusEventEntity();
        e.id = id;
        e.requestId = requestId;
        e.fromStatus = fromStatus;
        e.toStatus = toStatus;
        e.actorUserId = actorUserId;
        e.note = note;
        e.createdAt = now;
        return e;
    }
}

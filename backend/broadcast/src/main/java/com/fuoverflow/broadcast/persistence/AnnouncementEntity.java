package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
public class AnnouncementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(name = "background_color", nullable = false, length = 9)
    private String backgroundColor;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "link_label", length = 100)
    private String linkLabel;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "scroll_speed", nullable = false)
    private int scrollSpeed;

    @Column(name = "step_seconds", nullable = false)
    private int stepSeconds;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnnouncementEntity() {}

    public static AnnouncementEntity create(
            String title, String contentHtml, String backgroundColor,
            String linkUrl, String linkLabel, int priority, int scrollSpeed, int stepSeconds,
            String status, Instant startAt, Instant endAt, UUID createdBy) {
        AnnouncementEntity e = new AnnouncementEntity();
        e.title = title;
        e.contentHtml = contentHtml;
        e.backgroundColor = backgroundColor;
        e.linkUrl = linkUrl;
        e.linkLabel = linkLabel;
        e.priority = priority;
        e.scrollSpeed = scrollSpeed;
        e.stepSeconds = stepSeconds;
        e.status = status;
        e.startAt = startAt;
        e.endAt = endAt;
        e.createdBy = createdBy;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void updateContent(String contentHtml, String backgroundColor,
                              String linkUrl, String linkLabel,
                              int priority, int scrollSpeed, int stepSeconds) {
        this.contentHtml = contentHtml;
        this.backgroundColor = backgroundColor;
        this.linkUrl = linkUrl;
        this.linkLabel = linkLabel;
        this.priority = priority;
        this.scrollSpeed = scrollSpeed;
        this.stepSeconds = stepSeconds;
        this.updatedAt = Instant.now();
    }

    public void updateFull(String title, String contentHtml, String backgroundColor,
                           String linkUrl, String linkLabel,
                           int priority, int scrollSpeed, int stepSeconds,
                           Instant startAt, Instant endAt) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.backgroundColor = backgroundColor;
        this.linkUrl = linkUrl;
        this.linkLabel = linkLabel;
        this.priority = priority;
        this.scrollSpeed = scrollSpeed;
        this.stepSeconds = stepSeconds;
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getContentHtml() { return contentHtml; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getLinkUrl() { return linkUrl; }
    public String getLinkLabel() { return linkLabel; }
    public int getPriority() { return priority; }
    public int getScrollSpeed() { return scrollSpeed; }
    public int getStepSeconds() { return stepSeconds; }
    public String getStatus() { return status; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

CREATE TABLE announcements (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title           varchar(200)  NOT NULL,
    content_html    text          NOT NULL,
    background_color varchar(9)   NOT NULL DEFAULT '#1e40af',
    link_url        varchar(2048),
    link_label      varchar(100),
    priority        int           NOT NULL DEFAULT 0,
    scroll_speed    int           NOT NULL DEFAULT 50,
    step_seconds    int           NOT NULL DEFAULT 300,
    status          varchar(16)   NOT NULL DEFAULT 'DRAFT',
    start_at        timestamptz   NOT NULL,
    end_at          timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_announcements_status_priority
    ON announcements(status, priority DESC);

CREATE INDEX ix_announcements_start_end
    ON announcements(start_at, end_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'EXPIRED'));

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_end_after_start
    CHECK (end_at > start_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_step_positive
    CHECK (step_seconds > 0);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_scroll_speed_positive
    CHECK (scroll_speed > 0);

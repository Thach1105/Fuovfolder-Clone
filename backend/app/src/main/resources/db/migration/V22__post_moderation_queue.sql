-- Post moderation queue: add pending status for pre-approval workflow.

alter table posts drop constraint if exists posts_status_check;
alter table posts add constraint posts_status_check
    check (status in ('visible', 'hidden', 'deleted', 'flagged', 'pending'));

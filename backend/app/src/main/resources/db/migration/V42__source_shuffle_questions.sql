-- Per-source configurable question shuffling. Admin toggles whether the quiz runner
-- randomizes question order for a given source. Default true preserves prior behavior.

alter table source_catalog_items
    add column shuffle_questions boolean not null default true;

-- Align membership_plans.currency with JPA String mapping (varchar), not PostgreSQL char(3).

alter table membership_plans
    alter column currency type varchar(3) using trim(currency);

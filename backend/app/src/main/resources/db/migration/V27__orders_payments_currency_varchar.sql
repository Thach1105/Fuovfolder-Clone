-- Align orders.currency and payments.currency with JPA String mapping (varchar), not PostgreSQL char(3).
-- Mirrors V18__membership_plans_currency_varchar.sql for the two remaining payment tables.

alter table orders
    alter column currency type varchar(3) using trim(currency);

alter table payments
    alter column currency type varchar(3) using trim(currency);

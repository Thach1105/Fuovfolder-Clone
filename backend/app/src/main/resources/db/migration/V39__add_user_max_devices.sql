-- V39__add_user_max_devices.sql
ALTER TABLE users ADD COLUMN max_devices smallint DEFAULT NULL;
COMMENT ON COLUMN users.max_devices IS 'Per-user device limit. NULL=global default, 0=unlimited, >0=custom limit';

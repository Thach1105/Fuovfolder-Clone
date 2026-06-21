-- =================================================================
-- V26__add_points_system.sql
-- Points system + payments status extension cho PayOS integration
-- Flyway sẽ chạy file này sau V25 (checksum_varchar)
-- =================================================================

-- Bảng số dư điểm của user
CREATE TABLE IF NOT EXISTS point_balances (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    balance_points bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_point_balances_user ON point_balances(user_id);
CREATE INDEX IF NOT EXISTS ix_point_balances_user_updated ON point_balances(user_id, updated_at desc);

-- Bảng lịch sử giao dịch điểm
CREATE TABLE IF NOT EXISTS point_transactions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    amount_points bigint NOT NULL,
    direction varchar(16) NOT NULL,
    type varchar(64) NOT NULL,
    reference_type varchar(64) NULL,
    reference_id uuid NULL,
    payment_id uuid NULL,
    description text NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT point_transactions_direction_check CHECK (direction IN ('credit','debit'))
);

CREATE INDEX IF NOT EXISTS ix_point_transactions_user_created ON point_transactions(user_id, created_at desc);
CREATE INDEX IF NOT EXISTS ix_point_transactions_payment ON point_transactions(payment_id) WHERE payment_id IS NOT NULL;

-- Mở rộng payments status: thêm 'succeeded' (PayOS dùng status này)
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('pending','paid','failed','refunded','succeeded'));

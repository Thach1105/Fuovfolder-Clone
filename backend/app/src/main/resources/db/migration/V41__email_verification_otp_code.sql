-- Add OTP code verification support to email_verification_tokens
ALTER TABLE email_verification_tokens
    ADD COLUMN IF NOT EXISTS verification_code_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 5;

CREATE INDEX IF NOT EXISTS ix_email_verification_tokens_user_code
    ON email_verification_tokens (user_id, verification_code_hash)
    WHERE consumed_at IS NULL;

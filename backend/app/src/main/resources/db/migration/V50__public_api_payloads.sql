CREATE TABLE public_api_payloads (
    id uuid PRIMARY KEY,
    payload_json jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_public_api_payloads_created_at
    ON public_api_payloads (created_at DESC);

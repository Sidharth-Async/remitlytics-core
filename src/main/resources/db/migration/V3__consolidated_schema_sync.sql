-- 1. Ensure ledger_accounts matches LedgerAccount entity
ALTER TABLE ledger_accounts ADD COLUMN IF NOT EXISTS type VARCHAR(50) NOT NULL DEFAULT 'ASSET';

-- 2. Ensure ledger_entries matches LedgerEntry entity
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'entry_type'
    ) THEN
ALTER TABLE ledger_entries RENAME COLUMN entry_type TO direction;
ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'direction'
    ) THEN
ALTER TABLE ledger_entries ADD COLUMN direction VARCHAR(20) NOT NULL DEFAULT 'DEBIT';
END IF;
END $$;

-- 3. Ensure webhook_delivery_logs matches WebhookDeliveryLog entity
CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    target_url VARCHAR(500) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    http_status INT,
    response_body TEXT,
    error_message TEXT,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_webhook_logs_status_retry
    ON webhook_delivery_logs (status, next_retry_at);
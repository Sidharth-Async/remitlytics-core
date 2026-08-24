-- Ledger Accounts
CREATE TABLE IF NOT EXISTS ledger_accounts (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                                       );

-- Ledger Transactions
CREATE TABLE IF NOT EXISTS ledger_transactions (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                                       );

-- Ledger Entries
CREATE TABLE IF NOT EXISTS ledger_entries (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES ledger_accounts(id) ON DELETE CASCADE,
    direction VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                                                                        );

-- Webhook Delivery Logs
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
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                             );

CREATE INDEX IF NOT EXISTS idx_webhook_logs_status_retry
    ON webhook_delivery_logs (status, next_retry_at);
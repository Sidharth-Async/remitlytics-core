ALTER TABLE webhook_delivery_logs ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE organizations ADD COLUMN stripe_account_id VARCHAR(255);

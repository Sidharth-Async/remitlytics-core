ALTER TABLE tenants ADD COLUMN webhook_url VARCHAR(255);
ALTER TABLE webhook_delivery_logs ADD COLUMN last_error_message TEXT;

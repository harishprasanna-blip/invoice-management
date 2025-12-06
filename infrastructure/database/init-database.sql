-- Invoice Management System - Database Initialization
-- PostgreSQL 15+ with JSONB support
-- Multi-tenant architecture with schema-per-tenant isolation

-- ============================================================================
-- COMMON SCHEMA (Shared across all tenants)
-- Contains: event_store, tenant_registry, event_subscriptions
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS common;

-- Event Store Table (Transactional Outbox Pattern)
CREATE TABLE IF NOT EXISTS common.event_store (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    event_payload JSONB NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    published_by VARCHAR(100),

    -- Indexes for performance
    CONSTRAINT event_store_pkey PRIMARY KEY (event_id)
);

CREATE INDEX idx_event_tenant_aggregate ON common.event_store (tenant_id, aggregate_id);
CREATE INDEX idx_event_type ON common.event_store (event_type);
CREATE INDEX idx_event_created ON common.event_store (created_at DESC);
CREATE INDEX idx_event_unpublished ON common.event_store (published_at) WHERE published_at IS NULL;
CREATE INDEX idx_event_payload_gin ON common.event_store USING GIN (event_payload);

-- Partition event_store by month for performance (PostgreSQL 10+)
-- This is a template for future partitioning
-- ALTER TABLE common.event_store PARTITION BY RANGE (created_at);

COMMENT ON TABLE common.event_store IS 'Stores all domain events for event-driven architecture and audit trail';
COMMENT ON COLUMN common.event_store.published_at IS 'NULL indicates unpublished event (awaiting background worker)';

-- Event Subscriptions Table (Tracks which contexts have processed which events)
CREATE TABLE IF NOT EXISTS common.event_subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    last_processed_event_id UUID,
    last_processed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    error_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE(subscriber_name, event_type)
);

CREATE INDEX idx_subscription_name ON common.event_subscriptions (subscriber_name);
CREATE INDEX idx_subscription_status ON common.event_subscriptions (status);

COMMENT ON TABLE common.event_subscriptions IS 'Tracks event subscription state for each bounded context';

-- Event Processing Log (Idempotency and retry tracking)
CREATE TABLE IF NOT EXISTS common.event_processing_log (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES common.event_store(event_id),
    subscriber_name VARCHAR(100) NOT NULL,
    processing_status VARCHAR(50) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,

    CONSTRAINT fk_event_id FOREIGN KEY (event_id) REFERENCES common.event_store(event_id)
);

CREATE INDEX idx_processing_event_subscriber ON common.event_processing_log (event_id, subscriber_name);
CREATE INDEX idx_processing_status ON common.event_processing_log (processing_status);

COMMENT ON TABLE common.event_processing_log IS 'Logs event processing attempts for debugging and idempotency';

-- Tenant Registry Table (Multi-tenancy management)
CREATE TABLE IF NOT EXISTS common.tenant_registry (
    tenant_id UUID PRIMARY KEY,
    tenant_name VARCHAR(200) NOT NULL,
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    region VARCHAR(50) NOT NULL, -- US, EU for GDPR compliance
    subscription_tier VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);

CREATE INDEX idx_tenant_status ON common.tenant_registry (status);
CREATE INDEX idx_tenant_region ON common.tenant_registry (region);

COMMENT ON TABLE common.tenant_registry IS 'Registry of all tenants for multi-tenant isolation and management';

-- Tenant Configuration Table (Tenant-specific settings)
CREATE TABLE IF NOT EXISTS common.tenant_configuration (
    config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES common.tenant_registry(tenant_id),
    config_key VARCHAR(100) NOT NULL,
    config_value JSONB NOT NULL,
    config_type VARCHAR(50) NOT NULL, -- VALIDATION, APPROVAL, SAP_CONNECTION, PAYMENT, etc.
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE(tenant_id, config_key)
);

CREATE INDEX idx_config_tenant ON common.tenant_configuration (tenant_id);
CREATE INDEX idx_config_type ON common.tenant_configuration (config_type);

COMMENT ON TABLE common.tenant_configuration IS 'Tenant-specific configuration for validation rules, SAP credentials, etc.';

-- Audit Trail Table (Separate from aggregates for long-term retention)
CREATE TABLE IF NOT EXISTS common.audit_trail (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id UUID,
    user_role VARCHAR(100),
    ip_address VARCHAR(50),
    changes JSONB,
    correlation_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_action CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'APPROVED', 'REJECTED',
                                             'EXTRACTED', 'VALIDATED', 'POSTED', 'ESCALATED', 'RESOLVED', 'EXECUTED'))
);

CREATE INDEX idx_audit_tenant_aggregate ON common.audit_trail (tenant_id, aggregate_id);
CREATE INDEX idx_audit_occurred ON common.audit_trail (occurred_at DESC);
CREATE INDEX idx_audit_user ON common.audit_trail (user_id);
CREATE INDEX idx_audit_correlation ON common.audit_trail (correlation_id);

-- Partition by month for long-term retention
-- ALTER TABLE common.audit_trail PARTITION BY RANGE (occurred_at);

COMMENT ON TABLE common.audit_trail IS 'Complete audit trail for SOC2 compliance (10-year retention)';

-- Payment Runs Table (Payment Orchestration Context)
CREATE TABLE IF NOT EXISTS common.payment_runs (
    payment_run_id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    invoice_id UUID NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    total_amount_value NUMERIC(15,2) NOT NULL,
    total_amount_currency VARCHAR(3) NOT NULL,
    vendor_id VARCHAR(100) NOT NULL,
    vendor_bank_account VARCHAR(100) NOT NULL,
    due_date DATE NOT NULL,
    scheduled_payment_date DATE NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    saga_state VARCHAR(50) NOT NULL,
    completed_steps JSONB,
    compensation_actions JSONB,
    sap_document_number VARCHAR(50),
    sap_posting_result TEXT,
    gateway_transaction_id VARCHAR(100),
    gateway_response TEXT,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    approval_notes TEXT,
    executed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    audit_log JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payment_method CHECK (payment_method IN ('ACH', 'WIRE', 'CHECK', 'VIRTUAL_CARD')),
    CONSTRAINT chk_payment_status CHECK (payment_status IN ('SCHEDULED', 'APPROVED', 'EXECUTED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_saga_state CHECK (saga_state IN ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'COMPENSATING', 'COMPENSATED'))
);

CREATE INDEX idx_payment_tenant ON common.payment_runs (tenant_id);
CREATE INDEX idx_payment_invoice ON common.payment_runs (invoice_id);
CREATE INDEX idx_payment_status ON common.payment_runs (payment_status);
CREATE INDEX idx_payment_scheduled_date ON common.payment_runs (scheduled_payment_date);
CREATE INDEX idx_payment_due_date ON common.payment_runs (due_date);
CREATE INDEX idx_payment_saga_state ON common.payment_runs (saga_state);

COMMENT ON TABLE common.payment_runs IS 'Payment runs with Saga orchestration for distributed payment processing';

-- Trigger for payment_runs
CREATE TRIGGER trg_payment_runs_updated_at
    BEFORE UPDATE ON common.payment_runs
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- Payment Runs Table (PaymentOrchestrationContext)
CREATE TABLE IF NOT EXISTS common.payment_runs (
    payment_run_id UUID PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    invoice_id UUID NOT NULL,
    invoice_number VARCHAR(100) NOT NULL,
    total_amount_value DECIMAL(15, 2) NOT NULL,
    total_amount_currency VARCHAR(3) NOT NULL,
    vendor_id VARCHAR(100) NOT NULL,
    vendor_bank_account VARCHAR(100) NOT NULL,
    due_date DATE NOT NULL,
    scheduled_payment_date DATE NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    saga_state VARCHAR(50) NOT NULL,
    completed_steps JSONB,
    compensation_actions JSONB,
    sap_document_number VARCHAR(50),
    sap_posting_result TEXT,
    gateway_transaction_id VARCHAR(100),
    gateway_response TEXT,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    approval_notes TEXT,
    executed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    audit_log JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_runs_tenant ON common.payment_runs (tenant_id);
CREATE INDEX idx_payment_runs_invoice ON common.payment_runs (invoice_id);
CREATE INDEX idx_payment_runs_status ON common.payment_runs (payment_status);
CREATE INDEX idx_payment_runs_scheduled_date ON common.payment_runs (scheduled_payment_date);
CREATE INDEX idx_payment_runs_due_date ON common.payment_runs (due_date);
CREATE INDEX idx_payment_runs_saga_state ON common.payment_runs (saga_state);
CREATE INDEX idx_payment_runs_steps_gin ON common.payment_runs USING GIN (completed_steps);

COMMENT ON TABLE common.payment_runs IS 'Payment orchestration with Saga pattern for distributed transactions';
COMMENT ON COLUMN common.payment_runs.saga_state IS 'STARTED, IN_PROGRESS, COMPLETED, COMPENSATING, COMPENSATED';
COMMENT ON COLUMN common.payment_runs.completed_steps IS 'Array of completed saga steps with status';
COMMENT ON COLUMN common.payment_runs.compensation_actions IS 'Array of compensating actions to execute on failure';

-- Trigger for payment_runs
CREATE TRIGGER trg_payment_runs_updated_at
    BEFORE UPDATE ON common.payment_runs
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- ============================================================================
-- FUNCTIONS AND TRIGGERS
-- ============================================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION common.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for tenant_registry
CREATE TRIGGER trg_tenant_registry_updated_at
    BEFORE UPDATE ON common.tenant_registry
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- Trigger for tenant_configuration
CREATE TRIGGER trg_tenant_configuration_updated_at
    BEFORE UPDATE ON common.tenant_configuration
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- Trigger for event_subscriptions
CREATE TRIGGER trg_event_subscriptions_updated_at
    BEFORE UPDATE ON common.event_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- ============================================================================
-- DEFAULT DATA
-- ============================================================================

-- Insert default event subscriptions for bounded contexts
INSERT INTO common.event_subscriptions (subscriber_name, event_type, status) VALUES
    ('ValidationContext', 'InvoiceExtracted', 'ACTIVE'),
    ('ExceptionHandlingContext', 'ExtractionFailed', 'ACTIVE'),
    ('ExceptionHandlingContext', 'MismatchDetected', 'ACTIVE'),
    ('ExceptionHandlingContext', 'ComplianceViolationDetected', 'ACTIVE'),
    ('PaymentOrchestrationContext', 'ValidationPassed', 'ACTIVE'),
    ('ExceptionHandlingContext', 'PaymentFailed', 'ACTIVE')
ON CONFLICT (subscriber_name, event_type) DO NOTHING;

-- ============================================================================
-- GRANT PERMISSIONS (Adjust based on your DB user)
-- ============================================================================

-- Grant usage on common schema
-- GRANT USAGE ON SCHEMA common TO invoice_app_user;
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA common TO invoice_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA common TO invoice_app_user;

COMMENT ON SCHEMA common IS 'Shared schema for event store, tenant registry, and cross-cutting concerns';

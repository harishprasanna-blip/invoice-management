-- Tenant Schema Template
-- This script creates a complete schema for a single tenant
-- Usage: Replace {TENANT_ID} with actual tenant UUID (without hyphens)
--        Example: tenant_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6

-- Variable substitution (for scripting)
-- SET tenant_schema = 'tenant_{TENANT_ID}';

CREATE SCHEMA IF NOT EXISTS tenant_{TENANT_ID};

-- ============================================================================
-- INVOICE INGESTION CONTEXT
-- ============================================================================

-- Invoices Table (Aggregate Root)
CREATE TABLE IF NOT EXISTS tenant_{TENANT_ID}.invoices (
    invoice_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vendor_reference JSONB NOT NULL, -- {vendorId, vendorName, taxId}
    invoice_metadata JSONB NOT NULL, -- {invoiceNumber, invoiceDate, dueDate, currency, totalAmount}
    line_items JSONB NOT NULL, -- [{lineNumber, description, quantity, unitPrice, taxAmount, totalAmount}, ...]
    document_reference JSONB NOT NULL, -- {s3BucketKey, originalFileName, documentFormat, uploadTimestamp}
    extraction_result JSONB, -- {extractionId, confidenceScore, extractedFields, validationErrors, retryCount, extractedAt}
    ingestion_status VARCHAR(50) NOT NULL,
    audit_log JSONB NOT NULL DEFAULT '{"entries": []}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 1, -- Optimistic locking

    CONSTRAINT chk_ingestion_status CHECK (ingestion_status IN ('RECEIVED', 'EXTRACTING', 'EXTRACTED', 'EXTRACTION_FAILED'))
);

CREATE INDEX idx_invoice_number ON tenant_{TENANT_ID}.invoices ((invoice_metadata->>'invoiceNumber'));
CREATE INDEX idx_invoice_vendor ON tenant_{TENANT_ID}.invoices ((vendor_reference->>'vendorId'));
CREATE INDEX idx_invoice_status ON tenant_{TENANT_ID}.invoices (ingestion_status);
CREATE INDEX idx_invoice_created ON tenant_{TENANT_ID}.invoices (created_at DESC);
CREATE INDEX idx_invoice_metadata_gin ON tenant_{TENANT_ID}.invoices USING GIN (invoice_metadata);

COMMENT ON TABLE tenant_{TENANT_ID}.invoices IS 'Invoice aggregates with AI extraction results (InvoiceIngestionContext)';

-- ============================================================================
-- VALIDATION CONTEXT
-- ============================================================================

-- Payable Transactions Table (Aggregate Root)
CREATE TABLE IF NOT EXISTS tenant_{TENANT_ID}.payable_transactions (
    transaction_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invoice_reference JSONB NOT NULL, -- {invoiceId, invoiceNumber, invoiceAmount}
    purchase_order_reference JSONB, -- {poNumber, poLineItems, poAmount, poStatus}
    goods_receipt_reference JSONB, -- {grNumber, grLineItems, receivedQuantities, receivedDate}
    matching_result JSONB, -- {matchType, matchStatus, lineItemMatches, variances, overallScore, matchedAt}
    compliance_checks JSONB NOT NULL DEFAULT '[]', -- [{checkType, checkStatus, details, regulation, checkedAt}, ...]
    approval_workflow JSONB, -- {approvalId, requiredApprovers, actualApprovals, approvalStatus}
    validation_status VARCHAR(50) NOT NULL,
    audit_log JSONB NOT NULL DEFAULT '{"entries": []}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT chk_validation_status CHECK (validation_status IN ('PENDING', 'IN_PROGRESS', 'VALIDATED', 'VALIDATION_FAILED', 'COMPLIANCE_VIOLATION'))
);

CREATE INDEX idx_transaction_invoice ON tenant_{TENANT_ID}.payable_transactions ((invoice_reference->>'invoiceId'));
CREATE INDEX idx_transaction_status ON tenant_{TENANT_ID}.payable_transactions (validation_status);
CREATE INDEX idx_transaction_created ON tenant_{TENANT_ID}.payable_transactions (created_at DESC);

COMMENT ON TABLE tenant_{TENANT_ID}.payable_transactions IS 'Payable transactions with 2/3-way matching and compliance validation (ValidationContext)';

-- ============================================================================
-- EXCEPTION HANDLING CONTEXT
-- ============================================================================

-- Exception Cases Table (Aggregate Root)
CREATE TABLE IF NOT EXISTS tenant_{TENANT_ID}.exception_cases (
    case_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    source_context VARCHAR(50) NOT NULL,
    source_reference JSONB NOT NULL, -- {sourceEntityId, sourceEventType, sourceEventPayload}
    exception_type JSONB NOT NULL, -- {category, severity, subType}
    exception_details JSONB NOT NULL,
    resolution_strategy JSONB, -- {strategyType, mlRecommendation, selectedStrategy, selectionReason}
    resolution_attempts JSONB NOT NULL DEFAULT '[]', -- [{attemptNumber, attemptType, action, outcome, performedBy, attemptedAt, notes}, ...]
    escalation_path JSONB, -- {escalationLevel, assignedTo, assignedRole, escalatedAt, slaDeadline, escalationReason}
    case_status VARCHAR(50) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT chk_case_status CHECK (case_status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CLOSED_REJECTED')),
    CONSTRAINT chk_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_source_context CHECK (source_context IN ('INGESTION', 'VALIDATION', 'PAYMENT'))
);

CREATE INDEX idx_case_status ON tenant_{TENANT_ID}.exception_cases (case_status);
CREATE INDEX idx_case_priority ON tenant_{TENANT_ID}.exception_cases (priority);
CREATE INDEX idx_case_source_entity ON tenant_{TENANT_ID}.exception_cases ((source_reference->>'sourceEntityId'));
CREATE INDEX idx_case_escalated ON tenant_{TENANT_ID}.exception_cases (case_status, (escalation_path->>'assignedTo')) WHERE case_status = 'ESCALATED';
CREATE INDEX idx_case_created ON tenant_{TENANT_ID}.exception_cases (created_at DESC);

COMMENT ON TABLE tenant_{TENANT_ID}.exception_cases IS 'Exception cases with ML-driven resolution and escalation (ExceptionHandlingContext)';

-- ============================================================================
-- PAYMENT ORCHESTRATION CONTEXT
-- ============================================================================

-- Payment Runs Table (Aggregate Root)
CREATE TABLE IF NOT EXISTS tenant_{TENANT_ID}.payment_runs (
    run_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    run_configuration JSONB NOT NULL, -- {paymentDate, cutoffDate, vendorFilter, amountRange, applyEarlyPaymentDiscounts, paymentMethod}
    run_status VARCHAR(50) NOT NULL,
    saga_orchestration JSONB, -- {sagaId, currentPhase, compensatingActions}
    execution_metrics JSONB, -- {totalPayments, successfulPayments, failedPayments, totalAmount, totalDiscountsCaptured, touchlessPercentage, executionDuration}
    approval_metadata JSONB, -- {approvedBy, approvedAt, approvalComments}
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT chk_run_status CHECK (run_status IN ('DRAFT', 'APPROVED', 'EXECUTING', 'COMPLETED', 'PARTIALLY_FAILED', 'FAILED'))
);

CREATE INDEX idx_payment_run_status ON tenant_{TENANT_ID}.payment_runs (run_status);
CREATE INDEX idx_payment_run_date ON tenant_{TENANT_ID}.payment_runs ((run_configuration->>'paymentDate'));
CREATE INDEX idx_payment_run_created ON tenant_{TENANT_ID}.payment_runs (created_at DESC);

COMMENT ON TABLE tenant_{TENANT_ID}.payment_runs IS 'Payment run aggregates with saga orchestration (PaymentOrchestrationContext)';

-- Payments Table (Entity within PaymentRun aggregate)
CREATE TABLE IF NOT EXISTS tenant_{TENANT_ID}.payments (
    payment_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    transaction_id UUID NOT NULL, -- Link to payable_transactions
    invoice_reference JSONB NOT NULL,
    vendor_reference JSONB NOT NULL,
    payment_amount JSONB NOT NULL, -- {amount, currency}
    discount_applied JSONB, -- {discountTerms, discountAmount, discountPercentage, eligibleUntil}
    payment_status VARCHAR(50) NOT NULL,
    erp_posting_reference JSONB, -- {documentNumber, fiscalYear, companyCode, postedAt}
    payment_saga JSONB NOT NULL, -- {sagaId, sagaState, sagaSteps, compensationRequired, lastUpdatedAt}
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL DEFAULT 1,

    CONSTRAINT fk_payment_run FOREIGN KEY (run_id) REFERENCES tenant_{TENANT_ID}.payment_runs(run_id),
    CONSTRAINT chk_payment_status CHECK (payment_status IN ('SCHEDULED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_payment_run ON tenant_{TENANT_ID}.payments (run_id);
CREATE INDEX idx_payment_transaction ON tenant_{TENANT_ID}.payments (transaction_id);
CREATE INDEX idx_payment_status ON tenant_{TENANT_ID}.payments (payment_status);

COMMENT ON TABLE tenant_{TENANT_ID}.payments IS 'Individual payments with saga state machine (PaymentOrchestrationContext)';

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Update updated_at timestamps
CREATE TRIGGER trg_invoices_updated_at
    BEFORE UPDATE ON tenant_{TENANT_ID}.invoices
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON tenant_{TENANT_ID}.payable_transactions
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

CREATE TRIGGER trg_cases_updated_at
    BEFORE UPDATE ON tenant_{TENANT_ID}.exception_cases
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON tenant_{TENANT_ID}.payments
    FOR EACH ROW
    EXECUTE FUNCTION common.update_updated_at_column();

-- ============================================================================
-- GRANT PERMISSIONS
-- ============================================================================

-- GRANT USAGE ON SCHEMA tenant_{TENANT_ID} TO invoice_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_{TENANT_ID} TO invoice_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA tenant_{TENANT_ID} TO invoice_app_user;

COMMENT ON SCHEMA tenant_{TENANT_ID} IS 'Tenant-specific schema for invoice management data isolation';

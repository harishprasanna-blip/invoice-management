# Invoice Management System - Integration Guide

## System Overview

This document describes the integration points and event flows between the bounded contexts in the DDD-based invoice management system.

## Architecture Summary

```
┌─────────────────────┐
│ InvoiceIngestion    │──┐
│ Context             │  │
└─────────────────────┘  │
                         │ InvoiceExtractedEvent
                         ▼
                    ┌─────────────────────┐
                    │ ValidationContext   │
                    │                     │
                    └─────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
         │ Validation    │ Mismatch      │ Compliance
         │ Passed        │ Detected      │ Violation
         ▼               ▼               ▼
┌─────────────────┐  ┌─────────────────────┐
│ Payment         │  │ ExceptionHandling   │
│ Orchestration   │  │ Context             │
└─────────────────┘  └─────────────────────┘
```

## Port Assignments

| Service | Port | Purpose |
|---------|------|---------|
| InvoiceIngestionContext | 8080 | Invoice submission and extraction |
| ValidationContext | 8082 | 2/3-way matching and compliance |
| ExceptionHandlingContext | 8083 | Exception resolution |
| PaymentOrchestrationContext | 8084 | Payment execution |
| Document Extraction (Python) | 8001 | AI extraction service |
| ML Resolution (Python) | 8002 | ML recommendation service |
| PostgreSQL | 5432 | Main database |
| PgVector | 5433 | Vector database for ML |
| LocalStack (S3) | 4566 | Document storage |
| SAP Mock | 8090 | SAP ERP mock |

## Event Flow

### 1. Invoice Submission Flow

**User Action:** Submit invoice document

```
POST /api/v1/invoices
Content-Type: multipart/form-data

file: invoice.pdf
vendorName: "ACME Corp"
```

**InvoiceIngestionContext:**
1. Receives invoice document
2. Uploads to S3 with encryption
3. Creates Invoice aggregate (status: RECEIVED)
4. Emits `InvoiceReceivedEvent`
5. Calls Python AI service for extraction
6. Returns 202 Accepted with invoiceId

**Python Document Extraction Service:**
1. Receives extraction request
2. Downloads document from S3 (presigned URL)
3. Extracts data using Azure OpenAI GPT-4
4. Validates extraction result
5. Publishes `ExtractionCompleted` event to event_store

**InvoiceIngestionContext (Event Handler):**
1. Polls event_store for ExtractionCompleted
2. Updates Invoice aggregate (status: EXTRACTED)
3. Emits `InvoiceExtractedEvent`

### 2. Validation Flow

**ValidationContext (Event Listener):**
1. Polls event_store for `InvoiceExtractedEvent`
2. Creates PayableTransaction aggregate
3. Fetches PO and GR from SAP (via ACL)
4. Performs 2-way or 3-way matching
5. Performs compliance checks (GDPR, tax, sanctions, duplicate)
6. Completes validation

**Decision Points:**
- **All checks pass** → Emit `ValidationPassedEvent`
- **Variances exceed tolerance** → Emit `MismatchDetectedEvent`
- **Compliance check fails** → Emit `ComplianceViolationDetectedEvent`

### 3. Payment Flow (Happy Path)

**PaymentOrchestrationContext (Event Listener):**
1. Polls event_store for `ValidationPassedEvent`
2. Creates PaymentRun aggregate
3. Calculates early payment discounts
4. Executes Saga for payment:
   - Post to SAP GL
   - Execute payment
   - Update invoice status
5. Emits `PaymentExecutedEvent` or `PaymentFailedEvent`

### 4. Exception Handling Flow

**ExceptionHandlingContext (Event Listener):**
1. Polls event_store for exception events:
   - `MismatchDetectedEvent`
   - `ComplianceViolationDetectedEvent`
   - `ExtractionFailedEvent`
   - `PaymentFailedEvent`
2. Creates ExceptionCase aggregate
3. Calls ML service for resolution recommendation
4. Routes to appropriate resolver (auto or human)
5. Emits `ExceptionResolvedEvent` or `ExceptionEscalatedEvent`

## API Integration Examples

### Submit Invoice

```bash
curl -X POST http://localhost:8080/api/v1/invoices \
  -H "X-Tenant-ID: 550e8400-e29b-41d4-a716-446655440000" \
  -F "file=@invoice.pdf" \
  -F "vendorName=ACME Corp" \
  -F "poNumber=4500000001"
```

Response:
```json
{
  "invoiceId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "RECEIVED",
  "message": "Invoice submitted successfully"
}
```

### Check Validation Status

```bash
curl http://localhost:8082/api/v1/validation/invoices/123e4567-e89b-12d3-a456-426614174000 \
  -H "X-Tenant-ID: 550e8400-e29b-41d4-a716-446655440000"
```

Response:
```json
{
  "transactionId": "789e4567-e89b-12d3-a456-426614174999",
  "invoiceId": "123e4567-e89b-12d3-a456-426614174000",
  "validationStatus": "COMPLETED",
  "matchStatus": "MATCHED",
  "variances": [],
  "failedComplianceChecks": [],
  "readyForPayment": true,
  "validatedAt": "2024-01-25T10:30:00Z"
}
```

## Event Store Schema

### event_store Table

```sql
CREATE TABLE common.event_store (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    event_payload JSONB NOT NULL,
    event_metadata JSONB,
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    published_by VARCHAR(255),
    version INTEGER NOT NULL
);

CREATE INDEX idx_event_store_type ON common.event_store(event_type);
CREATE INDEX idx_event_store_aggregate ON common.event_store(aggregate_id);
CREATE INDEX idx_event_store_tenant ON common.event_store(tenant_id);
CREATE INDEX idx_event_store_occurred ON common.event_store(occurred_at);
CREATE INDEX idx_event_store_unpublished ON common.event_store(published_at)
    WHERE published_at IS NULL;
```

### event_subscriptions Table

```sql
CREATE TABLE common.event_subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES common.event_store(event_id),
    subscription_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    UNIQUE(event_id, subscription_name)
);

CREATE INDEX idx_subscriptions_name ON common.event_subscriptions(subscription_name);
CREATE INDEX idx_subscriptions_status ON common.event_subscriptions(status);
```

## Multi-Tenancy

### Tenant Context Propagation

Every HTTP request must include `X-Tenant-ID` header:

```bash
curl -H "X-Tenant-ID: 550e8400-e29b-41d4-a716-446655440000" \
  http://localhost:8080/api/v1/invoices
```

### Schema Isolation

Each tenant has a dedicated PostgreSQL schema:

```
tenant_550e8400_e29b_41d4_a716_446655440000
  ├── invoices
  ├── payable_transactions
  ├── exception_cases
  ├── payment_runs
  └── payments
```

### Tenant Security

- TenantContextFilter extracts tenant from X-Tenant-ID header
- TenantContext ThreadLocal stores current tenant
- Repository validates tenant before all operations
- SecurityException thrown on tenant mismatch

## SAP Integration

### Anti-Corruption Layer (ACL)

ValidationContext uses SAPProcurementAdapter to translate SAP data:

```java
// SAP Model (external)
SAPPurchaseOrder {
    "PurchaseOrder": "4500000001",
    "Supplier": "VENDOR-001",
    "TotalNetAmount": "5000.00"
}

// Domain Model (internal)
PurchaseOrderReference {
    poNumber: "4500000001",
    vendorId: "VENDOR-001",
    poAmount: Money(5000.00, USD)
}
```

### SAP API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/procurement/purchase-orders/{poNumber}` | GET | Fetch PO |
| `/api/procurement/goods-receipts/{grNumber}` | GET | Fetch GR |
| `/api/procurement/purchase-orders/{po}/goods-receipts/latest` | GET | Fetch latest GR for PO |
| `/api/finance/invoice-posting` | POST | Post invoice to FI |
| `/api/finance/gl-posting` | POST | Post GL document |

### Caching Strategy

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=300s  # 5 minutes
```

Cached data:
- Purchase Orders (5 min TTL)
- Goods Receipts (5 min TTL)

## Running the System

### Prerequisites

```bash
# Required environment variables
export AZURE_OPENAI_ENDPOINT=https://your-openai.openai.azure.com
export AZURE_OPENAI_API_KEY=your-api-key
export AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4
export DB_PASSWORD=invoice_secure_password_123
export SAP_API_URL=http://localhost:8090
```

### Start Infrastructure

```bash
# Start only database and dependencies
docker-compose up postgres localstack pgvector sap-mock -d

# Wait for health checks
docker-compose ps
```

### Start Services

```bash
# Option 1: Start all services
docker-compose up -d

# Option 2: Start specific services
docker-compose up invoice-ingestion-service validation-service -d

# Option 3: Build and run locally
mvn clean install
java -jar invoice-ingestion/target/invoice-ingestion-1.0.0-SNAPSHOT.jar
java -jar validation/target/validation-1.0.0-SNAPSHOT.jar
```

### Verify System Health

```bash
# Check all services
curl http://localhost:8080/actuator/health  # InvoiceIngestion
curl http://localhost:8082/api/v1/validation/health  # Validation
curl http://localhost:8001/health  # Document Extraction (Python)

# Check database
docker exec invoice-mgmt-postgres psql -U invoice_app -d invoice_management_prod \
  -c "SELECT COUNT(*) FROM common.event_store;"

# Check S3 (LocalStack)
aws --endpoint-url=http://localhost:4566 s3 ls s3://invoice-documents/
```

## Monitoring and Debugging

### View Events

```sql
-- View all events
SELECT event_id, event_type, aggregate_type, aggregate_id, occurred_at
FROM common.event_store
ORDER BY occurred_at DESC
LIMIT 10;

-- View unprocessed events for specific subscription
SELECT e.event_id, e.event_type, e.occurred_at
FROM common.event_store e
LEFT JOIN common.event_subscriptions s
  ON e.event_id = s.event_id
  AND s.subscription_name = 'ValidationContext.InvoiceExtracted'
WHERE s.event_id IS NULL
AND e.event_type = 'InvoiceExtracted'
ORDER BY e.occurred_at;
```

### View Logs

```bash
# View service logs
docker-compose logs -f invoice-ingestion-service
docker-compose logs -f validation-service
docker-compose logs -f document-extraction-service

# Filter for errors
docker-compose logs validation-service | grep ERROR
```

### Metrics

```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8082/actuator/prometheus

# Health details
curl http://localhost:8080/actuator/health | jq
```

## Troubleshooting

### Issue: Events Not Being Processed

**Symptoms:** InvoiceExtractedEvent in event_store but no validation started

**Check:**
```sql
-- Check if event exists
SELECT * FROM common.event_store WHERE event_type = 'InvoiceExtracted';

-- Check if already processed
SELECT * FROM common.event_subscriptions
WHERE subscription_name = 'ValidationContext.InvoiceExtracted';
```

**Solution:**
- Ensure ValidationContext service is running
- Check scheduler is enabled (`@EnableScheduling`)
- Verify database connectivity
- Check logs for exceptions

### Issue: SAP Integration Failing

**Symptoms:** Validation fails with "Purchase Order not found"

**Check:**
```bash
# Test SAP mock is running
curl http://localhost:8090/api/procurement/purchase-orders/4500000001

# Check SAP_API_BASE_URL environment variable
docker exec invoice-mgmt-validation env | grep SAP
```

**Solution:**
- Start SAP mock: `docker-compose up sap-mock -d --profile testing`
- Verify SAP_API_BASE_URL points to mock server
- Check SAP mock expectations file is mounted

### Issue: Tenant Isolation Not Working

**Symptoms:** Can access data from different tenant

**Check:**
- Verify X-Tenant-ID header is sent in all requests
- Check TenantContextFilter is registered
- Verify repository validates tenant security

**Solution:**
```java
// Ensure filter is applied
@Component
public class TenantContextFilter implements Filter {
    // Filter implementation
}

// Repository must validate tenant
private void validateTenantSecurity(PayableTransaction transaction) {
    if (!transaction.getTenantId().equals(TenantContext.getCurrentTenant())) {
        throw new SecurityException("Tenant mismatch");
    }
}
```

## Performance Considerations

### Database Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
```

### Event Processing

- Event polling interval: 5 seconds (configurable)
- Batch size: 10 events per poll
- Consider Kafka for high-volume scenarios (>10k invoices/day)

### SAP API Calls

- Cache TTL: 5 minutes
- Timeout: 30 seconds
- Circuit breaker recommended for production

## Security Checklist

- [ ] X-Tenant-ID header required for all requests
- [ ] TenantContext cleared in finally blocks
- [ ] Repository validates tenant on all operations
- [ ] S3 documents encrypted at rest (SSE-S3)
- [ ] Database credentials in environment variables
- [ ] SAP credentials secured
- [ ] Audit logging enabled for all operations
- [ ] HTTPS/TLS for all external communications (production)

## Next Steps

1. Complete ExceptionHandlingContext implementation
2. Implement PaymentOrchestrationContext with Saga pattern
3. Add comprehensive integration tests
4. Set up monitoring (Prometheus + Grafana)
5. Implement distributed tracing (Sleuth + Zipkin)
6. Production deployment to AWS ECS

# ValidationContext - Invoice Validation Service

## Overview

ValidationContext is responsible for validating invoices against procurement documents (Purchase Orders and Goods Receipts) and performing compliance checks. This bounded context implements 2-way and 3-way matching with configurable tolerance rules and comprehensive compliance validation.

## Domain Model

### Aggregate Root
- **PayableTransaction**: Coordinates matching and compliance validation
  - Links Invoice, Purchase Order, and Goods Receipt
  - Contains MatchingResult and ComplianceChecks
  - Emits domain events for downstream contexts

### Value Objects
- **PurchaseOrderReference**: SAP Purchase Order data (via ACL)
- **GoodsReceiptReference**: SAP Goods Receipt data (via ACL)
- **MatchingResult**: Result of 2/3-way matching with variances
- **InvoiceReference**: Reference to invoice from InvoiceIngestionContext

### Entities
- **ComplianceCheck**: Individual compliance validation result
- **MatchingResult.Variance**: Detected variance with tolerance flag
- **MatchingResult.LineItemMatch**: Line-level matching result

### Domain Services
- **ThreeWayMatchingService**: Performs 2/3-way matching with tolerance rules
- **ComplianceValidationService**: Validates GDPR, tax, sanctions, duplicates

### Domain Events
- **ValidationStartedEvent**: Validation process initiated
- **ValidationPassedEvent**: All checks passed → triggers PaymentOrchestrationContext
- **MismatchDetectedEvent**: Variances exceed tolerance → triggers ExceptionHandlingContext
- **ComplianceViolationDetectedEvent**: Compliance check failed → triggers ExceptionHandlingContext

## Architecture Patterns

### DDD Tactical Patterns
- **Aggregate Root**: PayableTransaction as consistency boundary
- **Value Objects**: Immutable objects (PO, GR references)
- **Domain Services**: Matching and compliance logic
- **Repository**: Persistence abstraction
- **Domain Events**: Inter-context communication

### Anti-Corruption Layer (ACL)
- **SAPProcurementAdapter**: Translates SAP data models to domain models
- Protects domain from SAP API changes
- Caches SAP data (5-minute TTL) to reduce API calls

### Hexagonal Architecture
- **Domain Core**: Pure business logic (no infrastructure dependencies)
- **Ports**: Interfaces (PayableTransactionRepository)
- **Adapters**: Implementations (JPA, SAP REST client)

### Event-Driven Architecture
- **Event Listener**: Polls event_store for InvoiceExtractedEvent
- **Event Publishing**: Publishes domain events via transactional outbox
- **Choreography**: Loose coupling between contexts

## Matching Logic

### 2-Way Matching (Invoice vs PO)
1. Validate currency match
2. Compare total amounts with tolerance
3. Calculate overall matching score

### 3-Way Matching (Invoice vs PO vs GR)
1. Validate currency match
2. Validate GR linked to correct PO
3. Compare total amounts (Invoice vs PO)
4. Compare line-level quantities (PO vs GR)
5. Calculate overall matching score

### Tolerance Configuration
- **Price Tolerance**: 2% (default)
- **Quantity Tolerance**: 5% (default)
- **Total Tolerance**: 1% (default)

Variances within tolerance: `PARTIAL_MATCH`
Variances exceeding tolerance: `MISMATCH`

## Compliance Checks

### GDPR Compliance (EU Tenants)
- **GDPR_CONSENT**: Vendor data processing consent
- **GDPR_DATA_MINIMIZATION**: Unnecessary PII detection

### Financial Compliance
- **TAX_VALIDATION**: Tax calculation and format validation
- **CONTRACT_COMPLIANCE**: Invoice within contract limits

### Security Compliance
- **SANCTIONS_SCREENING**: OFAC, EU, UN sanctions lists
- **DUPLICATE_CHECK**: Duplicate invoice detection

## REST API

### Endpoints
```
GET  /api/v1/validation/invoices/{invoiceId}           - Get validation status
GET  /api/v1/validation/transactions/{transactionId}   - Get transaction details
POST /api/v1/validation/transactions/{id}/revalidate   - Re-validate after resolution
GET  /api/v1/validation/health                         - Health check
```

## Event Flow

### Inbound Events
1. **InvoiceExtractedEvent** (from InvoiceIngestionContext)
   - Triggers validation workflow
   - Fetches PO/GR from SAP
   - Performs matching and compliance checks

### Outbound Events
1. **ValidationPassedEvent** → PaymentOrchestrationContext
   - All checks passed, ready for payment

2. **MismatchDetectedEvent** → ExceptionHandlingContext
   - Variances exceed tolerance, needs resolution

3. **ComplianceViolationDetectedEvent** → ExceptionHandlingContext
   - Compliance check failed, needs resolution

## SAP Integration

### Configuration
```yaml
sap:
  api:
    base-url: http://localhost:9000/sap
    username: ${SAP_USERNAME}
    password: ${SAP_PASSWORD}
    timeout-seconds: 30
```

### Caching Strategy
- **Cache Provider**: Caffeine
- **TTL**: 5 minutes (300 seconds)
- **Max Size**: 1000 entries
- **Cached Data**: Purchase Orders, Goods Receipts

### SAP API Endpoints
- `GET /api/procurement/purchase-orders/{poNumber}`
- `GET /api/procurement/goods-receipts/{grNumber}`
- `GET /api/procurement/purchase-orders/{poNumber}/goods-receipts/latest`

## Database Schema

### Table: payable_transactions
```sql
- transaction_id (UUID, PK)
- tenant_id (UUID, NOT NULL)
- invoice_id (UUID, UNIQUE)
- invoice_reference (JSONB)
- po_reference (JSONB)
- gr_reference (JSONB)
- matching_result (JSONB)
- compliance_checks (JSONB)
- validation_status (VARCHAR)
- validated_at (TIMESTAMP)
- audit_log (JSONB)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
- version (BIGINT)
```

## Configuration

### Application Properties
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/invoice_management
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=300s

server:
  port: 8082

sap:
  api:
    base-url: ${SAP_API_BASE_URL}
    timeout-seconds: 30
```

## Running the Service

### Local Development
```bash
# Build
mvn clean package

# Run
java -jar target/validation-1.0.0-SNAPSHOT.jar

# Or with Maven
mvn spring-boot:run
```

### Docker
```bash
# Build image
docker build -t validation-service .

# Run container
docker run -p 8082:8082 \
  -e DB_HOST=postgres \
  -e SAP_API_BASE_URL=http://sap-mock:9000/sap \
  validation-service
```

### Docker Compose
```bash
docker-compose up validation-service
```

## Testing

### Health Check
```bash
curl http://localhost:8082/api/v1/validation/health
```

### Get Validation Status
```bash
curl http://localhost:8082/api/v1/validation/invoices/{invoiceId}
```

## Multi-Tenancy

- **Schema-per-tenant** isolation
- TenantContext from X-Tenant-ID header or JWT
- Repository validates tenant security on all operations
- SAP adapter uses tenant for request routing

## Security

- **Tenant Isolation**: Repository enforces tenant boundary
- **Audit Logging**: All operations logged for SOC2 compliance
- **Optimistic Locking**: Version column prevents concurrent updates
- **SAP Credentials**: Secured via environment variables

## Dependencies

- Spring Boot 3.2.0
- Spring Data JPA
- Spring WebFlux (SAP client)
- PostgreSQL JDBC Driver
- Caffeine Cache
- Jackson (JSON serialization)
- Lombok

## Next Steps

After ValidationContext completion:
1. **ExceptionHandlingContext**: ML-driven exception resolution
2. **PaymentOrchestrationContext**: Payment execution with Saga pattern
3. Integration testing across all contexts
4. Production deployment configuration

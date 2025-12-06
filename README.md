# Invoice Management System

A Domain-Driven Design (DDD) based invoice management system with agentic AI workflows for intelligent automation of accounts payable processes.

## Architecture

### Technology Stack
- **Backend**: Spring Boot 3.2+ (Java 17) for core AP logic
- **AI Services**: Python (FastAPI) for Azure OpenAI integration
- **Database**: PostgreSQL 15+ with JSONB support
- **Cloud**: AWS (ECS, RDS, S3)
- **Orchestration**: Docker Compose (development), ECS Fargate (production)
- **ERP**: SAP REST API integration
- **AI/ML**: Azure OpenAI GPT-4
- **Compliance**: SOC2, GDPR (US & EU)

### Bounded Contexts

1. **InvoiceIngestionContext** - Multi-format invoice extraction with AI
2. **ValidationContext** - 2/3-way matching and compliance validation
3. **ExceptionHandlingContext** - ML-driven exception resolution
4. **PaymentOrchestrationContext** - Payment execution with Saga pattern

## Getting Started

### Prerequisites

- Docker Desktop 4.0+ with Docker Compose
- Java 17+ (for local development)
- Python 3.11+ (for AI services)
- Maven 3.8+
- Azure OpenAI API access (for production)

### Quick Start

1. **Clone the repository**
   ```bash
   cd "d:\invoice management"
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   # Edit .env with your Azure OpenAI credentials
   ```

3. **Start infrastructure** (PostgreSQL, LocalStack, PgVector)
   ```bash
   docker-compose up -d postgres localstack pgvector
   ```

4. **Verify database initialization**
   ```bash
   docker-compose exec postgres psql -U invoice_app -d invoice_management_prod -c "\dn"
   ```
   You should see the `common` schema.

5. **Build shared kernel**
   ```bash
   cd shared-kernel
   mvn clean install
   ```

6. **Run Spring Boot services** (when implemented)
   ```bash
   docker-compose up -d invoice-ingestion-service validation-service exception-handling-service payment-orchestration-service
   ```

7. **Run Python AI services** (when implemented)
   ```bash
   docker-compose up -d document-extraction-service ml-resolution-service
   ```

### Development Mode

Run services individually for development:

```bash
# Start only infrastructure
docker-compose up -d postgres localstack pgvector

# Run Spring Boot service locally (example)
cd invoice-ingestion
mvn spring-boot:run

# Run Python service locally (example)
cd ai-services/document-extraction
pip install -r requirements.txt
uvicorn main:app --reload --port 8001
```

## Project Structure

```
invoice management/
├── shared-kernel/                 # Shared domain models and infrastructure
│   ├── domain/                   # Value objects (Money, TenantId, AuditLog)
│   ├── events/                   # Domain events and Event Store
│   ├── tenant/                   # Multi-tenancy context
│   └── security/                 # Tenant context filter
├── invoice-ingestion/            # Invoice ingestion bounded context
├── validation/                   # Validation bounded context
├── exception-handling/           # Exception handling bounded context
├── payment-orchestration/        # Payment orchestration bounded context
├── ai-services/                  # Python AI services
│   ├── document-extraction/      # Azure OpenAI document extraction
│   └── ml-resolution/            # ML-based exception resolution
├── infrastructure/               # Infrastructure scripts
│   ├── database/                 # SQL initialization scripts
│   ├── localstack/               # LocalStack AWS setup
│   └── mocks/                    # Mock services for testing
├── docker-compose.yml            # Docker Compose configuration
├── .env.example                  # Environment variables template
└── README.md                     # This file
```

## Multi-Tenancy

The system uses PostgreSQL schema-per-tenant for strong data isolation:

- **Common schema**: Event store, tenant registry, audit trail
- **Tenant schemas**: `tenant_{tenantId}` for each tenant's data

### Creating a New Tenant

```sql
-- 1. Register tenant in common schema
INSERT INTO common.tenant_registry (tenant_id, tenant_name, schema_name, region, status)
VALUES ('a1b2c3d4-e5f6-g7h8-i9j0-k1l2m3n4o5p6', 'Acme Corp', 'tenant_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6', 'US', 'ACTIVE');

-- 2. Create tenant schema from template
-- Replace {TENANT_ID} with actual tenant UUID (without hyphens)
-- Use tenant-schema-template.sql as template
```

## Event-Driven Architecture

The system uses **Event Choreography** for inter-context communication:

1. Aggregate produces domain event
2. Event persisted in `event_store` (Transactional Outbox)
3. Background worker polls unpublished events
4. Worker publishes to Spring `ApplicationEventPublisher`
5. Subscribers consume via `@EventListener`

**Event Flow:**
```
InvoiceExtracted → ValidationContext
ValidationPassed → PaymentOrchestrationContext
MismatchDetected → ExceptionHandlingContext
ExceptionResolved → OriginalContext
PaymentExecuted → ERP (via Saga)
```

## API Endpoints

### Invoice Ingestion
- `POST /api/v1/invoices` - Submit invoice document
- `GET /api/v1/invoices/{id}` - Get invoice status

### Validation
- `GET /api/v1/transactions/{id}` - Get validation status

### Exception Handling
- `GET /api/v1/exceptions` - List exception cases
- `PUT /api/v1/exceptions/{id}/resolve` - Resolve exception

### Payment Orchestration
- `POST /api/v1/payment-runs` - Create payment run
- `GET /api/v1/payment-runs/{id}` - Get payment run status

## Compliance & Security

### GDPR (EU Tenants)
- Data minimization
- Right to erasure (anonymization after retention)
- DSAR API endpoint
- EU data residency (AWS eu-central-1)

### SOC2 (All Tenants)
- Comprehensive audit logging (10-year retention)
- Role-based access control (RBAC)
- Multi-AZ deployment (99.9% SLA)
- Encryption at rest and in transit

## Testing

```bash
# Unit tests
mvn test

# Integration tests (with Testcontainers)
mvn verify

# End-to-end tests
./scripts/run-e2e-tests.sh
```

## Deployment

### AWS ECS (Production)

```bash
# Build and push Docker images
./scripts/build-and-push.sh

# Deploy with Terraform/CloudFormation
cd infrastructure/aws
terraform apply
```

### Database Migrations

Database schema changes are managed via SQL scripts:
- `infrastructure/database/migrations/V{version}__{description}.sql`

## Monitoring & Observability

- **Logs**: CloudWatch Logs (JSON structured logging)
- **Metrics**: CloudWatch Metrics / Prometheus
- **Tracing**: Correlation IDs in all events
- **Health Checks**: `/actuator/health` (Spring Boot)

## Contributing

1. Create feature branch: `git checkout -b feature/your-feature`
2. Follow DDD principles and existing patterns
3. Write tests for new code
4. Update documentation
5. Submit pull request

## License

Proprietary - All Rights Reserved

## Contact

For questions or support, contact the development team.

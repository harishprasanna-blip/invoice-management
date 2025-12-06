# ML Resolution Service

AI-powered exception resolution recommendation service using Azure OpenAI GPT-4 and pgvector for semantic similarity search.

## Overview

This Python FastAPI service provides intelligent resolution recommendations for invoice exception cases by:
- Analyzing exception details using GPT-4
- Finding similar historical cases via vector similarity search
- Recommending resolution strategies with confidence scores
- Building knowledge base from resolved cases

## Architecture

**Bounded Context**: ExceptionHandlingContext (AI Agent)  
**Technology Stack**:
- FastAPI (async web framework)
- Azure OpenAI GPT-4 (recommendation engine)
- sentence-transformers (text embeddings)
- pgvector (vector similarity search)
- PostgreSQL (case storage)

## API Endpoints

### POST /api/v1/recommend-resolution
Get ML-powered resolution recommendation for exception case.

**Request**:
```json
{
  "case_id": "uuid",
  "exception_type": "MATCHING_MISMATCH",
  "severity": "HIGH",
  "exception_details": "PO number mismatch...",
  "source_context": "ValidationContext",
  "source_aggregate_id": "uuid"
}
```

**Response**:
```json
{
  "recommended_strategy": "HOLD_PENDING_INVESTIGATION",
  "confidence": 0.87,
  "reasoning": "Similar cases show...",
  "similar_cases": ["case-id-1", "case-id-2"],
  "automatable": false,
  "model_version": "gpt-4-2024-02-01"
}
```

### POST /api/v1/similar-cases
Find similar historical exception cases.

**Request**:
```json
{
  "exception_type": "MATCHING_MISMATCH",
  "exception_details": "Details...",
  "severity": "HIGH",
  "top_k": 5
}
```

**Response**:
```json
{
  "similar_case_ids": ["id1", "id2", "id3"],
  "similarity_scores": [0.92, 0.88, 0.85]
}
```

### POST /api/v1/store-case-embedding
Store resolved case for future similarity search.

## Resolution Strategies

Available strategies:
1. **APPROVE_AND_POST** - Approve and post to ERP
2. **REQUEST_VENDOR_CORRECTION** - Request vendor correction
3. **ADJUST_AND_APPROVE** - Make adjustment and approve
4. **HOLD_PENDING_INVESTIGATION** - Hold for investigation
5. **ESCALATE_TO_VENDOR_MANAGEMENT** - Escalate to vendor team
6. **ESCALATE_TO_FINANCE** - Escalate to finance
7. **ROUTE_TO_MANUAL_PROCESSING** - Manual processing queue

## Configuration

Environment variables:
```bash
# Azure OpenAI
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_API_KEY=your-api-key
AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4

# Vector Database (pgvector)
VECTOR_DB_URL=postgresql://vector_user:password@localhost:5433/vector_db

# Main Database
DATABASE_URL=postgresql://invoice_app:password@localhost:5432/invoice_management_prod
```

## Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Run service
uvicorn main:app --host 0.0.0.0 --port 8002 --reload

# Access docs
open http://localhost:8002/docs
```

## Docker

```bash
# Build image
docker build -t ml-resolution-service .

# Run container
docker run -p 8002:8002 \
  -e AZURE_OPENAI_ENDPOINT=... \
  -e AZURE_OPENAI_API_KEY=... \
  ml-resolution-service
```

## Integration with Exception Handling Service

The Spring Boot ExceptionHandlingService calls this service via `MLResolutionServiceClient`:

1. Exception case created
2. ML recommendation requested
3. Similar cases found via vector search
4. GPT-4 analyzes case and historical data
5. Recommendation returned with confidence score
6. If confidence > threshold and automatable, auto-resolve
7. Otherwise, route to appropriate resolver

## Fallback Behavior

If Azure OpenAI is unavailable or fails:
- Rule-based fallback recommendations provided
- Lower confidence scores (0.45-0.60)
- Not marked as automatable
- Service remains functional

## Performance

- Average response time: 1-2 seconds
- Embedding generation: ~100ms
- Vector search: ~50ms
- GPT-4 call: 1-1.5 seconds
- Handles 50+ concurrent requests

## Monitoring

Health check: `GET /health`

Metrics available via structured JSON logs:
- Request/response times
- GPT-4 success/failure rates
- Vector search performance
- Embedding generation stats

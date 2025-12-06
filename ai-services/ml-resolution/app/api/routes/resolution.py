"""
Resolution recommendation API routes.
Provides ML-powered exception resolution recommendations.
"""

from fastapi import APIRouter, HTTPException, Header, status
from pydantic import BaseModel, Field
from typing import Optional, List
from uuid import UUID
from decimal import Decimal
import structlog

from app.services.resolution_service import ResolutionService
from app.services.vector_service import VectorService

logger = structlog.get_logger(__name__)
router = APIRouter()

# Initialize services
resolution_service = ResolutionService()
vector_service = VectorService()


# Request/Response Models
class MLRecommendationRequest(BaseModel):
    """Request for ML resolution recommendation."""
    case_id: UUID = Field(..., description="Exception case ID")
    exception_type: str = Field(..., description="Type of exception")
    severity: str = Field(..., description="Exception severity")
    exception_details: str = Field(..., description="Detailed exception information")
    source_context: str = Field(..., description="Source bounded context")
    source_aggregate_id: UUID = Field(..., description="Source aggregate ID")


class MLRecommendationResponse(BaseModel):
    """Response containing ML recommendation."""
    recommended_strategy: str = Field(..., description="Recommended resolution strategy")
    confidence: Decimal = Field(..., description="Confidence score (0-1)")
    reasoning: str = Field(..., description="Explanation of recommendation")
    similar_cases: List[str] = Field(default_factory=list, description="Similar case IDs")
    automatable: bool = Field(..., description="Can be auto-resolved")
    model_version: str = Field(..., description="Model version used")


class SimilarCasesRequest(BaseModel):
    """Request for finding similar cases."""
    exception_type: str
    exception_details: str
    severity: str
    top_k: int = Field(default=5, ge=1, le=20)


class SimilarCasesResponse(BaseModel):
    """Response with similar cases."""
    similar_case_ids: List[str]
    similarity_scores: List[Decimal]


@router.post("/recommend-resolution", response_model=MLRecommendationResponse)
async def recommend_resolution(
    request: MLRecommendationRequest,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID")
) -> MLRecommendationResponse:
    """
    Get ML-powered resolution recommendation for exception case.
    
    Uses Azure OpenAI GPT-4 with historical case data to recommend
    the best resolution strategy with confidence score.
    """
    try:
        logger.info(
            "Received recommendation request",
            case_id=str(request.case_id),
            exception_type=request.exception_type,
            tenant_id=x_tenant_id
        )

        # Find similar historical cases
        similar_cases = await vector_service.find_similar_cases(
            exception_type=request.exception_type,
            exception_details=request.exception_details,
            severity=request.severity,
            top_k=5
        )

        # Get GPT-4 recommendation
        recommendation = await resolution_service.get_recommendation(
            case_id=request.case_id,
            exception_type=request.exception_type,
            severity=request.severity,
            exception_details=request.exception_details,
            source_context=request.source_context,
            similar_cases=similar_cases
        )

        logger.info(
            "Generated recommendation",
            case_id=str(request.case_id),
            strategy=recommendation["strategy"],
            confidence=float(recommendation["confidence"])
        )

        return MLRecommendationResponse(
            recommended_strategy=recommendation["strategy"],
            confidence=Decimal(str(recommendation["confidence"])),
            reasoning=recommendation["reasoning"],
            similar_cases=[case["case_id"] for case in similar_cases],
            automatable=recommendation["automatable"],
            model_version=recommendation["model_version"]
        )

    except Exception as e:
        logger.error(
            "Failed to generate recommendation",
            case_id=str(request.case_id),
            error=str(e)
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to generate recommendation: {str(e)}"
        )


@router.post("/similar-cases", response_model=SimilarCasesResponse)
async def find_similar_cases(
    request: SimilarCasesRequest,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID")
) -> SimilarCasesResponse:
    """
    Find similar historical exception cases using vector similarity search.
    
    Uses sentence-transformers embeddings stored in pgvector to find
    semantically similar cases.
    """
    try:
        logger.info(
            "Finding similar cases",
            exception_type=request.exception_type,
            tenant_id=x_tenant_id
        )

        similar_cases = await vector_service.find_similar_cases(
            exception_type=request.exception_type,
            exception_details=request.exception_details,
            severity=request.severity,
            top_k=request.top_k
        )

        case_ids = [case["case_id"] for case in similar_cases]
        scores = [Decimal(str(case["similarity"])) for case in similar_cases]

        logger.info(
            "Found similar cases",
            count=len(case_ids),
            tenant_id=x_tenant_id
        )

        return SimilarCasesResponse(
            similar_case_ids=case_ids,
            similarity_scores=scores
        )

    except Exception as e:
        logger.error(
            "Failed to find similar cases",
            error=str(e)
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to find similar cases: {str(e)}"
        )


@router.post("/store-case-embedding")
async def store_case_embedding(
    case_id: UUID,
    exception_type: str,
    exception_details: str,
    severity: str,
    resolution_strategy: str,
    x_tenant_id: Optional[str] = Header(None, alias="X-Tenant-ID")
):
    """
    Store case embedding for future similarity search.
    
    Called after a case is resolved to build historical knowledge base.
    """
    try:
        logger.info(
            "Storing case embedding",
            case_id=str(case_id),
            tenant_id=x_tenant_id
        )

        await vector_service.store_case_embedding(
            case_id=str(case_id),
            exception_type=exception_type,
            exception_details=exception_details,
            severity=severity,
            resolution_strategy=resolution_strategy,
            tenant_id=x_tenant_id
        )

        return {"status": "success", "case_id": str(case_id)}

    except Exception as e:
        logger.error(
            "Failed to store case embedding",
            case_id=str(case_id),
            error=str(e)
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to store case embedding: {str(e)}"
        )

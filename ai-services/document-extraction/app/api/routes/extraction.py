"""
API routes for document extraction.
Handles extraction requests from Spring Boot service.
"""

import uuid
from typing import Dict, Any, Optional

import structlog
from fastapi import APIRouter, HTTPException, status, BackgroundTasks
from pydantic import BaseModel, Field

from app.services.extraction_service import ExtractionService
from app.services.event_publisher import EventPublisher

logger = structlog.get_logger(__name__)

router = APIRouter()

# Dependency injection (simplified - in production use FastAPI Depends)
extraction_service = ExtractionService()
event_publisher = EventPublisher()


class DocumentReference(BaseModel):
    """Document reference from S3."""
    s3Url: str = Field(..., description="Presigned S3 URL for document download")
    documentFormat: str = Field(..., description="Document format (PDF, EDI_X12, etc.)")


class ExtractionRequest(BaseModel):
    """Request model for document extraction."""
    correlationId: str = Field(..., description="Correlation ID for tracing")
    tenantId: str = Field(..., description="Tenant identifier")
    invoiceId: str = Field(..., description="Invoice identifier")
    documentReference: DocumentReference
    extractionHints: Optional[Dict[str, Any]] = Field(default_factory=dict)


class ExtractionResponse(BaseModel):
    """Response model for extraction request."""
    correlationId: str
    status: str
    estimatedCompletionSeconds: int


@router.post("/extract", response_model=ExtractionResponse, status_code=status.HTTP_202_ACCEPTED)
async def request_extraction(
    request: ExtractionRequest,
    background_tasks: BackgroundTasks
):
    """
    Request invoice document extraction.

    Accepts extraction request and processes asynchronously.
    Returns 202 Accepted immediately, with result published via event.

    Args:
        request: Extraction request with document reference
        background_tasks: FastAPI background tasks for async processing

    Returns:
        ExtractionResponse with correlation ID and status
    """
    logger.info(
        "Extraction request received",
        correlation_id=request.correlationId,
        tenant_id=request.tenantId,
        invoice_id=request.invoiceId,
        document_format=request.documentReference.documentFormat
    )

    try:
        # Validate request
        if not request.documentReference.s3Url:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Document URL is required"
            )

        # Schedule extraction in background
        background_tasks.add_task(
            process_extraction,
            request
        )

        logger.info(
            "Extraction request accepted",
            correlation_id=request.correlationId,
            invoice_id=request.invoiceId
        )

        return ExtractionResponse(
            correlationId=request.correlationId,
            status="PROCESSING",
            estimatedCompletionSeconds=15
        )

    except Exception as e:
        logger.error(
            "Failed to accept extraction request",
            correlation_id=request.correlationId,
            error=str(e),
            exc_info=e
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to process request: {str(e)}"
        )


async def process_extraction(request: ExtractionRequest):
    """
    Process extraction asynchronously in background.

    Args:
        request: Extraction request
    """
    logger.info(
        "Starting extraction processing",
        correlation_id=request.correlationId,
        invoice_id=request.invoiceId
    )

    try:
        # Extract invoice data using Azure OpenAI
        extraction_result = await extraction_service.extract_invoice(
            document_url=request.documentReference.s3Url,
            document_format=request.documentReference.documentFormat,
            hints=request.extractionHints
        )

        # Publish extraction completed event to PostgreSQL event store
        await event_publisher.publish_extraction_completed(
            correlation_id=request.correlationId,
            tenant_id=request.tenantId,
            invoice_id=request.invoiceId,
            extraction_result=extraction_result
        )

        logger.info(
            "Extraction completed successfully",
            correlation_id=request.correlationId,
            invoice_id=request.invoiceId,
            confidence_score=extraction_result.get("confidenceScore")
        )

    except Exception as e:
        logger.error(
            "Extraction processing failed",
            correlation_id=request.correlationId,
            invoice_id=request.invoiceId,
            error=str(e),
            exc_info=e
        )

        # Publish extraction failed event
        await event_publisher.publish_extraction_failed(
            correlation_id=request.correlationId,
            tenant_id=request.tenantId,
            invoice_id=request.invoiceId,
            error_message=str(e)
        )


@router.get("/status/{correlation_id}")
async def get_extraction_status(correlation_id: str):
    """
    Get extraction status by correlation ID.

    Args:
        correlation_id: Correlation ID from extraction request

    Returns:
        Extraction status
    """
    # In production, query status from database/cache
    # For now, return placeholder
    return {
        "correlationId": correlation_id,
        "status": "PROCESSING",
        "message": "Extraction in progress"
    }

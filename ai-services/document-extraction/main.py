"""
Document Extraction Service with Azure OpenAI GPT-4.
FastAPI application for AI-powered invoice document extraction.

Bounded Context: InvoiceIngestionContext (Python AI Agent)
Integration: Called by Spring Boot InvoiceIngestionService
"""

import logging
import sys
from contextlib import asynccontextmanager
from typing import Optional

import structlog
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import extraction
from app.core.config import settings
from app.core.logging_config import configure_logging

# Configure structured logging
configure_logging()
logger = structlog.get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events."""
    # Startup
    logger.info("Starting Document Extraction Service", version="1.0.0")
    logger.info("Azure OpenAI configured", endpoint=settings.AZURE_OPENAI_ENDPOINT[:50] + "...")
    logger.info("S3 bucket configured", bucket=settings.AWS_S3_BUCKET)

    yield

    # Shutdown
    logger.info("Shutting down Document Extraction Service")


# Create FastAPI application
app = FastAPI(
    title="Invoice Document Extraction Service",
    description="AI-powered invoice document extraction using Azure OpenAI GPT-4",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware (for development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict to specific origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routes
app.include_router(extraction.router, prefix="/api/v1", tags=["extraction"])


@app.get("/health", tags=["health"])
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "document-extraction",
        "version": "1.0.0"
    }


@app.get("/", tags=["root"])
async def root():
    """Root endpoint."""
    return {
        "service": "Invoice Document Extraction Service",
        "version": "1.0.0",
        "docs": "/docs"
    }


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler."""
    logger.error("Unhandled exception", exc_info=exc, path=request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error"}
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=True,  # Development only
        log_config=None  # Use our custom logging
    )

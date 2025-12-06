"""
ML Resolution Service with Azure OpenAI GPT-4.
FastAPI application for AI-powered exception resolution recommendations.

Bounded Context: ExceptionHandlingContext (Python AI Agent)
Integration: Called by Spring Boot ExceptionHandlingService
"""

import logging
import sys
from contextlib import asynccontextmanager
from typing import Optional

import structlog
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import resolution
from app.core.config import settings
from app.core.logging_config import configure_logging

# Configure structured logging
configure_logging()
logger = structlog.get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events."""
    # Startup
    logger.info("Starting ML Resolution Service", version="1.0.0")
    logger.info("Azure OpenAI configured", endpoint=settings.AZURE_OPENAI_ENDPOINT[:50] + "...")
    logger.info("Vector DB configured", host=settings.VECTOR_DB_HOST)

    yield

    # Shutdown
    logger.info("Shutting down ML Resolution Service")


# Create FastAPI application
app = FastAPI(
    title="Invoice ML Resolution Service",
    description="AI-powered exception resolution recommendations using Azure OpenAI GPT-4",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(resolution.router, prefix="/api/v1", tags=["resolution"])


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "ml-resolution",
        "version": "1.0.0"
    }


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "ML Resolution Service",
        "version": "1.0.0",
        "docs": "/docs"
    }


# Global exception handler
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    logger.error("Unhandled exception", error=str(exc), path=request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error"}
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8002,
        reload=True,
        log_config=None  # Use structlog configuration
    )

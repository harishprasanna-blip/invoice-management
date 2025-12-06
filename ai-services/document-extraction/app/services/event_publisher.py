"""
Event publisher for publishing extraction results to PostgreSQL event store.
Implements transactional outbox pattern for reliable event delivery.
"""

import json
import uuid
from datetime import datetime
from typing import Dict, Any

import structlog
from sqlalchemy import create_engine, text
from sqlalchemy.pool import NullPool

from app.core.config import settings

logger = structlog.get_logger(__name__)


class EventPublisher:
    """Publishes domain events to PostgreSQL event store."""

    def __init__(self):
        """Initialize database connection."""
        self.engine = create_engine(
            settings.DATABASE_URL,
            poolclass=NullPool,  # No connection pooling for simplicity
            echo=False
        )

    async def publish_extraction_completed(
        self,
        correlation_id: str,
        tenant_id: str,
        invoice_id: str,
        extraction_result: Dict[str, Any]
    ):
        """
        Publish ExtractionCompleted event to event store.

        Args:
            correlation_id: Correlation ID for tracing
            tenant_id: Tenant identifier
            invoice_id: Invoice identifier
            extraction_result: Extraction result data
        """
        logger.info(
            "Publishing ExtractionCompleted event",
            correlation_id=correlation_id,
            invoice_id=invoice_id
        )

        event_payload = {
            "eventType": "ExtractionCompleted",
            "correlationId": correlation_id,
            "tenantId": tenant_id,
            "invoiceId": invoice_id,
            "extractionResult": extraction_result,
            "timestamp": datetime.utcnow().isoformat()
        }

        await self._insert_event(
            event_type="ExtractionCompleted",
            tenant_id=tenant_id,
            aggregate_id=invoice_id,
            aggregate_type="Invoice",
            payload=event_payload
        )

        logger.info("ExtractionCompleted event published", invoice_id=invoice_id)

    async def publish_extraction_failed(
        self,
        correlation_id: str,
        tenant_id: str,
        invoice_id: str,
        error_message: str
    ):
        """
        Publish ExtractionFailed event to event store.

        Args:
            correlation_id: Correlation ID for tracing
            tenant_id: Tenant identifier
            invoice_id: Invoice identifier
            error_message: Error message
        """
        logger.info(
            "Publishing ExtractionFailed event",
            correlation_id=correlation_id,
            invoice_id=invoice_id
        )

        event_payload = {
            "eventType": "ExtractionFailed",
            "correlationId": correlation_id,
            "tenantId": tenant_id,
            "invoiceId": invoice_id,
            "errorMessage": error_message,
            "timestamp": datetime.utcnow().isoformat()
        }

        await self._insert_event(
            event_type="ExtractionFailed",
            tenant_id=tenant_id,
            aggregate_id=invoice_id,
            aggregate_type="Invoice",
            payload=event_payload
        )

        logger.info("ExtractionFailed event published", invoice_id=invoice_id)

    async def _insert_event(
        self,
        event_type: str,
        tenant_id: str,
        aggregate_id: str,
        aggregate_type: str,
        payload: Dict[str, Any]
    ):
        """
        Insert event into PostgreSQL event store.

        Args:
            event_type: Type of event
            tenant_id: Tenant identifier
            aggregate_id: Aggregate identifier
            aggregate_type: Type of aggregate
            payload: Event payload
        """
        event_id = str(uuid.uuid4())
        metadata = {
            "publishedBy": "document-extraction-service",
            "publisher_version": "1.0.0"
        }

        query = text("""
            INSERT INTO common.event_store (
                event_id,
                tenant_id,
                aggregate_id,
                aggregate_type,
                event_type,
                event_version,
                event_payload,
                metadata,
                created_at
            ) VALUES (
                :event_id,
                :tenant_id,
                :aggregate_id,
                :aggregate_type,
                :event_type,
                :event_version,
                :event_payload,
                :metadata,
                :created_at
            )
        """)

        with self.engine.connect() as conn:
            conn.execute(
                query,
                {
                    "event_id": event_id,
                    "tenant_id": tenant_id,
                    "aggregate_id": aggregate_id,
                    "aggregate_type": aggregate_type,
                    "event_type": event_type,
                    "event_version": "1.0",
                    "event_payload": json.dumps(payload),
                    "metadata": json.dumps(metadata),
                    "created_at": datetime.utcnow()
                }
            )
            conn.commit()

        logger.debug("Event inserted into event store", event_id=event_id, event_type=event_type)

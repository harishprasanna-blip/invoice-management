"""
Vector similarity search service using pgvector and sentence-transformers.
Provides semantic search over historical exception cases.
"""

import asyncio
from typing import Dict, Any, List, Optional
import structlog
import psycopg2
from psycopg2.extras import RealDictCursor
from sentence_transformers import SentenceTransformer
import numpy as np

from app.core.config import settings

logger = structlog.get_logger(__name__)


class VectorService:
    """Service for vector similarity search over exception cases."""

    def __init__(self):
        """Initialize embedding model and database connection."""
        self.model = None
        self.conn = None
        self._initialize()

    def _initialize(self):
        """Initialize sentence transformer model."""
        try:
            logger.info("Loading embedding model", model=settings.EMBEDDING_MODEL)
            self.model = SentenceTransformer(settings.EMBEDDING_MODEL)
            logger.info("Embedding model loaded successfully")
        except Exception as e:
            logger.error("Failed to load embedding model", error=str(e))
            self.model = None

    def _get_connection(self):
        """Get database connection to pgvector."""
        try:
            if not self.conn or self.conn.closed:
                self.conn = psycopg2.connect(settings.VECTOR_DB_URL)
                # Enable pgvector extension if not already enabled
                with self.conn.cursor() as cur:
                    cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                    self.conn.commit()
            return self.conn
        except Exception as e:
            logger.error("Failed to connect to vector database", error=str(e))
            return None

    async def find_similar_cases(
        self,
        exception_type: str,
        exception_details: str,
        severity: str,
        top_k: int = 5
    ) -> List[Dict[str, Any]]:
        """
        Find similar exception cases using vector similarity search.
        
        Args:
            exception_type: Type of exception
            exception_details: Exception details text
            severity: Exception severity
            top_k: Number of similar cases to return
            
        Returns:
            List of similar cases with similarity scores
        """
        if not self.model:
            logger.warning("Embedding model not available, returning empty results")
            return []

        try:
            # Create embedding for query
            query_text = f"{exception_type} {severity} {exception_details}"
            query_embedding = await self._create_embedding(query_text)

            # Search vector database
            conn = self._get_connection()
            if not conn:
                return []

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                # Ensure table exists
                cur.execute("""
                    CREATE TABLE IF NOT EXISTS exception_case_embeddings (
                        case_id TEXT PRIMARY KEY,
                        exception_type TEXT NOT NULL,
                        exception_details TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        resolution_strategy TEXT NOT NULL,
                        embedding vector(384) NOT NULL,
                        tenant_id TEXT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """)
                conn.commit()

                # Create index if not exists
                cur.execute("""
                    CREATE INDEX IF NOT EXISTS exception_embeddings_idx 
                    ON exception_case_embeddings 
                    USING ivfflat (embedding vector_cosine_ops)
                    WITH (lists = 100);
                """)
                conn.commit()

                # Vector similarity search
                cur.execute("""
                    SELECT 
                        case_id,
                        exception_type,
                        exception_details,
                        severity,
                        resolution_strategy,
                        1 - (embedding <=> %s::vector) as similarity
                    FROM exception_case_embeddings
                    WHERE exception_type = %s
                    ORDER BY embedding <=> %s::vector
                    LIMIT %s;
                """, (
                    query_embedding.tolist(),
                    exception_type,
                    query_embedding.tolist(),
                    top_k
                ))

                results = cur.fetchall()

                # Filter by similarity threshold
                similar_cases = [
                    dict(row) for row in results
                    if row['similarity'] >= settings.SIMILARITY_THRESHOLD
                ]

                logger.info(
                    "Found similar cases",
                    count=len(similar_cases),
                    exception_type=exception_type
                )

                return similar_cases

        except Exception as e:
            logger.error("Failed to find similar cases", error=str(e))
            return []

    async def store_case_embedding(
        self,
        case_id: str,
        exception_type: str,
        exception_details: str,
        severity: str,
        resolution_strategy: str,
        tenant_id: Optional[str] = None
    ) -> bool:
        """
        Store case embedding for future similarity search.
        
        Args:
            case_id: Exception case ID
            exception_type: Type of exception
            exception_details: Exception details
            severity: Exception severity
            resolution_strategy: Applied resolution strategy
            tenant_id: Tenant ID for multi-tenancy
            
        Returns:
            True if stored successfully, False otherwise
        """
        if not self.model:
            logger.warning("Embedding model not available, skipping storage")
            return False

        try:
            # Create embedding
            text = f"{exception_type} {severity} {exception_details}"
            embedding = await self._create_embedding(text)

            # Store in vector database
            conn = self._get_connection()
            if not conn:
                return False

            with conn.cursor() as cur:
                cur.execute("""
                    INSERT INTO exception_case_embeddings 
                    (case_id, exception_type, exception_details, severity, 
                     resolution_strategy, embedding, tenant_id)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    ON CONFLICT (case_id) DO UPDATE SET
                        exception_type = EXCLUDED.exception_type,
                        exception_details = EXCLUDED.exception_details,
                        severity = EXCLUDED.severity,
                        resolution_strategy = EXCLUDED.resolution_strategy,
                        embedding = EXCLUDED.embedding,
                        tenant_id = EXCLUDED.tenant_id;
                """, (
                    case_id,
                    exception_type,
                    exception_details,
                    severity,
                    resolution_strategy,
                    embedding.tolist(),
                    tenant_id
                ))
                conn.commit()

            logger.info("Stored case embedding", case_id=case_id)
            return True

        except Exception as e:
            logger.error("Failed to store case embedding", case_id=case_id, error=str(e))
            return False

    async def _create_embedding(self, text: str) -> np.ndarray:
        """
        Create embedding vector for text.
        
        Args:
            text: Input text
            
        Returns:
            Embedding vector
        """
        # Run in thread pool to avoid blocking
        loop = asyncio.get_event_loop()
        embedding = await loop.run_in_executor(
            None,
            self.model.encode,
            text
        )
        return embedding

    def __del__(self):
        """Cleanup database connection."""
        if self.conn and not self.conn.closed:
            self.conn.close()

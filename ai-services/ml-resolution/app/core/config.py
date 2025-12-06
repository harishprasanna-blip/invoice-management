"""
Configuration settings for ML Resolution Service.
Uses Pydantic Settings for environment variable management.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional
import os


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Azure OpenAI Configuration
    AZURE_OPENAI_ENDPOINT: str = os.getenv("AZURE_OPENAI_ENDPOINT", "")
    AZURE_OPENAI_API_KEY: str = os.getenv("AZURE_OPENAI_API_KEY", "")
    AZURE_OPENAI_DEPLOYMENT_NAME: str = os.getenv("AZURE_OPENAI_DEPLOYMENT_NAME", "gpt-4")
    AZURE_OPENAI_API_VERSION: str = "2024-02-01"
    AZURE_OPENAI_TEMPERATURE: float = 0.3  # Lower for more deterministic outputs

    # Vector Database Configuration (pgvector)
    VECTOR_DB_URL: str = os.getenv("VECTOR_DB_URL", "postgresql://vector_user:vector_secure_password_123@localhost:5433/vector_db")
    
    @property
    def VECTOR_DB_HOST(self) -> str:
        """Extract host from database URL."""
        if "@" in self.VECTOR_DB_URL:
            return self.VECTOR_DB_URL.split("@")[1].split("/")[0].split(":")[0]
        return "localhost"

    # Main Database Configuration
    DATABASE_URL: str = os.getenv("DATABASE_URL", "postgresql://invoice_app:invoice_secure_password_123@localhost:5432/invoice_management_prod")

    # Embedding Model
    EMBEDDING_MODEL: str = "all-MiniLM-L6-v2"  # sentence-transformers model
    EMBEDDING_DIMENSION: int = 384

    # ML Configuration
    SIMILARITY_THRESHOLD: float = 0.75  # Minimum similarity score for recommendations
    TOP_K_SIMILAR_CASES: int = 5
    AUTO_RESOLUTION_CONFIDENCE_THRESHOLD: float = 0.85

    # Service Configuration
    SERVICE_NAME: str = "ml-resolution-service"
    LOG_LEVEL: str = "INFO"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True
    )


# Global settings instance
settings = Settings()

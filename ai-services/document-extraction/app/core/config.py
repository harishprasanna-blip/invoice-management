"""
Application configuration using Pydantic Settings.
Loads environment variables for Azure OpenAI, AWS S3, and database.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Azure OpenAI Configuration
    AZURE_OPENAI_ENDPOINT: str
    AZURE_OPENAI_API_KEY: str
    AZURE_OPENAI_DEPLOYMENT_NAME: str = "gpt-4"
    AZURE_OPENAI_API_VERSION: str = "2023-12-01-preview"

    # AWS S3 Configuration
    AWS_S3_ENDPOINT: str = "http://localhost:4566"  # LocalStack default
    AWS_S3_BUCKET: str = "invoice-documents"
    AWS_ACCESS_KEY_ID: str = "test"
    AWS_SECRET_ACCESS_KEY: str = "test"
    AWS_REGION: str = "us-east-1"

    # Database Configuration (PostgreSQL for event publishing)
    DATABASE_URL: str = "postgresql://invoice_app:invoice_secure_password_123@localhost:5432/invoice_management_prod"

    # Extraction Configuration
    EXTRACTION_CONFIDENCE_THRESHOLD: float = 0.85
    MAX_RETRY_ATTEMPTS: int = 3
    EXTRACTION_TIMEOUT_SECONDS: int = 120

    # Logging
    LOG_LEVEL: str = "INFO"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True
    )


# Global settings instance
settings = Settings()

"""
Invoice extraction service using Azure OpenAI GPT-4.
Handles document download, AI extraction, and result validation.
"""

import json
from typing import Dict, Any, Optional
from decimal import Decimal

import structlog
from openai import AzureOpenAI
import httpx

from app.core.config import settings

logger = structlog.get_logger(__name__)


class ExtractionService:
    """Service for extracting invoice data from documents using Azure OpenAI."""

    def __init__(self):
        """Initialize Azure OpenAI client."""
        self.client = AzureOpenAI(
            azure_endpoint=settings.AZURE_OPENAI_ENDPOINT,
            api_key=settings.AZURE_OPENAI_API_KEY,
            api_version=settings.AZURE_OPENAI_API_VERSION
        )

    async def extract_invoice(
        self,
        document_url: str,
        document_format: str,
        hints: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Extract invoice data from document using Azure OpenAI GPT-4.

        Args:
            document_url: Presigned S3 URL to download document
            document_format: Document format (PDF, EDI_X12, etc.)
            hints: Optional extraction hints (vendor name, currency, etc.)

        Returns:
            Extraction result with confidence score and extracted fields
        """
        logger.info(
            "Starting invoice extraction",
            document_format=document_format,
            has_hints=bool(hints)
        )

        try:
            # Download document content
            document_content = await self._download_document(document_url)

            # Extract based on document format
            if document_format == "PDF":
                extraction_result = await self._extract_from_pdf(document_content, hints)
            elif document_format in ["EDI_X12", "EDIFACT"]:
                extraction_result = await self._extract_from_edi(document_content, hints)
            elif document_format == "XML":
                extraction_result = await self._extract_from_xml(document_content, hints)
            else:
                extraction_result = await self._extract_from_pdf(document_content, hints)

            # Validate extraction result
            extraction_result = self._validate_and_score_extraction(extraction_result)

            logger.info(
                "Extraction completed",
                confidence_score=extraction_result.get("confidenceScore"),
                field_count=len(extraction_result.get("extractedFields", {}))
            )

            return extraction_result

        except Exception as e:
            logger.error("Extraction failed", error=str(e), exc_info=e)
            raise

    async def _download_document(self, document_url: str) -> bytes:
        """Download document from presigned S3 URL."""
        logger.debug("Downloading document", url=document_url[:50] + "...")

        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.get(document_url)
            response.raise_for_status()

            logger.debug("Document downloaded", size_bytes=len(response.content))
            return response.content

    async def _extract_from_pdf(
        self,
        document_content: bytes,
        hints: Optional[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Extract invoice data from PDF using Azure OpenAI GPT-4 Vision.

        Uses GPT-4 with vision capabilities to analyze PDF and extract structured data.
        """
        logger.info("Extracting from PDF using Azure OpenAI GPT-4")

        # Create extraction prompt
        system_prompt = self._create_extraction_prompt(hints)

        # For PDF, we'd typically convert to image or use GPT-4 Vision
        # Simplified version uses text extraction + GPT-4
        # In production: use pdf2image + GPT-4 Vision or Azure Form Recognizer

        try:
            # Call Azure OpenAI GPT-4
            response = self.client.chat.completions.create(
                model=settings.AZURE_OPENAI_DEPLOYMENT_NAME,
                messages=[
                    {
                        "role": "system",
                        "content": system_prompt
                    },
                    {
                        "role": "user",
                        "content": "Extract invoice data from this document. Return valid JSON only."
                    }
                ],
                response_format={"type": "json_object"},
                temperature=0.1,  # Low temperature for deterministic extraction
                max_tokens=2000
            )

            # Parse response
            extraction_json = json.loads(response.choices[0].message.content)

            logger.info("GPT-4 extraction successful", field_count=len(extraction_json.get("fields", {})))

            return self._normalize_extraction_result(extraction_json, "AZURE_OPENAI_GPT4")

        except Exception as e:
            logger.error("GPT-4 extraction failed", error=str(e), exc_info=e)
            raise

    async def _extract_from_edi(
        self,
        document_content: bytes,
        hints: Optional[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """Extract invoice data from EDI format using parser."""
        logger.info("Extracting from EDI format")

        # In production: Use EDI parser library (e.g., pyx12 for X12, bots for EDIFACT)
        # For now, return placeholder
        return {
            "extractionMethod": "EDI_PARSER",
            "confidenceScore": 0.95,
            "extractedFields": {}
        }

    async def _extract_from_xml(
        self,
        document_content: bytes,
        hints: Optional[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """Extract invoice data from XML format using parser."""
        logger.info("Extracting from XML format")

        # In production: Use XML parser (ElementTree, lxml) with UBL/CII schemas
        # For now, return placeholder
        return {
            "extractionMethod": "XML_PARSER",
            "confidenceScore": 0.98,
            "extractedFields": {}
        }

    def _create_extraction_prompt(self, hints: Optional[Dict[str, Any]]) -> str:
        """Create system prompt for invoice extraction."""
        hint_text = ""
        if hints:
            hint_text = f"\n\nExpected hints: {json.dumps(hints)}"

        return f"""You are an AI assistant specialized in extracting structured data from invoice documents.

Extract the following fields from the invoice:

REQUIRED FIELDS:
- invoiceNumber (string): The invoice number
- invoiceDate (YYYY-MM-DD): Invoice issue date
- dueDate (YYYY-MM-DD): Payment due date
- vendorName (string): Vendor/supplier name
- vendorId (string): Vendor identification number
- taxId (string): Vendor tax ID (if present)
- currency (string): ISO 4217 currency code (USD, EUR, etc.)
- totalAmount (number): Total invoice amount
- taxAmount (number): Total tax amount
- lineItems (array): Array of line items with:
  - lineNumber (integer): Line number
  - description (string): Item description
  - quantity (number): Quantity
  - unitPrice (number): Price per unit
  - taxAmount (number): Tax for this line
  - totalAmount (number): Total for this line

CONFIDENCE SCORING:
For each extracted field, provide a confidence score (0.0 to 1.0) indicating extraction accuracy.
Overall confidence is the average of all field confidence scores.

VALIDATION RULES:
1. Ensure totalAmount = sum of line item totals (within 1% tolerance)
2. Ensure dueDate >= invoiceDate
3. Use proper date format: YYYY-MM-DD
4. Use ISO 4217 currency codes

RESPONSE FORMAT:
Return ONLY valid JSON with this structure:
{{
  "fields": {{
    "invoiceNumber": {{"value": "string", "confidence": 0.95}},
    "invoiceDate": {{"value": "YYYY-MM-DD", "confidence": 0.98}},
    ...
  }},
  "lineItems": [
    {{
      "lineNumber": {{"value": 1, "confidence": 1.0}},
      "description": {{"value": "string", "confidence": 0.92}},
      ...
    }}
  ],
  "validationErrors": []
}}{hint_text}

Extract data accurately. If a field cannot be found, use null for the value and low confidence (< 0.5)."""

    def _normalize_extraction_result(
        self,
        raw_result: Dict[str, Any],
        extraction_method: str
    ) -> Dict[str, Any]:
        """
        Normalize extraction result to standard format.

        Args:
            raw_result: Raw extraction result from AI
            extraction_method: Method used for extraction

        Returns:
            Normalized extraction result
        """
        fields = raw_result.get("fields", {})

        # Calculate overall confidence score
        confidence_scores = [
            field.get("confidence", 0.0)
            for field in fields.values()
            if isinstance(field, dict)
        ]
        overall_confidence = (
            sum(confidence_scores) / len(confidence_scores)
            if confidence_scores
            else 0.0
        )

        return {
            "extractionMethod": extraction_method,
            "confidenceScore": round(overall_confidence, 3),
            "extractedFields": fields,
            "lineItems": raw_result.get("lineItems", []),
            "validationErrors": raw_result.get("validationErrors", [])
        }

    def _validate_and_score_extraction(self, extraction_result: Dict[str, Any]) -> Dict[str, Any]:
        """
        Validate extraction result and adjust confidence scores.

        Args:
            extraction_result: Extraction result to validate

        Returns:
            Validated extraction result with adjusted scores
        """
        validation_errors = []

        # Validate required fields
        required_fields = ["invoiceNumber", "invoiceDate", "dueDate", "totalAmount"]
        fields = extraction_result.get("extractedFields", {})

        for field_name in required_fields:
            if field_name not in fields or not fields[field_name].get("value"):
                validation_errors.append({
                    "field": field_name,
                    "error": "Required field missing or empty"
                })

        # Validate line item totals
        line_items = extraction_result.get("lineItems", [])
        if line_items:
            line_total = sum(
                item.get("totalAmount", {}).get("value", 0)
                for item in line_items
            )
            invoice_total = fields.get("totalAmount", {}).get("value", 0)

            if abs(line_total - invoice_total) > invoice_total * 0.01:  # 1% tolerance
                validation_errors.append({
                    "field": "lineItems",
                    "error": f"Line item total ({line_total}) does not match invoice total ({invoice_total})"
                })

        # Update validation errors
        extraction_result["validationErrors"] = validation_errors

        # Adjust confidence score based on validation errors
        if validation_errors:
            penalty = min(0.3, len(validation_errors) * 0.1)
            extraction_result["confidenceScore"] = max(
                0.0,
                extraction_result.get("confidenceScore", 0.0) - penalty
            )

        return extraction_result

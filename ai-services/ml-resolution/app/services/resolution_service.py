"""
Resolution recommendation service using Azure OpenAI GPT-4.
Provides intelligent exception resolution recommendations.
"""

import json
from typing import Dict, Any, List
from uuid import UUID
from decimal import Decimal
import structlog

from openai import AzureOpenAI
from app.core.config import settings

logger = structlog.get_logger(__name__)


class ResolutionService:
    """Service for generating ML-powered resolution recommendations."""

    def __init__(self):
        """Initialize Azure OpenAI client."""
        self.client = None
        if settings.AZURE_OPENAI_ENDPOINT and settings.AZURE_OPENAI_API_KEY:
            self.client = AzureOpenAI(
                api_key=settings.AZURE_OPENAI_API_KEY,
                api_version=settings.AZURE_OPENAI_API_VERSION,
                azure_endpoint=settings.AZURE_OPENAI_ENDPOINT
            )
        else:
            logger.warning("Azure OpenAI not configured, using fallback mode")

    async def get_recommendation(
        self,
        case_id: UUID,
        exception_type: str,
        severity: str,
        exception_details: str,
        source_context: str,
        similar_cases: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """
        Generate resolution recommendation using GPT-4.
        
        Args:
            case_id: Exception case ID
            exception_type: Type of exception
            severity: Exception severity
            exception_details: Detailed exception information
            source_context: Source bounded context
            similar_cases: List of similar historical cases
            
        Returns:
            Dictionary with recommendation details
        """
        if not self.client:
            return self._get_fallback_recommendation(exception_type, severity)

        try:
            # Build context from similar cases
            similar_cases_context = self._build_similar_cases_context(similar_cases)

            # Create prompt for GPT-4
            system_prompt = self._get_system_prompt()
            user_prompt = self._build_user_prompt(
                exception_type=exception_type,
                severity=severity,
                exception_details=exception_details,
                source_context=source_context,
                similar_cases_context=similar_cases_context
            )

            # Call GPT-4
            response = self.client.chat.completions.create(
                model=settings.AZURE_OPENAI_DEPLOYMENT_NAME,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                temperature=settings.AZURE_OPENAI_TEMPERATURE,
                max_tokens=1000,
                response_format={"type": "json_object"}
            )

            # Parse response
            result = json.loads(response.choices[0].message.content)
            
            # Validate and normalize response
            return self._validate_recommendation(result, exception_type, severity)

        except Exception as e:
            logger.error("GPT-4 recommendation failed", error=str(e))
            return self._get_fallback_recommendation(exception_type, severity)

    def _get_system_prompt(self) -> str:
        """Get system prompt for GPT-4."""
        return """You are an expert invoice exception resolution assistant for an enterprise AP automation system.
Your task is to analyze exception cases and recommend the best resolution strategy.

Available resolution strategies:
1. APPROVE_AND_POST - Approve invoice and post to ERP
2. REQUEST_VENDOR_CORRECTION - Request vendor to correct and resubmit
3. ADJUST_AND_APPROVE - Make adjustment and approve
4. HOLD_PENDING_INVESTIGATION - Hold for further investigation
5. ESCALATE_TO_VENDOR_MANAGEMENT - Escalate to vendor management team
6. ESCALATE_TO_FINANCE - Escalate to finance team
7. ROUTE_TO_MANUAL_PROCESSING - Send to manual processing queue

Consider:
- Exception type and severity
- Historical similar cases and their outcomes
- Business impact and urgency
- Automation potential

Respond in JSON format with:
{
  "strategy": "<STRATEGY_NAME>",
  "confidence": <0.0-1.0>,
  "reasoning": "<explanation>",
  "automatable": <true/false>,
  "alternative_strategies": ["<strategy1>", "<strategy2>"],
  "recommended_actions": ["<action1>", "<action2>"]
}"""

    def _build_user_prompt(
        self,
        exception_type: str,
        severity: str,
        exception_details: str,
        source_context: str,
        similar_cases_context: str
    ) -> str:
        """Build user prompt with case details."""
        return f"""Analyze this exception case and recommend the best resolution strategy:

**Exception Details:**
- Type: {exception_type}
- Severity: {severity}
- Context: {source_context}
- Details: {exception_details}

**Similar Historical Cases:**
{similar_cases_context}

Provide your recommendation in JSON format."""

    def _build_similar_cases_context(self, similar_cases: List[Dict[str, Any]]) -> str:
        """Build context string from similar cases."""
        if not similar_cases:
            return "No similar historical cases found."

        context_lines = []
        for i, case in enumerate(similar_cases[:3], 1):  # Top 3 cases
            context_lines.append(
                f"{i}. Type: {case.get('exception_type', 'N/A')}, "
                f"Resolution: {case.get('resolution_strategy', 'N/A')}, "
                f"Similarity: {case.get('similarity', 0):.2f}"
            )

        return "\n".join(context_lines)

    def _validate_recommendation(
        self,
        result: Dict[str, Any],
        exception_type: str,
        severity: str
    ) -> Dict[str, Any]:
        """Validate and normalize GPT-4 recommendation."""
        valid_strategies = {
            "APPROVE_AND_POST",
            "REQUEST_VENDOR_CORRECTION",
            "ADJUST_AND_APPROVE",
            "HOLD_PENDING_INVESTIGATION",
            "ESCALATE_TO_VENDOR_MANAGEMENT",
            "ESCALATE_TO_FINANCE",
            "ROUTE_TO_MANUAL_PROCESSING"
        }

        strategy = result.get("strategy", "HOLD_PENDING_INVESTIGATION")
        if strategy not in valid_strategies:
            strategy = "HOLD_PENDING_INVESTIGATION"

        confidence = float(result.get("confidence", 0.5))
        confidence = max(0.0, min(1.0, confidence))  # Clamp to [0, 1]

        # Determine if automatable based on confidence and strategy
        automatable = (
            confidence >= settings.AUTO_RESOLUTION_CONFIDENCE_THRESHOLD
            and strategy in ["APPROVE_AND_POST", "REQUEST_VENDOR_CORRECTION"]
        )

        return {
            "strategy": strategy,
            "confidence": confidence,
            "reasoning": result.get("reasoning", "AI-generated recommendation"),
            "automatable": automatable,
            "model_version": f"gpt-4-{settings.AZURE_OPENAI_API_VERSION}",
            "alternative_strategies": result.get("alternative_strategies", []),
            "recommended_actions": result.get("recommended_actions", [])
        }

    def _get_fallback_recommendation(
        self,
        exception_type: str,
        severity: str
    ) -> Dict[str, Any]:
        """Get rule-based fallback recommendation when GPT-4 unavailable."""
        # Simple rule-based logic
        strategy_map = {
            "MATCHING_MISMATCH": "HOLD_PENDING_INVESTIGATION",
            "COMPLIANCE_VIOLATION": "ESCALATE_TO_VENDOR_MANAGEMENT",
            "EXTRACTION_FAILED": "REQUEST_VENDOR_CORRECTION",
            "PAYMENT_FAILED": "HOLD_PENDING_INVESTIGATION",
            "DUPLICATE_INVOICE": "HOLD_PENDING_INVESTIGATION",
            "ROUTING_ERROR": "ROUTE_TO_MANUAL_PROCESSING"
        }

        strategy = strategy_map.get(exception_type, "HOLD_PENDING_INVESTIGATION")

        # Adjust confidence based on severity
        confidence_map = {
            "CRITICAL": 0.45,
            "HIGH": 0.50,
            "MEDIUM": 0.55,
            "LOW": 0.60
        }
        confidence = confidence_map.get(severity, 0.50)

        return {
            "strategy": strategy,
            "confidence": confidence,
            "reasoning": "Rule-based fallback recommendation (GPT-4 unavailable)",
            "automatable": False,
            "model_version": "fallback-rules-v1.0",
            "alternative_strategies": [],
            "recommended_actions": []
        }

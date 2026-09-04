"""Gemini-backed, safety constrained visual screening for plant photographs."""

from __future__ import annotations

import base64
import json
import re
from pathlib import Path
from typing import Any

import requests


class PlantVisionService:
    """Calls Gemini using a key which never leaves the Raspberry Pi."""

    API_URL = (
        "https://generativelanguage.googleapis.com/v1beta/"
        "models/gemini-3.1-flash-lite:generateContent"
    )
    MAX_IMAGE_BYTES = 5 * 1024 * 1024

    def __init__(self, key_path: str = "vision_api_key.txt") -> None:
        self._key_path = Path(key_path)

    def configured(self) -> bool:
        return self._key_path.is_file() and bool(self._read_key())

    def analyze(self, image_base64: str, mime_type: str, context: dict[str, Any]) -> dict[str, Any]:
        key = self._read_key()
        if not key:
            raise RuntimeError("VISION_NOT_CONFIGURED")
        if mime_type not in {"image/jpeg", "image/png", "image/webp"}:
            raise ValueError("UNSUPPORTED_IMAGE_TYPE")
        try:
            image = base64.b64decode(image_base64, validate=True)
        except Exception as error:
            raise ValueError("INVALID_IMAGE") from error
        if not image or len(image) > self.MAX_IMAGE_BYTES:
            raise ValueError("IMAGE_TOO_LARGE")

        payload = {
            "generationConfig": {"temperature": 0.2, "responseMimeType": "application/json"},
            "contents": [{"role": "user", "parts": [
                {"text": self._prompt(context)},
                {"inlineData": {"mimeType": mime_type, "data": image_base64}},
            ]}],
        }
        response = requests.post(
            self.API_URL,
            headers={"x-goog-api-key": key},
            json=payload,
            timeout=45,
        )
        if not response.ok:
            self._raise_provider_error(response)
        try:
            text = response.json()["candidates"][0]["content"]["parts"][0]["text"]
            result = json.loads(text)
        except Exception as error:
            raise RuntimeError("VISION_INVALID_RESPONSE") from error
        return self._normalize(result)

    def advise_organic(self, context: dict[str, Any]) -> dict[str, Any]:
        """Returns guarded organic product-profile guidance, never an application order."""
        key = self._read_key()
        if not key:
            raise RuntimeError("VISION_NOT_CONFIGURED")
        if not isinstance(context, dict):
            raise ValueError("INVALID_CONTEXT")

        payload = {
            "generationConfig": {
                "temperature": 0.15,
                "responseMimeType": "application/json",
            },
            "contents": [{"role": "user", "parts": [
                {"text": self._organic_prompt(context)},
            ]}],
        }
        response = requests.post(
            self.API_URL,
            headers={"x-goog-api-key": key},
            json=payload,
            timeout=45,
        )
        if not response.ok:
            self._raise_provider_error(response)
        try:
            text = response.json()["candidates"][0]["content"]["parts"][0]["text"]
            result = json.loads(text)
        except Exception as error:
            raise RuntimeError("VISION_INVALID_RESPONSE") from error
        return self._normalize_organic(result)

    @staticmethod
    def _raise_provider_error(response: requests.Response) -> None:
        """Expose a safe reason code, never the provider body or an API key."""
        try:
            body = response.json()
        except (ValueError, TypeError):
            body = {}
        error = body.get("error", {}) if isinstance(body, dict) else {}
        if not isinstance(error, dict):
            error = {}
        details = error.get("details", [])
        reasons = {
            str(detail.get("reason", ""))
            for detail in (details if isinstance(details, list) else [])
            if isinstance(detail, dict)
        }
        if reasons.intersection({"API_KEY_INVALID", "API_KEY_EXPIRED"}):
            raise RuntimeError("VISION_API_KEY_INVALID")
        raise RuntimeError(f"VISION_PROVIDER_ERROR:{response.status_code}")

    def _read_key(self) -> str:
        try:
            return self._key_path.read_text(encoding="utf-8").strip()
        except OSError:
            return ""

    @staticmethod
    def _prompt(context: dict[str, Any]) -> str:
        safe_context = json.dumps(context, ensure_ascii=False)
        analysis_goal = (
            "growth_status"
            if context.get("analysis_goal") == "growth_status"
            else "health_screening"
        )
        if analysis_goal == "growth_status":
            analysis_focus = (
                "The primary goal is plant growth assessment, not disease screening. "
                "Assess visible vigor, likely development stage, leaf color and density, "
                "stem and internode balance, flowering or fruiting progress, and visible "
                "growth stress. Do not infer exact plant age or growth rate from a single "
                "photo. Use possible_causes for factors that may be limiting growth and "
                "next_steps for safe observation and same-angle photo follow-up. Mention "
                "disease only when a clear red flag is visible. "
            )
        else:
            analysis_focus = (
                "The primary goal is a cautious visual health screening based on the "
                "selected symptoms. "
            )
        return (
            "You are AVORA Visual Plant Doctor. Analyze the supplied plant photo "
            "together with the garden context. This is agricultural decision support, "
            "not a diagnosis. Never recommend pesticides, dosage, or automatic irrigation. "
            + analysis_focus
            + "If photo quality is insufficient, say so clearly. Return only JSON with these keys: "
            "is_plant_photo (boolean), title (Turkish short string), confidence (integer 0-100), "
            "urgency (Düşük|Orta|Yüksek), visual_findings (Turkish string), "
            "possible_causes (array of at most 3 Turkish strings), next_steps (array of at most 4 Turkish strings), "
            "red_flags (array of Turkish strings), disclaimer (Turkish string), "
            "growth_score (integer 0-100 only for growth_status, otherwise -1), "
            "growth_stage (Turkish short string only for growth_status, otherwise empty), "
            "growth_signals (array of at most 4 Turkish strings only for growth_status). "
            "The growth_score is a cautious visible-vigor indicator from this photo, not "
            "an exact plant age or measured growth rate. "
            "Do not claim a disease with certainty. Garden context: " + safe_context
        )

    @staticmethod
    def _organic_prompt(context: dict[str, Any]) -> str:
        allowed_context = {
            "plant_type": str(context.get("plant_type", ""))[:80],
            "growth_stage": str(context.get("growth_stage", ""))[:40],
            "application_method": str(
                context.get("application_method", "")
            )[:40],
            "organic_only": bool(context.get("organic_only", True)),
            "deterministic_result": str(
                context.get("deterministic_result", "")
            )[:80],
        }
        safe_context = json.dumps(allowed_context, ensure_ascii=False)
        return (
            "You are AVORA Organic Fertilizer Advisor. AVORA's deterministic safety "
            "engine has found no enabled stored product that is both compatible with "
            "the current growth stage and explicitly allowed in organic farming. "
            "Provide selection guidance only. Never recommend a conventional or "
            "synthetic fertilizer, pesticide, exact dose, tank mixture, automatic "
            "application, or an unverified certification claim. Do not invent brand "
            "or product names. Describe at most three generic organic-compatible "
            "product profiles. Tell the user to verify the official label, organic "
            "farming authorization, crop, stage, application method, harvest interval, "
            "and local agricultural advice. Return only JSON with these keys: headline "
            "(short Turkish string), rationale (Turkish string), recommendations (array "
            "of at most 3 objects with product_type, purpose, selection_criteria, "
            "application_method), cautions (array of at most 4 Turkish strings), "
            "disclaimer (Turkish string). If context is insufficient, return an empty "
            "recommendations array and explain what is missing. Garden context: "
            + safe_context
        )

    @staticmethod
    def _normalize(value: dict[str, Any]) -> dict[str, Any]:
        confidence = value.get("confidence", 0)
        try:
            confidence = max(0, min(100, int(confidence)))
        except (TypeError, ValueError):
            confidence = 0
        urgency = value.get("urgency", "Düşük")
        if urgency not in {"Düşük", "Orta", "Yüksek"}:
            urgency = "Düşük"
        growth_score = value.get("growth_score", -1)
        try:
            growth_score = int(growth_score)
            if growth_score < 0 or growth_score > 100:
                growth_score = -1
        except (TypeError, ValueError):
            growth_score = -1
        if not bool(value.get("is_plant_photo", False)):
            growth_score = -1
        growth_signals = value.get("growth_signals", [])
        if not isinstance(growth_signals, list):
            growth_signals = []
        return {
            "is_plant_photo": bool(value.get("is_plant_photo", False)),
            "title": str(value.get("title", "Görsel ön değerlendirme"))[:120],
            "confidence": confidence,
            "urgency": urgency,
            "visual_findings": str(value.get("visual_findings", ""))[:1200],
            "possible_causes": [str(x)[:220] for x in value.get("possible_causes", [])[:3]],
            "next_steps": [str(x)[:220] for x in value.get("next_steps", [])[:4]],
            "red_flags": [str(x)[:220] for x in value.get("red_flags", [])[:3]],
            "disclaimer": str(value.get("disclaimer", "Bu sonuç kesin teşhis değildir."))[:300],
            "growth_score": growth_score,
            "growth_stage": str(value.get("growth_stage", ""))[:120],
            "growth_signals": [str(x)[:220] for x in growth_signals[:4]],
        }
    @staticmethod
    def _unsafe_organic_advice(value: str) -> bool:
        normalized = value.casefold()
        forbidden_terms = (
            "kimyasal", "sentetik", "conventional", "synthetic",
            "üre", "urea", "amonyum nitrat", "kalsiyum nitrat",
            "npk", "20-20-20", "10-5-40", "10.5.40",
        )
        if any(term in normalized for term in forbidden_terms):
            return True
        return bool(re.search(
            r"\b\d+(?:[.,]\d+)?\s*(?:kg|g|mg|ml|l)\s*(?:/|per)",
            normalized,
        ))

    @staticmethod
    def _normalize_organic(value: dict[str, Any]) -> dict[str, Any]:
        normalized_recommendations: list[dict[str, str]] = []
        recommendations = value.get("recommendations", [])
        if isinstance(recommendations, list):
            for item in recommendations[:3]:
                if not isinstance(item, dict):
                    continue
                product_type = str(item.get("product_type", ""))[:160].strip()
                purpose = str(item.get("purpose", ""))[:240].strip()
                selection = str(item.get("selection_criteria", ""))[:300].strip()
                method = str(item.get("application_method", ""))[:160].strip()
                combined = " ".join((product_type, purpose, selection, method))
                if product_type and not PlantVisionService._unsafe_organic_advice(
                    combined
                ):
                    normalized_recommendations.append({
                        "product_type": product_type,
                        "purpose": purpose,
                        "selection_criteria": selection,
                        "application_method": method,
                    })
        cautions = value.get("cautions", [])
        if not isinstance(cautions, list):
            cautions = []
        return {
            "headline": str(value.get(
                "headline", "Organik alternatif seçimi"
            ))[:120],
            "rationale": str(value.get("rationale", ""))[:800],
            "recommendations": normalized_recommendations,
            "cautions": [str(item)[:240] for item in cautions[:4]],
            "disclaimer": str(value.get(
                "disclaimer",
                "Ürün etiketi ve organik tarım uygunluğu doğrulanmadan uygulama yapmayın.",
            ))[:400],
        }

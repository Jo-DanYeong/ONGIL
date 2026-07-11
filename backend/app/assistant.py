from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from typing import Any

import httpx


OPEN_PICKUP_ZONES = "OPEN_PICKUP_ZONES"
OPEN_SAFE_ROUTES = "OPEN_SAFE_ROUTES"
REQUEST_LOCATION_SHARE = "REQUEST_LOCATION_SHARE"
NONE = "NONE"
ACTIONS = {OPEN_PICKUP_ZONES, OPEN_SAFE_ROUTES, REQUEST_LOCATION_SHARE, NONE}


@dataclass(frozen=True)
class AssistantResult:
    reply: str
    action: str
    requires_confirmation: bool
    classifier: str


class AssistantService:
    """Routes a return-home utterance to an app action; never calculates routes or risk."""

    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.api_key = (api_key if api_key is not None else os.getenv("GEMINI_API_KEY", "")).strip()
        self.model = model or os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
        self.transport = transport

    async def chat(
        self,
        message: str,
        role: str,
        time_bucket: str,
        facts: dict[str, Any] | None = None,
    ) -> AssistantResult:
        facts_reply = _explain_calculated_facts(message, role, facts or {})
        if facts_reply:
            return AssistantResult(facts_reply, NONE, False, "SERVER_FACTS")

        rule_action = classify_with_rules(message)
        action = rule_action
        reply = ""
        classifier = "RULES"
        if self.api_key:
            signals = privacy_safe_signals(message)
            if not signals:
                signals = ["general_guidance"]
            signals.append(f"server_rule_action:{rule_action}")
            try:
                model_action, model_reply = await self._classify_signals(signals, role, time_bucket)
                action = rule_action if rule_action != NONE else model_action
                reply = safe_model_reply(model_reply, action, role, time_bucket)
                classifier = "GEMINI_SIGNALS"
            except (httpx.HTTPError, KeyError, IndexError, json.JSONDecodeError, ValueError):
                action = rule_action
                classifier = "RULES_FALLBACK"

        return AssistantResult(
            reply=reply or reply_for(action, role, time_bucket),
            action=action,
            requires_confirmation=action == REQUEST_LOCATION_SHARE,
            classifier=classifier,
        )

    async def _classify_signals(
        self, signals: list[str], role: str, time_bucket: str
    ) -> tuple[str, str]:
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"{self.model}:generateContent"
        )
        schema = {
            "type": "object",
            "properties": {
                "action": {"type": "string", "enum": sorted(ACTIONS)},
                "reply": {
                    "type": "string",
                    "description": "One short warm Korean sentence spoken by the ON:GIL pet Oni.",
                },
            },
            "required": ["action", "reply"],
            "additionalProperties": False,
        }
        payload = {
            "contents": [{
                "parts": [{
                    "text": (
                        f"role={role}; timeBucket={time_bucket}; "
                        f"privacy-safe intent signals={','.join(signals)}"
                    )
                }]
            }],
            "systemInstruction": {"parts": [{"text": (
                "You are Oni, ON:GIL's small warm return-home pet. Classify one app action and "
                "write one short Korean reply appropriate for the role and time. Never calculate "
                "or invent a route, travel time, congestion, location, or safety score. Never say "
                "a route is completely or 100% safe. For location sharing, say user confirmation "
                "is required. Return NONE for ordinary guidance."
            )}]},
            "generationConfig": {
                "responseFormat": {
                    "text": {"mimeType": "application/json", "schema": schema}
                }
            },
        }
        async with httpx.AsyncClient(timeout=5, transport=self.transport) as client:
            response = await client.post(
                url,
                headers={"x-goog-api-key": self.api_key, "Content-Type": "application/json"},
                json=payload,
            )
            response.raise_for_status()
        data = response.json()
        text = data["candidates"][0]["content"]["parts"][0]["text"]
        result = json.loads(text)
        action = result["action"]
        if action not in ACTIONS:
            raise ValueError("Gemini returned an unsupported action")
        return action, str(result.get("reply", ""))


def classify_with_rules(message: str) -> str:
    normalized = re.sub(r"\s+", "", message.lower())
    if re.search(r"(위치).*(공유|보여|알려|전송)|어디있는지.*(공유|보여)", normalized):
        return REQUEST_LOCATION_SHARE
    if any(token in normalized for token in (
        "혼자", "걸어가", "걸어서", "밤길", "무서", "밝은길", "안전한길", "안전경로",
    )):
        return OPEN_SAFE_ROUTES
    if any(token in normalized for token in (
        "픽업", "데리러", "태우러", "차로와", "차타고가", "마중와",
    )):
        return OPEN_PICKUP_ZONES
    return NONE


def privacy_safe_signals(message: str) -> list[str]:
    """Extract allow-listed semantics only; raw text and personal identifiers never leave the server."""
    normalized = message.lower()
    dictionary = {
        "pickup": ("픽업", "데리러", "태우러", "마중", "차 타고"),
        "solo": ("혼자", "걸어서", "걸어가"),
        "location_share": ("위치", "공유", "보여줘"),
        "night_fear": ("밤길", "무서", "불안"),
        "safe_route": ("밝은", "안전", "가로등", "편의점", "파출소"),
        "unavailable_or_late": ("못 와", "늦어", "안 와", "불가"),
    }
    return [signal for signal, tokens in dictionary.items() if any(token in normalized for token in tokens)]


def reply_for(action: str, role: str, time_bucket: str) -> str:
    is_parent = role == "parent"
    is_night = time_bucket == "night"
    if action == OPEN_PICKUP_ZONES:
        return (
            "학생과 만나기 편한 픽업존을 교통·거리·정차 여건 순으로 보여드릴게요."
            if is_parent else
            "가까우면서 안전하고 정차하기 좋은 픽업존을 보여줄게."
        )
    if action == OPEN_SAFE_ROUTES:
        if is_parent:
            return "학생이 이용할 밝은 길과 안전 거점 우선 경로를 보여드릴게요."
        return (
            "그럼 밝은 길과 안전 거점을 우선한 야간 안전 경로를 보여줄게."
            if is_night else
            "혼자 가는 상황에 맞춰 보행 환경을 우선한 경로를 보여줄게."
        )
    if action == REQUEST_LOCATION_SHARE:
        return (
            "위치 공유는 상대방 확인 후 시작돼요. 요청 화면을 열어드릴게요."
            if is_parent else
            "위치 공유는 네가 확인한 뒤에만 시작돼. 요청 화면을 열어줄게."
        )
    return (
        "귀가 상황을 알려주세요. 픽업존, 야간 안전 경로, 위치 공유를 도와드릴 수 있어요."
        if is_parent else
        "픽업인지 혼자 가는지 말해줘. 픽업존, 야간 안전 경로, 위치 공유를 도와줄게."
    )


def safe_model_reply(reply: str, action: str, role: str, time_bucket: str) -> str:
    normalized = reply.strip()
    forbidden = ("완전히 안전", "100%", "백퍼센트", "GPS", "위도", "경도")
    if not normalized or len(normalized) > 160 or any(token in normalized for token in forbidden):
        return reply_for(action, role, time_bucket)
    if re.search(r"\d", normalized):
        return reply_for(action, role, time_bucket)
    if action == REQUEST_LOCATION_SHARE and "확인" not in normalized:
        return reply_for(action, role, time_bucket)
    return normalized


def _explain_calculated_facts(message: str, role: str, facts: dict[str, Any]) -> str | None:
    if not facts or not any(token in message for token in ("왜", "몇 분", "혼잡", "안전 점수", "설명")):
        return None
    parts = []
    if facts.get("routeMinutes") is not None:
        parts.append(f"예상 이동 시간은 {facts['routeMinutes']}분")
    if facts.get("safetyScore") is not None:
        parts.append(f"계산된 야간 안전 점수는 {facts['safetyScore']}점")
    if facts.get("trafficLevel"):
        parts.append(f"현재 교통은 {facts['trafficLevel']}")
    if facts.get("pickupZoneName"):
        parts.append(f"추천 장소는 {facts['pickupZoneName']}")
    if not parts:
        return None
    ending = "입니다." if role == "parent" else "이야."
    return ", ".join(parts) + ending + " 실제 계산 결과를 쉽게 풀어 설명했어." if role != "parent" else ", ".join(parts) + ending

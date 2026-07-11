import asyncio
import json

import httpx
import pytest

from app.assistant import (
    AssistantService,
    NONE,
    OPEN_PICKUP_ZONES,
    OPEN_SAFE_ROUTES,
    REQUEST_LOCATION_SHARE,
)


@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("엄마가 데리러 온대", OPEN_PICKUP_ZONES),
        ("오늘 혼자 걸어서 갈 것 같아", OPEN_SAFE_ROUTES),
        ("부모님한테 위치 보여줘", REQUEST_LOCATION_SHARE),
        ("밤길이 무서워", OPEN_SAFE_ROUTES),
        ("안녕 온이야", NONE),
    ],
)
def test_routes_return_home_messages_to_fixed_actions(message, expected):
    result = asyncio.run(AssistantService(api_key="").chat(message, "student", "night"))
    assert result.action == expected


def test_location_share_always_requires_user_confirmation():
    result = asyncio.run(AssistantService(api_key="").chat(
        "부모님한테 위치 보여줘", "student", "night"
    ))
    assert result.action == REQUEST_LOCATION_SHARE
    assert result.requires_confirmation is True


def test_explains_only_server_calculated_facts():
    result = asyncio.run(AssistantService(api_key="").chat(
        "왜 이 경로야? 설명해줘",
        "student",
        "night",
        {"routeMinutes": 15, "safetyScore": 84, "trafficLevel": "원활"},
    ))
    assert result.action == NONE
    assert "15분" in result.reply
    assert "84점" in result.reply


def test_gemini_receives_signals_not_raw_personal_text():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["body"] = request.content.decode("utf-8")
        return httpx.Response(200, json={
            "candidates": [{
                "content": {"parts": [{"text": json.dumps({"action": OPEN_SAFE_ROUTES})}]}
            }]
        })

    service = AssistantService(
        api_key="server-only-key",
        transport=httpx.MockTransport(handler),
    )
    result = asyncio.run(service.chat(
        "불안해. 가족코드 123456, GPS 37.12345,127.12345, 홍길동",
        "student",
        "night",
    ))

    assert result.action == OPEN_SAFE_ROUTES
    assert result.classifier == "GEMINI_SIGNALS"
    assert "night_fear" in captured["body"]
    assert "123456" not in captured["body"]
    assert "37.12345" not in captured["body"]
    assert "홍길동" not in captured["body"]
    assert "server-only-key" not in captured["body"]


def test_server_rule_wins_while_gemini_writes_pet_reply():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={
            "candidates": [{
                "content": {"parts": [{"text": json.dumps({
                    "action": NONE,
                    "reply": "마중 오는 날이구나! 만나기 편한 픽업존을 같이 찾아볼게.",
                }, ensure_ascii=False)}]}
            }]
        })

    service = AssistantService(
        api_key="server-only-key",
        transport=httpx.MockTransport(handler),
    )
    result = asyncio.run(service.chat("엄마가 데리러 온대", "student", "night"))

    assert result.action == OPEN_PICKUP_ZONES
    assert result.reply.startswith("마중 오는 날이구나")
    assert result.classifier == "GEMINI_SIGNALS"


def test_unsafe_or_invented_gemini_reply_is_replaced():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={
            "candidates": [{
                "content": {"parts": [{"text": json.dumps({
                    "action": OPEN_SAFE_ROUTES,
                    "reply": "이 길은 100% 안전하고 12분이면 도착해.",
                }, ensure_ascii=False)}]}
            }]
        })

    result = asyncio.run(AssistantService(
        api_key="server-only-key",
        transport=httpx.MockTransport(handler),
    ).chat("밤길이 무서워", "student", "night"))

    assert "100%" not in result.reply
    assert "12분" not in result.reply
    assert "야간 안전 경로" in result.reply

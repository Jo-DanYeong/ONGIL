from fastapi.testclient import TestClient

from app.main import app, traffic_service


client = TestClient(app)


def test_health_does_not_expose_api_key():
    response = client.get("/health")
    assert response.status_code == 200
    assert "apiKey" not in response.json()


def test_missing_key_uses_demo_fallback(monkeypatch):
    monkeypatch.setattr(traffic_service.client, "api_key", "")
    response = client.get("/traffic/links/1220003800")
    assert response.status_code == 200
    assert response.json()["source"] in {"TOPIS", "MOCK_FALLBACK"}


def test_invalid_history_date_is_rejected():
    response = client.get("/traffic/volume-history/A-01?date=20261340&hour=22")
    assert response.status_code == 422


def test_assistant_chat_contract():
    response = client.post("/api/assistant/chat", json={
        "message": "오늘 혼자 걸어서 갈 것 같아",
        "role": "student",
        "timeBucket": "night",
    })
    assert response.status_code == 200
    assert response.json()["action"] == "OPEN_SAFE_ROUTES"
    assert "밝은 길" in response.json()["reply"]

from __future__ import annotations

from typing import Literal

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field
from dotenv import load_dotenv

from .assistant import AssistantService
from .pickup import PickupZoneService
from .topis import TrafficDataService, validate_hour, validate_ymd


load_dotenv()

app = FastAPI(
    title="온길 TOPIS Traffic API",
    version="1.0.0",
    description="TOPIS 실시간 소통·돌발·교통량 이력을 온길 픽업존/안전 경로에 연결합니다.",
)
traffic_service = TrafficDataService()
pickup_zone_service = PickupZoneService(traffic_service)
assistant_service = AssistantService()


class LinkAssessmentRequest(BaseModel):
    linkIds: list[str] = Field(min_length=1, max_length=20)


class PickupCandidateRequest(BaseModel):
    id: str = Field(min_length=1, max_length=40)
    name: str = Field(min_length=1, max_length=60)
    linkId: str = Field(min_length=1, max_length=30)
    distanceMeters: int = Field(ge=0, le=5000)
    lightingCoverage: float = Field(ge=0, le=1)
    safetySpotCount: int = Field(ge=0, le=20)
    hasSidewalk: bool
    hasCrosswalk: bool
    legalStopAllowed: bool
    stoppingSuitability: float = Field(ge=0, le=1)
    waitingVehicles: int = Field(ge=0, le=100)
    capacity: int = Field(ge=1, le=100)


class PickupRecommendationRequest(BaseModel):
    maxWalkingMeters: int = Field(default=800, ge=100, le=2000)
    candidates: list[PickupCandidateRequest] = Field(min_length=2, max_length=20)


class AssistantFacts(BaseModel):
    routeMinutes: int | None = Field(default=None, ge=0, le=300)
    safetyScore: int | None = Field(default=None, ge=0, le=100)
    trafficLevel: str | None = Field(default=None, max_length=20)
    pickupZoneName: str | None = Field(default=None, max_length=60)


class AssistantChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=300)
    role: Literal["student", "parent"]
    timeBucket: Literal["day", "evening", "night"]
    facts: AssistantFacts | None = None


@app.get("/health")
async def health() -> dict:
    return {
        "status": "ok",
        "topisConfigured": traffic_service.client.configured,
        "assistantConfigured": bool(assistant_service.api_key),
        "fallbackEnabled": True,
    }


@app.get("/traffic/links/{link_id}")
async def link_traffic(link_id: str) -> dict:
    return await traffic_service.traffic(link_id)


@app.get("/traffic/incidents")
async def incidents(link_id: list[str] | None = Query(default=None)) -> dict:
    return await traffic_service.incident_list(link_id)


@app.get("/traffic/volume-history/{spot_number}")
async def volume_history(spot_number: str, date: str, hour: str) -> dict:
    try:
        ymd = validate_ymd(date)
        normalized_hour = validate_hour(hour)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return await traffic_service.volume(spot_number, ymd, normalized_hour)


@app.post("/traffic/assess-links")
async def assess_links(body: LinkAssessmentRequest) -> dict:
    return await traffic_service.assess_links(body.linkIds)


@app.post("/pickup-zones/recommend")
async def recommend_pickup_zone(body: PickupRecommendationRequest) -> dict:
    candidates = [
        {
            "id": item.id,
            "name": item.name,
            "link_id": item.linkId,
            "distance_meters": item.distanceMeters,
            "lighting_coverage": item.lightingCoverage,
            "safety_spot_count": item.safetySpotCount,
            "has_sidewalk": item.hasSidewalk,
            "has_crosswalk": item.hasCrosswalk,
            "legal_stop_allowed": item.legalStopAllowed,
            "stopping_suitability": item.stoppingSuitability,
            "waiting_vehicles": item.waitingVehicles,
            "capacity": item.capacity,
        }
        for item in body.candidates
    ]
    try:
        return await pickup_zone_service.recommend(candidates, body.maxWalkingMeters)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/api/assistant/chat")
async def assistant_chat(body: AssistantChatRequest) -> dict:
    result = await assistant_service.chat(
        message=body.message,
        role=body.role,
        time_bucket=body.timeBucket,
        facts=body.facts.model_dump(exclude_none=True) if body.facts else None,
    )
    return {
        "reply": result.reply,
        "action": result.action,
        "requiresConfirmation": result.requires_confirmation,
        "classifier": result.classifier,
    }

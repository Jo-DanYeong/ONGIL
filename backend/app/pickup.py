from __future__ import annotations

import asyncio
from collections import Counter
from typing import Any

from .topis import TrafficDataService


class PickupZoneService:
    """Ranks nearby, legally stoppable pickup candidates with live TOPIS data."""

    WEIGHTS = {
        "safety": 0.35,
        "distance": 0.25,
        "stopping": 0.25,
        "traffic": 0.10,
        "checkIn": 0.05,
    }

    def __init__(self, traffic_service: TrafficDataService) -> None:
        self.traffic_service = traffic_service

    async def recommend(
        self,
        candidates: list[dict[str, Any]],
        max_walking_meters: int = 800,
    ) -> dict[str, Any]:
        eligible = [
            candidate for candidate in candidates
            if candidate["legal_stop_allowed"]
            and candidate["distance_meters"] <= max_walking_meters
        ]
        if not eligible:
            raise ValueError("No legally stoppable pickup candidate is within walking range")

        traffic_rows = await asyncio.gather(*(
            self.traffic_service.traffic(candidate["link_id"])
            for candidate in eligible
        ))
        incidents = await self.traffic_service.incident_list(
            [candidate["link_id"] for candidate in eligible]
        )
        incident_counts = Counter(item["linkId"] for item in incidents["items"])

        ranked = [
            self._score_candidate(
                candidate,
                traffic,
                incident_counts[candidate["link_id"]],
                max_walking_meters,
            )
            for candidate, traffic in zip(eligible, traffic_rows)
        ]
        ranked.sort(key=lambda item: item["totalScore"], reverse=True)
        uses_live_data = any(row["source"] == "TOPIS" for row in traffic_rows)
        return {
            "recommended": ranked[0],
            "rankedCandidates": ranked,
            "weights": self.WEIGHTS,
            "usesLiveData": uses_live_data,
            "dataNotice": (
                "교통·돌발은 TOPIS, 정차 적합성과 보행 안전시설은 후보지 사전 데이터, "
                "대기량은 온길 체크인을 사용합니다."
            ),
        }

    def _score_candidate(
        self,
        candidate: dict[str, Any],
        traffic: dict[str, Any],
        incident_count: int,
        max_walking_meters: int,
    ) -> dict[str, Any]:
        lighting = candidate["lighting_coverage"]
        spot_score = min(1.0, candidate["safety_spot_count"] / 3)
        pedestrian_score = (
            lighting * 0.50
            + (1.0 if candidate["has_sidewalk"] else 0.0) * 0.25
            + (1.0 if candidate["has_crosswalk"] else 0.0) * 0.10
            + spot_score * 0.15
        )
        incident_safety = max(0.0, 1.0 - incident_count * 0.5)
        traffic_score = max(
            0.0,
            (1.0 - traffic["trafficRisk"]) * 0.75 + incident_safety * 0.25,
        )
        safety_score = pedestrian_score * 0.75 + traffic_score * 0.25
        distance_score = max(0.0, 1.0 - candidate["distance_meters"] / max_walking_meters)
        stopping_score = candidate["stopping_suitability"]
        check_in_score = max(
            0.0,
            1.0 - candidate["waiting_vehicles"] / candidate["capacity"],
        )
        total = (
            safety_score * self.WEIGHTS["safety"]
            + distance_score * self.WEIGHTS["distance"]
            + stopping_score * self.WEIGHTS["stopping"]
            + traffic_score * self.WEIGHTS["traffic"]
            + check_in_score * self.WEIGHTS["checkIn"]
        )
        reasons = [
            f"학생 위치에서 {candidate['distance_meters']}m",
            f"밝은 길 {round(lighting * 100)}% · 안전 거점 {candidate['safety_spot_count']}곳",
            f"정차 적합도 {round(stopping_score * 100)}점",
            f"현재 도로 {traffic['congestionLevel']} · {traffic['speedKph']:.1f}km/h",
            f"체크인 대기 {candidate['waiting_vehicles']}대/{candidate['capacity']}대",
        ]
        if incident_count:
            reasons.append(f"사고·공사·통제 정보 {incident_count}건 반영")
        return {
            "id": candidate["id"],
            "name": candidate["name"],
            "linkId": candidate["link_id"],
            "distanceMeters": candidate["distance_meters"],
            "waitingVehicles": candidate["waiting_vehicles"],
            "capacity": candidate["capacity"],
            "totalScore": round(total * 100),
            "safetyScore": round(safety_score * 100),
            "distanceScore": round(distance_score * 100),
            "stoppingScore": round(stopping_score * 100),
            "trafficScore": round(traffic_score * 100),
            "trafficRisk": traffic["trafficRisk"],
            "trafficLevel": traffic["congestionLevel"],
            "speedKph": traffic["speedKph"],
            "incidentCount": incident_count,
            "source": traffic["source"],
            "reasons": reasons,
        }

import asyncio

from app.pickup import PickupZoneService


class FakeTrafficService:
    async def traffic(self, link_id: str) -> dict:
        risk = {"near": 0.75, "safe": 0.10, "illegal": 0.05}[link_id]
        return {
            "linkId": link_id,
            "speedKph": 12.0 if risk > 0.5 else 28.0,
            "trafficRisk": risk,
            "congestionLevel": "정체" if risk > 0.5 else "원활",
            "source": "TOPIS",
        }

    async def incident_list(self, link_ids):
        return {"items": [], "count": 0, "source": "TOPIS", "warning": None}


def candidate(
    candidate_id: str,
    link_id: str,
    distance: int,
    lighting: float,
    stopping: float,
    legal: bool = True,
):
    return {
        "id": candidate_id,
        "name": f"{candidate_id} 픽업존",
        "link_id": link_id,
        "distance_meters": distance,
        "lighting_coverage": lighting,
        "safety_spot_count": 3,
        "has_sidewalk": True,
        "has_crosswalk": True,
        "legal_stop_allowed": legal,
        "stopping_suitability": stopping,
        "waiting_vehicles": 2,
        "capacity": 10,
    }


def test_recommends_safe_stoppable_candidate_not_only_nearest():
    result = asyncio.run(PickupZoneService(FakeTrafficService()).recommend([
        candidate("A", "near", 100, 0.45, 0.45),
        candidate("B", "safe", 280, 0.90, 0.95),
    ]))

    assert result["recommended"]["id"] == "B"
    assert result["recommended"]["source"] == "TOPIS"
    assert result["weights"] == {
        "safety": 0.35,
        "distance": 0.25,
        "stopping": 0.25,
        "traffic": 0.10,
        "checkIn": 0.05,
    }


def test_excludes_candidate_where_stopping_is_not_allowed():
    result = asyncio.run(PickupZoneService(FakeTrafficService()).recommend([
        candidate("A", "near", 100, 0.45, 0.45),
        candidate("C", "illegal", 30, 1.0, 1.0, legal=False),
    ]))

    assert result["recommended"]["id"] == "A"
    assert all(item["id"] != "C" for item in result["rankedCandidates"])

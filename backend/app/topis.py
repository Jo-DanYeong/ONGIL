from __future__ import annotations

import asyncio
import os
import time
from dataclasses import dataclass
from datetime import date
from typing import Any, Iterable
from urllib.parse import quote
from xml.etree import ElementTree

import httpx


class TopisError(RuntimeError):
    """Raised when TOPIS returns an invalid or unsuccessful response."""


class TopisNotConfigured(TopisError):
    """Raised when the Seoul Open Data API key has not been configured."""


@dataclass(frozen=True)
class CacheEntry:
    value: Any
    expires_at: float


class TimedCache:
    def __init__(self) -> None:
        self._values: dict[str, CacheEntry] = {}

    def get(self, key: str) -> Any | None:
        item = self._values.get(key)
        if item is None or item.expires_at <= time.monotonic():
            self._values.pop(key, None)
            return None
        return item.value

    def put(self, key: str, value: Any, ttl_seconds: int) -> None:
        self._values[key] = CacheEntry(value, time.monotonic() + ttl_seconds)


class TopisClient:
    """Async adapter for the official Seoul TOPIS Open API XML services."""

    TRAFFIC_SERVICE = "TrafficInfo"
    INCIDENT_SERVICE = "AccInfo"
    VOLUME_SERVICE = "VolInfo"

    def __init__(
        self,
        api_key: str | None = None,
        base_url: str | None = None,
        timeout_seconds: float | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.api_key = (api_key if api_key is not None else os.getenv("TOPIS_API_KEY", "")).strip()
        self.base_url = (base_url or os.getenv("TOPIS_BASE_URL", "http://openapi.seoul.go.kr:8088")).rstrip("/")
        self.timeout_seconds = timeout_seconds or float(os.getenv("TOPIS_TIMEOUT_SECONDS", "5"))
        self.transport = transport

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    async def link_traffic(self, link_id: str) -> dict[str, Any]:
        rows = await self._request_xml(self.TRAFFIC_SERVICE, 1, 1, link_id)
        if not rows:
            raise TopisError(f"TOPIS returned no traffic data for link {link_id}")
        row = rows[0]
        return {
            "linkId": row.get("link_id", link_id),
            "speedKph": _as_float(row.get("prcs_spd")),
            "travelTimeSeconds": _as_int(row.get("prcs_trv_time")),
        }

    async def incidents(self, start: int = 1, end: int = 1000) -> list[dict[str, Any]]:
        rows = await self._request_xml(self.INCIDENT_SERVICE, start, end)
        return [
            {
                "incidentId": row.get("acc_id", ""),
                "occurredDate": row.get("occr_date", ""),
                "occurredTime": row.get("occr_time", ""),
                "expectedClearDate": row.get("exp_clr_date", ""),
                "expectedClearTime": row.get("exp_clr_time", ""),
                "typeCode": row.get("acc_type", ""),
                "detailTypeCode": row.get("acc_dtype", ""),
                "linkId": row.get("link_id", ""),
                "tmX": _as_float(row.get("grs80tm_x")),
                "tmY": _as_float(row.get("grs80tm_y")),
                "description": row.get("acc_info", "").strip(),
            }
            for row in rows
        ]

    async def volume_history(self, spot_number: str, ymd: str, hour: str) -> list[dict[str, Any]]:
        rows = await self._request_xml(self.VOLUME_SERVICE, 1, 1000, spot_number, ymd, hour)
        return [
            {
                "spotNumber": row.get("spot_num", spot_number),
                "date": row.get("ymd", ymd),
                "hour": row.get("hh", hour),
                "directionType": row.get("io_type", ""),
                "laneNumber": _as_int(row.get("lane_num")),
                "volume": _as_int(row.get("vol")),
            }
            for row in rows
        ]

    async def _request_xml(self, service: str, start: int, end: int, *parts: str) -> list[dict[str, str]]:
        if not self.configured:
            raise TopisNotConfigured("TOPIS_API_KEY is not configured")
        if start < 1 or end < start or end - start >= 1000:
            raise ValueError("TOPIS row range must contain at most 1,000 rows and start at 1")
        path = "/".join(
            quote(str(part), safe="")
            for part in (self.api_key, "xml", service, str(start), str(end), *parts)
        )
        url = f"{self.base_url}/{path}/"
        async with httpx.AsyncClient(
            timeout=self.timeout_seconds,
            transport=self.transport,
            follow_redirects=False,
        ) as client:
            response = await client.get(url, headers={"Accept": "application/xml"})
            response.raise_for_status()
        return parse_topis_xml(response.text, service)


def parse_topis_xml(xml_text: str, service: str) -> list[dict[str, str]]:
    try:
        root = ElementTree.fromstring(xml_text)
    except ElementTree.ParseError as exc:
        raise TopisError("TOPIS returned malformed XML") from exc

    result = root.find("RESULT")
    if result is None and root.tag != service:
        code = root.findtext("CODE") or root.tag
        message = root.findtext("MESSAGE") or "Unknown TOPIS error"
        raise TopisError(f"{code}: {message}")
    code = result.findtext("CODE") if result is not None else "INFO-000"
    message = result.findtext("MESSAGE") if result is not None else ""
    if code != "INFO-000":
        raise TopisError(f"{code}: {message}")
    return [{child.tag.lower(): (child.text or "") for child in row} for row in root.findall("row")]


class TrafficDataService:
    """Product-facing traffic service with short caches and a demo fallback."""

    def __init__(self, client: TopisClient | None = None) -> None:
        self.client = client or TopisClient()
        self.cache = TimedCache()

    async def traffic(self, link_id: str) -> dict[str, Any]:
        cache_key = f"traffic:{link_id}"
        cached = self.cache.get(cache_key)
        if cached is not None:
            return {**cached, "cached": True}
        try:
            value = await self.client.link_traffic(link_id)
            value.update({"source": "TOPIS", "warning": None})
        except (TopisError, httpx.HTTPError) as exc:
            value = _mock_traffic(link_id)
            value.update({"source": "MOCK_FALLBACK", "warning": _safe_warning(exc)})
        value.update(congestion_metrics(value["speedKph"]))
        self.cache.put(cache_key, value, 60)
        return {**value, "cached": False}

    async def incident_list(self, link_ids: Iterable[str] | None = None) -> dict[str, Any]:
        wanted = {str(item) for item in link_ids or []}
        cache_key = "incidents:all"
        rows = self.cache.get(cache_key)
        source = "TOPIS"
        warning = None
        if rows is None:
            try:
                rows = await self.client.incidents()
                self.cache.put(cache_key, rows, 300)
            except (TopisError, httpx.HTTPError) as exc:
                rows = []
                source = "MOCK_FALLBACK"
                warning = _safe_warning(exc)
        if wanted:
            rows = [row for row in rows if row["linkId"] in wanted]
        return {"items": rows, "count": len(rows), "source": source, "warning": warning}

    async def volume(self, spot_number: str, ymd: str, hour: str) -> dict[str, Any]:
        cache_key = f"volume:{spot_number}:{ymd}:{hour}"
        cached = self.cache.get(cache_key)
        if cached is not None:
            return {**cached, "cached": True}
        try:
            rows = await self.client.volume_history(spot_number, ymd, hour)
            source, warning = "TOPIS", None
        except (TopisError, httpx.HTTPError) as exc:
            rows = []
            source, warning = "MOCK_FALLBACK", _safe_warning(exc)
        total = sum(item["volume"] for item in rows)
        value = {"items": rows, "totalVolume": total, "source": source, "warning": warning}
        self.cache.put(cache_key, value, 3600)
        return {**value, "cached": False}

    async def assess_links(self, link_ids: list[str]) -> dict[str, Any]:
        unique_ids = list(dict.fromkeys(link_ids))[:20]
        traffic_rows = await asyncio.gather(*(self.traffic(link_id) for link_id in unique_ids))
        incidents = await self.incident_list(unique_ids)
        average_speed = (
            round(sum(row["speedKph"] for row in traffic_rows) / len(traffic_rows), 1)
            if traffic_rows else 0.0
        )
        speed_risk = sum(row["trafficRisk"] for row in traffic_rows) / max(1, len(traffic_rows))
        incident_risk = min(0.5, incidents["count"] * 0.25)
        risk = round(min(1.0, speed_risk * 0.7 + incident_risk * 0.3), 2)
        recommendation = "추천" if risk < 0.35 else "주의" if risk < 0.65 else "비추천"
        reasons = []
        if any(row["congestionLevel"] == "정체" for row in traffic_rows):
            reasons.append("현재 링크 속도 15km/h 미만 정체")
        if incidents["count"]:
            reasons.append(f"사고·공사·통제 등 돌발정보 {incidents['count']}건")
        if not reasons:
            reasons.append("현재 원활한 교통 흐름")
        return {
            "linkIds": unique_ids,
            "averageSpeedKph": average_speed,
            "trafficRisk": risk,
            "pickupZoneRecommendation": recommendation,
            "reasons": reasons,
            "traffic": traffic_rows,
            "incidents": incidents["items"],
            "usesLiveData": any(row["source"] == "TOPIS" for row in traffic_rows),
        }


def congestion_metrics(speed_kph: float) -> dict[str, Any]:
    """TOPIS general-road thresholds: smooth >=25, slow 15-25, congested <15."""
    if speed_kph >= 25:
        return {"congestionLevel": "원활", "trafficRisk": 0.10, "pickupPenalty": 0.0}
    if speed_kph >= 15:
        return {"congestionLevel": "서행", "trafficRisk": 0.40, "pickupPenalty": 0.25}
    return {"congestionLevel": "정체", "trafficRisk": 0.80, "pickupPenalty": 0.55}


def validate_ymd(value: str) -> str:
    if len(value) != 8 or not value.isdigit():
        raise ValueError("date must use YYYYMMDD")
    date.fromisoformat(f"{value[:4]}-{value[4:6]}-{value[6:]}")
    return value


def validate_hour(value: str) -> str:
    if not value.isdigit() or not 0 <= int(value) <= 23:
        raise ValueError("hour must be between 00 and 23")
    return value.zfill(2)


def _mock_traffic(link_id: str) -> dict[str, Any]:
    speed = 12.0 + sum(ord(char) for char in link_id) % 22
    return {"linkId": link_id, "speedKph": speed, "travelTimeSeconds": int(900 / speed * 3.6)}


def _as_float(value: str | None) -> float:
    try:
        return float(value or 0)
    except ValueError:
        return 0.0


def _as_int(value: str | None) -> int:
    try:
        return int(float(value or 0))
    except ValueError:
        return 0


def _safe_warning(exc: Exception) -> str:
    """Return a client-safe warning without leaking a URL-embedded API key."""
    if isinstance(exc, TopisError):
        return str(exc)
    return f"TOPIS request failed ({type(exc).__name__})"

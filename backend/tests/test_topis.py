import asyncio

import httpx
import pytest

from app.topis import TopisClient, TrafficDataService, congestion_metrics, parse_topis_xml


TRAFFIC_XML = """<?xml version="1.0" encoding="UTF-8"?>
<TrafficInfo>
  <list_total_count>1</list_total_count>
  <RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다</MESSAGE></RESULT>
  <row><link_id>1220003800</link_id><prcs_spd>26</prcs_spd><prcs_trv_time>415</prcs_trv_time></row>
</TrafficInfo>"""

INCIDENT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<AccInfo>
  <list_total_count>1</list_total_count>
  <RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다</MESSAGE></RESULT>
  <row>
    <acc_id>1089840</acc_id><occr_date>20260711</occr_date><occr_time>072200</occr_time>
    <exp_clr_date>20260711</exp_clr_date><exp_clr_time>180000</exp_clr_time>
    <acc_type>A04</acc_type><acc_dtype>04B01</acc_dtype><link_id>1220003800</link_id>
    <grs80tm_x>201270.4</grs80tm_x><grs80tm_y>448246.5</grs80tm_y>
    <acc_info>시설물 보수로 일부 차로 통제</acc_info>
  </row>
</AccInfo>"""

VOLUME_XML = """<?xml version="1.0" encoding="UTF-8"?>
<VolInfo>
  <list_total_count>2</list_total_count>
  <RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다</MESSAGE></RESULT>
  <row><spot_num>A-01</spot_num><ymd>20260710</ymd><hh>22</hh><io_type>1</io_type><lane_num>1</lane_num><vol>420</vol></row>
  <row><spot_num>A-01</spot_num><ymd>20260710</ymd><hh>22</hh><io_type>1</io_type><lane_num>2</lane_num><vol>380</vol></row>
</VolInfo>"""


def response_for(request: httpx.Request) -> httpx.Response:
    path = str(request.url)
    if "/TrafficInfo/" in path:
        return httpx.Response(200, text=TRAFFIC_XML)
    if "/AccInfo/" in path:
        return httpx.Response(200, text=INCIDENT_XML)
    if "/VolInfo/" in path:
        return httpx.Response(200, text=VOLUME_XML)
    return httpx.Response(404)


def test_parser_rejects_api_error():
    with pytest.raises(Exception, match="INFO-100"):
        parse_topis_xml("<RESULT><CODE>INFO-100</CODE><MESSAGE>키 오류</MESSAGE></RESULT>", "TrafficInfo")


def test_official_services_are_mapped_to_product_fields():
    async def scenario():
        client = TopisClient(api_key="test-key", transport=httpx.MockTransport(response_for))
        return (
            await client.link_traffic("1220003800"),
            await client.incidents(),
            await client.volume_history("A-01", "20260710", "22"),
        )

    traffic, incidents, volume = asyncio.run(scenario())

    assert traffic == {"linkId": "1220003800", "speedKph": 26.0, "travelTimeSeconds": 415}
    assert incidents[0]["description"] == "시설물 보수로 일부 차로 통제"
    assert volume[0]["volume"] == 420


def test_assessment_combines_speed_and_incident_risk():
    async def scenario():
        client = TopisClient(api_key="test-key", transport=httpx.MockTransport(response_for))
        return await TrafficDataService(client).assess_links(["1220003800"])

    result = asyncio.run(scenario())

    assert result["usesLiveData"] is True
    assert result["incidents"][0]["linkId"] == "1220003800"
    assert result["pickupZoneRecommendation"] in {"추천", "주의", "비추천"}


def test_general_road_congestion_thresholds():
    assert congestion_metrics(27)["congestionLevel"] == "원활"
    assert congestion_metrics(20)["congestionLevel"] == "서행"
    assert congestion_metrics(12)["congestionLevel"] == "정체"


def test_http_failure_warning_never_exposes_api_key():
    async def scenario():
        transport = httpx.MockTransport(lambda request: httpx.Response(500))
        client = TopisClient(api_key="never-show-this-key", transport=transport)
        return await TrafficDataService(client).traffic("1220003800")

    result = asyncio.run(scenario())

    assert result["source"] == "MOCK_FALLBACK"
    assert "never-show-this-key" not in result["warning"]

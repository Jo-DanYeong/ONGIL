# TOPIS Open API 연동

온길은 서울 TOPIS 데이터를 서버에서만 호출합니다. Android 앱에 인증키를 넣지 않으며, 백엔드가 정규화한 결과만 앱에 전달합니다.

## 사용 데이터

| 우선순위 | 서울 데이터셋 | 서비스명 | 온길 사용처 |
|---|---|---|---|
| 필수 | 실시간 도로 소통 정보 (OA-13291) | `TrafficInfo` | 링크별 속도·통행시간, 픽업존 교통 근거 |
| 추가 | 실시간 돌발 정보 (OA-13315) | `AccInfo` | 사고·공사·통제 회피, 안전 경로 위험도 |
| 선택 | 교통량 이력 정보 (OA-13316) | `VolInfo` | 시간대별 혼잡 그래프·사전 예측 |

공식 요청 형식은 다음과 같습니다.

```text
http://openapi.seoul.go.kr:8088/{KEY}/xml/{SERVICE}/{START}/{END}/{추가인자...}/
```

예시:

```text
TrafficInfo: /{KEY}/xml/TrafficInfo/1/1/1220003800/
AccInfo:     /{KEY}/xml/AccInfo/1/1000/
VolInfo:     /{KEY}/xml/VolInfo/1/1000/A-01/20260710/22/
```

`VolInfo`는 공식 명세상 XML만 지원합니다. 구현은 세 API 모두 XML로 통일해 응답 차이를 줄였습니다.

## 인증키 설정

1. 서울 열린데이터광장에 로그인합니다.
2. 이용안내 → Open API 소개 → 일반 인증키 신청으로 이동합니다.
3. 환경은 `앱개발(모바일 솔루션 등)`, 사용 URL은 프로젝트 저장소 또는 배포 URL을 입력합니다.
4. 활용용도에는 `야간 통학 안전 경로 및 픽업존 추천`을 입력합니다.
5. 발급된 키를 `backend/.env`에만 저장합니다.

```powershell
Copy-Item backend\.env.example backend\.env
```

```dotenv
TOPIS_API_KEY=발급받은_인증키
TOPIS_BASE_URL=http://openapi.seoul.go.kr:8088
TOPIS_TIMEOUT_SECONDS=5
```

`.env`는 `.gitignore`에 포함돼 있어 GitHub에 올라가지 않습니다.

## 온길 API

백엔드를 실행한 뒤 다음 엔드포인트를 사용합니다.

```text
GET  /traffic/links/{linkId}
GET  /traffic/incidents?link_id=1220003800
GET  /traffic/volume-history/{spotNumber}?date=20260710&hour=22
POST /traffic/assess-links
POST /pickup-zones/recommend
```

링크 평가 요청:

```json
{
  "linkIds": ["1220003800", "1230016700"]
}
```

응답의 `trafficRisk`는 0~1이며 다음을 결합합니다.

- 링크 현재 속도 기반 위험 70%
- 해당 링크의 실시간 돌발 건수 기반 위험 30%

일반도로 혼잡 기준은 TOPIS 지도 범례와 동일하게 25km/h 이상 원활, 15~25km/h 서행, 15km/h 미만 정체로 분류합니다. 픽업존은 `추천`, `주의`, `비추천`으로 반환합니다.

## 주변 픽업존 추천

`POST /pickup-zones/recommend`는 지도에서 만든 주변 후보지를 비교합니다. 각 후보에는 도보 거리, TOPIS `LINK_ID`, 조도·보도·횡단보도·안전 거점, 합법적 정차 가능 여부, 정차 적합도, 온길 체크인 대기량을 전달합니다.

- 안전도 35%: 조도·보도·횡단보도·안전 거점과 TOPIS 돌발정보
- 학생 도보 거리 25%
- 정차 적합성 25%: 정차 허용 후보만 평가
- TOPIS 실시간 교통 10%
- 온길 체크인 혼잡 5%

정차 금지 후보와 최대 도보거리 밖 후보는 평가에서 제외합니다. TOPIS는 현재 속도·통행시간·사고·공사·통제를 제공하지만 가로등이나 합법적 정차 여부는 제공하지 않으므로, 이 값은 별도 공공데이터·현장 검증·후보지 사전 데이터와 결합해야 합니다.

## 장애와 호출량 처리

- 링크 소통 정보는 60초 캐시
- 돌발 정보는 5분 캐시
- 이력 정보는 1시간 캐시
- 인증키 미설정, 타임아웃, TOPIS 오류 시 `MOCK_FALLBACK`으로 시연 유지
- API 응답과 로그에 인증키를 포함하지 않음
- 한 번에 최대 20개 링크 평가, TOPIS 한 요청 최대 1,000행 준수

운영에서는 픽업 후보 도로와 안전 경로의 각 구간에 TOPIS `LINK_ID`를 저장해야 합니다. 링크 매핑에는 TOPIS의 도로별 링크정보·링크 Vertex정보를 초기 적재 데이터로 사용합니다.

# 온길 (ONGIL)

> 오늘도 안전하게, 집으로

Render 배포 방법은 [Render 백엔드 배포 문서](docs/RENDER_DEPLOY.md)를 참고하세요.

학생의 귀가 상황에 따라 **학부모와 안전한 픽업 합류** 또는 **혼자 귀가하는 야간 안전 우선 경로**를 결정해 주는 Android 서비스입니다.

온길의 차별점은 길만 표시하는 데 그치지 않고, 지도 위의 작은 귀가 도우미 **온이**가 “오늘 픽업이야, 혼자 가?”라고 먼저 물은 뒤 학생과 학부모에게 지금 필요한 귀가 방식을 실행하는 것입니다. 온이는 ONGIL의 핀·방패·별을 결합한 펫 형태이며 누르면 하단 대화창이 열립니다.

## 앱 작동 흐름

1. 온길 도우미가 오늘의 귀가 상황을 질문합니다.
2. `픽업 가능`이면 TOPIS 도로 혼잡도와 사용자 도착·대기 체크인을 합산해 덜 붐비는 픽업존을 추천합니다.
3. 학생과 학부모가 동의하면 귀가 종료까지만 위치를 공유합니다.
4. `혼자 귀가`이면 밝은 길, 편의점·파출소 등 안전 거점, 시간대와 교통 혼잡을 조합해 야간 안전 우선 경로를 안내합니다.
5. “위치 공유할래”라고 말하면 자동 공유하지 않고 사용자 확인 후 위치 공유 요청 화면을 엽니다.

온길은 실시간 골목 범죄를 예측한다고 표현하지 않습니다. 확인 가능한 보행 환경 데이터를 근거로 더 밝고 도움을 요청하기 쉬운 경로를 추천합니다.

## 현재 구현된 기능

- 로그인 및 학생/학부모 역할 선택
- 현재 위치와 픽업존을 표현한 지도형 홈 목업
- 학부모의 위치 공유 요청과 학생의 수락/거절 흐름
- 소요 시간과 위험도를 비교하는 안전 경로 플랜 A/B
- 학부모 모드의 `TMAP으로 안내받기` 연결 자리
- “픽업 가능 / 혼자 귀가”를 전환하는 온길 도우미
- 자연어를 `OPEN_PICKUP_ZONES`, `OPEN_SAFE_ROUTES`, `REQUEST_LOCATION_SHARE`, `NONE`으로 연결하는 온이 API
- 체크인 대기량과 교통 혼잡을 합산한 픽업존 추천
- 정차 금지 후보 제외 후 안전 35% · 거리 25% · 정차 적합성 25% · 교통 10% · 체크인 5%로 픽업존 순위 계산
- 조도·안전 거점·시간대·교통 혼잡 기반 야간 안전 점수
- 사용자가 집·학원·학교 주소를 설정하고 기기 로컬에 저장
- 현재 위치에서 추천 픽업존까지 TMAP 보행자 경로 표시
- 온이 답변이 한 글자씩 나타나는 펫 대화 효과

현재 지도와 사용자 위치는 발표용 목업입니다. 도로 혼잡도는 서울 TOPIS Open API를 호출하며, 픽업존 도착·대기 체크인은 해커톤 시뮬레이션입니다. 인증키가 없거나 API 호출이 실패하면 교통 데이터도 시연용 값으로 자동 전환됩니다.

## 프로젝트 구조

```text
app/src/main/java/com/project/ongil/
├─ LoginActivity.java          # 로그인 및 역할 선택
├─ MainActivity.java           # 지도형 홈과 역할별 기능
├─ LocationShareActivity.java  # 위치 공유 요청/수락 흐름
├─ RoutePlansActivity.java     # 경로별 시간/위험도 비교
└─ data/TrafficApiClient.java  # TOPIS 백엔드 호출

backend/app/
├─ main.py                     # 온길 교통 API 엔드포인트
└─ topis.py                    # TOPIS XML 변환·캐시·위험도 계산
```

TOPIS 인증키 발급, 환경 변수와 API 사용법은 [TOPIS 연동 문서](docs/TOPIS_API.md)를 참고하세요. 인증키는 Android 앱이 아닌 `backend/.env`에만 저장합니다.

온이의 액션 계약, 개인정보 보호와 Gemini 서버 설정은 [온이 귀가 도우미 API 문서](docs/ASSISTANT_API.md)를 참고하세요.

홈 지도 SDK와 앱 키 설정은 [TMAP Android 지도 연동 문서](docs/TMAP.md)를 참고하세요.

## 데이터와 판단 기준

야간 안전 점수는 `100`에 가까울수록 더 안전한 보행 환경을 뜻합니다.

- 도로 혼잡도: TOPIS 실시간 도로소통정보·돌발정보
- 픽업존 혼잡도: 온길 사용자 도착/대기 체크인 수
- 안전 경로: 가로등 비율·안전 거점 수·시간대·교통 혼잡
- 교통 예측/발표 그래프: TOPIS 교통량 이력정보

발표에서는 “범죄 다발 길 회피” 대신 **보행 환경 데이터 기반 야간 안전 우선 경로**라고 표현합니다.

## API 키 설정

실제 SK Open API 연동 전 `local.properties.example`을 참고해 개인 PC의 `local.properties`에 키를 추가합니다.

```properties
SK_OPEN_API_KEY=replace_with_your_key
```

`local.properties`는 `.gitignore`에 포함되어 GitHub에 올라가지 않습니다. 모바일 앱에 포함된 키는 추출될 수 있으므로 실제 서비스 단계에서는 사용 제한 설정 또는 서버 프록시를 검토해야 합니다.

TOPIS 인증키는 APK에 넣지 않고 `backend/.env`에만 저장합니다. Android의 `local.properties`에는 백엔드 주소와 학원가 주변 링크 ID만 설정합니다.

```properties
ONGIL_BACKEND_URL=https://your-ongil-backend.example.com
TOPIS_LINK_IDS=픽업존A_LINK_ID,픽업존B_LINK_ID,픽업존C_LINK_ID
```

`TOPIS_LINK_IDS`의 순서는 A/B/C 픽업존 후보와 대응합니다. 실제 장소와 도로 진행 방향이 확정되기 전에는 빈 값으로 두며, 확정 후 각 후보가 접한 TOPIS 링크 ID를 저장합니다.
1. 지도 SDK 표시 및 현재 위치 권한 처리
2. Firebase 또는 Supabase 기반 가족 연결과 실시간 위치 공유
3. TMAP 차량 경로 및 픽업존 연결
4. 공개 데이터 기반 위험도 계산
5. 발표용 시나리오 녹화

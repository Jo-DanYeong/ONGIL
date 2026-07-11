# Render 백엔드 배포

ONGIL 백엔드는 저장소 루트의 `render.yaml`을 사용하는 Render Blueprint로 배포합니다.
Python 서비스의 루트 디렉터리는 `backend`이며 서울과 가까운 Singapore 리전을 사용합니다.
저장소 루트의 `.python-version`으로 Python 3.12.8을 고정합니다.

## 1. GitHub에 변경 사항 올리기

Render가 `render.yaml`을 읽을 수 있도록 이 파일을 포함한 변경 사항을 GitHub의
`Jo-DanYeong/ONGIL` 저장소에 먼저 push합니다.

## 2. Render Blueprint 생성

1. Render Dashboard에서 **New + → Blueprint**를 선택합니다.
2. GitHub 저장소 `Jo-DanYeong/ONGIL`을 연결합니다.
3. Render가 감지한 `ongil-backend` 서비스를 생성합니다.
4. 최초 생성 화면에서 다음 비밀 환경 변수를 입력합니다.

| 환경 변수 | 값 |
| --- | --- |
| `TOPIS_API_KEY` | 서울시 TOPIS에서 발급받은 인증키 |
| `GEMINI_API_KEY` | Google AI Studio에서 발급받은 Gemini API 키 |

`TOPIS_BASE_URL`, `TOPIS_TIMEOUT_SECONDS`, `GEMINI_MODEL`은 Blueprint에 기본값이
들어 있습니다. API 키를 `.env`, `render.yaml`, Android 앱 또는 GitHub에 직접
커밋하지 않습니다.

## 3. 배포 확인

배포가 끝나면 Render가 발급한 HTTPS 주소에서 다음 URL을 확인합니다.

```text
https://<render-service-host>/health
https://<render-service-host>/docs
```

`/health`의 예시는 다음과 같습니다.

```json
{
  "status": "ok",
  "topisConfigured": true,
  "assistantConfigured": true,
  "fallbackEnabled": true
}
```

두 configured 값 중 하나가 `false`이면 Render Dashboard의 해당 서비스에서
**Environment**를 열어 누락된 키를 입력하고 재배포합니다.

## 4. Android 앱에 운영 주소 연결

Render 서비스 URL을 개인 PC의 `local.properties`에 입력합니다. 마지막 `/`는
있어도 앱이 정규화하지만, 아래처럼 제외하는 편이 명확합니다.

```properties
ONGIL_BACKEND_URL=https://<render-service-host>
```

Android Studio에서 Gradle Sync 후 앱을 다시 빌드합니다. `local.properties`는 Git에
올라가지 않으므로 팀원도 각자 같은 운영 주소를 설정해야 합니다.

## 수동 Web Service 설정값

Blueprint 대신 Web Service를 직접 만들 때는 다음 값을 사용합니다.

| 항목 | 값 |
| --- | --- |
| Runtime | Python 3 |
| Region | Singapore |
| Root Directory | `backend` |
| Build Command | `pip install -r requirements.txt` |
| Start Command | `python -m uvicorn app.main:app --host 0.0.0.0 --port $PORT` |
| Health Check Path | `/health` |

무료 인스턴스는 일정 시간 요청이 없으면 휴면 상태가 되어 첫 요청이 늦을 수 있습니다.
발표 전에 `/health`를 한 번 열어 인스턴스를 깨워 두는 것이 좋습니다.

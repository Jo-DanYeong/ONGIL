# 온이 귀가 도우미 API

온이는 경로나 위험도를 계산하는 AI가 아닙니다. 학생의 문장에서 귀가 상황을 파악해 이미 계산된 온길 기능으로 연결합니다.

```text
POST /api/assistant/chat
```

요청:

```json
{
  "message": "오늘 혼자 걸어서 갈 것 같아",
  "role": "student",
  "timeBucket": "night"
}
```

응답:

```json
{
  "reply": "그럼 밝은 길과 안전 거점을 우선한 야간 안전 경로를 보여줄게.",
  "action": "OPEN_SAFE_ROUTES",
  "requiresConfirmation": false,
  "classifier": "RULES"
}
```

허용 액션은 다음 네 개뿐입니다.

- `OPEN_PICKUP_ZONES`: 주변 픽업존 추천 화면
- `OPEN_SAFE_ROUTES`: 야간 안전 우선 경로 화면
- `REQUEST_LOCATION_SHARE`: 확인 다이얼로그를 거쳐 위치 공유 요청 화면
- `NONE`: 앱 기능을 실행하지 않는 일반 안내

## 안전 경계

- AI는 경로, 시간, 혼잡도, 안전 점수를 생성하지 않습니다.
- 실제 계산값 설명이 필요하면 선택적인 `facts` 필드로 서버 계산 결과만 전달합니다.
- 정확한 GPS, 가족 코드, 프로필 이름은 요청 필드에 포함하지 않습니다.
- Gemini에는 사용자의 원문이 아닌 `pickup`, `solo`, `night_fear` 같은 허용된 의도 신호만 전달합니다.
- 모델 출력은 JSON Schema로 네 가지 액션 중 하나로 제한하며, 사용자에게 보여줄 문장은 서버 템플릿이 만듭니다.
- 위치 공유는 `requiresConfirmation=true`이며 Android 확인창에서 사용자가 승인해야 실행됩니다.
- “완전히 안전한 길”이 아니라 “야간 안전 우선 경로”로 안내합니다.

## Gemini 설정

Gemini 키가 설정되면 모든 대화에서 제한된 의도 신호를 바탕으로 온이의 짧고 자연스러운 답변을 만듭니다. 앱 액션은 서버 규칙이 최종 결정하며 Gemini가 경로·시간·점수를 만들 수 없습니다. 키가 없어도 핵심 예시 문장은 서버 규칙과 기본 답변으로 동작합니다.

```dotenv
GEMINI_API_KEY=서버에만_저장하는_키
GEMINI_MODEL=gemini-2.5-flash
```

키는 `backend/.env`에만 저장하며 Android `local.properties`, `BuildConfig`, APK에 포함하지 않습니다.

PowerShell에서 백엔드를 실행합니다.

```powershell
cd C:\ONGIL
powershell -ExecutionPolicy Bypass -File .\backend\start.ps1
```

Android 에뮬레이터에서 로컬 백엔드에 접속하려면 `local.properties`에 다음 값을 추가한 뒤 앱을 다시 빌드합니다.

```properties
ONGIL_BACKEND_URL=http://10.0.2.2:8000
```

실제 휴대전화는 같은 Wi-Fi에 연결된 PC의 IPv4 주소를 사용합니다.

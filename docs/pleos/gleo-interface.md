# Gleo Interface (Gleo AI 앱 연동)

Gleo AI(차량 음성 에이전트)가 서드파티 앱의 기능을 음성 명령으로 부르게 하는 방식. LLM function calling과 비슷하다. 별도 SDK 의존성은 없다(assets 파일 + Intent).

출처 (확인일 2026-10-30)
- 핵심 개념: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
- App Schema: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability
- Callback Schema: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback
- 예제 코드: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples
- 스키마 전달: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema
- 사례(라디오 앱): https://document.pleos.ai/docs/pleos-only/app-integration-guide/use-cases/radio-app

## 흐름

1. 앱이 `assets/schema.json`에 Gleo가 부를 함수를 적는다(파일 이름·위치 고정)
2. Gleo가 부팅 때, 그리고 앱 설치·삭제 때 스키마를 읽는다
3. 사용자가 말하면 Gleo LLM이 함수를 골라 Intent로 앱을 부른다(Gleo Action)
4. 앱이 결과를 브로드캐스트로 돌려주면(Callback) Gleo가 그걸 바탕으로 말한다
5. 스키마는 Gleo 쪽 검토·승인 뒤 활성화된다고 문서에 적혀 있다(승인 절차는 문서에 없음)

## App Schema (`assets/schema.json`)

| 필드 | 내용 |
| --- | --- |
| `packageName` | 앱 패키지 |
| `appDescription` | 앱 목적 한두 문장(영어) |
| `invokeWord` | 예시에만 있고 필드 표에는 없다. 앱 호출 이름으로 보임(예: "리마인더") |
| `version` | 앱 마켓 등록 버전 |
| `actionCalls` | 함수 목록, **최대 3개** |
| `actionCalls[].type` | `"function"` 고정 |
| `function.name` | 대문자 + 밑줄, 2~3단어, 앱 안에서 고유. **Intent action 이름이 된다** |
| `function.description` | 영어 한두 문장, 예시 넣지 않기, Gleo 동작을 조작하는 문구 금지 |
| `function.parameters` | `type: "object"`, `properties`(type은 string·number·boolean·array 하나만, `enum`은 문자열만), `required`(필수만) |
| `function.component` | `"activity"`(기본, `startActivity`) 또는 `"service"`(`sendBroadcast`, 화면 없음) |

## Gleo Action (Gleo → 앱)

- Intent action = 함수 이름, `setPackage(앱 패키지)`, extra `ACTION_PARAM` = 인자 JSON 문자열
- 매니페스트에 함수마다 intent-filter를 단다. activity면 `category.DEFAULT`도
- 문서 예제의 `ACTION_PARAM` 값은 따옴표로 한 번 더 감싼 모양이다. 받는 쪽에서 둘 다 읽게 둔다

## Callback (앱 → Gleo)

- 브로드캐스트 action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`, extra `packageName`, `response`(JSON 문자열)
- `response`: `functionName`(필수, 함수 이름), `status`(필수, `success`·`asking`·`fail`), `message`(필수, **영어** 완전한 문장, 항목 이름은 다른 언어 가능), `mode`(선택, `voice` 기본·`beep`·`silent`), `data`(선택이지만 강력 권장, JSON 배열 문자열)
- `asking`: 필수 값 누락·목록 선택·확인이 필요할 때. `fail`: 조건 미충족과 시스템 오류 모두
- **Gleo는 7초 뒤 Action을 취소한다**

## 에뮬레이터 결과 (2026-10-30, `spikes/gleo-probe`)

- Gleo 앱 `ai.umos.ambientai` 4.2.0.15(사용자 10 프로세스). 엔지니어링 화면(`am start --user 10 -a ambientai.intent.action.ENGINEERING_MODE`) 기준 GAA SDK 3.7.3.1, GAAS 4.2.10-coral-aosp
- 기본 앱 3개가 이미 스키마를 쓴다: 차량 제어(`ai.umos.vehiclecontrol`), 미디어(`ai.umos.mediaapp`), 내비(`ai.umos.maps.android.navigation.app`)
- ✅ **사용자 앱 스키마도 읽는다**: `adb install` 직후 로그 `[Schema] Loaded schema.json from io.roadsensor.gleoprobe`, 스키마 3개 → 4개. 승인 절차 없이 읽혔다(실제 호출까지 되는지는 아래 미확인)
- ✅ 앱이 받는 쪽: adb로 Gleo Action과 같은 Intent를 보내 `service`(브로드캐스트)·`activity`(화면) 둘 다 `ACTION_PARAM`을 받았다
  - 막 설치한 앱은 정지(stopped) 상태라 브로드캐스트가 안 갔다. `-f 0x20`(정지 앱 포함)으로 보내야 받았다. Gleo가 이 플래그를 붙이는지는 미확인 → 앱을 한 번 실행해 두거나 `activity`로 받는 게 안전
- ✅ 앱이 돌려주는 쪽: 콜백 브로드캐스트가 Gleo에 닿는다(`processReceiver(): APP_ACTION_RESPONSE`). 다만 Gleo가 먼저 부른 호출이 없어서 `Reject AppActionResponse`로 버렸다(이유 문구는 없음, `pendingAction`으로 짝을 맞추는 것으로 보임)
- Gleo는 음성 전용이다. 글로 묻는 화면은 찾지 못했다. 에뮬레이터는 인터넷에 연결돼 있고, Gleo 기록 동기화는 `SDK Server Sync Fail`
- 미확인: 실제 음성 → Gleo LLM이 우리 함수를 고르는지 → 콜백을 받아들여 말하는지. 맥 마이크를 에뮬레이터에 연결해야 시험할 수 있다
- 미확인: 앱 마켓·실차에서 검토·승인 절차, 콜백 브로드캐스트를 받는 쪽 권한(Gleo가 서명 권한 `ai.umos.gleo.permission.APP_ACTION`을 선언해 두었다)

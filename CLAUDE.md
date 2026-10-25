# 프로젝트 규칙

Pleos Connect(현대자동차그룹 차량 인포테인먼트)에서 동작하는 차량용 앱 "Road Sensor"(가칭) 저장소다. 차량의 와이퍼·라이트·브레이크 신호를 익명으로 모아 도로 상태 지도와 진행 방향 알림을 보여준다.

## 기본

- 대화와 문서는 한국어로 쓴다. 코드 식별자는 영어로 쓴다.
- 진행 상황은 `docs/progress.md`, Pleos SDK 사실과 미해결 질문은 `docs/pleos/`에 있다. 작업 전에 관련 문서를 먼저 읽는다.

## 블라인드 심사

- 이 저장소의 어떤 파일에도 실명, 소속(학교·회사), 개인 계정 정보를 넣지 않는다. 대상: 코드, 주석, 문서, 커밋 메시지, 패키지명, 앱 화면, 리소스.
- git 작성자는 저장소 단위(`git config --local`)의 익명 값만 쓴다.
- 커밋 전에 스테이징된 변경에 개인정보가 없는지 확인한다.

## Pleos API

- Pleos는 비공개 신규 플랫폼이다. API를 추측해서 쓰지 않는다.
- `docs/pleos/`에 정리된 내용과, 받은 SDK를 `javap`로 확인한 시그니처만 쓴다. 문서와 실제 SDK가 다른 곳이 있다(`docs/pleos/vehicle-sdk.md`의 "실제 SDK와 문서가 다른 점").
- 문서에 없는 API가 필요하면 "확인 필요"로 표시하고 먼저 묻는다.

## 구조 원칙

- 차량 연동(포그라운드 서비스) → 상태 스토어 → 렌더러로 나눈다. 렌더러(WebView 또는 Compose)는 교체할 수 있어야 한다.
- 차량 신호 수집과 서버 전송은 포그라운드 서비스에만 둔다. 화면은 상태를 구독해서 그리기만 한다.
- 위치 공급원은 인터페이스로 분리한다(NaviHelper / 데모 궤적 재생). 에뮬레이터에서는 NaviHelper 위치가 고정이다.
- 상태가 바뀔 때만 다시 그린다. 변화가 없으면 렌더 루프를 멈춘다.
- 구조를 정하거나 바꿀 때는 이유를 짧게 설명하고, 큰 결정은 먼저 확인받는다.

## 차량 정책 (필수)

- 주행/정차 판단은 `CarUxRestrictions`만 따른다. 주행 중에는 애니메이션을 멈추고 한 줄 알림만 보여준다.
- 주행 중 표시가 필요한 Activity에는 `distractionOptimized` 메타데이터를 선언한다.
- 최상단 화면에서 Back 하드키를 누르면 종료하지 않고 `moveTaskToBack(true)`로 숨긴다.
- 필수 조작은 터치만으로 끝나야 한다. CCP(다이얼)는 지원하지 않는다고 선언한다(`com.hmg.rotary.ROTARY_SUPPORTED`=false).
- 위치·차량 데이터는 익명화하고, 필요한 것만 수집한다. 차량번호, VIN, 계정, 정확한 경로는 저장하지 않는다. 로그에 원본 좌표를 남기지 않는다(데모·디버그 빌드 제외).

## 검증

- 에뮬레이터: AVD `Pleos_Connect_v2.1.0`, adb 시리얼 `emulator-5554`.
- 앱은 AAOS **사용자 10**에서 실행된다. 런타임 권한은 `adb shell pm grant --user 10 <패키지> <권한>`으로 준다.
- `adb install -r`, `adb shell screencap`, `adb logcat`으로 직접 확인한다. Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`) 조작은 사용자가 한다.
- 빌드: `JAVA_HOME`=Android Studio 내장 JBR, `ANDROID_HOME`=Android SDK. AGP 9.4.0, Gradle 9.6.1.
- 앱: `android/`에서 `./gradlew :core:test`(판정·스토어 단위 테스트), `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease`(R8. 서명 키는 저장소 밖 `~/.android-keys/road-sensor/keystore.properties`, 사용자가 만든다. 에뮬레이터 시험만 `-ProadSensor.debugSignRelease=true`).
- 서버: `server/`에서 `npm start`(localhost:8080), `npm test`, `npm run typecheck`. 에뮬레이터는 `adb reverse tcp:8080 tcp:8080`으로 붙는다.
- 서버 로그에는 메서드·경로·상태만 남긴다. 임시 ID, 격자, IP를 로그에 넣지 않는다.
- 시뮬레이터: `simulator/`에서 `npm run rain`(비 구간 가상 차, 1분마다 업로드), `npm run kma -- --snapshot <파일>`(기상청 강수 재생, 가상 차 2,500대), `--backfill 24`(지난 24시간 녹화, 초단기실황은 최근 1일만 준다) 또는 `--live`(인증키 `simulator/.env`의 `KMA_SERVICE_KEY`, 사용자가 발급·입력), `npm test`. 자세한 내용은 `docs/kma-rain.md`.
- 지도 그리기: `shared/`에서 `npm run build:car`(→ `android/app/src/main/assets/web/`, 고친 뒤 앱을 다시 빌드), `npm test`, `npm run typecheck`. 디버그 빌드 WebView는 `adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>`로 들여다볼 수 있고, `roadSensor.stress(격자 수, 초)`로 fps를 잰다.
- 끝까지 확인하는 순서: 서버 `npm start` → `adb reverse tcp:8080 tcp:8080` → 앱 설치·실행 → 시뮬레이터 `npm run rain` → Virtual Console에서 D + ACCEL.

## 오픈소스

- OkHttp 4.12.0 (Apache License 2.0), Fastify, @fastify/websocket, PixiJS 8.22.0, esbuild(빌드 도구) (MIT). 번들 `map.js`에 함께 들어간 Pixi 의존 라이브러리 포함. 기상청 자료(공공데이터포털). 데모 궤적은 OpenStreetMap(ODbL, OSRM으로 계산). 출처 표는 README에 있다. README를 쓸 때 출처를 남긴다.

## 기타

- `spikes/`는 검증용 실험 코드다. 본 앱에서 그대로 가져다 쓰지 않는다.
- 오픈소스를 쓰면 라이선스를 지키고 README에 출처를 남긴다.
- 서명 키, API 키, `.env`는 커밋하지 않는다.

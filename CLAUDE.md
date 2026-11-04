# 프로젝트 규칙

Pleos Connect(현대자동차그룹 차량 인포테인먼트)에서 동작하는 차량용 앱 "Fuel Mate"(가칭) 저장소다. 내 차의 연료·배터리·운전 신호를 읽어 주유·충전비를 줄인다. 중심 기능은 진행 방향의 주유·충전소 가운데 "실제 이득"이 있는 곳 추천이고, 운전 습관 비용은 "이번 운행 요약" 한 줄로 보여 준다(docs/pivot-fuel.md 12·13장).

## 기본

- 대화와 문서는 한국어로 쓴다. 코드 식별자는 영어로 쓴다.
- 진행 상황은 `docs/progress.md`, 주제 결정 근거는 `docs/pivot-fuel.md`, Pleos SDK 사실과 미해결 질문은 `docs/pleos/`에 있다. 작업 전에 관련 문서를 먼저 읽는다.
- 이전 주제(Road Sensor)의 코드는 로컬 태그 `archive/road-sensor`에 있다. 필요하면 거기서 꺼내 보고, 그대로 되살리지 않는다.

## 블라인드 심사

- 이 저장소의 어떤 파일에도 실명, 소속(학교·회사), 개인 계정 정보를 넣지 않는다. 대상: 코드, 주석, 문서, 커밋 메시지, 패키지명, 앱 화면, 리소스.
- git 작성자는 저장소 단위(`git config --local`)의 익명 값만 쓴다.
- 커밋 전에 스테이징된 변경에 개인정보가 없는지 확인한다.

## Pleos API

- Pleos는 비공개 신규 플랫폼이다. API를 추측해서 쓰지 않는다.
- `docs/pleos/`에 정리된 내용과, 받은 SDK를 `javap`로 확인한 시그니처만 쓴다. 문서와 실제 SDK가 다른 곳이 있다(`docs/pleos/vehicle-sdk.md`의 "실제 SDK와 문서가 다른 점").
- 문서에 없는 API가 필요하면 "확인 필요"로 표시하고 먼저 묻는다.
- 에뮬레이터 차량은 전기차다. 연료(`Fuel`)·연비·전비 API는 에뮬레이터에서 지원되지 않고, 주행·충전해도 에너지 값이 바뀌지 않는다(docs/pivot-fuel.md). 연료·운행 소비는 어댑터 뒤 데모 공급원으로 시연한다.

## 구조 원칙

- 차량 연동(포그라운드 서비스) → 상태 스토어(`:core`, 순수 Kotlin) → 화면으로 나눈다. 판정·계산은 `:core`에 두고 단위 테스트로 확인한다.
- 차량 신호 수집과 외부 API 호출은 포그라운드 서비스에만 둔다. 화면은 상태를 구독해서 그리기만 한다.
- 에너지는 "에너지 비용(원)"으로 일반화하고 연료·전기는 어댑터로 나눈다.
- 위치 공급원은 인터페이스로 분리한다(NaviHelper / 데모 궤적 재생). 에뮬레이터에서는 NaviHelper 위치가 고정이다.
- 상태가 바뀔 때만 다시 그린다.
- 구조를 정하거나 바꿀 때는 이유를 짧게 설명하고, 큰 결정은 먼저 확인받는다.

## 차량 정책 (필수)

- 주행/정차 판단은 `CarUxRestrictions`만 따른다. 주행 중에는 큰 글자 한 줄 + 보조 한 줄과 내비 경유지 추가처럼 단순한 것만 보여 준다. 탐색·목록·설정은 정차 중에만. `LIMIT_CONTENT` 규제가 켜지면 한 줄만 보여 준다.
- 주행 중 표시가 필요한 Activity에는 `distractionOptimized` 메타데이터를 선언한다.
- 최상단 화면에서 Back 하드키를 누르면 종료하지 않고 `moveTaskToBack(true)`로 숨긴다.
- 필수 조작은 터치만으로 끝나야 한다. CCP(다이얼)는 지원하지 않는다고 선언한다(`com.hmg.rotary.ROTARY_SUPPORTED`=false).
- 위치·운행 기록은 차 안에만 저장하고, 필요한 것만 모은다. 차량번호, VIN, 계정은 저장하지 않는다. 외부 API에는 검색에 필요한 위치만 보낸다. 로그에 원본 좌표를 남기지 않는다(데모·디버그 빌드 제외).
- 운전 습관 비용은 추정값이다. "약"을 붙이고 계산 방법을 화면에서 열어 볼 수 있게 한다. 과장하지 않는다.

## 검증

- 에뮬레이터: AVD `Pleos_Connect_v2.1.0`, adb 시리얼 `emulator-5554`.
- 앱은 AAOS **사용자 10**에서 실행된다. 런타임 권한은 `adb shell pm grant --user 10 <패키지> <권한>`으로 준다.
- `adb install -r`, `adb shell screencap`, `adb logcat -s FuelMate`로 직접 확인한다. Virtual Console(`adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`) 조작은 사용자가 한다.
- 빌드: `JAVA_HOME`=Android Studio 내장 JBR, `ANDROID_HOME`=Android SDK. AGP 9.4.0, Gradle 9.6.1.
- 앱: `android/`에서 `./gradlew :core:test`(판정·계산·스토어 단위 테스트), `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease`(R8. 서명 키는 저장소 밖 `~/.android-keys/fuel-mate/keystore.properties`, 사용자가 만든다. 에뮬레이터 시험만 `-PfuelMate.debugSignRelease=true`).
- 실행: `adb shell am start --user 10 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n io.fuelmate/.ui.MainActivity`
- 연료·배터리·내비 API 시험: `spikes/fuel-probe`(docs/pivot-fuel.md 14장).

## 오픈소스·데이터

- 데모 궤적은 OpenStreetMap(ODbL), OSRM으로 계산. 출처는 궤적 파일과 README에 남긴다.
- 공공데이터를 쓰면 출처와 이용 조건(공공누리 유형 등)을 README에 남긴다. 오피넷 데이터는 수익 활용 시 석유공사 사전 협의가 필요하다(docs/pivot-fuel.md 5장).
- 오픈소스를 쓰면 라이선스를 지키고 README에 출처를 남긴다.

## 기타

- `spikes/`는 검증용 실험 코드다. 본 앱에서 그대로 가져다 쓰지 않는다.
- 서명 키, API 키, `.env`는 커밋하지 않는다.

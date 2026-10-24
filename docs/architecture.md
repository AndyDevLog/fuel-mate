# 앱 구조 설계

- 상태: 2026-10-24 결정 1~5번 확정(8장). "결정 필요" 표시가 남은 항목(렌더러, 패키지명)은 아직 정하지 않았다.
- 근거: `docs/pleos/`의 SDK 정리와 `spikes/vehicle-probe`로 확인한 에뮬레이터 동작.
- 범위: 11/14 제출용 MVP. 확장 기능(우회 경로, 음성, Gleo)은 자리만 남긴다.

---

## 1. 전체 그림

```
┌──────────────── 차량 (Pleos Connect, Android 14) ────────────────┐
│                                                                   │
│  [수집 서비스]  포그라운드 서비스, 화면과 무관하게 계속 동작         │
│   ├ VehicleAdapter   Pleos Vehicle SDK + AAOS Car API              │
│   ├ LocationSource   NaviHelper | 데모 궤적                         │
│   ├ Detector         신호 → 도로 관측값 (비·어두움·급정거)          │
│   ├ Uploader         익명 관측값 → 서버 (오프라인 큐)               │
│   └ MapFeed          서버 → 주변 격자 상태                          │
│            │                                                      │
│            ▼                                                      │
│  [상태 스토어]  AppState 하나, StateFlow로 배포                     │
│            │                                                      │
│            ▼                                                      │
│  [화면]  MainActivity                                              │
│   ├ 정차: 렌더러(도로 지도 / 내 차)  ← WebView 또는 Compose로 교체   │
│   └ 주행: 한 줄 알림 (네이티브 고정)                                │
└───────────────────────────────┬───────────────────────────────────┘
                                │ HTTPS 업로드 / WebSocket 구독
┌───────────────────────────────▼───────────────────────────────────┐
│ [서버]  격자별 집계 → 구독자에게 변경분 배포                          │
│   ▲ 가상 차량 시뮬레이터 (기상청 강수 기반, 같은 API로 업로드)        │
│   ▼ 관제 웹 대시보드 (React + Pixi, 같은 WebSocket 구독)             │
└───────────────────────────────────────────────────────────────────┘
```

이렇게 나눈 이유는 네 가지다.
- **수집은 서비스에, 화면은 구독만.** 백그라운드 앱은 8초 만에 동결되지만, 포그라운드 서비스는 계속 동작하는 걸 확인했다. 주행 중에 화면이 가려져도 신호 수집과 업로드가 끊기면 안 된다.
- **스토어가 유일한 진실.** 서비스, 화면, 렌더러가 같은 상태를 본다. 렌더러를 바꿔도 스토어 아래는 그대로다.
- **주행 모드 화면은 렌더러 밖에 둔다.** 주행 중 화면은 큰 글자 한 줄이 전부다. WebView 정책이 어떻게 나오든 주행 규제 대응은 흔들리지 않게 네이티브로 고정한다.
- **서버, 시뮬레이터, 대시보드가 같은 프로토콜을 쓴다.** 진짜 차 1대와 가상 차 수천 대가 같은 길로 들어오므로, 데모가 실제 구조를 그대로 보여준다.

---

## 2. 차량 앱 모듈

| Gradle 모듈 | 내용 | Android 의존 |
| --- | --- | --- |
| `:core` | 데이터 모델, 판정 로직(Detector), 격자 변환, 상태 스토어(리듀서), 프로토콜 타입 | 없음 (순수 Kotlin/JVM) |
| `:app` | 수집 서비스, VehicleAdapter, LocationSource, Uploader·MapFeed, Activity, 렌더러 | 있음 |

- 왜 두 개인가: 판정 로직(급정거 기준, 비 강도 정규화, 진행 방향 판정)은 PT에서 질문받을 핵심이다. 이걸 Android 없이 **JVM 단위 테스트**로 검증하려고 `:core`를 분리한다. 모듈을 더 쪼개면 일정(약 50시간)에 비해 관리 비용이 크다.
- 렌더러는 모듈이 아니라 `:app` 안의 인터페이스 + 구현 두 벌(`render/web`, `render/compose`)로 둔다. A/B 결정 후 하나만 남긴다.
- 패키지: `io.roadsensor` (스파이크와 같은 접두사). **결정 필요:** 앱 이름이 정해지면 바꿀지.

```
:core  io.roadsensor.core
  model/      VehicleSnapshot, Observation, CellState, AppState, DriveMode ...
  detect/     RainLevel, HardBrakeDetector, DarknessRule
  geo/        Geohash, Heading, AheadFilter(진행 방향 부채꼴, 목적지 띠)
  store/      Action, reduce(state, action), Store(StateFlow)
  protocol/   업로드·구독 메시지 (kotlinx.serialization)

:app   io.roadsensor
  service/    CollectorService (포그라운드), ServiceLocator
  vehicle/    PleosVehicleAdapter, CarApiAdapter (UX 규제·속도·기어)
  location/   LocationSource, NaviHelperSource, DemoTrackSource
  net/        Uploader(큐·재시도), MapFeed(WebSocket)
  ui/         MainActivity, DriveModeView, PermissionFlow
  render/     MapRenderer(인터페이스), web/, compose/
```

---

## 3. 수집 서비스

`CollectorService`: 포그라운드 서비스. 앱을 처음 열 때 시작하고, 앱을 종료할 때(사용자가 끄기) 멈춘다.

### 3-1. VehicleAdapter (차량 신호 → `VehicleSnapshot`)

| 신호 | 출처 | 받는 방식 | 스파이크 결과 |
| --- | --- | --- | --- |
| 와이퍼 켜짐 | `Wiper.registerWiperState(FRONT_WINDSHIELD)` | 구독 | ON/OFF만 옴 |
| 와이퍼 강도 | `Wiper.getCurrentWiperSwitch` | **조회**(구독 API 없음). 와이퍼가 켜져 있는 동안 2초마다 | Low=LEVEL_1, High=LEVEL_3, Auto=AUTO |
| 하향등·미등 | `Light.registerLowBeamLightState`, `registerTailLampLightState` | 구독 | 같이 바뀜 |
| 브레이크 페달 | `Brake.registerBrakePedalStatus` | 구독 | true/false |
| 속도 | AAOS `PERF_VEHICLE_SPEED` | 구독, 약 0.2초 간격 | 런타임 권한 `CAR_SPEED` 필요 |
| 기어 | AAOS `GEAR_SELECTION` | 구독 | P=4, D=8 |
| 주행 규제 | AAOS `CarUxRestrictionsManager` | 구독 | D에서 `NO_VIDEO`, `NO_SETUP` |
| 경고등 | `Safety.registerTelltales` | 구독, **있으면 쓰고 없으면 숨김** | 에뮬레이터만 확인됨 |

- 어댑터는 SDK 타입을 `:core` 모델로 바꿔서 스토어에 `Action`으로 넣기만 한다. 판정은 하지 않는다.
- `initialize()`에는 연결 콜백이 없다. 첫 호출이 실패하면 1초 간격으로 최대 5번 재시도한다.
- 오류 코드는 리플렉션 헬퍼로 읽는다(`ErrorCode`가 컴파일 경로에 없음).

### 3-2. LocationSource (위치)

```kotlin
interface LocationSource {
    val fixes: Flow<GeoFix>          // lat, lon, heading, speedMps, timeMs
    fun start(); fun stop()
}
```

- `NaviHelperSource`: `getCurrentLocationInfo()`를 2초마다 요청한다(자동 푸시가 확인되지 않았다). 목적지(`DestinationInfo`)도 받는다.
- `DemoTrackSource`: 미리 만든 궤적(JSON: 좌표 + 시간)을 재생한다. 차량 속도에 맞춰 진행시킬지, 궤적 속도대로 갈지 옵션으로 둔다.
- 에뮬레이터는 NaviHelper 위치가 고정이다(GNSS NMEA 체크섬 버그). 그래서 **데모 빌드는 `DemoTrackSource`가 기본**이다. 설정 화면(정차 중)에서 바꿀 수 있다.

### 3-3. Detector (신호 → 도로 관측값)

`:core`의 순수 함수다. 초기 기준값은 모두 상수 하나로 모아 두고, 시뮬레이터와 에뮬레이터로 조정한다.

| 관측 | 판정 | 초기 기준 |
| --- | --- | --- |
| **비** | 와이퍼 ON. 강도는 스위치 값으로 정규화 | 약 = `MIST`·`INTERMITTENT_*`·`CONTINUOUS_LEVEL_1` / 중 = `LEVEL_2~3` / 강 = `LEVEL_4~5` / 미상 = `AUTO` |
| **어두움** | 하향등 ON (주간주행등은 제외) | 터널 오검출을 줄이려고 같은 격자에서 30초 이상 유지될 때만 |
| **급정거** | 브레이크 true인 동안 감속도가 기준을 넘음 | 시작 속도 ≥ 20km/h, 감속도 ≥ 3.5m/s²(약 0.36g)가 0.5초 이상 |

- 비와 어두움은 **상태**다. 차가 격자를 지나갈 때마다 "이 격자에서 와이퍼가 켜져 있었다/꺼져 있었다"를 모두 보낸다. 꺼져 있다는 정보도 보내야 서버가 "비가 안 온다"와 "데이터가 없다"를 구분할 수 있다.
- 급정거는 **사건**이다. 일어난 격자와 진행 방향(8방위)만 보낸다.

### 3-4. Uploader (차 → 서버)

- 격자가 바뀔 때, 상태(비·어두움)가 바뀔 때, 급정거가 일어날 때 관측값을 만든다.
- 10초마다 모아서 보낸다. 실패하면 디스크 큐(최대 500개, 30분 지나면 버림)에 쌓아 두고 지수 백오프로 다시 보낸다. 주행 중 네트워크 끊김 대비는 Pleos 정책 요구사항이다.
- 정차 중(기어 P, 속도 0)에는 격자가 안 바뀌므로 거의 안 보낸다.

### 3-5. MapFeed (서버 → 차)

- WebSocket으로 내 주변 격자를 구독한다. 구독 단위는 큰 타일(geohash 5자리, 약 4.9km)이고, 내 위치를 포함한 타일과 주변 8칸, 총 9칸이다.
- 받은 `CellState` 변경분을 스토어에 넣는다. 연결이 끊기면 "오프라인" 상태로 표시하고 재연결한다.

---

## 4. 상태 스토어

단방향 흐름이다: `Action` → `reduce(state, action)` → 새 `AppState` → `StateFlow`.

```kotlin
data class AppState(
    val vehicle: VehicleSnapshot,      // 와이퍼(켜짐·강도), 하향등, 브레이크, 속도, 기어, 경고등
    val drive: DriveMode,              // PARKED | DRIVING (CarUxRestrictions 기준) + activeRestrictions
    val location: GeoFix?,             // 최근 위치 (원본 좌표는 화면용, 업로드하지 않음)
    val destination: GeoPoint?,
    val cells: Map<String, CellState>, // 주변 격자 상태 (서버에서 받음)
    val alerts: List<RoadAlert>,       // 진행 방향 앞 이벤트 (거리순)
    val link: LinkStatus,              // 서버 연결·대기 중인 업로드 수
    val contribution: Contribution,    // 오늘 보낸 관측 수 (기여감)
)
```

- **왜 하나의 상태인가.** 화면이 무엇을 보여줘야 하는지는 이 값 하나로 결정된다. 리듀서가 순수 함수라서 "이런 신호가 들어오면 이런 화면"을 단위 테스트로 증명할 수 있다.
- `alerts`는 리듀서가 매번 다시 계산한다. 입력은 `location`과 `cells`, 판정은 `AheadFilter`가 한다.
  - 진행 방향 앞 5km 부채꼴(±30°)을 보고, 목적지가 있으면 목적지 방향 직선 띠(좌우 1km)도 본다.
  - 거리는 직선거리이고, 화면에는 "약 2km 앞"처럼 표시한다.
- 스토어는 서비스 안에 산다. Activity는 같은 프로세스에서 서비스에 바인딩해 `StateFlow`를 받는다.

### 렌더 모델 (화면에 넘기는 값)

렌더러는 `AppState`를 직접 받지 않고, 화면에 필요한 것만 추린 `RenderModel`을 받는다.

```kotlin
data class RenderModel(
    val screen: Screen,                // MAP | MY_CAR
    val center: GeoPoint?, val heading: Float,
    val cells: List<CellView>,         // 격자, 종류별 강도 0~1
    val alerts: List<AlertView>,
    val myCar: MyCarView,              // 트윈용: 와이퍼·라이트·브레이크·경고등
)
```

- `distinctUntilChanged()`로 **바뀔 때만** 렌더러에 보낸다. 바뀐 게 없으면 렌더 루프를 멈춘다(헤드유닛 자원 절약, PT 포인트).
- 주행 중(`drive = DRIVING`)에는 렌더러를 멈추고(WebView는 `onPause()`, 애니메이션 정지) `DriveModeView`만 보여준다.

---

## 5. 렌더러

```kotlin
interface MapRenderer {
    val view: android.view.View
    fun render(model: RenderModel)
    fun pause(); fun resume()
}
```

| | A안: WebView + Pixi | B안: Compose Canvas |
| --- | --- | --- |
| 확인된 것 | Pleos WebView(Chromium 131)에서 WebGL2 동작 | API 34라서 AGSL 사용 가능 |
| 장점 | Pixi·GSAP 경험을 그대로 쓴다. 관제 웹과 그리기 코드를 공유한다 | 심사 정책 위험이 없다. 네이티브 성능 |
| 위험 | 심사에서 WebView UI가 허용되는지 모른다 | 새로 배워야 해서 일정 부담 |
| Kotlin ↔ 그림 | `RenderModel`을 JSON으로 `postWebMessage` | 직접 호출 |

- **결정 필요 (11/2까지, 설명회 후):** A/B. 어느 쪽이든 `MapRenderer` 아래만 바뀐다.
- A안을 고르면, 그리기 코드(TypeScript + Pixi)를 관제 웹과 같은 패키지로 공유한다. 차 화면은 그 패키지를 WebView용으로 번들한 결과물을 `assets/`에 넣는다(오프라인 동작).

---

## 6. 서버 프로토콜

### 6-1. 격자와 익명화

- 격자는 **geohash 7자리**(약 150m × 150m)로 한다.
  - 이유 1: 급정거는 교차로 단위로 의미가 있다.
  - 이유 2: geohash는 문자열 앞부분만 자르면 상위 타일이 되므로, 구독 타일(5자리)과 같은 체계를 쓸 수 있다.
- 차가 보내는 것: 격자 ID, 분 단위 시각, 관측값, 임시 ID.
- 보내지 않는 것: 원본 좌표, 경로, 출발지·목적지, 차량번호, VIN, 계정, 고정 기기 ID.
- **임시 ID**: 시동을 걸 때마다, 그리고 30분마다 새로 만드는 무작위 값이다. 서버는 "같은 격자에서 같은 차가 두 번 세어지지 않게" 하는 데만 쓴다.
- **k-익명성**: 서버는 서로 다른 임시 ID가 3개 이상 모인 격자만 배포한다. 차 한 대의 흔적은 지도에 나타나지 않는다.
  - 예외: 데모의 "내 차 신호 추가" 장면은 내 차 화면에만 내 기여를 표시하는 방식으로 보여준다.
- 확정(2026-10-24): geohash 7자리, 임시 ID 30분 교체, k=3.

### 6-2. 메시지 (JSON, `v: 1`)

**업로드** `POST /v1/observations`

```json
{
  "v": 1,
  "tid": "f3a9c1e2",
  "src": "car",
  "obs": [
    { "cell": "wydm9qk", "t": 29870412, "kind": "rain",  "level": 2 },
    { "cell": "wydm9qk", "t": 29870412, "kind": "dark",  "on": false },
    { "cell": "wydm9qs", "t": 29870413, "kind": "brake", "dir": 3 }
  ]
}
```

- `t`: 유닉스 분(분 단위로 뭉갬). `level`: 0=꺼짐, 1=약, 2=중, 3=강, 9=미상(AUTO). `dir`: 0~7(북에서 시계 방향 45° 단위).
- `src`: `car`(실차·에뮬레이터) 또는 `sim`(시뮬레이터). 집계는 똑같이 하고, 대시보드에서만 구분해서 보여준다. 데모의 정직성을 위해서다.
- 응답: `202 { "accepted": 3 }`.

**구독** `WS /v1/stream`

```json
→ { "type": "subscribe", "tiles": ["wydm9", "wydm8", "..."] }
← { "type": "cells", "updated": [
      { "cell": "wydm9qk", "n": 7, "rain": 0.71, "rainLevel": 2, "dark": 0.14,
        "brake": 3, "updatedAt": 29870415 } ],
    "expired": ["wydm9qe"] }
```

- `n`: 최근 15분 동안 이 격자를 지난 서로 다른 차 수.
- `rain`: 그중 와이퍼를 켠 차의 비율. `rainLevel`: 켠 차들의 강도 중앙값. `dark`: 하향등 비율. `brake`: 최근 15분 급정거 수.
- 서버는 1초마다 바뀐 격자만 모아서 보낸다. 15분 동안 새 관측이 없는 격자는 `expired`로 보낸다.

### 6-3. 서버 구현

- 확정(2026-10-24): **Node.js + TypeScript (Fastify + ws)**.
  - 이유 1: 가장 익숙한 언어다.
  - 이유 2: 관제 웹(React)과 프로토콜 타입을 공유할 수 있다.
  - 이유 3: 시뮬레이터도 같은 저장소에서 TypeScript로 쓴다.
- 저장소: MVP는 메모리(격자별 15분 슬라이딩 창). 재시작하면 비워진다. 데모에는 충분하다.
- 실행: 데모 때는 맥에서 로컬로 실행한다. 에뮬레이터는 `adb reverse tcp:8080 tcp:8080`으로 `localhost`에 붙는다. 공개 배포는 2단계 이후에 필요하면 한다.
- 저장소 구조(예정):

```
android/      차량 앱 (:core, :app)
server/       집계 서버 (TypeScript)
simulator/    가상 차량 (TypeScript, 기상청 강수 데이터)
dashboard/    관제 웹 (React + Pixi)
shared/       프로토콜 타입, (A안이면) 지도 그리기 코드
spikes/       검증용 실험 코드
docs/
```

---

## 7. 화면과 차량 정책

| 화면 | 언제 | 내용 | 정책 |
| --- | --- | --- | --- |
| 도로 지도 (메인) | 정차 | 주변 격자 색, 진행 방향 앞 알림 목록 | 렌더러. 바뀔 때만 그림 |
| 내 차 | 정차 | 차 모형 + 와이퍼·라이트·브레이크·경고등, 오늘 기여 수 | 렌더러 |
| 주행 모드 | `CarUxRestrictions`가 DO를 요구할 때 | 큰 글자 한 줄: "약 2km 앞 비 (강)" | 네이티브. 애니메이션 없음. `LIMIT_STRING_LENGTH`면 더 짧게 |
| 권한·설정 | 정차 | 속도 권한 요청, 위치 사용 안내, 데모 모드 | `NO_SETUP`이면 진입 막음 |

- Activity에 `distractionOptimized=true`를 선언한다. 선언하지 않으면 주행 중에 차단된다(확인함).
- Back 하드키는 `moveTaskToBack(true)`, CCP는 미지원 선언, 필수 조작은 모두 터치로 한다.
- 포그라운드 서비스 알림: "도로 정보 공유 중". 탭하면 앱이 열린다.

---

## 8. 결정 사항

| # | 항목 | 내용 | 상태 |
| --- | --- | --- | --- |
| 1 | Gradle 모듈 | `:core`(순수 Kotlin) + `:app` 두 개 | ✅ 확정 2026-10-24 |
| 2 | 주행 모드 화면 | 렌더러와 무관하게 네이티브로 고정 | ✅ 확정 2026-10-24 |
| 3 | 격자·익명화 | geohash 7자리, 임시 ID 30분 교체, k=3 | ✅ 확정 2026-10-24 |
| 4 | 서버 스택 | Node.js + TypeScript (Fastify + ws), 메모리 저장 | ✅ 확정 2026-10-24 |
| 5 | 저장소 구조 | `android/ server/ simulator/ dashboard/ shared/` | ✅ 확정 2026-10-24 |
| 6 | 렌더러 | A(WebView + Pixi) / B(Compose) | 결정 필요, 11/2까지 (설명회 후) |
| 7 | 패키지명 | `io.roadsensor` 유지 또는 앱 이름에 맞춰 변경 | 결정 필요, 첫 release 빌드 전 |

- 판정 기준값(급정거 감속도, 비 강도 구간, 어두움 유지 시간, 부채꼴 크기)은 확정 대상이 아니라 조정 가능한 초기값이다.

## 9. 아직 모르는 것 (설계에 미치는 영향)

- 포그라운드 서비스와 상시 알림이 Pleos 정책상 허용되는가 → 막히면 "앱이 화면에 있을 때만 수집"으로 줄인다. 구조는 같다.
- DO 선언 허용 여부 → 막히면 주행 모드 화면 없이 정차 중 알림만 남긴다.
- 경고등 퍼미션 → 이미 "있으면 표시"로 설계했다.
- WebView 허용 여부 → 렌더러 결정(#6)에서 처리한다.

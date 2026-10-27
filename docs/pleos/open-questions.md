# 확인 필요 항목

2026-10-24 문서 정리 기준. 해결되면 결과와 확인 방법을 적고 ✅로 바꾼다.

## Playground 콘솔 SDK 상태 (2026-10-24 확인)

| SDK | 상태 |
| --- | --- |
| Vehicle State | 사용 가능 (항목 11개) |
| NaviHelper | 사용 가능 (경로 정보 조회, 경로 탐색 설정) |
| FusedLocation, TextToSpeech, SpeechToText, Vehicle Control | Coming Soon |

Vehicle State 11개 항목(모두 사용 가능): 차량 제원, 차량 주행(브레이크 페달 포함), 도어/트렁크/프렁크, EV·연료, 공조, 내부/외부 라이트(안개등·상향등·하향등·후미등·방향지시등), 차량 안전(경고등: 안전벨트·브레이크·에어백·엔진오일·타이어 공기압), 좌석, 사이드미러, 통행료 시스템, 창문(잠금·열림·**와이퍼 모드**).

- ✅ 와이퍼는 "창문 상태 조회"에 포함되어 사용 가능하다. API 문서에서 Wiper가 `CAR_WINDOWS` 퍼미션을 쓰는 것과 일치한다.
- MVP 신호 3종(와이퍼 = 창문, 라이트, 브레이크 페달 = 차량 주행)이 콘솔 기준으로 모두 사용 가능하다.
- 차량 안전이 "사용 가능"으로 나와도, API 문서상 경고등 API는 `PRIVATE_CAR_SAFETY`(signature|privileged)다. 콘솔에 표시된 상태가 경고등 API까지 쓸 수 있다는 뜻인지는 에뮬레이터 호출로 확인한다(아래 1번).
- 브리프 계획대로 확장 기능(음성 안내, 정확한 위치)은 Coming Soon SDK에 의존한다. MVP에는 영향이 없다.

## 기획에 영향이 큰 것

### 1. 경고등(Telltale): 에뮬레이터에서는 동작한다. 실차·심사 기준은 확인 필요
- 문서상 `Safety.registerTelltales`, `getTelltalesInfo`는 `PRIVATE_CAR_SAFETY`(signature|privileged)를 요구한다.
- 2026-10-24 에뮬레이터 결과(`spikes/vehicle-probe`): 매니페스트에 `CAR_SAFETY`와 `PRIVATE_CAR_SAFETY`를 선언하고 debug 서명으로 설치했다. **두 퍼미션 모두 `granted=true`였다.** `checkTelltalesCapabilities` → `READ`, `getTelltalesInfo`와 `registerTelltales` 모두 정상 응답했다(현재 경고등 0개).
- 해석: 에뮬레이터는 보호 수준을 느슨하게 적용하는 것으로 보인다. 실차나 앱마켓 서명 기준에서도 허용되는지는 알 수 없다.
- 남은 일: 문의 메일에 "참가자 앱이 PRIVATE_CAR_SAFETY 경고등 API를 쓸 수 있는가"를 넣는다. 답을 받기 전까지 MVP에서는 경고등을 "있으면 표시" 수준의 보조 기능으로 두고, 핵심 신호는 와이퍼·라이트·브레이크 3종으로 설계한다.
- 워셔액 부족(`hasLowWasherFluidWarning`)은 에뮬레이터에서 미지원(`property ID ... not supported: 0x2120071e`)이라 대안으로 쓸 수 없다.

### 2. ✅ 내비 경로 선형(좌표 목록)을 받을 수 없다 → 진행 방향 기준으로 결정 (2026-10-24)
- NaviHelper 데이터 모델에 polyline이 없다. 현재 위치, 목적지, 경유지 좌표, 경로상 비율(`ratioOnRoute`), 혼잡 구간 offset만 있다.
- 영향: "내 경로 12km 지점부터 비"를 정확히 그릴 수 없다.
- 검토한 대안
  - A. 현재 위치 → 목적지 직선 주변 띠(corridor) 안의 이벤트만 보여준다. 가장 단순하지만 부정확하다.
  - B. 서버가 외부 경로 API(공공·오픈소스 라우팅)로 같은 출발지·목적지의 경로를 따로 계산한다. 정확도는 높아지지만 내비 경로와 다를 수 있다.
  - C. 주행 중 내 위치를 따라 "진행 방향 앞 N km"(heading 기준 부채꼴)만 본다. 경로 없이도 작동한다.
- **결정: C를 기본으로 하고, 목적지가 있으면 A로 보강한다.** B는 쓰지 않는다.
  - 이유: C는 `CurrentLocationInfo`의 `latitude`/`longitude`/`heading`만으로 작동한다. 그래서 목적지를 설정하지 않은 출퇴근길에도 쓸 수 있다. A는 목적지 좌표(`DestinationInfo`)만 있으면 되므로 출발 전에 미리 보여주는 데 쓴다. B는 외부 API 의존성이 생기고, 실제 내비 경로와 어긋날 수 있다는 약점이 있다.
  - 거리 표현: 경로상 거리를 알 수 없으므로 "2km 앞"은 직선거리로 계산한다. 화면 문구도 "약 2km 앞"처럼 근사치임을 드러낸다.
  - 초기값(에뮬레이터·시뮬레이터로 조정 예정): 부채꼴 반경 N = 5km, 진행 방향 ±30°, 직선 띠 폭 좌우 1km.
  - PT 예상 질문 "내비 경로와 정확히 맞나?"에 대한 답: 현재 SDK가 경로 선형을 제공하지 않아 진행 방향 기준으로 설계했다. 경로 선형이 공개되면 판정 함수만 교체하면 된다.

### 3. 주행 중 화면 표시를 위한 DO 선언 → 방법 확인됨 (2026-10-24)
- DO를 선언하지 않은 앱은 주행 중에 사용할 수 없다. 에뮬레이터에서 차단 화면이 뜨는 것을 확인했다.
- ✅ 선언 방법: AAOS 표준 `distractionOptimized` 메타데이터. 넣으면 주행 중에도 앱이 보인다(`driving-restriction.md`).
- 남은 질문: 3rd party 앱의 DO 선언이 앱마켓 심사에서 허용되는지 → 10/28 설명회에서 묻는다.

### 4. 앱 카테고리
- Pleos에는 "차량 관리" 카테고리가 없다. Mobility Services 또는 Maps/Navigation 중에서 고른다. 기획서 표현도 맞춰야 한다.

## 에뮬레이터 환경 (2026-10-24 확인)

Pleos Connect Emulator 2.1.0, Android 14 (API 34), `crp_avd_arm64`, M5 Pro 맥.

- ✅ **WebView 있음**: 시스템 WebView가 Pleos 전용 `ai.pleos.android.webview` 131.0.6752.0(Chromium 131)이다. 테스트 셸 `org.chromium.webview_shell`도 설치되어 있다.
- ✅ **WebGL2 동작**: WebView 테스트 셸에서 WebGL1·WebGL2 모두 사용 가능하다. 렌더러는 ANGLE → Android Emulator OpenGL ES Translator(Apple M5 Pro, ES 3.0)다. `MAX_TEXTURE_SIZE` 4096, 전체 화면 clear 루프 60fps.
  - 주의: 에뮬레이터 GPU는 맥 GPU를 번역해서 쓴다. 실차 헤드유닛 성능은 이보다 낮을 수 있으니 이 fps를 성능 근거로 쓰지 않는다.
  - 기술적으로는 A안(WebView + Pixi)이 가능하다. 남은 건 심사 정책상 WebView UI가 허용되는지(문의 메일 항목)다.
  - ⚠️ **앱 안의 WebView에서는 WebGL1이 차단된다** (2026-10-24, 본 앱 렌더러): `getContext("webgl")`은 null, `getContext("webgl2")`는 `WebGL 2.0 (OpenGL ES 3.0 Chromium)`. logcat에 `WebGL1 blocklisted`. Pixi 8의 자동 선택은 WebGL1으로 지원 여부를 보기 때문에 그대로 두면 Canvas 2D로 떨어진다 → `WebGLRenderer`를 직접 만들어 WebGL2로 쓴다(`shared/src/car/main.ts`). 실차 WebView도 같은지는 모른다.
- 화면: 디스플레이 2560×1440, 앱 영역 2560×1300(아래 140px는 시스템 바). 기본 홈은 왼쪽 차량 위젯 + 오른쪽 앱 영역의 분할 화면이다.
- 설치된 Pleos 서비스 중 `ai.pleos.playground.service.vehicle`, `.service.navi`가 있다. `.service.location.fused`(FusedLocation)도 설치되어 있지만, 콘솔에서는 Coming Soon이다. `.service.adas`, `.service.cap`, `ai.pleos.hardware.vehicle.emulator`(Virtual Console 연동으로 추정)도 있다.

## 에뮬레이터에서 확인할 것

- [x] 와이퍼 `WindowArea`: 지원 영역은 `FRONT_WINDSHIELD`, `REAR_WINDSHIELD` 두 개다. 둘 다 지원 상태는 `[OFF, ON, SERVICE]`이고, `OTHER`는 목록에 없다. accessType은 `READ`.
- [x] Virtual Console 조작 → 앱 수신 확인 (2026-10-24, 아래 "Virtual Console 연동 결과")
- [x] Virtual Console 와이퍼 단계 매핑: 아래 "와이퍼 단계 매핑" 표
- [ ] `WiperState.SERVICE`의 의미
- [x] Virtual Console 여는 법: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088/`. 도메인은 Vehicle, Motion(기어·브레이크·조향), ADAS, Convenience(도어·창문·조명·와이퍼), Safety(경고등), Energy, Climate. (https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [x] 속도: AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(m/s)로 읽힌다. Virtual Console의 ACCEL·BRAKE를 따라 약 0.2초 간격으로 갱신된다.
  - `android.car.permission.CAR_SPEED`는 **dangerous** 등급이라 런타임 허용이 필요하다. 본 앱에서는 권한 요청 화면이 있어야 한다.
  - AAOS는 앱을 **사용자 10**에서 실행한다. adb로 권한을 줄 때는 `pm grant --user 10 ...`. `--user` 없이 주면 SecurityException이 난다.
  - 기어는 `GEAR_SELECTION`(`CAR_POWERTRAIN`, normal)으로 읽힌다(P=4, D=8).
- [x] 위치: NaviHelper `getCurrentLocationInfo()` 요청에 바로 응답한다. 값은 고정 위치 37.4127212, 127.094681(성남 수정구 시흥동), heading 0.0이다.
  - ⚠️ 주행해도(0→79km/h) 위치가 바뀌지 않았다.
  - ⚠️ `adb emu geo fix`로 시스템 GPS를 강남역으로 바꾸면 Android `LocationManager`의 gps 위치는 바뀐다. 하지만 NaviHelper 위치는 시흥동 그대로였다. 내비 앱이 에뮬레이터 GPS를 쓰지 않는다.
  - 자동 푸시는 확인하지 못했다. 요청할 때만 응답이 왔다.
- [x] 길 안내 중에도 위치가 고정이다 (2026-10-24): 판교역까지 길 안내(3.1km)를 켜고 D + ACCEL로 37~62km/h 주행했다.
  - NaviHelper 이벤트: `onNavigationStatus` → `ROUTE_DRIVE`, `RouteStateInfo.naviMode` → `ROUTE_GUIDING`
  - 위치는 시흥동 그대로였다. 내비 지도의 차 아이콘도 출발점에 멈춰 있었고, 다음 안내도 "80m 좌회전"에서 줄지 않았다.
  - 결론: 에뮬레이터 내비는 차량 속도로 위치를 시뮬레이션하지 않는다. 데모에서 위치가 움직이게 하려면 앱의 궤적 재생 모드가 필요하다.
- ⚠️ **원인 확인: 에뮬레이터 GNSS의 NMEA 체크섬 버그** (2026-10-24)
  - 내비 앱(`ai.umos.maps.android.navigation.app`)은 `LocationManager`의 **NMEA 원문**(`GpsLocationSourceRepositoryImpl.onNmeaMessage`)으로 위치를 받는다.
  - 에뮬레이터 GNSS가 내보내는 NMEA는 위치와 상관없이 체크섬이 항상 `*47`이다. 실제 체크섬은 GPRMC `67`, GPGGA `42` 등이다. 그래서 내비가 모든 문장을 `NMEA checksum mismatch`로 버리고 기본 위치(시흥동)에 머문다.
  - 시도한 것: `geo fix` 단발(강남역), `geo fix` 연속 25회(시흥동 → 판교역), `geo nmea`로 체크섬이 맞는 GGA·RMC 직접 주입. 세 가지 모두 실패했다. `geo nmea`로 넣은 문장은 내비에 전달되지 않았고, 에뮬레이터가 마지막 `geo fix` 위치로 다시 만든 `*47` 문장만 전달됐다.
  - 결론: 이 에뮬레이터 이미지(2.1.0)에서는 내비가 움직이는 위치를 받을 방법이 없다. 시스템 이미지를 고치지 않는 한 우리가 우회할 수 없다. 데모 위치는 앱 자체 궤적 재생으로 간다.
  - 운영 측에 버그로 알릴 만한 내용이다(재현 방법: `adb emu geo fix` 후 내비 앱 logcat `GpsLocationSource`).
  - 참고: Android `LocationManager`의 gps 위치(좌표)는 정상으로 바뀐다. 우리 앱이 표준 위치 API를 쓸 수 있다면 에뮬레이터에서는 `geo fix`로 움직일 수 있다. 다만 Pleos 하드웨어 접근 정책상 표준 위치 API가 허용되는지는 확인 필요다.
- ⚠️ 백그라운드 앱 동결: 프로브 앱이 `onStop`된 뒤 약 8초 만에 Handler 주기 작업(위치 요청)이 멈췄다. 다시 앞으로 오면 쌓인 NaviHelper 이벤트가 한꺼번에 왔다.
  - 영향: "내 차 신호를 백그라운드에서 익명 전송"하려면 **포그라운드 서비스**(알림 표시)로 수집·전송을 돌려야 한다.
- ✅ 포그라운드 서비스로 해결 (2026-10-24, `spikes/vehicle-probe`의 `ProbeService`, `foregroundServiceType="dataSync"`)
  - 내비 앱을 앞에 띄워 프로브 화면이 `onStop`된 뒤에도, 2분 넘게 5초 heartbeat가 끊기지 않았다.
  - 백그라운드 상태에서 Virtual Console로 와이퍼 Low → High → Off, 브레이크를 조작했다. 서비스가 `WiperState`(ON/OFF), `WiperSwitch`(LEVEL_1 → LEVEL_3 → OFF), 브레이크(true/false)를 모두 1초 안에 받았다.
  - 서비스가 프로세스를 살려 두기 때문에, 같은 프로세스의 Activity 쪽 리스너도 같이 받았다. 본 앱에서는 수집을 서비스에만 두고, 화면은 상태 스토어를 구독하는 구조로 한다.
  - HOME 키로는 앱이 뒤로 가지 않는다(AAOS 분할 화면에서 앱 영역이 남음). 백그라운드 테스트는 다른 앱을 앞으로 띄워서 한다.
  - 남은 질문: Pleos 앱마켓 정책상 3rd party 앱의 포그라운드 서비스와 상시 알림이 허용되는지, 어떤 `foregroundServiceType`이 맞는지(`dataSync`, `location`, `specialUse` 중). 설명회 질문 후보다.
- 길 안내 중 NaviHelper 데이터: `DestinationInfo`에 목적지 좌표, `remainDistance`(m), `remainTime`(초)가 온다. `RouteStateInfo.trafficStatus.congestionItemList`에는 혼잡 구간이 `offset`(경로 시작점부터 m로 추정), `distance`, `trafficCongestionType`(HEAVY/SLOW), `roadName`으로 온다. 경로 선형은 없지만 "경로상 몇 m 지점" 표현은 가능하다. `CurrentLocationInfo.routeId`도 채워진다.
- 데모 대응(제안): 앱에 "데모 경로 재생" 모드를 두고, 미리 만든 GPS 궤적(예: 퇴근길)을 재생해서 위치를 넣는다. 위치 공급원을 인터페이스로 분리해 둔다(NaviHelper / 데모 궤적). 실차에서는 NaviHelper를 쓴다.
- 에뮬레이터 미지원 확인: 전방 안개등(`FRONT_FOG_LIGHTS_STATE`), 워셔액 부족 경고. 미지원 속성은 `java.lang.Exception`(메시지에 `IllegalArgumentException ... not supported`)으로 오고, ErrorCode는 붙지 않는다.
- [ ] `onCurrentLocationInfo`가 주기적으로 푸시되는지, `getCurrentLocationInfo()`를 호출할 때만 오는지
- [ ] `TrafficCongestionInfo.offset`·`distance`의 단위와 기준점
- [x] WebView 패키지 존재 여부와 WebGL 지원(브리프의 첫 스파이크): 위 "에뮬레이터 환경" 참고
- [ ] WebView 안에서 Pixi.js로 수천 개 점을 그렸을 때의 fps(렌더링 A/B안 결정용)

## Virtual Console 연동 결과 (2026-10-24, `spikes/vehicle-probe`)

| 조작 (Virtual Console) | 앱이 받은 값 | 판정 |
| --- | --- | --- |
| Front wiper Low·High·Auto | `WiperStateListener` → `ON` (단계와 상관없이 같음) | ✅ 켜짐/꺼짐만 구독됨 |
| 위 상태에서 스위치 조회 | `getCurrentWiperSwitch` → `CONTINUOUS_LEVEL_1` | ✅ 강도는 스위치에서 읽힘 |
| Exterior Lights Mode → Low beam / Off | `LOW_BEAM`과 `TAIL_LAMP`가 같이 `ON`/`OFF` | ✅ |
| High beam | `HIGH_BEAM` → `OFF` | ✅ |
| Brake 누름 / 뗌 | `BRAKE` → `true` / `false` | ✅ |
| Safety → Parking brake On / Blink / Off | `TELLTALE` → `[PARKING_BRAKE:ON]` / `[PARKING_BRAKE:BLINK]` / `[]` | ✅ (에뮬레이터 기준) |

와이퍼에서 알게 된 것 (기획 영향 있음)
- `WiperState`는 `OFF`/`ON`만 오고, 강도 정보가 없다.
- 강도는 `WiperSwitch`에 있다: `OFF`, `MIST`, `INTERMITTENT_LEVEL_1~5`, `CONTINUOUS_LEVEL_1~5`, `AUTO`, `SERVICE`, `OTHER`.
- 그런데 **`WiperSwitch`에는 구독(register) API가 없고 조회(get)만 있다.** 그래서 `WiperState`가 바뀔 때와 켜져 있는 동안 주기적으로(예: 10초) `getCurrentWiperSwitch`를 조회하는 방식으로 설계한다.
- 이 값으로 "비 온다"를 넘어 **비의 세기(이슬비·간헐·연속 단계)**를 격자에 집계할 수 있다. 차량특화성 설명에 쓸 수 있는 포인트다.
- `AUTO`(레인센서)일 때는 스위치 값만으로 강도를 알 수 없다. 이 경우는 "비 있음"으로만 센다.

와이퍼 단계 매핑 (Front wiper, 스위치 1초 주기 조회, 2026-10-24)

| Virtual Console | `WiperState` (구독) | `WiperSwitch` (조회) |
| --- | --- | --- |
| Off | `OFF` | `OFF` |
| Low | `ON` | `CONTINUOUS_LEVEL_1` |
| High | `ON` (변경 이벤트 없음) | `CONTINUOUS_LEVEL_3` |
| Auto | `ON` (변경 이벤트 없음) | `AUTO` |
| Off | `OFF` | `OFF` |

- Low → High → Auto로 바뀌는 동안 `WiperStateListener`는 아무 이벤트도 보내지 않았다. 강도 변화는 스위치 조회로만 알 수 있다는 것이 확인됐다.
- 스위치 값은 상태 이벤트보다 0.05~0.5초 늦게 반영됐다. 1초 주기 조회로 충분하다.
- 에뮬레이터 Virtual Console은 Low·High·Auto 3단계만 제공한다. `MIST`, `INTERMITTENT_LEVEL_1~5`, `CONTINUOUS_LEVEL_2·4·5`는 에뮬레이터로 재현할 수 없다. 실차에서 어떤 값이 오는지는 차종마다 다를 수 있다.
- 앞서 마지막 Off가 안 찍혔던 건, 이번에는 정상적으로 찍혔다(`OFF`/`OFF`). 이전에는 마지막 조작이 Off가 아니었던 것으로 보인다.
- 설계 결정 후보: 비 강도를 3단계로 정규화한다.
  - 약 = `MIST`·`INTERMITTENT_*`·`CONTINUOUS_LEVEL_1`
  - 중 = `CONTINUOUS_LEVEL_2~3`
  - 강 = `CONTINUOUS_LEVEL_4~5`
  - 미상 = `AUTO`
  - 에뮬레이터에서는 Low=약, High=중으로 보인다.

## 주행 중 알림 시험 (2026-10-26, 에뮬레이터)

질문: 내비를 앞에 띄우고 달릴 때 우리 앱이 알려 줄 방법이 있는가. Pleos 문서에는 주행 중 서드파티 알림 정책이 없다.

방법: 디버그 빌드 전용 수신기가 adb 브로드캐스트를 받으면 중요도 높음(IMPORTANCE_HIGH) 채널로 알림 하나를 띄운다(지금은 `app/src/debug/.../AheadNotificationProbe`, 실제 앞길 알림과 같은 `AlertNotifier.build`를 쓴다). 사용자 10에 `POST_NOTIFICATIONS`를 `pm grant`로 줌(그전에는 허용 안 됨이라 수집 서비스 상시 알림도 안 보였다).

1차(21:28·21:58, 수신기 `AlertProbeReceiver`, 지금은 지움):

| 상황 | 앞 화면 | 결과 |
| --- | --- | --- |
| P | 우리 앱 | 위에 뜨지 않음. 상단 표시줄 종 아이콘에 빨간 점(알림 센터에 쌓임) |
| P | 내비(`ai.umos.maps.android.navigation.app`, 길 안내 중) | 화면 위쪽 가운데에 카드로 뜸(제목 + 한 줄) |
| D, 주행 상태 MOVING(2), 시속 14~125km | 우리 앱(주행 배너) | 뜸. 우리 배너 왼쪽 위를 가린다 |
| D, MOVING | 내비 | 뜸. category 없음·`CATEGORY_NAVIGATION` 차이 없음 |

- 알림을 듣는 쪽: AAOS `CarNotificationListener` 외에 Pleos `ai.umos.homescreen`, `ai.umos.drivingview`, `ai.umos.ambientai`(Gleo, `GleoNotificationListenerService`), `ai.pleos.service.ui`
- Gleo가 알림을 소리로 읽는 흔적은 로그에 없다(소리는 직접 확인 못 함)
- D에서는 왼쪽 패널이 `ai.umos.drivingview`의 주행 화면(속도, 내비 회전 안내)으로 바뀐다
- 한계: 에뮬레이터 결과다. 실차·앱마켓 심사에서 주행 중 서드파티 알림을 허용하는지는 모른다
- 설계 영향: 우리 앱이 앞에 있을 때는 알림을 보내지 않는다(주행 배너를 가린다). 뒤에 있을 때만 새 구간을 알린다

2차(22:1x, 앞길 알림 기능을 넣은 뒤): 종류(category) 없이 보내면 **화면 위에 뜨지 않았다.** 시스템 로그 `CarHeadsUpNotificationManager: category not set for: io.roadsensor`. 같은 알림을 종류만 바꿔 보냈다(내비가 앞, 보내기 전 카드 없음을 확인):

| category | 화면 위 카드 | 10초 뒤 |
| --- | --- | --- |
| 없음, `recommendation`, `event`, `status`, `reminder`, `service` | 안 뜸 | - |
| `navigation` | 뜸 | 남아 있음(지우지 않으면 40초 넘게, 내비 회전 안내를 가림) |
| `msg`(메시지) | 뜸(앱 아이콘 포함) | 남아 있음 |

- 1차에서 종류 없이 뜬 것과 결과가 다르다. 원인은 모른다(채널·알림 내용 차이, 첫 알림 여부 등 확인 못 함)
- 결정: `CATEGORY_NAVIGATION` + `setTimeoutAfter(8초)`. 메시지는 뜻이 맞지 않는다. 8초 뒤 알림이 지워지며 카드도 사라지는 것을 확인했다
- 확인 필요: 길 안내 앱이 아닌 앱이 `CATEGORY_NAVIGATION`을 써도 되는지(Pleos 정책 문서에 없음)
- 주행 확인(22:26~22:27, D, 시속 45~65km, 내비가 앞): "약 100m 앞 급정거 잦음", "약 200m 앞 비 (중)" 카드가 차례로 뜸. 어두운 구간은 앱 화면에서 본 뒤라 알리지 않음(설계대로)
- 관찰(원인 모름): 22:07·22:09·22:22~22:23 주행 중에 홈 화면 쪽(uid 10119)이 런처 인텐트로 우리 앱과 내비를 2~3초 간격으로 번갈아 띄운 기록이 있다. 사용자는 누르지 않았다고 한다. 22:26 이후 주행에서는 없었다
- 같이 고친 것: 런처·알림으로 다시 열 때마다 `MainActivity`가 새로 쌓이고 이전 화면(WebView 포함)이 남아 있었다(6개) → `launchMode="singleTask"`로 같은 화면을 앞으로 가져온다

## 글꼴 (2026-10-27, 에뮬레이터)

Pleos 개발 문서에서 글꼴·디자인 지침은 찾지 못했다. 에뮬레이터 이미지에서 확인한 사실:

- `/product/fonts`: 42dot Sans, Asta Sans, Bai Jamjuree, Pretendard(파일). 글꼴 업데이트 영역 `/data/fonts/files/…`에도 Asta Sans가 있다
- `/product/etc/fonts_customization.xml`(42dot): 안드로이드 기본 `sans-serif`를 **Asta Sans**로 바꾼다. `pretendard`라는 이름도 Asta Sans를 가리킨다. `asta-light`→42dot, `asta-semibold`/`asta-bold`→Bai Jamjuree 별칭
- Asta Sans: 42dot이 만든 글꼴, 한글 포함, **OFL-1.1**(https://github.com/42dot/Asta-Sans)
- 네이티브 View는 글꼴을 지정하지 않으면 Asta Sans로 그린다(설정 기준)
- **WebView는 Pleos 글꼴을 보지 못한다**: `system-ui`·`sans-serif`가 Roboto + Noto Sans CJK KR로 그려지고, `"Asta Sans"`, `pretendard`, `42dot` 등 이름을 넣어도 기본 글꼴로 돌아간다(DevTools `CSS.getPlatformFontsForNode`)
- 안드로이드 공개 API `SystemFonts.getAvailableFonts()`(API 29)는 Asta Sans 파일까지 돌려주고 앱이 읽을 수 있다. 같은 글자 폭 비교로 기본 글꼴의 실제 파일을 찾으면 굵기 400 = Regular, 600 = SemiBold, 700 = Bold(설정 파일만 보면 700이 ExtraBold처럼 보이지만 실제는 Bold)
- 대응: 이 파일을 WebView에 넘겨 `@font-face`로 쓴다(docs/architecture.md 5장)

## 아직 문서를 안 읽은 것

- [ ] 속도: 브리프는 Pleos FAQ가 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 권장한다고 적었다. 이번에 다시 확인하지 않았다. 급정거 추정에 필요하다. (https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
- [ ] Safety의 워셔액·오일 레벨 API 퍼미션
- [ ] `TurnSignalStateListener`, `LowWasherFluidWaringListener` 콜백 시그니처
- [ ] `TrafficEventType`, `TrafficCongestionType`, `CautionPointType` enum 값
- [ ] 하드웨어 접근 정책, 보안·개인정보 가이드(위치 동의 안내 문구 요건)
- [ ] Vehicle SDK Sample App 가이드(최소 앱 구성 참고용)

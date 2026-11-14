# NaviHelper SDK (3.0.5)

Pleos Connect 기본 내비(Navi Service)에 경로 요청을 보내고, 위치·경로 정보를 이벤트로 받는 SDK.

출처
- 소개: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
- 의존성: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk
- 퍼미션: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions
- 초기화: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release
- 이벤트: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners
- 오류: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling
- API Reference: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry

## 의존성

```kotlin
implementation("ai.pleos.playground:NaviHelper:3.0.5")
```

## 퍼미션

| 퍼미션 | 주요 API | 보호 수준 |
| --- | --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | `get*Info()`, `requestTrip()` | normal |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | `requestRoute`, `requestRouteWithWaypoints`, `cancelRoute`, `requestReRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption` | normal |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | `requestNavigationScreen()` | 확인 필요 |

## 생명주기

```kotlin
val naviHelper = NaviHelper(applicationContext)
naviHelper.initialize(
    onConnected = { /* 여기서 addListener, API 호출 */ },
    onFailed = { throwable -> /* 초기 연결 재시도 모두 실패 */ }
)
// 인자 없는 initialize()도 있지만 콜백 버전을 권장
naviHelper.addListener(listener)
...
naviHelper.removeListener(listener)   // 등록했던 같은 인스턴스
naviHelper.release()                  // 이후 재사용하려면 새로 생성 + initialize
```

## 조회는 "요청 → 이벤트" 방식

`get*Info()`는 **반환값이 없다**(`fun getCurrentLocationInfo()`). 호출하면 결과가 리스너 콜백으로 온다. 문서상 실제로 어떤 이벤트가 오는지는 내비 앱 구현에 따라 달라질 수 있다.

| 요청 | 결과 이벤트 | 퍼미션 |
| --- | --- | --- |
| `getCurrentLocationInfo()` | `onCurrentLocationInfo(CurrentLocationInfo)` | `NAVI_ROUTE` |
| `getRouteStateInfo()` | `onRouteStateInfo(RouteStateInfo)` | `NAVI_ROUTE` |
| `getDestinationInfo()` | `onDestinationInfo(...)` | `NAVI_ROUTE` |
| `getWaypointInfo()` | `onWayPointInfo(...)` | `NAVI_ROUTE` |
| `getTBTInfo()` | `onTBTInfo(...)` | `NAVI_ROUTE` |
| `requestReRoute()` | `onReRouteEnded()` | `NAVI_ROUTE_SEARCH` |

## NaviHelperEventListener

필요한 콜백만 override한다(모두 `open fun`).

| 분류 | 콜백 |
| --- | --- |
| 경로 | `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onRouteCancelled()`, `onReRouteEnded()`, `onRouteOptionChanged()` |
| 목적지·경유지 | `onDestinationChanged()`, `onDestinationArrived()`, `onWaypointChanged()`, `onWayPointArrived()` |
| 정보 | `onCurrentLocationInfo`, `onRouteStateInfo`, `onDestinationInfo`, `onWayPointInfo`, `onTBTInfo`, `onTripInfo`, `onNavigationStatus`, `onDrivingInfo`, `onCurrentRoadSpeedLimit`, `onCameraAlert`, `onCautionPointInfo`, `onBookmarkInfo`, `onRecentDestinationInfo`, `onChargerOperatorInfo` |
| 오류 | `onError(NaviErrorCode)` |

`NaviErrorCode`: `CAUSE_LOW_BATTERY`, `CAUSE_SEARCH_ROUTE_OVER_DISTANCE`, `CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE`, `CAUSE_SEARCH_ROUTE`.

## 데이터 모델 (`ai.pleos.playground.navi.data`, 모두 `@Serializable data class`)

위치

```kotlin
CurrentLocationInfo(latitude: Double, longitude: Double, heading: Double = 0.0,
                    address: String, routeId: String, currentRouteIndex: Int?)
```

→ **위도·경도·방향이 있다.** 브리프의 "CurrentLocationInfo에 위경도가 있는지" 항목 해결. 단, 갱신 주기(자동 푸시인지, 요청할 때만 오는지)는 확인 필요.

경로 상태

```kotlin
RouteStateInfo(termsAgreement: Boolean, naviMode: NaviMode, isRouting: Boolean,
               routeOption: RouteOption, remainDestinationChargingInt: Int,
               remainWaypointChargingInt: List<Int>, trafficStatus: TrafficStatus?)
TrafficStatus(summary: TrafficCongestionSummary,
              congestionItemList: List<TrafficCongestionInfo>,
              trafficEventList: List<TrafficEventInfo>)
TrafficCongestionInfo(offset: Double, distance: Double, trafficCongestionTime: Long,
                      trafficCongestionType: TrafficCongestionType, avrSpeed: Double,
                      roadName: String, startCrossName: String, endCrossName: String)
TrafficEventInfo(latitude: Double, longitude: Double, eventType: TrafficEventType,
                 startTime: String, endTime: String, description: String)
TrafficInfo(congestionType: TrafficCongestionType, startRatio: Int, endRatio: Int)
```

목적지·경유지·주행

```kotlin
DestinationInfo(latitude, longitude, displayLatitude?, displayLongitude?, name, address,
                remainDistance: Int, remainTime: Int, tollFare: Int, poiId, poiSubId?, poiProvider)
RoutePointInfo(latitude, longitude, poiName, poiId, ratioOnRoute: Double,
               poiType, pointType, settingType)
RouteStartInfo(duration: Int, distance: Int, tollFare: Int, name: String, remainDestinationChargingValue: Int)
DrivingInfo(destination: DrivingState, waypoints: List<DrivingState>)
DrivingState(name, duration: Int, distance: Int, ...충전 관련 필드)
TBTInfo(type: TurnType, distance: Double, description: String)
CautionPoint(id: String, type: CautionPointType, remainDistance: Int)
```

## 에뮬레이터에서 확인한 것 (2026-11-05, Fuel Mate `app/navi/NaviWaypoint.kt`)

- ⚠️ `RouteStateInfo.isRouting`은 **안내 중에도 false**로 왔다(주유소로 안내 중, `naviMode=ROUTE_GUIDING`). 안내 중인지는 `naviMode`(`NOT_WORKING`, `SAFE_GUIDING`(경로 없는 안전운전), `ROUTE_GUIDING`, `SIMULATION_GUIDING`)로 본다
- 경유지를 넣으면 잠깐 `SAFE_GUIDING`이 됐다가 다시 `ROUTE_GUIDING`이 된다. 내비 앱이 앞으로 나온다
- (2026-11-08) 경유지를 넣으면 내비가 "경유지 N개 포함" 경로 미리보기와 [안내 시작] 카운트다운(약 5초)을 띄운다. 이 동안 `SAFE_GUIDING`이고 목적지도 비어 있다. 카운트다운이 끝나면 `onWaypointChanged(ADD)`가 오고(요청 뒤 약 7초) `ROUTE_GUIDING`으로 돌아온다
- (2026-11-08) 미리보기에서 [X]를 누르면 안내 전체가 끝나고 `onWaypointChanged`는 오지 않는다. 앱은 넣기 응답을 15초 기다린 뒤 포기하고 경유 상태를 지운다(`NaviWaypoint.PENDING_TIMEOUT_MS`)
- (2026-11-08) 경유지가 있는 채로 또 `addWaypoint(FIRST)`를 부르면 앞의 경유지는 그대로 있고 하나가 더 붙는다("경유지 2개 포함")
- (2026-11-08, javap) `removeWaypoint(WaypointIndex)`. `WaypointIndex`는 `FIRST`, `SECOND`, `ALL`이고 `WaypointCmdType`은 `ADD`, `REMOVE`다(경유지는 2개까지로 보인다. 2개가 찬 뒤의 추가 요청에는 응답이 없었다)
- (2026-11-08 에뮬레이터) `removeWaypoint(FIRST)`도 내비가 앞으로 나와 미리보기와 카운트다운을 띄우고, 약 7초 뒤 `onWaypointChanged(REMOVE)`가 온다. 그 직후 `getDestinationInfo()`는 경유지를 뺀 목적지 거리를 돌려줬다(13,000m). 이어서 `addWaypoint`를 부르면 미리보기가 한 번 더 뜬다. 바꾸기 전체는 약 15초
- 경유지를 넣기 전 남은 거리: `onDrivingInfo`는 넣기 전에는 오지 않았고, `getDestinationInfo()` → `DestinationInfo.remainDistance`가 경로 거리와 맞게 왔다(2,300m, 14,000m)
- ⚠️ `WaypointChangedInfo.routeStartInfo.distance`는 **첫 경유지까지 거리**다(2026-11-06 확인). 2,300m 경로에 경유지를 넣자 이 값은 4,800m, `onDrivingInfo`는 목적지 9,200m·경유지 [4,800]m, `remainDistance`는 9,200m였다. 넣은 뒤의 목적지 거리는 `onDrivingInfo`·`remainDistance`로 받고, 실제 우회 = 넣은 뒤 목적지 거리 − 넣기 전 목적지 거리(여기서는 6,900m). 예전에 본 "10000 → 4800"도 이 값을 잘못 비교한 것이다. 앱은 말이 안 되는 우회 값(음수·20km 초과)이면 "실제 우회 거리는 확인하지 못함"으로 표시한다
- 이미 경유지로 들어간 곳을 다시 넣으면 `onWaypointChanged`가 오지 않았다
  - (2026-11-15 재현) 앱만 다시 시작하면 앱은 내비에 남은 경유지를 모른다. 이때 같은 곳을 넣으면 내비는 경로도 다시 짜지 않고 아무 응답도 주지 않는다
  - (2026-11-15 javap, 에뮬레이터) `getWaypointInfo()` → `onWayPointInfo(List<WaypointInfo>)`. `WaypointInfo`에는 `name`, `address`, 좌표, `remainDistance`, `remainTime`, `poiId`가 있다. 안내 중 경유지 이름 목록을 받을 수 있어, 앱은 넣기 전에 이 목록으로 이미 있는지 본다. `DrivingInfo.waypoints`의 `DrivingState.name`에도 경유지 이름이 온다
- 목적지 도착 콜백은 `onDestinationArrived(DestinationArrivedInfo)`, 경유지 도착은 `onWayPointArrived(WaypointArrivedInfo)`다(javap. 위 표의 이름에는 인자가 빠져 있다)
- `requestRoute(RouteInfo(...))`(주유소 좌표·이름만, POI ID 없음)로 안내가 시작되고 음성 안내가 나온다

## 이 프로젝트에 중요한 점

- ⚠️ **장소 이름 검색 API가 없다**(NaviHelper 공개 메서드 javap, 2026-11-06). `requestRoute`는 좌표를 받는다. 쓸 수 있는 것은 `getRecentDestinationInfo`·`getBookmarkInfo`(이름·좌표·POI ID. 에뮬레이터에서 최근 3곳이 실제로 왔다, 즐겨찾기 2곳은 이름 또는 좌표가 비어 목록에 안 나왔고 값은 미확인)와, 내비 앱 열기다
- ⚠️ 내비 앱을 열 때 선택자 인텐트(`makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_MAPS)`)를 그대로 쓰면 떠 있는 내비를 앞으로 가져오지 않고 새 태스크로 하나 더 띄워 "내비게이션을 실행 중입니다"에서 멈춘다. 지도 앱 패키지를 `resolveActivity`로 찾고 `getLaunchIntentForPackage`로 연다(매니페스트 `<queries>` 필요). 부팅 직후·반복·내비 강제 종료 뒤 모두 태스크 1개에 검색창이 뜨는 것을 확인했다(2026-11-06)
- `requestRoute`·`addWaypoint`를 부르면 내비가 앞으로 나온다. 내비는 `SYSTEM_ALERT_WINDOW`가 있어 허용되고, 일반 앱은 포그라운드 서비스여도 `Background activity launch blocked`로 스스로 앞으로 나올 수 없다(2026-11-06 확인). 사용자가 작업 표시줄 아이콘으로 돌아온다
- ⚠️ **경로 선형(좌표 목록)을 주는 API가 없다.** 데이터 클래스 32개를 다 봤지만 polyline 형태의 데이터는 없다. 경로 위 위치는 목적지·경유지 좌표, `ratioOnRoute`, 혼잡 구간의 `offset`/`distance`(경로 시작점 기준 거리로 보임) 정도로만 표현된다.
  - 그래서 "경로 12km 지점부터 비"는 그대로는 구현할 수 없다. 대안은 [open-questions.md](open-questions.md)에 정리했다.
- `TrafficCongestionInfo.offset`·`distance`의 단위(m인지)와 기준점은 문서에 없다. 확인 필요.
- `requestReRoute()`는 우회할 지점을 지정할 수 없고 재탐색만 요청한다. "위험 구간을 피하는 경로"는 경유지 추가(`addWaypoint`)로 유도하는 방식을 따로 검토해야 한다.

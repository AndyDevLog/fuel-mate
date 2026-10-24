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

## 이 프로젝트에 중요한 점

- ⚠️ **경로 선형(좌표 목록)을 주는 API가 없다.** 데이터 클래스 32개를 다 봤지만 polyline 형태의 데이터는 없다. 경로 위 위치는 목적지·경유지 좌표, `ratioOnRoute`, 혼잡 구간의 `offset`/`distance`(경로 시작점 기준 거리로 보임) 정도로만 표현된다.
  - 그래서 "경로 12km 지점부터 비"는 그대로는 구현할 수 없다. 대안은 [open-questions.md](open-questions.md)에 정리했다.
- `TrafficCongestionInfo.offset`·`distance`의 단위(m인지)와 기준점은 문서에 없다. 확인 필요.
- `requestReRoute()`는 우회할 지점을 지정할 수 없고 재탐색만 요청한다. "위험 구간을 피하는 경로"는 경유지 추가(`addWaypoint`)로 유도하는 방식을 따로 검토해야 한다.

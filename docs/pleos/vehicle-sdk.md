# Vehicle SDK (2.3.2)

차량 상태 조회·제어 SDK. 앱은 SDK를 통해 Pleos Connect의 Vehicle Service(AAOS 위에서 동작)에 IPC로 연결한다.

출처
- 소개: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- 의존성: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- 도메인 객체: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object
- 퍼미션: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- 오류 코드: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code
- API Reference: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry

## 의존성

```kotlin
implementation("ai.pleos.playground:Vehicle:2.3.2")
```

- 실제로는 `vehicle-data:1.1.11`, `vehicle-aidl:1.1.1`, Hilt, Gson, coroutines, kotlin-reflect가 함께 들어온다(debug APK 약 9MB).
- SDK 매니페스트가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 퍼미션을 자동으로 합친다. 요구하는 Vehicle Service 최소 버전은 2.3.13이다.
- AGP 9.4.0 + Gradle 9.6.1 + JDK 25(Android Studio 내장)로 빌드를 확인했다(`spikes/vehicle-probe`).

## ⚠️ 실제 SDK(2.3.2)와 문서가 다른 점 (javap로 확인, 2026-10-24)

| 항목 | 문서 | 실제 |
| --- | --- | --- |
| `VehicleException` | 공개 클래스로 `e.errorCode` 사용 | 난독화되어 `ai.pleos.playground.vehicle.callback.exception.a`. 오류 코드 getter도 `a()` |
| `ErrorCode` | `ai.pleos.playground.vehicle.constant.ErrorCode`로 분기 | `vehicle-data` 모듈에 있고, 앱의 컴파일 경로에 노출되지 않는다. `ErrorCode.ERROR_...`를 쓰면 컴파일 오류가 난다 |
| `WindowArea` 생성자 | `WindowArea(val values: WindowValue)` | `WindowArea(vararg WindowValue)` |
| `Vehicle.getGear()` | 도메인 목록에 없음 | 실제로 있음 |

- 오류 코드가 필요하면 리플렉션으로 "반환 타입 이름이 `...constant.ErrorCode`인 인자 없는 메서드"를 찾아 읽는다(`spikes/vehicle-probe`의 `describe()`).
- 미지원 속성 오류는 `VehicleException`이 아니라 일반 `java.lang.Exception`으로 온다. 이때 ErrorCode는 없다.
- `registerXxx` 직후 현재 값으로 콜백이 한 번 온다. 그래서 등록만 해도 초기값을 받을 수 있다.
- `initialize()`에는 연결 완료 콜백이 없다. 스파이크에서는 1.5초 뒤에 조회했고 정상 동작했다. 본 앱에서는 첫 호출이 `ERROR_SERVICE_CONNECTION_FAILURE`면 재시도하는 방식이 필요하다.

## 생명주기와 호출 방식

1. `Vehicle(context)`로 인스턴스를 만들고 `initialize()`로 서비스에 연결한다.
2. `vehicle.getWiper()`, `getLight()`, `getBrake()`, `getSafety()` 같은 `getXxx()`로 도메인 객체를 얻는다. 도메인 객체를 직접 생성하지 않는다.
3. 도메인 객체에서 네 종류의 API를 호출한다.

| 종류 | 이름 패턴 | 결과 전달 |
| --- | --- | --- |
| Capability | `checkXxxCapabilities` | `onComplete(CapabilityConfig, areaMap)` / `onFailed(Exception)` |
| Get | `getCurrentXxx` | `onComplete(값?)` / `onFailed(Exception)` |
| Set | `setXxx` | 실패할 때만 `onFailed` |
| Callback | `registerXxx(listener)` / `unregisterXxx(listener)` | 리스너의 `onXxxUpdated(...)` / `onFailed(e)` |

4. 사용이 끝나면 `release()`. 비동기 콜백이 끝나기 전에 `release()`를 부르지 않는다.

- 모든 결과는 비동기(IPC)다. Get도 콜백으로 온다.
- 모든 리스너는 `sealed interface VehicleListener`를 상속하고, 공통으로 `onFailed(e: Exception)`를 구현해야 한다.
- Capability의 `CapabilityConfig.accessType`(`READ`, `READ_WRITE`, `WRITE`, `NONE` 등)으로 지원 여부를 먼저 확인하고 Get/Callback을 부르는 것이 권장 흐름이다.

`Vehicle` 클래스 문서의 Callback 예제는 `vehicle.getGear()`와 `GearSelectionListener`를 쓴다. 도메인 목록에는 Gear가 없지만, 실제 SDK에는 `getGear()`가 있다(아래 "실제 SDK와 문서가 다른 점"). 기어 API 시그니처는 쓰기 전에 javap로 확인한다.

## 퍼미션

매니페스트에 `pleos.car.permission.{상수명}` 형식으로 선언한다. 조회는 `CAR_*`, 제어는 `CONTROL_CAR_*`. 이 프로젝트에서 쓰는 것:

| 용도 | 퍼미션 | 보호 수준 |
| --- | --- | --- |
| Wiper 조회·구독 | `pleos.car.permission.CAR_WINDOWS` | normal |
| Light 조회·구독 | `pleos.car.permission.CAR_LIGHTS` | normal |
| Brake 페달 조회·구독 | `pleos.car.permission.CAR_DRIVE` | normal |
| 경고등(Telltale) 조회·구독 | `pleos.car.permission.PRIVATE_CAR_SAFETY` | **signature\|privileged** |

퍼미션이 없으면 `onFailed`로 `VehicleException`(`ERROR_SECURITY_PERMISSION`)이 올 수 있다.

## 오류 처리

`onFailed`의 `Exception`이 `VehicleException`이면 `e.errorCode`(`ai.pleos.playground.vehicle.constant.ErrorCode`)로 분기한다. 모든 실패가 `VehicleException`인 것은 아니다.

| ErrorCode | 의미 | 처리 |
| --- | --- | --- |
| `ERROR_NOT_SUPPORTED_PROPERTY` | 기능 미지원 | UI 비활성 |
| `ERROR_NOT_SUPPORTED_AREA` | 영역 미지원 | 다른 영역 선택 |
| `ERROR_NOT_SUPPORTED_VALUE_TYPE` | 값 타입 미지원 | 파라미터 확인 |
| `ERROR_SECURITY_PERMISSION` | 퍼미션 부족 | 매니페스트 확인 |
| `ERROR_SERVICE_CONNECTION_FAILURE` | 서비스 연결 실패 | `initialize()`·에뮬레이터 확인 |
| `ERROR_INTERNAL_SERVICE` | 내부 오류 | 재시도 |
| `ERROR_DRIVING_RESTRICTED` | 주행 중 기능 제한 | 안내 |
| `ERROR_EMPTY_RESULT` | 결과 없음 | 빈 상태 UI |
| `ERROR_UNDEFINED` | 기타 | 로그·재시도 |

## Wiper (`vehicle.getWiper()`)

패키지 `ai.pleos.playground.vehicle.api.Wiper`. 모두 `CAR_WINDOWS` 필요.

```kotlin
fun checkWiperStateCapabilities(
    onComplete: (CapabilityConfig, Map<WindowArea, List<WiperState>>) -> Unit,
    onFailed: (Exception) -> Unit)
fun getCurrentWiperState(area: WindowArea, onComplete: (WiperState?) -> Unit, onFailed: (Exception) -> Unit)
fun registerWiperState(area: WindowArea, listener: WiperStateListener)
fun unregisterWiperState(area: WindowArea, listener: WiperStateListener)
```

- 그 밖에 `checkWiperSwitchCapabilities`, `getCurrentWiperSwitch(area, ...)`(반환 `WiperSwitch?`), `setWiperSwitch`(제어)가 있다. 이 프로젝트는 상태(State)만 쓴다.
- `checkWiperStateCapabilities`에는 `CapabilityConfig` 없이 `Map`만 받는 deprecated 오버로드도 있다. 새 오버로드를 쓴다.

리스너와 값

```kotlin
interface WiperStateListener : VehicleListener {
    fun onWiperStateUpdated(state: WiperState?)
    fun onFailed(e: Exception)
}
enum class WiperState { OTHER, OFF, ON, SERVICE }   // 각 값의 설명은 문서에 없음
```

영역

- `class WindowArea(val values: WindowValue) : Area`
- `enum WindowValue`: `FRONT_WINDSHIELD`, `REAR_WINDSHIELD`, `ROW_1_LEFT`, `ROW_1_RIGHT`, `ROW_2_LEFT`, `ROW_2_RIGHT`, `ROOF_TOP_1`, `ROOF_TOP_2`
- 확인 필요: 앞 와이퍼가 `WindowArea(WindowValue.FRONT_WINDSHIELD)`인지. `checkWiperStateCapabilities`의 areaMap 키로 실제 지원 영역을 확인한다.

## Light (`vehicle.getLight()`)

패키지 `ai.pleos.playground.vehicle.api.Light`. 모두 `CAR_LIGHTS` 필요. 외부등, 실내등, 앰비언트 등이 있고 각 등마다 check/get/register/unregister가 따로 있다. 이 프로젝트에 필요한 것:

```kotlin
fun getCurrentLowBeamLightState(onComplete: (LightState?) -> Unit, onFailed: (Exception) -> Unit)
fun registerLowBeamLightState(listener: LightStateListener)
fun registerHighBeamLightState(listener: LightStateListener)
// 같은 패턴: FrontFogLight, RearFogLight, TailLampLight, CabinLight, ReadingLight
// 각각 unregisterXxxLightState(listener), checkXxxLightStateCapabilities(...)
```

```kotlin
interface LightStateListener : VehicleListener {
    fun onLightStateUpdated(state: LightState?)
}
enum class LightState { OFF, ON, DAYTIME_RUNNING }
```

- 영역 파라미터가 없다(등 종류별 함수로 구분).
- ⚠️ `LightStateListener` 설명에는 "HighBeamLightState updates"라고 적혀 있지만, 하향등·안개등·앰비언트 등 모든 등의 register 함수가 같은 리스너 타입을 받는다. 리스너 인스턴스를 등마다 따로 만들어야 어느 등의 변화인지 구분할 수 있다.
- 어두움 판단에는 하향등(`LowBeam`)과 미등(`TailLamp`)이 핵심이고, 안개등(`FrontFog`)은 악천후 보조 신호로 쓸 수 있다.

## Brake (`vehicle.getBrake()`)

모두 `CAR_DRIVE` 필요.

```kotlin
fun getCurrentBrakePedalStatus(onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit)  // true = 밟는 중
fun registerBrakePedalStatus(listener: BrakePedalStatusListener)
fun unregisterBrakePedalStatus(listener: BrakePedalStatusListener)
fun checkBrakePedalStatusCapabilities(...)
```

```kotlin
interface BrakePedalStatusListener : VehicleListener {
    fun onBrakePedalStatusUpdated(status: Boolean?)
}
```

- 페달을 밟았는지(Boolean)만 온다. 세기나 감속도는 없다. "급정거"는 페달 신호와 속도 변화(감속률)를 같이 써서 앱에서 추정해야 한다. 속도 API는 [open-questions.md](open-questions.md) 참고.

## Safety: 경고등 (`vehicle.getSafety()`)

```kotlin
// PRIVATE_CAR_SAFETY (signature|privileged)
fun registerTelltales(listener: TelltaleInfoListener)
fun unregisterTelltales(listener: TelltaleInfoListener)
fun getTelltalesInfo(onComplete: (telltales: Telltales) -> Unit, onFailed: (Exception) -> Unit)
```

```kotlin
interface TelltaleInfoListener : VehicleListener {
    fun onTelltaleInfoUpdated(telltales: Telltales?)
}
data class Telltales(val telltales: List<TelltaleData> = arrayListOf())
data class TelltaleData(val type: TelltaleType, val state: TelltaleState,
                        val color: Int, val priority: Int, val warningCount: Int)
```

- ⚠️ **경고등 API는 `signature|privileged` 퍼미션을 요구한다.** 일반 3rd party 앱에는 보통 부여되지 않는 보호 수준이다. 즉 이 앱에서는 못 쓸 가능성이 높다.
- ⚠️ `getTelltalesInfo`의 어노테이션에는 `CAR_SAFETY`라고 적혀 있는데, 본문에는 `PRIVATE_CAR_SAFETY`라고 적혀 있다. 어느 쪽이 맞는지 에뮬레이터에서 직접 호출해 확인해야 한다.
- Safety에는 그 밖에 `hasLowWasherFluidWarning`(워셔액 부족), 엔진·브레이크·변속기 오일 레벨, 안전벨트, 에어백 API가 있다. 각 API가 요구하는 퍼미션은 아직 확인하지 않았다.

## 리스너 전체 목록 (이 프로젝트 관련만)

| 리스너 | 콜백 | 등록 위치 | 퍼미션 |
| --- | --- | --- | --- |
| `WiperStateListener` | `onWiperStateUpdated(WiperState?)` | `Wiper.registerWiperState(area, l)` | `CAR_WINDOWS` |
| `LightStateListener` | `onLightStateUpdated(LightState?)` | `Light.registerLowBeamLightState(l)` 등 | `CAR_LIGHTS` |
| `BrakePedalStatusListener` | `onBrakePedalStatusUpdated(Boolean?)` | `Brake.registerBrakePedalStatus(l)` | `CAR_DRIVE` |
| `TelltaleInfoListener` | `onTelltaleInfoUpdated(Telltales?)` | `Safety.registerTelltales(l)` | `PRIVATE_CAR_SAFETY` ⚠️ |
| `LowWasherFluidWaringListener` | 확인 필요 | `Safety.registerLowWasherFluidWaringState` | 확인 필요 |
| `TurnSignalStateListener` | 확인 필요 | `TurnSignal` 도메인 | `CAR_LIGHTS` (대표 퍼미션 표 기준) |

모든 리스너에는 공통 `onFailed(e: Exception)`도 있다. 리스너 이름의 `Waring` 오타는 SDK의 실제 이름이다.

## 최소 매니페스트 (이 프로젝트 MVP)

```xml
<uses-permission android:name="pleos.car.permission.CAR_WINDOWS" />
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
```

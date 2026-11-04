# Fuel Mate (가칭)

내 차의 연료·배터리와 운전 신호를 읽어, 진행 방향에서 **실제로 이득이 되는** 주유·충전소를 골라 주고, 이번 운행의 운전 습관 비용을 한 줄로 알려 주는 Pleos Connect 차량용 앱.

> 개발 중입니다. 지금은 차량 신호 수집과 상태 화면 뼈대만 있습니다. 진행 상황은 [docs/progress.md](docs/progress.md), 주제와 기능을 정한 근거는 [docs/pivot-fuel.md](docs/pivot-fuel.md)에 있습니다.

## 테스트 환경

- Pleos Connect Emulator 2.1.0(Android Studio 에뮬레이터, AVD 이름 예: `Pleos_Connect_v2.1.0`). 앱은 AAOS 사용자 10에서 실행됩니다.
- 에뮬레이터 차량은 전기차입니다. 위치는 고정이라 앱은 기본으로 데모 경로(시흥동 → 판교역)를 재생합니다.
- 속도·기어·브레이크는 Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`)로 바꿉니다.

## 실행 방법

```bash
cd android && ./gradlew :app:assembleDebug
```

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb shell pm grant --user 10 io.fuelmate android.car.permission.CAR_SPEED
```

```bash
adb shell am start --user 10 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n io.fuelmate/.ui.MainActivity
```

빌드에는 Android Studio 내장 JBR(`JAVA_HOME`)과 Android SDK(`ANDROID_HOME`)가 필요합니다. 단위 테스트는 `cd android && ./gradlew :core:test`.

## APK 위치

제출용 release APK는 `apk/` 폴더에 둘 예정입니다.

## 개인정보

- 위치·운행 기록은 차 안에만 저장합니다. 차량번호, VIN, 계정은 저장하지 않습니다.
- 로그에 원본 좌표를 남기지 않습니다.

## 오픈소스·데이터 출처

| 이름 | 용도 | 라이선스·조건 |
| --- | --- | --- |
| Pleos Vehicle SDK, NaviHelper SDK | 차량 신호, 위치·경로 | Pleos Playground 이용 조건 |
| kotlinx.coroutines | 상태 스토어 | Apache License 2.0 |
| OpenStreetMap, OSRM | 데모 경로(`android/app/src/main/assets/tracks/`) | © OpenStreetMap contributors, ODbL |

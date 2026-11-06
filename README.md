# Fuel Mate

내 차의 연료·배터리와 운전 신호를 읽어, 내비 목적지로 가는 길에서 **실제로 이득이 되는** 주유·충전소를 골라 주고, 이번 운행의 운전 습관 비용을 한 줄로 알려 주는 Pleos Connect 차량용 앱.

> 개발 중입니다. 지금은 차량 신호 수집과 상태 화면 뼈대만 있습니다. 진행 상황은 [docs/progress.md](docs/progress.md), 주제와 기능을 정한 근거는 [docs/pivot-fuel.md](docs/pivot-fuel.md)에 있습니다.

## 테스트 환경

- Pleos Connect Emulator 2.1.0(Android Studio 에뮬레이터, AVD 이름 예: `Pleos_Connect_v2.1.0`). 앱은 AAOS 사용자 10에서 실행됩니다.
- 에뮬레이터 차량은 전기차입니다. 내비에 목적지를 넣으면 그 방향의 주유소를 추천합니다. 에뮬레이터 내비 위치는 고정이라, 차가 달리면 앱이 목적지 쪽 직선으로 위치를 움직입니다(화면에 "위치 데모" 표시, 실제 도로를 따르지 않음). 실제 도로 궤적(시흥동 → 판교역)을 재생하려면 `--es location DEMO`로 실행합니다.
- 시연은 휘발유 차 기준입니다(지금 Pleos Connect가 탑재된 차는 가솔린·하이브리드·LPG 그랜저). 에뮬레이터 차는 전기차라 연료 값을 주지 않으므로, 앱은 기본으로 **데모 연료차**(60L 탱크 25%에서 시작, 실제 속도 신호로 소비를 계산해 연료를 줄임)를 씁니다. 차가 주는 실제 값(Pleos Fuel·EvBattery)이나 데모 전기차로 바꾸려면 아래 "에너지 공급원 바꾸기"를 보세요.
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

에너지 공급원 바꾸기(앱을 다시 켜도 유지됩니다): `VEHICLE`(차량 값), `DEMO_FUEL`(데모 연료차, 기본), `DEMO_EV`(데모 전기차)

```bash
adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es energy VEHICLE
```

충전요금 단계 바꾸기(전기 단가, 기본 `FAST_100`): `SLOW`, `FAST_30`, `FAST_50`, `FAST_100`, `ULTRA`

```bash
adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es chargeTier FAST_100
```

오피넷 인증키(선택): [오피넷 오픈 API](https://www.opinet.co.kr/user/custapi/openApiNew.do)에서 받은 키를 저장소 밖 `~/.android-keys/fuel-mate/api-keys.properties`에 `opinetCertKey=<키>`로 넣고 빌드합니다. 키가 없으면 휘발유 가정 단가(1,700원/L)를 씁니다.

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
| 오피넷 "주유소 전국 평균가격(현재)" 오픈 API(한국석유공사, [공공데이터포털 15150932](https://www.data.go.kr/data/15150932/openapi.do)) | 휘발유 단가(하루 한 번, 위치를 보내지 않음) | 공공데이터포털 표기 "이용허락범위 제한 없음". 오피넷 저작권정책은 수익 목적 이용 시 공사와 사전 협의, 출처 표기(한국석유공사) |
| 오피넷 "반경 내 주유소" 오픈 API(한국석유공사) | 주유소별 휘발유 가격·위치(검색 중심 좌표만 보냄) | 위와 같음 |
| 기후에너지환경부 공공 전기차 충전요금(2026-08-01 시행 5단계) | 전기 단가(사용자가 단계 선택) | 보도 기준 값, 공식 고시 원문 확인 필요 |
| OpenStreetMap, OSRM | 데모 경로(`android/app/src/main/assets/tracks/`) | © OpenStreetMap contributors, ODbL |

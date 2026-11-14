# Fuel Mate

내 차의 연료량과 가는 방향을 읽어, 돌아가는 기름값까지 뺀 **실제로 이득인** 주유소를 골라 탭 한 번에 내비 경유지로 넣어 주고, 이번 운행의 운전 습관 비용을 한 줄로 알려 주는 Pleos Connect 차량용 앱.

- 출발 전(정차): 내비 목적지로 가는 길의 주유소 가운데 "약 1,680원 이득"처럼 실제 이득이 가장 큰 곳을 크게 보여 주고, [한강에너지 들르기] 한 번으로 내비 경유지에 넣습니다.
- 달리는 동안(주행): 주행 규제(`CarUxRestrictions`)를 따라 멈춘 그림, 큰 글자 한 줄, 보조 한 줄, 버튼 하나만 보입니다. 목록·펼치기·애니메이션은 정차 중에만 있습니다.
- 도착한 뒤(정차): 급제동·급가속이 기름값을 약 얼마나 더 쓰게 했는지 운행 타임라인과 함께 보여 줍니다(추정값, 계산 방법을 화면에서 펼쳐 볼 수 있음).

## APK 위치

[`apk/FuelMate_FuelMate_release.apk`](apk/FuelMate_FuelMate_release.apk) (release, versionName 0.1.0, R8 축소·난독화, release 키로 APK Signature Scheme v2·v3 서명)

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator **2.1.0** (Android API 34 시스템 이미지 `pleos_car_emulator`, arm64-v8a) |
| 화면 규격 | **2560 × 1440**, 160dpi, 가로 |
| AVD 설정 | 공식 설치 문서와 같음: Form Factor Automotive, Cold boot, RAM 4GB |
| 개발 도구 | Android Studio 2026.2, AGP 9.4.0, Gradle 9.6.1, JDK 21(Android Studio 내장 JBR) |
| 앱 | minSdk 28, targetSdk 34, Kotlin. Pleos Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5 |
| 네트워크 | 주유소 가격(오피넷 오픈 API)을 받으려면 에뮬레이터가 인터넷에 연결되어 있어야 합니다 |

알아 둘 점

- 앱은 AAOS **사용자 10**에서 실행됩니다. adb로 권한을 줄 때는 `--user 10`을 붙입니다.
- 에뮬레이터 차량은 전기차라 연료 API가 값을 주지 않고, 달려도 에너지 값이 줄지 않습니다. 시연은 지금 Pleos Connect가 탑재된 휘발유 차 기준이라, 앱은 기본으로 **데모 연료차**(60L 탱크 25%에서 시작, 실제 차량 속도 신호로 소비를 계산해 연료를 줄임)를 씁니다. 화면에 "(데모)"로 표시합니다.
- 에뮬레이터 내비 위치는 달려도 움직이지 않습니다. 차가 달리면 앱이 목적지 쪽 직선으로 위치를 움직입니다(화면에 "위치 데모(직선 이동)" 표시, 실제 도로를 따르지 않음).
- 속도·브레이크·기어·주행 규제·내비 목적지와 경유지, 주유소 가격은 모두 실제 SDK·API 값입니다.

## 실행 방법

### 1. 에뮬레이터 켜기

Android Studio Device Manager에서 Pleos Connect 2.1.0 AVD를 실행합니다. 부팅 직후 홈 화면이 아직 안 떠 있으면 앱이 시스템 바 없이 전체 화면으로 열릴 수 있어, 먼저 홈을 띄웁니다.

```bash
adb shell input keyevent KEYCODE_HOME
```

### 2. 설치와 권한

같은 패키지(`io.fuelmate`)가 다른 서명으로 깔려 있으면 먼저 지웁니다.

```bash
adb uninstall io.fuelmate
```

```bash
adb install -r apk/FuelMate_FuelMate_release.apk
```

처음 실행하면 권한 대화상자가 두 번 뜹니다. 둘 다 [허용]을 누릅니다.

- "위치 정보에 액세스": 차량 속도 권한(`android.car.permission.CAR_SPEED`)입니다. AAOS가 속도를 위치 권한 묶음으로 보여 줍니다. 급제동·급가속 판정에 씁니다.
- "알림을 보내도록": 차량 신호를 모으는 포그라운드 서비스의 상시 알림입니다.

속도 권한을 미리 주려면:

```bash
adb shell pm grant --user 10 io.fuelmate android.car.permission.CAR_SPEED
```

### 3. 앱 실행

앱 목록의 Fuel Mate를 누르거나:

```bash
adb shell am start --user 10 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n io.fuelmate/.ui.MainActivity
```

### 4. 시연 순서

1. **첫 화면**: 목적지가 없으면 앱이 하는 일과 내비의 즐겨찾기·최근 목적지가 보입니다. 바로 가기의 [안내]를 누르거나 [어디로 가세요?]로 내비에서 목적지를 검색해 안내를 시작합니다.
2. **추천**: 안내가 시작된 뒤 앱을 다시 열면 가는 길 주유소를 찾아 실제 이득이 가장 큰 곳을 보여 줍니다(오피넷 응답까지 몇 초). [○○ 들르기]를 누르면 내비 경유지로 들어가고, 내비가 다시 계산한 실제 우회 거리로 이득을 다시 계산합니다.
3. **주행**: Virtual Console에서 기어를 D로 바꾸고 ACCEL을 누르면 주행 화면으로 바뀝니다. 브레이크를 세게 밟으면(시속 20km 이상에서 초당 11km/h 넘게 감속) 급제동으로 기록되고 "지금 운행" 금액이 오릅니다.
4. **도착**: 기어를 P로 바꾸면 운행이 저장되고, 지난 운행 카드에 습관 비용과 타임라인이 나타납니다. [계산 방법 보기]로 항목별 금액과 가정값을 펼칩니다.

Virtual Console 열기:

```bash
adb forward tcp:8088 tcp:8088
```

그다음 브라우저에서 `http://127.0.0.1:8088/`을 엽니다.

### 실행 옵션(선택)

에너지 공급원 바꾸기(앱을 다시 켜도 유지): `VEHICLE`(차량 값), `DEMO_FUEL`(데모 연료차, 기본), `DEMO_EV`(데모 전기차)

```bash
adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es energy VEHICLE
```

실제 도로 궤적(시흥동 → 판교역, OpenStreetMap·OSRM)으로 위치를 재생하려면:

```bash
adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es location DEMO
```

충전요금 단계 바꾸기(전기 단가, 기본 `FAST_100`): `SLOW`, `FAST_30`, `FAST_50`, `FAST_100`, `ULTRA`

```bash
adb shell am start --user 10 -n io.fuelmate/.ui.MainActivity --es chargeTier FAST_100
```

## 소스에서 빌드하기

`JAVA_HOME`에 JDK 21(Android Studio 내장 JBR), `ANDROID_HOME`에 Android SDK를 둡니다.

```bash
cd android && ./gradlew :core:test
```

```bash
cd android && ./gradlew :app:assembleDebug
```

- `:core:test`: 판정·계산·상태 스토어 단위 테스트(순수 Kotlin)
- release 빌드(`:app:assembleRelease`)는 저장소 밖 `~/.android-keys/fuel-mate/keystore.properties`(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)의 키로 서명합니다. 키가 없으면 서명되지 않은 APK가 나옵니다.
- 오피넷 인증키: [오피넷 오픈 API](https://www.opinet.co.kr/user/custapi/openApiNew.do)에서 받은 키를 저장소 밖 `~/.android-keys/fuel-mate/api-keys.properties`에 `opinetCertKey=<키>`로 넣고 빌드합니다. 키는 저장소에 없습니다. 키가 없으면 주유소 가격을 받지 못하고 휘발유 가정 단가(1,700원/L)를 씁니다. 제출한 APK에는 키가 들어 있습니다.

## 구조

```
android/
  core/   순수 Kotlin: 상태 스토어(단방향), 운전 사건 판정, 습관 비용, 주유소 이득 계산, 좌표 변환, 단가
  app/    차량 연동(포그라운드 서비스) → 상태 구독 → 화면(정차·주행)
docs/     진행 기록(progress.md), 주제 결정 근거(pivot-fuel.md), Pleos SDK 정리(pleos/)
spikes/   SDK 확인용 실험 코드(본 앱과 별개)
```

- 차량 신호 수집과 외부 API 호출은 포그라운드 서비스에만 있고, 화면은 상태를 구독해 바뀐 것이 있을 때만 다시 그립니다.
- 주행/정차는 `CarUxRestrictions`만 따릅니다. 주행 화면을 그리는 Activity에 `distractionOptimized`를 선언했고, 최상단 Back 키는 앱을 끄지 않고 뒤로 숨깁니다(`moveTaskToBack`). 필수 조작은 터치만으로 끝나며 CCP는 지원하지 않는다고 선언했습니다.

## 개인정보

- 위치·운행 기록은 차 안에만 저장합니다. 차량번호, VIN, 계정은 저장하지 않습니다.
- 외부(오피넷)에는 주유소 검색 중심 좌표만 보냅니다. 평균 가격 조회에는 위치를 보내지 않습니다.
- 로그에 원본 좌표를 남기지 않습니다.

## 오픈소스·데이터 출처

| 이름 | 용도 | 라이선스·조건 |
| --- | --- | --- |
| Pleos Vehicle SDK, NaviHelper SDK | 차량 신호, 위치·경로·경유지 | Pleos Playground 이용 조건 |
| kotlinx.coroutines | 상태 스토어 | Apache License 2.0 |
| 오피넷 "주유소 전국 평균가격(현재)" 오픈 API(한국석유공사, [공공데이터포털 15150932](https://www.data.go.kr/data/15150932/openapi.do)) | 휘발유 단가(하루 한 번, 위치를 보내지 않음) | 공공데이터포털 표기 "이용허락범위 제한 없음". 오피넷 저작권정책은 수익 목적 이용 시 공사와 사전 협의, 출처 표기(한국석유공사) |
| 오피넷 "반경 내 주유소" 오픈 API(한국석유공사) | 주유소별 휘발유 가격·위치(검색 중심 좌표만 보냄) | 위와 같음 |
| 기후에너지환경부 공공 전기차 충전요금(2026-08-01 시행 5단계) | 전기 단가(사용자가 단계 선택) | 보도 기준 값, 공식 고시 원문 확인 필요 |
| OpenStreetMap, OSRM | 데모 경로(`android/app/src/main/assets/tracks/`) | © OpenStreetMap contributors, ODbL |

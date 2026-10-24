# 진행 기록

공모전 일정 기준으로 진행 상황을 적는다. Pleos SDK 관련 세부 사항과 미해결 질문은 [pleos/open-questions.md](pleos/open-questions.md)에 있다.

## 일정 한눈에 보기

| 단계 | 기간 | 상태 |
| --- | --- | --- |
| 1. 환경 확인 | 10/22 ~ 10/26 | 진행 중 (대부분 완료) |
| 2. 방향 확정 | 10/27 ~ 11/2 | 대기 |
| 3. 핵심 흐름 | 11/3 ~ 11/9 | 대기 |
| 4. 제출 준비 (11/14 토 제출) | 11/10 ~ 11/14 | 대기 |
| 5. PT 준비 | 11/16 ~ 12/2 | 대기 |
| 6. PT | 12/3 ~ 12/4 | 대기 |

주요 외부 일정: 10/28(수) 온라인 설명회, 11/15(일) 23:59 접수 마감, 11/23(월) 1차 결과, 12/18(금) 최종 발표.

## 1단계: 환경 확인 (10/22 ~ 10/26)

### 완료

2026-10-24
- [x] 개발 환경: Android Studio 2026.2.1(Apple Silicon 네이티브), Android SDK, platform-tools 설치
- [x] Pleos Playground 계정 생성, 프로젝트 `Road Sensor` 생성
- [x] 콘솔 SDK 상태 확인: Vehicle State(11개 항목)와 NaviHelper는 사용 가능. FusedLocation, TTS, STT, Vehicle Control은 Coming Soon. MVP 신호 3종(와이퍼, 라이트, 브레이크)은 모두 사용 가능하다.
- [x] Pleos Connect Emulator 2.1.0 설치(arm64 이미지), AVD 생성(RAM 4GB, Cold boot), 부팅과 adb 연결 확인
- [x] WebView 확인: Pleos 전용 WebView(Chromium 131)가 있고, WebGL2가 동작한다(에뮬레이터 기준 60fps).
- [x] Pleos 문서 정리: `docs/pleos/`에 Vehicle SDK, NaviHelper, 주행 규제, 확인 필요 항목
- [x] `CurrentLocationInfo` 필드 확인: 위도, 경도, heading이 있다.
- [x] 와이퍼 API 확인: `WiperState { OTHER, OFF, ON, SERVICE }`, 퍼미션 `CAR_WINDOWS`
- [x] 결정: 경로 알림은 진행 방향 앞 N km(heading 기준) 방식을 기본으로 하고, 목적지가 있으면 직선 띠로 보강한다(내비 경로 선형 API가 없어서).

- [x] **차량 신호 최소 앱** (`spikes/vehicle-probe`): Vehicle SDK로 와이퍼·라이트·브레이크·경고등을 구독했다. Virtual Console 조작이 앱에 실시간으로 들어오는 것까지 확인했다. 실제 SDK가 문서와 다른 점 4가지를 `docs/pleos/vehicle-sdk.md`에 기록했다.
- [x] **경고등 퍼미션**: 에뮬레이터에서는 `PRIVATE_CAR_SAFETY`가 부여되고 동작한다. 실차 기준은 문의가 필요하다.
- [x] 발견: 와이퍼 강도는 `WiperSwitch`(MIST·간헐 1~5·연속 1~5·AUTO)에 있고, 구독은 안 되고 조회만 된다.

- [x] **주행 중 차단(DO)**: DO를 선언하지 않으면 D 기어에서 앱이 차단된다. `distractionOptimized` 메타데이터를 넣으면 보인다. 규제 플래그는 `NO_VIDEO`·`NO_SETUP`.
- [x] **속도·기어**: AAOS `CarPropertyManager`로 실시간으로 읽힌다(속도 권한은 런타임 허용 필요).
- [x] **위치**: NaviHelper로 읽히지만 고정 위치(시흥동)라서 주행해도, geo fix를 해도 안 움직인다 → 데모용 궤적 재생 모드가 필요하다.

### 남은 일

- [x] 내비 길 안내 중에도 NaviHelper 위치는 고정이다(주행해도 안 움직임) → 데모 궤적 재생 모드가 확정적으로 필요하다.
- [x] 원인: 에뮬레이터 GNSS NMEA 체크섬이 항상 `*47`로 틀려서 내비가 위치를 버린다. `geo fix`, 연속 이동, `geo nmea` 주입 모두 실패했다. 우리 쪽에서 우회할 수 없다.
- [x] 발견: 백그라운드로 간 앱은 약 8초 뒤 얼어서 멈춘다 → 신호 수집·전송은 포그라운드 서비스로 해야 한다.
- [x] 포그라운드 서비스(`dataSync`)를 쓰면 화면이 뒤로 가도 차량 신호 수집이 계속된다(와이퍼·브레이크 이벤트 1초 안에 수신).

- [x] 와이퍼 단계 매핑: Low=`CONTINUOUS_LEVEL_1`, High=`CONTINUOUS_LEVEL_3`, Auto=`AUTO`. 강도가 바뀌어도 `WiperState` 이벤트는 오지 않는다(`docs/pleos/open-questions.md`).
- [ ] **문의 메일 발송**: 브리프의 질문에 아래 새 항목을 추가한다.
  - 주행 중 표시를 위한 DO(Distraction Optimization) 선언 방법과, 3rd party 앱의 DO 선언 허용 여부
  - 경고등(Telltale) API의 `PRIVATE_CAR_SAFETY` 퍼미션을 참가자 앱이 쓸 수 있는지(에뮬레이터에서는 부여됨)
  - SDK 2.3.2에서 `VehicleException`과 `ErrorCode`가 앱 코드에 노출되지 않는 문제(문서 예제가 컴파일되지 않음)
  - `WiperSwitch` 변경 구독 API 제공 계획
  - 앱 카테고리(Mobility Services로 등록 예정) 확인
- [ ] **저장소 준비**: 공모전 전용 GitHub 계정을 만들고 `git init`, 익명 작성자 설정(`git config --local`), 저장소 안 `CLAUDE.md`(프로젝트 규칙만)를 작성한다.

## 구현

2026-10-24 (`feat/android-skeleton`)
- [x] `android/` 뼈대: `:core`(순수 Kotlin) + `:app`. AGP 9.4.0, Kotlin 2.2.10, coroutines 1.10.2
- [x] `:core`: 모델(AppState 등), 판정(비 세기, 급정거), geohash·진행 방향 필터, 상태 스토어(리듀서), 주행 모드 문구. 단위 테스트 25개 통과
- [x] `:app`: 수집 서비스(포그라운드), Pleos Vehicle 어댑터, Car API 어댑터(주행 규제·속도·기어), NaviHelper 위치, 화면(정차: 상태 요약 / 주행: 한 줄 알림)
- [x] 에뮬레이터: 설치·실행, 서비스 시작, NaviHelper 연결, D 기어에서 주행 모드 화면 표시
- [x] 정차 화면, 속도 권한 요청(정차 중 요청 → 허용 → 속도 재구독), Virtual Console 조작(P 기어, 와이퍼 High → "ON / CONTINUOUS_LEVEL_3 → 비 중") 화면 반영 확인
- [x] 데모 궤적 재생: `TrackPlayer`(:core, 테스트 7개) + `DemoTrackSource`(차량 속도를 따라 1초마다 위치). 정차 화면 버튼으로 데모/내비 전환, 기본은 데모. 에뮬레이터에서 D + ACCEL로 2.5km 샘플 궤적 26개 격자를 지나 끝에서 처음으로 반복하는 것까지 확인
- [ ] 샘플 궤적(시흥동 → 판교역 직선)을 실제 도로 궤적으로 교체: 데모 경로(기상청 강수 데이터와 맞출 도로) 정할 때
- [ ] 다음: 서버 업로드·구독

## 2단계: 방향 확정 (10/27 ~ 11/2)

- [x] 앱 구조 설계: [architecture.md](architecture.md). 결정 1~5번(모듈, 주행 모드 화면, 격자·익명화, 서버 스택, 저장소 구조) 확정 (2026-10-24)
- [ ] 렌더러 A/B(결정 6), 패키지명(결정 7)

- [ ] 10/28 설명회 참석. Gleo 연동 가능 여부와 WebView 정책을 질문한다.
- [ ] 렌더링 A/B안 결정. 판단 근거로 WebView + Pixi에서 수천 개 점을 그렸을 때의 fps 스파이크를 한다.
- [ ] 기획서 초안(DOCX 8개 문항)
- [ ] 서버와 시뮬레이터 뼈대(격자 집계, 가상 차량, 기상청 강수 데이터)

## 3단계 이후

- 3단계: 차량 신호 → 서버 → 지도가 끝에서 끝까지 동작, 주행 모드(CarUxRestrictions)
- 4단계: 시연 영상, README, release APK(`apk/` 폴더), 기획서 최종본
- 5단계: 내 차 트윈, 경로 알림 보강, 관제 웹 대시보드(React + Pixi), 발표 자료

## 기획에 반영할 발견

| 발견 | 영향 | 대응 |
| --- | --- | --- |
| 경고등 API가 signature\|privileged 퍼미션 | MVP의 경고등 구독, 내 차 트윈 경고등 표시 | 에뮬레이터에서는 동작함. 실차 기준은 문의. 그때까지 보조 기능으로 |
| 와이퍼 강도는 WiperSwitch에만 있고 구독 불가 | 비 세기 집계 | WiperState 변경 시 + 주기적으로 스위치 조회 |
| 내비 경로 선형 API 없음 | "경로 12km 지점부터 비" 불가 | 진행 방향 기준으로 결정함(위 참고) |
| DO 미선언 앱은 주행 중 사용 불가 | 주행 모드 한 줄 알림 | DO 선언 방법·허용 여부 문의 |
| "차량 관리" 카테고리 없음 | 기획서 표현, 앱 등록 | Mobility Services로 |
| 브레이크는 페달 on/off만 | 급정거 판단 | 페달 + 속도 감속률로 추정. 속도는 `PERF_VEHICLE_SPEED`로 0.2초 간격 확인됨 |
| NaviHelper 위치가 에뮬레이터에서 고정 | 데모에서 차가 움직이지 않음 | 위치 공급원 분리 + 데모 궤적 재생 모드 |
| 백그라운드 앱이 약 8초 뒤 동결 | 주행 중 신호 수집·전송이 끊김 | 포그라운드 서비스로 해결 확인. Pleos 정책상 허용 여부와 서비스 유형은 설명회에서 질문 |
| 속도 권한이 dangerous | 첫 실행 권한 요청 UX | 정차 중 첫 실행에서 요청(주행 중에는 설정 화면 제한) |

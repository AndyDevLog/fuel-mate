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
- [x] 문의 메일 1차 답변(2026-10-25): AI 코딩 도구 사용 가능(조건: 지정 에뮬레이터에서 주요 기능 정상 동작, PT에서 코드 동작 원리 직접 설명, 저작권 책임은 참가자). WebView·WebGL, 준비 중 SDK, 1차 APK 구동 환경은 42dot 확인 중 → [briefing-questions.md](briefing-questions.md)
  - 영향: 심사 환경에는 우리 서버가 없을 수 있다 → 서버 없이도 지도·알림이 보이는 방법이 필요하다(결정 필요)
- [x] 지도 표현 바꾸기(2026-10-25, `feat/route-ahead-ui`): 파란 격자 대신 **배경 도로 + 앞길 색칠 + 패널 앞 5km 막대**
  - `:core` `RouteAhead`: 앞길 5km를 30m마다 훑어 구간을 만들고, 앞길이 있으면 알림 거리도 길 따라 잰 값(테스트 6개, 합계 62개)
  - 데모 궤적이 남은 길을 앞길로 준다(`TrackPlayer.pathAhead`), 내비 위치는 진행 방향 직선(추정)
  - 배경 도로: OpenStreetMap 주요 도로 1,975개(점 5,435개, 114KB), 출처 표시
  - 발견: 에뮬레이터 WebView에서 도로를 한 Graphics로 그리면 정점 인덱스 한도 초과로 안 그려짐 → 1,500점씩 나눔
  - 에뮬레이터 확인: 알림 "약 2.1km 앞 비 (중) · 2.9km 구간"(길 따라 거리, 예전 직선 900m), 유휴 0프레임, 격자 3,000개 + 도로 58fps
  - 사용자 의견 반영: 차 화면에서 격자(네모)를 지움(관제 화면용 옵션으로만 남김), 범례를 선 모양으로, 상태 줄에서 "주변 격자 n개" 뺌
- [x] 주행 화면 바꾸기(2026-10-25, `feat/drive-map`, 사용자 결정): 한 줄만 → **네이티브 배너(한 줄 + "앞서 간 차 5대가 와이퍼를 켰습니다") + 애니메이션 없는 단순 지도(앞 3km)**
  - 근거: Pleos 카테고리 문서가 Maps/Navigation·Mobility Services에 간결한 지도를 허용. 자동 애니메이션은 없앰(1초마다 제자리 갱신). `LIMIT_CONTENT`면 지도 숨김
  - `:core`: 구간별 신호를 낸 차 수(격자 차 수 × 비율의 최댓값, 급정거는 횟수), `DriveLine.detail`, `DriveMode.showsDriveMap`. 테스트 2개(합계 64개)
  - 패널 알림 카드에도 "앞서 간 차 n대" 표시
  - `CLAUDE.md` 차량 정책 문장, 설계 결정 2, `docs/pleos/driving-restriction.md` 갱신
- [x] 위치·앞길 한 번에 보내기(2026-10-25, `refactor/location-with-route`): 데모 궤적이 위치와 앞길을 따로 dispatch해서 1초에 상태가 두 번 바뀌고, 그 사이 "새 위치 + 예전 앞길" 상태가 생겼다
  - `Action.LocationFixed(fix, routeAhead)` 하나로 합치고 `RouteAheadChanged`·`LocationSink.onRouteAhead`를 지움. 앞길 없는 위치(내비)가 오면 앞길을 지운다. 테스트 1개(합계 65개)
  - 에뮬레이터 확인(디버그 빌드, WebView `roadSensor.stats`): 주행 중 렌더 모델 1.6개/초 → 약 1개/초, 정차 중 10초 동안 모델·프레임 0
- [ ] **서버 공개 배포 방법 결정** (2026-10-25 보류): 심사 환경에서 APK가 붙을 서버
  - Fly.io: 지금 구조 그대로, 무료 아님(가장 작은 기기 상시 약 $2/월, 체험은 2시간 또는 7일), CLI로 배포 가능
  - Supabase: 무료(1주 비활성 시 일시정지), 서버 로직을 SQL·Edge Function·Realtime으로 다시 짜야 함(1~2일), 가상 차는 pg_cron으로, React 관제 대시보드와 연결하기 좋음
  - 앱 안 데모 데이터: 서버 없이도 지도 표시(무료, 인터넷 불필요), 실제 네트워크 구조는 보여 주지 못함
  - Render 무료 + 외부 핑: 15분 비활성 시 잠듦, 정책 확인 필요
- [ ] **추가 문의**: 1차 메일에 없던 아래 항목
  - 주행 중 표시를 위한 DO(Distraction Optimization) 선언 방법과, 3rd party 앱의 DO 선언 허용 여부
  - 경고등(Telltale) API의 `PRIVATE_CAR_SAFETY` 퍼미션을 참가자 앱이 쓸 수 있는지(에뮬레이터에서는 부여됨)
  - SDK 2.3.2에서 `VehicleException`과 `ErrorCode`가 앱 코드에 노출되지 않는 문제(문서 예제가 컴파일되지 않음)
  - `WiperSwitch` 변경 구독 API 제공 계획
  - 앱 카테고리(Mobility Services로 등록 예정) 확인
  - APK 파일명 규칙 `팀명_앱이름_release.apk`에서 개인 참가는 팀명이 성명이라 블라인드와 부딪힘 → 개인 참가자는 무엇을 쓰는지
  - 서버가 필요한 앱의 1차 심사: 심사 환경 에뮬레이터가 인터넷에 붙는지, README의 로컬 서버 실행 방법을 따라 주는지
  - 블라인드 기준이 GitHub 계정명·커밋 작성자에도 적용되는지, 시연 영상 형식·길이 제한
- [x] **저장소 준비**: 공모전 전용 GitHub 계정을 만들고 `git init`, 익명 작성자 설정(`git config --local`), 저장소 안 `CLAUDE.md`(프로젝트 규칙만)를 작성한다.

## 구현

2026-10-24 (`feat/android-skeleton`)
- [x] `android/` 뼈대: `:core`(순수 Kotlin) + `:app`. AGP 9.4.0, Kotlin 2.2.10, coroutines 1.10.2
- [x] `:core`: 모델(AppState 등), 판정(비 세기, 급정거), geohash·진행 방향 필터, 상태 스토어(리듀서), 주행 모드 문구. 단위 테스트 25개 통과
- [x] `:app`: 수집 서비스(포그라운드), Pleos Vehicle 어댑터, Car API 어댑터(주행 규제·속도·기어), NaviHelper 위치, 화면(정차: 상태 요약 / 주행: 한 줄 알림)
- [x] 에뮬레이터: 설치·실행, 서비스 시작, NaviHelper 연결, D 기어에서 주행 모드 화면 표시
- [x] 정차 화면, 속도 권한 요청(정차 중 요청 → 허용 → 속도 재구독), Virtual Console 조작(P 기어, 와이퍼 High → "ON / CONTINUOUS_LEVEL_3 → 비 중") 화면 반영 확인
- [x] 데모 궤적 재생: `TrackPlayer`(:core, 테스트 7개) + `DemoTrackSource`(차량 속도를 따라 1초마다 위치). 정차 화면 버튼으로 데모/내비 전환, 기본은 데모. 에뮬레이터에서 D + ACCEL로 2.5km 샘플 궤적 26개 격자를 지나 끝에서 처음으로 반복하는 것까지 확인
- [x] 데모 궤적을 실제 도로로 교체(2026-10-25): 시흥동 → 판교역 약 5.4km(창업로 → 대왕판교로 → 판교로 → 분당내곡로), 점 220개. OSRM으로 계산, OpenStreetMap(ODbL) 출처를 궤적 파일과 README에 남김. 예전 직선 샘플은 지웠다
- [x] 서버 뼈대 (`server/`, `feat/server-skeleton`): Node 24 + TypeScript(빌드 없이 실행) + Fastify 5 + @fastify/websocket
  - `POST /v1/observations`: JSON 스키마로 검증. 모르는 필드(예: 원본 좌표)는 지우지 않고 400으로 거부한다
  - 격자 집계: 15분 창, 같은 차는 격자마다 최근 값 하나, k=3 미만은 배포하지 않음
  - `WS /v1/stream`: 타일 구독 → 스냅샷, 이후 1초마다 바뀐 격자만 보냄. `GET /v1/cells`(디버그), `GET /health`
  - 로그: 메서드·경로·상태만 남긴다(임시 ID, 격자, IP, 호스트 이름 없음)
  - 테스트 19개 통과, 타입 검사 통과, 실제 서버로 업로드 → 구독 수신 확인
- [x] 앱 업로더 (`feat/uploader`): `ObservationBuilder`(:core, 테스트 9개)가 격자 이동·상태 변화·5분 갱신·급정거마다 관측값을 만들고, `Uploader`가 10초마다 묶어서 보낸다(실패 시 최대 2분 간격 재시도). 임시 ID는 30분마다 교체. 평문 HTTP는 127.0.0.1에만 허용
  - 에뮬레이터 확인: 데모 궤적으로 30초 주행하는 동안 격자 8개를 지나며 10초마다 업로드 성공, 서버 `activeCells` 9개(차 1대라 배포 0개, k=3 규칙대로)
  - ~~남은 것: 업로드 큐를 디스크에 저장~~ → 아래 `feat/upload-queue`에서 완료
- [x] 앱 구독 (`feat/mapfeed`): `MapFeed`가 내 위치의 타일(geohash 5자리)과 둘레 8칸을 WebSocket으로 구독하고, 끊기면 1초~30초 간격으로 재연결한다(OkHttp 4.12.0). 끊기면 받은 격자와 알림을 비운다. 업로드 상태와 구독 상태를 따로 표시한다
- [x] 시뮬레이터 (`simulator/`, 의존성 없음): 궤적의 40~90% 구간을 지나는 가상 차 5대가 와이퍼 켬(강도 2)을 1분마다 업로드한다. 격자 ID가 앱과 같은지 테스트로 확인
- [x] **처음으로 끝까지 동작 (2026-10-24 22:21)**: 시뮬레이터 → 서버(격자 14개 배포) → 앱 구독 → 진행 방향 필터 → 주행 모드 화면 "약 900m 앞 비 (중)". 차가 다가가면서 800m → 700m → 600m → 500m → 400m로 줄어드는 것까지 확인
- [x] 업로드 큐 디스크 저장 (`feat/upload-queue`): `ObservationCodec`(:core, 한 줄 형식, 테스트 3개)으로 `files/upload-queue.txt`에 저장한다. 임시 파일에 쓰고 바꿔치기한다. 저장 파일에도 원본 좌표·임시 ID는 없다
  - 에뮬레이터 확인: 서버를 끈 채 관측값 3개가 쌓임 → 앱 프로세스 강제 종료 → 서버 켜고 재시작 → "업로드 큐 복원 3개" → "업로드 6개 (대기 0)" → 파일 비워짐
- [x] 설명회 질문지: [briefing-questions.md](briefing-questions.md) (설계가 바뀌는 질문 4개, 평가·제출 4개, 기타 5개)
- [x] release 빌드(2026-10-25): R8 코드 축소·리소스 축소로 **22MB → 2.7MB**. Pleos SDK는 리플렉션 때문에 통째로 유지(`proguard-rules.pro`). 패키지명 `io.roadsensor` 유지(결정 7)
  - 서명 키는 저장소 밖 `~/.android-keys/road-sensor/keystore.properties`에서 읽는다(사용자가 직접 생성). 시험용 `-ProadSensor.debugSignRelease=true`로 디버그 키 서명
  - 에뮬레이터 확인: release APK로 수집 서비스·Vehicle SDK 구독·WebView 지도(WebGL2)·구독 수신 정상, 실제 도로 궤적의 비 구간 16격자 표시
  - 서명 키 생성(2026-10-25, 사용자): 인증서 이름 `CN=Road Sensor, O=Road Sensor`(실명 없음). 정식 서명 release를 `apksigner`로 확인(APK Signature Scheme v2, 서명자 1, 인증서 지문이 키와 같음) → 에뮬레이터 설치·실행·지도 정상
  - 키 파일(`~/.android-keys/road-sensor/`)은 저장소 밖에 있다. 잃어버리면 같은 앱으로 업데이트할 수 없으니 따로 백업한다
  - 남은 것: `apk/` 파일명(문의 중)
- [x] README(2026-10-25): 앱 소개, 화면, 테스트 환경, 실행 방법, 빌드, 개인정보, 오픈소스·데이터 출처
- [x] 설명회 미참석 결정, 질문 13개 모두 기본값으로 진행 ([briefing-questions.md](briefing-questions.md))
- [x] 렌더러 A안(WebView + Pixi) 확정 (architecture.md 결정 6)
- [x] WebView 렌더러 (`feat/renderer-web`, architecture.md 5장)
  - `:core` `RenderModels.of(state)` → `RenderModel`(테스트 5개, 합계 52개). 속도처럼 지도에 안 쓰는 값은 빼서 `distinctUntilChanged()`만으로 바뀔 때만 보낸다
  - `:app` `MapRenderer` + `WebMapRenderer`: `assets/web`을 띄우고 JSON을 `postWebMessage`로 보낸다. D에서는 숨기고 `onPause()`, 그동안 온 모델은 최신 하나만 들고 있다가 P에서 보낸다. 상태 요약 텍스트는 "상태" 버튼으로 접었다
  - `shared/`: TypeScript + Pixi 8.22.0, esbuild로 IIFE 한 파일(`map.js` 560KB, 외부 CDN 없음). 격자 색(비 파랑·어두움 노란 테두리·급정거 붉은 점), 진행 방향 부채꼴·거리 고리, 목적지 띠, 내 차, 알림 목록(DOM)
  - 발견: 앱 WebView에서 WebGL1이 차단되고 WebGL2만 된다. Pixi 자동 선택이 Canvas 2D로 떨어져서 `WebGLRenderer`를 직접 만든다(open-questions.md)
  - 에뮬레이터 확인 (2026-10-24 22:55): P에서 시뮬레이터 비 구간 14격자·내 위치·알림 5개 표시. 바뀐 게 없으면 5초 동안 0프레임
  - P → D 전환: 지도가 사라지고 한 줄 화면 "약 1km 앞 비 (중)". 그동안 WebView는 `document.hidden`, 5초 동안 프레임 0·모델 0
  - 격자 수별 측정(방향을 매 프레임 바꾸며 5초, 에뮬레이터 = 맥 GPU라 실차 근거로 쓰지 않음): 1,000개 60fps / 3,000개 60fps / 6,000개 60fps / 10,000개 59.7fps. 도형 다시 만들기 10,000개 10ms, 첫 프레임 최대 20ms
  - 남은 것: 기본 지도(도로) 없음
- [x] 알림 구간 묶기 (`feat/alert-zones`): 같은 종류의 이웃(8방향) 격자를 한 알림으로 묶는다. 거리 = 가장 가까운 격자, 비 강도 = 구간에서 가장 센 값. 패널에 "1.2km 구간"을 붙이고 지도는 구간 격자 전체에 테두리. 주행 한 줄은 그대로("약 1km 앞 비 (중)"). 이웃 찾기는 격자 번호 해시(O(n)). 테스트 2개
- [x] 내 차 화면(`MY_CAR`): 위에서 본 차 모형(와이퍼 켜질 때 두 번 쓸고 멈춤, 하향등 빛, 미등·브레이크등, 기어), 패널에 차량 신호·경고등(사용 불가/없음 구분)·"내가 보내는 신호". 정차 화면 오른쪽 위 "내 차 보기" 버튼으로 바꾼다. 테스트 1개(합계 55개)
- [x] 기상청 강수 기반 시뮬레이터 ([kma-rain.md](kma-rain.md)): 공공데이터포털 초단기실황(자동승인) + 쌍선형 보간 + 가상 차 2,500대. 녹화(`--live --record-only`)와 재생(`--snapshot`). 테스트 8개
  - 로컬 확인: 가짜 스냅숏으로 업로드 97,000개/분 → 서버 배포 격자 7,823개 → 앱 수신 6,932개, 주행 한 줄 "약 100m 앞 비 (강)"
  - 2026-10-25: 공공데이터포털 인증키 발급(사용자, 자동승인, 만료 2028-10-25), `simulator/.env`에 저장(커밋 안 함). 실제 호출로 응답 형식 확인, **최근 24시간 자료만 조회됨** → `--backfill 24` 추가
  - 남은 것: 비 오는 날 하루 안에 `npm run kma -- --backfill 24`로 녹화
- [x] 에뮬레이터 확인 (2026-10-24 23:16, P): 격자 6,932개 지도, 알림 "약 100m 앞 비 (강) · 4.9km 구간" 하나. 구간 테두리는 시작 격자만 굵게(수백 칸이 흰 그물로 덮이지 않게). 내 차 화면 전환, 들어올 때 와이퍼 두 번 쓸기 → 이후 4초 0프레임. 라이트·브레이크 켜짐 표시는 Virtual Console 조작으로 아직 확인하지 않음
- [x] 화면 다듬기 (2026-10-24 23:21)
  - 이미 비 구간 안이면 "지금 여기 비 (강) · 약 4.9km 계속", 주행 한 줄 "지금 비 (강) · 약 4.9km 계속"(짧게: "지금 비"). 내 차 격자가 구간에 들면 구간 안으로 본다. 테스트 2개(합계 56개)
  - 앱 시작 때 와이퍼·하향등·미등·브레이크 현재 값을 한 번 읽는다(`getCurrent...`, SDK 2.3.2 javap 확인). 에뮬레이터에서 "브레이크 끔"으로 바로 표시됨
  - 기본 제목 표시줄 제거(`FEATURE_NO_TITLE`)
  - D 전환 확인: 주행 한 줄 "지금 비 (강) · 약 4.9km 계속", WebView는 숨김·4초 동안 0프레임
  - 격자 틈을 화면 px 기준으로: 격자가 24px 이상이면 1.5px, 작으면 0(0.5px만 둬도 줄이 남았다). 알림 구간이 40칸을 넘으면 시작 격자만 테두리

## 2단계: 방향 확정 (10/27 ~ 11/2)

- [x] 앱 구조 설계: [architecture.md](architecture.md). 결정 1~5번(모듈, 주행 모드 화면, 격자·익명화, 서버 스택, 저장소 구조) 확정 (2026-10-24)
- [x] 렌더러 A안(WebView + Pixi) 확정 (결정 6, 2026-10-24)
- [x] 패키지명(결정 7): `io.roadsensor` 유지(2026-10-25)

- [x] ~~10/28 설명회 참석~~ → 참석하지 않고 기본값으로 진행. 10/29쯤 공모전 사이트에 녹화본·Q&A 정리가 올라오면 확인한다.
- [x] 렌더링 A/B안 결정 → A안. 수천 개 점을 그렸을 때의 fps는 렌더러를 만들면서 확인한다.
- [x] 기획서 초안(2026-10-24): 공식 접수양식의 02(8개 문항)만 채움. 원문 [proposal.md](proposal.md), 채운 양식 `docs/proposal/기획서-초안.docx`, 다시 만들기 `docs/proposal/fill_form.py`. 01·03·04(개인정보·서명)는 본인 작성
  - 2026-10-25: 예상 MAU를 근거(2030년 약 2,000만 대 탑재 목표, 2026년 5월 신형 그랜저부터, 2026년 4월 현대자동차그룹 발표)와 가정(설치율·월 활성)으로 나눠 채움. 출시 1년 차 국내 약 9천, 2030년 국내 약 18만. Retention 목표 30일 30%(가정). 화면 시안을 실제 도로 캡처로 교체
  - 남은 것: GitHub 링크, 시연 영상, 숏폼(선택)
  - 주의: Word에서 저장하면 문서 속성에 Word 계정 이름이 들어갈 수 있다 → 제출 전 "문서 검사"
- [x] 서버와 시뮬레이터 뼈대(격자 집계, 가상 차량) — 기상청 강수 데이터 연동은 남음

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
| 백그라운드 앱이 약 8초 뒤 동결 | 주행 중 신호 수집·전송이 끊김 | 포그라운드 서비스(`dataSync`)로 해결 확인. 설명회 없이 그대로 진행 |
| 속도 권한이 dangerous | 첫 실행 권한 요청 UX | 정차 중 첫 실행에서 요청(주행 중에는 설정 화면 제한) |

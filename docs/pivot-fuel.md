# 주제 전환 사전 확인: 내 차 주유비 매니저(가칭)

Road Sensor → "내 차 주유비 매니저"로 바꿀 수 있는지 확인한 기록이다. 본 앱 코드는 바꾸지 않았다. 확인일 2026-11-04, 브랜치 `feat/fuel-pivot-check`, 시험 앱 `spikes/fuel-probe`.

표기: ✅ 가능 · ❌ 불가 · ❓ 불확실(근거와 확인 방법을 적음)

## 0. 결론(잠정): **조건부**

- 강점: 내비 경유지 추가가 에뮬레이터에서 동작하고, **우회 거리·시간을 내비가 직접 계산해 준다**(§6). "실제 이득" 공식의 우회 비용을 추정 없이 쓸 수 있다
- 전기차 쪽은 배터리 잔량(Wh)·SoC·용량·충전 상태·공조·외기 온도를 에뮬레이터에서 읽는다(§9)
- 막히는 것 1: **에뮬레이터 차량이 전기차다**(`INFO_FUEL_TYPE`=10, 전기). Pleos 연료량·연비 API는 전부 "지원하지 않는 속성"으로 실패했다. 내연기관 데이터는 에뮬레이터로 시연할 수 없고, 실차에서 되는지도 모른다. 그런데 지금 Pleos 탑재 차종(그랜저·아반떼)은 내연기관·하이브리드 중심이다
- 막히는 것 1-2: **에뮬레이터는 주행·충전에 따른 에너지 변화를 흉내 내지 않는다**. D + ACCEL로 시속 140km까지 달려도 배터리 잔량·주행거리·순간 전력이 그대로였고, 충전 스위치를 켜도 충전이 진행되지 않았다. 값은 Virtual Console로 손으로 바꿀 때만 바뀐다(SoC·잔량·충전 포트·공조는 즉시 반영). 운행 단위 소비량을 쓰는 기능은 데모 데이터 재생이 필요하다
- 막히는 것 2: **운전 습관의 원화 환산 근거가 약하다**. 가속 페달·엔진 RPM·시동 상태가 없어서 급가속은 속도 변화로만 추정한다. 공신력 있는 수치(미국 에너지부)는 범위가 넓고(10~40%) 전기차에는 적용 근거가 없다(§7)
- 막히는 것 3: 오피넷 무료 키는 **하루 1,500건**(2차 자료)이고 키 하나를 모든 사용자가 나눠 쓴다. 수익 활용은 석유공사 사전 협의가 필요하다(§5)
- 그래서 "진행 가능"이 되려면: (1) MVP 범위를 정하고(§12), (2) 내연기관이면 실차 연료 API 지원을 Pleos에 확인하고, (3) 운전 습관은 "원화 단정"이 아니라 "내 평소 대비 상대 비교"로 낮춰 잡아야 한다

## 1. 연료 데이터 (내연기관)

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| Pleos `Fuel` 도메인 API 존재 | ✅ | SDK 2.3.2 javap: `getFuelPercent((Int)->Unit, onFailed)`, `registerFuelPercent(FuelPercentListener{onFuelPercentUpdated(Int?)})`, 연비 4종(아래 §2). 권한 `pleos.car.permission.CAR_ENERGY`(**normal**) |
| 에뮬레이터에서 값이 나오나 | ❌ | `getFuelPercent` → `IllegalArgumentException: property ID ... not supported: 0x21400be5`(Pleos 전용 속성이 에뮬레이터 VHAL에 없음) |
| AAOS 표준 `FUEL_LEVEL`·`INFO_FUEL_CAPACITY` | ❓ | 값은 온다(`FUEL_LEVEL`=15000.0, `INFO_FUEL_CAPACITY`=15000.0, 단위 mL로 보임). 하지만 차가 전기차(`INFO_FUEL_TYPE`=[10])라 기본값으로 보이고 의미가 없다 |
| `FUEL_LEVEL_LOW` | ✅(값) | false. 구독 가능(바뀔 때만) |
| `RANGE_REMAINING` | ✅(값) | 0.0. 1~2Hz 연속 속성. Emulation Controls에 Remaining Range 항목이 있다(이번에 바꿔 보지는 않음). 주행해도 0.0에서 바뀌지 않았다 |
| AAOS 권한 | | `android.car.permission.CAR_ENERGY` **dangerous**(런타임 요청), `CAR_INFO` normal. Pleos `CAR_ENERGY`는 normal이라 Pleos 경로가 쉽다 |
| 해상도 | ❓ | Pleos는 `Int` 퍼센트(1% 단위). 50L 탱크면 1% = 0.5L라 한 번 운행의 소비량을 재기엔 거칠다 |
| Virtual Console로 연료량 바꾸기 | ❌(추정) | Emulation Controls의 Energy 항목은 Battery SOC·Energy·Capacity·Charge·Remaining Range뿐이고 연료가 없다 |

## 2. 연비 계산 재료

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 연비 API | ✅ 존재 / ❌ 에뮬레이터 | Pleos `Fuel`: `getInstantaneousFuelEconomy`, `getCurrentFuelEconomy`, `getCumulativeFuelEconomy`, `getSinceChargeFuelEconomy`(모두 Float), `register…`(FuelEconomyListener). 에뮬레이터에서 전부 미지원(`0x21600be0~be2`, `ERROR_NOT_SUPPORTED_PROPERTY`). 단위 모름 |
| AAOS 표준 연비 속성 | ❌ | 표준 속성에 순간 연비가 없다. `FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME`는 표시 단위 설정일 뿐 |
| 주행거리 | ✅ Pleos(값) / ❌ AAOS | Pleos `Odometer.getCurrentDistanceDriven`(Float, `CAR_DRIVE` normal) 값 0.0이 옴. 다만 에뮬레이터에서 D + ACCEL로 달려도 0.0에서 늘지 않았다(콘솔 Odometer 항목으로만 바뀌는 것으로 보임). 지금 본 앱처럼 속도를 적분한 자체 거리계로 대신할 수 있다. AAOS `PERF_ODOMETER`는 `CAR_MILEAGE`(**signature\|privileged**) 필요해서 읽기 실패 |
| 주유 후 주행거리·시간 | ❌ 에뮬레이터 | Pleos `getTripSinceRefillDrivingDistance/Time` 미지원 |
| 연료량 변화 ÷ 주행거리 | ❓ | 연료량이 1% 단위라 짧은 운행에선 0 또는 1%로 튄다. 며칠 단위 평균 연비 정도만 계산 가능. 실차 해상도 확인 필요 |

## 3. 운전 습관 신호

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 가속 페달 값 | ❌ | Pleos SDK·AAOS 표준 속성 모두 없음(2026-10-31 확인, docs/pleos/vehicle-sdk.md "SteeringWheel") |
| 속도 변화율로 급가속·급감속 | ✅ | `PERF_VEHICLE_SPEED` 0.2초 간격(이미 사용 중). 급정거 판정은 이미 구현 |
| 브레이크 페달 | ✅ | 밟음/뗌만 |
| 엔진 RPM | ❌ | `ENGINE_RPM` 속성이 에뮬레이터에 없음. 있더라도 `CAR_ENGINE_DETAILED`는 signature\|privileged |
| 시동 상태(`IGNITION_STATE`, `ENGINE_ON`) | ❌ | 속성 없음 |
| 공회전 판정 | ❓ | "속도 0 + 기어 P 아님"만 가능. 하이브리드·ISG 차는 정차 중 엔진이 꺼져 있어 오판한다. 전기차는 공회전 개념이 없다(대신 정차 중 공조 사용) |
| 기어 | ✅ | Pleos `getCurrentGearSelectionState` = `GEAR_DRIVE`, Virtual Console 표시도 D(일치) |

## 4. 주유 감지

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 연료량 급증으로 주유 시점·양 | ❓ | 논리상 가능(퍼센트 급증 × 탱크 용량). 에뮬레이터는 연료 값이 없어 시험 불가. Pleos에는 탱크 용량 API가 없고 AAOS `INFO_FUEL_CAPACITY`(CAR_INFO normal)를 써야 함 |
| 전기차 충전 감지 | ✅(값) | `EV_CHARGE_STATE`(=2), `EV_CHARGE_PORT_CONNECTED`, Pleos `isEvChargePortOpen`·`getEvChargeSwitchState`. 충전 포트 열림/닫힘은 바로 온다. 충전 스위치를 켜도 충전이 진행되지 않아 충전량 기록은 에뮬레이터로 시험할 수 없다(§9) |

## 5. 오피넷 유가정보 API

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| 키 발급 | 회원가입 → "무료 API 이용 신청" → 인증키 | 오피넷 이용 안내, 2차 자료 |
| 반경 검색 | `https://www.opinet.co.kr/api/aroundAll.do` `certkey`, `out`(xml/json), `x`,`y`(**KATEC**), `radius`(**최대 5,000m**), `prodcd`(B027 휘발유, D047 경유, B034 고급휘발유, K015 자동차부탄), `sort`(1 가격순, 2 거리순) | 오피넷 API 상세(apiId=3) |
| 응답 | `UNI_ID`, `POLL_DIV_CD`(상표, 예시에는 `POLL_DIV_CO`), `OS_NM`, `PRICE`, `DISTANCE`(m), `GIS_X_COOR`, `GIS_Y_COOR`(KATEC) | 같은 페이지 |
| 좌표 변환 | 필요(WGS84 ↔ KATEC). 변환 라이브러리와 그 라이선스는 확인 필요 | |
| 호출 제한 | **하루 1,500건**(공식 페이지에서는 확인 못 함, 2차 자료 두 곳 일치). 키 하나를 전체 사용자가 나눠 쓰면 서비스 규모에서 바로 막힌다 → 서버 캐시 또는 협의 필요 | GitHub hass-opinet, Glama opinet-mcp |
| 가격 갱신 주기 | ❓ 문서에서 찾지 못함 | 석유공사 문의 (052)216-2514, price@knoc.co.kr |
| 이용 조건 | API 전용 약관은 못 찾음. 사이트 저작권 정책: 저작권은 석유공사, 무단 복제·배포 금지, **수익 활용은 사전 협의·허락**, 출처 표시. 공공데이터포털의 "전국 주유소 평균가격" API는 "이용허락범위 제한 없음" | 오피넷 이용 안내, data.go.kr 15150932 |
| 진행 방향 검색 | 반경 최대 5km라 앞쪽 지점 몇 곳을 잡아 여러 번 부르는 방식(기존 "진행 방향 부채꼴 + 목적지 방향 띠" 그대로). 호출 수가 늘어난다 | |

## 6. 내비 연동 (NaviHelper 3.0.5)

| 항목 | 결과 | 근거(에뮬레이터 로그) |
| --- | --- | --- |
| 경로 요청 | ✅ | `requestRoute(RouteInfo(판교역))` → `onRouteStarted(duration=720, distance=3100, remainDestinationChargingValue=77003)` |
| 경유지 추가 | ✅ | `addWaypoint(RequestWaypointInfo(lat, lon, "테스트 주유소", WaypointIndex.FIRST, …))` → `onWaypointChanged(index=FIRST, cmdType=ADD)`, `DrivingInfo` 목적지 4,600m·1,140초, 경유지 3,100m·720초 |
| **우회 비용** | ✅ | 경유지 전후 목적지 거리·시간 차이 = 우회 거리·시간(이 예에서 +1.5km, +7분). 추정 없이 쓸 수 있다 |
| 도착 시 잔량 | ✅(전기차) | `RouteStateInfo.remainDestinationChargingInt` 77003 → 경유지 추가 후 76783, 경유지 잔량 77005. 배터리 77,400과 비교하면 **Wh 단위로 보인다**(3.1km에 약 397Wh). 콘솔에서 Battery Energy를 12,000으로 바꾸자 `DrivingInfo` 도착 잔량이 11,385, 경유지 잔량이 11,606으로 다시 계산되어 와서 Wh가 맞아 보인다. 내연기관 차에서 무엇을 주는지는 모름 |
| 충전 사업자 목록 | ✅ | `getChargerOperatorInfo()` → 35개(HD 현대자동차(E-pit), PI GS차지비, EV 에버온, PW 파워큐브, ME 환경부 …). id·name만 있고 요금·위치는 없다 |
| 주의 | ❓ | 경유지 추가 직후 `onRouteCancelled`가 한 번 오고 다시 경로가 잡힌다. `WaypointInfo`의 위경도가 0.0으로 온다(주소는 "분당구 삼평동"으로 채워짐). POI ID 없이 좌표만 넣었다 |
| 권한 | | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` 모두 normal |

## 7. 연비 손실의 원화 환산 근거

| 자료 | 내용 | 한계 |
| --- | --- | --- |
| 미국 에너지부 fueleconomy.gov "Driving More Efficiently" | 공격적 운전(과속·급가속·급제동)은 연비를 고속도로에서 약 15~30%, 시내 정체에서 10~40% 낮춘다. 공회전은 시간당 약 0.25~0.5갤런(약 0.95~1.9L), 엔진 크기·에어컨에 따라 다름 | 범위가 넓다. 디젤·하이브리드는 덜 정확하고, **플러그인·전기차는 적용 여부를 모른다**고 명시 |
| 국내 공신력 자료 | ❓ 한국에너지공단 경제운전 공식 수치는 이번에 찾지 못함 | 에너지공단 자료 직접 확인 필요 |
| 전기차 공조 | AAA(2019): 영하 7℃(20°F)에서 히터를 켜면 주행거리 평균 41% 감소, 히터 없이 추위만이면 12%. 35℃(95°F) 에어컨은 17% | 실험실(다이나모) 5차종 평균, 테슬라는 반박 |

과장 없이 할 수 있는 방법
- 원화 단정("급가속으로 4,000원") 대신 **내 평소 대비 상대 비교**: 급가속·급감속이 적었던 날과 많았던 날의 평균 연비(전비)를 내 기록끼리 비교하고, 차이를 그날 유가로 원화 환산한다. 데이터가 쌓일 때까지(예: 2주) 숫자를 보여 주지 않는다
- 이것도 **운행 단위 소비량을 잴 수 있어야** 성립한다. 전기차는 배터리 Wh(Float)로 실차에서는 가능성이 있지만 에뮬레이터는 주행해도 잔량이 줄지 않는다(§9), 내연기관은 연료 1% 단위라 며칠 단위로만 가능하다. 실차에서 순간 연비 API가 되면 훨씬 좋아진다
- 공식 범위(10~40%)는 "참고: 일반적으로 이 정도"라는 안내 문구로만 쓴다

## 8. 공모전 조건 (사용자가 확인할 목록)

- [ ] 이미 기획서를 제출했는지. FAQ상 마감(11/15 23:59) 전 같은 메일로 재제출 가능하고 마지막 자료가 최종본(전체 자료 + 서명한 동의서 다시 첨부)
- [ ] 기획서 8문항 전부(한 줄 소개, 배경, 기능·차량 특화, 시나리오, SDK 사용 계획, 기대효과·MAU, 참고자료, 숏폼)를 새 주제로 다시 쓸지
- [ ] 숏폼을 이미 올렸는지(링크 교체 필요 여부)
- [ ] GitHub 저장소 이름·README·`apk/팀명_앱이름_release.apk` 이름
- [ ] 오피넷 데이터 사용 조건: 공모전 출품·수상 시 주최 측 통상실시권·전시와 "수익 활용 사전 협의" 조항의 관계(석유공사 문의)
- [ ] 공공데이터(충전소) 공공누리 제1유형 출처 표시
- [ ] 남은 기간: 11/4 기준 접수 마감까지 11일

## 9. 전기차 지원 (추가 확인)

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 에뮬레이터가 전기차인가 | ✅ | `INFO_FUEL_TYPE`=[10](전기), `INFO_EV_BATTERY_CAPACITY`=77400.0, `INFO_EV_CONNECTOR_TYPE`=[5] |
| 배터리 잔량 | ✅ | Pleos `getEvBatteryLevel`=77400.0(Float, Wh로 보임), `getEvBatteryStateOfCharge`=100(Int %), `getEvBatteryCapacity`=77400.0(이것만 `pleos.car.permission.CAR_INFO`도 필요, 없으면 `SecurityException`). AAOS `EV_BATTERY_LEVEL`=77400.0, `EV_CURRENT_BATTERY_CAPACITY`=77400.0. 모두 구독 가능 |
| Virtual Console로 배터리 바꾸기 | ✅ | Emulation Controls → Battery SOC를 움직이면 Pleos `ev.soc`가 바로 온다(67→73→86→53→47, 1% 단위 정수, 0.5~1초 간격으로 따라옴). Battery Energy를 바꾸면 Pleos `ev.level`과 AAOS `EV_BATTERY_LEVEL`이 같은 시각에 온다(77400→50000→12000). **두 값은 서로 연동되지 않는다**(SoC 47%인데 잔량 12,000Wh). 시연에서는 둘을 같이 맞춰야 한다 |
| 전비 API | ✅ 존재 / ❌ 에뮬레이터 | Pleos `EvBattery` 순간·현재·누적·충전 후 전비 모두 미지원(`0x21600b13~b15`) |
| 순간 전력·회생제동 | ❌ 에뮬레이터 / ❓ 실차 | AAOS `EV_BATTERY_INSTANTANEOUS_CHARGE_RATE`=0.0(1~10Hz). D + ACCEL로 약 10초 동안 시속 140km까지 가속했다가 BRAKE로 세웠지만 **0.0 그대로**였다. `EV_REGENERATIVE_BRAKING_STATE`=2도 그대로(이 값은 회생제동 **설정** 상태라 "지금 회생 중"은 아니다) |
| 주행 중 소비 | ❌ 에뮬레이터 | 같은 주행 동안 배터리 잔량(Pleos·AAOS), Pleos 주행거리(`odo.distance` 0.0), 주행 가능 거리(0.0)가 **하나도 바뀌지 않았다**. 에뮬레이터는 주행에 따른 에너지 소비를 흉내 내지 않는다 → 운행 단위 소비량·전비 계산은 에뮬레이터로 시연할 수 없고, 데모 데이터(재생)가 필요하다 |
| 충전 상태 | ✅ 포트 / ❌ 충전 진행 | `EV_CHARGE_STATE`=2, `EV_CHARGE_PORT_CONNECTED`=false, `EV_CHARGE_TIME_REMAINING`=0. Virtual Console Charge Port Open → Pleos `ev.portOpen` true, Closed → false가 바로 왔다. Charge Switch On으로 10초 두어도 `EV_CHARGE_STATE`·`EV_CHARGE_PORT_CONNECTED`·충전 속도·배터리 잔량은 그대로였다 → **에뮬레이터는 충전 진행도 흉내 내지 않는다**. Pleos는 충전 스위치를 조회(`getEvChargeSwitchState`)만 하고 구독 API가 없다. AAOS `EV_CHARGE_SWITCH`는 이번 시험에서 구독하지 않았다 |
| 공조 | ✅ | Pleos `CAR_CLIMATE`(normal). 설정 온도 22.0·실내 온도 19.1은 운전석 영역으로 읽힘. 전원·에어컨·자동·풍량은 운전석 영역으로는 실패하고(조회·구독 모두), `check…Capabilities`가 돌려준 지원 영역(1·2열 전체 한 영역)으로 써야 한다. Virtual Console 조작이 모두 바로 왔다: 전원 On/Off(true → false, 5초 간격 그대로), 운전석 설정 온도 23.0 → 25.0 → 26.5 → 18.5(0.5℃ 단위), 에어컨 Off/On/Off. AAOS 공조는 `CONTROL_CAR_CLIMATE`(signature\|privileged)라 Pleos로 읽는다 |
| 외기 온도 | ✅ | AAOS `ENV_OUTSIDE_TEMPERATURE`=25.0(`CAR_EXTERIOR_ENVIRONMENT` normal) |
| 도착 시 잔량·충전 사업자 | ✅ | §6 |
| 공공 충전소 API | ✅/❓ | 한국환경공단 "전기자동차 충전소 정보"(data.go.kr 15076352): 충전소 정보(위치, 충전기 타입, 출력, 운영기관, 이용 시간) + 충전기 상태(`stat`, `statUpdDt`, `period` 1~10분 조회 범위, 기본 5분). 무료, 업데이트 "실시간", 공공누리 제1유형. **요금 필드 없음**. 상태 코드 뜻과 호출 제한은 상세 명세서 확인 필요 |
| 충전 요금 비교 | ❌ | 사업자·회원·급속/완속별 요금을 주는 공공 API를 찾지 못함 |

## 10. 막히는 항목의 대안

| 막힘 | 대안 |
| --- | --- |
| 에뮬레이터에 연료 값 없음 | 연료 어댑터 뒤에 **데모 연료 공급원**(데모 궤적처럼 연료량·연비를 재생)을 두고 시연. 실차 동작은 Pleos 답변 전까지 "확인 필요"로 표기 |
| 가속 페달·RPM 없음 | 속도 변화율(0.2초)로 급가속·급감속, 정차 시간으로 공회전 근사. 기획서에 한계를 적는다 |
| 운행 단위 소비량을 못 잼(내연기관) | 며칠 단위 평균 연비로 비교, 실차 순간 연비 API 확인 |
| 오피넷 호출 한도 | 서버에서 격자 단위로 캐시(유가는 하루 몇 번만 바뀜), 사용자당 직접 호출 금지. 서비스 규모면 석유공사 협의 |
| KATEC 좌표 | 서버에서 변환(오픈소스 라이브러리 라이선스 확인) |
| 충전 요금 비교 불가 | 전기차는 요금 대신 "지금 비어 있는 충전기" + 도착 잔량 중심 |

## 11. Pleos 기술 지원 문의 목록

1. 실차(내연기관·하이브리드: 그랜저·아반떼)에서 `Fuel.getFuelPercent`와 연비 4종(순간·현재·누적·주유 후)이 지원되는지, 단위·해상도·갱신 주기
2. 에뮬레이터를 내연기관 차로 바꾸는 방법(차종 설정)이나 Virtual Console 연료 조작이 있는지
3. 실차 전기차에서 `EvBattery` 전비 4종과 `Odometer` 트립(주유·충전 후) API가 지원되는지
4. 가속 페달 위치, 엔진 RPM, 시동 상태를 서드파티에 제공할 계획이 있는지. 에뮬레이터 Virtual Console 안에는 가속 페달 값(`accelerator_pct`, DDS 토픽)과 전원 상태(Off~Driving)가 이미 있는데 Vehicle SDK로는 오지 않는다
5. 서드파티 앱이 `addWaypoint`로 주유소·충전소를 경유지에 넣는 것이 정책상 허용되는지, 주행 중 허용 여부, 운전자 확인이 필요한지
6. `RequestWaypointInfo`의 `poiId`·`poiProvider` 체계(내비 POI와 연결하는 방법), 추가한 경유지의 `WaypointInfo` 위경도가 0.0으로 오는 이유
7. `remainDestinationChargingInt` 단위(Wh인지)와 내연기관 차에서의 의미
8. 에뮬레이터에서 배터리 SoC와 잔량(Wh)이 따로 움직이는데, 실차에서는 둘이 같은 출처인지
9. 안내 중인데 `RouteStateInfo.isRouting`이 false로 오는 이유(의미가 "경로 탐색 중"인지), `DestinationInfo.remainDistance`가 10000으로 오는 이유와 단위. 경유지를 넣기 전 목적지까지 남은 거리를 얻는 올바른 방법

## 12. 결정이 필요한 것 (사용자에게)

### MVP 축

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| A. 주유·충전소 추천(어디서 넣을까) | 오피넷·충전소 API와 내비 우회 비용으로 **에뮬레이터에서 끝까지 시연 가능**. 효과(원화)가 계산식으로 분명 | 티맵·오피넷·블루링크로 대체 가능하다는 약점(차량특화성). 오피넷 호출 한도·이용 조건 |
| B. 운전 습관 비용(어떻게 달릴까) | 차에서만 되는 차별점 | 페달·RPM이 없어 근사. 원화 환산 근거가 약하고 데이터가 쌓여야 의미가 생김. 에뮬레이터 시연이 어렵다(가짜 데이터 필요) |
| C. A를 중심으로, B는 "이번 운행 요약" 한 줄만 | 시연 가능 + 차량특화 이야기를 함께 | 둘 다 얕아질 위험 |

### 차종 범위

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| 내연기관만 | 지금 Pleos 탑재 차(그랜저·아반떼)와 맞음. 오피넷 유가로 원화 계산이 깔끔 | **에뮬레이터에서 연료 데이터를 못 읽음**(데모 공급원 필요). 실차 지원 미확인 |
| 전기차만 | 에뮬레이터에서 실제 데이터로 시연 가능. 현대차 전동화 방향과 맞음. 공조·외기 온도 코칭 근거(AAA) | 충전 요금 비교 데이터가 없음. 지금 Pleos 탑재 차종과 어긋남 |
| 둘 다(에너지 비용 어댑터) | 구조상 깔끔하고 확장 이야기가 좋음 | 남은 11일에 두 배 작업. 시연은 결국 전기차 쪽만 실데이터 |

### 결정 (2026-11-04, 사용자)

- MVP 축: **C. 주유·충전소 추천(A) 중심 + 운전 습관(B)은 "이번 운행 요약" 한 줄**
- 차종 범위: **둘 다 지원, 시연은 전기차**. 핵심 로직은 "에너지 비용(원)"으로 일반화하고 연료·전기는 어댑터로 나눈다. 에뮬레이터 실데이터는 전기차 쪽, 내연기관은 데모 공급원 + Pleos 문의(§11)
- 주제를 실제로 바꿀지(공모전 조건 §8 확인)와 본 앱 구현 시작은 아직 정하지 않았다

### 결정 변경 (2026-11-04, 사용자): 시연은 휘발유

- **시연 차종을 전기차에서 휘발유로 바꾼다.** 둘 다 지원하는 구조(에너지 비용 어댑터)는 그대로 둔다
- 이유
  - 지금 Pleos Connect가 탑재된 현대차는 더 뉴 그랜저(2026-05 출시)이고 파워트레인은 가솔린 2.5·3.5, 1.6 터보 하이브리드, LPG 3.5다. 전기차는 없다. 초기 계약은 가솔린 58%, 하이브리드 40%, LPG 2%(보도 기준)
  - 추천의 "실제 이득"을 진짜 가격으로 계산할 수 있다: 오피넷은 주유소별 가격을 주지만, 충전소별 요금을 비교할 공공 API는 없다(§9)
  - 데모 전기차도 잔량은 데모였다(에뮬레이터는 달려도 배터리가 줄지 않음). 속도·브레이크·기어·주행 규제·내비는 어느 쪽이든 실제 SDK 신호다
- 잃는 것: 연료 잔량을 SDK에서 읽는 장면(Virtual Console로 SoC를 바꾸면 앱이 따라오는 장면)을 못 보인다. 에뮬레이터 화면에 전기차(아이오닉5)가 보여서, 영상에서 "에뮬레이터 차가 전기차라 연료는 데모 공급원"이라고 밝힌다. 실차 연료 API 지원은 §11 1번 문의로 남는다
- 앱 기본값: `EnergyMode.DEMO_FUEL`. 경유·LPG는 아직 구분하지 않는다(휘발유 가격·발열량으로 계산)

## 13. 재검토: 운전 습관 비용("어떻게 달릴까")이 핵심일 때 (2026-11-04)

사용자 의견: 서비스의 핵심은 최저가 주유소 찾기가 아니라 "급가속·급제동·공회전이 연비를 얼마나 깎았는지 원화로, 내 평소 주행과 비교해서" 보여 주는 것이다.

### 13-1. 새로 열린 SDK

| SDK | 상태 | 이 기능에 주는 것 |
| --- | --- | --- |
| FusedLocation 1.1.3 | 문서·저장소에 있음 | `LocationData(latitude, longitude, altitude, heading, confidence, timestampMillis)`(javap). 속도·가속도는 없다. **고도**로 오르막·내리막을 보정할 수 있다(갱신 주기는 문서에 없음). 권한 `pleos.car.permission.FUSED_LOCATION` |
| TextToSpeech 3.3.1, SpeechToText 3.3.1 | 문서·저장소에 있음 | 운행 요약을 말로 읽어 주기 |
| LLM | 에뮬레이터 2.1.0 릴리스 노트에서 **제공 종료** | |
| Vehicle 2.3.2 | 바뀌지 않음(저장소 최신 2.3.2) | 가속 페달·RPM·시동 상태는 여전히 없다 |
| placesense V1 | 저장소에만 있고 문서에 없음 | 쓰지 않는다 |

### 13-2. 쓸 수 있는 재료

| 재료 | 출처 | 상태 |
| --- | --- | --- |
| 속도 0.2초 간격 | AAOS `PERF_VEHICLE_SPEED` | ✅ 이미 사용 중 |
| 브레이크 밟음/뗌 | Pleos Brake | ✅ |
| 기어, 연료 종류 | Pleos Gear, AAOS `INFO_FUEL_TYPE` | ✅ |
| 고도 | FusedLocation | ✅ 값 형태 확인, 에뮬레이터 동작은 미확인 |
| 차 크기 | AAOS `INFO_EXTERIOR_DIMENSIONS`(CAR_INFO normal) = [1776, 4950, 2008, 2140, 2984, 1665, 1667, 11800](높이·길이·폭·거울 포함 폭·축간거리·윤거·회전반경, mm) | ✅ 공기저항 면적 추정에 쓴다 |
| 차 무게 | AAOS `VEHICLE_CURB_WEIGHT` | ❌ `PRIVILEGED_CAR_INFO` 필요. 차종(`INFO_MODEL`) → 공개 제원 표, 또는 사용자 입력·기본값 |
| 실제 소비량 | Pleos 연비·전비 API, 배터리 Wh | ❌ 에뮬레이터 미지원·주행해도 안 바뀜. 실차 확인 필요 |
| 유가 | 공공데이터포털 한국석유공사 "전국 주유소 평균가격"(15150932, 무료, **이용허락범위 제한 없음**) | ✅ 원화 환산은 평균가 하루 한 번이면 충분(반경 검색 키·호출 한도 문제 없음). 오퍼레이션·변수는 활용가이드 확인 필요 |
| 전기 단가 | 공공 API 없음 | 사용자 설정(기본값은 공공 급속 요금, 값 확인 필요) |

### 13-3. 계산 방법: 측정이 아니라 물리 기반 추정 + 내 평소 대비 비교

실제 소비량을 실시간으로 잴 수 없으므로(§2, §9), 속도 기록에서 "버린 에너지"를 계산하고 연료·전기로 환산한다.

| 항목 | 계산 | 근거 수준 |
| --- | --- | --- |
| **급제동** | 브레이크 중 잃은 운동 에너지 ½·m·(v₁² − v₂²)에서 그동안 구름저항·공기저항이 쓴 몫을 뺀 것 = 마찰 브레이크로 버린 에너지. 내연기관: ÷ (엔진·구동 효율 × 휘발유 발열량 약 32MJ/L). 전기차: 회생제동으로 일부 회수되므로 × (1 − 회수율) | 물리 법칙이라 설명 가능. 효율·회수율·무게는 가정값 |
| **과속** | 공기저항 에너지 ½·ρ·Cd·A·v²·거리에서 기준 속도(예: 시속 100km) 초과분. A는 차 크기에서, Cd는 가정(0.28~0.30) | 물리 법칙. Cd·효율은 가정값 |
| **공회전**(내연기관) | 정차 시간 × 공회전 연료율(미국 에너지부: 시간당 약 0.95~1.9L, 배기량·에어컨에 따라) | 근거 있음. 시동 상태를 몰라 ISG·하이브리드에서 과대 추정 → "N분 이상 긴 정차"만 세고, 하이브리드 구분은 확인 필요 |
| **급가속** | 같은 속도까지의 운동 에너지는 천천히 가속해도 같다. 손실은 엔진 고부하 효율 저하와 "급가속 → 급제동" 패턴에서 생긴다 | **원화 단독 환산 근거가 약하다**. 횟수와 패턴으로 보여 주고, 원화는 급제동·과속에 몰아서 계산 |
| **평소 대비** | 주 단위 "100km당 손실(원)"을 지난 4주 내 평균과 비교 → "이번 주 급제동으로 평소보다 약 1,200원 더" | 같은 차·같은 가정끼리 비교라 무게·효율 가정의 오차가 상당 부분 상쇄된다. 데이터가 2주쯤 쌓일 때까지는 숫자를 안 보여 준다 |

어림 계산(가정: 1,600kg, 엔진·구동 효율 25%, 휘발유 1,700원/L): 시속 60km → 0 급제동 한 번 ≈ 222kJ ≈ 0.028L ≈ **약 47원**. 하루 10번이면 약 470원, 주 5일이면 약 2,300원이다. "이번 주 급제동으로 약 2,000원" 정도가 물리적으로 말이 되는 크기다. 다만 신호등처럼 피할 수 없는 정지도 들어 있어서, "평소 대비"로 피할 수 있었던 몫을 보여 주는 편이 정직하다.

판정 기준은 국내 운행기록 분석 논문에 인용된 위험운전 정의를 출발점으로 쓴다(공단 공식 고시는 확인 필요): 급가속 = 초당 11km/h 이상 가속, 급감속 = 초당 7.5km/h 이상 감속, 급제동 = 브레이크와 함께 초당 11km/h 이상 감속, 과속 = 제한속도 + 20km/h 초과.

### 13-4. 에뮬레이터 시연과 검증

- ✅ 시연 가능: 판정과 계산의 입력(속도·브레이크)은 실제 SDK 데이터다. Virtual Console ACCEL로 약 10초에 시속 0 → 140km(평균 초당 약 14km/h, 급가속 기준 초과), BRAKE로 급감속이 실제로 나온다(§9 주행 시험). 그 데이터로 "이번 운행에서 버린 에너지·원화"를 계산해 보여 줄 수 있다
- ❌ 검증 불가: 에뮬레이터는 소비를 흉내 내지 않아 추정이 실제 소비와 맞는지 비교할 수 없다. 실차에서 Pleos 누적 연비(지원 시)나 전기차 배터리 Wh 변화로 보정해야 한다. 그 전까지는 문헌 범위(공격적 운전 시 10~40% 손실)를 크게 넘지 않는지로 상한을 점검한다

### 13-4-1. Virtual Console로 소비를 흉내 낼 수 있나 (2026-11-04)

- 공식 Virtual Console 가이드의 Energy 항목은 8개(Remaining Range, Battery Energy·SOC·Capacity, 충전 전류·목표, Charge Switch, Charge Port)이고 **모두 손으로 값을 넣는 방식**이다. 연료 항목과 차종(내연기관) 전환은 없다. Motion에는 Odometer(km)가 있다
- Pleos `FuelPercentListener`·`getFuelPercent`는 SDK에 있지만(§1) 이 에뮬레이터 차는 전기차라 연료 속성(`0x21400be5`)이 없어 실패한다
- Virtual Console 페이지는 HTTP로 값을 쓴다. `GET /vehicle/catalog`(63개 항목: 쓰기 주소·속성 ID), `GET /vehicle/state`(현재 값). 에너지 쓰기 주소: `/vehicle/ev-battery`(Battery Energy `EV_BATTERY_LEVEL`, SOC 벤더 속성 `0x21400BA7`, 용량, 충전), `/vehicle/odometer`(누적 거리 `PERF_ODOMETER`, 주행 가능 거리 `RANGE_REMAINING`)
- 그래서 **시연용 "소비 시뮬레이터"를 앱 밖에 둘 수 있다**: 콘솔 상태의 속도(`controls.speed_kph`)를 읽어 주행 거리·배터리 에너지를 줄이는 값을 위 주소로 써 넣으면, 앱은 실제 SDK 경로로 소비를 받는다. 단, 이 소비는 우리가 넣은 모델 값이라 **추정의 검증에는 쓸 수 없고 시연용**이다. Virtual Console에 값을 쓰는 일이라 사용자 승인 후에 만든다
- 참고로 플랫폼 안에는 가속 페달(`controls.accelerator_pct`, DDS 토픽 `SDV_MOTION_ACCELERATOR_BASE_ACCELERATORBASE_ACTUAL_PEDAL`)과 전원 상태(숨은 항목 `power-system-actual`: Off·StandBy·Ready·On·Driving)가 있다. Vehicle SDK 2.3.2로는 앱에 오지 않는다 → Pleos 문의(§11) 대상

### 13-5. 결론: 가능(추정으로)

- "급제동·과속·긴 공회전으로 약 얼마 더 썼다, 평소보다 얼마 많다"는 **물리 기반 추정으로 가능하다**. 문구는 "약"으로, 계산 방법은 화면에서 열어 볼 수 있게 한다
- 급가속 원화 단독 환산은 근거가 약해 횟수·패턴으로만 보여 준다
- 원화 환산용 유가는 공공데이터포털 평균가로 충분하다(오피넷 반경 검색 키·호출 한도 문제를 피한다)
- §12의 MVP 결정(추천 중심)은 이 의견에 맞춰 다시 정해야 한다

## 14. 시험 방법 (`spikes/fuel-probe`)

```bash
cd spikes/fuel-probe && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant --user 10 io.fuelmate.fuelprobe android.car.permission.CAR_ENERGY
adb shell pm grant --user 10 io.fuelmate.fuelprobe android.car.permission.CAR_SPEED
adb shell am start --user 10 -n io.fuelmate.fuelprobe/.MainActivity
adb shell am start --user 10 -n io.fuelmate.fuelprobe/.MainActivity --es cmd route      # 판교역 경로
adb shell am start --user 10 -n io.fuelmate.fuelprobe/.MainActivity --es cmd waypoint   # 경유지 추가
adb logcat -s FuelProbe
```

로그 앞 글자: `P` Pleos SDK, `A` AAOS 표준 속성, `N` NaviHelper, `▶` 구독 콜백. Pleos 연속 속성 리스너는 값이 같아도 약 4초마다 다시 온다.

## 출처

- Pleos Vehicle SDK 2.3.2·NaviHelper 3.0.5(navi-data 3.0.4) javap, 에뮬레이터 `dumpsys car_service`·`dumpsys package permission`
- 오피넷 오픈 API: https://www.opinet.co.kr/user/custapi/openApiInfo.do , 반경 검색 https://www.opinet.co.kr/user/custapi/openApiInfoDtl.do?apiId=3 , 이용 안내 https://www.opinet.co.kr/user/custapi/openApiIntro.do
- 호출 제한(2차): https://github.com/eigger/hass-opinet , https://glama.ai/mcp/servers/KimJintak/opinet-mcp
- 공공데이터포털 전국 주유소 평균가격: https://www.data.go.kr/data/15150932/openapi.do
- 한국환경공단 전기자동차 충전소 정보: https://www.data.go.kr/data/15076352/openapi.do
- 미국 에너지부: https://www.fueleconomy.gov/feg/driveHabits.jsp
- AAA 저온 주행거리(2019): https://www.colorado.aaa.com/node/11006

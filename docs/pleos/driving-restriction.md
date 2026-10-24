# 주행 규제

출처
- 주행 규제 개발 가이드: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- 지역별 주행 규제 정책: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- 주행 안전을 고려한 설계: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 앱 카테고리: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category
- AAOS: https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager

## 원칙

- 주행/정차 판단은 앱이 속도·기어로 직접 하지 않는다. 플랫폼이 주는 `CarUxRestrictions`만 따른다. 플랫폼은 속도·기어·브레이크 같은 하드웨어 신호와 지역 규정을 반영해서 이 값을 내려준다.
- AAOS 표준 `CarUxRestrictionsManager.registerListener()` → `onUxRestrictionsChanged(CarUxRestrictions)`로 받는다.
- 판단 방식: `isRequiresDistractionOptimization()`가 true면 규제 중이다. 이때 `getActiveRestrictions()` 비트마스크로 기능별 제한을 확인한다.

```kotlin
// 공식 샘플(동영상 제한)과 같은 패턴
if (r.isRequiresDistractionOptimization &&
    (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0) {
    stopVideo()
}
```

## DO(Distraction Optimization) 선언

| 상황 | DO 미선언 앱 |
| --- | --- |
| Parking | 사용 가능 |
| Drive | **사용 불가** |

- 기획의 "주행 모드 한 줄 알림"을 보여주려면 DO를 선언해야 한다. DO를 선언한 앱도 규제 중에는 `ActiveRestrictions`를 보고 기능 제한을 직접 적용해야 한다.
- ✅ 에뮬레이터 확인(2026-10-24, `spikes/vehicle-probe`): AAOS 표준대로 Activity에 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 넣으면 된다.
  - DO 미선언 빌드: 기어 D로 바꾸는 즉시 앱 영역이 "주행 중에는 사용할 수 없습니다" 화면으로 덮였다. 이때 앱에는 `onPause`가 오지 않았다. 앱은 자기가 가려졌다는 걸 모른다.
  - DO 선언 빌드: 같은 규제 상태(`DO필요=true`)에서도 앱 화면이 그대로 보였다.
- 아직 모르는 것: 3rd party 앱의 DO 선언이 앱마켓 심사에서 허용되는지. 설명회 질문 후보다.
- 에뮬레이터 규제 값: P → D 전환 즉시 `isRequiresDistractionOptimization=true`, `activeRestrictions=80`(`NO_VIDEO` 16 + `NO_SETUP` 64). 아래 표의 "그외 지역"과 같다. 키보드·검색은 허용된다.

## 기능별 제한 플래그 (그외 지역 = 한국으로 추정, Driving 기준)

| 기능 | 플래그 | 그외 지역 Driving | 북미 Driving |
| --- | --- | --- | --- |
| 키보드 | `NO_KEYBOARD`(8) | ✅ | ⛔ |
| 다이얼패드 | `NO_DIALPAD`(1) | ✅ | ⛔ |
| 통합 검색 | `NO_KEYBOARD`(8) + `NO_FILTERING`(2) | ✅ | 음성만 |
| 미디어 앱 로그인·설정·검색 | `NO_SETUP`(64) + 8 + 2 | ✅ | ⛔ |
| 차량 설정(디지털키·도어·시트) | `NO_SETUP`(64) | ⛔ | ⛔ |
| 동영상 | `NO_VIDEO`(16) | ⛔ | ⛔ |
| 내비 검색 | 8 + 2 | ✅ | ⛔ |
| QR 로그인 | `NO_SETUP`(64) | ⛔ | ⛔ |
| 대용량 텍스트 | `LIMIT_CONTENT`(32) + `LIMIT_STRING_LENGTH`(4) | ⛔ | ⛔ |

Parking에서는 모두 허용된다. 확인 필요: 한국이 "그외 지역"에 해당하는지는 문서에 명시되어 있지 않다.

## 이 앱의 주행 모드 설계에 반영할 것

- 규제 중에는 지도 렌더링과 애니메이션을 멈추고 큰 글자 한 줄만 보여준다. 문서는 움직이는 시각 요소, 자동 애니메이션, 반복 화면 전환을 최소화하라고 한다.
- `LIMIT_STRING_LENGTH`/`LIMIT_CONTENT`가 켜지면 알림 문구도 짧게 줄인다. 이 비트도 처리 대상이다.
- 주행 중 설정 화면 진입(`NO_SETUP`)을 막는다.
- 큰 터치 영역, 높은 대비, 텍스트 최소화(Pleos Connect UI Design Guide).

## 앱 카테고리

Pleos 공식 카테고리: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication.

- ⚠️ **"차량 관리"라는 카테고리는 목록에 없다.** 브리프는 이 앱을 "차량 관리"로 분류했는데, 이건 현대차 보도자료 표현으로 보인다.
- 주행 중 기능이 제한적으로 허용될 수 있는 카테고리는 Music/Audio, Maps/Navigation, Mobility Services다.
  - Maps/Navigation: 실시간 교통 정보, 주변 교통 상황 안내, 간결한 지도
  - Mobility Services: "주행과 밀접한 관련이 있는 차량 관리 서비스", 현재 위치 기반 즉각 정보
- 이 앱은 **Mobility Services**(또는 Maps/Navigation)로 등록하는 것이 주행 중 알림과 가장 잘 맞는다. Playground 프로젝트를 만들 때 카테고리 선택에 반영한다.

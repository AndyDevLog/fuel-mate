# Pleos 문서 정리

Pleos 공식 문서(https://document.pleos.ai)를 읽고 이 프로젝트에 필요한 부분만 요약한 노트다. 원문을 옮긴 것이 아니라 정리한 것이므로, 코드를 쓰기 전에 애매하면 각 항목의 출처 링크를 다시 연다.

- 확인일: 2026-10-24 (이후 항목별 확인일은 각 파일에)
- 기준 버전: Vehicle SDK 2.3.2 (Vehicle Service 2.3.21), NaviHelper SDK 3.0.5
- 이전 주제(Road Sensor) 때 확인한 내용이 섞여 있다. 본문에 나오는 `spikes/vehicle-probe`, `spikes/gleo-probe`, `spikes/input-probe`와 도로 지도·앞길 알림 이야기는 그 시절 기록이다(코드는 로컬 태그 `archive/road-sensor`, 시험 앱은 저장소 밖 백업). 연료·배터리·공조·내비 경유지 확인은 [../pivot-fuel.md](../pivot-fuel.md)와 `spikes/fuel-probe`

## 규칙

- 여기 적힌 API만 사용한다. 여기 없는 API는 "확인 필요"로 표시하고 먼저 문서를 확인한다.
- 문서끼리 내용이 어긋나는 곳은 각 파일에 ⚠️로 표시했다.
- 문서로 답이 안 나오는 항목은 [open-questions.md](open-questions.md)에 모은다.

## 파일

| 파일 | 내용 |
| --- | --- |
| [vehicle-sdk.md](vehicle-sdk.md) | 의존성, 생명주기, 퍼미션, 오류 코드, Wiper·Light·Brake·Safety(경고등) API와 리스너 |
| [navihelper-sdk.md](navihelper-sdk.md) | 의존성, 퍼미션, 초기화, 이벤트 리스너, 위치·경로 데이터 모델 |
| [gleo-interface.md](gleo-interface.md) | Gleo AI 음성 연동: App Schema, Gleo Action, Callback, 에뮬레이터 결과 |
| [driving-restriction.md](driving-restriction.md) | CarUxRestrictions 연동, DO 선언, 지역별 규제, 앱 카테고리 |
| [ui-design-guide.md](ui-design-guide.md) | UI Design Guide 원칙·수치 기준(대비, 글자 높이, 레이아웃, 주간·야간), 이 앱에서 재는 법 |
| [open-questions.md](open-questions.md) | 확인 필요 항목과 기획에 미치는 영향 |

## 공통 Maven 저장소

`settings.gradle(.kts)`의 `dependencyResolutionManagement.repositories`에 추가한다.

```kotlin
maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
```

# Pleos Connect UI Design Guide (정리)

화면을 만들거나 점검할 때 쓰는 기준. 확인일 2026-10-27.

출처
- 소개(원칙·인터페이스 기준): https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro
- Typography: https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography
- Display Layout: https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout
- Colors: https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors
- Basic Button: https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button
- 주행 안전을 고려한 설계: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline

## 원칙

- 사용성·안전성: 운전 중 시각·인지 부담을 줄인다. 한 손으로, 단순한 조작만. 시선을 빼앗는 동영상·애니메이션·자동 스크롤을 쓰지 않는다
- 일관성: 아이콘·용어·상호작용을 기본 탑재 UI와 맞춘다
- 즉시성: 1~2초 안에 파악할 핵심 정보만. 입력 응답 0.25초 이하, 늦으면 응답 중 상태 표시
- 자주 쓰는 기능은 첫 화면·위쪽에. 드롭다운·다단계 메뉴를 남용하지 않는다

## 수치 기준

| 항목 | 기준 |
| --- | --- |
| 대비 | 아이콘·텍스트 4.5:1 이상 |
| 글자 높이 | 주요 정보 5mm 이상, 보조 3mm 이상 |
| 최소 글자 크기 | 12sp 이상 권장(실제 픽셀 높이 18px 이상), 줄 높이 1.5배 |
| 문장 | 한 줄 영문 80자 이하, 3줄 이하 |
| 글꼴 | 시스템 UI 글꼴은 Asta Sans 하나(한국어·영어). 캐릭터가 강한 글꼴은 피한다 |

- 버튼 크기·색상 값은 그림(SVG·PNG)으로만 있어 수치를 읽지 못했다. 터치 영역 기준 페이지는 찾지 못했다
- 버튼 컴포넌트: Root·Label·Icon Prefix/Suffix, 상태는 Pressed·Disabled·Loading. 세그먼트 메뉴(segmented menu) 컴포넌트가 있다

## 레이아웃

- 디스플레이: 17인치, 14.6인치, 12.9인치(Connect-L). 유형: Connect-S, Connect-W(가로로 긴 고급 차종), Connect-W-Extended, Connect-WF
- 앱 창에는 앱 1개 또는 2개가 뜬다(분할 화면)
- 모든 앱은 가로·세로 두 레이아웃에 맞게 설계해야 한다(한 방향 전용 게임 등 예외)
- 상태 표시줄, GNB, 공조 패널, 키보드는 앱보다 위 레이어라 앱을 잠시 가릴 수 있다

## 그 밖에

- 주간·야간 모드: 조명 환경에 따라 밝기·대비를 자동 조정한다
- 아이콘은 텍스트 라벨과 함께 쓴다
- 알림은 필요할 때만 간결하게, 광고 금지

## 이 앱에서 잴 때

- 에뮬레이터 화면 밀도 160dpi: 네이티브 1sp = 1px, 1mm ≈ 6.3px
- **앱 WebView의 devicePixelRatio는 1.375**다: CSS 1px이 화면 1.375px. WebView 글자 크기는 CSS px × 1.375로 환산한다
- 실제 차량 디스플레이의 해상도·크기별 밀도는 모른다(확인 필요). 위 mm 환산은 에뮬레이터 기준이다

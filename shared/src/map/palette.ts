/**
 * 지도 색. 차 화면은 어두운 배경이라 밝기 차이로 읽히게 한다.
 * 비 = 파란 채움(강도가 셀수록 진하게), 어두움 = 노란 테두리(라이트를 켠 차), 급정거 = 붉은 점.
 */
import type { AlertKind, RainLevel } from "../render-model.ts";

export const palette = {
  background: 0x0b0f14,
  /** 데이터는 있고 이상은 없는 격자. "비가 안 온다"와 "데이터가 없다"를 구분한다. */
  clear: 0x24303d,
  rain: { 1: 0x8fd3ff, 2: 0x3b9dff, 3: 0x2f5bff, 9: 0x5aa0d0 } as Record<Exclude<RainLevel, 0>, number>,
  dark: 0xf5c84c,
  brake: 0xff4d5e,
  alert: 0xffffff,
  ahead: 0xffffff,
  route: 0x9be7c4,
  car: 0x4de1ff,
  ring: 0xffffff,
} as const;

/** DOM 쪽(알림 목록·범례)에서 쓰는 CSS 색. */
export function cssColor(c: number): string {
  return `#${c.toString(16).padStart(6, "0")}`;
}

export function alertColor(kind: AlertKind): number {
  switch (kind) {
    case "RAIN": return palette.rain[2];
    case "DARK": return palette.dark;
    case "HARD_BRAKE": return palette.brake;
  }
}

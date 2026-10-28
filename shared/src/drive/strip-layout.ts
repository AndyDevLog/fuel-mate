/**
 * 주행 화면 앞길 띠의 배치 계산(순수 함수, test/drive.test.ts).
 *
 * 띠는 고정하고 내 차만 움직인다. 띠가 보여 주는 길은 "기준점(anchor) 뒤 BEHIND_M ~ 앞 AHEAD_M"이다.
 * 내 차가 기준점에서 REANCHOR_M만큼 가면 기준점을 내 차 위치로 옮긴다(렌더러는 띠와 차를 함께 미끄러뜨린다).
 * 위치가 튀면(epoch 변경) 바로 새로 맞춘다.
 */
import type { AlertKind, DriveItem, DriveView, RainLevel } from "../render-model.ts";

export const BEHIND_M = 150;
export const AHEAD_M = 3_000;
/** 내 차가 띠의 이 지점에 닿으면 띠를 다시 맞춘다(앞으로 1km는 늘 보이게) */
export const REANCHOR_M = 2_000;
/**
 * 구간 경계 떨림 무시 폭. 앞길을 30m 간격으로 훑어 구간을 만들어서, 같은 구간도 매초 경계가 ±30m 흔들린다.
 * 이보다 작은 변화는 이전 값을 그대로 쓴다.
 */
export const JITTER_M = 60;

export interface Anchor {
  epoch: number;
  /** 띠의 0m(내 차가 출발하는 자리)에 해당하는 거리계 값 */
  anchorM: number;
}

export type AnchorChange = "keep" | "slide" | "reset";

/** 띠 위에 놓인 구간. 거리는 거리계 기준 절대값(m) */
export interface Placed {
  fromAbs: number;
  toAbs: number;
  kind: AlertKind;
  rainLevel: RainLevel;
  label: string;
}

/** 새 모델에서 띠를 그대로 둘지(keep), 미끄러뜨려 다시 맞출지(slide), 바로 새로 맞출지(reset). */
export function nextAnchor(prev: Anchor | null, d: DriveView): { anchor: Anchor; change: AnchorChange } {
  const fresh = { epoch: d.epoch, anchorM: d.odometerM };
  if (prev === null || prev.epoch !== d.epoch) return { anchor: fresh, change: "reset" };
  const rel = d.odometerM - prev.anchorM;
  if (rel < -BEHIND_M) return { anchor: fresh, change: "reset" };
  if (rel >= REANCHOR_M) return { anchor: fresh, change: "slide" };
  return { anchor: prev, change: "keep" };
}

/** 거리계 값 abs가 띠의 어디쯤인가(0 = 왼쪽 끝, 1 = 오른쪽 끝, 범위 밖은 잘라 낸다). */
export function frac(anchorM: number, abs: number): number {
  const t = (abs - (anchorM - BEHIND_M)) / (BEHIND_M + AHEAD_M);
  return Math.min(1, Math.max(0, t));
}

/** 내 차 기준 구간(DriveItem)을 거리계 기준으로 바꾸고, 이전 배치와 거의 같으면 이전 경계를 쓴다. */
export function place(d: DriveView, prev: Placed[]): Placed[] {
  return d.items.map((it: DriveItem) => {
    const next = { fromAbs: d.odometerM + it.fromM, toAbs: d.odometerM + it.toM, kind: it.kind, rainLevel: it.rainLevel, label: it.label };
    // 같은 종류이고 겹치는 이전 구간이 있으면 같은 구간으로 본다
    const same = prev.find((p) => p.kind === next.kind && p.fromAbs < next.toAbs && next.fromAbs < p.toAbs);
    if (same === undefined) return next;
    return {
      ...next,
      fromAbs: Math.abs(same.fromAbs - next.fromAbs) < JITTER_M ? same.fromAbs : next.fromAbs,
      toAbs: Math.abs(same.toAbs - next.toAbs) < JITTER_M ? same.toAbs : next.toAbs,
    };
  });
}

/**
 * 0→1 에르미트 곡선. m0·m1은 시작·끝 기울기(1 = 그 끝에서 구간 평균 속도로 달림).
 * 가속 중(m0 < m1)이면 처음엔 느리고 점점 빨라지고, 감속 중이면 반대다. 둘 다 1이면 직선(선형).
 * 되감김(중간에 뒤로 갔다 옴)이 없게, 기울기를 0 이상·반지름 3 안쪽으로 자른다(Fritsch–Carlson 단조 조건).
 */
export function hermiteEase(m0: number, m1: number): (t: number) => number {
  let a = Math.max(0, m0);
  let b = Math.max(0, m1);
  const r = Math.hypot(a, b);
  if (r > 3) { a = (a * 3) / r; b = (b * 3) / r; }
  return (t) => {
    const t2 = t * t;
    const t3 = t2 * t;
    return (t3 - 2 * t2 + t) * a + (-2 * t3 + 3 * t2) + (t3 - t2) * b;
  };
}

/**
 * 한 구간(dist만큼을 dur초에)의 ease. v0·v1은 구간 시작·끝의 속도(dist와 같은 단위/초).
 * 한 구간의 끝 속도를 다음 구간의 시작 속도로 넘기면 경계에서 속도가 끊기지 않는다.
 * 속도를 모르거나 거의 안 움직이면 선형("none").
 */
export function segmentEase(v0: number | null, v1: number | null, dist: number, dur: number): "none" | ((t: number) => number) {
  if (v0 === null || v1 === null || dist <= 0.5 || dur <= 0) return "none";
  return hermiteEase((v0 * dur) / dist, (v1 * dur) / dist);
}

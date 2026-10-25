/**
 * 궤적 위 도로 상태 구간(비·어두움·급정거)과, 가상 차가 분마다 올릴 관측값.
 * 부작용이 없어서 테스트로 확인한다. 업로드는 road-zones.ts가 한다.
 */
import { cellsAlong, direction, geohash, pointAt, type Point } from "./geo.ts";
import type { CellId, Observation, RainLevel, UnixMinute } from "../../server/src/protocol.ts";

/** 비율 구간 [from, to] (0 = 궤적 시작, 1 = 끝). */
export type Range = [number, number];

export interface ZoneSpec {
  rain: Range | null;
  rainLevel: RainLevel;
  /** 하향등을 켠 구간(터널, 가로등 없는 길 같은 곳). */
  dark: Range | null;
  /** 급정거가 잦은 지점. */
  brakeAt: number | null;
}

export interface ZonePlan {
  /** 가상 차가 지나는 궤적 전체 격자. 모든 격자에 라이트 켬/끔을 보내서 k(=3)대를 넘긴다. */
  all: CellId[];
  rain: Set<CellId>;
  dark: Set<CellId>;
  brake: { cell: CellId; dir: number } | null;
}

/**
 * 급정거는 사건이라 서버가 15분 창 안의 횟수를 센다. 4분마다 한 번이면 창 안에 늘 3~4회
 * (앱 알림 기준 AheadFilter.MIN_HARD_BRAKES = 2 이상, 지도 강도 최대 RenderModel.BRAKE_FULL = 5 미만).
 * 그래서 내 차 급정거 하나가 더해지면 지도에서 바로 진해진다.
 */
export const BRAKE_EVERY_MIN = 4;

/** 서버 창(15분)보다 조금 짧게 되돌아가서, 시작하자마자 급정거 지점이 보이게 한다. */
export const BRAKE_BACKFILL_MIN = 13;

export function parseRange(s: string): Range | null {
  if (s === "off") return null;
  const m = /^([\d.]+)-([\d.]+)$/.exec(s);
  if (!m) throw new Error(`구간은 "0.4-0.9" 또는 "off": ${s}`);
  const r: Range = [Number(m[1]), Number(m[2])];
  if (!(r[0] >= 0 && r[0] < r[1] && r[1] <= 1)) throw new Error(`구간은 0 ≤ from < to ≤ 1: ${s}`);
  return r;
}

export function parsePoint(s: string): number | null {
  if (s === "off") return null;
  const x = Number(s);
  if (!(x >= 0 && x <= 1)) throw new Error(`지점은 0~1 또는 "off": ${s}`);
  return x;
}

export function planZones(points: Point[], spec: ZoneSpec): ZonePlan {
  const at = spec.brakeAt === null ? null : pointAt(points, spec.brakeAt);
  return {
    all: cellsAlong(points, 0, 1),
    rain: new Set(spec.rain ? cellsAlong(points, ...spec.rain) : []),
    dark: new Set(spec.dark ? cellsAlong(points, ...spec.dark) : []),
    brake: at && { cell: geohash(at.point.lat, at.point.lon), dir: direction(at.headingDeg) },
  };
}

/** (after, until] 사이에 급정거가 나는 분과, 그때 급정거하는 차(돌아가며). */
export function brakeMinutes(after: UnixMinute, until: UnixMinute, cars: number): { t: UnixMinute; car: number }[] {
  const out: { t: UnixMinute; car: number }[] = [];
  for (let t = after + 1; t <= until; t++) {
    if (t % BRAKE_EVERY_MIN === 0) out.push({ t, car: (t / BRAKE_EVERY_MIN) % cars });
  }
  return out;
}

/**
 * 차 한 대가 t분에 올릴 관측값. 급정거는 brakeMinutes가 이 차에 준 분만 넣는다.
 * 실차처럼 격자마다 와이퍼·라이트 값을 꺼진 것까지 보낸다. 켠 것만 보내면 비율의 분모가 켠 차뿐이라,
 * 구간 밖에서 내 차 혼자 와이퍼를 켜도 100%가 되어 지도에 드러난다.
 */
export function observationsOf(
  plan: ZonePlan, level: RainLevel, t: UnixMinute, brakesAt: UnixMinute[],
): Observation[] {
  const obs: Observation[] = [];
  for (const cell of plan.all) {
    obs.push({ cell, t, kind: "rain", level: plan.rain.has(cell) ? level : 0 });
    obs.push({ cell, t, kind: "dark", on: plan.dark.has(cell) });
  }
  if (plan.brake) for (const bt of brakesAt) obs.push({ cell: plan.brake.cell, t: bt, kind: "brake", dir: plan.brake.dir });
  return obs;
}

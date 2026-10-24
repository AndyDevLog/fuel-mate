/**
 * 가상 차 무리. 각 차는 영역 안에서 직선에 가까운 길(약 6km)을 달린 것으로 치고,
 * 지나간 격자마다 "와이퍼 켬/끔"을 올린다. 비의 세기는 강수 스냅숏을 보간한 값이다.
 * 같은 차·같은 격자는 매분 같은 값을 내게 해서(시드 고정) 와이퍼가 깜빡이지 않는다.
 */
import { geohash } from "../geo.ts";
import type { Observation, RainLevel } from "../../../server/src/protocol.ts";
import { levelOf, type Bounds } from "./snapshot.ts";

export interface VirtualCar {
  tid: string;
  /** 지나간 격자와 그 격자에서 처음 지난 점 */
  cells: { cell: string; lat: number; lon: number }[];
}

const STEP_M = 30;
const M_PER_DEG_LAT = 111_320;

/** 결정적 난수(mulberry32). 같은 시드면 같은 무리가 나온다. */
export function random(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296;
  };
}

export function makeFleet(b: Bounds, count: number, seed = 1, pathM = 6_000): VirtualCar[] {
  const rand = random(seed);
  const mPerDegLon = M_PER_DEG_LAT * Math.cos(((b.south + b.north) / 2) * (Math.PI / 180));
  const cars: VirtualCar[] = [];
  for (let i = 0; i < count; i++) {
    let lat = b.south + rand() * (b.north - b.south);
    let lon = b.west + rand() * (b.east - b.west);
    let heading = rand() * 2 * Math.PI;
    const seen = new Map<string, { cell: string; lat: number; lon: number }>();
    for (let d = 0; d <= pathM; d += STEP_M) {
      if (lat < b.south || lat > b.north || lon < b.west || lon > b.east) break;
      const cell = geohash(lat, lon);
      if (!seen.has(cell)) seen.set(cell, { cell, lat, lon });
      heading += (rand() - 0.5) * 0.08; // 길은 조금씩 휜다
      lat += (Math.cos(heading) * STEP_M) / M_PER_DEG_LAT;
      lon += (Math.sin(heading) * STEP_M) / mPerDegLon;
    }
    const tid = `sim-${Math.floor(rand() * 0xffffffff).toString(16).padStart(8, "0")}`;
    if (seen.size > 0) cars.push({ tid, cells: [...seen.values()].slice(0, 500) });
  }
  return cars;
}

/** 비 세기별로 와이퍼를 켜는 차의 비율. 약한 비에는 안 켜는 차도 있다. */
const WIPER_ON = { 1: 0.75, 2: 0.9, 3: 0.97 } as const;

/** 이 차가 지금 올릴 관측값. rain(lat, lon)은 1시간 강수량(mm). */
export function observationsOf(car: VirtualCar, rain: (lat: number, lon: number) => number, t: number): Observation[] {
  return car.cells.map(({ cell, lat, lon }) => {
    const base = levelOf(rain(lat, lon));
    const r = random(hash(`${car.tid}:${cell}`));
    let level: RainLevel = 0;
    if (base !== 0 && r() < WIPER_ON[base]) {
      const jitter = r();
      level = Math.min(3, Math.max(1, base + (jitter < 0.1 ? -1 : jitter > 0.9 ? 1 : 0))) as RainLevel;
    }
    return { cell, t, kind: "rain", level };
  });
}

function hash(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
}

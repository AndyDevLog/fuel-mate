/**
 * 강수 스냅숏: 한 발표 시각의 5km 격자별 1시간 강수량. 녹화해 두면 날씨와 상관없이 같은 비를 다시 재생할 수 있다
 * (시연 날 비가 안 와도 된다). 파일은 simulator/data/에 둔다.
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { toGridFrac, toGrid } from "./grid.ts";
import type { GridRain } from "./ncst.ts";

export interface Bounds { south: number; west: number; north: number; east: number }

export interface RainSnapshot {
  v: 1;
  /** 자료 출처. 실제 기상청 자료면 "kma-ultra-srt-ncst", 테스트용 가짜 자료면 "synthetic" */
  source: "kma-ultra-srt-ncst" | "synthetic";
  /** 발표 일시(KST) */
  baseDate: string;
  baseTime: string;
  bounds: Bounds;
  points: GridRain[];
}

/** 영역을 덮는 격자점(바깥 한 칸씩 여유를 둬서 가장자리도 보간되게). */
export function gridPointsFor(b: Bounds): { nx: number; ny: number }[] {
  const corners = [toGrid(b.south, b.west), toGrid(b.south, b.east), toGrid(b.north, b.west), toGrid(b.north, b.east)];
  const xs = corners.map((c) => c.nx), ys = corners.map((c) => c.ny);
  const out: { nx: number; ny: number }[] = [];
  for (let nx = Math.min(...xs) - 1; nx <= Math.max(...xs) + 1; nx++) {
    for (let ny = Math.min(...ys) - 1; ny <= Math.max(...ys) + 1; ny++) out.push({ nx, ny });
  }
  return out;
}

/** 격자점 사이를 쌍선형 보간한 1시간 강수량(mm)을 돌려주는 함수. 없는 격자점은 0으로 본다. */
export function rainSampler(s: RainSnapshot): (lat: number, lon: number) => number {
  const map = new Map(s.points.map((p) => [`${p.nx},${p.ny}`, p.rn1]));
  return (lat, lon) => interpolate(map, lat, lon);
}

function interpolate(map: Map<string, number>, lat: number, lon: number): number {
  const { x, y } = toGridFrac(lat, lon);
  const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0;
  const v = (nx: number, ny: number) => map.get(`${nx},${ny}`) ?? 0;
  return v(x0, y0) * (1 - fx) * (1 - fy) + v(x0 + 1, y0) * fx * (1 - fy) +
    v(x0, y0 + 1) * (1 - fx) * fy + v(x0 + 1, y0 + 1) * fx * fy;
}

/**
 * 1시간 강수량 → 와이퍼 강도(서버 프로토콜 level). 기상청 강수 강도 표현을 따른다:
 * 약한 비 3mm/h 미만, 보통 비 3~15mm/h, 강한 비 15mm/h 이상. 0.1mm 미만은 비 없음.
 */
export function levelOf(rn1: number): 0 | 1 | 2 | 3 {
  if (rn1 < 0.1) return 0;
  if (rn1 < 3) return 1;
  if (rn1 < 15) return 2;
  return 3;
}

export function loadSnapshot(path: string): RainSnapshot {
  const s = JSON.parse(readFileSync(path, "utf8")) as RainSnapshot;
  if (s.v !== 1 || !Array.isArray(s.points)) throw new Error(`강수 스냅숏 형식이 아님: ${path}`);
  return s;
}

export function saveSnapshot(path: string, s: RainSnapshot): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, `${JSON.stringify(s, null, 1)}\n`);
}

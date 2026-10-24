import type {
  CellId, CellState, Observation, RainLevel, Source, TileId, UnixMinute, UploadRequest, UploadResponse,
} from "./protocol.ts";

/**
 * 격자별 집계 (docs/architecture.md 6-1, 6-2).
 * - 최근 windowMin분 동안의 관측만 센다. 같은 차(임시 ID)는 격자마다 가장 최근 값 하나만 남긴다.
 * - 서로 다른 차가 k대 이상 지나간 격자만 배포한다(k-익명성). 차 한 대의 흔적은 지도에 나타나지 않는다.
 * - 시각은 밖에서 넣는다(nowMin). 테스트에서 시간을 마음대로 돌릴 수 있게 하려고.
 */
export interface AggregatorOptions {
  windowMin?: number;
  k?: number;
}

interface Seen { t: UnixMinute; src: Source }
interface RainReport { level: RainLevel; t: UnixMinute }
interface DarkReport { on: boolean; t: UnixMinute }
interface BrakeReport { tid: string; t: UnixMinute }

interface Bucket {
  seen: Map<string, Seen>;
  rain: Map<string, RainReport>;
  dark: Map<string, DarkReport>;
  brakes: BrakeReport[];
}

export class Aggregator {
  readonly windowMin: number;
  readonly k: number;

  private readonly buckets = new Map<CellId, Bucket>();
  private readonly published = new Map<CellId, CellState>();
  private readonly dirty = new Set<CellId>();

  constructor(options: AggregatorOptions = {}) {
    this.windowMin = options.windowMin ?? 15;
    this.k = options.k ?? 3;
  }

  ingest(req: UploadRequest, nowMin: UnixMinute): UploadResponse {
    let accepted = 0;
    for (const o of req.obs) {
      // 미래(1분 넘게)이거나 창 밖의 과거 관측은 버린다
      if (o.t > nowMin + 1 || o.t <= nowMin - this.windowMin) continue;
      this.add(req.tid, req.src, o);
      accepted++;
    }
    return { accepted, rejected: req.obs.length - accepted };
  }

  private add(tid: string, src: Source, o: Observation): void {
    const b = this.bucket(o.cell);
    const prev = b.seen.get(tid);
    if (!prev || prev.t <= o.t) b.seen.set(tid, { t: o.t, src });
    switch (o.kind) {
      case "rain": {
        const r = b.rain.get(tid);
        if (!r || r.t <= o.t) b.rain.set(tid, { level: o.level, t: o.t });
        break;
      }
      case "dark": {
        const d = b.dark.get(tid);
        if (!d || d.t <= o.t) b.dark.set(tid, { on: o.on, t: o.t });
        break;
      }
      case "brake":
        b.brakes.push({ tid, t: o.t });
        break;
    }
    this.dirty.add(o.cell);
  }

  private bucket(cell: CellId): Bucket {
    let b = this.buckets.get(cell);
    if (!b) {
      b = { seen: new Map(), rain: new Map(), dark: new Map(), brakes: [] };
      this.buckets.set(cell, b);
    }
    return b;
  }

  /** 창 밖으로 밀려난 관측을 지운다. 바뀐 격자는 다음 flush에서 다시 계산된다. */
  prune(nowMin: UnixMinute): void {
    const cutoff = nowMin - this.windowMin;
    for (const [cell, b] of this.buckets) {
      let changed = false;
      for (const map of [b.seen, b.rain, b.dark] as Map<string, { t: UnixMinute }>[]) {
        for (const [tid, v] of map) if (v.t <= cutoff) { map.delete(tid); changed = true; }
      }
      const before = b.brakes.length;
      b.brakes = b.brakes.filter((x) => x.t > cutoff);
      if (b.brakes.length !== before) changed = true;
      if (changed) this.dirty.add(cell);
      if (b.seen.size === 0) this.buckets.delete(cell);
    }
  }

  /** 격자 하나의 현재 집계. k대 미만이면 null(배포하지 않음). */
  stateOf(cell: CellId): CellState | null {
    const b = this.buckets.get(cell);
    if (!b || b.seen.size < this.k) return null;

    const rainOn = [...b.rain.values()].filter((r) => r.level !== 0);
    const darkOn = [...b.dark.values()].filter((d) => d.on).length;
    const sim = [...b.seen.values()].filter((s) => s.src === "sim").length;
    const updatedAt = Math.max(...[...b.seen.values()].map((s) => s.t));

    return {
      cell,
      n: b.seen.size,
      rain: b.rain.size === 0 ? 0 : round2(rainOn.length / b.rain.size),
      rainLevel: medianRainLevel(rainOn.map((r) => r.level)),
      dark: b.dark.size === 0 ? 0 : round2(darkOn / b.dark.size),
      brake: b.brakes.length,
      sim: round2(sim / b.seen.size),
      updatedAt,
    };
  }

  /** 지난 flush 이후 바뀐 격자. 새로 배포할 것(updated)과 내릴 것(expired)으로 나눈다. */
  flush(): { updated: CellState[]; expired: CellId[] } {
    const updated: CellState[] = [];
    const expired: CellId[] = [];
    for (const cell of this.dirty) {
      const next = this.stateOf(cell);
      const prev = this.published.get(cell);
      if (next) {
        if (!prev || !sameState(prev, next)) {
          this.published.set(cell, next);
          updated.push(next);
        }
      } else if (prev) {
        this.published.delete(cell);
        expired.push(cell);
      }
    }
    this.dirty.clear();
    return { updated, expired };
  }

  /** 구독을 시작할 때 보내는 현재 배포 상태. */
  snapshot(tiles: Iterable<TileId>): CellState[] {
    const set = new Set(tiles);
    return [...this.published.values()].filter((s) => set.has(tileOf(s.cell)));
  }

  get publishedCount(): number {
    return this.published.size;
  }

  /** 관측이 하나라도 있는 격자 수(배포 전 포함). 내용은 드러내지 않고 개수만. */
  get activeCellCount(): number {
    return this.buckets.size;
  }
}

export function tileOf(cell: CellId): TileId {
  return cell.slice(0, 5);
}

/** 켠 차들의 강도 중앙값. 세기를 아는 값(1~3)이 하나도 없고 미상(9)만 있으면 9. 켠 차가 없으면 0. */
export function medianRainLevel(levels: RainLevel[]): RainLevel {
  const known = levels.filter((l) => l >= 1 && l <= 3).sort((a, b) => a - b);
  if (known.length === 0) return levels.length > 0 ? 9 : 0;
  return known[Math.floor((known.length - 1) / 2)];
}

function round2(x: number): number {
  return Math.round(x * 100) / 100;
}

function sameState(a: CellState, b: CellState): boolean {
  return a.n === b.n && a.rain === b.rain && a.rainLevel === b.rainLevel &&
    a.dark === b.dark && a.brake === b.brake && a.sim === b.sim;
}

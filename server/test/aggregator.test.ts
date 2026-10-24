import { describe, expect, it } from "vitest";
import { Aggregator, medianRainLevel } from "../src/aggregator.ts";
import type { Observation, RainLevel, UploadRequest } from "../src/protocol.ts";

const CELL = "wydku4g";
const NOW = 29_000_000;

const upload = (tid: string, obs: Observation[], src: "car" | "sim" = "car"): UploadRequest =>
  ({ v: 1, tid, src, obs });
const rain = (level: RainLevel, t = NOW, cell = CELL): Observation => ({ cell, t, kind: "rain", level });

function withCars(agg: Aggregator, levels: RainLevel[], t = NOW) {
  levels.forEach((level, i) => agg.ingest(upload(`car-${i}`, [rain(level, t)]), NOW));
}

describe("k-익명성", () => {
  it("차 2대까지는 배포하지 않고, 3대째부터 배포한다", () => {
    const agg = new Aggregator();
    withCars(agg, [2, 2]);
    expect(agg.flush().updated).toEqual([]);

    agg.ingest(upload("car-3", [rain(0)]), NOW);
    const { updated } = agg.flush();
    expect(updated).toHaveLength(1);
    expect(updated[0]).toMatchObject({ cell: CELL, n: 3, rain: 0.67, rainLevel: 2 });
  });

  it("같은 차가 여러 번 보내도 한 대로 센다", () => {
    const agg = new Aggregator();
    for (let i = 0; i < 5; i++) agg.ingest(upload("same-car", [rain(3, NOW - i)]), NOW);
    expect(agg.stateOf(CELL)).toBeNull();
  });

  it("같은 차는 가장 최근 값만 반영한다 (와이퍼를 끄면 비 비율이 내려간다)", () => {
    const agg = new Aggregator();
    withCars(agg, [2, 2, 2], NOW - 2);
    agg.ingest(upload("car-0", [rain(0, NOW)]), NOW);
    expect(agg.stateOf(CELL)).toMatchObject({ n: 3, rain: 0.67 });
  });
});

describe("15분 창", () => {
  it("창 밖의 과거 관측과 1분 넘는 미래 관측은 받지 않는다", () => {
    const agg = new Aggregator();
    const r = agg.ingest(upload("a", [rain(1, NOW - 15), rain(1, NOW + 2), rain(1, NOW - 14)]), NOW);
    expect(r).toEqual({ accepted: 1, rejected: 2 });
  });

  it("시간이 지나 차가 빠지면 k 미만이 되어 expired로 내린다", () => {
    const agg = new Aggregator();
    withCars(agg, [2, 2, 2], NOW);
    expect(agg.flush().updated).toHaveLength(1);

    agg.prune(NOW + 15);
    expect(agg.flush()).toEqual({ updated: [], expired: [CELL] });
  });

  it("바뀐 게 없으면 다시 보내지 않는다", () => {
    const agg = new Aggregator();
    withCars(agg, [1, 1, 1]);
    agg.flush();
    agg.ingest(upload("car-0", [rain(1)]), NOW);
    expect(agg.flush().updated).toEqual([]);
  });
});

describe("집계 값", () => {
  it("어두움 비율, 급정거 수, 시뮬레이터 비율", () => {
    const agg = new Aggregator();
    agg.ingest(upload("a", [{ cell: CELL, t: NOW, kind: "dark", on: true }, { cell: CELL, t: NOW, kind: "brake", dir: 3 }]), NOW);
    agg.ingest(upload("b", [{ cell: CELL, t: NOW, kind: "dark", on: false }]), NOW);
    agg.ingest(upload("c", [{ cell: CELL, t: NOW, kind: "brake", dir: 3 }], "sim"), NOW);
    agg.ingest(upload("d", [{ cell: CELL, t: NOW, kind: "dark", on: true }], "sim"), NOW);
    expect(agg.stateOf(CELL)).toMatchObject({ n: 4, dark: 0.67, brake: 2, sim: 0.5, rain: 0, rainLevel: 0 });
  });

  it("강도 중앙값: 미상(AUTO)은 세기를 아는 값이 있으면 제외한다", () => {
    expect(medianRainLevel([])).toBe(0);
    expect(medianRainLevel([9, 9])).toBe(9);
    expect(medianRainLevel([1, 3, 9])).toBe(1);
    expect(medianRainLevel([1, 2, 3])).toBe(2);
    expect(medianRainLevel([3, 3, 1, 9])).toBe(3);
  });

  it("스냅샷은 요청한 타일(앞 5자리)의 배포 격자만 준다", () => {
    const agg = new Aggregator();
    withCars(agg, [2, 2, 2]);
    agg.flush();
    expect(agg.snapshot(["wydku"])).toHaveLength(1);
    expect(agg.snapshot(["wydks"])).toHaveLength(0);
  });
});

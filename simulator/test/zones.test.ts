import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { direction } from "../src/geo.ts";
import { BRAKE_BACKFILL_MIN, brakeMinutes, observationsOf, parsePoint, parseRange, planZones } from "../src/zones.ts";
import { Aggregator } from "../../server/src/aggregator.ts";

const track = JSON.parse(readFileSync(new URL("../../android/app/src/main/assets/tracks/sihung-pangyo-road.json", import.meta.url), "utf8")) as { points: [number, number][] };
const points = track.points.map(([lat, lon]) => ({ lat, lon }));
const spec = { rain: parseRange("0.4-0.9"), rainLevel: 2 as const, dark: parseRange("0.12-0.28"), brakeAt: parsePoint("0.33") };

test("구간·지점 인자", () => {
  assert.deepEqual(parseRange("0.12-0.28"), [0.12, 0.28]);
  assert.equal(parseRange("off"), null);
  assert.equal(parsePoint("off"), null);
  assert.throws(() => parseRange("0.9-0.4"));
  assert.throws(() => parsePoint("1.5"));
});

test("방향 8방위는 앱 Protocol.direction과 같다", () => {
  assert.deepEqual([0, 22.4, 22.5, 90, 180, 337.4, 337.5, -45].map(direction), [0, 0, 1, 2, 4, 7, 0, 7]);
});

test("기본 배치: 출발하면 어두움 → 급정거 → 비 순서, 서로 겹치지 않는다", () => {
  const plan = planZones(points, spec);
  const firstIndex = (cells: Set<string>) => plan.all.findIndex((c) => cells.has(c));
  const brakeIndex = plan.all.indexOf(plan.brake!.cell);
  assert.ok(firstIndex(plan.dark) < brakeIndex && brakeIndex < firstIndex(plan.rain));
  assert.ok(!plan.dark.has(plan.brake!.cell) && !plan.rain.has(plan.brake!.cell));
  assert.ok([...plan.dark].every((c) => !plan.rain.has(c)));
});

test("서버 집계: 시작하자마자 세 가지가 배포되고 급정거는 창 안에 2~4회로 유지된다", () => {
  const plan = planZones(points, spec);
  const agg = new Aggregator();
  const tids = ["sim-a", "sim-b", "sim-c", "sim-d", "sim-e"];
  const start = 29_000_000;
  let lastT = start - BRAKE_BACKFILL_MIN;
  for (let t = start; t < start + 40; t++) {
    agg.prune(t);
    const brakes = brakeMinutes(lastT, t, tids.length);
    lastT = t;
    tids.forEach((tid, i) => {
      const res = agg.ingest({ v: 1, tid, src: "sim", obs: observationsOf(plan, 2, t, brakes.filter((b) => b.car === i).map((b) => b.t)) }, t);
      assert.equal(res.rejected, 0);
    });
    const b = agg.stateOf(plan.brake!.cell)!;
    // 앱 알림 기준 2회 이상, 지도 강도 최대(5) 미만 → 내 차 급정거 하나가 더해지면 보인다
    assert.ok(b.brake >= 2 && b.brake <= 4, `${t - start}분: 급정거 ${b.brake}회`);
  }
  for (const cell of plan.dark) assert.equal(agg.stateOf(cell)!.dark, 1);
  for (const cell of plan.rain) {
    const s = agg.stateOf(cell)!;
    assert.equal(s.rain, 1);
    assert.equal(s.rainLevel, 2);
    assert.equal(s.dark, 0);
  }
  // 아무 일 없는 격자도 5대가 지나가서 배포된다(내 차 신호가 더해질 자리)
  assert.ok(plan.all.every((c) => agg.stateOf(c)?.n === 5));
  const quiet = plan.all.find((c) => !plan.rain.has(c) && !plan.dark.has(c) && c !== plan.brake!.cell)!;
  assert.deepEqual([agg.stateOf(quiet)!.rain, agg.stateOf(quiet)!.dark], [0, 0]);

  // 조용한 격자에서 내 차 혼자 와이퍼·라이트를 켜도 비율이 앱 기준(비 0.3, 어두움 0.5)에 못 미친다
  const t = start + 40;
  agg.ingest({ v: 1, tid: "car-me", src: "car", obs: [
    { cell: quiet, t, kind: "rain", level: 3 }, { cell: quiet, t, kind: "dark", on: true },
  ] }, t);
  const mine = agg.stateOf(quiet)!;
  assert.ok(mine.rain < 0.3 && mine.dark < 0.5, JSON.stringify(mine));

  // 급정거는 사건이라 내 차 한 번이 그대로 더해진다
  const before = agg.stateOf(plan.brake!.cell)!.brake;
  agg.ingest({ v: 1, tid: "car-me", src: "car", obs: [{ cell: plan.brake!.cell, t, kind: "brake", dir: plan.brake!.dir }] }, t);
  assert.equal(agg.stateOf(plan.brake!.cell)!.brake, before + 1);
});

test("비만(--dark off --brake off): 라이트는 모두 끔, 급정거 없음", () => {
  const plan = planZones(points, { ...spec, dark: null, brakeAt: null });
  assert.equal(plan.dark.size, 0);
  assert.equal(plan.brake, null);
  const obs = observationsOf(plan, 2, 1, [1]);
  assert.ok(obs.every((o) => o.kind !== "brake" && (o.kind !== "dark" || !o.on)));
});

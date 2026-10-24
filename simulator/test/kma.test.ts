import { test } from "node:test";
import assert from "node:assert/strict";
import { fromGrid, toGrid } from "../src/kma/grid.ts";
import { baseTimeOf, parseRn1, rn1FromResponse } from "../src/kma/ncst.ts";
import { gridPointsFor, levelOf, loadSnapshot, rainSampler, type RainSnapshot } from "../src/kma/snapshot.ts";
import { makeFleet, observationsOf } from "../src/kma/fleet.ts";
import { geohash } from "../src/geo.ts";

test("기상청 격자: 서울 종로 → 60,127, 격자점은 되돌리면 같은 격자", () => {
  assert.deepEqual(toGrid(37.5714, 126.9658), { nx: 60, ny: 127 });
  for (const [nx, ny] of [[60, 127], [62, 123], [98, 76]]) {
    const p = fromGrid(nx, ny);
    assert.deepEqual(toGrid(p.lat, p.lon), { nx, ny });
  }
});

test("실황 발표 시각: 45분 전이면 한 시간 전, 자정을 넘으면 전날", () => {
  // KST = UTC + 9
  assert.deepEqual(baseTimeOf(new Date("2026-11-03T05:50:00Z")), { baseDate: "20261103", baseTime: "1400" });
  assert.deepEqual(baseTimeOf(new Date("2026-11-03T05:30:00Z")), { baseDate: "20261103", baseTime: "1300" });
  assert.deepEqual(baseTimeOf(new Date("2026-11-02T15:10:00Z")), { baseDate: "20261102", baseTime: "2300" });
});

test("RN1 값 읽기와 오류 응답", () => {
  assert.equal(parseRn1("0"), 0);
  assert.equal(parseRn1("12.5"), 12.5);
  assert.equal(parseRn1("1mm 미만"), 0.5);
  assert.equal(parseRn1("강수없음"), 0);
  const ok = { response: { header: { resultCode: "00" }, body: { items: { item: [
    { category: "T1H", obsrValue: "11.2" }, { category: "RN1", obsrValue: "4" }] } } } };
  assert.equal(rn1FromResponse(ok), 4);
  assert.throws(() => rn1FromResponse({ response: { header: { resultCode: "03", resultMsg: "NO_DATA" } } }), /03 NO_DATA/);
});

test("강수량 → 와이퍼 강도(기상청 강수 강도 구간)", () => {
  assert.deepEqual([0, 0.05, 0.1, 2.9, 3, 14.9, 15, 40].map(levelOf), [0, 0, 1, 1, 2, 2, 3, 3]);
});

test("보간: 격자점에서는 그 값, 두 격자점 사이에서는 그 사이 값", () => {
  const a = fromGrid(62, 123), b = fromGrid(63, 123);
  const s: RainSnapshot = { v: 1, source: "synthetic", baseDate: "", baseTime: "", bounds: { south: 0, west: 0, north: 0, east: 0 },
    points: [{ nx: 62, ny: 123, rn1: 10 }, { nx: 63, ny: 123, rn1: 0 }] };
  const rain = rainSampler(s);
  assert.ok(Math.abs(rain(a.lat, a.lon) - 10) < 0.01);
  const mid = rain((a.lat + b.lat) / 2, (a.lon + b.lon) / 2);
  assert.ok(mid > 3 && mid < 7, `중간 ${mid}`);
});

test("영역을 덮는 격자점에 여유 한 칸이 있다", () => {
  const b = { south: 37.35, west: 127.035, north: 37.458, east: 127.17 };
  const pts = gridPointsFor(b);
  const c = toGrid(b.south, b.west);
  assert.ok(pts.some((p) => p.nx === c.nx - 1 && p.ny === c.ny - 1));
  assert.equal(new Set(pts.map((p) => `${p.nx},${p.ny}`)).size, pts.length);
});

test("가상 차 무리: 같은 시드면 같고, 같은 차·격자는 매분 같은 값을 올린다", () => {
  const snapshot = loadSnapshot(new URL("fixtures/synthetic-rain.json", import.meta.url).pathname);
  const rain = rainSampler(snapshot);
  const a = makeFleet(snapshot.bounds, 50, 7), b = makeFleet(snapshot.bounds, 50, 7);
  assert.deepEqual(a, b);
  const car = a[0];
  assert.ok(car.cells.length > 20, `격자 ${car.cells.length}개`);
  assert.ok(car.cells.every((c) => c.cell === geohash(c.lat, c.lon)));
  const now = observationsOf(car, rain, 100).map((o) => ({ ...o, t: 0 }));
  const later = observationsOf(car, rain, 101).map((o) => ({ ...o, t: 0 }));
  assert.deepEqual(now, later);
});

test("비 띠 안을 지난 차들은 대부분 와이퍼를 켠다", () => {
  const snapshot = loadSnapshot(new URL("fixtures/synthetic-rain.json", import.meta.url).pathname);
  const rain = rainSampler(snapshot);
  const obs = makeFleet(snapshot.bounds, 300, 3).flatMap((c) => observationsOf(c, rain, 0).map((o) => ({ o, c })));
  const wet = obs.filter(({ o }) => o.kind === "rain" && levelOf(rain(...latLonOf(o.cell, obs))) >= 2);
  const on = wet.filter(({ o }) => o.kind === "rain" && o.level !== 0).length;
  assert.ok(wet.length > 100);
  assert.ok(on / wet.length > 0.85, `켠 비율 ${on / wet.length}`);
});

function latLonOf(cell: string, obs: { o: { cell: string }; c: { cells: { cell: string; lat: number; lon: number }[] } }[]): [number, number] {
  const hit = obs.find((x) => x.o.cell === cell)!.c.cells.find((c) => c.cell === cell)!;
  return [hit.lat, hit.lon];
}

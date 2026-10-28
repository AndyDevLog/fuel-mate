import { test } from "node:test";
import assert from "node:assert/strict";
import { AHEAD_M, BEHIND_M, REANCHOR_M, frac, hermiteEase, nextAnchor, place, segmentEase } from "../src/drive/strip-layout.ts";
import type { DriveView } from "../src/render-model.ts";

const view = (odometerM: number, epoch = 1, items: DriveView["items"] = []): DriveView => ({ odometerM, epoch, speedMps: null, items });

test("처음과 위치가 튀었을 때(epoch 변경)는 바로 새로 맞춘다", () => {
  assert.equal(nextAnchor(null, view(500)).change, "reset");
  const a = nextAnchor(null, view(500)).anchor;
  assert.equal(nextAnchor(a, view(520, 2)).change, "reset");
});

test("달리는 동안은 띠를 두고, 2km를 가면 미끄러뜨려 다시 맞춘다", () => {
  const a = nextAnchor(null, view(1_000)).anchor;
  const mid = nextAnchor(a, view(1_000 + REANCHOR_M - 1));
  assert.equal(mid.change, "keep");
  assert.equal(mid.anchor.anchorM, 1_000);
  const end = nextAnchor(a, view(1_000 + REANCHOR_M));
  assert.equal(end.change, "slide");
  assert.equal(end.anchor.anchorM, 1_000 + REANCHOR_M);
});

test("띠 위치: 기준점 뒤 150m가 왼쪽 끝, 앞 3km가 오른쪽 끝", () => {
  assert.equal(frac(1_000, 1_000 - BEHIND_M), 0);
  assert.equal(frac(1_000, 1_000 + AHEAD_M), 1);
  assert.equal(frac(1_000, 9_999), 1);
  assert.ok(Math.abs(frac(1_000, 1_000) - BEHIND_M / (BEHIND_M + AHEAD_M)) < 1e-9);
});

test("구간 경계가 60m 안쪽으로 흔들리면 이전 경계를 쓴다", () => {
  const rain = (fromM: number, toM: number) => ({ fromM, toM, kind: "RAIN" as const, rainLevel: 2 as const, label: "비(중)" });
  const first = place(view(1_000, 1, [rain(600, 1_500)]), []);
  assert.deepEqual([first[0].fromAbs, first[0].toAbs], [1_600, 2_500]);
  // 1초 뒤 차가 14m 가고, 경계는 샘플링 때문에 30m 흔들림
  const next = place(view(1_014, 1, [rain(556, 1_516)]), first);
  // 새 값은 1,570 / 2,530 이지만 둘 다 60m 안쪽이라 이전 경계를 쓴다
  assert.deepEqual([next[0].fromAbs, next[0].toAbs], [1_600, 2_500]);
  // 크게 바뀌면(구간이 늘어남) 새 경계를 쓴다
  const grown = place(view(1_014, 1, [rain(556, 1_986)]), first);
  assert.equal(grown[0].toAbs, 3_000);
});

test("에르미트 ease: 등속이면 직선, 가속이면 처음이 느리고 감속이면 처음이 빠르다", () => {
  const linear = hermiteEase(1, 1);
  for (const t of [0, 0.25, 0.5, 0.75, 1]) assert.ok(Math.abs(linear(t) - t) < 1e-9);
  const accel = hermiteEase(0.5, 1.5);
  const brake = hermiteEase(1.5, 0.5);
  assert.ok(accel(0.5) < 0.5 && brake(0.5) > 0.5);
  for (const f of [accel, brake]) { assert.equal(f(0), 0); assert.ok(Math.abs(f(1) - 1) < 1e-9); }
});

test("에르미트 ease는 급감속처럼 기울기가 커도 되감지 않는다", () => {
  for (const [m0, m1] of [[6, 0], [0, 6], [4, 4], [3, 0]]) {
    const f = hermiteEase(m0, m1);
    let prev = 0;
    for (let i = 1; i <= 100; i++) {
      const y = f(i / 100);
      assert.ok(y >= prev - 1e-9, `m0=${m0} m1=${m1} t=${i / 100}`);
      prev = y;
    }
  }
});

test("구간 ease: 속도를 모르거나 거의 안 움직이면 선형", () => {
  assert.equal(segmentEase(null, 10, 50, 1), "none");
  assert.equal(segmentEase(10, 10, 0.2, 1), "none");
  assert.equal(typeof segmentEase(10, 20, 15, 1), "function");
});

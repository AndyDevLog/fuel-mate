import { test } from "node:test";
import assert from "node:assert/strict";
import { slice } from "../src/map/route.ts";

const pts: [number, number][] = [[0, 0], [100, 0], [100, 100]];
const cum = [0, 100, 200];

test("꺾은선 구간 자르기: 꺾이는 점을 지나면 그 점을 넣는다", () => {
  assert.deepEqual(slice(pts, cum, 50, 150), [[50, 0], [100, 0], [100, 50]]);
});

test("한 선분 안이면 두 점, 길이를 넘으면 끝에서 자른다", () => {
  assert.deepEqual(slice(pts, cum, 10, 40), [[10, 0], [40, 0]]);
  assert.deepEqual(slice(pts, cum, 180, 500), [[100, 80], [100, 100]]);
});

test("빈 구간은 빈 배열", () => {
  assert.deepEqual(slice(pts, cum, 120, 120), []);
  assert.deepEqual(slice(pts, cum, 250, 300), []);
});

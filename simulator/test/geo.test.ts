import { test } from "node:test";
import assert from "node:assert/strict";
import { cellsAlong, distanceM, geohash } from "../src/geo.ts";

test("geohash 표준 예제 (앱 :core GeoTest와 같은 값)", () => {
  assert.equal(geohash(57.64911, 10.40744, 11), "u4pruydqqvj");
});

test("앱과 같은 격자: 에뮬레이터 기본 위치 → wydku4g", () => {
  assert.equal(geohash(37.4127212, 127.094681), "wydku4g");
});

test("시흥동 → 판교역 약 2.5km", () => {
  const d = distanceM({ lat: 37.4127212, lon: 127.094681 }, { lat: 37.39479166, lon: 127.11256944 });
  assert.ok(Math.abs(d - 2_530) < 100, `거리 ${d}`);
});

test("궤적 구간의 격자는 순서대로, 중복 없이", () => {
  const pts = [{ lat: 37.4127212, lon: 127.094681 }, { lat: 37.39479166, lon: 127.11256944 }];
  const all = cellsAlong(pts, 0, 1);
  const part = cellsAlong(pts, 0.4, 0.9);
  assert.equal(new Set(all).size, all.length);
  assert.ok(part.length > 5 && part.length < all.length);
  assert.ok(part.every((c) => all.includes(c)));
  assert.equal(all[0], "wydku4g");
});

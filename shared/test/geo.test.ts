import { test } from "node:test";
import assert from "node:assert/strict";
import { LocalProjection, geohashBounds, geohashCenter, geohashEncode } from "../src/geo.ts";
import { geohash as simulatorGeohash } from "../../simulator/src/geo.ts";

test("앱과 같은 격자: 에뮬레이터 기본 위치 → wydku4g", () => {
  assert.equal(geohashEncode(37.4127212, 127.094681), "wydku4g");
  assert.equal(geohashEncode(57.64911, 10.40744, 11), "u4pruydqqvj");
});

test("시뮬레이터와 같은 격자 ID", () => {
  for (let i = 0; i < 200; i++) {
    const lat = 37.3 + i * 0.0013, lon = 127.0 + i * 0.0021;
    assert.equal(geohashEncode(lat, lon), simulatorGeohash(lat, lon));
  }
});

test("풀어낸 범위 안에 원래 점이 있고, 중심을 다시 묶으면 같은 격자", () => {
  const b = geohashBounds("wydku4g");
  assert.ok(b.latLo <= 37.4127212 && 37.4127212 <= b.latHi);
  assert.ok(b.lonLo <= 127.094681 && 127.094681 <= b.lonHi);
  const c = geohashCenter("wydku4g");
  assert.equal(geohashEncode(c.lat, c.lon), "wydku4g");
  // 7자리 ≈ 150m
  const p = new LocalProjection(c);
  assert.ok(Math.abs(p.x(b.lonHi) - p.x(b.lonLo) - 121) < 5, "경도 폭");
  assert.ok(Math.abs(p.y(b.latLo) - p.y(b.latHi) - 153) < 5, "위도 폭");
});

test("지역 평면: 동쪽은 +x, 북쪽은 -y", () => {
  const p = new LocalProjection({ lat: 37.4, lon: 127.1 });
  assert.ok(p.x(127.11) > 0);
  assert.ok(p.y(37.41) < 0);
  assert.ok(Math.abs(p.y(37.41) + 1_113) < 2);
  assert.equal(p.isFar({ lat: 37.41, lon: 127.11 }), false);
  assert.equal(p.isFar({ lat: 37.8, lon: 127.1 }), true);
});

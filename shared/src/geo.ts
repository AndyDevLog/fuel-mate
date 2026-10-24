/** geohash 풀기와 지역 평면 좌표. 차량 앱 :core Geohash와 같은 계산이다. */

import type { LatLon } from "./render-model.ts";

const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export interface Bounds { latLo: number; latHi: number; lonLo: number; lonHi: number }

export function geohashBounds(hash: string): Bounds {
  let latLo = -90, latHi = 90, lonLo = -180, lonHi = 180;
  let even = true;
  for (const c of hash) {
    const idx = BASE32.indexOf(c);
    if (idx < 0) throw new Error(`geohash가 아닌 문자: ${c}`);
    for (let b = 4; b >= 0; b--) {
      const on = ((idx >> b) & 1) === 1;
      if (even) {
        const mid = (lonLo + lonHi) / 2;
        if (on) lonLo = mid; else lonHi = mid;
      } else {
        const mid = (latLo + latHi) / 2;
        if (on) latLo = mid; else latHi = mid;
      }
      even = !even;
    }
  }
  return { latLo, latHi, lonLo, lonHi };
}

export function geohashEncode(lat: number, lon: number, precision = 7): string {
  let latLo = -90, latHi = 90, lonLo = -180, lonHi = 180;
  let out = "", bit = 0, ch = 0, even = true;
  while (out.length < precision) {
    if (even) {
      const mid = (lonLo + lonHi) / 2;
      if (lon >= mid) { ch |= 1 << (4 - bit); lonLo = mid; } else lonHi = mid;
    } else {
      const mid = (latLo + latHi) / 2;
      if (lat >= mid) { ch |= 1 << (4 - bit); latLo = mid; } else latHi = mid;
    }
    even = !even;
    if (bit < 4) bit++; else { out += BASE32[ch]; bit = 0; ch = 0; }
  }
  return out;
}

export function geohashCenter(hash: string): LatLon {
  const b = geohashBounds(hash);
  return { lat: (b.latLo + b.latHi) / 2, lon: (b.lonLo + b.lonHi) / 2 };
}

const M_PER_DEG_LAT = 111_320;

/**
 * 기준점 둘레 수십 km를 평면(m)으로 펴는 등장방형 투영. x = 동쪽, y = 남쪽(화면 아래 방향).
 * 격자 크기(150m)와 화면 범위(수 km)에서는 오차가 화면 1px보다 작다.
 */
export class LocalProjection {
  readonly origin: LatLon;
  private readonly mPerDegLon: number;

  constructor(origin: LatLon) {
    this.origin = origin;
    this.mPerDegLon = M_PER_DEG_LAT * Math.cos((origin.lat * Math.PI) / 180);
  }

  x(lon: number): number { return (lon - this.origin.lon) * this.mPerDegLon; }
  y(lat: number): number { return (this.origin.lat - lat) * M_PER_DEG_LAT; }

  /** 기준점에서 너무 멀어지면(투영 오차) 새 기준점을 잡아야 한다. */
  isFar(p: LatLon, limitM = 30_000): boolean {
    return Math.hypot(this.x(p.lon), this.y(p.lat)) > limitM;
  }
}

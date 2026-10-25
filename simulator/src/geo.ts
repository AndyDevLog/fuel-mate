/** 차량 앱(:core Geohash, GeoMath)과 같은 계산. 격자 ID가 앱과 정확히 같아야 한다. */

export interface Point { lat: number; lon: number }

const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export function geohash(lat: number, lon: number, precision = 7): string {
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

export function distanceM(a: Point, b: Point): number {
  const r = 6_371_000, rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad, dLon = (b.lon - a.lon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

/** 진행 방위각(북=0°, 시계 방향). 앱 GeoMath.bearingDeg와 같은 식. */
export function bearingDeg(a: Point, b: Point): number {
  const rad = Math.PI / 180;
  const lat1 = a.lat * rad, lat2 = b.lat * rad, dLon = (b.lon - a.lon) * rad;
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return (Math.atan2(y, x) / rad + 360) % 360;
}

/** 8방위(0=북, 1=북동, …). 앱 Protocol.direction과 같은 식. */
export function direction(headingDeg: number): number {
  return Math.floor((((headingDeg % 360) + 360) % 360 + 22.5) / 45) % 8;
}

/** 궤적에서 앞에서부터 distance(m) 지점과 그 구간의 진행 방위각. */
function walker(points: Point[]): { total: number; at: (d: number) => { point: Point; headingDeg: number } } {
  const cum = [0];
  for (let i = 1; i < points.length; i++) cum.push(cum[i - 1] + distanceM(points[i - 1], points[i]));
  return {
    total: cum[cum.length - 1],
    at(d) {
      let i = 0;
      while (i < points.length - 2 && cum[i + 1] < d) i++;
      const seg = cum[i + 1] - cum[i];
      const t = seg === 0 ? 0 : (d - cum[i]) / seg;
      const a = points[i], b = points[i + 1];
      return { point: { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t }, headingDeg: bearingDeg(a, b) };
    },
  };
}

/** 궤적의 [from, to] 비율 구간을 stepM 간격으로 훑어서 지나는 격자들(순서 유지, 중복 제거). */
export function cellsAlong(points: Point[], from: number, to: number, stepM = 30): string[] {
  const w = walker(points);
  const cells = new Set<string>();
  for (let d = w.total * from; d <= w.total * to; d += stepM) {
    const { point } = w.at(d);
    cells.add(geohash(point.lat, point.lon));
  }
  return [...cells];
}

/** 궤적의 frac(0~1) 지점과 그곳의 진행 방위각. */
export function pointAt(points: Point[], frac: number): { point: Point; headingDeg: number } {
  const w = walker(points);
  return w.at(w.total * frac);
}

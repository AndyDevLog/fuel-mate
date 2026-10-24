/**
 * 기상청 단기예보 격자(5km, 람베르트 정각원추 투영) ↔ 위경도.
 * 기상청 단기예보 조회서비스 활용가이드의 변환식(격자 5km, 표준위도 30°·60°, 기준점 126°E·38°N = 격자 43,136).
 */

const RE = 6371.00877; // 지구 반경(km)
const GRID = 5.0;
const SLAT1 = 30.0, SLAT2 = 60.0, OLON = 126.0, OLAT = 38.0;
const XO = 43, YO = 136;

const D = Math.PI / 180;
const re = RE / GRID;
const slat1 = SLAT1 * D, slat2 = SLAT2 * D, olon = OLON * D, olat = OLAT * D;
const sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) /
  Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
const sf = (Math.tan(Math.PI * 0.25 + slat1 * 0.5) ** sn * Math.cos(slat1)) / sn;
const ro = (re * sf) / Math.tan(Math.PI * 0.25 + olat * 0.5) ** sn;

/** 위경도 → 격자 좌표(소수). 보간에 쓴다. 정수 격자는 [toGrid]. */
export function toGridFrac(lat: number, lon: number): { x: number; y: number } {
  const ra = (re * sf) / Math.tan(Math.PI * 0.25 + lat * D * 0.5) ** sn;
  let theta = lon * D - olon;
  if (theta > Math.PI) theta -= 2 * Math.PI;
  if (theta < -Math.PI) theta += 2 * Math.PI;
  theta *= sn;
  return { x: ra * Math.sin(theta) + XO, y: ro - ra * Math.cos(theta) + YO };
}

export function toGrid(lat: number, lon: number): { nx: number; ny: number } {
  const { x, y } = toGridFrac(lat, lon);
  return { nx: Math.floor(x + 0.5), ny: Math.floor(y + 0.5) };
}

/** 격자 → 위경도(격자점 위치). */
export function fromGrid(nx: number, ny: number): { lat: number; lon: number } {
  const xn = nx - XO, yn = ro - ny + YO;
  let ra = Math.sqrt(xn * xn + yn * yn);
  if (sn < 0) ra = -ra;
  const lat = 2 * Math.atan(((re * sf) / ra) ** (1 / sn)) - Math.PI * 0.5;
  const theta = Math.atan2(xn, yn);
  return { lat: lat / D, lon: (theta / sn + olon) / D };
}

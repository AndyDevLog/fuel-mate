/**
 * 비 구간 시뮬레이터.
 * 궤적의 일부 구간을 지나는 가상 차 N대가 "와이퍼 켬"을 1분마다 업로드한다(서버 15분 창에서 사라지지 않게).
 *
 *   npm run rain -- [--track ../android/app/src/main/assets/tracks/sample-sihung-pangyo.json]
 *                   [--from 0.4] [--to 0.9] [--cars 5] [--level 2] [--server http://127.0.0.1:8080] [--once]
 *
 * 업로드는 src="sim"으로 표시한다. 서버는 실차와 똑같이 집계하고, 관제 웹에서만 구분해 보여준다.
 */
import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { randomBytes } from "node:crypto";
import { cellsAlong, type Point } from "./geo.ts";
import type { UploadRequest } from "../../server/src/protocol.ts";

const DEFAULT_TRACK = new URL("../../android/app/src/main/assets/tracks/sample-sihung-pangyo.json", import.meta.url);

const { values: args } = parseArgs({
  options: {
    track: { type: "string" },
    from: { type: "string", default: "0.4" },
    to: { type: "string", default: "0.9" },
    cars: { type: "string", default: "5" },
    level: { type: "string", default: "2" },
    server: { type: "string", default: "http://127.0.0.1:8080" },
    once: { type: "boolean", default: false },
  },
});

const track = JSON.parse(readFileSync(args.track ?? DEFAULT_TRACK, "utf8")) as { name: string; points: [number, number][] };
const points: Point[] = track.points.map(([lat, lon]) => ({ lat, lon }));
const cells = cellsAlong(points, Number(args.from), Number(args.to));
const level = Number(args.level) as 1 | 2 | 3;
const cars = Array.from({ length: Number(args.cars) }, () => `sim-${randomBytes(4).toString("hex")}`);

console.log(`궤적: ${track.name}`);
console.log(`비 구간: ${args.from} ~ ${args.to} 지점, 격자 ${cells.length}개, 가상 차 ${cars.length}대, 강도 ${level}`);

async function uploadAll(): Promise<void> {
  const t = Math.floor(Date.now() / 60_000);
  let accepted = 0;
  for (const tid of cars) {
    const body: UploadRequest = { v: 1, tid, src: "sim", obs: cells.map((cell) => ({ cell, t, kind: "rain", level })) };
    const res = await fetch(`${args.server}/v1/observations`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    accepted += ((await res.json()) as { accepted: number }).accepted;
  }
  console.log(`${new Date().toLocaleTimeString()}  업로드 ${accepted}개`);
}

await uploadAll();
if (!args.once) setInterval(() => uploadAll().catch((e) => console.error("업로드 실패:", e.message)), 60_000);

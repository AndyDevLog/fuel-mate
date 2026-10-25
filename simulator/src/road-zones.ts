/**
 * 도로 상태 구간 시뮬레이터.
 * 궤적 전체를 지나는 가상 차 N대가 1분마다 업로드한다(서버 15분 창에서 사라지지 않게).
 * - 어두운 구간: 하향등 켬(나머지 격자는 끔)
 * - 급정거 지점: 4분마다 한 대씩 돌아가며 급정거(시작할 때 지난 13분 치를 채운다)
 * - 비 구간: 와이퍼 켬
 * 기본 배치는 데모 궤적(시흥동 → 판교역)을 출발하면 어두움 → 급정거 → 비 순서로 만나게 했다.
 *
 *   npm run zones -- [--track <궤적 JSON>] [--rain 0.4-0.9|off] [--level 2] [--dark 0.12-0.28|off]
 *                    [--brake 0.33|off] [--cars 5] [--server http://127.0.0.1:8080] [--once]
 *   npm run rain                                          비만(= --dark off --brake off)
 *
 * 업로드는 src="sim"으로 표시한다. 서버는 실차와 똑같이 집계하고, 관제 웹에서만 구분해 보여준다.
 */
import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { randomBytes } from "node:crypto";
import type { Point } from "./geo.ts";
import type { RainLevel, UploadRequest } from "../../server/src/protocol.ts";
import { BRAKE_BACKFILL_MIN, brakeMinutes, observationsOf, parsePoint, parseRange, planZones } from "./zones.ts";

const DEFAULT_TRACK = new URL("../../android/app/src/main/assets/tracks/sihung-pangyo-road.json", import.meta.url);

const { values: args } = parseArgs({
  options: {
    track: { type: "string" },
    rain: { type: "string", default: "0.4-0.9" },
    level: { type: "string", default: "2" },
    dark: { type: "string", default: "0.12-0.28" },
    brake: { type: "string", default: "0.33" },
    cars: { type: "string", default: "5" },
    server: { type: "string", default: "http://127.0.0.1:8080" },
    once: { type: "boolean", default: false },
  },
});

const track = JSON.parse(readFileSync(args.track ?? DEFAULT_TRACK, "utf8")) as { name: string; points: [number, number][] };
const points: Point[] = track.points.map(([lat, lon]) => ({ lat, lon }));
const level = Number(args.level) as RainLevel;
const spec = { rain: parseRange(args.rain), rainLevel: level, dark: parseRange(args.dark), brakeAt: parsePoint(args.brake) };
const plan = planZones(points, spec);
const cars = Array.from({ length: Number(args.cars) }, () => `sim-${randomBytes(4).toString("hex")}`);

console.log(`궤적: ${track.name}, 격자 ${plan.all.length}개, 가상 차 ${cars.length}대`);
console.log(`비: ${args.rain} (격자 ${plan.rain.size}개, 강도 ${level}) / 어두움: ${args.dark} (격자 ${plan.dark.size}개) / 급정거: ${args.brake}`);

let lastT = Math.floor(Date.now() / 60_000) - BRAKE_BACKFILL_MIN;

async function uploadAll(): Promise<void> {
  const t = Math.floor(Date.now() / 60_000);
  const brakes = brakeMinutes(lastT, t, cars.length);
  lastT = t;
  let accepted = 0;
  for (const [i, tid] of cars.entries()) {
    const mine = brakes.filter((b) => b.car === i).map((b) => b.t);
    const body: UploadRequest = { v: 1, tid, src: "sim", obs: observationsOf(plan, level, t, mine) };
    const res = await fetch(`${args.server}/v1/observations`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    accepted += ((await res.json()) as { accepted: number }).accepted;
  }
  const brakeNote = plan.brake && brakes.length > 0 ? `, 급정거 ${brakes.length}회` : "";
  console.log(`${new Date().toLocaleTimeString()}  업로드 ${accepted}개${brakeNote}`);
}

await uploadAll();
if (!args.once) setInterval(() => uploadAll().catch((e) => console.error("업로드 실패:", e.message)), 60_000);

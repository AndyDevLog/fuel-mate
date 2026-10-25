/**
 * 기상청 강수 기반 시뮬레이터. 가상 차 수천 대가 실제(또는 녹화한) 강수 분포대로 와이퍼 켬/끔을 1분마다 올린다.
 *
 *   npm run kma -- --snapshot data/kma-202611031500.json    녹화한 강수 재생(키 필요 없음, 시연용)
 *   npm run kma -- --live                                   지금 강수를 받아 쓰고, 발표 시각마다 data/에 녹화
 *   npm run kma -- --live --record-only                     녹화만(업로드 안 함). 비 오는 날 켜 두면 된다
 *   npm run kma -- --backfill 24                            지난 24시간(1~24)을 한 번에 녹화하고 끝낸다.
 *                                                           초단기실황은 최근 1일 자료만 주므로, 비 온 날 하루 안에 한 번 돌리면 된다
 *
 *   [--cars 2500] [--bounds south,west,north,east] [--seed 1] [--server http://127.0.0.1:8080] [--once]
 *
 * --live는 KMA_SERVICE_KEY(공공데이터포털 일반 인증키, Decoding)가 필요하다. simulator/.env에 두면 npm 스크립트가 읽는다.
 * 업로드는 src="sim"이다. 키·좌표는 로그에 남기지 않는다.
 */
import { parseArgs } from "node:util";
import type { UploadRequest } from "../../server/src/protocol.ts";
import { baseTimeOf, fetchRn1 } from "./kma/ncst.ts";
import { gridPointsFor, levelOf, loadSnapshot, rainSampler, saveSnapshot, type Bounds, type RainSnapshot } from "./kma/snapshot.ts";
import { makeFleet, observationsOf, type VirtualCar } from "./kma/fleet.ts";

/** 기본 영역: 데모 궤적(시흥동 → 판교역) 둘레 약 12km × 12km. 앱의 진행 방향 앞 5km가 다 들어간다. */
const DEMO_BOUNDS: Bounds = { south: 37.35, west: 127.035, north: 37.458, east: 127.17 };

const { values: args } = parseArgs({
  options: {
    snapshot: { type: "string" },
    live: { type: "boolean", default: false },
    "record-only": { type: "boolean", default: false },
    cars: { type: "string", default: "2500" },
    bounds: { type: "string" },
    seed: { type: "string", default: "1" },
    server: { type: "string", default: "http://127.0.0.1:8080" },
    data: { type: "string", default: new URL("../data/", import.meta.url).pathname },
    once: { type: "boolean", default: false },
    backfill: { type: "string" },
  },
});

const bounds: Bounds = args.bounds
  ? (([south, west, north, east]) => ({ south, west, north, east }))(args.bounds.split(",").map(Number))
  : DEMO_BOUNDS;

if (!args.live && !args.snapshot && !args.backfill) {
  console.error("--snapshot <파일>, --live, --backfill <시간 수> 중 하나가 필요하다");
  process.exit(1);
}

async function fetchSnapshot(at = new Date()): Promise<RainSnapshot> {
  const key = process.env.KMA_SERVICE_KEY;
  if (!key) throw new Error("KMA_SERVICE_KEY가 없다(simulator/.env)");
  const { baseDate, baseTime } = baseTimeOf(at);
  const points = [];
  for (const { nx, ny } of gridPointsFor(bounds)) points.push({ nx, ny, rn1: await fetchRn1(key, nx, ny, baseDate, baseTime) });
  return { v: 1, source: "kma-ultra-srt-ncst", baseDate, baseTime, bounds, points };
}

function describe(s: RainSnapshot): string {
  const wet = s.points.filter((p) => levelOf(p.rn1) > 0);
  const max = Math.max(0, ...s.points.map((p) => p.rn1));
  return `${s.source} ${s.baseDate} ${s.baseTime} · 격자점 ${s.points.length}개 중 비 ${wet.length}개 · 최대 ${max}mm/h`;
}

if (args.backfill) {
  // 지금 발표분부터 n시간 전까지. 비가 없던 시각도 저장한다(언제 비가 시작·끝났는지 알 수 있게)
  const hours = Math.min(24, Math.max(1, Number(args.backfill)));
  for (let h = 0; h < hours; h++) {
    const s = await fetchSnapshot(new Date(Date.now() - h * 3_600_000));
    const path = `${args.data}kma-${s.baseDate}${s.baseTime}.json`;
    saveSnapshot(path, s);
    console.log(`녹화: ${path} · ${describe(s)}`);
  }
  process.exit(0);
}

let snapshot: RainSnapshot;
if (args.snapshot) {
  snapshot = loadSnapshot(args.snapshot);
} else {
  snapshot = await fetchSnapshot();
  const path = `${args.data}kma-${snapshot.baseDate}${snapshot.baseTime}.json`;
  saveSnapshot(path, snapshot);
  console.log(`녹화: ${path}`);
}
console.log(`강수: ${describe(snapshot)}`);

const fleet: VirtualCar[] = args["record-only"] ? [] : makeFleet(bounds, Number(args.cars), Number(args.seed));
if (fleet.length > 0) {
  const cells = new Set(fleet.flatMap((c) => c.cells.map((x) => x.cell)));
  console.log(`가상 차 ${fleet.length}대 · 지나는 격자 ${cells.size}개`);
}

async function uploadAll(): Promise<void> {
  const rain = rainSampler(snapshot);
  const t = Math.floor(Date.now() / 60_000);
  let accepted = 0, failed = 0, next = 0;
  // 맥 한 대에서 도는 로컬 서버라서 동시에 32개까지만 보낸다
  const worker = async () => {
    while (next < fleet.length) {
      const car = fleet[next++];
      const body: UploadRequest = { v: 1, tid: car.tid, src: "sim", obs: observationsOf(car, rain, t) };
      try {
        const res = await fetch(`${args.server}/v1/observations`, {
          method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        accepted += ((await res.json()) as { accepted: number }).accepted;
      } catch { failed++; }
    }
  };
  await Promise.all(Array.from({ length: 32 }, worker));
  console.log(`${new Date().toLocaleTimeString()}  업로드 ${accepted}개${failed ? ` (실패 ${failed}대)` : ""}`);
}

async function refreshLive(): Promise<void> {
  const { baseTime, baseDate } = baseTimeOf(new Date());
  if (baseDate === snapshot.baseDate && baseTime === snapshot.baseTime) return;
  snapshot = await fetchSnapshot();
  const path = `${args.data}kma-${snapshot.baseDate}${snapshot.baseTime}.json`;
  saveSnapshot(path, snapshot);
  console.log(`녹화: ${path}\n강수: ${describe(snapshot)}`);
}

if (fleet.length > 0) await uploadAll();
if (!args.once) {
  if (fleet.length > 0) setInterval(() => uploadAll().catch((e) => console.error("업로드 실패:", e.message)), 60_000);
  if (args.live) setInterval(() => refreshLive().catch((e) => console.error("강수 갱신 실패:", e.message)), 10 * 60_000);
}

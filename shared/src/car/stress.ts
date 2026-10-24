/**
 * 측정용(디버그 빌드에서만 노출): 가짜 격자 N개를 내 위치 둘레에 깔고
 * ① 도형을 다시 만드는 시간 ② 방향을 매 프레임 바꾸며 그릴 때의 fps를 잰다. 끝나면 원래 모델로 돌린다.
 */
import type { RoadMap } from "../map/RoadMap.ts";
import { geohashBounds, geohashEncode } from "../geo.ts";
import type { CellView, RainLevel, RenderModel } from "../render-model.ts";

interface Host {
  current(): RenderModel | null;
  apply(m: RenderModel): void;
  setForce(on: boolean): void;
  stats: { frames: number; lastFrameMs: number };
}

export async function runStress(map: RoadMap, count: number, seconds: number, host: Host) {
  const base = host.current();
  if (base === null || base.center === null) throw new Error("위치 수신 전");

  const cells = fakeCells(base.center.lat, base.center.lon, count);
  const t0 = performance.now();
  host.apply({ ...base, cells });
  const buildMs = performance.now() - t0;

  const frames0 = host.stats.frames;
  let maxFrameMs = 0;
  const start = performance.now();
  host.setForce(true);
  await new Promise<void>((done) => {
    const tick = (now: number) => {
      const elapsed = now - start;
      map.setHeadingImmediate((base.heading + elapsed * 0.03) % 360);
      maxFrameMs = Math.max(maxFrameMs, host.stats.lastFrameMs);
      if (elapsed < seconds * 1_000) requestAnimationFrame(tick); else done();
    };
    requestAnimationFrame(tick);
  });
  host.setForce(false);
  const frames = host.stats.frames - frames0;
  const elapsedS = (performance.now() - start) / 1_000;

  host.apply(base);
  map.setHeadingImmediate(base.heading);
  const result = {
    cells: cells.length,
    buildMs: Math.round(buildMs * 10) / 10,
    fps: Math.round((frames / elapsedS) * 10) / 10,
    maxRenderMs: Math.round(maxFrameMs * 10) / 10,
  };
  console.log(`stress ${JSON.stringify(result)}`);
  return result;
}

/** 내 위치 격자를 가운데 두고 정사각형으로 퍼지는 격자들. 종류·강도는 섞는다(묶음 수 최악에 가깝게). */
function fakeCells(lat: number, lon: number, count: number): CellView[] {
  const b = geohashBounds(geohashEncode(lat, lon));
  const dLat = b.latHi - b.latLo, dLon = b.lonHi - b.lonLo;
  const side = Math.ceil(Math.sqrt(count));
  const out: CellView[] = [];
  let seed = 7;
  const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let i = 0; i < side && out.length < count; i++) {
    for (let j = 0; j < side && out.length < count; j++) {
      const cell = geohashEncode(lat + (i - side / 2) * dLat, lon + (j - side / 2) * dLon);
      const r = rand();
      const rainLevel = (r < 0.4 ? 0 : [1, 2, 3, 9][Math.floor(rand() * 4)]) as RainLevel;
      out.push({
        cell,
        rain: rainLevel === 0 ? 0 : rand(),
        rainLevel,
        dark: rand() < 0.2 ? rand() : 0,
        brake: rand() < 0.05 ? rand() : 0,
      });
    }
  }
  return out.sort((x, y) => (x.cell < y.cell ? -1 : 1));
}

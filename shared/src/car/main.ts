/**
 * 차량 앱 WebView 진입점. Kotlin(WebMapRenderer)이 postWebMessage로 렌더 모델 JSON을 보낸다.
 *
 * 화면은 세 개다: 도로 지도(MAP)와 내 차(MY_CAR)는 Pixi, 주행(DRIVE)은 앞길 띠(DOM + GSAP, src/drive). 어느 쪽인지는 모델의 screen이 정한다.
 * 렌더 루프는 필요할 때만 돈다: 새 모델이 오거나 화면 크기가 바뀌면 한 프레임을 예약하고,
 * 애니메이션(내 차 이동 0.6초, 와이퍼 두 번 쓸기)이 끝나면 멈춘다. 바뀐 게 없으면 GPU를 쓰지 않는다.
 * 주행 중에는 Pixi 캔버스를 숨기고 렌더 루프를 돌리지 않는다. 띠는 GSAP 트윈이 있을 때만 움직인다.
 */
import { gsap } from "gsap";
import { Container, WebGLRenderer, autoDetectRenderer, type Renderer } from "pixi.js";
import { DriveStrip } from "../drive/DriveStrip.ts";
import { loadVehicleFont } from "../fonts.ts";
import { RoadMap, type BaseRoads } from "../map/RoadMap.ts";
import { palette } from "../map/palette.ts";
import { CarTwin } from "../twin/CarTwin.ts";
import type { RenderModel } from "../render-model.ts";
import { Panel } from "./panel.ts";
import { runStress } from "./stress.ts";

declare global {
  interface Window {
    roadSensor: {
      render(model: RenderModel | string): void;
      stats: { frames: number; models: number; lastFrameMs: number; lastModelMs: number; readonly gsapFrames: number };
      stress?: (cells: number, seconds?: number) => Promise<unknown>;
    };
    /** roads.js(배경 도로)가 넣는다. 없으면 배경 도로 없이 그린다. */
    roadSensorRoads?: BaseRoads;
  }
}

const debug = new URLSearchParams(location.search).has("debug");
/** gsapFrames: GSAP 틱커가 돈 횟수. 움직임이 없을 때 늘지 않아야 한다(측정용) */
const stats = { frames: 0, models: 0, lastFrameMs: 0, lastModelMs: 0, get gsapFrames() { return gsap.ticker.frame; } };

const panel = new Panel(document.getElementById("panel")!);
// 차 화면: 격자는 깔지 않고 앞길 색칠만. 측정(?debug&cells)에서는 격자도 그려 부하를 본다
const map = new RoadMap({ cells: new URLSearchParams(location.search).has("cells") });
const twin = new CarTwin();
const stage = new Container();
let renderer: Renderer | null = null;
let pending: RenderModel | null = null;
let raf = 0;
/** 측정용: true면 애니메이션이 없어도 매 프레임 그린다. */
let forceFrames = false;
/** 주행 화면 앞길 띠 */
const drive = new DriveStrip(document.getElementById("drive")!);
/** 지금 주행 화면인가(Pixi 캔버스·패널·지도 출처를 숨기고 띠만) */
let driving = false;

function invalidate(): void {
  if (raf === 0 && renderer !== null) raf = requestAnimationFrame(frame);
}

function frame(now: number): void {
  raf = 0;
  const t0 = performance.now();
  // 둘 다 불러야 각자 애니메이션이 진행된다(|| 단락 평가 금지)
  const animating = [map.update(now), twin.update(now)].some(Boolean);
  renderer!.render(stage);
  stats.frames++;
  stats.lastFrameMs = performance.now() - t0;
  if (animating || forceFrames) invalidate();
}

function apply(model: RenderModel): void {
  if (model.v !== 1) {
    console.warn(`렌더 모델 버전이 다름: ${model.v}`);
    return;
  }
  panel.update(model);
  const isDrive = model.screen === "DRIVE";
  if (isDrive !== driving) {
    driving = isDrive;
    drive.show(isDrive);
    document.getElementById("map")!.hidden = isDrive;
    document.getElementById("attribution")!.hidden = isDrive;
    if (!isDrive) { layout(); invalidate(); }
  }
  if (isDrive) {
    if (model.drive !== null) drive.update(model.drive, performance.now());
    stats.models++;
    return;
  }
  if (renderer === null) { pending = model; return; }
  const t0 = performance.now();
  const now = performance.now();
  const showMap = model.screen !== "MY_CAR";
  const switched = map.root.visible !== showMap;
  map.root.visible = showMap;
  twin.root.visible = !showMap;
  const dirty = [map.setModel(model, now), twin.setState(model.myCar, now), switched].some(Boolean);
  if (switched && !showMap) twin.enter(now);
  stats.models++;
  stats.lastModelMs = performance.now() - t0;
  if (dirty) invalidate();
}

function layout(): void {
  panel.fitAlerts();
  if (renderer === null) return;
  renderer.resize(window.innerWidth, window.innerHeight);
  if (driving) { drive.layout(); return; }
  const l = { width: window.innerWidth, height: window.innerHeight, insetLeft: panel.width };
  if ([map.layout(l), twin.layout(l)].some(Boolean)) invalidate();
}

window.roadSensor = {
  render: (m) => apply(typeof m === "string" ? (JSON.parse(m) as RenderModel) : m),
  stats,
};

// Kotlin → WebView: WebView.postWebMessage(WebMessage(json), "*")
window.addEventListener("message", (e: MessageEvent) => {
  if (typeof e.data === "string") window.roadSensor.render(e.data);
});
window.addEventListener("resize", layout);
document.addEventListener("visibilitychange", () => { if (!document.hidden) invalidate(); });

/**
 * Pixi의 자동 선택(autoDetectRenderer)은 WebGL1 컨텍스트로 지원 여부를 본다.
 * 에뮬레이터 Pleos WebView는 WebGL1이 차단되고 WebGL2만 되므로, 자동 선택에 맡기면 Canvas 2D로 떨어진다.
 * 그래서 WebGL2가 되면 WebGLRenderer를 직접 만들고(WebGL2 컨텍스트를 먼저 요청한다), 안 될 때만 자동 선택에 맡긴다.
 */
async function createRenderer(): Promise<Renderer> {
  const options = {
    background: palette.background,
    antialias: true,
    resolution: window.devicePixelRatio,
    autoDensity: true,
    width: window.innerWidth,
    height: window.innerHeight,
  };
  if (document.createElement("canvas").getContext("webgl2") !== null) {
    const r = new WebGLRenderer();
    await r.init({ ...options, preferWebGLVersion: 2 });
    return r;
  }
  console.warn("WebGL2 없음: 자동 선택으로 그린다");
  return autoDetectRenderer({ ...options, preference: "webgl" });
}

async function start(): Promise<void> {
  // 렌더러를 만드는 동안 차량 글꼴을 같이 불러 둔다(캔버스 글자는 첫 프레임의 글꼴로 굳는다)
  const fonts = loadVehicleFont();
  try {
    renderer = await createRenderer();
  } catch (e) {
    panel.fatal("지도를 그릴 수 없습니다 (렌더러 초기화 실패)");
    console.error(e);
    return;
  }
  await fonts;
  console.info(`renderer ${renderer.name} ${renderer instanceof WebGLRenderer ? `webgl${renderer.context.webGLVersion}` : ""}`);
  document.getElementById("map")!.appendChild(renderer.canvas);
  twin.root.visible = false;
  stage.addChild(map.root, twin.root);
  map.setBaseRoads(window.roadSensorRoads ?? null);
  document.getElementById("attribution")!.textContent = window.roadSensorRoads?.attribution ?? "";
  layout();
  if (pending !== null) { const m = pending; pending = null; apply(m); }
  invalidate();

  if (debug) {
    window.roadSensor.stress = (cells, seconds = 5) =>
      runStress(map, cells, seconds, {
        current: () => panel.model,
        apply: (m) => map.setModel(m, performance.now()),
        setForce: (on) => { forceFrames = on; if (on) invalidate(); },
        stats,
      });
  }
}

void start();

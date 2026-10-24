/**
 * 차량 앱 WebView 진입점. Kotlin(WebMapRenderer)이 postWebMessage로 렌더 모델 JSON을 보낸다.
 *
 * 렌더 루프는 필요할 때만 돈다: 새 모델이 오거나 화면 크기가 바뀌면 한 프레임을 예약하고,
 * 내 차 이동 애니메이션(0.6초)이 끝나면 멈춘다. 바뀐 게 없으면 GPU를 쓰지 않는다.
 * 주행 중에는 Kotlin이 WebView를 숨기고 onPause()하므로 여기까지 오지 않는다.
 */
import { Container, WebGLRenderer, autoDetectRenderer, type Renderer } from "pixi.js";
import { RoadMap } from "../map/RoadMap.ts";
import { palette } from "../map/palette.ts";
import type { RenderModel } from "../render-model.ts";
import { Panel } from "./panel.ts";
import { runStress } from "./stress.ts";

declare global {
  interface Window {
    roadSensor: {
      render(model: RenderModel | string): void;
      stats: { frames: number; models: number; lastFrameMs: number; lastModelMs: number };
      stress?: (cells: number, seconds?: number) => Promise<unknown>;
    };
  }
}

const debug = new URLSearchParams(location.search).has("debug");
const stats = { frames: 0, models: 0, lastFrameMs: 0, lastModelMs: 0 };

const panel = new Panel(document.getElementById("panel")!);
const map = new RoadMap();
const stage = new Container();
let renderer: Renderer | null = null;
let pending: RenderModel | null = null;
let raf = 0;
/** 측정용: true면 애니메이션이 없어도 매 프레임 그린다. */
let forceFrames = false;

function invalidate(): void {
  if (raf === 0 && renderer !== null) raf = requestAnimationFrame(frame);
}

function frame(now: number): void {
  raf = 0;
  const t0 = performance.now();
  const animating = map.update(now);
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
  if (renderer === null) { pending = model; return; }
  const t0 = performance.now();
  const dirty = map.setModel(model, performance.now());
  stats.models++;
  stats.lastModelMs = performance.now() - t0;
  if (dirty) invalidate();
}

function layout(): void {
  if (renderer === null) return;
  renderer.resize(window.innerWidth, window.innerHeight);
  if (map.layout({ width: window.innerWidth, height: window.innerHeight, insetLeft: panel.width })) invalidate();
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
  try {
    renderer = await createRenderer();
  } catch (e) {
    panel.fatal("지도를 그릴 수 없습니다 (렌더러 초기화 실패)");
    console.error(e);
    return;
  }
  console.info(`renderer ${renderer.name} ${renderer instanceof WebGLRenderer ? `webgl${renderer.context.webGLVersion}` : ""}`);
  document.getElementById("map")!.appendChild(renderer.canvas);
  stage.addChild(map.root);
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

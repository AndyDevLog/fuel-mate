/**
 * 도로 지도 그리기(Pixi v8). 렌더 모델을 받아 격자·진행 방향·내 차를 그린다.
 * 차량 앱 WebView와 관제 웹이 같이 쓰므로 DOM·브리지에는 손대지 않는다. 렌더 루프도 부르는 쪽이 돌린다:
 * setModel()·layout()이 true를 돌려주거나 update()가 true인 동안만 한 프레임씩 그리면 된다.
 *
 * 좌표: 격자는 "세계"(기준점 둘레 m 평면)에 한 번 그려 두고, 내 차가 움직이거나 방향이 바뀌면
 * 세계 컨테이너의 변환만 바꾼다. 그래서 격자 수천 개라도 위치 갱신에는 도형을 다시 만들지 않는다.
 */
import { Container, Graphics, Text } from "pixi.js";
import { LocalProjection, geohashBounds } from "../geo.ts";
import type { AlertView, CellView, LatLon, RenderModel } from "../render-model.ts";
import { palette } from "./palette.ts";

export interface MapLayout {
  width: number;
  height: number;
  /** 왼쪽에 알림 패널이 덮는 폭. 내 차는 나머지 영역 가운데에 둔다. */
  insetLeft: number;
}

interface Pose { x: number; y: number; heading: number }

const MOVE_MS = 600;
/**
 * 격자 사이 틈(화면 px). 격자가 크게 보일 때는 낱낱이 읽히게 틈을 두고,
 * 작게 보일 때(수천 칸)는 틈을 없애서 그물처럼 보이지 않게 한다(0.5px만 둬도 안티앨리어싱으로 줄이 남는다).
 */
const CELL_GAP_PX = { large: 1.5, small: 0, threshold: 24 };
/** geohash 7자리 격자의 대략 폭(m). 화면 크기 판단에만 쓴다. */
const CELL_SIZE_M = 130;
/** 알림 구간의 격자를 낱낱이 테두리 칠 최대 칸 수 */
const MAX_OUTLINED_CELLS = 40;
const ALPHA_STEPS = 20;

export class RoadMap {
  readonly root = new Container();

  private readonly world = new Container();
  private readonly cellsG = new Graphics();
  private readonly brakeG = new Graphics();
  private readonly alertG = new Graphics();
  private readonly routeG = new Graphics();
  private readonly aheadG = new Graphics();
  private readonly ringsG = new Graphics();
  private readonly ringLabels = new Container();
  private readonly car = new Graphics();
  private readonly north = new Container();

  private projection: LocalProjection | null = null;
  private model: RenderModel | null = null;
  private layoutValue: MapLayout = { width: 1, height: 1, insetLeft: 0 };
  private anchor = { x: 0, y: 0 };
  private pxPerM = 0.1;

  private pose: Pose | null = null;
  private from: Pose | null = null;
  private to: Pose | null = null;
  private moveStart = 0;

  constructor() {
    this.world.addChild(this.cellsG, this.routeG, this.brakeG, this.alertG);
    this.root.addChild(this.aheadG, this.world, this.ringsG, this.ringLabels, this.car, this.north);
    this.drawCar();
    this.drawNorth();
  }

  /** 화면 크기가 바뀌면 부른다. 다시 그려야 하면 true. */
  layout(layout: MapLayout): boolean {
    this.layoutValue = layout;
    const mapWidth = layout.width - layout.insetLeft;
    this.anchor = { x: layout.insetLeft + mapWidth / 2, y: layout.height * 0.8 };
    // 진행 방향 앞 판정 반경이 화면 위쪽까지 들어오게
    const radius = this.model?.ahead.radiusM ?? 5_000;
    this.pxPerM = (this.anchor.y - 24) / (radius * 1.05);
    this.car.position.set(this.anchor.x, this.anchor.y);
    this.north.position.set(layout.width - 72, layout.height - 72);
    this.drawStatic();
    // 틈은 화면 px 기준이라 축척이 바뀌면 격자를 다시 그린다
    if (this.projection !== null && this.model !== null) this.drawCells(this.model.cells);
    this.applyPose();
    return true;
  }

  /** 새 렌더 모델. 다시 그려야 하면 true. */
  setModel(next: RenderModel, now: number): boolean {
    const prev = this.model;
    this.model = next;

    const visible = next.center !== null;
    this.world.visible = this.car.visible = this.aheadG.visible = this.ringsG.visible = this.ringLabels.visible = visible;
    if (next.center === null) return prev?.center != null;

    let rebuildAll = false;
    if (this.projection === null || this.projection.isFar(next.center)) {
      this.projection = new LocalProjection(next.center);
      this.pose = null;
      rebuildAll = true;
    }
    if (prev === null || prev.ahead.radiusM !== next.ahead.radiusM || prev.ahead.halfAngleDeg !== next.ahead.halfAngleDeg) {
      this.layout(this.layoutValue);
    }
    if (rebuildAll || prev === null || !sameCells(prev.cells, next.cells)) this.drawCells(next.cells);
    if (rebuildAll || prev === null || !sameAlerts(prev.alerts, next.alerts)) this.drawAlerts(next.alerts);
    if (rebuildAll || prev === null || !samePoint(prev.center, next.center) || !samePoint(prev.destination, next.destination)) {
      this.drawRoute(next.center, next.destination, next.ahead.corridorHalfWidthM);
    }
    this.moveTo(next.center, next.heading, now);
    return true;
  }

  /** 애니메이션 한 걸음. 아직 움직이는 중이면 true(다음 프레임도 그려야 함). */
  update(now: number): boolean {
    if (this.from === null || this.to === null) return false;
    const t = Math.min(1, (now - this.moveStart) / MOVE_MS);
    const e = 1 - (1 - t) ** 3;
    this.pose = {
      x: this.from.x + (this.to.x - this.from.x) * e,
      y: this.from.y + (this.to.y - this.from.y) * e,
      heading: this.from.heading + angleDelta(this.from.heading, this.to.heading) * e,
    };
    this.applyPose();
    if (t >= 1) { this.from = this.to = null; return false; }
    return true;
  }

  get animating(): boolean { return this.to !== null; }

  /** 측정용: 방향만 바로 바꾼다(도형은 그대로, 변환만 바뀜). */
  setHeadingImmediate(heading: number): void {
    if (this.pose === null) return;
    this.pose = { ...this.pose, heading };
    this.applyPose();
  }

  private moveTo(center: LatLon, heading: number, now: number): void {
    const p = this.projection!;
    const target: Pose = { x: p.x(center.lon), y: p.y(center.lat), heading };
    if (this.pose === null) {
      this.pose = target;
      this.from = this.to = null;
      this.applyPose();
      return;
    }
    if (target.x === this.pose.x && target.y === this.pose.y && target.heading === this.pose.heading) return;
    this.from = { ...this.pose };
    this.to = target;
    this.moveStart = now;
  }

  private applyPose(): void {
    if (this.pose === null) return;
    const rad = (-this.pose.heading * Math.PI) / 180;
    this.world.pivot.set(this.pose.x, this.pose.y);
    this.world.position.set(this.anchor.x, this.anchor.y);
    this.world.scale.set(this.pxPerM);
    this.world.rotation = rad;
    this.north.rotation = rad;
  }

  /** 격자: 색·투명도가 같은 것끼리 한 경로로 모아 채운다(채우기 호출 수 = 묶음 수). */
  private drawCells(cells: CellView[]): void {
    const p = this.projection!;
    const fills = new Map<string, number[]>();
    const darks = new Map<number, number[]>();
    const brakes: number[] = [];
    const push = <K>(map: Map<K, number[]>, key: K, ...v: number[]) => {
      let list = map.get(key);
      if (!list) map.set(key, (list = []));
      list.push(...v);
    };

    const cellPx = CELL_SIZE_M * this.pxPerM;
    const gapM = (cellPx >= CELL_GAP_PX.threshold ? CELL_GAP_PX.large : CELL_GAP_PX.small) / this.pxPerM;
    for (const c of cells) {
      const b = geohashBounds(c.cell);
      const x0 = p.x(b.lonLo) + gapM / 2, x1 = p.x(b.lonHi) - gapM / 2;
      const y0 = p.y(b.latHi) + gapM / 2, y1 = p.y(b.latLo) - gapM / 2;
      if (c.rain > 0 && c.rainLevel !== 0) {
        push(fills, `${palette.rain[c.rainLevel]}|${step(0.3 + 0.65 * c.rain)}`, x0, y0, x1 - x0, y1 - y0);
      } else {
        push(fills, `${palette.clear}|${step(0.6)}`, x0, y0, x1 - x0, y1 - y0);
      }
      if (c.dark >= 0.2) push(darks, step(0.3 + 0.6 * c.dark), x0 + 12, y0 + 12, x1 - x0 - 24, y1 - y0 - 24);
      if (c.brake > 0) brakes.push((x0 + x1) / 2, (y0 + y1) / 2, 20 + 36 * c.brake);
    }

    const g = this.cellsG.clear();
    for (const [key, rects] of fills) {
      const [color, alpha] = key.split("|").map(Number);
      for (let i = 0; i < rects.length; i += 4) g.rect(rects[i], rects[i + 1], rects[i + 2], rects[i + 3]);
      g.fill({ color, alpha });
    }
    for (const [alpha, rects] of darks) {
      for (let i = 0; i < rects.length; i += 4) g.rect(rects[i], rects[i + 1], rects[i + 2], rects[i + 3]);
      g.stroke({ width: 12, color: palette.dark, alpha });
    }

    const bg = this.brakeG.clear();
    for (let i = 0; i < brakes.length; i += 3) bg.circle(brakes[i], brakes[i + 1], brakes[i + 2]);
    if (brakes.length > 0) bg.fill({ color: palette.brake, alpha: 0.9 });
  }

  /**
   * 알림 구간: 구간의 격자는 옅고 얇은 테두리, 구간이 시작되는 격자(가장 가까운 격자)는 굵은 테두리.
   * 구간이 [MAX_OUTLINED_CELLS]칸을 넘으면 시작 격자만 그린다(지도가 흰 그물처럼 덮이지 않게). 순서는 왼쪽 목록이 보여준다.
   */
  private drawAlerts(alerts: AlertView[]): void {
    const p = this.projection!;
    const g = this.alertG.clear();
    const rect = (cell: string) => {
      const b = geohashBounds(cell);
      g.rect(p.x(b.lonLo), p.y(b.latHi), p.x(b.lonHi) - p.x(b.lonLo), p.y(b.latLo) - p.y(b.latHi));
    };
    const starts = new Set(alerts.map((a) => a.cell));
    const rest = new Set(alerts.flatMap((a) => a.cells).filter((c) => !starts.has(c)));
    // 구간이 크면(비 띠 전체 등) 칸마다 테두리를 그리면 그물이 되므로 시작 격자만 남긴다
    if (rest.size > MAX_OUTLINED_CELLS) rest.clear();
    for (const cell of rest) rect(cell);
    if (rest.size > 0) g.stroke({ width: 6, color: palette.alert, alpha: 0.22 });
    for (const cell of starts) rect(cell);
    if (starts.size > 0) g.stroke({ width: 18, color: palette.alert, alpha: 0.95 });
  }

  /** 목적지가 있으면 목적지 방향 직선 띠(판정과 같은 폭). */
  private drawRoute(center: LatLon, destination: LatLon | null, halfWidthM: number): void {
    const g = this.routeG.clear();
    if (destination === null) return;
    const p = this.projection!;
    const x0 = p.x(center.lon), y0 = p.y(center.lat), x1 = p.x(destination.lon), y1 = p.y(destination.lat);
    g.moveTo(x0, y0).lineTo(x1, y1).stroke({ width: halfWidthM * 2, color: palette.route, alpha: 0.06, cap: "round" });
    g.moveTo(x0, y0).lineTo(x1, y1).stroke({ width: 18, color: palette.route, alpha: 0.5 });
    g.circle(x1, y1, 60).fill({ color: palette.route, alpha: 0.9 });
  }

  /** 화면 고정 요소: 진행 방향 부채꼴, 거리 고리(1km 간격). 화면 크기가 바뀔 때만 다시 그린다. */
  private drawStatic(): void {
    const { x, y } = this.anchor;
    const radiusM = this.model?.ahead.radiusM ?? 5_000;
    const half = ((this.model?.ahead.halfAngleDeg ?? 30) * Math.PI) / 180;
    const r = radiusM * this.pxPerM;

    this.aheadG.clear()
      .moveTo(x, y).arc(x, y, r, -Math.PI / 2 - half, -Math.PI / 2 + half).closePath()
      .fill({ color: palette.ahead, alpha: 0.045 })
      .stroke({ width: 1.5, color: palette.ahead, alpha: 0.18 });

    const rings = this.ringsG.clear();
    for (const label of this.ringLabels.removeChildren()) label.destroy();
    for (let km = 1; km * 1_000 <= radiusM; km++) {
      const rr = km * 1_000 * this.pxPerM;
      rings.circle(x, y, rr);
      const text = new Text({ text: `${km}km`, style: { fontFamily: "sans-serif", fontSize: 18, fill: palette.ring } });
      text.alpha = 0.45;
      text.position.set(x + 8, y - rr - 24);
      this.ringLabels.addChild(text);
    }
    rings.stroke({ width: 1, color: palette.ring, alpha: 0.1 });
  }

  private drawCar(): void {
    this.car.clear()
      .circle(0, 0, 34).fill({ color: palette.car, alpha: 0.16 })
      .poly([0, -26, 18, 18, 0, 8, -18, 18]).fill({ color: palette.car })
      .stroke({ width: 2, color: 0xffffff, alpha: 0.9 });
  }

  private drawNorth(): void {
    const g = new Graphics()
      .circle(0, 0, 30).fill({ color: 0x000000, alpha: 0.35 }).stroke({ width: 1, color: 0xffffff, alpha: 0.3 })
      .poly([0, -22, 8, 0, -8, 0]).fill({ color: 0xff6b6b })
      .poly([0, 22, 8, 0, -8, 0]).fill({ color: 0xffffff, alpha: 0.6 });
    const n = new Text({ text: "N", style: { fontFamily: "sans-serif", fontSize: 16, fontWeight: "bold", fill: 0xffffff } });
    n.anchor.set(0.5);
    n.position.set(0, -44);
    this.north.addChild(g, n);
  }
}

function step(alpha: number): number {
  return Math.round(Math.min(1, Math.max(0, alpha)) * ALPHA_STEPS) / ALPHA_STEPS;
}

function angleDelta(from: number, to: number): number {
  return ((((to - from) % 360) + 540) % 360) - 180;
}

function samePoint(a: LatLon | null, b: LatLon | null): boolean {
  return a === b || (a !== null && b !== null && a.lat === b.lat && a.lon === b.lon);
}

function sameCells(a: CellView[], b: CellView[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const x = a[i], y = b[i];
    if (x.cell !== y.cell || x.rain !== y.rain || x.rainLevel !== y.rainLevel || x.dark !== y.dark || x.brake !== y.brake) return false;
  }
  return true;
}

function sameAlerts(a: AlertView[], b: AlertView[]): boolean {
  return a.length === b.length && a.every((x, i) => x.kind === b[i].kind && x.cells.join() === b[i].cells.join());
}

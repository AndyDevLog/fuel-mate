/**
 * 도로 지도 그리기(Pixi v8). 배경 도로 위에 "내 앞길"을 상태별로 색칠하고(내비의 교통 색처럼), 주변 격자는 옅게 깐다.
 * 차량 앱 WebView와 관제 웹이 같이 쓰므로 DOM·브리지에는 손대지 않는다. 렌더 루프도 부르는 쪽이 돌린다:
 * setModel()·layout()이 true를 돌려주거나 update()가 true인 동안만 한 프레임씩 그리면 된다.
 *
 * 좌표: 격자는 "세계"(기준점 둘레 m 평면)에 한 번 그려 두고, 내 차가 움직이거나 방향이 바뀌면
 * 세계 컨테이너의 변환만 바꾼다. 그래서 격자 수천 개라도 위치 갱신에는 도형을 다시 만들지 않는다.
 */
import { Container, Graphics, Text } from "pixi.js";
import { LocalProjection, geohashBounds } from "../geo.ts";
import type { CellView, LatLon, RenderModel, RouteView, SegmentView } from "../render-model.ts";
import { palette } from "./palette.ts";
import { slice } from "./route.ts";

export interface MapLayout {
  width: number;
  height: number;
  /** 왼쪽에 알림 패널이 덮는 폭. 내 차는 나머지 영역 가운데에 둔다. */
  insetLeft: number;
  /** 위쪽을 덮는 높이(주행 중 네이티브 배너). 없으면 0 */
  insetTop?: number;
  /** 화면 위쪽까지 들어올 앞길 거리(m). 없으면 진행 방향 판정 반경 */
  rangeM?: number;
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
const ALPHA_STEPS = 20;
/** 주변 격자는 앞길보다 한참 옅게 깐다(앞길 색칠이 주인공) */
const CELL_ALPHA = 0.45;

/** 배경 도로(OpenStreetMap). ways = [분류(0 고속, 1 주요, 2 보조), [위도, 경도, 위도, 경도, ...]] */
export interface BaseRoads {
  attribution: string;
  ways: [number, number[]][];
}

/** 화면 px 기준 선 굵기와 색 */
const ROAD_STYLE = [
  { width: 5, color: 0x4a5664 },
  { width: 3.5, color: 0x3a4552 },
  { width: 2, color: 0x2c3541 },
];
const ROUTE_PX = 9;
/**
 * 배경 도로를 Graphics 하나에 몰아 그리면 정점 수가 에뮬레이터 GPU(ANGLE → GLES 번역)의 인덱스 한도를 넘어
 * "Element value exceeds maximum element index"로 아무것도 안 그려진다. 그래서 점 이만큼씩 나눠 그린다.
 */
const ROAD_POINTS_PER_CHUNK = 1_500;

export interface RoadMapOptions {
  /**
   * 주변 격자(네모)를 깔지. 차 화면은 앞길 색칠만으로 충분해서 끈다(격자는 운전자에게 낯선 단위다).
   * 관제 화면처럼 넓은 영역의 분포를 볼 때만 켠다. 알림·앞길 계산에는 영향이 없다.
   */
  cells?: boolean;
}

export class RoadMap {
  readonly root = new Container();
  private readonly showCells: boolean;

  private readonly world = new Container();
  private readonly roadsC = new Container();
  private readonly cellsG = new Graphics();
  private readonly brakeG = new Graphics();
  private readonly routeG = new Graphics();
  private readonly markerG = new Graphics();
  private readonly ringsG = new Graphics();
  private readonly ringLabels = new Container();
  private readonly car = new Graphics();
  private readonly north = new Container();

  private projection: LocalProjection | null = null;
  private baseRoads: BaseRoads | null = null;
  private model: RenderModel | null = null;
  private layoutValue: MapLayout = { width: 1, height: 1, insetLeft: 0 };
  private anchor = { x: 0, y: 0 };
  private pxPerM = 0.1;

  /** false면 내 차 이동을 애니메이션 없이 바로 옮긴다(주행 중: 자동 애니메이션 최소화) */
  animate = true;

  private pose: Pose | null = null;
  private from: Pose | null = null;
  private to: Pose | null = null;
  private moveStart = 0;

  constructor(options: RoadMapOptions = {}) {
    this.showCells = options.cells ?? false;
    this.world.addChild(this.roadsC, this.cellsG, this.brakeG, this.routeG, this.markerG);
    this.root.addChild(this.ringsG, this.ringLabels, this.world, this.car, this.north);
    this.drawCar();
    this.drawNorth();
  }

  /** 화면 크기가 바뀌면 부른다. 다시 그려야 하면 true. */
  layout(layout: MapLayout): boolean {
    this.layoutValue = layout;
    const mapWidth = layout.width - layout.insetLeft;
    this.anchor = { x: layout.insetLeft + mapWidth / 2, y: layout.height * 0.8 };
    // 보여 줄 앞길 거리가 화면 위쪽(덮인 곳 아래)까지 들어오게
    const radius = layout.rangeM ?? this.model?.ahead.radiusM ?? 5_000;
    this.pxPerM = (this.anchor.y - (layout.insetTop ?? 0) - 24) / (radius * 1.05);
    this.car.position.set(this.anchor.x, this.anchor.y);
    this.north.position.set(layout.width - 72, layout.height - 72);
    this.drawStatic();
    // 틈·선 굵기는 화면 px 기준이라 축척이 바뀌면 다시 그린다
    if (this.projection !== null && this.model !== null) {
      this.drawRoads();
      this.drawCells(this.model.cells);
      this.drawRoute(this.model.route, this.model.destination);
    }
    this.applyPose();
    return true;
  }

  /** 배경 도로. 한 번만 넣으면 되고, 투영 기준점이 바뀌거나 축척이 바뀔 때 다시 그린다. */
  setBaseRoads(roads: BaseRoads | null): void {
    this.baseRoads = roads;
    if (this.projection !== null) this.drawRoads();
  }

  /** 새 렌더 모델. 다시 그려야 하면 true. */
  setModel(next: RenderModel, now: number): boolean {
    const prev = this.model;
    this.model = next;

    const visible = next.center !== null;
    this.world.visible = this.car.visible = this.ringsG.visible = this.ringLabels.visible = visible;
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
    if (rebuildAll) this.drawRoads();
    if (rebuildAll || prev === null || !sameCells(prev.cells, next.cells)) this.drawCells(next.cells);
    if (rebuildAll || prev === null || !sameRoute(prev.route, next.route) || !samePoint(prev.destination, next.destination)) {
      this.drawRoute(next.route, next.destination);
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
    if (this.pose === null || !this.animate) {
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
    if (!this.showCells) {
      this.cellsG.clear();
      this.brakeG.clear();
      return;
    }
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
        push(fills, `${palette.rain[c.rainLevel]}|${step((0.3 + 0.65 * c.rain) * CELL_ALPHA)}`, x0, y0, x1 - x0, y1 - y0);
      } else {
        push(fills, `${palette.clear}|${step(0.35)}`, x0, y0, x1 - x0, y1 - y0);
      }
      if (c.dark >= 0.2) push(darks, step((0.3 + 0.6 * c.dark) * CELL_ALPHA), x0 + 12, y0 + 12, x1 - x0 - 24, y1 - y0 - 24);
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
    if (brakes.length > 0) bg.fill({ color: palette.brake, alpha: 0.5 });
  }

  /** 배경 도로(OpenStreetMap). 분류별로, 점 [ROAD_POINTS_PER_CHUNK]개씩 나눈 Graphics로 긋는다(굵은 길이 위). */
  private drawRoads(): void {
    for (const old of this.roadsC.removeChildren()) old.destroy();
    const p = this.projection;
    if (p === null || this.baseRoads === null) return;
    for (let cls = ROAD_STYLE.length - 1; cls >= 0; cls--) {
      const st = ROAD_STYLE[cls];
      let g: Graphics | null = null;
      let points = 0;
      const flush = () => {
        if (g !== null && points > 0) g.stroke({ width: st.width / this.pxPerM, color: st.color, cap: "butt", join: "bevel" });
        g = null;
        points = 0;
      };
      for (const [c, flat] of this.baseRoads.ways) {
        if (c !== cls || flat.length < 4) continue;
        if (g === null) g = this.roadsC.addChild(new Graphics());
        g.moveTo(p.x(flat[1]), p.y(flat[0]));
        for (let i = 2; i < flat.length; i += 2) g.lineTo(p.x(flat[i + 1]), p.y(flat[i]));
        points += flat.length / 2;
        if (points >= ROAD_POINTS_PER_CHUNK) flush();
      }
      flush();
    }
  }

  /**
   * 앞길: 회색 선을 깔고, 그 위에 구간별 색을 덧칠한다(비 → 어두움 → 급정거 순으로 위에 그린다).
   * 구간이 시작되는 곳에는 흰 점을 찍는다. 실제 길을 모르면(estimated) 더 옅고 가는 선이다.
   */
  private drawRoute(route: RouteView | null, destination: LatLon | null): void {
    const g = this.routeG.clear();
    const mg = this.markerG.clear();
    const p = this.projection!;
    const px = (v: number) => v / this.pxPerM;
    if (route !== null && route.path.length >= 2) {
      const pts = route.path.map(([lat, lon]) => [p.x(lon), p.y(lat)] as [number, number]);
      const cum = [0];
      for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
      // 프로필(core)은 대원 거리, 여기는 평면 거리라 아주 조금 다르다 → 길이를 맞춰 비례로 자른다
      const scale = route.lengthM > 0 ? cum[cum.length - 1] / route.lengthM : 1;

      polyline(g, pts);
      g.stroke({ width: px(route.estimated ? 5 : ROUTE_PX), color: 0xcfd8e3, alpha: route.estimated ? 0.16 : 0.32, cap: "round", join: "round" });

      const order: SegmentView["kind"][] = ["RAIN", "DARK", "HARD_BRAKE"];
      for (const kind of order) {
        for (const s of route.segments.filter((x) => x.kind === kind)) {
          const part = slice(pts, cum, s.fromM * scale, s.toM * scale);
          if (part.length < 2) continue;
          polyline(g, part);
          g.stroke({ width: px(ROUTE_PX + 2), color: segmentColor(s), alpha: 0.95, cap: "round", join: "round" });
          if (s.fromM > 0) mg.circle(part[0][0], part[0][1], px(6)).fill({ color: 0xffffff }).stroke({ width: px(2), color: segmentColor(s) });
        }
      }
    }
    if (destination !== null) {
      mg.circle(p.x(destination.lon), p.y(destination.lat), px(8)).fill({ color: palette.route }).stroke({ width: px(2), color: 0xffffff, alpha: 0.8 });
    }
  }

  /** 화면 고정 요소: 거리 고리(1km 간격). 화면 크기가 바뀔 때만 다시 그린다. */
  private drawStatic(): void {
    const { x, y } = this.anchor;
    const radiusM = this.model?.ahead.radiusM ?? 5_000;

    const rings = this.ringsG.clear();
    for (const label of this.ringLabels.removeChildren()) label.destroy();
    for (let km = 1; km * 1_000 <= radiusM; km++) {
      const rr = km * 1_000 * this.pxPerM;
      rings.circle(x, y, rr);
      const text = new Text({ text: `${km}km`, style: { fontFamily: "sans-serif", fontSize: 18, fill: palette.ring } });
      text.alpha = 0.35;
      text.position.set(x + 8, y - rr - 24);
      this.ringLabels.addChild(text);
    }
    rings.stroke({ width: 1, color: palette.ring, alpha: 0.07 });
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

function sameRoute(a: RouteView | null, b: RouteView | null): boolean {
  if (a === null || b === null) return a === b;
  return a.estimated === b.estimated && a.lengthM === b.lengthM &&
    a.path.length === b.path.length && a.path.every((p, i) => p[0] === b.path[i][0] && p[1] === b.path[i][1]) &&
    a.segments.length === b.segments.length &&
    a.segments.every((s, i) => s.fromM === b.segments[i].fromM && s.toM === b.segments[i].toM && s.kind === b.segments[i].kind && s.rainLevel === b.segments[i].rainLevel);
}

export function segmentColor(s: Pick<SegmentView, "kind" | "rainLevel">): number {
  switch (s.kind) {
    case "RAIN": return s.rainLevel === 0 ? palette.rain[2] : palette.rain[s.rainLevel];
    case "DARK": return palette.dark;
    case "HARD_BRAKE": return palette.brake;
  }
}

function polyline(g: Graphics, pts: [number, number][]): void {
  g.moveTo(pts[0][0], pts[0][1]);
  for (let i = 1; i < pts.length; i++) g.lineTo(pts[i][0], pts[i][1]);
}

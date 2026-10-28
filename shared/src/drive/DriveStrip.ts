/**
 * 주행 화면 앞길 띠(DOM + GSAP). 위쪽 큰 글자 배너는 Kotlin이 네이티브로 그리고, 여기는 그 아래 띠만 그린다.
 *
 * 연출 원칙(주행 중이라 절제한다, Pleos UI 가이드 "주행 중 애니메이션을 줄이거나 제한"):
 * - 띠는 고정하고 내 차만 움직인다. 움직이는 것은 "내 차의 실제 이동" 하나다.
 * - 위치는 1초마다 온다. 다음 위치까지 직전 간격만큼 트윈으로 이어 붙이되, ease는 구간마다 차량 속도로 만든다(에르미트):
 *   구간 시작 속도 = 앞 구간 끝 속도, 끝 속도 = 그 위치를 받을 때의 차량 속도. 그래서 가속·감속이 실제 차처럼 곡선으로 보이고
 *   1초 경계에서 속도가 끊기지 않는다. 등속이면 직선과 같다. 고정 ease-in-out을 쓰면 매초 가감속해 출렁인다.
 * - 2km를 가면 띠와 차를 한 timeline으로 0.6초 동안 함께 미끄러뜨려 다시 맞춘다. 그 밖의 효과는 없다.
 * - Pixi(WebGL)는 쓰지 않는다. transform 트윈만이라 GPU 합성만 쓰고, 트윈이 없으면 GSAP 틱커를 재운다.
 */
import { gsap } from "gsap";
import { cssColor, palette } from "../map/palette.ts";
import type { DriveView, RainLevel, AlertKind } from "../render-model.ts";
import { AHEAD_M, BEHIND_M, type Anchor, type Placed, frac, nextAnchor, place, segmentEase } from "./strip-layout.ts";

/** 정면에서 본 자동차(48 그리드, 면 아이콘). Kotlin 쪽 아이콘과 같은 모양: 앞유리·그릴은 구멍(even-odd) */
const CAR_PATH =
  "M16 12H32L36.5 21.5H11.5Z M18.2 14.6H29.8L33.2 21.5H14.8Z " +
  "M13 21.5H35A3 3 0 0 1 38 24.5V31A3 3 0 0 1 35 34H13A3 3 0 0 1 10 31V24.5A3 3 0 0 1 13 21.5Z " +
  "M16.8 26.2H31.2A0.8 0.8 0 0 1 31.2 27.8H16.8A0.8 0.8 0 0 1 16.8 26.2Z " +
  "M7.5 19.5H9A1 1 0 0 1 10 20.5V22H6.5V20.5A1 1 0 0 1 7.5 19.5Z M38 19.5H40.5A1 1 0 0 1 41.5 20.5V22H38Z " +
  "M11 34H17V36A1.5 1.5 0 0 1 15.5 37.5H12.5A1.5 1.5 0 0 1 11 36Z M31 34H37V36A1.5 1.5 0 0 1 35.5 37.5H32.5A1.5 1.5 0 0 1 31 36Z";

/** 띠를 다시 맞출 때 미끄러지는 시간(초) */
const SLIDE_S = 0.6;

export class DriveStrip {
  private readonly root: HTMLElement;
  private readonly bar: HTMLElement;
  /** 띠 위 구간과 문구. 다시 맞출 때 통째로 미끄러진다 */
  private readonly world: HTMLElement;
  private readonly labels: HTMLElement;
  private readonly car: HTMLElement;
  private readonly empty: HTMLElement;

  private anchor: Anchor | null = null;
  private placed: Placed[] = [];
  private last: DriveView | null = null;
  private lastAt = 0;
  /** 앞 구간이 끝날 때의 속도(px/s). 다음 구간의 시작 속도가 된다 */
  private vEnd: number | null = null;
  /** 우리가 틱커를 재웠는가. 깨어 있는 틱커를 다시 깨우면 시간 기준이 다시 잡혀 한 프레임씩 밀린다 */
  private asleep = false;

  constructor(root: HTMLElement) {
    this.root = root;
    root.innerHTML = `
      <div class="strip">
        <div class="car"><svg viewBox="0 0 48 48" aria-hidden="true"><path fill-rule="evenodd" d="${CAR_PATH}"/></svg></div>
        <div class="bar"><div class="world"></div></div>
        <div class="labels"></div>
        <p class="empty" hidden>앞 ${AHEAD_M / 1_000}km 이상 없음</p>
      </div>`;
    this.bar = root.querySelector(".bar")!;
    this.world = root.querySelector(".world")!;
    this.labels = root.querySelector(".labels")!;
    this.car = root.querySelector(".car")!;
    this.empty = root.querySelector(".empty")!;
    // 트윈이 다 끝나면 틱커를 재운다(바뀐 게 없으면 렌더 루프를 멈춘다는 원칙). 새 트윈을 만들면 GSAP이 다시 깨운다
    gsap.ticker.add(() => {
      if (gsap.globalTimeline.getChildren(true, true, false).length > 0) return;
      gsap.ticker.sleep();
      this.asleep = true;
    });
  }

  show(on: boolean): void {
    this.root.hidden = !on;
    if (!on) {
      gsap.killTweensOf([this.car, this.world, this.labels]);
      this.anchor = null;
      this.placed = [];
      this.last = null;
      this.vEnd = null;
    }
  }

  update(d: DriveView, now: number): void {
    const moved = this.last === null || this.last.odometerM !== d.odometerM || this.last.epoch !== d.epoch;
    if (!moved) {
      // 위치는 그대로이고 구간·문구만 바뀐 모델(격자 갱신 등): 다시 그리기만 하고, 달리는 중인 차 트윈은 건드리지 않는다
      if (this.anchor !== null && !sameItems(this.last!, d)) {
        this.placed = place(d, this.placed);
        this.last = d;
        this.draw(this.anchor, this.bar.clientWidth);
      }
      return;
    }
    const { anchor, change } = nextAnchor(this.anchor, d);
    const width = this.bar.clientWidth;
    const shiftPx = this.anchor === null ? 0 : (anchor.anchorM - this.anchor.anchorM) / (BEHIND_M + AHEAD_M) * width;
    // 직전 모델과의 간격만큼 이동한다(보통 1초). 처음이거나 오래 멈췄다 오면 너무 길거나 짧지 않게 자른다
    const dt = Math.min(1.5, Math.max(0.3, (now - this.lastAt) / 1_000));
    this.anchor = anchor;
    this.placed = place(d, change === "keep" ? this.placed : []);
    this.last = d;
    this.lastAt = now;

    this.draw(anchor, width);
    const carX = frac(anchor.anchorM, d.odometerM) * width;
    // 이 위치를 받을 때의 차량 속도(px/s). 구간 끝 속도로 쓰고, 다음 구간의 시작 속도로 넘긴다
    const v1 = d.speedMps === null ? null : d.speedMps * (width / (BEHIND_M + AHEAD_M));
    const easeTo = (from: number) => segmentEase(this.vEnd ?? v1, v1, carX - from, dt);
    // 잠든 틱커는 시간이 멈춰 있다. 먼저 깨워야 새 트윈의 시작 시각이 지금이 된다(안 그러면 멈췄다가 몰아서 따라잡는다).
    // 깨어 있을 때는 부르지 않는다
    if (this.asleep) { gsap.ticker.wake(); this.asleep = false; }
    if (change === "reset") {
      gsap.killTweensOf([this.car, this.world, this.labels]);
      gsap.set([this.world, this.labels], { x: 0 });
      gsap.set(this.car, { x: carX });
    } else if (change === "slide") {
      // 차의 화면 위치 = 길 위 위치(평소처럼 dt 동안, 속도 곡선) + 띠의 미끄러짐(0.6초).
      // 둘을 따로 움직여 매 프레임 더한다: 띠가 미끄러지는 동안에도 차는 제 속도로 달리고, 끝나고 멈추지 않는다
      const road = { x: Number(gsap.getProperty(this.car, "x")) - shiftPx };
      const place = () => gsap.set(this.car, { x: road.x + Number(gsap.getProperty(this.world, "x")) });
      gsap.killTweensOf(this.car);
      gsap.timeline()
        .fromTo([this.world, this.labels], { x: shiftPx }, { x: 0, duration: SLIDE_S, ease: "power2.inOut", onUpdate: place }, 0)
        .to(road, { x: carX, duration: dt, ease: easeTo(road.x), onUpdate: place }, 0);
    } else {
      gsap.to(this.car, { x: carX, duration: dt, ease: easeTo(Number(gsap.getProperty(this.car, "x"))), overwrite: true });
    }
    this.vEnd = v1;
  }

  /** 화면 크기가 바뀌면 애니메이션 없이 다시 놓는다. */
  layout(): void {
    if (this.anchor === null || this.last === null) return;
    const width = this.bar.clientWidth;
    this.draw(this.anchor, width);
    gsap.killTweensOf([this.car, this.world, this.labels]);
    gsap.set([this.world, this.labels], { x: 0 });
    gsap.set(this.car, { x: frac(this.anchor.anchorM, this.last.odometerM) * width });
  }

  private draw(anchor: Anchor, width: number): void {
    // 구간: 비를 먼저, 어두움·급정거를 위에(지도 색칠 순서와 같다). 짧은 구간도 보이게 최소 10px
    const order: AlertKind[] = ["RAIN", "DARK", "HARD_BRAKE"];
    this.world.replaceChildren(...[...this.placed].sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind)).map((p) => {
      const el = document.createElement("div");
      el.className = "seg";
      const x0 = frac(anchor.anchorM, p.fromAbs) * width;
      const x1 = Math.max(frac(anchor.anchorM, p.toAbs) * width, x0 + 10);
      el.style.left = `${x0}px`;
      el.style.width = `${x1 - x0}px`;
      el.style.background = colorOf(p.kind, p.rainLevel);
      return el;
    }));

    // 문구: 구간 시작 아래(이미 들어선 구간은 내 차 자리), 겹치면 오른쪽으로 밀고 넘치면 뺀다(가까운 것부터)
    this.labels.replaceChildren();
    let next = 0;
    const carX = this.last === null ? 0 : frac(anchor.anchorM, this.last.odometerM) * width;
    for (const p of this.placed) {
      const el = document.createElement("span");
      el.textContent = p.label;
      el.style.color = colorOf(p.kind, p.rainLevel);
      this.labels.append(el);
      const start = Math.max(frac(anchor.anchorM, p.fromAbs) * width, p.label.startsWith("지금") ? carX : 0, next);
      if (start + el.offsetWidth > width) { el.remove(); break; }
      el.style.left = `${start}px`;
      next = start + el.offsetWidth + 32;
    }
    this.empty.hidden = this.placed.length > 0;
  }
}

function sameItems(a: DriveView, b: DriveView): boolean {
  return a.items.length === b.items.length && a.items.every((x, i) => {
    const y = b.items[i];
    return x.fromM === y.fromM && x.toM === y.toM && x.kind === y.kind && x.rainLevel === y.rainLevel && x.label === y.label;
  });
}

function colorOf(kind: AlertKind, level: RainLevel): string {
  switch (kind) {
    case "RAIN": return cssColor(level === 0 ? palette.rain[2] : palette.rain[level]);
    case "DARK": return cssColor(palette.dark);
    case "HARD_BRAKE": return cssColor(palette.brake);
  }
}

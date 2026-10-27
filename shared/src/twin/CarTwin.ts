/**
 * 내 차 화면(위에서 본 차 모형, 앞이 위). 와이퍼·라이트·브레이크를 차량 신호대로 켜고 끈다.
 * 렌더 루프 원칙은 지도와 같다: 상태가 바뀔 때만 그리고, 와이퍼가 켜지는 순간 두 번 쓸고 나서 멈춘다.
 */
import { Container, Graphics, Text } from "pixi.js";
import { FONT_FAMILIES } from "../fonts.ts";
import type { MyCarView } from "../render-model.ts";

export interface TwinLayout { width: number; height: number; insetLeft: number }

const SWEEP_MS = 1_100;
const SWEEPS = 2;
/** 와이퍼가 쓸어 올라가는 최대 각도(rad, 반시계) */
const SWEEP_RAD = 1.65;

const color = {
  body: 0x2a3440, bodyEdge: 0x8a96a3, glass: 0x13202c, roof: 0x222b35, wheel: 0x46525f,
  off: 0x55606b, beam: 0xfff3b0, wet: 0x3b9dff, tail: 0xa0202a, brake: 0xff3040, blade: 0xcfd8e3,
};

export class CarTwin {
  readonly root = new Container();

  private readonly car = new Container();
  private readonly beams = new Graphics();
  private readonly body = new Graphics();
  private readonly glass = new Graphics();
  private readonly lamps = new Graphics();
  private readonly brakeGlow = new Graphics();
  private readonly blades = [new Graphics(), new Graphics()];
  private readonly gear = new Text({ text: "", style: { fontFamily: FONT_FAMILIES, fontSize: 40, fontWeight: "bold", fill: 0xffffff } });

  private state: MyCarView | null = null;
  private sweepStart = -1;

  constructor() {
    this.gear.anchor.set(0.5);
    this.gear.position.set(0, 18);
    this.gear.alpha = 0.8;
    // 와이퍼 축: 앞유리 아래쪽 왼편·가운데(운전석이 왼쪽)
    this.blades[0].position.set(-32, -33);
    this.blades[1].position.set(4, -33);
    for (const b of this.blades) b.rect(0, -2, 34, 4).fill({ color: color.blade }).circle(0, 0, 3).fill({ color: color.blade });
    this.car.addChild(this.beams, this.brakeGlow, this.body, this.glass, ...this.blades, this.lamps, this.gear);
    this.root.addChild(this.car);
    this.drawBody();
  }

  layout(l: TwinLayout): boolean {
    const w = l.width - l.insetLeft;
    // 차 길이 220 + 앞 불빛 120 단위가 높이의 85%에 들어가게
    const s = Math.min((l.height * 0.85) / 340, (w * 0.7) / 160);
    this.car.scale.set(s);
    this.car.position.set(l.insetLeft + w / 2, l.height / 2 + 60 * s);
    return true;
  }

  /** 다시 그려야 하면 true. */
  setState(next: MyCarView, now: number): boolean {
    const prev = this.state;
    this.state = next;
    if (next.wiperOn === true && prev?.wiperOn !== true) this.sweepStart = now;
    if (next.wiperOn !== true) this.sweepStart = -1;
    this.drawParts();
    this.applyBlades(now);
    return true;
  }

  /** 화면에 들어올 때 부른다. 와이퍼가 켜져 있으면 두 번 쓸어서 보여준다. 다시 그려야 하면 true. */
  enter(now: number): boolean {
    if (this.state?.wiperOn !== true) return false;
    this.sweepStart = now;
    return true;
  }

  /** 와이퍼가 쓰는 동안 true. */
  update(now: number): boolean {
    return this.applyBlades(now);
  }

  private applyBlades(now: number): boolean {
    let angle = 0;
    let moving = false;
    if (this.sweepStart >= 0) {
      const t = (now - this.sweepStart) / SWEEP_MS;
      if (t < SWEEPS) {
        angle = (-SWEEP_RAD * (1 - Math.cos(2 * Math.PI * t))) / 2;
        moving = true;
      } else this.sweepStart = -1;
    }
    for (const b of this.blades) b.rotation = angle;
    return moving;
  }

  private drawBody(): void {
    const wheels: [number, number][] = [[-60, -80], [48, -80], [-60, 50], [48, 50]];
    this.body.clear();
    for (const [x, y] of wheels) this.body.roundRect(x, y, 12, 32, 4);
    this.body.fill({ color: color.wheel })
      .roundRect(-50, -110, 100, 220, 28).fill({ color: color.body }).stroke({ width: 2, color: color.bodyEdge, alpha: 0.7 })
      .roundRect(-36, -26, 72, 88, 10).fill({ color: color.roof })
      .poly([-34, 68, 34, 68, 38, 88, -38, 88]).fill({ color: color.glass });
  }

  private drawParts(): void {
    const s = this.state!;
    const wet = s.wiperOn === true;
    this.glass.clear().poly([-40, -64, 40, -64, 34, -30, -34, -30])
      .fill({ color: wet ? color.wet : color.glass, alpha: wet ? 0.55 : 1 });
    for (const b of this.blades) b.tint = wet ? 0xffffff : 0x8a96a3;

    const beam = s.lowBeamOn === true;
    this.beams.clear();
    if (beam) {
      for (const x of [-33, 33]) this.beams.poly([x - 8, -108, x + 8, -108, x + 40, -230, x - 40, -230]);
      this.beams.fill({ color: color.beam, alpha: 0.16 });
    }

    const brake = s.brakePressed === true;
    const tail = brake ? color.brake : s.tailLampOn === true ? color.tail : color.off;
    this.lamps.clear()
      .roundRect(-44, -108, 22, 8, 3).roundRect(22, -108, 22, 8, 3).fill({ color: beam ? color.beam : color.off })
      .roundRect(-44, 100, 22, 8, 3).roundRect(22, 100, 22, 8, 3).fill({ color: tail });
    this.brakeGlow.clear();
    if (brake) this.brakeGlow.ellipse(0, 112, 70, 22).fill({ color: color.brake, alpha: 0.35 });

    this.gear.text = s.gear === "UNKNOWN" ? "-" : s.gear;
  }
}

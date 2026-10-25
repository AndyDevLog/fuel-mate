/**
 * 왼쪽 패널(DOM). 도로 지도에서는 진행 방향 앞 알림, 내 차 화면에서는 차량 신호와 내가 보내는 신호를 보여준다. 글자는 DOM이 맡는다: 한글 글꼴·선명도를 시스템이 처리하고, 모델이 올 때만 바뀐다.
 */
import { alertColor, cssColor, palette } from "../map/palette.ts";
import { segmentColor } from "../map/RoadMap.ts";
import type { RenderModel } from "../render-model.ts";

export class Panel {
  model: RenderModel | null = null;

  private readonly alerts: HTMLOListElement;
  private readonly empty: HTMLElement;
  private readonly status: HTMLElement;
  private readonly myCar: HTMLElement;
  private readonly notice: HTMLElement;
  private readonly road: HTMLElement;
  private readonly car: HTMLElement;
  private readonly carRows: HTMLElement;
  private readonly sending: HTMLElement;
  private readonly legend: HTMLElement;
  private readonly bar: HTMLElement;
  private readonly ticks: HTMLElement;
  private readonly stripNote: HTMLElement;

  private readonly root: HTMLElement;

  constructor(root: HTMLElement) {
    this.root = root;
    this.alerts = root.querySelector("#alerts")!;
    this.empty = root.querySelector("#empty")!;
    this.status = root.querySelector("#status")!;
    this.myCar = root.querySelector("#mycar")!;
    this.notice = document.getElementById("notice")!;
    this.road = root.querySelector("#road")!;
    this.car = root.querySelector("#car")!;
    this.carRows = root.querySelector("#car-rows")!;
    this.sending = root.querySelector("#sending")!;
    this.legend = root.querySelector("#legend")!;
    this.bar = root.querySelector("#bar")!;
    this.ticks = root.querySelector("#ticks")!;
    this.stripNote = root.querySelector("#strip-note")!;
    this.drawLegend(this.legend);
  }

  get width(): number { return this.root.offsetWidth; }

  update(m: RenderModel): void {
    this.model = m;
    const onMap = m.screen === "MAP";
    // 주행 중에는 패널을 통째로 숨긴다(지도와 위쪽 네이티브 배너만)
    this.root.hidden = m.screen === "DRIVE";
    this.road.hidden = !onMap;
    this.car.hidden = onMap;
    this.myCar.hidden = !onMap;
    this.legend.hidden = !onMap;
    this.updateCar(m);
    this.updateStrip(m);

    this.alerts.replaceChildren(...m.alerts.map((a) => {
      const li = document.createElement("li");
      li.style.setProperty("--c", cssColor(alertColor(a.kind)));
      const dist = document.createElement("b");
      dist.textContent = a.headline;
      const label = document.createElement("span");
      label.textContent = a.label;
      li.append(dist, label);
      const extra = [a.lengthText, a.reportersText].filter((t): t is string => t !== null);
      for (const t of extra) {
        const small = document.createElement("small");
        small.textContent = t;
        li.append(small);
      }
      return li;
    }));
    this.empty.hidden = m.alerts.length > 0 || m.center === null;
    this.empty.textContent = m.online ? "앞쪽 도로 이상 없음" : "도로 정보를 받지 못하고 있습니다";

    const car = m.myCar;
    this.myCar.replaceChildren(
      chip("와이퍼", car.wiperOn, ["", "약", "중", "강"][car.rainLevel] ?? ""),
      chip("하향등", car.lowBeamOn),
      chip("브레이크", car.brakePressed),
    );

    this.status.textContent = m.online ? "실시간 연결됨" : "오프라인: 도로 정보를 받지 못하고 있습니다";
    this.status.classList.toggle("offline", !m.online);

    this.notice.hidden = m.center !== null || !onMap;
    this.notice.textContent = "위치 수신 전";
  }

  /** 앞 5km 막대. 구간 위치는 앞길 거리 그대로다(지도 색칠·알림 거리와 같다). 비는 넓게, 어두움·급정거는 오른쪽 띠로 겹친다. */
  private updateStrip(m: RenderModel): void {
    const total = m.ahead.radiusM;
    const pct = (d: number) => `${Math.min(100, Math.max(0, (d / total) * 100))}%`;
    const segs = m.route?.segments ?? [];
    const order = ["RAIN", "DARK", "HARD_BRAKE"];
    this.bar.replaceChildren(...[...segs].sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind)).map((s) => {
      const d = document.createElement("div");
      d.className = s.kind === "RAIN" ? "seg" : "seg thin";
      d.style.setProperty("--c", cssColor(segmentColor(s)));
      d.style.bottom = pct(s.fromM);
      d.style.height = `max(4px, ${pct(s.toM - s.fromM)})`;
      return d;
    }));
    const marks = [0, 1_000, 2_000, 3_000, 4_000, 5_000].filter((d) => d <= total);
    const starts = new Set(segs.map((s) => Math.round(s.fromM / 1_000)));
    this.ticks.replaceChildren(...marks.map((d) => {
      const t = document.createElement("span");
      t.style.bottom = pct(d);
      t.textContent = d === 0 ? "내 차" : `${d / 1_000}km`;
      t.classList.toggle("hit", d > 0 && starts.has(d / 1_000));
      return t;
    }));
    this.stripNote.hidden = !(m.route?.estimated ?? false);
  }

  private updateCar(m: RenderModel): void {
    const c = m.myCar;
    const onOff = (b: boolean | null) => (b === true ? "켬" : b === false ? "끔" : "-");
    const rain = ["", "약", "중", "강"][c.rainLevel] ?? "세기 모름";
    const rows: [string, string][] = [
      ["와이퍼", c.wiperOn === true ? `켬 · ${c.wiperText ?? "-"}` : onOff(c.wiperOn)],
      ["하향등", onOff(c.lowBeamOn)],
      ["미등", onOff(c.tailLampOn)],
      ["브레이크", c.brakePressed === true ? "밟음" : c.brakePressed === false ? "뗌" : "-"],
      ["기어", c.gear === "UNKNOWN" ? "-" : c.gear],
      ["경고등", c.telltales === null ? "사용할 수 없음" : c.telltales.length === 0 ? "없음" : c.telltales.join(", ")],
    ];
    this.carRows.replaceChildren(...rows.flatMap(([k, v]) => {
      const dt = document.createElement("dt");
      dt.textContent = k;
      const dd = document.createElement("dd");
      dd.textContent = v;
      return [dt, dd];
    }));

    const signals: string[] = [];
    if (c.rainLevel !== 0) signals.push(c.rainLevel === 9 ? "비 (세기 모름)" : `비 (${rain})`);
    if (c.lowBeamOn === true) signals.push("어두움");
    this.sending.textContent =
      `${signals.length > 0 ? `${signals.join(" · ")} 신호를 보내는 중` : "이상 없음 신호를 보내는 중"}` +
      ` · 급정거 감지 ${c.hardBrakes}회`;
  }

  fatal(message: string): void {
    this.notice.hidden = false;
    this.notice.textContent = message;
  }

  private drawLegend(el: HTMLElement): void {
    const item = (color: string, text: string, shape = "") => {
      const s = document.createElement("span");
      s.className = `key ${shape}`;
      s.style.setProperty("--c", color);
      s.textContent = text;
      return s;
    };
    // 지도에서는 앞길 위 색 선으로 보이므로 범례도 선 모양으로 둔다
    el.replaceChildren(
      item(cssColor(palette.rain[1]), "비 약", "line"),
      item(cssColor(palette.rain[2]), "중", "line"),
      item(cssColor(palette.rain[3]), "강", "line"),
      item(cssColor(palette.dark), "어두움", "line"),
      item(cssColor(palette.brake), "급정거", "line"),
    );
  }
}

function chip(name: string, on: boolean | null, extra = ""): HTMLElement {
  const s = document.createElement("span");
  s.className = `chip ${on === true ? "on" : on === false ? "off" : "unknown"}`;
  s.textContent = `${name} ${on === true ? "켬" : on === false ? "끔" : "-"}${on && extra ? ` (${extra})` : ""}`;
  return s;
}

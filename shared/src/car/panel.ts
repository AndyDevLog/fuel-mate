/**
 * 왼쪽 알림 패널(DOM). 글자는 DOM이 맡는다: 한글 글꼴·선명도를 시스템이 처리하고, 모델이 올 때만 바뀐다.
 */
import { alertColor, cssColor, palette } from "../map/palette.ts";
import type { RenderModel } from "../render-model.ts";

export class Panel {
  model: RenderModel | null = null;

  private readonly alerts: HTMLOListElement;
  private readonly empty: HTMLElement;
  private readonly status: HTMLElement;
  private readonly myCar: HTMLElement;
  private readonly notice: HTMLElement;

  private readonly root: HTMLElement;

  constructor(root: HTMLElement) {
    this.root = root;
    this.alerts = root.querySelector("#alerts")!;
    this.empty = root.querySelector("#empty")!;
    this.status = root.querySelector("#status")!;
    this.myCar = root.querySelector("#mycar")!;
    this.notice = document.getElementById("notice")!;
    this.drawLegend(root.querySelector("#legend")!);
  }

  get width(): number { return this.root.offsetWidth; }

  update(m: RenderModel): void {
    this.model = m;

    this.alerts.replaceChildren(...m.alerts.map((a) => {
      const li = document.createElement("li");
      li.style.setProperty("--c", cssColor(alertColor(a.kind)));
      const dist = document.createElement("b");
      dist.textContent = `약 ${a.distanceText} 앞`;
      const label = document.createElement("span");
      label.textContent = a.label;
      li.append(dist, label);
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

    this.status.textContent = `${m.online ? "실시간 연결됨" : "오프라인"} · 주변 격자 ${m.cells.length}개`;
    this.status.classList.toggle("offline", !m.online);

    this.notice.hidden = m.center !== null;
    this.notice.textContent = "위치 수신 전";
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
    el.replaceChildren(
      item(cssColor(palette.rain[1]), "비 약"),
      item(cssColor(palette.rain[2]), "중"),
      item(cssColor(palette.rain[3]), "강"),
      item(cssColor(palette.dark), "어두움", "ring"),
      item(cssColor(palette.brake), "급정거", "dot"),
      item(cssColor(palette.clear), "이상 없음"),
    );
  }
}

function chip(name: string, on: boolean | null, extra = ""): HTMLElement {
  const s = document.createElement("span");
  s.className = `chip ${on === true ? "on" : on === false ? "off" : "unknown"}`;
  s.textContent = `${name} ${on === true ? "켬" : on === false ? "끔" : "-"}${on && extra ? ` (${extra})` : ""}`;
  return s;
}

/**
 * 렌더 모델 v1. 차량 앱 :core의 RenderModel(present/RenderModel.kt)을 RenderModelJson으로 보낸 형식과 같다.
 * 원본 좌표(center, destination)는 같은 기기 안의 WebView에만 오고 서버로 가지 않는다.
 */

export const RENDER_MODEL_VERSION = 1;

/** 0=없음, 1=약, 2=중, 3=강, 9=미상(AUTO). 서버 프로토콜과 같은 값. */
export type RainLevel = 0 | 1 | 2 | 3 | 9;

export interface LatLon { lat: number; lon: number }

export interface CellView {
  /** geohash 7자리 */
  cell: string;
  /** 와이퍼를 켠 차 비율 0~1. 비가 없으면 0 */
  rain: number;
  rainLevel: RainLevel;
  /** 하향등 비율 0~1 */
  dark: number;
  /** 급정거 강도 0~1 (15분 급정거 수 / 5) */
  brake: number;
}

export type AlertKind = "RAIN" | "DARK" | "HARD_BRAKE";

export interface AlertView {
  kind: AlertKind;
  cell: string;
  distanceM: number;
  /** "900m", "1.2km" */
  distanceText: string;
  /** "비 (중)", "어두운 구간", "급정거 잦음" */
  label: string;
  /** "약 1km 앞", 이미 구간 안이면 "지금 여기" */
  headline: string;
  /** 내 차가 이미 이 구간 안에 있다 */
  here: boolean;
  /** 구간의 모든 격자(가까운 순). 첫 번째가 cell */
  cells: string[];
  /** "400m 구간", 구간 안이면 "약 4.9km 계속". 짧은 구간(200m 미만)은 null */
  lengthText: string | null;
}

export type Gear = "P" | "R" | "N" | "D" | "UNKNOWN";

/** 내 차 상태. null = 아직 못 받음 */
export interface MyCarView {
  wiperOn: boolean | null;
  /** "연속 3", "간헐 2", "자동" */
  wiperText: string | null;
  rainLevel: RainLevel;
  lowBeamOn: boolean | null;
  tailLampOn: boolean | null;
  brakePressed: boolean | null;
  gear: Gear;
  /** null = 경고등 API를 쓸 수 없음, [] = 켜진 경고등 없음 */
  telltales: string[] | null;
  /** 이번 실행에서 내 차가 감지해 보낸 급정거 수 */
  hardBrakes: number;
}

export interface RenderModel {
  v: number;
  screen: "MAP" | "MY_CAR";
  center: LatLon | null;
  /** 진행 방향(북 0°, 시계 방향) */
  heading: number;
  destination: LatLon | null;
  ahead: { radiusM: number; halfAngleDeg: number; corridorHalfWidthM: number };
  cells: CellView[];
  alerts: AlertView[];
  myCar: MyCarView;
  online: boolean;
}

/**
 * 차량 ↔ 서버 프로토콜 v1 (docs/architecture.md 6-2).
 * 관제 웹을 만들 때 shared/로 옮겨서 같이 쓴다.
 */

export const PROTOCOL_VERSION = 1;

/** geohash 7자리(약 150m). 차는 원본 좌표 대신 이것만 보낸다. */
export type CellId = string;
/** geohash 5자리(약 4.9km). 구독 단위. */
export type TileId = string;
/** 유닉스 분(분 단위로 뭉갠 시각). */
export type UnixMinute = number;

/** 0=꺼짐, 1=약, 2=중, 3=강, 9=미상(AUTO) */
export type RainLevel = 0 | 1 | 2 | 3 | 9;

export type Observation =
  | { cell: CellId; t: UnixMinute; kind: "rain"; level: RainLevel }
  | { cell: CellId; t: UnixMinute; kind: "dark"; on: boolean }
  | { cell: CellId; t: UnixMinute; kind: "brake"; dir: number };

export type Source = "car" | "sim";

/** POST /v1/observations */
export interface UploadRequest {
  v: number;
  /** 임시 ID. 시동마다, 30분마다 새로 만든다. */
  tid: string;
  src: Source;
  obs: Observation[];
}

export interface UploadResponse {
  accepted: number;
  rejected: number;
}

/** 격자 하나의 집계. k(=3)대 이상이 지나간 격자만 배포한다. */
export interface CellState {
  cell: CellId;
  /** 최근 창(15분) 동안 이 격자를 지난 서로 다른 차 수 */
  n: number;
  /** 와이퍼를 켠 차 비율 0~1 */
  rain: number;
  /** 켠 차들의 강도 중앙값 */
  rainLevel: RainLevel;
  /** 하향등을 켠 차 비율 0~1 */
  dark: number;
  /** 최근 창의 급정거 수 */
  brake: number;
  /** 시뮬레이터 관측 비율 0~1 (관제 웹에서 구분 표시용) */
  sim: number;
  updatedAt: UnixMinute;
}

/** 차 → 서버 (WebSocket) */
export interface SubscribeMessage {
  type: "subscribe";
  tiles: TileId[];
}

/** 서버 → 차 (WebSocket) */
export interface CellsMessage {
  type: "cells";
  updated: CellState[];
  expired: CellId[];
}

// ---- 입력 검증용 JSON 스키마 (Fastify가 요청을 받기 전에 검사한다) ----

const GEOHASH = "^[0-9b-hjkmnp-z]+$";

export const uploadSchema = {
  type: "object",
  required: ["v", "tid", "src", "obs"],
  additionalProperties: false,
  properties: {
    v: { const: PROTOCOL_VERSION },
    tid: { type: "string", minLength: 4, maxLength: 64 },
    src: { enum: ["car", "sim"] },
    obs: {
      type: "array",
      maxItems: 500,
      items: {
        type: "object",
        required: ["cell", "t", "kind"],
        additionalProperties: false,
        properties: {
          cell: { type: "string", minLength: 7, maxLength: 7, pattern: GEOHASH },
          t: { type: "integer", minimum: 0 },
          kind: { enum: ["rain", "dark", "brake"] },
          level: { enum: [0, 1, 2, 3, 9] },
          on: { type: "boolean" },
          dir: { type: "integer", minimum: 0, maximum: 7 },
        },
        // 종류별 필수 필드
        allOf: [
          { if: { properties: { kind: { const: "rain" } } }, then: { required: ["level"] } },
          { if: { properties: { kind: { const: "dark" } } }, then: { required: ["on"] } },
          { if: { properties: { kind: { const: "brake" } } }, then: { required: ["dir"] } },
        ],
      },
    },
  },
} as const;

export function isSubscribeMessage(x: unknown): x is SubscribeMessage {
  if (typeof x !== "object" || x === null) return false;
  const m = x as Record<string, unknown>;
  return m.type === "subscribe" &&
    Array.isArray(m.tiles) &&
    m.tiles.length <= 25 &&
    m.tiles.every((t) => typeof t === "string" && /^[0-9b-hjkmnp-z]{5}$/.test(t));
}

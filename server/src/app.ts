import Fastify, { type FastifyInstance } from "fastify";
import websocket from "@fastify/websocket";
import type { WebSocket } from "ws";
import { Aggregator, tileOf } from "./aggregator.ts";
import {
  type CellsMessage, type TileId, type UnixMinute, type UploadRequest, isSubscribeMessage, uploadSchema,
} from "./protocol.ts";

export interface AppOptions {
  aggregator?: Aggregator;
  /** 현재 시각(유닉스 분). 테스트에서 바꿔 끼운다. */
  nowMin?: () => UnixMinute;
  logger?: boolean;
}

export interface RoadSensorServer {
  app: FastifyInstance;
  aggregator: Aggregator;
  /** 창 정리 + 바뀐 격자를 구독자에게 보낸다. 운영에서는 1초마다 부른다. */
  tick(): void;
}

export const unixMinute = (): UnixMinute => Math.floor(Date.now() / 60_000);

export async function buildServer(options: AppOptions = {}): Promise<RoadSensorServer> {
  const aggregator = options.aggregator ?? new Aggregator();
  const nowMin = options.nowMin ?? unixMinute;
  const app = Fastify({
    // 로그에는 메서드·경로(쿼리 제외)·상태만 남긴다. 요청 본문(임시 ID, 격자), 차의 IP, 서버 호스트 이름은 남기지 않는다.
    logger: options.logger
      ? {
          base: null,
          serializers: {
            req: (req: { method: string; url: string }) => ({ method: req.method, path: req.url.split("?")[0] }),
            res: (res: { statusCode: number }) => ({ status: res.statusCode }),
          },
        }
      : false,
    // 모르는 필드는 지우지 않고 거부한다. 차가 실수로 원본 좌표를 보내면 조용히 넘어가지 않고 400으로 드러나게.
    ajv: { customOptions: { removeAdditional: false } },
  });
  await app.register(websocket);

  const subscribers = new Map<WebSocket, Set<TileId>>();

  app.get("/health", async () => ({ ok: true, cells: aggregator.publishedCount, subscribers: subscribers.size }));

  app.post<{ Body: UploadRequest }>("/v1/observations", { schema: { body: uploadSchema } }, async (req, reply) => {
    const result = aggregator.ingest(req.body, nowMin());
    return reply.code(202).send(result);
  });

  // 디버그·관제용 스냅샷: GET /v1/cells?tiles=wydku,wydks
  app.get<{ Querystring: { tiles?: string } }>("/v1/cells", async (req) => {
    const tiles = (req.query.tiles ?? "").split(",").filter((t) => /^[0-9b-hjkmnp-z]{5}$/.test(t));
    return { type: "cells", updated: aggregator.snapshot(tiles), expired: [] } satisfies CellsMessage;
  });

  app.get("/v1/stream", { websocket: true }, (socket) => {
    subscribers.set(socket, new Set());
    socket.on("message", (raw) => {
      let msg: unknown;
      try { msg = JSON.parse(raw.toString()); } catch { return; }
      if (!isSubscribeMessage(msg)) return;
      const tiles = new Set(msg.tiles);
      subscribers.set(socket, tiles);
      send(socket, { type: "cells", updated: aggregator.snapshot(tiles), expired: [] });
    });
    socket.on("close", () => subscribers.delete(socket));
  });

  function tick(): void {
    aggregator.prune(nowMin());
    const { updated, expired } = aggregator.flush();
    if (updated.length === 0 && expired.length === 0) return;
    for (const [socket, tiles] of subscribers) {
      const msg: CellsMessage = {
        type: "cells",
        updated: updated.filter((s) => tiles.has(tileOf(s.cell))),
        expired: expired.filter((c) => tiles.has(tileOf(c))),
      };
      if (msg.updated.length > 0 || msg.expired.length > 0) send(socket, msg);
    }
  }

  return { app, aggregator, tick };
}

function send(socket: WebSocket, msg: CellsMessage): void {
  if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(msg));
}

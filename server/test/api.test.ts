import { afterEach, describe, expect, it } from "vitest";
import { buildServer, type RoadSensorServer } from "../src/app.ts";
import type { CellsMessage, UploadRequest } from "../src/protocol.ts";

const NOW = 29_000_000;
let server: RoadSensorServer;

afterEach(async () => {
  await server?.app.close();
});

const body = (tid: string, level = 2): UploadRequest => ({
  v: 1, tid, src: "car", obs: [{ cell: "wydku4g", t: NOW, kind: "rain", level: level as 2 }],
});

describe("POST /v1/observations", () => {
  it("정상 업로드는 202와 처리 개수를 돌려준다", async () => {
    server = await buildServer({ nowMin: () => NOW });
    const res = await server.app.inject({ method: "POST", url: "/v1/observations", payload: body("car-a") });
    expect(res.statusCode).toBe(202);
    expect(res.json()).toEqual({ accepted: 1, rejected: 0 });
  });

  it.each([
    ["원본 좌표 같은 모르는 필드", { ...body("car-a"), lat: 37.4 }],
    ["관측 안에 원본 좌표", { ...body("car-a"), obs: [{ cell: "wydku4g", t: NOW, kind: "rain", level: 1, lat: 37.4 }] }],
    ["임시 ID가 너무 짧음", body("a")],
    ["격자가 7자리가 아님", { ...body("car-a"), obs: [{ cell: "wydku", t: NOW, kind: "rain", level: 1 }] }],
    ["geohash에 없는 문자", { ...body("car-a"), obs: [{ cell: "wydku4a", t: NOW, kind: "rain", level: 1 }] }],
    ["비 관측에 level이 없음", { ...body("car-a"), obs: [{ cell: "wydku4g", t: NOW, kind: "rain" }] }],
    ["프로토콜 버전이 다름", { ...body("car-a"), v: 2 }],
  ])("잘못된 요청은 400: %s", async (_name, payload) => {
    server = await buildServer({ nowMin: () => NOW });
    const res = await server.app.inject({ method: "POST", url: "/v1/observations", payload });
    expect(res.statusCode).toBe(400);
  });
});

describe("WS /v1/stream", () => {
  it("구독하면 스냅샷을 받고, 이후 바뀐 격자를 받는다", async () => {
    server = await buildServer({ nowMin: () => NOW });
    const { app, tick } = server;

    // 구독 전에 3대가 이미 지나간 격자
    for (const tid of ["car-a", "car-b", "car-c"]) await app.inject({ method: "POST", url: "/v1/observations", payload: body(tid) });
    tick();

    await app.ready();
    const ws = await app.injectWS("/v1/stream");
    const messages: CellsMessage[] = [];
    const next = () => new Promise<CellsMessage>((resolve) => ws.once("message", (d) => resolve(JSON.parse(d.toString()))));

    let pending = next();
    ws.send(JSON.stringify({ type: "subscribe", tiles: ["wydku"] }));
    messages.push(await pending);
    expect(messages[0].updated).toHaveLength(1);
    expect(messages[0].updated[0]).toMatchObject({ cell: "wydku4g", n: 3, rainLevel: 2 });

    // 한 대가 더 와서 값이 바뀐다
    pending = next();
    await app.inject({ method: "POST", url: "/v1/observations", payload: body("car-d", 0) });
    tick();
    messages.push(await pending);
    expect(messages[1].updated[0]).toMatchObject({ n: 4, rain: 0.75 });

    ws.terminate();
  });

  it("구독하지 않은 타일의 변화는 보내지 않는다", async () => {
    server = await buildServer({ nowMin: () => NOW });
    const { app, tick } = server;
    await app.ready();
    const ws = await app.injectWS("/v1/stream");
    const got: string[] = [];
    ws.on("message", (d) => got.push(d.toString()));
    ws.send(JSON.stringify({ type: "subscribe", tiles: ["wydks"] }));
    await new Promise((r) => setTimeout(r, 50));

    for (const tid of ["car-a", "car-b", "car-c"]) await app.inject({ method: "POST", url: "/v1/observations", payload: body(tid) });
    tick();
    await new Promise((r) => setTimeout(r, 50));

    // 첫 메시지는 빈 스냅샷, 그다음은 없어야 한다
    expect(got).toHaveLength(1);
    expect(JSON.parse(got[0]).updated).toEqual([]);
    ws.terminate();
  });
});

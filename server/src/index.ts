import { buildServer } from "./app.ts";

const PORT = Number(process.env.PORT ?? 8080);
const HOST = process.env.HOST ?? "127.0.0.1";
const TICK_MS = 1_000;

const { app, tick } = await buildServer({ logger: true });
const timer = setInterval(tick, TICK_MS);

await app.listen({ port: PORT, host: HOST });
// 에뮬레이터에서는 adb reverse tcp:8080 tcp:8080 으로 localhost:8080에 붙는다

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, async () => {
    clearInterval(timer);
    await app.close();
    process.exit(0);
  });
}

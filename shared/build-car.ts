/**
 * 차량 앱 WebView용 번들: src/car → android/app/src/main/assets/web/{index.html, map.js}
 * 외부 CDN 없이 오프라인으로 동작해야 해서 Pixi까지 한 파일로 묶는다.
 * file:// 페이지는 ES 모듈 스크립트를 못 읽으므로(CORS) IIFE로 낸다.
 *
 *   npm run build:car            한 번 빌드
 *   npm run watch:car            고칠 때마다 다시 빌드
 */
import { copyFileSync, mkdirSync } from "node:fs";
import * as esbuild from "esbuild";

const outdir = new URL("../android/app/src/main/assets/web/", import.meta.url).pathname;
const watch = process.argv.includes("--watch");

mkdirSync(outdir, { recursive: true });
copyFileSync(new URL("src/car/index.html", import.meta.url).pathname, `${outdir}index.html`);

const options: esbuild.BuildOptions = {
  entryPoints: [new URL("src/car/main.ts", import.meta.url).pathname],
  outfile: `${outdir}map.js`,
  bundle: true,
  format: "iife",
  // Pleos WebView는 Chromium 131
  target: "chrome131",
  minify: !watch,
  sourcemap: false,
  legalComments: "eof",
  logLevel: "info",
};

if (watch) await (await esbuild.context(options)).watch();
else await esbuild.build(options);

/**
 * 기상청 초단기실황(공공데이터포털 "기상청_단기예보 조회서비스" getUltraSrtNcst) 받기.
 * 5km 격자 한 점마다 한 번 호출한다. 개발계정 하루 10,000건, 자동승인(2026-10-24 포털 안내 기준).
 *
 * 인증키는 환경 변수 KMA_SERVICE_KEY(포털의 "일반 인증키(Decoding)")로만 받는다. 키가 들어간 URL은 로그에 남기지 않는다.
 * 확인 필요: 응답 형식은 포털 공통 형식(response.header / response.body.items.item)으로 썼다. 키를 받으면 실제 응답으로 확인한다.
 */

const ENDPOINT = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";

export interface GridRain { nx: number; ny: number; rn1: number }

/** 실황 발표 시각. 매시 정각 자료가 그 시각 40분쯤부터 조회되므로, 45분 전이면 한 시간 전 자료를 쓴다. */
export function baseTimeOf(now: Date): { baseDate: string; baseTime: string } {
  const kst = new Date(now.getTime() + 9 * 3_600_000 - (now.getUTCMinutes() < 45 ? 3_600_000 : 0));
  const p = (n: number) => String(n).padStart(2, "0");
  return {
    baseDate: `${kst.getUTCFullYear()}${p(kst.getUTCMonth() + 1)}${p(kst.getUTCDate())}`,
    baseTime: `${p(kst.getUTCHours())}00`,
  };
}

/** RN1(1시간 강수량, mm) 값. "1mm 미만"은 0.5, 숫자가 아닌 그 밖의 표현은 0으로 본다. */
export function parseRn1(value: string): number {
  // "1mm 미만"은 parseFloat가 1로 읽으므로 먼저 본다
  if (value.includes("미만")) return 0.5;
  const n = Number.parseFloat(value);
  return Number.isFinite(n) ? Math.max(0, n) : 0;
}

interface NcstResponse {
  response?: {
    header?: { resultCode?: string; resultMsg?: string };
    body?: { items?: { item?: { category: string; obsrValue: string }[] } };
  };
}

/** 응답 JSON에서 RN1을 꺼낸다. 오류 응답이면 예외(메시지에 키는 넣지 않는다). */
export function rn1FromResponse(json: NcstResponse): number {
  const header = json.response?.header;
  if (header?.resultCode !== "00") throw new Error(`초단기실황 오류: ${header?.resultCode ?? "?"} ${header?.resultMsg ?? ""}`.trim());
  const item = json.response?.body?.items?.item?.find((i) => i.category === "RN1");
  if (!item) throw new Error("초단기실황 응답에 RN1이 없다");
  return parseRn1(item.obsrValue);
}

export async function fetchRn1(key: string, nx: number, ny: number, baseDate: string, baseTime: string): Promise<number> {
  const q = new URLSearchParams({
    ServiceKey: key, pageNo: "1", numOfRows: "20", dataType: "JSON",
    base_date: baseDate, base_time: baseTime, nx: String(nx), ny: String(ny),
  });
  const res = await fetch(`${ENDPOINT}?${q}`);
  const text = await res.text();
  if (!res.ok) throw new Error(`초단기실황 HTTP ${res.status}`);
  let json: NcstResponse;
  try { json = JSON.parse(text) as NcstResponse; } catch {
    // 키 오류 등은 JSON을 요청해도 XML로 온다
    const code = /<returnReasonCode>(\d+)<\/returnReasonCode>/.exec(text)?.[1];
    const msg = /<returnAuthMsg>([^<]+)<\/returnAuthMsg>/.exec(text)?.[1];
    throw new Error(`초단기실황 응답이 JSON이 아님${code ? ` (${code} ${msg ?? ""})` : ""}`);
  }
  return rn1FromResponse(json);
}

/** 앞길 꺾은선 계산(Pixi 없음, Node 테스트 가능). */

/** 꺾은선의 [from, to] 거리 구간(같은 단위)만 잘라 낸다. */
export function slice(pts: [number, number][], cum: number[], from: number, to: number): [number, number][] {
  const at = (d: number): [number, number] => {
    let i = 0;
    while (i < cum.length - 2 && cum[i + 1] < d) i++;
    const seg = cum[i + 1] - cum[i];
    const t = seg === 0 ? 0 : Math.min(1, Math.max(0, (d - cum[i]) / seg));
    return [pts[i][0] + (pts[i + 1][0] - pts[i][0]) * t, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * t];
  };
  const end = Math.min(to, cum[cum.length - 1]);
  if (end <= from) return [];
  const out: [number, number][] = [at(from)];
  for (let i = 1; i < pts.length - 1; i++) if (cum[i] > from && cum[i] < end) out.push(pts[i]);
  out.push(at(end));
  return out;
}

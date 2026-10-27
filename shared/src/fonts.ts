/**
 * 지도 캔버스(Pixi) 글자의 글꼴. 패널(DOM)과 같은 "Vehicle Sans"를 먼저 쓴다.
 * "Vehicle Sans"는 index.html의 @font-face다: 앱이 차량 기본 글꼴 파일(Pleos: Asta Sans)을 넘겨준다
 * (android WebMapRenderer·SystemSansFont). 못 받으면 다음 글꼴로 넘어간다.
 */
export const FONT_FAMILIES = ["Vehicle Sans", "Noto Sans CJK KR", "sans-serif"];

/**
 * 캔버스 글자는 처음 그릴 때의 글꼴로 굳으므로, 첫 프레임 전에 차량 글꼴을 불러 둔다.
 * 파일을 못 받으면(차량 밖 브라우저 등) 바로 실패하고, 그대로 넘어가 다음 글꼴로 그린다.
 */
export async function loadVehicleFont(): Promise<void> {
  await Promise.allSettled([400, 700].map((w) => document.fonts.load(`${w} 16px "Vehicle Sans"`)));
}

package io.roadsensor.ui

import io.roadsensor.core.geo.Geohash
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.RainLevel
import io.roadsensor.core.present.DriveLine
import kotlin.math.roundToInt

/**
 * 렌더러가 붙기 전까지 정차 화면에 보여주는 상태 요약.
 * 원본 좌표 대신 격자 ID를 보여준다(서버에 보내는 것과 같은 단위).
 */
object StatusText {

    fun of(s: AppState): String = buildString {
        val v = s.vehicle
        appendLine("■ 내 차")
        appendLine("  와이퍼    ${onOff(v.wiperOn)}  ${v.wiperSwitch ?: "-"}  → 비 ${rain(s.rainLevel)}")
        appendLine("  하향등    ${onOff(v.lowBeamOn)}   미등 ${onOff(v.tailLampOn)}")
        appendLine("  브레이크  ${onOff(v.brakePressed)}")
        appendLine("  속도      ${v.speedMps?.let { "${(it * 3.6f).roundToInt()} km/h" } ?: "권한 없음 또는 수신 전"}   기어 ${v.gear}")
        appendLine("  경고등    ${v.telltales?.let { list -> if (list.isEmpty()) "없음" else list.joinToString { "${it.type}:${it.state}" } } ?: "사용 불가"}")
        appendLine()
        appendLine("■ 주행 규제   ${if (s.drive.restricted) "주행 중 (제한 ${s.drive.activeRestrictions})" else "정차"}")
        appendLine()
        appendLine("■ 위치")
        appendLine("  격자      ${s.location?.let { Geohash.encode(it.point) } ?: "수신 전"}  (${s.location?.origin ?: "-"})")
        appendLine("  방향      ${s.location?.headingDeg?.roundToInt()?.let { "$it°" } ?: "-"}")
        appendLine("  목적지    ${if (s.destination != null) "있음 (격자 ${Geohash.encode(s.destination!!)})" else "없음"}")
        appendLine()
        val upload = when (s.link.uploadOk) { true -> "정상"; false -> "실패"; null -> "대기" }
        appendLine("■ 서버   업로드 $upload (대기 ${s.link.pendingUploads}개)   지도 구독 ${if (s.link.feedConnected) "연결됨" else "끊김"}")
        appendLine()
        appendLine("■ 도로 (주변 격자 ${s.cells.size}개)")
        if (s.alerts.isEmpty()) appendLine("  진행 방향 앞 이상 없음")
        s.alerts.forEach { appendLine("  ${kind(it.kind)}  약 ${it.distanceM}m  ${it.cell}") }
        appendLine()
        appendLine("■ 기여   급정거 감지 ${s.contribution.hardBrakes}회")
        appendLine()
        append("주행 모드 문구 미리보기: ${DriveLine.of(s)}")
    }

    private fun onOff(b: Boolean?) = when (b) { true -> "ON "; false -> "OFF"; null -> " - " }

    private fun rain(l: RainLevel) = when (l) {
        RainLevel.NONE -> "없음"; RainLevel.LIGHT -> "약"; RainLevel.MODERATE -> "중"
        RainLevel.HEAVY -> "강"; RainLevel.UNKNOWN -> "세기 미상"
    }

    private fun kind(k: AlertKind) = when (k) {
        AlertKind.RAIN -> "비    "; AlertKind.DARK -> "어두움"; AlertKind.HARD_BRAKE -> "급정거"
    }
}

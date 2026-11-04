package io.fuelmate.ui

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.LocationOrigin
import io.fuelmate.core.model.Trip
import kotlin.math.roundToInt

/** 뼈대 단계의 상태 요약. 기능 화면이 생기면 개발용으로만 남긴다. */
object StatusText {

    fun of(s: AppState): String = buildString {
        appendLine("Fuel Mate")
        appendLine()
        appendLine("주행: ${if (s.drive.restricted) "주행 중" else "정차"}")
        appendLine("기어: ${s.vehicle.gear.takeIf { it != Gear.UNKNOWN }?.name ?: "모름"}")
        appendLine("속도: ${s.vehicle.speedMps?.let { "${(it * 3.6f).toInt()} km/h" } ?: "모름"}")
        appendLine("브레이크: ${when (s.vehicle.brakePressed) { true -> "밟음"; false -> "뗌"; null -> "모름" }}")
        appendLine("에너지: ${energyLine(s)}")
        s.energy?.let { e ->
            appendLine("이번 실행 사용: ${"%.2f".format(s.energyMeter.used)} ${e.kind.unit}" +
                if (s.energyMeter.added > 0) ", 충전·주유 ${"%.2f".format(s.energyMeter.added)} ${e.kind.unit}" else "")
        }
        appendLine("이번 실행 주행 거리: ${"%.1f".format(s.odometer.meters / 1000)} km")
        appendLine("위치: ${s.location?.origin?.let { if (it == LocationOrigin.DEMO_TRACK) "데모 경로" else "내비" } ?: "모름"}")
        appendLine()
        appendLine("지금 운행: ${s.trip?.let { tripLine(it) } ?: "없음(P)"}")
        appendLine("지난 운행: ${s.trips.firstOrNull()?.let { tripLine(it) } ?: "없음"}")
        appendLine("저장된 운행: ${s.trips.size}개")
    }

    private fun tripLine(t: Trip): String {
        val unit = t.energyKind?.unit ?: ""
        return "${"%.1f".format(t.distanceM / 1000)}km · ${t.durationMs / 60_000}분 ${t.durationMs / 1000 % 60}초 · " +
            "사용 ${"%.2f".format(t.energyUsed)}$unit · " +
            "급가속 ${t.count<DriveEvent.HardAccel>()} 급감속 ${t.count<DriveEvent.HardDecel>()} " +
            "급제동 ${t.count<DriveEvent.HardBrake>()} 긴 정차 ${t.count<DriveEvent.LongIdle>()}"
    }

    private fun energyLine(s: AppState): String {
        val e = s.energy ?: return "모름"
        val origin = if (e.origin == EnergyOrigin.DEMO) " (데모)" else ""
        val percent = e.percent?.let { "${it.roundToInt()}%" } ?: "?%"
        val amount = e.amount?.let { "%.1f".format(it) } ?: "?"
        val capacity = e.capacity?.let { "%.1f".format(it) } ?: "?"
        return "${e.kind.label} $percent · $amount / $capacity ${e.kind.unit}$origin"
    }
}

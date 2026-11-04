package io.fuelmate.ui

import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.LocationOrigin

/** 뼈대 단계의 상태 요약. 기능 화면이 생기면 개발용으로만 남긴다. */
object StatusText {

    fun of(s: AppState): String = buildString {
        appendLine("Fuel Mate")
        appendLine()
        appendLine("주행: ${if (s.drive.restricted) "주행 중" else "정차"}")
        appendLine("기어: ${s.vehicle.gear.takeIf { it != Gear.UNKNOWN }?.name ?: "모름"}")
        appendLine("속도: ${s.vehicle.speedMps?.let { "${(it * 3.6f).toInt()} km/h" } ?: "모름"}")
        appendLine("브레이크: ${when (s.vehicle.brakePressed) { true -> "밟음"; false -> "뗌"; null -> "모름" }}")
        appendLine("이번 실행 주행 거리: ${"%.1f".format(s.odometer.meters / 1000)} km")
        appendLine("위치: ${s.location?.origin?.let { if (it == LocationOrigin.DEMO_TRACK) "데모 경로" else "내비" } ?: "모름"}")
        appendLine("급정거: ${s.events.count { it is DriveEvent.HardBrake }}회")
    }
}

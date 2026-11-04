package io.fuelmate.core.store

import io.fuelmate.core.detect.HardBrakeRule
import io.fuelmate.core.geo.GeoMath
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.DriveMode
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.Odometer
import io.fuelmate.core.model.VehicleSnapshot

/**
 * 순수 함수: 이전 상태 + 액션 → 새 상태. 부작용(저장·화면 갱신)은 하지 않는다.
 * 같은 입력이면 항상 같은 출력이라서, "이 신호가 오면 이 화면"을 단위 테스트로 확인할 수 있다.
 */
fun reduce(state: AppState, action: Action): AppState = when (action) {
    is Action.GearChanged -> state.withVehicle { copy(gear = action.gear) }

    is Action.BrakeChanged -> {
        val next = state.withVehicle { copy(brakePressed = action.pressed) }
        if (action.pressed == true) next
        else next.copy(brakeTracker = HardBrakeRule.onBrakeReleased(state.brakeTracker))
    }

    is Action.SpeedSample -> {
        val result = HardBrakeRule.onSpeed(
            state.brakeTracker, action.timeMs, action.speedMps, state.vehicle.brakePressed == true,
        )
        val next = state.copy(
            vehicle = state.vehicle.copy(speedMps = action.speedMps),
            brakeTracker = result.tracker,
        )
        if (!result.fired) next
        else next.addEvent(
            DriveEvent.HardBrake(
                timeMs = action.timeMs,
                startSpeedMps = result.tracker.pressStartSpeedMps ?: action.speedMps,
            ),
        )
    }

    is Action.DriveModeChanged -> state.copy(drive = DriveMode(action.restricted, action.activeRestrictions))

    is Action.LocationFixed -> state.copy(
        location = action.fix,
        odometer = state.odometer.advance(state.location?.point, action.fix.point),
    )
    is Action.DestinationChanged -> state.copy(destination = action.destination)
}

private fun Odometer.advance(from: GeoPoint?, to: GeoPoint): Odometer {
    if (from == null) return copy(epoch = epoch + 1)
    val d = GeoMath.distanceM(from, to)
    return if (d > Odometer.JUMP_M) copy(epoch = epoch + 1) else copy(meters = meters + d)
}

private inline fun AppState.withVehicle(change: VehicleSnapshot.() -> VehicleSnapshot) =
    copy(vehicle = vehicle.change())

private fun AppState.addEvent(event: DriveEvent) = copy(events = (listOf(event) + events).take(AppState.MAX_EVENTS))

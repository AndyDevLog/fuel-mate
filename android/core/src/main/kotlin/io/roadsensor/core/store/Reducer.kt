package io.roadsensor.core.store

import io.roadsensor.core.detect.HardBrakeRule
import io.roadsensor.core.geo.AheadFilter
import io.roadsensor.core.geo.RouteAhead
import io.roadsensor.core.model.AppState
import io.roadsensor.core.model.DriveMode
import io.roadsensor.core.model.RoadEvent
import io.roadsensor.core.model.VehicleSnapshot

/**
 * 순수 함수: 이전 상태 + 액션 → 새 상태. 부작용(업로드·화면 갱신)은 하지 않는다.
 * 같은 입력이면 항상 같은 출력이라서, "이 신호가 오면 이 화면"을 단위 테스트로 확인할 수 있다.
 */
fun reduce(state: AppState, action: Action): AppState = when (action) {
    is Action.WiperChanged -> state.withVehicle { copy(wiperOn = action.on) }
    is Action.WiperSwitchChanged -> state.withVehicle { copy(wiperSwitch = action.switch) }
    is Action.LowBeamChanged -> state.withVehicle { copy(lowBeamOn = action.on) }
    is Action.TailLampChanged -> state.withVehicle { copy(tailLampOn = action.on) }
    is Action.TelltalesChanged -> state.withVehicle { copy(telltales = action.telltales) }
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
            RoadEvent.HardBrake(
                timeMs = action.timeMs,
                point = state.location?.point,
                headingDeg = state.location?.headingDeg,
                startSpeedMps = result.tracker.pressStartSpeedMps ?: action.speedMps,
            ),
        ).let { it.copy(contribution = it.contribution.copy(hardBrakes = it.contribution.hardBrakes + 1)) }
    }

    is Action.DriveModeChanged -> state.copy(drive = DriveMode(action.restricted, action.activeRestrictions))

    is Action.LocationFixed -> state.copy(location = action.fix).withAlerts()
    is Action.DestinationChanged -> state.copy(destination = action.destination).withAlerts()
    is Action.RouteAheadChanged -> state.copy(routeAhead = action.route).withAlerts()

    is Action.CellsUpdated -> {
        val cells = state.cells - action.expired.toSet() + action.updated.associateBy { it.cell }
        state.copy(cells = cells).withAlerts()
    }

    is Action.UploadStatusChanged -> state.copy(link = state.link.copy(uploadOk = action.ok, pendingUploads = action.pending))

    // 구독이 끊기면 받은 격자는 오래된 정보가 되므로 비운다(알림도 같이 사라진다)
    is Action.FeedStatusChanged ->
        if (action.connected) state.copy(link = state.link.copy(feedConnected = true))
        else state.copy(link = state.link.copy(feedConnected = false), cells = emptyMap()).withAlerts()
}

private inline fun AppState.withVehicle(change: VehicleSnapshot.() -> VehicleSnapshot) =
    copy(vehicle = vehicle.change())

private fun AppState.withAlerts(): AppState {
    val profile = RouteAhead.profile(location, routeAhead, cells)
    return copy(ahead = profile, alerts = AheadFilter.alerts(location, destination, cells.values, profile))
}

private fun AppState.addEvent(event: RoadEvent) = copy(events = (listOf(event) + events).take(AppState.MAX_EVENTS))

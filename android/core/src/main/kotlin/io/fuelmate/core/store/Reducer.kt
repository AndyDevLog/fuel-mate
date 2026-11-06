package io.fuelmate.core.store

import io.fuelmate.core.detect.DriveDetector
import io.fuelmate.core.energy.EnergyRules
import io.fuelmate.core.geo.GeoMath
import io.fuelmate.core.model.AppState
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.DriveMode
import io.fuelmate.core.model.EnergyMeter
import io.fuelmate.core.model.EnergyState
import io.fuelmate.core.model.Gear
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.Odometer
import io.fuelmate.core.model.Trip
import io.fuelmate.core.model.VehicleSnapshot

/**
 * 순수 함수: 이전 상태 + 액션 → 새 상태. 부작용(저장·화면 갱신)은 하지 않는다.
 * 같은 입력이면 항상 같은 출력이라서, "이 신호가 오면 이 화면"을 단위 테스트로 확인할 수 있다.
 */
fun reduce(state: AppState, action: Action): AppState = when (action) {
    is Action.GearChanged -> reduceGear(state, action)

    is Action.BrakeChanged -> state.withVehicle { copy(brakePressed = action.pressed) }

    is Action.SpeedSample -> reduceSpeed(state, action)

    is Action.DriveModeChanged -> state.copy(drive = DriveMode(action.restricted, action.activeRestrictions))

    is Action.EnergyReading -> reduceEnergy(state, action)

    is Action.FuelPriceUpdated -> action.prices.gasoline
        ?.let { state.copy(prices = state.prices.copy(fuelWonPerL = it, fuelTradeDate = action.prices.tradeDate)) }
        ?: state
    is Action.ChargeTierChanged -> state.copy(prices = state.prices.copy(chargeTier = action.tier))

    is Action.StationsFound -> state.copy(stationSearch = action.search)
    is Action.WaypointSet -> state.copy(waypoint = action.waypoint)
    is Action.BookmarksLoaded -> state.copy(bookmarks = action.places)
    is Action.RecentsLoaded -> state.copy(recents = action.places)

    is Action.TripsRestored -> state.copy(trips = action.trips.take(AppState.MAX_TRIPS))

    is Action.LocationFixed -> state.copy(
        location = action.fix,
        odometer = state.odometer.advance(state.location?.point, action.fix.point),
    )
    is Action.DestinationChanged -> state.copy(destination = action.destination)
}

/** 운행이 이보다 짧게 움직였으면(기어만 넣었다 뺌) 남기지 않는다 */
const val MIN_TRIP_M = 100.0

/**
 * P에서 벗어나면 운행 시작, P로 돌아오면 운행 끝. UNKNOWN은 무시한다.
 * 앱을 주행 중에 켜면 첫 기어 값(D)에서 시작하므로 그 전 구간은 빠진다.
 */
private fun reduceGear(state: AppState, a: Action.GearChanged): AppState {
    if (a.gear == Gear.UNKNOWN) return state
    val step = DriveDetector.onGear(state.driveTracker, a.timeMs, a.gear, state.vehicle.speedMps)
    val trip = state.trip?.withDrive(step.events, step.idleEndedMs)
    val next = state.copy(vehicle = state.vehicle.copy(gear = a.gear), driveTracker = step.tracker)
    return when {
        a.gear == Gear.P && trip != null -> {
            val done = trip.copy(endMs = a.timeMs)
            val keep = done.distanceM >= MIN_TRIP_M
            next.copy(trip = null, trips = if (keep) (listOf(done) + state.trips).take(AppState.MAX_TRIPS) else state.trips)
        }
        a.gear != Gear.P && trip == null ->
            next.copy(trip = Trip(startMs = a.timeMs, startWallMs = a.wallMs, energyKind = state.energy?.kind))
        else -> next.copy(trip = trip)
    }
}

private fun reduceSpeed(state: AppState, a: Action.SpeedSample): AppState {
    val v = state.vehicle
    val prev = state.driveTracker.window.lastOrNull()
    val step = DriveDetector.onSpeed(state.driveTracker, a.timeMs, a.speedMps, v.brakePressed == true, v.gear)
    val trip = state.trip?.let { t ->
        var next = t.withDrive(step.events, step.idleEndedMs)
            .copy(endMs = a.timeMs, maxSpeedMps = maxOf(t.maxSpeedMps, a.speedMps))
        val dtMs = prev?.let { a.timeMs - it.timeMs }
        if (prev != null && dtMs != null && dtMs in 1..DriveDetector.MAX_GAP_MS) {
            val avg = (prev.speedMps + a.speedMps) / 2.0
            val ds = avg * dtMs / 1000.0
            val ref = Trip.HIGH_SPEED_REF_MPS.toDouble()
            next = next.copy(
                distanceM = next.distanceM + ds,
                highSpeedExcess = if (avg > ref) next.highSpeedExcess + (avg * avg - ref * ref) * ds else next.highSpeedExcess,
                movingMs = if (avg > DriveDetector.MOVING_MPS) next.movingMs + dtMs else next.movingMs,
            )
        }
        next
    }
    return state.copy(
        vehicle = v.copy(speedMps = a.speedMps),
        driveTracker = step.tracker,
        trip = trip,
        lastMovingMs = if (a.speedMps > EnergyRules.MOVING_MPS) a.timeMs else state.lastMovingMs,
    )
}

private fun Trip.withDrive(events: List<DriveEvent>, idleEndedMs: Long?): Trip =
    if (events.isEmpty() && idleEndedMs == null) this
    else copy(events = this.events + events, idleMs = idleMs + (idleEndedMs ?: 0))

/** 공급원(종류·출처)이 같으면 받은 값만 덮어쓰고 계량기를 고친다. 바뀌면 새로 시작한다 */
private fun reduceEnergy(state: AppState, r: Action.EnergyReading): AppState {
    val prev = state.energy?.takeIf { it.kind == r.kind && it.origin == r.origin }
    val base = prev ?: EnergyState(r.kind, r.origin)
    val next = base.copy(
        amount = r.amount ?: base.amount,
        capacity = r.capacity ?: base.capacity,
        reportedPercent = r.percent ?: base.reportedPercent,
    )
    val meter = if (prev == null) EnergyMeter()
    else EnergyRules.advance(
        state.energyMeter, prev.amount, next.amount, next.capacity,
        moving = state.lastMovingMs?.let { r.timeMs - it <= EnergyRules.REGEN_WINDOW_MS } ?: false,
    )
    // 공급원이 바뀌어 계량기가 새로 시작하면 차이를 운행에 더하지 않는다
    val trip = state.trip?.let { t ->
        if (prev == null) t.copy(energyKind = r.kind)
        else t.copy(
            energyKind = r.kind,
            energyUsed = t.energyUsed + (meter.used - state.energyMeter.used),
            energyAdded = t.energyAdded + (meter.added - state.energyMeter.added),
        )
    }
    return state.copy(energy = next, energyMeter = meter, trip = trip)
}

private fun Odometer.advance(from: GeoPoint?, to: GeoPoint): Odometer {
    if (from == null) return copy(epoch = epoch + 1)
    val d = GeoMath.distanceM(from, to)
    return if (d > Odometer.JUMP_M) copy(epoch = epoch + 1) else copy(meters = meters + d)
}

private inline fun AppState.withVehicle(change: VehicleSnapshot.() -> VehicleSnapshot) =
    copy(vehicle = vehicle.change())

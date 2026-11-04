package io.fuelmate.core.energy

import io.fuelmate.core.model.EnergyOrigin
import io.fuelmate.core.store.Action

/**
 * 데모 에너지 공급원의 상태. 속도를 일정 간격으로 받아 [Consumption]으로 잔량을 줄인다.
 * 불변 값이라 같은 속도 기록이면 항상 같은 잔량이 나온다(시연 영상과 테스트를 맞추기 쉽다).
 */
data class DemoEnergy(
    val params: VehicleParams,
    val amount: Double,
    private val lastTimeMs: Long? = null,
    private val lastSpeedMps: Double = 0.0,
) {
    fun tick(timeMs: Long, speedMps: Float?, poweredOn: Boolean): DemoEnergy {
        val v1 = (speedMps ?: 0f).toDouble()
        val prev = lastTimeMs
        val dtS = if (prev == null) 0.0 else (timeMs - prev) / 1000.0
        // 서비스가 멈췄다 돌아오면 간격이 길다. 그 사이 속도를 모르므로 소비를 세지 않는다
        val used = if (dtS in 0.0..MAX_GAP_S) Consumption.step(params, dtS, lastSpeedMps, v1, poweredOn) else 0.0
        return copy(
            amount = (amount - used).coerceIn(0.0, params.capacity),
            lastTimeMs = timeMs,
            lastSpeedMps = v1,
        )
    }

    fun reading(): Action.EnergyReading = Action.EnergyReading(
        timeMs = lastTimeMs ?: 0L, kind = params.kind, origin = EnergyOrigin.DEMO, amount = amount, capacity = params.capacity,
    )

    companion object {
        const val MAX_GAP_S = 5.0

        fun start(params: VehicleParams, percent: Double) =
            DemoEnergy(params, amount = params.capacity * percent / 100)
    }
}

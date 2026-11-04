package io.fuelmate.energy

/**
 * 에너지 공급원. 실차는 Pleos Fuel·EvBattery, 데모는 속도로 계산한 소비다.
 * 공급원은 값을 [io.fuelmate.core.store.Action.EnergyReading]으로 바꿔 넣기만 하고, 해석(쓴 양·충전)은 :core가 한다.
 */
interface EnergySource {
    fun start()
    fun stop()
}

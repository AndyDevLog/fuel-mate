package io.fuelmate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import io.fuelmate.Log
import io.fuelmate.core.cost.ChargeTier
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.GeoFix
import io.fuelmate.core.model.GeoPoint
import io.fuelmate.core.model.SavedPlace
import io.fuelmate.core.store.Action
import io.fuelmate.core.store.Store
import io.fuelmate.energy.DemoEnergySource
import io.fuelmate.energy.EnergyMode
import io.fuelmate.energy.EnergySource
import io.fuelmate.energy.PleosEnergySource
import io.fuelmate.location.DemoTrackSource
import io.fuelmate.location.LocationMode
import io.fuelmate.location.LocationSink
import io.fuelmate.location.LocationSource
import io.fuelmate.location.NaviHelperSource
import io.fuelmate.core.station.Station
import io.fuelmate.core.station.StationRecommender
import io.fuelmate.navi.NaviPlaces
import io.fuelmate.navi.NaviWaypoint
import io.fuelmate.price.ChargeTierSetting
import io.fuelmate.station.StationSearchSource
import io.fuelmate.price.FuelPriceSource
import io.fuelmate.trip.TripStorage
import io.fuelmate.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.fuelmate.vehicle.CarApiAdapter
import io.fuelmate.vehicle.PleosVehicleAdapter

/**
 * 차량 신호 수집 서비스(포그라운드). 화면이 뒤로 가도 계속 동작한다.
 * Activity만 있으면 onStop 후 약 8초 만에 동결된다(docs/pleos/open-questions.md).
 * 상태 스토어는 여기 하나만 있고, 화면은 바인딩해서 구독만 한다.
 */
class CollectorService : Service() {

    val store = Store()

    private lateinit var vehicle: PleosVehicleAdapter
    private lateinit var carApi: CarApiAdapter
    private lateinit var location: LocationSource
    private lateinit var locationMode: LocationMode
    private lateinit var energy: EnergySource
    private lateinit var energyMode: EnergyMode
    private lateinit var tripStorage: TripStorage
    private lateinit var fuelPrice: FuelPriceSource
    private lateinit var stationSearch: StationSearchSource
    private lateinit var naviWaypoint: NaviWaypoint
    private lateinit var naviPlaces: NaviPlaces
    private val scope = MainScope()

    inner class LocalBinder : Binder() {
        val store: Store get() = this@CollectorService.store
        val locationMode: LocationMode get() = this@CollectorService.locationMode
        fun onPermissionsChanged() {
            carApi.onPermissionsChanged()
            // 알림 권한을 받기 전에 띄운 상시 알림은 숨겨진 채 남는다. 바로 보이게 다시 띄운다
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
        fun setLocationMode(mode: LocationMode) = this@CollectorService.switchLocation(mode)
        fun setEnergyMode(mode: EnergyMode) = this@CollectorService.switchEnergy(mode)
        /** 지금 추천(없으면 아무것도 안 함)을 내비 경유지로 넣는다. 주행 중에도 한 번 누르면 끝나는 동작이다 */
        fun addRecommendedWaypoint() {
            val r = StationRecommender.recommend(store.state.value)
            if (r == null) Log.w("추천이 없어 경유지를 넣지 않음") else naviWaypoint.add(r.best.station)
        }
        /** 화면에서 고른 주유소를 내비 경유지로 넣는다 */
        fun addWaypoint(station: Station) = naviWaypoint.add(station)
        /** 첫 화면: 저장된 곳으로 바로 안내를 시작한다 */
        fun startRoute(place: SavedPlace) = naviPlaces.startRoute(place)
        fun refreshPlaces() = naviPlaces.refresh()
        fun setChargeTier(tier: ChargeTier) {
            ChargeTierSetting.save(this@CollectorService, tier)
            store.dispatch(Action.ChargeTierChanged(tier))
        }
    }

    // 원본 좌표는 로그에 남기지 않는다(데모·디버그 빌드도 기본으로 남기지 않는다)
    private val locationSink = object : LocationSink {
        override fun onFix(fix: GeoFix) = store.dispatch(Action.LocationFixed(fix))
        override fun onDestination(destination: GeoPoint?) = store.dispatch(Action.DestinationChanged(destination))
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Log.i("수집 서비스 시작")

        // 운행 기록을 먼저 되돌린다(차량 신호보다 앞서야 새 운행이 기록 앞에 붙는다)
        tripStorage = TripStorage(this)
        store.dispatch(Action.TripsRestored(tripStorage.load()))
        Log.i("운행 기록 ${store.state.value.trips.size}개 불러옴")
        saveTripsOnChange()

        // 단가: 충전요금 단계(설정)와 오피넷 평균 유가(캐시 → 하루 한 번 갱신)
        store.dispatch(Action.ChargeTierChanged(ChargeTierSetting.load(this)))
        fuelPrice = FuelPriceSource(this, scope, store::dispatch).also { it.start() }
        // 주유소 추천: 반경 검색(연료 50% 이하일 때) + 내비 경유지
        stationSearch = StationSearchSource(scope, { store.state.value }, store::dispatch).also { it.start() }
        naviWaypoint = NaviWaypoint(this, store::dispatch).also { it.start() }
        naviPlaces = NaviPlaces(this, store::dispatch).also { it.start() }

        vehicle = PleosVehicleAdapter(this, store::dispatch).also { it.start() }
        carApi = CarApiAdapter(this, store::dispatch).also { it.start() }
        locationMode = LocationMode.load(this)
        location = createLocation(locationMode).also { it.start(locationSink) }
        energyMode = EnergyMode.load(this)
        energy = createEnergy(energyMode).also { it.start() }
    }

    /** 운행이 끝나 기록이 바뀔 때만 파일에 쓴다. 쓰기는 메인 스레드 밖에서 */
    private fun saveTripsOnChange() {
        scope.launch {
            store.state.map { it.trips }.distinctUntilChanged().drop(1).collect { trips ->
                withContext(Dispatchers.IO) { tripStorage.save(trips) }
                trips.firstOrNull()?.let { t ->
                    Log.i("운행 저장: ${"%.1f".format(t.distanceM / 1000)}km ${t.durationMs / 1000}초, 사건 ${t.events.size}개, 기록 ${trips.size}개")
                }
            }
        }
    }

    private fun createEnergy(mode: EnergyMode): EnergySource {
        val state = { store.state.value }
        return when (mode) {
            EnergyMode.DEMO_EV -> DemoEnergySource.ev(state, store::dispatch)
            EnergyMode.DEMO_FUEL -> DemoEnergySource.fuel(state, store::dispatch)
            EnergyMode.VEHICLE -> {
                val (kind, fuelCapacityL) = carApi.readEnergyInfo()
                // 종류를 모르면 연료차로 본다: 지금 Pleos 탑재 차종(그랜저·아반떼)이 내연기관·하이브리드다
                PleosEnergySource(vehicle, kind ?: EnergyKind.FUEL, fuelCapacityL, store::dispatch)
            }
        }
    }

    private fun switchEnergy(mode: EnergyMode) {
        if (mode == energyMode) return
        Log.i("에너지 공급원 변경: $energyMode → $mode")
        energy.stop()
        energyMode = mode
        EnergyMode.save(this, mode)
        energy = createEnergy(mode).also { it.start() }
    }

    private fun createLocation(mode: LocationMode): LocationSource = when (mode) {
        LocationMode.NAVI -> NaviHelperSource(this, speedMps = { store.state.value.vehicle.speedMps })
        LocationMode.DEMO -> DemoTrackSource(this, speedMps = { store.state.value.vehicle.speedMps })
    }

    private fun switchLocation(mode: LocationMode) {
        if (mode == locationMode) return
        Log.i("위치 출처 변경: $locationMode → $mode")
        location.stop()
        locationMode = mode
        LocationMode.save(this, mode)
        location = createLocation(mode).also { it.start(locationSink) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        fuelPrice.stop()
        stationSearch.stop()
        naviWaypoint.stop()
        naviPlaces.stop()
        scope.cancel()
        energy.stop()
        location.stop()
        carApi.stop()
        vehicle.stop()
        Log.i("수집 서비스 종료")
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "운행 기록", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fuel Mate")
            .setContentText("운행 기록 중")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "collector"
        const val NOTIFICATION_ID = 1
    }
}

package io.fuelmate.ui

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import io.fuelmate.Log
import io.fuelmate.core.cost.ChargeTier
import io.fuelmate.core.model.AppState
import io.fuelmate.core.station.Station
import io.fuelmate.energy.EnergyMode
import io.fuelmate.service.CollectorService
import io.fuelmate.vehicle.CarApiAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 화면은 수집 서비스의 상태를 구독해서 그리기만 한다.
 * 주행/정차는 CarUxRestrictions만 따른다(state.drive.restricted): 주행 중이면 [DrivingScreen], 정차 중이면 [ParkedScreen].
 * 상태를 [ScreenModel]로 바꾼 값이 같으면 다시 그리지 않는다. 개발용 상태 글자는 `--es screen status`로만 연다.
 */
class MainActivity : Activity() {

    private val scope = MainScope()
    private var collect: Job? = null
    private var binder: CollectorService.LocalBinder? = null
    private lateinit var frame: FrameLayout
    private lateinit var parked: ParkedScreen
    private lateinit var driving: DrivingScreen
    private lateinit var status: TextView
    private var showStatus = false
    private var lastModel: ScreenModel? = null
    private var askedPermissions = false
    /** 실행 인텐트로 받은 에너지 공급원(개발·시연용). 서비스에 연결되면 넘긴다 */
    private var pendingEnergyMode: EnergyMode? = null
    private var pendingChargeTier: ChargeTier? = null
    private var pendingAddWaypoint = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as CollectorService.LocalBinder
            binder = b
            applyPendingModes()
            collect = scope.launch { b.store.state.collect { render(it) } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collect?.cancel()
            binder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val ui = Ui(this, Palette.of(this))
        val add: (Station) -> Unit = { s -> binder?.addWaypoint(s) }
        parked = ParkedScreen(ui, add)
        driving = DrivingScreen(ui, add)
        status = ui.text(TextSize.SMALL).apply {
            setBackgroundColor(ui.p.background)
            setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
        }
        frame = FrameLayout(this).apply {
            setBackgroundColor(ui.p.background)
            addView(parked.view)
            addView(driving.view)
            addView(ScrollView(this@MainActivity).apply { addView(status) })
        }
        setContentView(frame)
        readModes(intent)

        val intent = Intent(this, CollectorService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // singleTask라 다시 실행하면 새 화면 대신 여기로 온다
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readModes(intent)
        applyPendingModes()
    }

    private fun readModes(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ENERGY)?.let { name ->
            pendingEnergyMode = runCatching { EnergyMode.valueOf(name) }.getOrNull()
                ?: null.also { Log.w("모르는 에너지 공급원: $name") }
        }
        if (intent?.getStringExtra(EXTRA_WAYPOINT) == "best") pendingAddWaypoint = true
        when (intent?.getStringExtra(EXTRA_SCREEN)) {
            "status" -> showStatus = true
            "app" -> showStatus = false
        }
        intent?.getStringExtra(EXTRA_CHARGE_TIER)?.let { name ->
            pendingChargeTier = runCatching { ChargeTier.valueOf(name) }.getOrNull()
                ?: null.also { Log.w("모르는 충전요금 단계: $name") }
        }
    }

    private fun applyPendingModes() {
        val b = binder ?: return
        pendingEnergyMode?.let { b.setEnergyMode(it) }
        pendingEnergyMode = null
        pendingChargeTier?.let { b.setChargeTier(it) }
        pendingChargeTier = null
        if (pendingAddWaypoint) b.addRecommendedWaypoint()
        pendingAddWaypoint = false
    }

    override fun onDestroy() {
        unbindService(connection)
        scope.cancel()
        super.onDestroy()
    }

    // Pleos 필수 정책: 최상단 화면에서 Back은 종료 대신 숨김
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            Log.i("권한 결과: ${permissions.zip(grantResults.toList()).joinToString { (p, r) -> "${p.substringAfterLast('.')}=$r" }}")
            binder?.onPermissionsChanged()
        }
    }

    private fun render(state: AppState) {
        if (showStatus) status.text = StatusText.of(state)
        val model = ScreenModel.of(state)
        if (model != lastModel) {
            lastModel = model
            when (model) {
                is ScreenModel.Driving -> driving.render(model)
                is ScreenModel.Parked -> parked.render(model)
            }
        }
        // 보이는 화면 하나만 남긴다
        val which = if (showStatus) 2 else if (model is ScreenModel.Driving) 1 else 0
        for (i in 0 until frame.childCount) {
            val v = if (i == which) View.VISIBLE else View.GONE
            if (frame.getChildAt(i).visibility != v) frame.getChildAt(i).visibility = v
        }

        // 권한은 정차 중에만 한 번 묻는다(주행 중에는 설정 화면이 제한된다).
        // 속도: 급가속·급정거 판정. 알림: 수집 서비스 상시 알림(API 33부터 런타임 권한)
        if (!state.drive.restricted && !askedPermissions) {
            askedPermissions = true
            val missing = buildList {
                if (!CarApiAdapter.hasSpeedPermission(this@MainActivity)) add(Car.PERMISSION_SPEED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        /** `--es energy VEHICLE|DEMO_EV|DEMO_FUEL` */
        const val EXTRA_ENERGY = "energy"
        /** `--es chargeTier SLOW|FAST_30|FAST_50|FAST_100|ULTRA` */
        const val EXTRA_CHARGE_TIER = "chargeTier"
        /** `--es waypoint best`: 지금 추천을 내비 경유지로 넣는다(화면 버튼이 생기기 전 개발·시연용) */
        const val EXTRA_WAYPOINT = "waypoint"
        /** `--es screen status|app`: 개발용 상태 글자 / 앱 화면 */
        const val EXTRA_SCREEN = "screen"
    }
}

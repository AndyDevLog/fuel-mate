package io.roadsensor.ui

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.roadsensor.Log
import io.roadsensor.location.LocationMode
import io.roadsensor.core.model.AppState
import io.roadsensor.core.present.DriveLine
import io.roadsensor.core.present.RenderModels
import io.roadsensor.core.present.Screen
import io.roadsensor.render.MapRenderer
import io.roadsensor.render.web.WebMapRenderer
import io.roadsensor.service.CollectorService
import io.roadsensor.vehicle.CarApiAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 화면은 수집 서비스의 상태를 구독해서 그리기만 한다.
 * 정차: 렌더러(WebView + Pixi). 도로 지도와 내 차 화면을 버튼으로 바꾼다.
 * 주행: 위쪽 네이티브 배너(큰 글자 한 줄 + 보조 한 줄)와 그 아래 애니메이션 없는 단순 지도(Screen.DRIVE).
 * LIMIT_CONTENT 규제면 지도를 멈추고 배너가 화면 전체를 차지한다(docs/architecture.md 결정 2).
 * 상태 요약 텍스트는 디버그용으로 접어 둔다(오른쪽 위 "상태" 버튼).
 */
class MainActivity : Activity() {

    private val scope = MainScope()
    private var collect: Job? = null
    private var binder: CollectorService.LocalBinder? = null

    private lateinit var renderer: MapRenderer
    private lateinit var controls: View
    private lateinit var tabs: ScreenTabs
    /** 개발·시연용(위치 출처, 상태). 평소에는 숨기고 탭을 길게 누르면 연다. */
    private lateinit var devControls: View
    private lateinit var locationButton: TextView
    private lateinit var statusButton: TextView
    private lateinit var statusPanel: View
    private lateinit var statusText: TextView
    private lateinit var driveBanner: LinearLayout
    private lateinit var driveText: TextView
    private lateinit var driveSub: TextView

    private var driving = false
    private var driveMap = false
    private var resumed = false
    private var visible = false
    private var lastState: AppState? = null
    /** 지금 보여줄 정차 화면. 화면만의 상태라서 스토어가 아니라 여기 둔다. */
    private val screen = MutableStateFlow(Screen.MAP)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as CollectorService.LocalBinder
            binder = b
            b.setUiVisible(visible)
            updateLocationButton()
            collect = scope.launch {
                // 렌더러에는 렌더 모델이 바뀔 때만 보낸다(속도처럼 지도에 안 쓰는 값은 걸러진다)
                launch {
                    combine(b.store.state, screen) { s, sc -> RenderModels.of(s, if (s.drive.showsDriveMap) Screen.DRIVE else sc) }
                        .distinctUntilChanged().flowOn(Dispatchers.Default)
                        .collect { renderer.render(it) }
                }
                b.store.state.collect { render(it) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collect?.cancel()
            binder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 기본 제목 표시줄을 없애 앱 영역을 지도에 다 쓴다
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        renderer = WebMapRenderer(this)

        // 정차 화면 전환 탭. 길게 누르면 개발·시연용 버튼(위치 출처, 상태)이 열린다. 정차 중에만 보인다.
        tabs = ScreenTabs(this, onSelect = { screen.value = it }, onLongPress = {
            val open = devControls.visibility != View.VISIBLE
            devControls.visibility = if (open) View.VISIBLE else View.GONE
            if (!open) setStatusOpen(false)
        })
        locationButton = devButton(this) {
            val b = binder ?: return@devButton
            val next = if (b.locationMode == LocationMode.DEMO) LocationMode.NAVI else LocationMode.DEMO
            b.setLocationMode(next)
            updateLocationButton()
        }
        statusButton = devButton(this) { setStatusOpen(statusPanel.visibility != View.VISIBLE) }.apply { text = "상태" }
        devControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
            addView(locationButton)
            addView(statusButton, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = 12 })
        }
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            // 개발용 줄이 열려도 탭은 내용 폭 그대로 오른쪽에 붙어 있게 한다
            addView(tabs, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.END })
            addView(devControls, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = 12; gravity = Gravity.END })
        }
        statusText = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
        }
        statusPanel = ScrollView(this).apply {
            setBackgroundColor(Color.argb(0xE6, 0, 0, 0))
            visibility = View.GONE
            addView(statusText)
        }
        driveText = TextView(this).apply {
            textSize = 56f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        driveSub = TextView(this).apply {
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(0xB8, 0xC4, 0xD0))
            setPadding(0, 8, 0, 0)
        }
        driveBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 28, 32, 32)
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            addView(driveText)
            addView(driveSub)
        }
        setContentView(FrameLayout(this).apply {
            addView(renderer.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(0, 24, 24, 0)
            })
            addView(statusPanel, FrameLayout.LayoutParams(1000, MATCH_PARENT, Gravity.END).apply {
                setMargins(0, 260, 24, 24)
            })
            addView(driveBanner, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })

        scope.launch { screen.collect { tabs.select(it) } }

        val intent = Intent(this, CollectorService::class.java)
        startForegroundService(Intent(intent).putExtra(CollectorService.EXTRA_UI_VISIBLE, true))
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // 화면이 보이는 동안에는 서비스가 앞길 알림을 띄우지 않는다(주행 배너를 가린다).
    // 분할 화면에서는 보이면서 onResume이 아닐 수 있어서 onStart/onStop으로 본다.
    override fun onStart() {
        super.onStart()
        visible = true
        binder?.setUiVisible(true)
    }

    override fun onStop() {
        visible = false
        binder?.setUiVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateRenderer()
    }

    override fun onPause() {
        resumed = false
        updateRenderer()
        super.onPause()
    }

    override fun onDestroy() {
        unbindService(connection)
        scope.cancel()
        renderer.destroy()
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

    private fun updateLocationButton() {
        val mode = binder?.locationMode ?: return
        val other = if (mode == LocationMode.DEMO) LocationMode.NAVI else LocationMode.DEMO
        locationButton.text = "위치 출처: ${mode.label}  (눌러서 ${other.label}로)"
    }

    private fun setStatusOpen(open: Boolean) {
        statusPanel.visibility = if (open) View.VISIBLE else View.GONE
        statusButton.text = if (open) "상태 닫기" else "상태"
        lastState?.let { if (open) statusText.text = StatusText.of(it) }
    }

    /** 렌더러는 화면이 앞에 있고, 정차 중이거나 주행 지도를 보여도 될 때만 돈다. 아니면 숨기고 멈춘다(WebView.onPause). */
    private fun updateRenderer() {
        if (resumed && (!driving || driveMap)) renderer.resume() else renderer.pause()
    }

    /** 주행 배너: 지도가 있으면 위쪽 띠, 없으면(LIMIT_CONTENT) 화면 전체. */
    private fun layoutDriveBanner() {
        val lp = driveBanner.layoutParams as FrameLayout.LayoutParams
        lp.height = if (driveMap) WRAP_CONTENT else MATCH_PARENT
        lp.gravity = Gravity.TOP
        driveBanner.layoutParams = lp
        driveBanner.setBackgroundColor(if (driveMap) Color.argb(0xE6, 0, 0, 0) else Color.BLACK)
    }

    private var askedPermissions = false

    private fun render(state: AppState) {
        lastState = state
        if (state.drive.restricted != driving || state.drive.showsDriveMap != driveMap) {
            driving = state.drive.restricted
            driveMap = state.drive.showsDriveMap
            driveBanner.visibility = if (driving) View.VISIBLE else View.GONE
            controls.visibility = if (driving) View.GONE else View.VISIBLE
            if (driving) setStatusOpen(false)
            layoutDriveBanner()
            updateRenderer()
        }
        if (driving) {
            driveText.text = DriveLine.of(state)
            val detail = DriveLine.detail(state)
            driveSub.text = detail ?: ""
            driveSub.visibility = if (detail == null) View.GONE else View.VISIBLE
        }
        else if (statusPanel.visibility == View.VISIBLE) statusText.text = StatusText.of(state)

        // 권한은 정차 중에만 한 번 묻는다(주행 중에는 설정 화면이 제한된다).
        // 속도: 급정거 판정. 알림: 앞길 알림과 수집 서비스 상시 알림(API 33부터 런타임 권한)
        if (!driving && !askedPermissions) {
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
    }
}

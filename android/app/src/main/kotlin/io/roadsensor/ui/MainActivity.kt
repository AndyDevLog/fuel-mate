package io.roadsensor.ui

import android.app.Activity
import android.car.Car
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
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
 * 정차: 렌더러(WebView + Pixi). 도로 지도와 내 차 화면을 버튼으로 바꾼다. 주행: 큰 글자 한 줄(네이티브 고정, docs/architecture.md 결정 2).
 * 상태 요약 텍스트는 디버그용으로 접어 둔다(오른쪽 위 "상태" 버튼).
 */
class MainActivity : Activity() {

    private val scope = MainScope()
    private var collect: Job? = null
    private var binder: CollectorService.LocalBinder? = null

    private lateinit var renderer: MapRenderer
    private lateinit var controls: View
    private lateinit var screenButton: Button
    private lateinit var locationButton: Button
    private lateinit var statusButton: Button
    private lateinit var statusPanel: View
    private lateinit var statusText: TextView
    private lateinit var driveText: TextView

    private var driving = false
    private var resumed = false
    private var lastState: AppState? = null
    /** 지금 보여줄 정차 화면. 화면만의 상태라서 스토어가 아니라 여기 둔다. */
    private val screen = MutableStateFlow(Screen.MAP)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as CollectorService.LocalBinder
            binder = b
            updateLocationButton()
            collect = scope.launch {
                // 렌더러에는 렌더 모델이 바뀔 때만 보낸다(속도처럼 지도에 안 쓰는 값은 걸러진다)
                launch {
                    combine(b.store.state, screen, RenderModels::of).distinctUntilChanged().flowOn(Dispatchers.Default)
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

        // 위치 출처 전환(정차 중에만 보인다). 큰 터치 영역으로 둔다.
        locationButton = Button(this).apply {
            textSize = 20f
            minHeight = 100
            setOnClickListener {
                val b = binder ?: return@setOnClickListener
                val next = if (b.locationMode == LocationMode.DEMO) LocationMode.NAVI else LocationMode.DEMO
                b.setLocationMode(next)
                updateLocationButton()
            }
        }
        screenButton = Button(this).apply {
            textSize = 20f
            minHeight = 100
            setOnClickListener { screen.value = if (screen.value == Screen.MAP) Screen.MY_CAR else Screen.MAP }
        }
        statusButton = Button(this).apply {
            textSize = 20f
            minHeight = 100
            text = "상태"
            setOnClickListener { setStatusOpen(statusPanel.visibility != View.VISIBLE) }
        }
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(screenButton)
            addView(locationButton)
            addView(statusButton)
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
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(renderer.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(0, 24, 24, 0)
            })
            addView(statusPanel, FrameLayout.LayoutParams(1000, MATCH_PARENT, Gravity.END).apply {
                setMargins(0, 150, 24, 24)
            })
            addView(driveText, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        })

        scope.launch { screen.collect { screenButton.text = if (it == Screen.MAP) "내 차 보기" else "도로 지도 보기" } }

        val intent = Intent(this, CollectorService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
        if (requestCode == REQUEST_SPEED) {
            Log.i("속도 권한 결과: ${grantResults.joinToString()}")
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

    /** 렌더러는 화면이 앞에 있고 정차 중일 때만 돈다. 주행 중에는 숨기고 멈춘다(WebView.onPause). */
    private fun updateRenderer() {
        if (resumed && !driving) renderer.resume() else renderer.pause()
    }

    private var askedSpeed = false

    private fun render(state: AppState) {
        lastState = state
        if (state.drive.restricted != driving) {
            driving = state.drive.restricted
            driveText.visibility = if (driving) View.VISIBLE else View.GONE
            controls.visibility = if (driving) View.GONE else View.VISIBLE
            if (driving) setStatusOpen(false)
            updateRenderer()
        }
        if (driving) driveText.text = DriveLine.of(state)
        else if (statusPanel.visibility == View.VISIBLE) statusText.text = StatusText.of(state)

        // 속도 권한은 정차 중에만 묻는다(주행 중에는 설정 화면이 제한된다)
        if (!driving && !askedSpeed && !CarApiAdapter.hasSpeedPermission(this)) {
            askedSpeed = true
            requestPermissions(arrayOf(Car.PERMISSION_SPEED), REQUEST_SPEED)
        }
    }

    private companion object {
        const val REQUEST_SPEED = 1
    }
}

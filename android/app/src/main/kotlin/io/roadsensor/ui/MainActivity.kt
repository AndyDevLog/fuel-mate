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
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import io.roadsensor.Log
import io.roadsensor.core.model.AppState
import io.roadsensor.core.present.DriveLine
import io.roadsensor.service.CollectorService
import io.roadsensor.vehicle.CarApiAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 화면은 수집 서비스의 상태를 구독해서 그리기만 한다.
 * 정차: 렌더러 자리(지금은 상태 요약). 주행: 큰 글자 한 줄(네이티브 고정, docs/architecture.md 결정 2).
 */
class MainActivity : Activity() {

    private val scope = MainScope()
    private var collect: Job? = null
    private var binder: CollectorService.LocalBinder? = null

    private lateinit var parkedView: View
    private lateinit var statusText: TextView
    private lateinit var driveText: TextView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as CollectorService.LocalBinder
            binder = b
            collect = scope.launch { b.store.state.collect { render(it) } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collect?.cancel()
            binder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusText = TextView(this).apply {
            textSize = 20f
            typeface = Typeface.MONOSPACE
            setPadding(48, 48, 48, 48)
        }
        parkedView = ScrollView(this).apply { addView(statusText) }
        driveText = TextView(this).apply {
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(parkedView)
            addView(driveText)
        })

        val intent = Intent(this, CollectorService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
        if (requestCode == REQUEST_SPEED) {
            Log.i("속도 권한 결과: ${grantResults.joinToString()}")
            binder?.onPermissionsChanged()
        }
    }

    private var askedSpeed = false

    private fun render(state: AppState) {
        val driving = state.drive.restricted
        driveText.visibility = if (driving) View.VISIBLE else View.GONE
        parkedView.visibility = if (driving) View.GONE else View.VISIBLE
        if (driving) driveText.text = DriveLine.of(state)
        else statusText.text = StatusText.of(state)

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

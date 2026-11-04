package io.fuelmate.ui

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Window
import android.widget.TextView
import io.fuelmate.Log
import io.fuelmate.core.model.AppState
import io.fuelmate.service.CollectorService
import io.fuelmate.vehicle.CarApiAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 화면은 수집 서비스의 상태를 구독해서 그리기만 한다.
 * 뼈대 단계라 상태 요약 한 장만 보여 준다. 기능 화면은 docs/progress.md 순서대로 붙인다.
 */
class MainActivity : Activity() {

    private val scope = MainScope()
    private var collect: Job? = null
    private var binder: CollectorService.LocalBinder? = null
    private lateinit var text: TextView
    private var askedPermissions = false

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
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        text = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(16, 20, 28))
            setPadding(64, 64, 64, 64)
        }
        setContentView(text)

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
        if (requestCode == REQUEST_PERMISSIONS) {
            Log.i("권한 결과: ${permissions.zip(grantResults.toList()).joinToString { (p, r) -> "${p.substringAfterLast('.')}=$r" }}")
            binder?.onPermissionsChanged()
        }
    }

    private fun render(state: AppState) {
        text.text = StatusText.of(state)

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
    }
}

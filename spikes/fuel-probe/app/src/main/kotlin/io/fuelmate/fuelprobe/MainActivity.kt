package io.fuelmate.fuelprobe

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 스파이크: 주유비 매니저 주제 전환 전 확인(docs/pivot-fuel.md).
 * 연료·배터리·연비·주행거리·공조(Pleos SDK)와 AAOS 표준 속성, NaviHelper 경유지를 찍는다.
 * 확인 방법: adb logcat -s FuelProbe
 * 내비 시험: am start --user 10 -n io.fuelmate.fuelprobe/.MainActivity --es cmd route|waypoint|charger
 */
class MainActivity : Activity() {

    private lateinit var output: TextView
    private lateinit var pleos: PleosEnergyProbe
    private lateinit var aaos: AaosEnergyProbe
    private lateinit var navi: NaviFuelProbe
    private val main = Handler(Looper.getMainLooper())
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(output) })

        pleos = PleosEnergyProbe(this, ::log).also { it.start() }
        aaos = AaosEnergyProbe(this, ::log).also { it.start() }
        navi = NaviFuelProbe(this, ::log).also { it.start() }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra("cmd")) {
            "route" -> navi.requestTestRoute()
            "waypoint" -> navi.addTestWaypoint()
            "charger" -> navi.requestChargerOperators()
            "dump" -> pleos.readOnce()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        pleos.stop()
        aaos.stop()
        navi.stop()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        main.post {
            output.append("${time.format(Date())}  $line\n")
            // 화면 글이 너무 길어지면 앞을 자른다(로그는 logcat에 남는다)
            if (output.length() > 20_000) output.text = output.text.takeLast(10_000)
        }
    }

    companion object {
        const val TAG = "FuelProbe"
    }
}

package io.roadsensor.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.roadsensor.Log
import io.roadsensor.core.model.RoadAlert
import io.roadsensor.core.present.DriveLine
import io.roadsensor.core.present.NearestZoneWatcher
import io.roadsensor.core.present.NearestZoneWatcher.Decision
import io.roadsensor.core.store.Store
import io.roadsensor.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 앞길 알림. 앱 화면이 안 보일 때(내비가 앞에 있을 때) 가장 가까운 구간이 새로 바뀌면 알림을 하나 띄운다.
 * 언제 띄우고 지울지는 :core [NearestZoneWatcher]가 정한다. 에뮬레이터에서는 주행 중에도 내비 위에 뜬다
 * (docs/pleos/open-questions.md "주행 중 알림 시험").
 * 알림은 한 칸만 쓴다: 새 알림이 이전 것을 바꿔서 알림 센터에 쌓이지 않는다. 좌표는 넣지 않는다.
 */
class AlertNotifier(
    private val context: Context,
    private val store: Store,
    private val uiVisible: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val watcher = NearestZoneWatcher()
    private var warnedPermission = false

    fun start() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "앞길 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "앱 화면이 안 보일 때 가장 가까운 비·어두움·급정거 구간이 바뀌면 알려 줍니다"
            },
        )
        scope.launch {
            // 알림 목록(거리 포함)·글자 수 규제·화면 표시가 바뀔 때만 판정한다. 정차 중에는 거의 돌지 않는다
            store.state.map { it.alerts to it.drive.limitStringLength }.distinctUntilChanged()
                .combine(uiVisible) { (alerts, short), visible -> Triple(alerts, short, visible) }
                .collect { (alerts, short, visible) ->
                    when (val d = watcher.onAlerts(alerts, notify = !visible)) {
                        is Decision.Show -> show(d.alert, short)
                        Decision.Cancel -> manager.cancel(NOTIFICATION_ID)
                        Decision.Keep -> Unit
                    }
                }
        }
    }

    private fun show(alert: RoadAlert, short: Boolean) {
        if (!canPost()) return
        manager.notify(NOTIFICATION_ID, build(context, alert, short))
        Log.i("앞길 알림: ${alert.kind} ${alert.distanceM}m")
    }

    private fun canPost(): Boolean {
        val ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!ok && !warnedPermission) {
            warnedPermission = true
            Log.w("알림 권한 없음: 앞길 알림을 띄우지 않는다")
        }
        return ok
    }

    companion object {
        const val CHANNEL_ID = "ahead"
        const val NOTIFICATION_ID = 2
        /** 화면 위 카드를 이만큼만 두고 알림을 지운다. 내비게이션 종류 카드는 안 지우면 내비 안내 위에 계속 남는다. */
        const val SHOW_MS = 8_000L

        /**
         * 알림 하나. 디버그 빌드의 시험 수신기도 같은 함수를 쓴다.
         * 종류는 내비게이션: 에뮬레이터 차량 시스템은 내비게이션·메시지 종류만 화면 위에 띄운다
         * (docs/pleos/open-questions.md "주행 중 알림 시험").
         */
        fun build(
            context: Context, alert: RoadAlert, short: Boolean, category: String? = Notification.CATEGORY_NAVIGATION,
        ): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(DriveLine.lineOf(alert, short))
                .apply { DriveLine.detailOf(alert, short)?.let(::setContentText) }
                .apply { if (category != null) setCategory(category) }
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(SHOW_MS)
                .build()
        }
    }
}

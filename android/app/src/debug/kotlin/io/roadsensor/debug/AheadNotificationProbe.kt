package io.roadsensor.debug

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.roadsensor.Log
import io.roadsensor.core.model.AlertKind
import io.roadsensor.core.model.RoadAlert
import io.roadsensor.service.AlertNotifier

/**
 * 시험용(디버그 빌드에만 있음): 앞길 알림과 똑같은 알림을 운전 없이 띄워서, 내비 위에 뜨는지 본다.
 *
 *   adb shell am broadcast --user 10 -n io.roadsensor/.debug.AheadNotificationProbe \
 *       -a io.roadsensor.debug.AHEAD_PROBE [--es category navigation|recommendation|status|event|reminder|service|message]
 *   지우기: ... -a io.roadsensor.debug.AHEAD_PROBE --ez cancel true
 *
 * 채널은 수집 서비스(AlertNotifier.start)가 만든다. 앱을 한 번 실행한 뒤 쓴다.
 */
class AheadNotificationProbe : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (intent.getBooleanExtra("cancel", false)) {
            manager.cancel(AlertNotifier.NOTIFICATION_ID)
            Log.i("시험 앞길 알림 지움")
            return
        }
        val category = when (intent.getStringExtra("category")) {
            "navigation" -> Notification.CATEGORY_NAVIGATION
            "recommendation" -> Notification.CATEGORY_RECOMMENDATION
            "status" -> Notification.CATEGORY_STATUS
            "event" -> Notification.CATEGORY_EVENT
            "reminder" -> Notification.CATEGORY_REMINDER
            "service" -> Notification.CATEGORY_SERVICE
            "message" -> Notification.CATEGORY_MESSAGE
            else -> null
        }
        val alert = RoadAlert(AlertKind.HARD_BRAKE, "test", distanceM = 300, reporters = 4)
        manager.notify(AlertNotifier.NOTIFICATION_ID, AlertNotifier.build(context, alert, short = false, category = category))
        Log.i("시험 앞길 알림 보냄 (category=${category ?: "없음"})")
    }
}

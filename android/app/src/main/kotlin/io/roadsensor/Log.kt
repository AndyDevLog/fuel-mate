package io.roadsensor

/** 앱 공통 로그. 확인: adb logcat -s RoadSensor */
object Log {
    private const val TAG = "RoadSensor"

    fun i(message: String) {
        android.util.Log.i(TAG, message)
    }

    fun w(message: String, e: Throwable? = null) {
        android.util.Log.w(TAG, message, e)
    }
}

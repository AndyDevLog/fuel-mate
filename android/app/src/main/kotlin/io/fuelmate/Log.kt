package io.fuelmate

/** 앱 공통 로그. 확인: adb logcat -s FuelMate */
object Log {
    private const val TAG = "FuelMate"

    fun i(message: String) {
        android.util.Log.i(TAG, message)
    }

    fun w(message: String, e: Throwable? = null) {
        android.util.Log.w(TAG, message, e)
    }
}

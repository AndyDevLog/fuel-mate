package io.fuelmate.trip

import android.content.Context
import io.fuelmate.Log
import io.fuelmate.core.model.DriveEvent
import io.fuelmate.core.model.EnergyKind
import io.fuelmate.core.model.Trip
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 끝난 운행을 차 안(앱 전용 저장소)에 JSON으로 저장한다. 위치는 저장하지 않는다(운행에 위치가 없다).
 * 차량번호·VIN·계정도 없다. 사건 시각은 운행 시작부터의 경과(ms)로 저장한다.
 * 파일이 깨져 있으면 덮어쓰지 않게 `.bad`로 옮겨 두고 빈 기록으로 시작한다.
 */
class TripStorage(context: Context) {

    private val file = File(context.filesDir, FILE)

    fun load(): List<Trip> {
        if (!file.isFile) return emptyList()
        return runCatching { decode(JSONObject(file.readText())) }
            .onFailure {
                Log.w("운행 기록 읽기 실패, ${file.name}.bad로 옮김", it)
                file.renameTo(File(file.parentFile, "$FILE.bad"))
            }
            .getOrDefault(emptyList())
    }

    fun save(trips: List<Trip>) {
        // 쓰다 끊겨도 이전 파일이 남게 임시 파일에 쓰고 바꾼다
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(encode(trips).toString())
        if (!tmp.renameTo(file)) Log.w("운행 기록 저장 실패")
    }

    private fun encode(trips: List<Trip>) = JSONObject()
        .put("version", VERSION)
        .put("trips", JSONArray().apply { trips.forEach { put(encode(it)) } })

    private fun encode(t: Trip) = JSONObject()
        .put("startMs", t.startMs)
        .put("startWallMs", t.startWallMs)
        .put("durationMs", t.durationMs)
        .put("distanceM", t.distanceM)
        .put("movingMs", t.movingMs)
        .put("idleMs", t.idleMs)
        .put("maxSpeedMps", t.maxSpeedMps.toDouble())
        .put("energyKind", t.energyKind?.name ?: JSONObject.NULL)
        .put("energyUsed", t.energyUsed)
        .put("energyAdded", t.energyAdded)
        .put("events", JSONArray().apply { t.events.forEach { put(encode(it, t.startMs)) } })

    private fun encode(e: DriveEvent, tripStartMs: Long): JSONObject {
        val o = JSONObject().put("offsetMs", e.timeMs - tripStartMs)
        return when (e) {
            is DriveEvent.HardAccel -> o.put("type", "accel").speeds(e.startSpeedMps, e.endSpeedMps, e.peakMps2)
            is DriveEvent.HardDecel -> o.put("type", "decel").speeds(e.startSpeedMps, e.endSpeedMps, e.peakMps2)
            is DriveEvent.HardBrake -> o.put("type", "brake").speeds(e.startSpeedMps, e.endSpeedMps, e.peakMps2)
            is DriveEvent.LongIdle -> o.put("type", "idle").put("durationMs", e.durationMs)
        }
    }

    private fun JSONObject.speeds(start: Float, end: Float, peak: Float) =
        put("startSpeedMps", start.toDouble()).put("endSpeedMps", end.toDouble()).put("peakMps2", peak.toDouble())

    private fun decode(root: JSONObject): List<Trip> {
        val arr = root.getJSONArray("trips")
        return (0 until arr.length()).map { decodeTrip(arr.getJSONObject(it)) }
    }

    private fun decodeTrip(o: JSONObject): Trip {
        val start = o.getLong("startMs")
        val events = o.getJSONArray("events")
        return Trip(
            startMs = start,
            startWallMs = o.getLong("startWallMs"),
            endMs = start + o.getLong("durationMs"),
            distanceM = o.getDouble("distanceM"),
            movingMs = o.getLong("movingMs"),
            idleMs = o.getLong("idleMs"),
            maxSpeedMps = o.getDouble("maxSpeedMps").toFloat(),
            energyKind = o.optString("energyKind").takeIf { it.isNotEmpty() && it != "null" }
                ?.let { runCatching { EnergyKind.valueOf(it) }.getOrNull() },
            energyUsed = o.getDouble("energyUsed"),
            energyAdded = o.getDouble("energyAdded"),
            events = (0 until events.length()).mapNotNull { decodeEvent(events.getJSONObject(it), start) },
        )
    }

    private fun decodeEvent(o: JSONObject, tripStartMs: Long): DriveEvent? {
        val t = tripStartMs + o.getLong("offsetMs")
        fun f(key: String) = o.getDouble(key).toFloat()
        return when (o.getString("type")) {
            "accel" -> DriveEvent.HardAccel(t, f("startSpeedMps"), f("endSpeedMps"), f("peakMps2"))
            "decel" -> DriveEvent.HardDecel(t, f("startSpeedMps"), f("endSpeedMps"), f("peakMps2"))
            "brake" -> DriveEvent.HardBrake(t, f("startSpeedMps"), f("endSpeedMps"), f("peakMps2"))
            "idle" -> DriveEvent.LongIdle(t, o.getLong("durationMs"))
            else -> null
        }
    }

    private companion object {
        const val FILE = "trips.json"
        const val VERSION = 1
    }
}

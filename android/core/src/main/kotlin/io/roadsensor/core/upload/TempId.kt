package io.roadsensor.core.upload

/**
 * 임시 ID. 서버가 "같은 격자에서 같은 차를 두 번 세지 않게" 하는 데만 쓴다.
 * 서비스가 시작될 때(시동) 새로 만들고, 30분마다 바꾼다. 차량번호·VIN·기기 ID와는 아무 관계가 없는 무작위 값이다.
 */
class TempIdRotator(
    private val newId: () -> String,
    private val rotateMs: Long = ROTATE_MS,
) {
    private var id: String? = null
    private var issuedAtMs = 0L

    fun current(nowMs: Long): String {
        val existing = id
        if (existing != null && nowMs - issuedAtMs < rotateMs) return existing
        return newId().also { id = it; issuedAtMs = nowMs }
    }

    companion object {
        const val ROTATE_MS = 30 * 60 * 1000L
    }
}

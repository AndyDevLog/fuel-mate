package io.roadsensor.vehicle

/**
 * Vehicle SDK 오류 설명.
 * SDK 2.3.2의 VehicleException은 난독화되어 있고 ErrorCode는 컴파일 경로에 없다(docs/pleos/vehicle-sdk.md).
 * 그래서 ErrorCode를 돌려주는 인자 없는 메서드를 이름으로 찾아 읽는다.
 */
object VehicleErrors {
    private const val ERROR_CODE_CLASS = "ai.pleos.playground.vehicle.constant.ErrorCode"

    fun code(e: Exception): String? = e.javaClass.methods
        .firstOrNull { it.parameterCount == 0 && it.returnType.name == ERROR_CODE_CLASS }
        ?.let { runCatching { it.invoke(e)?.toString() }.getOrNull() }

    fun describe(e: Exception): String = "${code(e) ?: e.javaClass.simpleName}: ${e.message}"
}

# release 빌드(R8) 규칙.

# Pleos SDK: VehicleErrors가 ErrorCode를 클래스 이름으로 찾고(리플렉션), SDK가 내부에서 AIDL·리플렉션을 쓸 수 있어 통째로 둔다
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# AAOS Car API는 기기 라이브러리(useLibrary)라서 앱에 들어가지 않는다
-dontwarn android.car.**

package io.roadsensor.core.store

import io.roadsensor.core.model.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 앱 상태의 유일한 보관소. 수집 서비스 안에 하나만 둔다.
 * dispatch는 어느 스레드에서 불러도 된다(SDK 콜백은 binder 스레드에서 온다). update는 원자적으로 적용된다.
 */
class Store(initial: AppState = AppState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun dispatch(action: Action) {
        _state.update { reduce(it, action) }
    }
}

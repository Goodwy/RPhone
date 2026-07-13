package dev.goodwy.rphone.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UssdRepository {
    private val _response = MutableStateFlow<Pair<String, String>?>(null)
    val response: StateFlow<Pair<String, String>?> = _response.asStateFlow()

    fun post(request: String, response: String) {
        _response.value = request to response
    }

    fun clear() {
        _response.value = null
    }
}

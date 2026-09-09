package com.remixtv.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Потоковый буфер отладочных сообщений для вывода на экран.
 * Запись и накопление строк выполняются только после [setOutputEnabled](true).
 */
object AppDebugLog {

    private const val MAX_LINES = 250

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock = Any()

    @Volatile
    private var debugOutputEnabled: Boolean = false

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun setOutputEnabled(enabled: Boolean) {
        debugOutputEnabled = enabled
        if (!enabled) {
            synchronized(lock) {
                _lines.value = emptyList()
            }
        }
    }

    fun log(tag: String, message: String) {
        if (!debugOutputEnabled) return
        val line = "${timeFormat.format(Date())} [$tag] $message"
        synchronized(lock) {
            _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        }
    }
}

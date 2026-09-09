package com.remixtv.utils

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Единый формат времени для сравнения с полями start_date/end_date на сервере (ISO без Z).
 */
object TimeProvider {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    fun nowIsoUtc(): String = formatter.format(Instant.now())
}

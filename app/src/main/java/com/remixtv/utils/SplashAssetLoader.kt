package com.remixtv.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Загрузка заставки из assets. Поддерживаются PNG и JPEG.
 *
 * Ожидаемые пути (по порядку пробуем первый существующий):
 * - `remixtv/splash.png`
 * - `remixtv/splash.jpg`
 */
object SplashAssetLoader {

    private val candidates = arrayOf("remixtv/splash.png", "remixtv/splash.jpg")

    fun loadBitmap(context: Context): Bitmap? {
        val am = context.assets
        for (path in candidates) {
            try {
                am.open(path).use { input: InputStream ->
                    return BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}

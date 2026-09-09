package com.remixtv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.remixtv.presentation.ui.MainActivity
import com.remixtv.utils.PreferencesManager

/**
 * Запуск приложения после загрузки ОС, если включено в настройках.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val prefs = PreferencesManager(context.applicationContext)
        if (!prefs.bootStartEnabled) return

        val launch = Intent(context.applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.applicationContext.startActivity(launch)
    }
}

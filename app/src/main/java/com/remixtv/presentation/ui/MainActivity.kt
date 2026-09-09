package com.remixtv.presentation.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.remixtv.R
import com.remixtv.RemixTVApplication
import com.remixtv.databinding.ActivityMainBinding
import com.remixtv.service.PlaybackService
import com.remixtv.utils.AppDebugLog
import kotlinx.coroutines.launch

/**
 * Главная активность Android TV: контейнер фрагментов и глобальная обработка медиа-клавиш пульта.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var playback: PlaybackService? = null
    private var playbackBound: Boolean = false

    private val fragmentDebugCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentPreAttached(fm: FragmentManager, f: Fragment, context: Context) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} preAttached")
        }

        override fun onFragmentAttached(fm: FragmentManager, f: Fragment, context: Context) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} attached")
        }

        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} created")
        }

        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} viewCreated id=${v.id}")
        }

        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} started")
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} resumed")
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} paused")
        }

        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} stopped")
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} viewDestroyed")
        }

        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} destroyed")
        }

        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            AppDebugLog.log("Fragment", "${f::class.simpleName} detached")
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppDebugLog.log("Service", "onServiceConnected ${name?.className}")
            val binder = service as PlaybackService.LocalBinder
            playback = binder.getService()
            val f = supportFragmentManager.findFragmentById(R.id.player_container)
            if (f is PlayerFragment) {
                f.bindPlayback(playback!!)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppDebugLog.log("Service", "onServiceDisconnected ${name?.className}")
            val f = supportFragmentManager.findFragmentById(R.id.player_container)
            if (f is PlayerFragment) {
                f.unbindPlayback()
            }
            playback = null
        }
    }

    fun getPlayback(): PlaybackService? = playback

    fun applyDebugPanelFromPrefs() {
        val app = application as RemixTVApplication
        val on = app.preferencesManager.debugEnabled
        AppDebugLog.setOutputEnabled(on)
        binding.debugDrawer.isVisible = on
    }

    private fun toggleDebugPanel() {
        val willBeVisible = !binding.debugDrawer.isVisible
        binding.debugDrawer.isVisible = willBeVisible
        AppDebugLog.setOutputEnabled(willBeVisible)
    }

    fun refreshPlaybackNotificationMetadata() {
        playback?.refreshMediaNotification()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDebugLog.log("MainActivity", "onCreate saved=${savedInstanceState != null}")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentDebugCallbacks, true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppDebugLog.lines.collect { list ->
                    binding.debugLog.text = list.joinToString("\n")
                    binding.debugScroll.post {
                        binding.debugScroll.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.player_container, PlayerFragment())
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            AppDebugLog.log(
                "FragmentManager",
                "backStack изменён, записей=${supportFragmentManager.backStackEntryCount}"
            )
        }

        PlaybackService.start(this)
        AppDebugLog.log("MainActivity", "PlaybackService.start + bindService")
        playbackBound = bindService(
            Intent(this, PlaybackService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        applyDebugPanelFromPrefs()
    }

    override fun onStart() {
        super.onStart()
        AppDebugLog.log("MainActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        AppDebugLog.log("MainActivity", "onResume")
        applyDebugPanelFromPrefs()
    }

    override fun onPause() {
        AppDebugLog.log("MainActivity", "onPause")
        super.onPause()
    }

    override fun onStop() {
        AppDebugLog.log("MainActivity", "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        AppDebugLog.log("MainActivity", "onDestroy")
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentDebugCallbacks)
        if (playbackBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
            }
            playbackBound = false
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyName = KeyEvent.keyCodeToString(event.keyCode)
            AppDebugLog.log("Клавиша", "$keyName")
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    AppDebugLog.log("MainActivity", "команда: PLAY/PAUSE → сервис")
                    sendPlaybackAction(PlaybackService.ACTION_PLAY_PAUSE)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    AppDebugLog.log("MainActivity", "команда: NEXT → сервис")
                    sendPlaybackAction(PlaybackService.ACTION_NEXT)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    AppDebugLog.log("MainActivity", "команда: PREV → сервис")
                    sendPlaybackAction(PlaybackService.ACTION_PREV)
                    return true
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS -> {
                    AppDebugLog.log("MainActivity", "открыть настройки")
                    openSettings()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    AppDebugLog.log("MainActivity", "переключить панель отладки")
                    toggleDebugPanel()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sendPlaybackAction(action: String) {
        AppDebugLog.log("MainActivity", "startService action=$action")
        val i = Intent(this, PlaybackService::class.java).setAction(action)
        startService(i)
    }

    private fun openSettings() {
        AppDebugLog.log("MainActivity", "fragment replace → SettingsFragment")
        supportFragmentManager.commit {
            addToBackStack("settings")
            replace(R.id.player_container, SettingsFragment())
        }
    }
}

package com.remixtv.presentation.viewmodels

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remixtv.RemixTVApplication
import com.remixtv.domain.models.DownloadState
import com.remixtv.utils.SplashAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RemixTVApplication

    val downloadState: StateFlow<DownloadState> =
        app.playlistRepository.observeDownloadState()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DownloadState(null, 0, 0, false, null)
            )

    private val _splashBitmap = MutableStateFlow<Bitmap?>(null)
    val splashBitmap: StateFlow<Bitmap?> = _splashBitmap.asStateFlow()

    /**
     * true — нет активных роликов с готовым локальным файлом (пустой плейлист или всё ещё качается).
     */
    val showIdleSplash: StateFlow<Boolean> =
        app.playlistRepository.observePlayableEntries()
            .map { entries ->
                entries.isEmpty() || entries.none { e ->
                    val v = e.video ?: return@none false
                    val p = v.localPath ?: return@none false
                    File(p).exists()
                }
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _splashBitmap.value = SplashAssetLoader.loadBitmap(application)
        }
    }
}

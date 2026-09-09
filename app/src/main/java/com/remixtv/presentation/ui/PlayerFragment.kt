package com.remixtv.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.remixtv.R
import com.remixtv.databinding.FragmentPlayerBinding
import com.remixtv.presentation.viewmodels.PlayerViewModel
import com.remixtv.service.PlaybackService
import com.remixtv.utils.AppDebugLog
import kotlinx.coroutines.launch

class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        AppDebugLog.log("PlayerFragment", "onCreateView")
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        AppDebugLog.log("PlayerFragment", "onResume → привязка плеера при наличии сервиса")
        (activity as? MainActivity)?.getPlayback()?.let { bindPlayback(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    var lastDownloadKey: Triple<Int?, Boolean, String?>? = null
                    viewModel.downloadState.collect { st ->
                        val key = Triple(st.videoId, st.isDownloading, st.lastError)
                        if (key != lastDownloadKey) {
                            lastDownloadKey = key
                            AppDebugLog.log(
                                "PlayerUI",
                                "downloadState id=${st.videoId} loading=${st.isDownloading} err=${st.lastError}"
                            )
                        }
                        val show = st.isDownloading && st.videoId != null
                        binding.downloadPanel.isVisible = show
                        if (show) {
                            val total = st.totalBytes.coerceAtLeast(1L)
                            val pct = ((st.bytesRead * 100) / total).toInt().coerceIn(0, 100)
                            binding.downloadProgress.progress = pct
                        }
                        binding.errorToast.isVisible = st.lastError != null
                    }
                }
                launch {
                    viewModel.splashBitmap.collect { bmp ->
                        binding.splashImage.setImageBitmap(bmp)
                    }
                }
                launch {
                    var lastIdle: Boolean? = null
                    viewModel.showIdleSplash.collect { idle ->
                        if (lastIdle != idle) {
                            lastIdle = idle
                            AppDebugLog.log("PlayerUI", "заставка ожидания: ${if (idle) "да" else "нет"}")
                        }
                        binding.splashImage.isVisible = idle
                        binding.playerView.isVisible = !idle
                    }
                }
            }
        }
    }

    fun bindPlayback(service: PlaybackService) {
        AppDebugLog.log("PlayerFragment", "bindPlayback")
        binding.playerView.player = service.getPlayer()
    }

    fun unbindPlayback() {
        AppDebugLog.log("PlayerFragment", "unbindPlayback")
        binding.playerView.player = null
    }

    override fun onDestroyView() {
        AppDebugLog.log("PlayerFragment", "onDestroyView")
        binding.playerView.player = null
        binding.splashImage.setImageDrawable(null)
        _binding = null
        super.onDestroyView()
    }
}

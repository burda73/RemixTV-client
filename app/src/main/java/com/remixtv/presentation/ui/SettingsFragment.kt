package com.remixtv.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.remixtv.R
import com.remixtv.RemixTVApplication
import com.remixtv.presentation.viewmodels.SettingsViewModel
import com.remixtv.utils.AppDebugLog
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        AppDebugLog.log("SettingsFragment", "onCreatePreferences")
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val app = requireContext().applicationContext as RemixTVApplication
        val pm = app.preferencesManager
        findPreference<EditTextPreference>("pref_server_url")?.text = pm.serverUrl
        findPreference<EditTextPreference>("pref_player_name")?.text = pm.playerName
        findPreference<SwitchPreferenceCompat>("pref_debug_enabled")?.isChecked = pm.debugEnabled
        findPreference<SwitchPreferenceCompat>("pref_boot_start")?.isChecked = pm.bootStartEnabled
        findPreference<EditTextPreference>("pref_cache_mb")?.text = pm.cacheMaxMb.toString()
        findPreference<ListPreference>("pref_sync_interval")?.value =
            pm.autoSyncIntervalMinutes.toString()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppDebugLog.log("SettingsFragment", "onViewCreated")

        findPreference<EditTextPreference>("pref_server_url")?.setOnPreferenceChangeListener { _, v ->
            AppDebugLog.log("SettingsFragment", "изменён URL сервера")
            viewModel.saveServerUrl(v as String)
            true
        }
        findPreference<EditTextPreference>("pref_player_name")?.setOnPreferenceChangeListener { _, v ->
            AppDebugLog.log("SettingsFragment", "изменено имя проигрывателя")
            viewModel.savePlayerName(v as String)
            (activity as? MainActivity)?.refreshPlaybackNotificationMetadata()
            true
        }
        findPreference<SwitchPreferenceCompat>("pref_debug_enabled")?.setOnPreferenceChangeListener { _, v ->
            val on = v as Boolean
            AppDebugLog.log("SettingsFragment", "отладка: $on")
            viewModel.setDebugEnabled(on)
            (activity as? MainActivity)?.applyDebugPanelFromPrefs()
            true
        }
        findPreference<SwitchPreferenceCompat>("pref_boot_start")?.setOnPreferenceChangeListener { _, v ->
            AppDebugLog.log("SettingsFragment", "автозапуск: $v")
            viewModel.setBootStartEnabled(v as Boolean)
            true
        }
        findPreference<EditTextPreference>("pref_cache_mb")?.setOnPreferenceChangeListener { _, v ->
            AppDebugLog.log("SettingsFragment", "изменён лимит кэша")
            val n = (v as String).toIntOrNull() ?: return@setOnPreferenceChangeListener false
            viewModel.saveCacheMaxMb(n)
            true
        }
        findPreference<ListPreference>("pref_sync_interval")?.setOnPreferenceChangeListener { _, v ->
            AppDebugLog.log("SettingsFragment", "изменён интервал синхронизации")
            val n = (v as String).toIntOrNull() ?: return@setOnPreferenceChangeListener false
            viewModel.saveAutoSyncMinutes(n)
            true
        }
        findPreference<Preference>("pref_sync_now")?.setOnPreferenceClickListener {
            AppDebugLog.log("SettingsFragment", "нажата «Синхронизировать сейчас»")
            viewModel.syncNow()
            true
        }

        findPreference<Preference>("pref_device_ip")?.summary = getString(
            R.string.pref_device_ip_summary,
            viewModel.deviceIp ?: "—"
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.status.collect { msg ->
                    AppDebugLog.log("SettingsFragment", "status summary=${msg ?: ""}")
                    findPreference<Preference>("pref_status")?.summary = msg ?: ""
                }
            }
        }
    }
}

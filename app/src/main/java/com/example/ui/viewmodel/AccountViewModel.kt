package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.preferences.AppThemeMode
import com.example.domain.AppUpdateDownloadState
import com.example.domain.GitHubReleaseInfo
import com.example.domain.GitHubReleaseUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val preferencesRepository: AppPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferences()
        )

    val cachedAvailableUpdate: StateFlow<GitHubReleaseInfo?> = preferencesRepository.preferencesFlow
        .map { prefs -> GitHubReleaseUpdater.parseCachedAvailableUpdate(prefs) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    private val _updateReleaseInfo = MutableStateFlow<GitHubReleaseInfo?>(null)
    val updateReleaseInfo: StateFlow<GitHubReleaseInfo?> = _updateReleaseInfo.asStateFlow()

    private val _downloadState = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    fun openCachedUpdateSheet() {
        val cached = cachedAvailableUpdate.value ?: return
        _downloadState.value = AppUpdateDownloadState.Idle
        _updateReleaseInfo.value = cached
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAmoledDark(enabled) }
    }

    fun setUseSystemFont(useSystem: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseSystemFont(useSystem) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEffectsEnabled(enabled) }
    }

    fun setTripBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTripBubbleEnabled(enabled) }
    }

    fun markWalkthroughSeen() {
        viewModelScope.launch { preferencesRepository.setHasSeenOnboardingWalkthrough(true) }
    }

    fun resetOnboardingWalkthrough() {
        viewModelScope.launch { preferencesRepository.setHasSeenOnboardingWalkthrough(false) }
    }

    fun checkForUpdates(force: Boolean) {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            _updateStatusMessage.value = null
            val result = GitHubReleaseUpdater.checkForUpdate(preferencesRepository, force = force)
            _checkingUpdate.value = false
            result.onSuccess { info ->
                if (info != null) {
                    _downloadState.value = AppUpdateDownloadState.Idle
                    _updateReleaseInfo.value = info
                } else if (force) {
                    _updateStatusMessage.value = "You are already on the latest Billi release."
                }
            }.onFailure { err ->
                if (force) {
                    _updateStatusMessage.value = err.localizedMessage ?: "Could not reach GitHub Releases."
                }
            }
        }
    }

    fun startApkDownload(context: Context) {
        val release = _updateReleaseInfo.value ?: return
        if (_downloadState.value is AppUpdateDownloadState.Downloading) return
        viewModelScope.launch {
            _downloadState.value = AppUpdateDownloadState.Downloading(0f, 0L, release.apkSizeBytes)
            val result = GitHubReleaseUpdater.downloadApk(
                context = context.applicationContext,
                release = release,
                onProgress = { progressState ->
                    _downloadState.value = progressState
                }
            )
            result.onSuccess { downloaded ->
                _downloadState.value = AppUpdateDownloadState.ReadyToInstall(
                    apkFile = downloaded.file,
                    checksumVerified = downloaded.checksumVerified
                )
            }.onFailure { err ->
                _downloadState.value = AppUpdateDownloadState.Error(
                    err.localizedMessage ?: "Failed to download APK."
                )
            }
        }
    }

    fun dismissUpdateSheet() {
        _updateReleaseInfo.value = null
        _downloadState.value = AppUpdateDownloadState.Idle
    }

    fun clearStatusMessage() {
        _updateStatusMessage.value = null
    }
}

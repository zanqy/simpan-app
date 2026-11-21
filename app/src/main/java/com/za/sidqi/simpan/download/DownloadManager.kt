package com.za.sidqi.simpan.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Fetching(val url: String) : DownloadState
    data class Running(val url: String, val title: String, val progress: Float, val eta: Long) : DownloadState
    data class Done(val url: String, val title: String, val path: String, val durationSec: Int, val sizeBytes: Long) : DownloadState
    data class Failed(val url: String, val message: String) : DownloadState
    data object Canceled : DownloadState
}

object DownloadManager {
    const val PROCESS_ID = "simpan_download_1"
    const val ACTION_START = "com.za.sidqi.simpan.START"
    const val ACTION_CANCEL = "com.za.sidqi.simpan.CANCEL"
    const val EXTRA_URL = "url"

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun update(state: DownloadState) {
        _state.value = state
    }

    fun isBusy(): Boolean = _state.value is DownloadState.Fetching || _state.value is DownloadState.Running

    fun start(context: Context, url: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_URL, url)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancel(context: Context) {
        com.yausername.youtubedl_android.YoutubeDL.destroyProcessById(PROCESS_ID)
        update(DownloadState.Canceled)
        val intent = Intent(context, DownloadService::class.java).apply { action = ACTION_CANCEL }
        runCatching { context.stopService(intent) }
    }
}

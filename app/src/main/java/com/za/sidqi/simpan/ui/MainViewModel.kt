package com.za.sidqi.simpan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.za.sidqi.simpan.data.AppDatabase
import com.za.sidqi.simpan.data.DownloadRecord
import com.za.sidqi.simpan.download.DownloadManager
import com.za.sidqi.simpan.download.DownloadState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val state = DownloadManager.state

    val history = AppDatabase.get(app).dao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun validate(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return "Link tidak boleh kosong"
        val uri = runCatching { Uri.parse(value) }.getOrNull()
            ?: return "Link tidak valid"
        if (uri.scheme != "http" && uri.scheme != "https") return "Link harus http(s)"
        val host = uri.host ?: return "Link tidak valid"
        val ok = host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host.endsWith(".youtu.be")
        return if (ok) null else "Hanya link YouTube yang didukung"
    }

    fun submit(url: String) = DownloadManager.start(getApplication(), url.trim())

    fun cancel() = DownloadManager.cancel(getApplication())

    fun delete(record: DownloadRecord) {
        viewModelScope.launch { AppDatabase.get(getApplication()).dao().delete(record.id) }
    }
}

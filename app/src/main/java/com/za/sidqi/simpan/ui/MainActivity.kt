package com.za.sidqi.simpan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.za.sidqi.simpan.R
import com.za.sidqi.simpan.data.DownloadRecord
import com.za.sidqi.simpan.download.DownloadState
import java.io.File

private val Bg = Color(0xFF0B0D0B)
private val Surface = Color(0xFF171B17)
private val Ink = Color(0xFFF3F0E7)
private val Muted = Color(0xFFB7BFB4)
private val Coral = Color(0xFFFF7654)
private val Amber = Color(0xFFF3C45C)
private val Mint = Color(0xFF7CE0B4)
private val Line = Color(0xFF333B33)
private val OnAccent = Color(0xFF14180F)

private val Fraunces = FontFamily(Font(R.font.fraunces))
private val Plex = FontFamily(
    Font(R.font.plex_regular),
    Font(R.font.plex_semibold, FontWeight.SemiBold)
)
private val DmSans = FontFamily(Font(R.font.dm_sans))

class MainActivity : ComponentActivity() {
    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsState()
            val accent = when (state) {
                is DownloadState.Done -> Mint
                is DownloadState.Running, is DownloadState.Fetching -> Amber
                else -> Coral
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = accent,
                    background = Bg,
                    surface = Surface,
                    onPrimary = OnAccent,
                    onBackground = Ink,
                    onSurface = Ink
                )
            ) {
                SimpanScreen(vm = vm, state = state)
            }
        }
    }
}

@Composable
fun SimpanScreen(vm: MainViewModel, state: DownloadState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val history by vm.history.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Text("Simpan.", fontFamily = Fraunces, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("YOUTUBE → MP3", fontFamily = Plex, fontSize = 10.sp, color = Muted, letterSpacing = 2.sp)
        Spacer(Modifier.height(26.dp))

        Text(
            when (state) {
                is DownloadState.Fetching -> "Mengambil info lagu"
                is DownloadState.Running -> "Sedang diunduh"
                is DownloadState.Done -> "Selesai"
                is DownloadState.Failed -> "Gagal"
                is DownloadState.Canceled -> "Dibatalkan"
                is DownloadState.Idle -> "Tempel linknya"
            }.uppercase(),
            fontFamily = Plex,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (state) {
                is DownloadState.Done -> Mint
                is DownloadState.Running, is DownloadState.Fetching -> Amber
                else -> Coral
            }
        )
        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            is DownloadState.Running -> RunningCard(s, vm)
            is DownloadState.Done -> DoneBanner(s)
            is DownloadState.Failed -> MessageBanner(s.message, Coral)
            is DownloadState.Canceled -> MessageBanner("Unduhan dibatalkan", Coral)
            else -> Unit
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .background(Color(0xFF101410))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = input,
                onValueChange = { value -> input = value; error = null },
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 13.sp, fontFamily = Plex),
                modifier = Modifier.fillMaxWidth()
            )
            if (input.isBlank()) {
                Text(
                    "https://music.youtube.com/watch?v=…",
                    color = Color(0xFF737C72),
                    fontSize = 12.sp,
                    fontFamily = Plex
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboard.getText()?.let { text -> input = text.toString(); error = null } },
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("TEMPEL", fontFamily = Plex, fontSize = 11.sp, color = Muted) }

            Button(
                onClick = {
                    error = vm.validate(input)
                    if (error == null) vm.submit(input)
                },
                enabled = state !is DownloadState.Running && state !is DownloadState.Fetching,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral,
                    contentColor = OnAccent
                )
            ) { Text("↓  UNDUH MP3", fontFamily = Plex, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        val errorText = error
        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText, color = Coral, fontSize = 12.sp, fontFamily = DmSans)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "MP3 256 kbps · langsung masuk folder Downloads",
            color = Muted,
            fontSize = 11.sp,
            fontFamily = Plex
        )

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Riwayat", fontFamily = Fraunces, fontSize = 17.sp, color = Ink)
            Text("${history.size} lagu", fontFamily = Plex, fontSize = 10.sp, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        if (history.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Line, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada unduhan", fontFamily = Fraunces, fontSize = 14.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Lagu yang selesai akan tersimpan di sini.",
                        fontFamily = DmSans,
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.id }) { record ->
                    HistoryRow(record = record, vm = vm, context = context)
                }
            }
        }
    }
}

@Composable
private fun RunningCard(state: DownloadState.Running, vm: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(state.title, fontFamily = DmSans, fontSize = 14.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text(
                "${state.progress.toInt().coerceIn(0, 100)}%" + if (state.eta > 0) "  ·  ETA ${state.eta}s" else "",
                fontFamily = Fraunces,
                fontSize = 30.sp,
                color = Amber
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                color = Amber,
                trackColor = Line
            )
            if (state.lastLine.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.lastLine,
                    color = Muted,
                    fontSize = 10.sp,
                    fontFamily = Plex,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { vm.cancel() },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("BATALKAN", fontFamily = Plex, fontSize = 11.sp, color = Ink) }
        }
    }
}

@Composable
private fun DoneBanner(state: DownloadState.Done) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13221B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(50.dp)).background(Mint),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = OnAccent, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("Tersimpan di Downloads", fontFamily = Fraunces, fontSize = 14.sp, color = Mint)
                Text(
                    "${state.title}.mp3 · ${formatSize(state.sizeBytes)}",
                    fontFamily = Plex,
                    fontSize = 11.sp,
                    color = Color(0xFF9CC9B2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, modifier = Modifier.padding(14.dp), fontFamily = DmSans, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun HistoryRow(record: DownloadRecord, vm: MainViewModel, context: android.content.Context) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Coral),
            contentAlignment = Alignment.Center
        ) {
            Text(
                record.title.take(2).uppercase(),
                color = OnAccent,
                fontFamily = Plex,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.title,
                fontFamily = DmSans,
                fontSize = 13.sp,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatDuration(record.durationSec)} · ${formatSize(record.sizeBytes)}",
                fontFamily = Plex,
                fontSize = 11.sp,
                color = Muted
            )
        }
        TextButtonGlyph("▷") {
            openFile(context, record)
        }
        TextButtonGlyph("↗") {
            val uri = uriFor(context, record) ?: return@TextButtonGlyph
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan MP3")) }
        }
        TextButtonGlyph("×") { vm.delete(record) }
    }
}

private fun uriFor(context: android.content.Context, record: DownloadRecord): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(record.outputPath))
}.getOrNull()

private fun openFile(context: android.content.Context, record: DownloadRecord) {
    val uri = uriFor(context, record) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/mpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun TextButtonGlyph(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Text(glyph, color = Ink, fontSize = 16.sp)
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

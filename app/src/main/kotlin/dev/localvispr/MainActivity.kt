package dev.localvispr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.localvispr.accessibility.VisprAccessibilityService
import dev.localvispr.models.DownloadStatus
import dev.localvispr.models.ModelCatalog
import dev.localvispr.models.ModelSpec
import dev.localvispr.onboarding.OnboardingScreen
import dev.localvispr.service.FloatingBubbleService
import dev.localvispr.settings.LanguagePreference
import dev.localvispr.text.DictationStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.get(this)
        setContent {
            MaterialTheme {
                val settings by container.settings.state.collectAsState()
                var viewingOnboarding by remember { mutableStateOf(false) }

                if (!settings.onboardingCompleted || viewingOnboarding) {
                    OnboardingScreen(
                        onFinish = {
                            container.settings.setOnboardingCompleted(true)
                            viewingOnboarding = false
                        }
                    )
                } else {
                    MainScreen(
                        container = container,
                        onRestartOnboarding = { viewingOnboarding = true },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(container: AppContainer, onRestartOnboarding: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.state.collectAsState()
    val downloads by container.downloader.statusByModel.collectAsState()
    val bubbleRunning by FloatingBubbleService.running.collectAsState()
    val accessibilityInstance by VisprAccessibilityService.instance.collectAsState()
    val lastResult by FloatingBubbleService.lastResult.collectAsState()

    // Re-read permission state whenever the user returns from system settings.
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasMicPermission =
        remember(refreshTick) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    val hasOverlayPermission = remember(refreshTick) { Settings.canDrawOverlays(context) }
    val hasAccessibility = accessibilityInstance != null

    // Automatically keep the background bubble service alive as soon as the
    // required permissions are granted — no manual "Start bubble" button needed.
    DisposableEffect(hasMicPermission, hasOverlayPermission, bubbleRunning) {
        if (hasMicPermission && hasOverlayPermission && !bubbleRunning) {
            runCatching { FloatingBubbleService.start(context) }
        }
        onDispose {}
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshTick++
        }

    // If the active STT model is missing on startup, ask the user whether to download
    // the small default model (~110 MB) right away.
    var dismissedFirstRunPrompt by remember { mutableStateOf(false) }
    val activeSttInstalled =
        remember(settings.sttModelId, downloads, refreshTick) {
            container.models.isInstalled(settings.sttModel)
        }
    if (
        !activeSttInstalled &&
            !dismissedFirstRunPrompt &&
            downloads[settings.sttModel.id] !is DownloadStatus.Running
    ) {
        FirstRunModelDialog(
            sttSpec = settings.sttModel,
            llmSpec = settings.llmModel,
            onConfirm = {
                dismissedFirstRunPrompt = true
                scope.launch {
                    container.downloader.download(settings.sttModel)
                    settings.llmModel?.let { llm ->
                        if (!container.models.isInstalled(llm)) container.downloader.download(llm)
                    }
                    refreshTick++
                }
            },
            onDismiss = { dismissedFirstRunPrompt = true },
        )
    }

    var scratchPad by remember { mutableStateOf("") }
    var dictInput by
        remember(settings.dictionaryWords) {
            mutableStateOf(settings.dictionaryWords.joinToString(", "))
        }

    Scaffold(topBar = { TopAppBar(title = { Text("LocalVispr") }) }) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Floating bubble control + permissions
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. System-Integration & Blase",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    PermissionRow(
                        label = "Mikrofon",
                        granted = hasMicPermission,
                        actionLabel = "Erlauben",
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            )
                        },
                    )
                    PermissionRow(
                        label = "Über anderen Apps einblenden",
                        granted = hasOverlayPermission,
                        actionLabel = "Öffnen",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                    )
                    PermissionRow(
                        label = "Bedienungshilfe (Text einfügen)",
                        granted = hasAccessibility,
                        actionLabel = "Aktivieren",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )

                    // Switches for bubble behavior
                    SettingSwitchRow(
                        label = "Live-Text während des Sprechens",
                        checked = settings.liveStreamingEnabled,
                        onCheckedChange = { container.settings.setLiveStreamingEnabled(it) },
                    )
                    SettingSwitchRow(
                        label = "Haptik",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { container.settings.setHapticsEnabled(it) },
                    )
                    SettingSwitchRow(
                        label = "Shake-to-Dictate",
                        checked = settings.shakeToDictateEnabled,
                        onCheckedChange = { container.settings.setShakeToDictateEnabled(it) },
                    )

                    // Snooze buttons
                    val isSnoozed = settings.snoozedUntilEpochMs > System.currentTimeMillis()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isSnoozed) {
                            OutlinedButton(onClick = { container.settings.setSnoozedUntil(0L) }) {
                                Text("Snooze aufheben", maxLines = 1, softWrap = false)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    container.settings.setSnoozedUntil(
                                        System.currentTimeMillis() + 15 * 60 * 1000L
                                    )
                                }
                            ) {
                                Text("Snooze 15m", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    container.settings.setSnoozedUntil(
                                        System.currentTimeMillis() + 60 * 60 * 1000L
                                    )
                                }
                            ) {
                                Text("Snooze 1h", maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onRestartOnboarding,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Onboarding-Tour ansehen", maxLines = 1, softWrap = false)
                    }
                }
            }

            // 2. Language & style selection
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Diktat-Sprache & Stil", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LanguagePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.languagePreference == pref,
                                onClick = { container.settings.setLanguagePreference(pref) },
                                label = { Text(pref.label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DictationStyle.entries.forEach { style ->
                            FilterChip(
                                selected = settings.dictationStyle == style,
                                onClick = { container.settings.setDictationStyle(style) },
                                label = { Text(style.label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }

            // 3. Speech-to-text models
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "3. Spracherkennung (Sherpa-ONNX Whisper)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ModelCatalog.sttModels.forEach { spec ->
                        ModelRow(
                            spec = spec,
                            selected = settings.sttModelId == spec.id,
                            installed =
                                remember(downloads, refreshTick) {
                                    container.models.isInstalled(spec)
                                },
                            status = downloads[spec.id] ?: DownloadStatus.Idle,
                            onSelect = { container.settings.setSttModel(spec) },
                            onDownload = {
                                scope.launch {
                                    container.downloader.download(spec)
                                    refreshTick++
                                }
                            },
                            onDelete = {
                                container.transcriber.close()
                                container.models.delete(spec)
                                refreshTick++
                            },
                        )
                    }
                }
            }

            // 4. On-device LLM cleanup models
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "4. Text-Glättung (On-Device LLM via LiteRT-LM)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.llmModelId == null,
                            onClick = { container.settings.setLlmModel(null) },
                        )
                        Text("Nur Regel-Filter (kein LLM, 0 MB)")
                    }
                    ModelCatalog.llmModels.forEach { spec ->
                        ModelRow(
                            spec = spec,
                            selected = settings.llmModelId == spec.id,
                            installed =
                                remember(downloads, refreshTick) {
                                    container.models.isInstalled(spec)
                                },
                            status = downloads[spec.id] ?: DownloadStatus.Idle,
                            onSelect = { container.settings.setLlmModel(spec) },
                            onDownload = {
                                scope.launch {
                                    container.downloader.download(spec)
                                    refreshTick++
                                }
                            },
                            onDelete = {
                                container.llmEngine.close()
                                container.models.delete(spec)
                                refreshTick++
                            },
                        )
                    }
                }
            }

            // 5. Custom vocabulary + scratchpad for immediate testing
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "5. Eigenes Wörterbuch & Testfeld",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = dictInput,
                        onValueChange = {
                            dictInput = it
                            container.settings.setDictionaryWords(it)
                        },
                        label = { Text("Eigene Begriffe (Komma-getrennt)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = scratchPad,
                        onValueChange = { scratchPad = it },
                        label = { Text("Hier tippen und Blase antippen zum Testen") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (lastResult.isNotEmpty()) {
                        Text(
                            text = "Letztes Diktat: $lastResult",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // 6. Dictation history
            var historyQuery by remember { mutableStateOf("") }
            val clipboardManager = LocalClipboardManager.current
            val historyEntries =
                remember(historyQuery, refreshTick, lastResult) {
                    container.history.search(historyQuery)
                }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("6. Diktat-Historie", style = MaterialTheme.typography.titleMedium)
                        if (historyEntries.isNotEmpty() || historyQuery.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    container.history.clear()
                                    refreshTick++
                                }
                            ) {
                                Text("Leeren", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = historyQuery,
                        onValueChange = { historyQuery = it },
                        label = { Text("Historie durchsuchen") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (historyEntries.isEmpty()) {
                        Text(
                            text =
                                if (historyQuery.isEmpty()) "Noch keine Diktate gespeichert."
                                else "Keine Treffer für die Suche.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        historyEntries.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = entry.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (entry.languageCode.isNotEmpty()) {
                                        Text(
                                            text = entry.languageCode.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(entry.text))
                                    },
                                    contentPadding =
                                        PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Kopieren", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (granted) "✓ $label" else "• $label",
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        if (!granted) {
            OutlinedButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(actionLabel, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    selected: Boolean,
    installed: Boolean,
    status: DownloadStatus,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.padding(end = 8.dp)) {
                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(spec.subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(4.dp))
            when (status) {
                is DownloadStatus.Running -> Text("Lädt…", maxLines = 1, softWrap = false)
                else ->
                    if (installed) {
                        TextButton(onClick = onDelete) {
                            Text("Löschen", maxLines = 1, softWrap = false)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("${spec.approxSizeMb} MB laden", maxLines = 1, softWrap = false)
                        }
                    }
            }
        }
        if (status is DownloadStatus.Running) {
            val fraction = status.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (status is DownloadStatus.Failed) {
            Text(
                text = "Fehler: ${status.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FirstRunModelDialog(
    sttSpec: ModelSpec,
    llmSpec: ModelSpec?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalMb = sttSpec.approxSizeMb + (llmSpec?.approxSizeMb ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lokale Sprachmodelle laden?") },
        text = {
            Text(
                "Auf diesem Gerät ist noch kein Modell für die Offline-Spracherkennung " +
                    "installiert. Soll LocalVispr jetzt ${sttSpec.title}" +
                    (llmSpec?.let { " und ${it.title}" } ?: "") +
                    " (~$totalMb MB, ohne Account) herunterladen?"
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Herunterladen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Später") } },
    )
}

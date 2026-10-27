package dev.localvispr.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.localvispr.accessibility.VisprAccessibilityService

/**
 * Onboarding screen for LocalVispr, heavily inspired by the reverse-engineered workflow of the
 * reference APK (Wispr Flow) while strictly tailored for 100% local, private, and accountless
 * on-device operation.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var flowStep by remember { mutableStateOf(OnboardingStep.INTRO) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check system permissions on lifecycle resume when returning from settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasMic =
        remember(refreshTick) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    val hasOverlay = remember(refreshTick) { Settings.canDrawOverlays(context) }
    val hasAccessibility = remember(refreshTick) { VisprAccessibilityService.isEnabled(context) }
    val hasBatteryExemption =
        remember(refreshTick) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OnboardingNavigationFooter(
                currentStep = flowStep,
                canProceed =
                    when (flowStep) {
                        OnboardingStep.PERMISSIONS -> hasMic && hasOverlay
                        else -> true
                    },
                onNext = {
                    if (flowStep.hasNext) {
                        flowStep = OnboardingStep.fromIndex(flowStep.index + 1)
                    } else {
                        onFinish()
                    }
                },
                onBack = {
                    if (flowStep.hasPrevious) {
                        flowStep = OnboardingStep.fromIndex(flowStep.index - 1)
                    }
                },
                onSkip = { onFinish() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Segmented top progress bar (inspired by Wispr Flow's 4-5 segment indicator).
            OnboardingProgressBar(
                totalSteps = OnboardingStep.TOTAL_STEPS,
                currentStepIndex = flowStep.index,
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            )

            when (flowStep) {
                OnboardingStep.INTRO -> IntroStepContent()
                OnboardingStep.PLAYGROUND -> PlaygroundStepContent()
                OnboardingStep.PERMISSIONS ->
                    PermissionsStepContent(
                        context = context,
                        hasMic = hasMic,
                        hasOverlay = hasOverlay,
                        hasAccessibility = hasAccessibility,
                        hasBatteryExemption = hasBatteryExemption,
                    )
                OnboardingStep.READY -> ReadyStepContent()
            }
        }
    }
}

@Composable
private fun OnboardingProgressBar(
    totalSteps: Int,
    currentStepIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until totalSteps) {
            val isPassedOrCurrent = i <= currentStepIndex
            Box(
                modifier =
                    Modifier.weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isPassedOrCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
            )
        }
    }
}

@Composable
private fun IntroStepContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // App Identity Visual: Stylized Cyberpunk Neon Ring
        Box(
            modifier =
                Modifier.size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F081C))
                    .border(3.dp, Color(0xFFFF1E9A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFE9D5FF),
            )
        }

        Text(
            text = "Smart-Dictation\n100 % Lokale KI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text =
                "Sprich frei heraus. LocalVispr verwandelt deine Stimme in präzisen, " +
                    "vollständig bereinigten Text – ganz ohne Cloud, ohne Benutzerkonto und 100 % privat auf deinem Gerät.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureCard(
            icon = "🔒",
            title = "100 % Privat & Offline",
            description =
                "Weder Audiodaten noch Transkripte verlassen dein Gerät. Funktioniert überall ohne Internet.",
        )

        FeatureCard(
            icon = "✂️",
            title = "Automatische Text-Glättung",
            description =
                "Füllwörter (ähm, äh, halt), Zögern und Stottern werden vor dem Einfügen automatisch entfernt.",
        )

        FeatureCard(
            icon = "🌐",
            title = "App-übergreifend verfügbar",
            description =
                "Funktioniert in jeder Android-App (Messenger, E-Mail, Notizen, Browser) über die schwebende Blase.",
        )
    }
}

@Composable
private fun FeatureCard(
    icon: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = icon, fontSize = 28.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaygroundStepContent() {
    var textValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Probier es aus!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text =
                "Tippe in das Testfeld unten. Wenn der Dienst aktiv ist, dockt sich " +
                    "die Blase an die Tastatur. Tippe auf die Blase, sprich los und tippe auf Fertig.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Mock Messenger Card (analog zu Wispr Flow's TIYOnboardingStep)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "💬 Chat-Vorschau",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFA6ADC8),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF313244))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Wann hast du morgen Zeit für die Besprechung?",
                        color = Color(0xFFCDD6F4),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = {
                        Text(
                            text = "Hier tippen oder per Blase diktieren...",
                            color = Color(0xFF6C7086),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💡 So funktioniert's im Alltag:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        "1. Beliebige App öffnen & Textfeld antippen\n" +
                            "2. Auf die schwebende 'V'-Blase tippen\n" +
                            "3. Sprechen – Füllwörter werden geglättet und Text direkt eingefügt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionsStepContent(
    context: Context,
    hasMic: Boolean,
    hasOverlay: Boolean,
    hasAccessibility: Boolean,
    hasBatteryExemption: Boolean,
) {
    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Berechtigungen einrichten",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text =
                "Damit LocalVispr systemweit Text einfügen kann, benötigt Android " +
                    "einige Berechtigungen. Du kannst sie hier direkt erteilen:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 1. Microphone
        PermissionRow(
            icon = "🎤",
            title = "Mikrofon",
            description = "Ermöglicht die lokale Audio-Aufnahme für das Whisper-Modell.",
            isGranted = hasMic,
            onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            actionLabel = "Erlauben",
        )

        // 2. Overlay
        PermissionRow(
            icon = "🪟",
            title = "Über anderen Apps einblenden",
            description = "Erlaubt der schwebenden Blase, über Messenger und Browsern zu schweben.",
            isGranted = hasOverlay,
            onAction = {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                context.startActivity(intent)
            },
            actionLabel = "Öffnen",
        )

        // 3. Accessibility Service (with vital reverse-engineered guidance)
        PermissionRow(
            icon = "⌨️",
            title = "Bedienungshilfe (Text-Einfügung)",
            description = "Wird benötigt, um fertigen Text direkt in Eingabefelder zu schreiben.",
            isGranted = hasAccessibility,
            onAction = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            actionLabel = "Aktivieren",
        )

        // Detailed guidance box extracted from REA findings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C08)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⚠️ Wichtige Anleitung für die Bedienungshilfe:",
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                for (step in AccessibilityGuidance.steps) {
                    Text(
                        text = step.instruction,
                        color = if (step.isWarning) Color(0xFFFCA5A5) else Color(0xFFFEF3C7),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // 4. Battery Optimization
        PermissionRow(
            icon = "🔋",
            title = "Akku-Optimierung ausnehmen",
            description = "Verhindert, dass Android den Hintergrunddienst unerwartet beendet.",
            isGranted = hasBatteryExemption,
            onAction = {
                val intent =
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    )
                context.startActivity(intent)
            },
            actionLabel = "Zulassen",
        )
    }
}

@Composable
private fun PermissionRow(
    icon: String,
    title: String,
    description: String,
    isGranted: Boolean,
    onAction: () -> Unit,
    actionLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isGranted) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
            ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) "✓ Erteilt" else "• Fehlt",
                        color =
                            if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isGranted) {
                Button(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
                    Text(text = actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ReadyStepContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = "🎉", fontSize = 54.sp)

        Text(
            text = "Du bist startklar!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            text =
                "LocalVispr ist eingerichtet. Wenn du auf 'Starten' tippst, " +
                    "wird der Hintergrunddienst aktiviert.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        FeatureCard(
            icon = "👀",
            title = "Intelligente Sichtbarkeit",
            description =
                "Die Blase bleibt verborgen und erscheint automatisch, sobald ein bearbeitbares Textfeld aktiv ist.",
        )

        FeatureCard(
            icon = "⚙️",
            title = "Jederzeit anpassbar",
            description =
                "In der App kannst du Sprachmodelle wählen, Diktat-Stile wechseln und Fachwörter ins Wörterbuch eintragen.",
        )
    }
}

@Composable
private fun OnboardingNavigationFooter(
    currentStep: OnboardingStep,
    canProceed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep.hasPrevious) {
                OutlinedButton(onClick = onBack) { Text(text = "Zurück") }
            } else {
                TextButton(onClick = onSkip) {
                    Text(text = "Überspringen", color = MaterialTheme.colorScheme.outline)
                }
            }

            Button(onClick = onNext, enabled = canProceed) {
                Text(
                    text =
                        if (currentStep == OnboardingStep.READY) "Starten & Loslegen" else "Weiter"
                )
            }
        }
    }
}

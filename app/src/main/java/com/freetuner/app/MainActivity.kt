package com.freetuner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.freetuner.app.audio.AdvancedMicrophonePitchMonitor
import com.freetuner.app.audio.ChordDetectionResult
import com.freetuner.app.audio.ChordDetector
import com.freetuner.app.audio.ChordTemplates
import com.freetuner.app.audio.PitchDetectionResult
import com.freetuner.app.audio.PitchDetector
import com.freetuner.app.audio.PitchMath
import com.freetuner.app.audio.ReferenceTonePlayer
import com.freetuner.app.audio.YinPitchDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private val AppBackground = Color(0xFF07090F)
private val CardBackground = Color(0xFF2E3948)
private val AccentBlue = Color(0xFF69A6FF)
private val AccentGreen = Color(0xFF8BEA3E)
private val AccentTeal = Color(0xFF18A6C9)
private val AccentRed = Color(0xFFFF3355)
private val AccentGold = Color(0xFFF5C518)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_FreeTuner)
        super.onCreate(savedInstanceState)
        setContent {
            FreeTunerApp()
        }
    }
}

enum class FreeTunerTab {
    Tuner,
    Metronome,
    Tools,
    Settings,
}

private fun tabSymbol(tab: FreeTunerTab): String = when (tab) {
    FreeTunerTab.Tuner -> "◉"
    FreeTunerTab.Metronome -> "♪"
    FreeTunerTab.Tools -> "✦"
    FreeTunerTab.Settings -> "⚙"
}

internal fun shouldFlashNoteForStatus(tuningStatus: String): Boolean = tuningStatus == "In tune"

data class InstrumentPreset(
    val name: String,
    val tuningLabel: String,
    val strings: List<String>,
    val description: String,
)

private val presets = listOf(
    InstrumentPreset("Guitar", "Standard", listOf("E2", "A2", "D3", "G3", "B3", "E4"), "Six-string standard tuning"),
    InstrumentPreset("Guitar", "Drop D", listOf("D2", "A2", "D3", "G3", "B3", "E4"), "Fast rock retune"),
    InstrumentPreset("Guitar", "Open G", listOf("D2", "G2", "D3", "G3", "B3", "D4"), "Open slide setup"),
    InstrumentPreset("Guitar", "DADGAD", listOf("D2", "A2", "D3", "G3", "A3", "D4"), "Folk alternate tuning"),
    InstrumentPreset("Ukulele", "Standard", listOf("G4", "C4", "E4", "A4"), "Bright four-string tuning"),
    InstrumentPreset("Bass", "Standard", listOf("E1", "A1", "D2", "G2"), "Four-string bass"),
    InstrumentPreset("Violin", "Standard", listOf("G3", "D4", "A4", "E5"), "Classical string setup"),
    InstrumentPreset("Viola", "Standard", listOf("C3", "G3", "D4", "A4"), "Warm mid-range tuning"),
    InstrumentPreset("Cello", "Standard", listOf("C2", "G2", "D3", "A3"), "Orchestral low strings"),
    InstrumentPreset("Mandolin", "Standard", listOf("G3", "D4", "A4", "E5"), "Double-course tuning"),
    InstrumentPreset("Banjo", "Open G", listOf("G4", "D3", "G3", "B3", "D4"), "Five-string classic"),
    InstrumentPreset("Chromatic", "Any note", emptyList(), "Listen freely for any pitch"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeTunerApp() {
    var selectedTab by rememberSaveable { mutableStateOf(FreeTunerTab.Tuner) }
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var haptics by rememberSaveable { mutableStateOf(true) }
    var noiseFilter by rememberSaveable { mutableStateOf(true) }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var transposeSteps by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkColorScheme(
                primary = AccentGreen,
                secondary = AccentBlue,
                tertiary = AccentTeal,
                background = AppBackground,
                surface = AppBackground,
                surfaceVariant = CardBackground,
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2E7D32),
                secondary = Color(0xFF1565C0),
                tertiary = Color(0xFF00838F),
            )
        },
    ) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FreeTuner", fontWeight = FontWeight.Bold)
                            Text(
                                text = "Precision tuner",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentBlue,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A0D13)) {
                    FreeTunerTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tabSymbol(tab), fontSize = 16.sp) },
                            label = { Text(tab.name) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = AppBackground,
            ) {
                when (selectedTab) {
                    FreeTunerTab.Tuner -> TunerScreen(
                        noiseFilter = noiseFilter,
                        transposeSteps = transposeSteps,
                    )
                    FreeTunerTab.Metronome -> MetronomeScreen()
                    FreeTunerTab.Tools -> ToolsScreen(
                        transpose = transposeSteps,
                        onTransposeChange = { transposeSteps = it },
                    )
                    FreeTunerTab.Settings -> SettingsScreen(
                        darkTheme = darkTheme,
                        haptics = haptics,
                        noiseFilter = noiseFilter,
                        leftHanded = leftHanded,
                        onDarkThemeChanged = { darkTheme = it },
                        onHapticsChanged = { haptics = it },
                        onNoiseFilterChanged = { noiseFilter = it },
                        onLeftHandedChanged = { leftHanded = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun TunerScreen(
    noiseFilter: Boolean,
    transposeSteps: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val monitor = remember {
        AdvancedMicrophonePitchMonitor(
            algorithm = AdvancedMicrophonePitchMonitor.PitchDetectionAlgorithm.PHASE2_YIN
        )
    }

    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { microphoneGranted = it },
    )

    var referenceA4Hz by rememberSaveable { mutableFloatStateOf(440f) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(0.92f) }
    var pitchResult by remember { mutableStateOf<PitchDetectionResult?>(null) }
    var stableFrequencyHz by rememberSaveable { mutableFloatStateOf(0f) }
    var lostSignalFrames by rememberSaveable { mutableIntStateOf(0) }
    var pitchHistory by remember { mutableStateOf(listOf<Int>()) }
    val currentNoiseFilter by rememberUpdatedState(noiseFilter)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val currentReferenceA4Hz by rememberUpdatedState(referenceA4Hz)

    DisposableEffect(microphoneGranted) {
        if (microphoneGranted) {
            monitor.start(scope) { result ->
                val filtered = when {
                    result == null -> null
                    currentNoiseFilter && !PitchDetector.passesSensitivityGate(result, currentSensitivity) -> null
                    currentNoiseFilter && result.confidence < 0.5f -> null
                    else -> result
                }
                pitchResult = result
                if (filtered != null) {
                    lostSignalFrames = 0
                    stableFrequencyHz = YinPitchDetector.smoothFrequencyWithVibrato(
                        previousHz = stableFrequencyHz.takeIf { current -> current > 0f },
                        newHz = filtered.frequencyHz,
                        vibratoDetected = false,
                        factor = 0.3f,
                    )
                    pitchHistory = (pitchHistory + PitchMath.centsOffFromNearest(filtered.frequencyHz, currentReferenceA4Hz)).takeLast(60)
                } else {
                    lostSignalFrames += 1
                    if (lostSignalFrames >= 2) {
                        stableFrequencyHz = 0f
                        pitchHistory = (pitchHistory + 0).takeLast(60)
                    }
                }
            }
        }
        onDispose {
            scope.launch { monitor.stop() }
        }
    }

    val transposeRatio = 2f.pow(transposeSteps / 12f)
    val detectedFrequencyHz = stableFrequencyHz.takeIf { it > 0f }
    val displayFrequencyHz = detectedFrequencyHz?.times(transposeRatio)
    val rawNote = if (displayFrequencyHz != null) PitchMath.closestNoteName(displayFrequencyHz) else "--"
    val detectedNote = PitchMath.prettyNoteName(rawNote)
    val currentMidi = if (displayFrequencyHz != null) PitchMath.closestMidiFromFrequency(displayFrequencyHz) else null
    val flatNeighborNote = currentMidi?.let { PitchMath.prettyNoteName(PitchMath.noteNameForMidi(it - 1)) }
    val sharpNeighborNote = currentMidi?.let { PitchMath.prettyNoteName(PitchMath.noteNameForMidi(it + 1)) }
    val cents = if (displayFrequencyHz != null) PitchMath.centsOffFromNearest(displayFrequencyHz, referenceA4Hz) else 0
    val needlePosition = PitchDetector.centsToNeedle(cents)
    val tuningStatus = when {
        !microphoneGranted -> "Enable mic"
        detectedFrequencyHz == null -> "No signal"
        cents < -8 -> "Tune up"
        cents > 8 -> "Tune down"
        else -> "In tune"
    }
    val signalLabel = PitchDetector.noiseLabel(pitchResult)
    val signalPercent = PitchDetector.amplitudePercent(pitchResult)
    val animatedNeedle by animateFloatAsState(
        targetValue = needlePosition,
        animationSpec = spring(stiffness = 80f, dampingRatio = 0.95f),
        label = "tuner-needle",
    )
    val statusColor by animateColorAsState(
        targetValue = when (tuningStatus) {
            "In tune" -> AccentGreen
            "Tune up", "Tune down" -> AccentBlue
            "Enable mic" -> AccentGold
            "No signal" -> Color.LightGray
            else -> Color.LightGray
        },
        label = "status-color",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TunerMeterCard(
            detectedNote = detectedNote,
            frequencyHz = displayFrequencyHz,
            cents = cents,
            tuningStatus = tuningStatus,
            signalLabel = signalLabel,
            signalPercent = signalPercent,
            needlePosition = animatedNeedle,
            statusColor = statusColor,
            pitchHistory = pitchHistory,
            hasSignal = detectedFrequencyHz != null,
            flatNeighborNote = flatNeighborNote,
            sharpNeighborNote = sharpNeighborNote,
        )

        if (!microphoneGranted) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("Enable microphone")
            }
        }

        CalibrationCard(
            referenceA4Hz = referenceA4Hz,
            onReferenceChange = { referenceA4Hz = it },
            sensitivity = sensitivity,
            onSensitivityChange = { sensitivity = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                modifier = Modifier.weight(1f),
                title = "A4",
                value = "${referenceA4Hz.roundToInt()} Hz",
                color = AccentGreen,
            )
            StatusPill(
                modifier = Modifier.weight(1f),
                title = "Signal",
                value = "$signalPercent%",
                color = AccentTeal,
            )
            StatusPill(
                modifier = Modifier.weight(1f),
                title = "Mic",
                value = if (microphoneGranted) "REC" else "OFF",
                color = if (microphoneGranted) AccentRed else AccentBlue,
            )
        }
    }
}

@Composable
private fun TransposeBanner(
    transposeSteps: Int,
    onOpenTools: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF311B92), Color(0xFF1565C0), AccentGold)),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("If you are using a transposed instrument, set it now.", fontWeight = FontWeight.SemiBold)
                Text("Current transpose: $transposeSteps semitones", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = onOpenTools) {
                Text("Set it now")
            }
        }
    }
}

@Composable
private fun StatusPill(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.76f))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun FeatureChip(label: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TunerMeterCard(
    detectedNote: String,
    frequencyHz: Float?,
    cents: Int,
    tuningStatus: String,
    signalLabel: String,
    signalPercent: Int,
    needlePosition: Float,
    statusColor: Color,
    pitchHistory: List<Int>,
    hasSignal: Boolean,
    flatNeighborNote: String?,
    sharpNeighborNote: String?,
) {
    val shouldFlashNote = shouldFlashNoteForStatus(tuningStatus) && hasSignal
    val infiniteTransition = rememberInfiniteTransition(label = "tuner-note-flash")
    val noteScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "note-scale",
    )
    val noteGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "note-glow",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF394457), Color(0xFF2A3442), Color(0xFF1E2632)),
                    ),
                ),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Tuner", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Signal $signalPercent% • $signalLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    Text(text = tuningStatus, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (frequencyHz != null) "${String.format("%.1f", frequencyHz)} Hz" else "",
                    color = AccentBlue,
                )
                TuningGauge(
                    needlePosition = needlePosition,
                    hasSignal = hasSignal,
                    flatNeighborNote = flatNeighborNote,
                    sharpNeighborNote = sharpNeighborNote,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (shouldFlashNote) AccentGreen.copy(alpha = noteGlow * 0.16f) else Color(0xFF445264),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (hasSignal) detectedNote else "--",
                            modifier = Modifier.scale(if (shouldFlashNote) noteScale else 1f),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (shouldFlashNote) AccentGreen else statusColor,
                        )
                        Text(
                            if (hasSignal) {
                                when {
                                    cents > 0 -> "+${cents}¢"
                                    else -> "${cents}¢"
                                }
                            } else {
                                "0¢"
                            },
                            fontSize = 32.sp,
                            color = if (shouldFlashNote) AccentGreen else statusColor,
                        )
                    }
                }
                PitchHistoryGraph(history = pitchHistory)
            }
        }
    }
}

private const val GaugeStartAngle = 160f
private const val GaugeSweepAngle = 220f

private fun gaugeAngleForCents(cents: Float): Float {
    val fraction = ((cents + 50f) / 100f).coerceIn(0f, 1f)
    return GaugeStartAngle + GaugeSweepAngle * fraction
}

@Composable
private fun TuningGauge(
    needlePosition: Float,
    hasSignal: Boolean,
    flatNeighborNote: String?,
    sharpNeighborNote: String?,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val topPadding = 10.dp
        val bottomPadding = 22.dp
        val radiusDp = maxWidth * 0.44f
        val canvasHeight = topPadding + radiusDp * 1.36f + bottomPadding

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight),
            ) {
            val width = size.width
            val radius = width * 0.44f
            val center = Offset(width / 2f, topPadding.toPx() + radius)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
            val bandWidth = 10.dp.toPx()

            drawArc(
                color = Color(0xFF101A28),
                startAngle = GaugeStartAngle,
                sweepAngle = GaugeSweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - radius * 1.04f, center.y - radius * 1.04f),
                size = androidx.compose.ui.geometry.Size(radius * 2.08f, radius * 2.08f),
            )
            drawArc(
                color = Color(0xFF4B5F79).copy(alpha = 0.3f),
                startAngle = GaugeStartAngle,
                sweepAngle = GaugeSweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = bandWidth + 3.dp.toPx(), cap = StrokeCap.Round),
            )

            // Color bands span the full -50..+50 cent sweep so every angle is covered:
            // red beyond +-28c, gold between +-8c and +-28c, green within +-8c (in tune).
            val redStart = gaugeAngleForCents(-50f)
            val goldFlatStart = gaugeAngleForCents(-28f)
            val greenStart = gaugeAngleForCents(-8f)
            val goldSharpStart = gaugeAngleForCents(8f)
            val redSharpStart = gaugeAngleForCents(28f)
            val sweepEnd = gaugeAngleForCents(50f)

            listOf(
                Triple(redStart, goldFlatStart, AccentRed),
                Triple(goldFlatStart, greenStart, AccentGold),
                Triple(greenStart, goldSharpStart, AccentGreen),
                Triple(goldSharpStart, redSharpStart, AccentGold),
                Triple(redSharpStart, sweepEnd, AccentRed),
            ).forEach { (start, end, color) ->
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = end - start,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = bandWidth, cap = StrokeCap.Butt),
                )
            }

            for (step in -50..50) {
                val angle = Math.toRadians(gaugeAngleForCents(step.toFloat()).toDouble())
                val isMajor = step % 10 == 0
                val isMid = step % 5 == 0
                val outerRadius = radius - bandWidth - 5.dp.toPx()
                val innerRadius = when {
                    isMajor -> outerRadius - 20.dp.toPx()
                    isMid -> outerRadius - 13.dp.toPx()
                    else -> outerRadius - 6.dp.toPx()
                }
                val start = Offset(
                    x = center.x + cos(angle).toFloat() * innerRadius,
                    y = center.y + sin(angle).toFloat() * innerRadius,
                )
                val end = Offset(
                    x = center.x + cos(angle).toFloat() * outerRadius,
                    y = center.y + sin(angle).toFloat() * outerRadius,
                )
                val tickColor = when {
                    step in -8..8 -> AccentGreen
                    step < -28 || step > 28 -> AccentRed
                    else -> AccentGold
                }
                drawLine(
                    color = tickColor.copy(alpha = if (isMajor || isMid) 1f else 0.55f),
                    start = start,
                    end = end,
                    strokeWidth = when {
                        isMajor -> 2.4.dp.toPx()
                        isMid -> 1.6.dp.toPx()
                        else -> 0.75.dp.toPx()
                    },
                    cap = StrokeCap.Round,
                )
            }

            val labelPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 13.dp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            listOf(-50, -30, -10, 0, 10, 30, 50).forEach { marker ->
                val angle = Math.toRadians(gaugeAngleForCents(marker.toFloat()).toDouble())
                val labelRadius = radius - bandWidth - 40.dp.toPx()
                val point = Offset(
                    x = center.x + cos(angle).toFloat() * labelRadius,
                    y = center.y + sin(angle).toFloat() * labelRadius,
                )
                val label = if (marker > 0) "+$marker" else marker.toString()
                drawContext.canvas.nativeCanvas.drawText(label, point.x, point.y, labelPaint)
            }

            val angleDegrees = 270f + (if (hasSignal) needlePosition.coerceIn(-1f, 1f) else 0f) * 92f
            val needleAngle = Math.toRadians(angleDegrees.toDouble())
            val needleLength = radius - bandWidth - 28.dp.toPx()
            val needleEnd = Offset(
                x = center.x + cos(needleAngle).toFloat() * needleLength,
                y = center.y + sin(needleAngle).toFloat() * needleLength,
            )
            val shadowOffset = Offset(2.dp.toPx(), 3.dp.toPx())
            val isInTune = hasSignal && needlePosition.coerceIn(-1f, 1f) in -0.16f..0.16f

            if (isInTune) {
                drawCircle(
                    color = AccentGreen.copy(alpha = 0.22f),
                    radius = 64.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = AccentGreen.copy(alpha = 0.1f),
                    radius = 82.dp.toPx(),
                    center = center,
                )
            }

            drawLine(
                color = Color.Black.copy(alpha = 0.4f),
                start = center + shadowOffset,
                end = needleEnd + shadowOffset,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val needleColor = when {
                isInTune -> AccentGreen
                needlePosition < -0.3f || needlePosition > 0.3f -> AccentRed
                else -> AccentGold
            }
            drawLine(
                color = if (hasSignal) needleColor else Color(0xFFD0D7E2),
                start = center,
                end = needleEnd,
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = Color(0xFFE7EDF7), radius = 17.dp.toPx(), center = center)
            drawCircle(color = Color(0xFF293341), radius = 10.dp.toPx(), center = center)
            drawCircle(color = if (hasSignal) needleColor else Color(0xFF92A1B4), radius = 4.5.dp.toPx(), center = center)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("FLAT", style = MaterialTheme.typography.labelSmall, color = AccentRed.copy(alpha = 0.85f))
                    Text(flatNeighborNote ?: "--", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SHARP", style = MaterialTheme.typography.labelSmall, color = AccentRed.copy(alpha = 0.85f))
                    Text(sharpNeighborNote ?: "--", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun PitchHistoryGraph(history: List<Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+", style = MaterialTheme.typography.labelLarge, color = AccentGreen)
            Text("Pitch trail", style = MaterialTheme.typography.labelLarge, color = AccentBlue)
            Text("−", style = MaterialTheme.typography.labelLarge, color = AccentRed)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color(0xFF233041), RoundedCornerShape(14.dp)),
        ) {
            val columns = 10
            val rows = 10
            val cellWidth = size.width / columns
            val cellHeight = size.height / rows
            val centerY = size.height / 2f

            for (column in 0..columns) {
                val x = column * cellWidth
                drawLine(
                    color = AccentBlue.copy(alpha = 0.18f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            for (row in 0..rows) {
                val y = row * cellHeight
                drawLine(
                    color = AccentBlue.copy(alpha = if (row == rows / 2) 0.45f else 0.18f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (row == rows / 2) 2.dp.toPx() else 1.dp.toPx(),
                )
            }

            if (history.size > 1) {
                val stepX = size.width / (history.size - 1).coerceAtLeast(1)
                for (index in 0 until history.lastIndex) {
                    val y1 = centerY - (history[index].coerceIn(-50, 50) / 50f) * (size.height * 0.42f)
                    val y2 = centerY - (history[index + 1].coerceIn(-50, 50) / 50f) * (size.height * 0.42f)
                    drawLine(
                        color = Color.White,
                        start = Offset(index * stepX, y1),
                        end = Offset((index + 1) * stepX, y2),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("+50", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
            Text("0", style = MaterialTheme.typography.labelSmall, color = AccentBlue)
            Text("-50", style = MaterialTheme.typography.labelSmall, color = AccentRed)
        }
    }
}

@Composable
private fun MicrophoneCard(
    microphoneGranted: Boolean,
    onEnableMic: () -> Unit,
    noiseFilter: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Live input", style = MaterialTheme.typography.titleMedium)
            Text(
                if (microphoneGranted) "Listening through the device microphone." else "Microphone permission is required for real tuning.",
            )
            Text("Noise filter: ${if (noiseFilter) "enabled" else "disabled"}")
            if (!microphoneGranted) {
                Button(onClick = onEnableMic) {
                    Text("Enable microphone")
                }
            }
        }
    }
}

@Composable
private fun InstrumentCard(
    preset: InstrumentPreset,
    presetIndex: Int,
    stringIndex: Int,
    onPresetSelect: (Int) -> Unit,
    onStringSelect: (Int) -> Unit,
    targetString: String,
    targetFrequencyHz: Float,
    tonePlaying: Boolean,
    onToggleTone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Instrument and retune", style = MaterialTheme.typography.titleMedium)
            Text("${preset.name} • ${preset.tuningLabel}")
            Text(preset.description, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEachIndexed { index, item ->
                    val selected = index == presetIndex
                    Button(
                        onClick = { onPresetSelect(index) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AccentGreen.copy(alpha = 0.3f) else Color.DarkGray,
                        ),
                    ) {
                        Text(if (item.name == "Guitar") item.tuningLabel else item.name)
                    }
                }
            }
            if (preset.strings.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    preset.strings.forEachIndexed { index, string ->
                        val selected = index == stringIndex
                        Button(
                            onClick = { onStringSelect(index) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) AccentBlue.copy(alpha = 0.32f) else Color.DarkGray,
                            ),
                        ) {
                            Text(PitchMath.prettyNoteName(string))
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Target string: ${PitchMath.prettyNoteName(targetString)}")
            Text("Target frequency: ${String.format("%.1f", targetFrequencyHz)} Hz")
            Button(onClick = onToggleTone) {
                Text(if (tonePlaying) "Stop reference tone" else "Play reference tone")
            }
        }
    }
}

@Composable
private fun CalibrationCard(
    referenceA4Hz: Float,
    onReferenceChange: (Float) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calibration", style = MaterialTheme.typography.titleMedium)
            Text("A4 reference: ${referenceA4Hz.roundToInt()} Hz")
            Slider(value = referenceA4Hz, onValueChange = onReferenceChange, valueRange = 432f..446f)
            Text("Pitch sensitivity: ${(sensitivity * 100).roundToInt()}%")
            Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0.25f..0.95f)
        }
    }
}

@Composable
private fun MetronomeScreen() {
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }
    var bpm by rememberSaveable { mutableFloatStateOf(120f) }
    var beats by rememberSaveable { mutableIntStateOf(4) }
    var running by rememberSaveable { mutableStateOf(false) }
    var accentFirstBeat by rememberSaveable { mutableStateOf(true) }
    var beatCounter by rememberSaveable { mutableIntStateOf(1) }
    var lastTapMillis by rememberSaveable { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    LaunchedEffect(running, bpm, beats, accentFirstBeat) {
        if (running) {
            beatCounter = 1
            while (true) {
                val tone = if (accentFirstBeat && beatCounter == 1) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
                toneGenerator.startTone(tone, 90)
                delay((60000f / bpm).roundToLong())
                beatCounter = if (beatCounter >= beats) 1 else beatCounter + 1
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2A174B), Color(0xFF15395E), Color(0xFF11362B))))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hands-free metronome", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Steady timing for practice, lessons, and live rehearsal.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureChip(label = "Tap tempo")
                        FeatureChip(label = "Stable beat")
                        FeatureChip(label = "Practice")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Metronome", style = MaterialTheme.typography.titleLarge)
                Text("${bpm.roundToInt()} BPM", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Slider(value = bpm, onValueChange = { bpm = it }, valueRange = 40f..220f)
                Text("Beat $beatCounter of $beats")
                BeatDots(totalBeats = beats, currentBeat = beatCounter)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5, 6).forEach { option ->
                        Button(onClick = { beats = option }) {
                            Text(option.toString())
                        }
                    }
                }
                SettingToggle("Accent first beat", accentFirstBeat) { accentFirstBeat = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { running = !running }) {
                        Text(if (running) "Stop" else "Start")
                    }
                    Button(onClick = {
                        val now = System.currentTimeMillis()
                        if (lastTapMillis > 0L) {
                            val interval = now - lastTapMillis
                            if (interval in 250..1500) bpm = (60000f / interval.toFloat()).coerceIn(40f, 220f)
                        }
                        lastTapMillis = now
                    }) {
                        Text("Tap tempo")
                    }
                }
            }
        }
    }
}

@Composable
private fun BeatDots(
    totalBeats: Int,
    currentBeat: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(totalBeats) { index ->
            val active = index + 1 == currentBeat
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(if (active) 24.dp else 12.dp)
                    .height(12.dp)
                    .background(
                        if (active) AccentGreen else Color(0xFF4A5566),
                        RoundedCornerShape(12.dp),
                    ),
            )
        }
    }
}

private enum class ToolsSubTab { Practice, ChordRecognizer }

@Composable
private fun ToolsScreen(
    transpose: Int,
    onTransposeChange: (Int) -> Unit,
) {
    val tonePlayer = remember { ReferenceTonePlayer() }
    var note by rememberSaveable { mutableStateOf("C") }
    var octave by rememberSaveable { mutableIntStateOf(4) }
    var tonePlaying by rememberSaveable { mutableStateOf(false) }
    var showForkDialog by rememberSaveable { mutableStateOf(false) }
    var subTab by rememberSaveable { mutableStateOf(ToolsSubTab.Practice) }

    DisposableEffect(Unit) {
        onDispose { tonePlayer.stop() }
    }

    val selectedNote = "$note$octave"
    val selectedFrequency = PitchMath.frequencyForNoteName(selectedNote)
    val writtenNote = PitchMath.prettyNoteName(PitchMath.closestNoteName(selectedFrequency * 2f.pow(transpose / 12f)))

    LaunchedEffect(tonePlaying, selectedFrequency) {
        if (tonePlaying) {
            tonePlayer.playTone(selectedFrequency.toDouble())
        } else {
            tonePlayer.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF10253F), Color(0xFF213C68), Color(0xFF164031))))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Practice tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Premium utilities for tuning, transposition, and practice.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureChip(label = "Tuning fork")
                        FeatureChip(label = "Transpose")
                        FeatureChip(label = "Reference tone")
                    }
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { showForkDialog = true }) {
                        Text("Open tuning fork")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolsSubTabButton(
                label = "Practice",
                selected = subTab == ToolsSubTab.Practice,
                modifier = Modifier.weight(1f),
                onClick = { subTab = ToolsSubTab.Practice },
            )
            ToolsSubTabButton(
                label = "Chords",
                selected = subTab == ToolsSubTab.ChordRecognizer,
                modifier = Modifier.weight(1f),
                onClick = { subTab = ToolsSubTab.ChordRecognizer },
            )
        }

        when (subTab) {
            ToolsSubTab.Practice -> {
                Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Transpose helper", style = MaterialTheme.typography.titleMedium)
                        Text("Semitone shift: $transpose")
                        Slider(value = transpose.toFloat(), onValueChange = { onTransposeChange(it.roundToInt()) }, valueRange = -6f..6f, steps = 11)
                        Text(
                            text = "Concert: ${PitchMath.prettyNoteName(selectedNote)} → Written: $writtenNote",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Included tuner pack", style = MaterialTheme.typography.titleMedium)
                        Text("• Tuning fork popup")
                        Text("• Concert-to-written transpose")
                        Text("• Enhanced speaker-friendly reference tone")
                        Text("• Live chord recognizer")
                        Text("• Responsive live tuner dashboard")
                        Text("• Store-ready branded interface")
                    }
                }
            }
            ToolsSubTab.ChordRecognizer -> ChordRecognizerCard()
        }
    }

    if (showForkDialog) {
        TuningForkDialog(
            note = note,
            octave = octave,
            tonePlaying = tonePlaying,
            onDismiss = {
                tonePlaying = false
                showForkDialog = false
            },
            onNoteChange = { note = it },
            onOctaveDecrease = { octave = (octave - 1).coerceAtLeast(1) },
            onOctaveIncrease = { octave = (octave + 1).coerceAtMost(7) },
            onToggleTone = {
                tonePlaying = !tonePlaying
            },
        )
    }
}

@Composable
private fun ToolsSubTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentGreen.copy(alpha = 0.28f) else Color.Transparent,
            contentColor = if (selected) AccentGreen else Color.White.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChordRecognizerCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detector = remember { ChordDetector() }

    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { microphoneGranted = it },
    )

    var listening by rememberSaveable { mutableStateOf(false) }
    var chordResult by remember { mutableStateOf<ChordDetectionResult?>(null) }

    DisposableEffect(listening, microphoneGranted) {
        if (listening && microphoneGranted) {
            detector.start(scope) { result -> chordResult = result }
        } else {
            chordResult = null
        }
        onDispose {
            scope.launch { detector.stop() }
        }
    }

    val hasChord = chordResult != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF2C2144), Color(0xFF1B2C46), Color(0xFF12261F))),
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chord recognizer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Strum a chord near the microphone to identify it from live audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B2230), RoundedCornerShape(20.dp))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            chordResult?.chordName ?: if (listening) "Listening…" else "--",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasChord) AccentGreen else Color.White.copy(alpha = 0.6f),
                        )
                        if (hasChord) {
                            Text(
                                "Confidence ${((chordResult?.confidence ?: 0f) * 100).roundToInt()}%",
                                color = AccentTeal,
                            )
                        }
                    }
                }

                ChromaBars(chroma = chordResult?.chroma)

                if (!microphoneGranted) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    ) {
                        Text("Enable microphone")
                    }
                } else {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { listening = !listening }) {
                        Text(if (listening) "Stop listening" else "Start listening")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromaBars(chroma: FloatArray?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChordTemplates.noteNames.forEachIndexed { index, label ->
            val value = chroma?.get(index) ?: 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((6 + value * 58).dp)
                        .background(AccentTeal.copy(alpha = 0.4f + value * 0.6f), RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TuningForkDialog(
    note: String,
    octave: Int,
    tonePlaying: Boolean,
    onDismiss: () -> Unit,
    onNoteChange: (String) -> Unit,
    onOctaveDecrease: () -> Unit,
    onOctaveIncrease: () -> Unit,
    onToggleTone: () -> Unit,
) {
    val naturalNotes = listOf("C", "D", "E", "F", "G", "A", "B")
    val flatNotes = listOf("C#", "D#", "F#", "G#", "A#")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tuning fork") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A4 = 440 Hz")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        naturalNotes.forEach { item ->
                            NoteChoiceButton(label = PitchMath.prettyNoteName(item), selected = note == item, onClick = { onNoteChange(item) })
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        flatNotes.forEach { item ->
                            NoteChoiceButton(label = PitchMath.prettyNoteName(item), selected = note == item, onClick = { onNoteChange(item) })
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onOctaveDecrease) { Text("−") }
                    Text("Octave $octave", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onOctaveIncrease) { Text("+") }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onToggleTone) {
                    Text(if (tonePlaying) "Stop tuning fork" else "Play tuning fork")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun NoteChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentBlue.copy(alpha = 0.34f) else Color(0xFF20262F),
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    haptics: Boolean,
    noiseFilter: Boolean,
    leftHanded: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    onNoiseFilterChanged: (Boolean) -> Unit,
    onLeftHandedChanged: (Boolean) -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showDonationPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(DonationPrefsName, Context.MODE_PRIVATE)
        val lastShownMillis = prefs.getLong(KeyDonationLastShownMillis, 0L)
        val now = System.currentTimeMillis()
        if (now - lastShownMillis >= DonationPromptIntervalMillis) {
            showDonationPrompt = true
            prefs.edit().putLong(KeyDonationLastShownMillis, now).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF20263E), Color(0xFF174A62), Color(0xFF28421E))))
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FreeTuner setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tune the app for dark performance spaces and quiet practice rooms.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureChip(label = "Dark mode")
                        FeatureChip(label = "Responsive")
                        FeatureChip(label = "Simple")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                SettingToggle("Dark theme", darkTheme, onDarkThemeChanged)
                SettingToggle("Haptic feedback", haptics, onHapticsChanged)
                SettingToggle("Noise filter", noiseFilter, onNoiseFilterChanged)
                SettingToggle("Left-handed layout", leftHanded, onLeftHandedChanged)
                Button(modifier = Modifier.fillMaxWidth(), onClick = { showAbout = true }) {
                    Text("About FreeTuner")
                }
                Text("The layout is optimized for phones and now looks cleaner for Play Store screenshots.")
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About FreeTuner") },
            text = {
                Text("FreeTuner is an offline-ready tuner for musicians with a live dashboard, transpose helper, metronome, and practice tools. The app uses microphone access only for pitch detection.")
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showDonationPrompt) {
        AlertDialog(
            onDismissRequest = { showDonationPrompt = false },
            title = { Text("Enjoying FreeTuner?") },
            text = {
                Text("FreeTuner is free, offline, and ad-free. If it's useful to you, consider supporting development with a small donation.")
            },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DonationUrl)))
                    showDonationPrompt = false
                }) {
                    Text("Support on Ko-fi")
                }
            },
            dismissButton = {
                Button(onClick = { showDonationPrompt = false }) {
                    Text("Maybe later")
                }
            },
        )
    }
}

private const val DonationPrefsName = "freetuner_donation_prefs"
private const val KeyDonationLastShownMillis = "last_shown_millis"
private const val DonationPromptIntervalMillis = 30L * 24 * 60 * 60 * 1000
private const val DonationUrl = "https://ko-fi.com/freetuner"

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

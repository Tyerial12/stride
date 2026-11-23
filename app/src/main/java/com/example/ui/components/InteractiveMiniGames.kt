package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BrewGrade
import com.example.data.model.ForgeGrade
import com.example.data.model.FreshJourneyResetBanner
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Interactive 3-Strike Anvil Timing Mini-Game for Forging / Enhancing Gear.
 * Hitting the golden sweet spot 3 times produces a MASTERWORK (+18%) bonus!
 */
@Composable
fun AnvilTimingForgeDialog(
    targetLabel: String,
    relaxedTiming: Boolean,
    onComplete: (ForgeGrade) -> Unit,
    onDismiss: () -> Unit
) {
    val cycleMs = if (relaxedTiming) 1650 else 1080
    val perfectHalfWidth = if (relaxedTiming) 0.14f else 0.09f
    val goodHalfWidth = if (relaxedTiming) 0.28f else 0.21f

    val infinite = rememberInfiniteTransition(label = "anvil_cursor")
    val cursorPos by infinite.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anvil_sweep"
    )

    val strikeScores = remember { mutableStateListOf<Int>() }
    var lastHitBanner by remember { mutableStateOf("Tap HAMMER when the ember enters the gold core!") }
    var finishedGrade by remember { mutableStateOf<ForgeGrade?>(null) }
    val sparkAnim = remember { Animatable(0f) }

    LaunchedEffect(strikeScores.size) {
        if (strikeScores.isNotEmpty()) {
            sparkAnim.snapTo(1f)
            sparkAnim.animateTo(0f, animationSpec = tween(420))
        }
        if (strikeScores.size >= 3 && finishedGrade == null) {
            val total = strikeScores.sum()
            finishedGrade = when {
                total >= 5 -> ForgeGrade.MASTERWORK
                total >= 3 -> ForgeGrade.TEMPERED
                else -> ForgeGrade.STANDARD
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Sunsteel Anvil",
                        tint = Color(0xFFF59E0B)
                    )
                    Text(
                        text = "SUNSTEEL ANVIL FORGE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                // 3-Strike indicator pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        val score = strikeScores.getOrNull(idx)
                        val bg = when (score) {
                            2 -> Color(0xFFF59E0B)
                            1 -> Color(0xFF10B981)
                            0 -> Color(0xFF64748B)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val label = when (score) {
                            2 -> "PERFECT"
                            1 -> "GOOD"
                            0 -> "OK"
                            else -> "HIT ${idx + 1}"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bg
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (score != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Moving Forge Heat Bar Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1917))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val barY = h * 0.30f
                        val barH = h * 0.40f

                        // Base iron track
                        drawRoundRect(
                            color = Color(0xFF334155),
                            topLeft = Offset(12f, barY),
                            size = Size(w - 24f, barH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        // Good emerald zone
                        val goodLeft = w * (0.5f - goodHalfWidth)
                        val goodW = w * (goodHalfWidth * 2f)
                        drawRoundRect(
                            color = Color(0xFF10B981).copy(alpha = 0.55f),
                            topLeft = Offset(goodLeft, barY),
                            size = Size(goodW, barH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        // Perfect gold sweet spot
                        val perfLeft = w * (0.5f - perfectHalfWidth)
                        val perfW = w * (perfectHalfWidth * 2f)
                        drawRoundRect(
                            color = Color(0xFFF59E0B),
                            topLeft = Offset(perfLeft, barY),
                            size = Size(perfW, barH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        // Moving glowing hammer cursor
                        val cx = w * cursorPos
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(cx - 5.dp.toPx(), barY - 8.dp.toPx()),
                            size = Size(10.dp.toPx(), barH + 16.dp.toPx()),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        // Spark burst on strike
                        val sp = sparkAnim.value
                        if (sp > 0.02f) {
                            drawCircle(
                                color = Color(0xFFFFD166).copy(alpha = sp * 0.7f),
                                radius = (1f - sp) * 44.dp.toPx() + 8.dp.toPx(),
                                center = Offset(w * 0.5f, h * 0.5f)
                            )
                        }
                    }
                }

                Text(
                    text = finishedGrade?.let { "Result: ${it.label} (+${it.masteryXp} Smithing XP)" } ?: lastHitBanner,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (finishedGrade == ForgeGrade.MASTERWORK) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (finishedGrade == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("anvil_cancel_button")
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val dist = abs(cursorPos - 0.5f)
                                val pts = when {
                                    dist <= perfectHalfWidth -> 2
                                    dist <= goodHalfWidth -> 1
                                    else -> 0
                                }
                                strikeScores.add(pts)
                                lastHitBanner = when (pts) {
                                    2 -> "GOLDEN SPARK! Perfect Hammer Strike!"
                                    1 -> "SOLID TEMPER! Good Strike!"
                                    else -> "Glancing blow — aim for the gold core!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("anvil_strike_button")
                        ) {
                            Text("STRIKE ANVIL!", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Button(
                        onClick = { onComplete(finishedGrade!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("anvil_claim_button")
                    ) {
                        Text("Claim ${finishedGrade!!.label}", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * Interactive Alchemist Cauldron Brewing Mini-Game.
 * Tap STOKE to raise heat (it cools naturally) and keep the flame inside the emerald zone!
 */
@Composable
fun AlchemistFlameBrewDialog(
    recipeName: String,
    relaxedTiming: Boolean,
    onComplete: (BrewGrade) -> Unit,
    onDismiss: () -> Unit
) {
    var heat by remember { mutableFloatStateOf(0.50f) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    var sweetSpotMs by remember { mutableIntStateOf(0) }
    var finishedGrade by remember { mutableStateOf<BrewGrade?>(null) }

    val totalDurationMs = 4200
    val zoneMin = if (relaxedTiming) 0.34f else 0.40f
    val zoneMax = if (relaxedTiming) 0.72f else 0.66f

    LaunchedEffect(Unit) {
        val tickMs = 50
        while (elapsedMs < totalDurationMs) {
            delay(tickMs.toLong())
            elapsedMs += tickMs
            val coolRate = if (relaxedTiming) 0.011f else 0.016f
            heat = (heat - coolRate).coerceIn(0.05f, 0.98f)
            if (heat in zoneMin..zoneMax) {
                sweetSpotMs += tickMs
            }
        }
        val ratio = sweetSpotMs.toFloat() / totalDurationMs.toFloat()
        finishedGrade = when {
            ratio >= 0.72f -> BrewGrade.DOUBLE_BREW
            ratio >= 0.45f -> BrewGrade.POTENT
            else -> BrewGrade.STANDARD
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Alchemist Cauldron",
                        tint = Color(0xFF10B981)
                    )
                    Text(
                        text = "ALCHEMIST CAULDRON BREW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Brewing: $recipeName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF059669)
                )

                // Time progress bar
                LinearProgressIndicator(
                    progress = { (elapsedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFF10B981)
                )

                // Cauldron Heat Gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val trackY = h * 0.28f
                        val trackH = h * 0.44f

                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF38BDF8), Color(0xFF10B981), Color(0xFFEF4444))
                            ),
                            topLeft = Offset(12f, trackY),
                            size = Size(w - 24f, trackH),
                            cornerRadius = CornerRadius(12f, 12f),
                            alpha = 0.35f
                        )
                        // Emerald sweet spot
                        drawRoundRect(
                            color = Color(0xFF10B981).copy(alpha = 0.75f),
                            topLeft = Offset(w * zoneMin, trackY),
                            size = Size(w * (zoneMax - zoneMin), trackH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        // Live flame needle
                        val nx = w * heat
                        drawCircle(
                            color = if (heat in zoneMin..zoneMax) Color(0xFFFFF8E7) else Color(0xFFF97316),
                            radius = 12.dp.toPx(),
                            center = Offset(nx, h * 0.5f)
                        )
                    }
                }

                val purityPct = ((sweetSpotMs.toFloat() / totalDurationMs.toFloat()) * 100f).toInt().coerceIn(0, 100)
                Text(
                    text = finishedGrade?.let { "${it.label} • +${it.masteryXp} Alchemy XP!" }
                        ?: "Hold flame in the Emerald Zone! Purity: $purityPct%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (heat in zoneMin..zoneMax) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (finishedGrade == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { heat = (heat - 0.12f).coerceIn(0.05f, 0.98f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("brew_cool_button")
                        ) {
                            Text("Cool Vent")
                        }
                        Button(
                            onClick = { heat = (heat + 0.14f).coerceIn(0.05f, 0.98f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("brew_stoke_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Stoke Flame",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STOKE FLAME", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                } else {
                    Button(
                        onClick = { onComplete(finishedGrade!!) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("brew_claim_button")
                    ) {
                        Text("Bottle ${finishedGrade!!.label}", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * One-time welcome & notice modal shown strictly once after the step-preserving progression reset completes.
 */
@Composable
fun FreshJourneyMigrationDialog(
    banner: FreshJourneyResetBanner,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, Color(0xFFF59E0B)),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Solstride Dawn of Evolution",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "DAWN OF EVOLUTION: FRESH JOURNEY",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Welcome back, ${banner.heroName}! Solstride has been reborn for interactive long-term play. All RPG progression has been freshly reset for everyone — while 100% of your real-world steps were safely preserved!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsWalk,
                            contentDescription = "Steps Preserved",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "STEPS 100% PRESERVED",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                            Text(
                                text = "Lifetime: ${"%,d".format(banner.preservedTotalSteps)} steps • This Week: ${"%,d".format(banner.preservedWeeklySteps)} steps",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("• Active Timing Dodge/Parry Rings & Combo Chains", style = MaterialTheme.typography.bodySmall)
                    Text("• 3-Stage Branching Pet Evolutions & Interactive Care Habitat", style = MaterialTheme.typography.bodySmall)
                    Text("• Walkable 2D Realm Wilds, Anvil Forging & Cauldron Brewing", style = MaterialTheme.typography.bodySmall)
                    Text("• Upgradeable Sunhaven Buildings, Weekly World Boss & Guilds", style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("fresh_journey_confirm_button")
                ) {
                    Text("Begin Fresh Journey", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DerivedHeroStats
import com.example.data.model.HeroProfile
import com.example.data.model.LootChest
import com.example.domain.ExpeditionEngine
import com.example.domain.ExpeditionOutcomeReport
import com.example.domain.ExpeditionRiskTier
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun riskTierColor(tier: ExpeditionRiskTier): Color = when (tier) {
    ExpeditionRiskTier.SAFE -> Color(0xFF15803D)
    ExpeditionRiskTier.GUARDED -> Color(0xFF0369A1)
    ExpeditionRiskTier.RISKY -> Color(0xFFD97706)
    ExpeditionRiskTier.PERILOUS -> Color(0xFFB91C1C)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimedExpeditionCard(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    currentTimeMs: Long,
    battleActive: Boolean,
    lastReport: ExpeditionOutcomeReport?,
    onStartExpedition: (Int) -> Unit,
    onStopExpedition: () -> Unit,
    onClaimExpedition: () -> Unit,
    onOpenAwardedChest: (LootChest) -> Unit,
    onDismissReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    var showStopConfirm by remember { mutableStateOf(false) }

    // Expedition Outcome Debrief Banner (Success or Failure)
    if (lastReport != null) {
        SolstrideCard(
            modifier = modifier.fillMaxWidth(),
            containerColor = if (lastReport.succeeded) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            strongBorder = true
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (lastReport.succeeded) Icons.Filled.AutoAwesome else Icons.Filled.Warning,
                    contentDescription = if (lastReport.succeeded) "Expedition succeeded" else "Expedition failed",
                    tint = if (lastReport.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lastReport.succeeded) "EXPEDITION SUCCEEDED!" else "EXPEDITION FAILED",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (lastReport.succeeded) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        text = "${ExpeditionEngine.formatDuration(lastReport.durationMinutes)} • Survival: ${lastReport.survivalChancePct}% • +${lastReport.solCoinsEarned} Sol Coins",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lastReport.summaryMessage,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val awardedChest = lastReport.awardedChest
                if (lastReport.succeeded && awardedChest != null) {
                    Button(
                        onClick = { onOpenAwardedChest(awardedChest) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_expedition_chest_button")
                    ) {
                        Text("Open Chest")
                    }
                }
                OutlinedButton(
                    onClick = onDismissReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dismiss_expedition_report_button"),
                    border = BorderStroke(1.5.dp, ext.borderStrong)
                ) {
                    Text(if (lastReport.succeeded) "Dismiss" else "Regroup")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Active Expedition State (Countdown + Survival Forecast + Stop Early with No Reward)
    if (profile.hasActiveExpedition()) {
        val totalMs = (profile.activeExpeditionEndMs - profile.activeExpeditionStartMs).coerceAtLeast(1L)
        val remainingMs = (profile.activeExpeditionEndMs - currentTimeMs).coerceAtLeast(0L)
        val elapsedMs = (totalMs - remainingMs).coerceIn(0L, totalMs)
        val progress = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        val isComplete = remainingMs <= 0L
        val durationMin = profile.activeExpeditionDurationMin.toInt()
            .coerceIn(ExpeditionEngine.MIN_DURATION_MINUTES, ExpeditionEngine.MAX_DURATION_MINUTES)
        val elapsedMin = (elapsedMs / 60_000L).toInt().coerceIn(0, durationMin)
        val liveCoinReward = ExpeditionEngine.computeSolCoinReward(
            elapsedMinutes = if (isComplete) durationMin else elapsedMin,
            plannedDurationMinutes = durationMin
        )
        val forecast = ExpeditionEngine.evaluateRiskForecast(durationMin, stats, profile.level.toInt())
        val riskColor = riskTierColor(forecast.riskTier)

        SolstrideCard(
            modifier = modifier.fillMaxWidth(),
            containerColor = if (isComplete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            strongBorder = true
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isComplete) Icons.Filled.AutoAwesome else Icons.Filled.HourglassTop,
                        contentDescription = "Active timed expedition",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isComplete) "Expedition Returned!" else "Timed Expedition Active",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${ExpeditionEngine.formatDuration(durationMin)} Run • ${forecast.ultraRareChancePct}% Ultra Rare Chest",
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.rarityUltraRare
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = riskColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, riskColor)
                ) {
                    Text(
                        text = "${forecast.survivalChancePct}% SURVIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = riskColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isComplete) "00:00:00" else ExpeditionEngine.formatCountdown(remainingMs),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Time Spent: ${liveCoinReward.timeSpentPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (isComplete) {
                            "+${liveCoinReward.fullSolCoins} Sol Coins Ready"
                        } else {
                            "Sol Coins: +${liveCoinReward.earnedSolCoins} / ${liveCoinReward.fullSolCoins}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isComplete) {
                    "Your hero has returned! Resolve the survival roll (${forecast.survivalChancePct}% odds) to claim +${liveCoinReward.fullSolCoins} Sol Coins and your sealed Expedition Relic Chest."
                } else {
                    "Hero is locked while exploring (watch steps still auto-sync). Stopping early awards Sol Coins based on % of time spent (min 5m), but forfeits XP, materials & the chest."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (isComplete) {
                Button(
                    onClick = onClaimExpedition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("claim_expedition_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resolve Expedition & Loot Chest", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(
                    onClick = { showStopConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop_expedition_early_button"),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (liveCoinReward.earnedSolCoins > 0L) {
                            "Recall Early (+${liveCoinReward.earnedSolCoins} Sol Coins)"
                        } else {
                            "Stop Expedition Early (0 Coins <5m)"
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        if (showStopConfirm) {
            AlertDialog(
                onDismissRequest = { showStopConfirm = false },
                title = { Text("Recall Expedition Early?") },
                text = {
                    Text(
                        if (liveCoinReward.earnedSolCoins > 0L) {
                            "Recalling at ${liveCoinReward.timeSpentPercent}% time spent will unlock your hero and award +${liveCoinReward.earnedSolCoins} Sol Coins, but you will forfeit XP, Sunshards, materials, and the sealed Expedition Chest. Continue?"
                        } else {
                            "Less than 5 minutes have elapsed (${liveCoinReward.timeSpentPercent}%). Recalling now unlocks your hero with 0 Sol Coins and forfeits all expedition rewards. Continue?"
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showStopConfirm = false
                            onStopExpedition()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.testTag("confirm_stop_expedition_button")
                    ) {
                        Text(
                            if (liveCoinReward.earnedSolCoins > 0L) {
                                "Recall (+${liveCoinReward.earnedSolCoins} Coins)"
                            } else {
                                "Stop (0 Coins)"
                            }
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showStopConfirm = false }) {
                        Text("Keep Going")
                    }
                }
            )
        }
        return
    }

    // Idle Launcher: Presets + Custom 5m to 23h Slider + Gear-Weighted Survival Forecast
    var selectedMinutesFloat by remember { mutableFloatStateOf(30f) }
    val selectedMinutes = selectedMinutesFloat.roundToInt()
        .coerceIn(ExpeditionEngine.MIN_DURATION_MINUTES, ExpeditionEngine.MAX_DURATION_MINUTES)
    val forecast = ExpeditionEngine.evaluateRiskForecast(selectedMinutes, stats, profile.level.toInt())
    val riskColor = riskTierColor(forecast.riskTier)

    SolstrideCard(
        modifier = modifier.fillMaxWidth(),
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Filled.Explore,
                    contentDescription = "Timed expeditions",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Timed Expeditions (5m – 23h)",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Won: ${profile.expeditionsCompleted} • Failed: ${profile.expeditionsFailed}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = riskColor.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, riskColor)
            ) {
                Text(
                    text = "${forecast.riskTier.label.uppercase(Locale.US)} ${forecast.survivalChancePct}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = riskColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpeditionEngine.presets.forEach { preset ->
                FilterChip(
                    selected = selectedMinutes == preset.durationMinutes,
                    onClick = { selectedMinutesFloat = preset.durationMinutes.toFloat() },
                    label = { Text(preset.shortLabel, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("exp_preset_${preset.id}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Duration: ${ExpeditionEngine.formatDuration(selectedMinutes)}",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFontFamily
            )
            Text(
                text = "Chest Ultra Rare: ${forecast.ultraRareChancePct}%",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = ext.rarityUltraRare
            )
        }

        Slider(
            value = selectedMinutesFloat,
            onValueChange = { raw ->
                val snapped = if (raw < 60f) {
                    ((raw / 5f).roundToInt() * 5).coerceAtLeast(5)
                } else {
                    ((raw / 15f).roundToInt() * 15).coerceAtMost(ExpeditionEngine.MAX_DURATION_MINUTES)
                }
                selectedMinutesFloat = snapped.toFloat()
            },
            valueRange = ExpeditionEngine.MIN_DURATION_MINUTES.toFloat()..ExpeditionEngine.MAX_DURATION_MINUTES.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expedition_duration_slider")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sol Coin Payout (${forecast.durationTimePercent}% of 23h max):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "+${forecast.expectedSolCoins} Sol Coins",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // Gear Power vs Recommended Survival Requirement Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, riskColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "Gear survival rating",
                        tint = riskColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Gear Power ${forecast.currentGearPower} / Rec. ${forecast.recommendedGearPower}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (forecast.currentGearPower < forecast.recommendedGearPower) {
                                "Risk of expedition failure! Forge or equip stronger weapons & armor."
                            } else {
                                "Strong gear + ${stats.expeditionWardPercent}% Wayfarer Ward active."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onStartExpedition(selectedMinutes) },
            enabled = !battleActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("launch_timed_expedition_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Filled.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (battleActive) {
                    "Finish Active Battle First"
                } else {
                    "Send Hero (${ExpeditionEngine.formatDuration(selectedMinutes)} • ${forecast.survivalChancePct}%)"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

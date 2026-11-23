package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConstellationBranch
import com.example.data.model.ConstellationCatalog
import com.example.data.model.DerivedHeroStats
import com.example.data.model.DreamRestTier
import com.example.data.model.DungeonChamberType
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.SlayerContractCatalog
import com.example.data.model.StepDungeonCatalog
import com.example.data.model.currentCalendarDateKey
import kotlin.math.roundToInt

/**
 * Dual-Pool Real-Life Steps (Battle Energy) & Sleep (Dungeon/Boss Stamina) HUD Card.
 * Explains the strict real-life energy rule (0 EN generated in combat) and Pinnacle Chest Exclusivity.
 */
@Composable
fun DualPoolEnergyStaminaHudCard(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    hasWatchSleepDetection: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dreamTier = hardcoreState.activeDreamTier()
    val maxEnergy = stats.computedMaxEnergy.coerceAtLeast(100)
    val energyRatio = (profile.strideEnergy.toFloat() / maxEnergy.toFloat()).coerceIn(0f, 1f)
    val maxStamina = hardcoreState.ComputedMaxStamina(profile.endurance).coerceAtLeast(100)
    val staminaRatio = (hardcoreState.heroStamina.toFloat() / maxStamina.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dual_pool_energy_stamina_hud"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Dual-Pool Real Step & Sleep Economy",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (hasWatchSleepDetection) {
                                "WATCH CONNECTED • STEP ENERGY & SLEEP STAMINA"
                            } else {
                                "WATCH DISCONNECTED • STEPS FUEL ENERGY & STAMINA"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (hasWatchSleepDetection) {
                                "Watch Connected (Sleep Active): Steps recharge Energy • Watch Sleep restores Stamina"
                            } else {
                                "Watch Disconnected: Real Steps recharge BOTH Battle Energy & Hero Stamina!"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "✨ ${hardcoreState.dreamShards} Dream Shards",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB388FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Dual Bars: Step Battle Energy + Sleep Dungeon/Boss Stamina
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pool 1: Step Battle Energy
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsWalk,
                                    contentDescription = "Step Battle Energy",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "STEP ENERGY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                            }
                            Text(
                                text = "${profile.strideEnergy}/$maxEnergy",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { energyRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = Color(0xFFFFB300),
                            trackColor = Color(0xFFFFB300).copy(alpha = 0.18f)
                        )
                        Text(
                            text = "Recharged by real Steps (${String.format("%.2fx", stats.stepEnergyMultiplier)} yield)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Pool 2: Sleep Stamina
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasWatchSleepDetection) Icons.Default.Bedtime else Icons.Default.DirectionsWalk,
                                    contentDescription = "Hero Stamina",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (hasWatchSleepDetection) "SLEEP STAMINA" else "STEP & REST STAMINA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                            Text(
                                text = "${hardcoreState.heroStamina}/$maxStamina",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { staminaRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF00E5FF).copy(alpha = 0.18f)
                        )
                        Text(
                            text = if (hasWatchSleepDetection) {
                                "Watch Connected: Watch Sleep restores Stamina for Bosses & Dungeons"
                            } else {
                                "Watch Disconnected: Recharged by real Steps + Sanctuary Rest"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Active Dream Rest Tier + Pinnacle Chest Rule Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🌙 Active Dream Tier: ${dreamTier.title} (+${dreamTier.bonusXpPct}% XP • +${dreamTier.bonusCritPct}% Crit)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "🏆 Pinnacle Chest Rule: Loot Chests drop ONLY on Boss Kills, Dungeon Clears & Expedition Finishes",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Multi-Room Step Dungeon Delve Card with custom Canvas corridor map, chamber progress,
 * Pace Surge button, and Final Chamber Dungeon Boss Chest challenge.
 */
@Composable
fun MultiRoomStepDungeonCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    onSelectDungeon: (String) -> Unit,
    onSprintCorridor: () -> Unit,
    onResolveChamber: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDungeon = hardcoreState.activeDungeon()
    val activeRoomIdx = hardcoreState.activeDungeonRoomIndex.coerceIn(0, activeDungeon.chambers.lastIndex)
    val activeChamber = hardcoreState.activeDungeonChamber()
    val reqSteps = hardcoreState.effectiveChamberStepsRequired()
    val curSteps = hardcoreState.activeDungeonRoomSteps.coerceAtMost(reqSteps)
    val roomRatio = (curSteps.toFloat() / reqSteps.toFloat()).coerceIn(0f, 1f)
    val chamberReady = curSteps >= reqSteps

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_room_step_dungeon_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🏰 MULTI-ROOM STEP DUNGEONS & LABYRINTH DELVES",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Walk real steps through every chamber • Guaranteed Boss Chest at Final Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${hardcoreState.dungeonClearsCount} Clears",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Horizontal Dungeon Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StepDungeonCatalog.dungeons.forEach { dungeon ->
                    val selected = dungeon.id == activeDungeon.id
                    val unlocked = profile.currentRealm >= dungeon.recommendedRealm ||
                        profile.level >= dungeon.recommendedRealm * 3L
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectDungeon(dungeon.id) },
                        label = {
                            Text(
                                text = "R${dungeon.recommendedRealm} • ${dungeon.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (unlocked) Icons.Default.Shield else Icons.Default.Lock,
                                contentDescription = dungeon.name,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("select_dungeon_${dungeon.id}")
                    )
                }
            }

            // Dungeon Corridor Visual Node Map Canvas
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0E1320),
                border = BorderStroke(1.dp, Color(0xFF3949AB).copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${activeDungeon.name} • Chamber ${activeRoomIdx + 1}/${activeDungeon.chambers.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "Sovereign: ${activeDungeon.bossName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF8A65)
                        )
                    }

                    // Interactive Node Path Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        val count = activeDungeon.chambers.size.coerceAtLeast(2)
                        val padX = 24.dp.toPx()
                        val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
                        val stepX = usableW / (count - 1).toFloat()
                        val centerY = size.height / 2f

                        for (i in 0 until count - 1) {
                            val startX = padX + i * stepX
                            val endX = padX + (i + 1) * stepX
                            val cleared = i < activeRoomIdx
                            drawLine(
                                color = if (cleared) Color(0xFF00E676) else Color(0xFF37474F),
                                start = Offset(startX, centerY),
                                end = Offset(endX, centerY),
                                strokeWidth = if (cleared) 4.dp.toPx() else 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = if (cleared) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                            )
                        }

                        activeDungeon.chambers.forEachIndexed { idx, node ->
                            val cx = padX + idx * stepX
                            val isBoss = node.type == DungeonChamberType.FINAL_BOSS_VAULT
                            val isCurrent = idx == activeRoomIdx
                            val isCleared = idx < activeRoomIdx
                            val nodeColor = when {
                                isCleared -> Color(0xFF00E676)
                                isCurrent && isBoss -> Color(0xFFFF5252)
                                isCurrent -> Color(0xFFFFD54F)
                                isBoss -> Color(0xFFFF7043)
                                else -> Color(0xFF78909C)
                            }
                            if (isCurrent) {
                                drawCircle(
                                    color = nodeColor.copy(alpha = 0.26f),
                                    radius = if (isBoss) 19.dp.toPx() else 16.dp.toPx(),
                                    center = Offset(cx, centerY)
                                )
                            }
                            drawCircle(
                                color = nodeColor,
                                radius = if (isBoss) 12.dp.toPx() else 9.dp.toPx(),
                                center = Offset(cx, centerY)
                            )
                            drawCircle(
                                color = Color(0xFF0E1320),
                                radius = if (isBoss) 5.dp.toPx() else 3.5.dp.toPx(),
                                center = Offset(cx, centerY)
                            )
                        }
                    }

                    // Chamber details + step progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${activeChamber.type.label}] ${activeChamber.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = activeChamber.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (chamberReady) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "$curSteps / $reqSteps Steps",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (chamberReady) Color(0xFF00E676) else Color(0xFFFFD54F),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { roomRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = if (chamberReady) Color(0xFF00E676) else Color(0xFFFFB300),
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }
            }

            // Action Buttons: Pace Surge (+400 steps for 18 EN / 12 STA) & Resolve Chamber / Fight Boss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSprintCorridor,
                    enabled = !chamberReady,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dungeon_pace_surge_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Pace Surge",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pace Surge (+400 Steps • 18 EN)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onResolveChamber,
                    enabled = chamberReady,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("dungeon_resolve_chamber_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeChamber.type == DungeonChamberType.FINAL_BOSS_VAULT) {
                            Color(0xFFD32F2F)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (activeChamber.type == DungeonChamberType.FINAL_BOSS_VAULT) {
                            Icons.Default.Whatshot
                        } else {
                            Icons.Default.CheckCircle
                        },
                        contentDescription = "Resolve Chamber",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeChamber.type == DungeonChamberType.FINAL_BOSS_VAULT) {
                            "Fight Boss & Claim Chest (${activeChamber.staminaCost} STA)"
                        } else {
                            "Clear Chamber (${activeChamber.staminaCost} STA)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Sleep Sanctuary & Dream Rest Tier Attunement Card.
 * Lets players inspect the 4 Dream Rest Tiers and attune their daily sleep rest
 * (or auto-sync via Watch / Health Connect) to restore Hero Stamina, cure wounds, and earn Dream Shards.
 */
@Composable
fun SleepSanctuaryDreamCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    onAttuneSleep: (Float) -> Unit,
    hasWatchSleepDetection: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeTier = hardcoreState.activeDreamTier()
    val todayKey = currentCalendarDateKey()
    val alreadyAttunedToday = hardcoreState.lastSanctuaryRestDateKey == todayKey
    var selectedHours by remember {
        mutableFloatStateOf(
            if (hardcoreState.lastSleepDurationMinutes > 0) {
                (hardcoreState.lastSleepDurationMinutes / 60f).coerceIn(3f, 9f)
            } else {
                7.5f
            }
        )
    }
    val previewTier = DreamRestTier.forSleepMinutes((selectedHours * 60f).roundToInt())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sleep_sanctuary_dream_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)
        ),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Sanctuary",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "🌙 SLEEP SANCTUARY & DREAM REST TIERS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = if (hasWatchSleepDetection) {
                                "Watch Connected (Sleep Active) • Auto-restores Stamina, cures Wounds & grants Dream Shards"
                            } else {
                                "Watch Disconnected • Your real Steps recharge BOTH Energy & Stamina + attune Dream Rest below"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasWatchSleepDetection) {
                        Color(0xFF00E5FF).copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (hasWatchSleepDetection) Color(0xFF00E5FF) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = if (hasWatchSleepDetection) "WATCH CONNECTED" else "WATCH DISCONNECTED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasWatchSleepDetection) {
                            Color(0xFF00E5FF)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 4 Dream Rest Tier Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DreamRestTier.entries.forEach { tier ->
                    val isCurrent = tier == activeTier
                    Surface(
                        modifier = Modifier.width(175.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            Color(0xFF00E5FF).copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        },
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) Color(0xFF00E5FF) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = tier.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrent) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${tier.staminaRestorePct}% Stamina • ${String.format("%.2fx", tier.stepEnergyMultiplier)} Step EN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                            Text(
                                text = "+${tier.bonusXpPct}% XP • +${tier.bonusCritPct}% Crit • +${tier.dreamShardGrant} Shards",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Daily Sleep Sanctuary Attunement Dial
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Night's Sleep Rest: ${String.format("%.1f", selectedHours)} Hours",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preview: ${previewTier.title}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                    Slider(
                        value = selectedHours,
                        onValueChange = { selectedHours = it },
                        valueRange = 3.0f..9.5f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.testTag("sleep_sanctuary_hours_slider")
                    )
                    Button(
                        onClick = { onAttuneSleep(selectedHours) },
                        enabled = !alreadyAttunedToday,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attune_sleep_sanctuary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Attune Sleep Rest",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (alreadyAttunedToday) {
                                "Sleep Sanctuary Attuned Today (${activeTier.title})"
                            } else {
                                "Attune ${previewTier.title} (${previewTier.staminaRestorePct}% Stamina & +${previewTier.dreamShardGrant} Dream Shards)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Starlight Constellation Talent Tree + 5-Attribute Hero Point Allocator Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConstellationTalentTreeCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    onAllocateCoreStat: (String) -> Unit,
    onAllocateExtendedStat: (Boolean) -> Unit,
    onUpgradeConstellation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBranch by remember { mutableStateOf(ConstellationBranch.STRIDER) }
    val branchNodes = remember(selectedBranch) { ConstellationCatalog.byBranch(selectedBranch) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("constellation_talent_tree_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        ),
        border = BorderStroke(1.dp, Color(0xFFB388FF).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✨ 5-ATTRIBUTE HERO BUILD & STARLIGHT CONSTELLATIONS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB388FF)
                    )
                    Text(
                        text = "Spend Level-Up Points on 5 Core Attributes & Dream Shards on Constellation Stars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF7C4DFF).copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "${profile.unspentPoints} Stat Pts • ${hardcoreState.dreamShards} Shards",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD1C4E9),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // 5 Hero Attributes Grid (Might, Vigor, Endurance, Agility, Perception)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttributeAllocChip(
                    label = "MIGHT",
                    value = profile.might,
                    bonusHint = "+ATK & Boss Dmg",
                    canSpend = profile.unspentPoints > 0L,
                    onClick = { onAllocateCoreStat("might") },
                    modifier = Modifier.weight(1f)
                )
                AttributeAllocChip(
                    label = "VIGOR",
                    value = profile.vigor,
                    bonusHint = "+Max HP & Crit",
                    canSpend = profile.unspentPoints > 0L,
                    onClick = { onAllocateCoreStat("vigor") },
                    modifier = Modifier.weight(1f)
                )
                AttributeAllocChip(
                    label = "ENDURANCE",
                    value = profile.endurance,
                    bonusHint = "+DEF, Max EN & STA",
                    canSpend = profile.unspentPoints > 0L,
                    onClick = { onAllocateCoreStat("endurance") },
                    modifier = Modifier.weight(1f)
                )
                AttributeAllocChip(
                    label = "AGILITY",
                    value = hardcoreState.agilityStat.toLong(),
                    bonusHint = "+ATK, +Crit & Dodge",
                    canSpend = profile.unspentPoints > 0L,
                    onClick = { onAllocateExtendedStat(true) },
                    modifier = Modifier.weight(1f)
                )
                AttributeAllocChip(
                    label = "PERCEPTION",
                    value = hardcoreState.perceptionStat.toLong(),
                    bonusHint = "+Pierce, +Crit & Loot",
                    canSpend = profile.unspentPoints > 0L,
                    onClick = { onAllocateExtendedStat(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 3 Constellation Branch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConstellationBranch.entries.forEach { branch ->
                    val selected = branch == selectedBranch
                    FilterChip(
                        selected = selected,
                        onClick = { selectedBranch = branch },
                        label = {
                            Text(
                                text = branch.title.removePrefix("Way of the "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = branch.title,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Starlight Constellation Canvas
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A0E1A),
                border = BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.45f))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 20.dp)
                ) {
                    val count = branchNodes.size.coerceAtLeast(2)
                    val stepX = size.width / (count - 1).toFloat()
                    val cy = size.height / 2f
                    for (i in 0 until count - 1) {
                        val r1 = hardcoreState.constellationRank(branchNodes[i].id)
                        val r2 = hardcoreState.constellationRank(branchNodes[i + 1].id)
                        val lit = r1 > 0 && r2 > 0
                        drawLine(
                            color = if (lit) Color(0xFFB388FF) else Color(0xFF37474F),
                            start = Offset(i * stepX, cy),
                            end = Offset((i + 1) * stepX, cy),
                            strokeWidth = if (lit) 3.5.dp.toPx() else 2.dp.toPx()
                        )
                    }
                    branchNodes.forEachIndexed { idx, node ->
                        val rank = hardcoreState.constellationRank(node.id)
                        val cx = idx * stepX
                        val starColor = when {
                            rank >= node.maxRank -> Color(0xFFFFD54F)
                            rank > 0 -> Color(0xFFB388FF)
                            else -> Color(0xFF546E7A)
                        }
                        if (rank > 0) {
                            drawCircle(
                                color = starColor.copy(alpha = 0.25f),
                                radius = 15.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                        drawCircle(
                            color = starColor,
                            radius = 8.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            // 4 Nodes for the Selected Constellation Branch
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                branchNodes.forEach { node ->
                    val rank = hardcoreState.constellationRank(node.id)
                    val isMax = rank >= node.maxRank
                    val nextShardCost = node.dreamShardCost * (rank + 1)
                    val nextSunCost = node.sunshardCost * (rank + 1)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(
                            1.dp,
                            if (rank > 0) Color(0xFFB388FF).copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "★ ${node.title} (Rank $rank/${node.maxRank})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (rank > 0) Color(0xFFD1C4E9) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = node.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onUpgradeConstellation(node.id) },
                                enabled = !isMax,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("ignite_constellation_${node.id}")
                            ) {
                                Text(
                                    text = if (isMax) "MAXED" else "$nextShardCost ✨ + $nextSunCost ☀",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeAllocChip(
    label: String,
    value: Long,
    bonusHint: String,
    canSpend: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canSpend, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(
            1.dp,
            if (canSpend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = bonusHint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (canSpend) {
                Text(
                    text = "[+1 Tap]",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }
        }
    }
}

/**
 * Gear Prefix & Suffix Affix Reforging Workshop Card.
 */
@Composable
fun GearAffixReforgeCard(
    profile: HeroProfile,
    items: List<InventoryItem>,
    hardcoreState: HardcoreRpgState,
    onReforgeAffixes: (InventoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val equipmentItems = remember(items) {
        items.filter { it.resolvedSlot().isEquipment }.sortedByDescending { it.equipped }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gear_affix_reforge_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        ),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hardware,
                        contentDescription = "Gear Affix Reforging",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "🔨 ANCIENT PREFIX & SUFFIX AFFIX REFORGER",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFB300)
                        )
                        Text(
                            text = "Imbue Weapons, Armor & Relics with % ATK, DEF, HP, Crit, Pierce & Step Energy multipliers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "Materials: ${hardcoreState.sunsteelOre} Ore • ${hardcoreState.elementalEssence} Essence • ${profile.sunshards} Sunshards (Cost: 3 Ore + 2 Essence + 220 Sunshards)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (equipmentItems.isEmpty()) {
                Text(
                    text = "Defeat Bosses, clear Step Dungeons, or finish Timed Step Expeditions to earn Loot Chest gear to reforge!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    equipmentItems.take(6).forEach { item ->
                        val (prefix, suffix) = hardcoreState.affixesForItem(item.id)
                        val displayTitle = buildString {
                            if (prefix != null) append("${prefix.label} ")
                            append(item.name)
                            if (suffix != null) append(" ${suffix.label}")
                        }
                        val affixText = if (prefix != null || suffix != null) {
                            listOfNotNull(prefix?.summary(), suffix?.summary()).joinToString(" • ")
                        } else {
                            "No Affixes Rolled Yet — Tap Reforge to imbue Prefix + Suffix!"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = BorderStroke(
                                1.dp,
                                if (item.equipped) Color(0xFFFFB300).copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EquipmentArtworkForItem(
                                    item = item,
                                    size = 46.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.equipped) "★ $displayTitle (Equipped)" else displayTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (prefix != null) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = affixText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onReforgeAffixes(item) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("reforge_affix_${item.id}")
                                ) {
                                    Text(
                                        text = "Reforge",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step-Fueled Slayer Contracts Board Card (Dual Real-Life Steps + Combat/Boss Kills).
 */
@Composable
fun StepSlayerContractsCard(
    hardcoreState: HardcoreRpgState,
    onClaimContract: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("step_slayer_contracts_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📜 STEP-FUELED SLAYER CONTRACTS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Repeatable bounties combining real-world walking quotas & combat victories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "${hardcoreState.slayerContractsCompleted} Completed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            SlayerContractCatalog.contracts.forEach { contract ->
                val curSteps = (hardcoreState.slayerContractStepsMap[contract.id] ?: 0)
                    .coerceAtMost(contract.stepsRequired)
                val curKills = (hardcoreState.slayerContractKillsMap[contract.id] ?: 0)
                    .coerceAtMost(contract.killsRequired)
                val ready = curSteps >= contract.stepsRequired && curKills >= contract.killsRequired
                val stepProgress = (curSteps.toFloat() / contract.stepsRequired.toFloat()).coerceIn(0f, 1f)
                val killProgress = (curKills.toFloat() / contract.killsRequired.toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(
                        1.dp,
                        if (ready) Color(0xFF00E676) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contract.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = contract.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onClaimContract(contract.id) },
                                enabled = ready,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("claim_slayer_contract_${contract.id}")
                            ) {
                                Text(
                                    text = if (ready) "Claim Bounty" else "In Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Steps: $curSteps / ${contract.stepsRequired}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                                LinearProgressIndicator(
                                    progress = { stepProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${if (contract.requiresBossOrDungeon) "Boss/Dungeon" else "Combat"} Kills: $curKills / ${contract.killsRequired}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                                LinearProgressIndicator(
                                    progress = { killProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = Color(0xFFFF7043)
                                )
                            }
                        }

                        Text(
                            text = "Rewards: +${contract.xpReward} XP • +${contract.sunshardReward} Sunshards • +${contract.solCoinReward} Sol Coins • +${contract.dreamShardReward} Dream Shard(s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

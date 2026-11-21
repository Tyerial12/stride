package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.SolstrideTab
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import com.example.widget.SolstrideWidgetType
import com.example.widget.WidgetAutoSyncConfig
import com.example.widget.WidgetSnapshotState
import com.example.widget.WildPetBattleEngine

// Distinct per-widget palette tokens (matching XML drawable backgrounds 1:1)
private val CmdTop = Color(0xFF261317)
private val CmdBottom = Color(0xFF120A0C)
private val CmdBorder = Color(0xFFE05246)
private val CmdSubCard = Color(0xFF2A171B)
private val CmdSubBorder = Color(0xFF59282E)

private val HeroTop = Color(0xFF221A11)
private val HeroBottom = Color(0xFF120F0B)
private val HeroBorder = Color(0xFFE0A124)
private val HeroSubCard = Color(0xFF231D16)
private val HeroSubBorder = Color(0xFF3F3223)

private val PetTop = Color(0xFF211533)
private val PetBottom = Color(0xFF100B18)
private val PetBorder = Color(0xFFA855F7)
private val PetSubCard = Color(0xFF241836)
private val PetSubBorder = Color(0xFF4D3073)

private val StepsTop = Color(0xFF0E242E)
private val StepsBottom = Color(0xFF081319)
private val StepsBorder = Color(0xFF00D4EE)
private val StepsSubCard = Color(0xFF112833)
private val StepsSubBorder = Color(0xFF1E4E63)

private val WidgetTextCream = Color(0xFFFFF8EE)
private val WidgetAccentGold = Color(0xFFFFB300)
private val WidgetAccentGreen = Color(0xFF2EC27E)
private val WidgetAccentCyan = Color(0xFF00E5FF)
private val WidgetAccentPurple = Color(0xFFC084FC)
private val WidgetAccentCrimson = Color(0xFFEF4444)
private val WidgetAlertBg = Color(0xFF4A1515)
private val WidgetAlertStroke = Color(0xFFFF5252)

@Composable
fun WidgetHubQuickCard(
    snapshot: WidgetSnapshotState,
    autoSyncConfig: WidgetAutoSyncConfig = WidgetAutoSyncConfig(),
    currentTimeMs: Long,
    onOpenWidgetStudio: () -> Unit,
    onSyncWidgetsNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("expedition_widget_hub_card"),
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = "Home screen widgets",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "4 Specialized Home Widgets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (autoSyncConfig.enabled) {
                            "Auto-Sync ${autoSyncConfig.intervalLabel()} • ${snapshot.formattedSyncTime()}"
                        } else {
                            "Auto-Sync Paused • ${snapshot.formattedSyncTime()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onSyncWidgetsNow,
                modifier = Modifier.testTag("quick_sync_widgets_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Sync all home screen widgets now",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4 distinct 2x2 mini-tiles highlighting each widget's unique telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, CmdBorder.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "⚔ CAMPAIGN RADAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = CmdBorder,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "R${snapshot.realmNumber}-${snapshot.realmStage} • Radar ${snapshot.encounterProgressPct}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "🎁 ${snapshot.unopenedChestCount} Chests • 📜 ${snapshot.claimableQuestCount} Quests",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, HeroBorder.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "🔨 HERO ARMORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = HeroBorder,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "PWR ${snapshot.heroPower} • ${snapshot.equippedGearCount}/6",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "HP ${snapshot.hpPercent()}% • ⛏ ${snapshot.sunsteelOre} Ore",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, PetBorder.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "🐾 PET ARENA",
                        style = MaterialTheme.typography.labelSmall,
                        color = PetBorder,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (snapshot.hasPet) "${snapshot.petName} Lv.${snapshot.petLevel}" else "Adopt Pet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "⚡ ${snapshot.petWildStamina}/5 • 🍖 ${snapshot.petStepTreats} • 🏆 ${snapshot.petTotalWildWins}W",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, StepsBorder.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "👟 STEPS & SLEEP",
                        style = MaterialTheme.typography.labelSmall,
                        color = StepsBorder,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snapshot.formattedSteps(),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (snapshot.isHeroSleeping) "🌙 Sleeping (Watch)"
                        else if (snapshot.hasActiveExpedition) "⏳ Expedition Live"
                        else "${snapshot.stepGoalPercent()}% Goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (snapshot.hasHardcoreAlert()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = WidgetAlertBg,
                border = BorderStroke(1.dp, WidgetAlertStroke)
            ) {
                Text(
                    text = snapshot.hardcoreAlertLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD6D6),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onOpenWidgetStudio,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("open_widget_studio_btn")
        ) {
            Icon(
                imageVector = Icons.Filled.AddToHomeScreen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Preview & Pin All 4 Widgets")
        }
    }
}

@Composable
fun WidgetStudioDialog(
    snapshot: WidgetSnapshotState,
    autoSyncConfig: WidgetAutoSyncConfig = WidgetAutoSyncConfig(),
    activeWidgetCount: Int = 0,
    currentTimeMs: Long,
    onDismiss: () -> Unit,
    onPinWidget: (SolstrideWidgetType) -> Unit,
    onSyncAllWidgets: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit = {},
    onSelectAutoSyncInterval: (Long) -> Unit = {},
    onNavigateTab: (SolstrideTab) -> Unit
) {
    val ext = LocalSolstrideColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 760.dp)
                .testTag("widget_studio_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Widgets,
                            contentDescription = "Widget Command Studio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Widget Command Studio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "4 Distinct Live Widgets • $activeWidgetCount Placed • ${snapshot.formattedSyncTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_widget_studio_btn")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Widget Studio")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Background Auto-Sync Control Panel
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("widget_auto_sync_panel"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, ext.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Widget auto-sync",
                                    tint = if (autoSyncConfig.enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Background Widget Auto-Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (autoSyncConfig.enabled) {
                                            "Active (${autoSyncConfig.intervalLabel()}) + instant watch & expedition updates"
                                        } else {
                                            "Paused • Tap Refresh on widgets to update manually"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = autoSyncConfig.enabled,
                                onCheckedChange = onToggleAutoSync,
                                modifier = Modifier.testTag("widget_auto_sync_switch")
                            )
                        }

                        if (autoSyncConfig.enabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(15L to "15m Fast", 30L to "30m Balanced", 60L to "60m Saver").forEach { (mins, label) ->
                                    FilterChip(
                                        selected = autoSyncConfig.intervalMinutes == mins,
                                        onClick = { onSelectAutoSyncInterval(mins) },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.testTag("widget_sync_interval_${mins}m")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onSyncAllWidgets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_sync_all_widgets_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync All Widgets Now")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Battle & Campaign Radar (4x2 — Crimson/Ember)
                    WidgetStudioEntryBlock(
                        type = SolstrideWidgetType.UNIFIED_COMMAND,
                        accentColor = CmdBorder,
                        onPin = { onPinWidget(SolstrideWidgetType.UNIFIED_COMMAND) }
                    ) {
                        BattleCampaignRadarPreview(
                            snapshot = snapshot,
                            onRefresh = onSyncAllWidgets,
                            onTapBattle = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.BATTLE)
                            },
                            onTapForgeOrVault = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.INVENTORY)
                            },
                            onTapMapOrQuests = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.EXPEDITION)
                            }
                        )
                    }

                    // 2. Hero Build & Forge Armory (3x2 — Molten Amber)
                    WidgetStudioEntryBlock(
                        type = SolstrideWidgetType.HERO_STATS,
                        accentColor = HeroBorder,
                        onPin = { onPinWidget(SolstrideWidgetType.HERO_STATS) }
                    ) {
                        HeroArmoryWidgetPreview(
                            snapshot = snapshot,
                            onRefresh = onSyncAllWidgets,
                            onTapArmory = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.INVENTORY)
                            }
                        )
                    }

                    // 3. Interactive Pet Arena (4x2 — Twilight Violet)
                    WidgetStudioEntryBlock(
                        type = SolstrideWidgetType.COMPANION_PET,
                        accentColor = PetBorder,
                        onPin = { onPinWidget(SolstrideWidgetType.COMPANION_PET) }
                    ) {
                        PetArenaWidgetPreview(
                            snapshot = snapshot,
                            onRefresh = onSyncAllWidgets,
                            onTapPets = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.PETS)
                            }
                        )
                    }

                    // 4. Wayfarer Steps, Watch Sleep & Expedition (3x2 — Starlight Cyan)
                    WidgetStudioEntryBlock(
                        type = SolstrideWidgetType.STEPS_EXPEDITION,
                        accentColor = StepsBorder,
                        onPin = { onPinWidget(SolstrideWidgetType.STEPS_EXPEDITION) }
                    ) {
                        StepSleepExpeditionPreview(
                            snapshot = snapshot,
                            currentTimeMs = currentTimeMs,
                            onRefresh = onSyncAllWidgets,
                            onTapExpedition = {
                                onDismiss()
                                onNavigateTab(SolstrideTab.EXPEDITION)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetStudioEntryBlock(
    type: SolstrideWidgetType,
    accentColor: Color,
    onPin: () -> Unit,
    previewContent: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = type.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accentColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = type.sizeBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            previewContent()
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_widget_${type.id}_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.AddToHomeScreen,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pin ${type.title} to Home Screen")
            }
        }
    }
}

@Composable
private fun ThemedWidgetFrame(
    topColor: Color,
    bottomColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(topColor, bottomColor)))
            .padding(1.5.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, borderColor)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun BattleCampaignRadarPreview(
    snapshot: WidgetSnapshotState,
    onRefresh: () -> Unit,
    onTapBattle: () -> Unit,
    onTapForgeOrVault: () -> Unit,
    onTapMapOrQuests: () -> Unit
) {
    val encounterReady = snapshot.encounterProgressPct >= 100
    ThemedWidgetFrame(
        topColor = CmdTop,
        bottomColor = CmdBottom,
        borderColor = CmdBorder
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTapBattle() }
                ) {
                    val hcTag = if (snapshot.hardcoreOathEnabled) " 💀HC" else ""
                    Text(
                        text = "⚔ R${snapshot.realmNumber}-${snapshot.realmStage} • ${snapshot.realmName}$hcTag",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFFF5F3),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Spire Floor ${snapshot.spireFloor} • Bosses Slain: ${snapshot.bossesDefeated}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF2B8B5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = onTapBattle,
                    shape = RoundedCornerShape(999.dp),
                    color = if (encounterReady) WidgetAlertBg else Color(0xFF3A2B0E),
                    border = BorderStroke(1.dp, if (encounterReady) WidgetAlertStroke else WidgetAccentGold)
                ) {
                    Text(
                        text = if (encounterReady) "⚔ READY!" else "Radar ${snapshot.encounterProgressPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (encounterReady) Color(0xFFFFE4E2) else WidgetAccentGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    color = CmdSubCard,
                    border = BorderStroke(1.dp, CmdSubBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Campaign Radar preview",
                        tint = WidgetAccentGold,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.clickable { onTapBattle() }) {
                Text(
                    text = if (encounterReady) "⚔ BOSS / ENCOUNTER LOCKED ON — ENGAGE NOW!"
                    else "Encounter Radar: ${snapshot.encounterProgressPct}% to Next Ambush",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD8D4),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { snapshot.encounterProgressPct.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = WidgetAccentCrimson,
                    trackColor = Color(0xFF331B1E)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RadarAlertTile(
                    value = "🎁 ${snapshot.unopenedChestCount}",
                    label = if (snapshot.unopenedChestCount > 0) "OPEN VAULT" else "CHESTS",
                    accent = if (snapshot.unopenedChestCount > 0) WidgetAccentGold else Color(0xFFB89A97),
                    onClick = onTapForgeOrVault,
                    modifier = Modifier.weight(1f)
                )
                RadarAlertTile(
                    value = "📜 ${snapshot.claimableQuestCount}",
                    label = if (snapshot.claimableQuestCount > 0) "CLAIM NOW" else "QUESTS",
                    accent = if (snapshot.claimableQuestCount > 0) WidgetAccentGreen else Color(0xFFB89A97),
                    onClick = onTapMapOrQuests,
                    modifier = Modifier.weight(1f)
                )
                RadarAlertTile(
                    value = "⭐ ${snapshot.unspentMasteryPoints}",
                    label = if (snapshot.unspentMasteryPoints > 0L) "SPEND PTS" else "SKILL PTS",
                    accent = if (snapshot.unspentMasteryPoints > 0L) WidgetAccentPurple else Color(0xFFB89A97),
                    onClick = onTapForgeOrVault,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onTapBattle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = WidgetAlertBg,
                    border = BorderStroke(1.dp, WidgetAlertStroke)
                ) {
                    Text(
                        text = if (encounterReady) "⚔ FIGHT!" else "⚔ BATTLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFE2E0),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
                Surface(
                    onClick = onTapForgeOrVault,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF3A2B0E),
                    border = BorderStroke(1.dp, WidgetAccentGold)
                ) {
                    Text(
                        text = "🔨 FORGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentGold,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
                Surface(
                    onClick = onTapMapOrQuests,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0C2E36),
                    border = BorderStroke(1.dp, WidgetAccentCyan)
                ) {
                    Text(
                        text = "🗺 MAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentCyan,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarAlertTile(
    value: String,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = CmdSubCard,
        border = BorderStroke(1.dp, CmdSubBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE5C0BC),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeroArmoryWidgetPreview(
    snapshot: WidgetSnapshotState,
    onRefresh: () -> Unit,
    onTapArmory: () -> Unit
) {
    ThemedWidgetFrame(
        topColor = HeroTop,
        bottomColor = HeroBottom,
        borderColor = HeroBorder
    ) {
        Column(
            modifier = Modifier.clickable { onTapArmory() },
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lv.${snapshot.level}${snapshot.prestigeTag()} ${snapshot.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = WidgetTextCream,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${snapshot.elementSymbol} ${snapshot.elementLabel} • ${snapshot.specializationTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF3A2B0E),
                    border = BorderStroke(1.dp, WidgetAccentGold)
                ) {
                    Text(
                        text = "PWR ${snapshot.heroPower}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    color = HeroSubCard,
                    border = BorderStroke(1.dp, HeroSubBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Hero Armory preview",
                        tint = WidgetAccentGold,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = HeroSubCard,
                border = BorderStroke(1.dp, HeroSubBorder)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HP ${snapshot.currentHp}/${snapshot.maxHp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WidgetAccentGreen,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { snapshot.hpPercent() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = WidgetAccentGreen,
                                trackColor = Color(0xFF2E261F)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EN ${snapshot.strideEnergy}/${snapshot.maxStrideEnergy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WidgetAccentGold,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { snapshot.energyPercent() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = WidgetAccentGold,
                                trackColor = Color(0xFF2E261F)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ATK ${snapshot.attack} • DEF ${snapshot.defense} • CRIT ${snapshot.critChancePct}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = WidgetTextCream,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val gearColor = when {
                        snapshot.hasHardcoreAlert() -> Color(0xFFFF7A7A)
                        snapshot.equippedArmorSlots <= 0 -> Color(0xFFFBBF24)
                        else -> Color(0xFF86EFAC)
                    }
                    Text(
                        text = snapshot.gearLoadoutSummary(),
                        style = MaterialTheme.typography.labelSmall,
                        color = gearColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = HeroSubCard,
                    border = BorderStroke(1.dp, HeroSubBorder)
                ) {
                    Text(
                        text = "🪙 ${snapshot.solCoins} • ☀ ${snapshot.sunshards} • ⛏ ${snapshot.sunsteelOre} • ✨ ${snapshot.elementalEssence}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE9D8C0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    onClick = onTapArmory,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF3A2B0E),
                    border = BorderStroke(1.dp, WidgetAccentGold)
                ) {
                    Text(
                        text = "🔨 FORGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PetArenaWidgetPreview(
    snapshot: WidgetSnapshotState,
    onRefresh: () -> Unit,
    onTapPets: () -> Unit
) {
    ThemedWidgetFrame(
        topColor = PetTop,
        bottomColor = PetBottom,
        borderColor = PetBorder
    ) {
        Column(
            modifier = Modifier.clickable { onTapPets() },
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (snapshot.hasPet) "${snapshot.petElementSymbol} ${snapshot.petName} • Lv.${snapshot.petLevel}"
                        else "🐾 Interactive Pet Arena",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFF8F0FF),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val rarityPrefix = if (snapshot.petRarityLabel.isNotBlank()) "${snapshot.petRarityLabel} " else ""
                    Text(
                        text = if (snapshot.hasPet) {
                            "$rarityPrefix${snapshot.petElementLabel} • Wpn T${snapshot.petFoodLevel} • Arm T${snapshot.petGearLevel}"
                        } else {
                            "No Companion Equipped • Visit Sanctuary"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD8B4FE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF311B4D),
                    border = BorderStroke(1.dp, WidgetAccentPurple)
                ) {
                    Text(
                        text = "⚡ ${snapshot.petWildStamina}/${WildPetBattleEngine.MAX_WILD_STAMINA}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE9D5FF),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    color = PetSubCard,
                    border = BorderStroke(1.dp, PetSubBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Pet Arena preview",
                        tint = WidgetAccentPurple,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = WidgetAlertBg,
                border = BorderStroke(1.dp, WidgetAlertStroke)
            ) {
                Text(
                    text = "🔒 APP OPEN — Close Solstride to Feed or Fight Wild Pets",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD6D6),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = PetSubCard,
                border = BorderStroke(1.dp, PetSubBorder)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (snapshot.hasPet) "XP ${snapshot.petXp}/${snapshot.petXpToNext} (${snapshot.petXpPercent()}%)"
                            else "Exclusive Widget Pet Leveling",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE9D5FF),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "❤ ${snapshot.petMoodPct}% • 🍖 ${snapshot.petStepTreats} • 🏆 ${snapshot.petTotalWildWins}W",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFCD34D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { snapshot.petXpPercent() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = WidgetAccentPurple,
                        trackColor = Color(0xFF1A1026)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = if (snapshot.hasPet) "Synergy: ${snapshot.petBonusSummary}"
                        else "Adopt a companion for Hero ATK/DEF Synergy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = snapshot.petLastBattleSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDFD0F2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF103626),
                    border = BorderStroke(1.dp, WidgetAccentGreen)
                ) {
                    Text(
                        text = if (snapshot.petStepTreats > 0) "🍖 FEED (Treat)" else "🍖 FEED (5c)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier.weight(1.25f),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF311B4D),
                    border = BorderStroke(1.dp, WidgetAccentPurple)
                ) {
                    Text(
                        text = "⚔ WILD FIGHT (-1⚡)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF3E8FF),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = onTapPets,
                    shape = RoundedCornerShape(10.dp),
                    color = PetSubCard,
                    border = BorderStroke(1.dp, PetSubBorder)
                ) {
                    Text(
                        text = "🐾 PETS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD8B4FE),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSleepExpeditionPreview(
    snapshot: WidgetSnapshotState,
    currentTimeMs: Long,
    onRefresh: () -> Unit,
    onTapExpedition: () -> Unit
) {
    ThemedWidgetFrame(
        topColor = StepsTop,
        bottomColor = StepsBottom,
        borderColor = StepsBorder
    ) {
        Column(
            modifier = Modifier.clickable { onTapExpedition() },
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${snapshot.formattedSteps()} Steps",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color(0xFFECFEFF),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Goal ${snapshot.formattedGoal()} (${snapshot.stepGoalPercent()}%) • Week ${snapshot.formattedWeeklySteps()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7DD3FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0C2E36),
                    border = BorderStroke(1.dp, WidgetAccentCyan)
                ) {
                    Text(
                        text = if (snapshot.isWatchConnected) "⌚ Watch" else "📱 Phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    color = StepsSubCard,
                    border = BorderStroke(1.dp, StepsSubBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Steps and Sleep preview",
                        tint = WidgetAccentCyan,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { snapshot.stepGoalPercent() / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = WidgetAccentCyan,
                trackColor = Color(0xFF0E2A36)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StepsSubCard,
                border = BorderStroke(1.dp, StepsSubBorder)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = snapshot.sleepStatusSummary(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snapshot.isHeroSleeping) Color(0xFFC4B5FD) else Color(0xFF93C5FD),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val staminaRewardLine = if (snapshot.petWildStamina >= WildPetBattleEngine.MAX_WILD_STAMINA) {
                        "⭐ Steps→Hero XP • ⚡ Pet Stamina FULL (${snapshot.petWildStamina}/5)"
                    } else {
                        "⭐ Steps→Hero XP • ⚡ +1 Pet Stamina in ${snapshot.stepsToNextStamina} steps"
                    }
                    Text(
                        text = staminaRewardLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA5F3FC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (snapshot.hasActiveExpedition && snapshot.expeditionEndMs > 0L) {
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { snapshot.expeditionProgressPercent(currentTimeMs) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = WidgetAccentGold,
                            trackColor = Color(0xFF1C3540)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.expeditionStatusLine(currentTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFCD34D),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    onClick = onTapExpedition,
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF0C2E36),
                    border = BorderStroke(1.dp, WidgetAccentCyan)
                ) {
                    Text(
                        text = "EXPEDITION →",
                        style = MaterialTheme.typography.labelSmall,
                        color = WidgetAccentCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

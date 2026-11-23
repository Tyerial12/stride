package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.DerivedHeroStats
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.OwnedPet
import com.example.data.model.ParryGrade
import com.example.data.model.WeeklyWorldBoss
import com.example.data.model.WorldBossContribution
import com.example.data.model.WorldFogState
import com.example.data.model.currentCalendarDateKey
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.ClassSkillCatalog
import com.example.domain.CombatEngine
import com.example.domain.EnemyIntentType
import com.example.domain.ExpeditionEngine
import com.example.domain.ProgressionEngine
import com.example.ui.components.AnimatedBattleStage
import com.example.ui.components.ExpeditionLockBanner
import com.example.ui.components.HeroShowcaseCard
import com.example.ui.components.HeroSleepingBanner
import com.example.ui.components.DualPoolEnergyStaminaHudCard
import com.example.ui.components.MultiRoomStepDungeonCard
import com.example.ui.components.SolCoinBadge
import com.example.ui.components.SolstrideCard
import com.example.ui.components.StepSlayerContractsCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.components.WeeklyWorldBossCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleScreen(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    inventory: List<InventoryItem>,
    equippedPet: OwnedPet?,
    battleMode: BattleMode,
    battle: BattleState,
    expeditionLocked: Boolean,
    expeditionRemainingMs: Long,
    onSelectBattleMode: (BattleMode) -> Unit,
    onJumpToExpedition: () -> Unit,
    onEngageBattle: (Boolean) -> Unit,
    onStrike: () -> Unit,
    onSolarSkill: () -> Unit,
    onClassActiveSkill: (Int) -> Unit = {},
    onUltimateCombo: () -> Unit = {},
    onGuard: () -> Unit,
    onUseElixir: () -> Unit,
    onRetreat: () -> Unit,
    onToggleHardcoreOath: () -> Unit = {},
    onSelectSolCoinWager: (Long) -> Unit = {},
    onOpenDroppedChest: (LootChest) -> Unit,
    onDismissOutcome: () -> Unit,
    isHeroSleeping: Boolean = false,
    sleepSourceLabel: String = "",
    activeSleepMinutes: Long = 0L,
    onWakeHero: () -> Unit = {},
    worldFog: WorldFogState = WorldFogState(),
    parryRingProgress: Float = 0f,
    onTriggerParryDodge: () -> Unit = {},
    onPetEvolutionBurst: () -> Unit = {},
    weeklyWorldBoss: WeeklyWorldBoss? = null,
    worldBossContributions: List<WorldBossContribution> = emptyList(),
    onStrikeWorldBoss: () -> Unit = {},
    onClaimWorldBossMilestone: (Int) -> Unit = {},
    onSelectStepDungeon: (String) -> Unit = {},
    onSprintDungeonCorridor: () -> Unit = {},
    onResolveDungeonChamber: () -> Unit = {},
    onClaimSlayerContract: (String) -> Unit = {},
    hasWatchSleepDetection: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val elixirCount = inventory.filter { it.resolvedSlot() == ItemSlot.CONSUMABLE }.sumOf { it.quantity }
    val currentRealm = ProgressionEngine.realmForNumber(profile.currentRealm.toInt())
    val todayRiftAffinity = remember { ProgressionEngine.dailyRiftAffinity(currentCalendarDateKey()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isHeroSleeping && !battle.active) {
            item {
                HeroSleepingBanner(
                    sleepSourceLabel = sleepSourceLabel,
                    activeSleepMinutes = activeSleepMinutes,
                    onWakeHero = onWakeHero
                )
            }
        }

        if (expeditionLocked) {
            item {
                ExpeditionLockBanner(
                    remainingMs = expeditionRemainingMs,
                    onJumpToExpedition = onJumpToExpedition
                )
            }
        }

        val outcome = battle.outcome
        if (outcome != null) {
            item {
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (outcome.victory) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    strongBorder = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (outcome.victory) Icons.Filled.EmojiEvents else Icons.Filled.Shield,
                            contentDescription = "Battle outcome",
                            tint = if (outcome.victory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when {
                                outcome.wasBossVictory -> "BOSS VICTORY CLAIMED"
                                outcome.victory -> "VICTORY CLAIMED"
                                outcome.retreated -> "RETREATED SAFELY"
                                else -> "HARDCORE DEFEAT TOLL"
                            },
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = outcome.summaryLine, style = MaterialTheme.typography.bodyMedium)

                    outcome.progressionNote?.let { note ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (outcome.solCoinsEarned > 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MonetizationOn,
                                    contentDescription = "Sol Coins reward",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "+${outcome.solCoinsEarned} SOL COINS EARNED",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (outcome.solCoinsLost > 0L || outcome.woundInflicted || outcome.gearDurabilityLossPct > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                        ) {
                            val penaltyText = buildList {
                                if (outcome.solCoinsLost > 0L) add("-${outcome.solCoinsLost} Sol Coins")
                                if (outcome.woundInflicted) add("+1 Battle Wound (-10% HP/ATK)")
                                if (outcome.gearDurabilityLossPct > 0) add("-${outcome.gearDurabilityLossPct}% Gear Durability")
                            }.joinToString(" • ")
                            Text(
                                text = "Penalty: $penaltyText",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (outcome.oreEarned > 0L || outcome.herbsEarned > 0L || outcome.essenceEarned > 0L || outcome.droppedRune != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, ext.borderStrong)
                        ) {
                            val matText = buildList {
                                if (outcome.oreEarned > 0L) add("+${outcome.oreEarned} Sunsteel Ore")
                                if (outcome.herbsEarned > 0L) add("+${outcome.herbsEarned} Aether Herbs")
                                if (outcome.essenceEarned > 0L) add("+${outcome.essenceEarned} Essence")
                                outcome.droppedRune?.let { add("+1 ${it.symbol} ${it.label} Rune!") }
                            }.joinToString(" • ")
                            Text(
                                text = "Spoils: $matText",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (outcome.leveledUpTo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "LEVEL UP! Reached Level ${outcome.leveledUpTo} (+2 Mastery Points)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    outcome.droppedChest?.let { chest ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, ext.rarityUltraRare)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "Dropped treasure chest",
                                        tint = ext.rarityUltraRare
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = chest.resolvedTier().title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Ultra Rare Odds: ${chest.ultraRareChancePct}% (6-Slot Loot)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = ext.rarityUltraRare
                                        )
                                    }
                                }
                                if (chest.id.isNotBlank()) {
                                    Button(
                                        onClick = { onOpenDroppedChest(chest) },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("battle_open_dropped_chest_button")
                                    ) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Loot")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onDismissOutcome()
                                onEngageBattle(false)
                            },
                            enabled = !expeditionLocked,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("battle_again_button")
                        ) {
                            Text(
                                when (outcome.mode) {
                                    BattleMode.ENDLESS_SPIRE -> "Next Floor"
                                    BattleMode.DAILY_ELEMENTAL_RIFT -> "Re-Enter Rift"
                                    BattleMode.WEEKLY_BOSS_RAID -> "Raid Again"
                                    BattleMode.REALM_CAMPAIGN -> "Next Stage"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = onDismissOutcome,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dismiss_outcome_button"),
                            border = BorderStroke(1.5.dp, ext.borderStrong)
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }

        val enemy = battle.enemy
        if (!battle.active || enemy == null) {
            // 4-Mode Switcher: 10-Realm, Spire, Daily Rift, Raid Boss
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BattleMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = battleMode == mode,
                            onClick = { onSelectBattleMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, BattleMode.entries.size),
                            modifier = Modifier.testTag("battle_mode_${mode.name.lowercase()}"),
                            icon = {
                                val iconVec = when (mode) {
                                    BattleMode.REALM_CAMPAIGN -> Icons.Filled.Map
                                    BattleMode.ENDLESS_SPIRE -> Icons.Filled.Stairs
                                    BattleMode.DAILY_ELEMENTAL_RIFT -> Icons.Filled.AutoAwesome
                                    BattleMode.WEEKLY_BOSS_RAID -> Icons.Filled.Whatshot
                                }
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            // Dual-Pool Real-Life Step Battle Energy & Sleep Stamina HUD + Pinnacle Chest Rule
            item {
                DualPoolEnergyStaminaHudCard(
                    profile = profile,
                    stats = stats,
                    hardcoreState = hardcoreState,
                    hasWatchSleepDetection = hasWatchSleepDetection
                )
            }

            // Multi-Room Step Dungeons & Labyrinth Delves (Boss Chest at Final Vault)
            item {
                MultiRoomStepDungeonCard(
                    profile = profile,
                    hardcoreState = hardcoreState,
                    onSelectDungeon = onSelectStepDungeon,
                    onSprintCorridor = onSprintDungeonCorridor,
                    onResolveChamber = onResolveDungeonChamber
                )
            }

            // Visible 2D Hero Preview ready for combat
            item {
                HeroShowcaseCard(
                    profile = profile,
                    stats = stats,
                    inventory = inventory,
                    equippedPet = equippedPet,
                    compact = true,
                    isSleeping = isHeroSleeping,
                    sleepSourceLabel = sleepSourceLabel,
                    worldFog = worldFog
                )
            }

            // Step-Fueled Slayer Contracts Board
            item {
                StepSlayerContractsCard(
                    hardcoreState = hardcoreState,
                    onClaimContract = onClaimSlayerContract
                )
            }

            // Ironstride Hardcore Oath & Blood-Oath Sol Coin Wager Card
            item {
                HardcoreOathAndWagerCard(
                    profile = profile,
                    hardcoreState = hardcoreState,
                    expeditionLocked = expeditionLocked,
                    onToggleHardcoreOath = onToggleHardcoreOath,
                    onSelectSolCoinWager = onSelectSolCoinWager
                )
            }

            item {
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    strongBorder = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (battleMode) {
                                    BattleMode.REALM_CAMPAIGN ->
                                        "Realm ${currentRealm.realmNumber}: ${currentRealm.title} (${currentRealm.affinity.symbol})"
                                    BattleMode.ENDLESS_SPIRE ->
                                        "Endless Spire • Floor ${profile.currentSpireFloor}"
                                    BattleMode.DAILY_ELEMENTAL_RIFT ->
                                        "Daily ${todayRiftAffinity.symbol} ${todayRiftAffinity.label} Rift (2 Waves)"
                                    BattleMode.WEEKLY_BOSS_RAID ->
                                        "Weekly Colossal Raid • Kronos, Eclipse Titan"
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = when (battleMode) {
                                    BattleMode.REALM_CAMPAIGN ->
                                        "Stage ${profile.realmStage}/5 • Boss: ${currentRealm.bossName} • Weak to ${currentRealm.affinity.weakAgainst().label}"
                                    BattleMode.ENDLESS_SPIRE -> {
                                        val nextBossFloor = (((profile.currentSpireFloor - 1L) / 5L) + 1L) * 5L
                                        "Highest Cleared: Floor ${profile.highestSpireFloor} • Multi-Wave & Boss at F$nextBossFloor"
                                    }
                                    BattleMode.DAILY_ELEMENTAL_RIFT ->
                                        "Guaranteed ${todayRiftAffinity.label} Rune, Elemental Essence & +8 Sol Coins!"
                                    BattleMode.WEEKLY_BOSS_RAID ->
                                        "2-Phase Raid Boss with Doom Timer • +25 Sol Coins, Rune & Sovereign Chest!"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SolCoinBadge(solCoins = profile.solCoins)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (battleMode) {
                            BattleMode.REALM_CAMPAIGN ->
                                "${currentRealm.subtitle}. Your Hero Affinity is ${stats.primaryAffinity.symbol} ${stats.primaryAffinity.label} (${stats.activeSpecialization.ultimateName}). Socket Elemental Runes in the Forge to exploit 1.35x elemental weaknesses!"
                            BattleMode.ENDLESS_SPIRE ->
                                "Infinite scaling multi-wave gauntlet! Every 5th floor spawns a 2-Phase Spire Sovereign Boss awarding +10 Sol Coins, Runes & Sovereign Chests."
                            BattleMode.DAILY_ELEMENTAL_RIFT ->
                                "Rotating daily 2-wave Elemental Rift! Clear both waves to harvest Elemental Runes, Sunsteel Ore, Aether Herbs, Essence & Sol Coins."
                            BattleMode.WEEKLY_BOSS_RAID ->
                                "Endgame Raid Encounter! Kronos enters Phase II Enrage at 50% HP with a 7-Turn Solar Doom Timer. Bring your Pet Synergy Combo & Ultimate!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    TacticalStatBar(
                        label = if (expeditionLocked) "Encounter Gauge (Off on Expedition)" else "Encounter Gauge",
                        current = profile.encounterProgress,
                        maximum = 100L,
                        barColor = MaterialTheme.colorScheme.primary,
                        suffix = "%"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TacticalStatBar(
                        label = "Solar Stride Energy",
                        current = profile.strideEnergy,
                        maximum = stats.computedMaxEnergy.toLong(),
                        barColor = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onEngageBattle(false) },
                            enabled = !expeditionLocked,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("start_skirmish_button")
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (battleMode) {
                                    BattleMode.ENDLESS_SPIRE -> "Fight F${profile.currentSpireFloor}"
                                    BattleMode.DAILY_ELEMENTAL_RIFT -> "Enter Rift"
                                    BattleMode.WEEKLY_BOSS_RAID -> "Launch Raid"
                                    BattleMode.REALM_CAMPAIGN ->
                                        if (profile.encounterProgress >= 100L) "Fight R${profile.currentRealm}-${profile.realmStage}" else "Scout (15 EN)"
                                }
                            )
                        }
                        if (battleMode == BattleMode.REALM_CAMPAIGN || battleMode == BattleMode.ENDLESS_SPIRE) {
                            OutlinedButton(
                                onClick = { onEngageBattle(true) },
                                enabled = !expeditionLocked,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("start_boss_button"),
                                border = BorderStroke(1.5.dp, ext.borderStrong)
                            ) {
                                Text("Boss (+10 Coins)")
                            }
                        }
                    }
                }
            }

            // Shared Live Weekly World Boss Raid Card (Real Firestore Community HP & Milestones)
            item {
                WeeklyWorldBossCard(
                    profile = profile,
                    worldBoss = weeklyWorldBoss,
                    contributions = worldBossContributions,
                    onStrikeWorldBoss = onStrikeWorldBoss,
                    onClaimWorldBossMilestone = onClaimWorldBossMilestone
                )
            }
            return@LazyColumn
        }

        // LIVE 2D ANIMATED HERO VS ENEMY BATTLE STAGE + COMBO / MUTATOR BADGES!
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (battle.comboCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            border = BorderStroke(1.2.dp, Color(0xFFF59E0B))
                        ) {
                            Text(
                                text = "🔥 ${battle.comboCount}x COMBO (+${battle.comboCount * 10}% DMG)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    battle.lastParryGrade?.let { grade ->
                        val gColor = when (grade) {
                            ParryGrade.PERFECT -> Color(0xFF10B981)
                            ParryGrade.GLANCING -> Color(0xFFF59E0B)
                            ParryGrade.MISSED -> Color(0xFFEF4444)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = gColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, gColor)
                        ) {
                            Text(
                                text = grade.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = gColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (battle.weeklyMutatorLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "⚡ ${battle.weeklyMutatorLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                AnimatedBattleStage(
                    profile = profile,
                    inventory = inventory,
                    equippedPet = equippedPet,
                    battle = battle,
                    enemy = enemy,
                    worldFog = worldFog
                )
            }
        }

        // ACTIVE TIMING DODGE / PARRY RING OVERLAY WHEN ENEMY STRIKES!
        if (battle.pendingEnemyTelegraph) {
            item {
                val relaxed = hardcoreState.relaxedTimingAssist
                val inPerfect = if (relaxed) parryRingProgress in 0.50f..0.94f else parryRingProgress in 0.62f..0.89f
                val ringAccent = if (inPerfect) Color(0xFF10B981) else Color(0xFFF59E0B)
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color(0xFF1A1025),
                    strongBorder = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (inPerfect) "⚡ PARRY NOW! (PERFECT WINDOW)" else "⚔ INCOMING ENEMY STRIKE — TIME YOUR PARRY!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (inPerfect) Color(0xFF34D399) else Color(0xFFFDE68A)
                        )
                        Box(
                            modifier = Modifier.size(116.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val maxR = size.minDimension * 0.48f
                                val targetR = maxR * 0.38f
                                val currentR = maxR * (1f - parryRingProgress * 0.72f)
                                // Target emerald ring
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = 0.25f),
                                    radius = targetR,
                                    center = c
                                )
                                drawCircle(
                                    color = Color(0xFF34D399),
                                    radius = targetR,
                                    center = c,
                                    style = Stroke(width = 5.dp.toPx())
                                )
                                // Contracting golden timing ring
                                drawCircle(
                                    color = ringAccent,
                                    radius = currentR.coerceAtLeast(8f),
                                    center = c,
                                    style = Stroke(width = 4.5.dp.toPx())
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Parry target",
                                tint = ringAccent,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Button(
                            onClick = onTriggerParryDodge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (inPerfect) Color(0xFF059669) else Color(0xFFD97706)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("battle_parry_dodge_button")
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = "Dodge and Parry")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (inPerfect) "PERFECT PARRY + COUNTER!" else "TAP TO DODGE / PARRY!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Enemy HP Bar, Elemental Matchup, Active Status Effects & Intent Telegraph Card
        item {
            val elemMult = stats.primaryAffinity.damageMultiplierAgainst(enemy.affinity)
            val matchupLabel = when {
                elemMult > 1.1f -> "SUPER EFFECTIVE 1.35x (${stats.primaryAffinity.symbol} vs ${enemy.affinity.symbol})"
                elemMult < 0.9f -> "RESISTED 0.75x (${stats.primaryAffinity.symbol} vs ${enemy.affinity.symbol})"
                else -> "NEUTRAL 1.0x (${stats.primaryAffinity.symbol} vs ${enemy.affinity.symbol})"
            }
            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${enemy.name} (${enemy.affinity.symbol} ${enemy.affinity.label})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            elemMult > 1.1f -> MaterialTheme.colorScheme.primaryContainer
                            elemMult < 0.9f -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(1.dp, ext.borderSubtle)
                    ) {
                        Text(
                            text = matchupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TacticalStatBar(
                    label = "Enemy HP (Shield ${enemy.shield}${if (enemy.phase >= 2) " • PHASE II" else ""})",
                    current = enemy.currentHp.toLong(),
                    maximum = enemy.maxHp.toLong(),
                    barColor = MaterialTheme.colorScheme.error
                )

                if (enemy.statuses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        enemy.statuses.forEach { st ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "${st.type.badge} ${st.type.label} (${st.turnsRemaining}T)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Enemy next attack intent",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val intentDesc = buildString {
                            if (enemy.nextIntent.type == EnemyIntentType.AEGIS_WARD) {
                                append("Intent: ${enemy.nextIntent.type.label} (+${enemy.nextIntent.shieldGain} Shield)")
                            } else {
                                append("Intent: ${enemy.nextIntent.type.label} (~${enemy.nextIntent.expectedDamage} DMG)")
                            }
                            enemy.nextIntent.inflictsStatus?.let { st ->
                                append(" + ${st.label}")
                            }
                        }
                        Text(
                            text = intentDesc,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Player Combat Vitals, Valor, Pet Synergy Gauge, Aegis Barrier & Active Statuses
        item {
            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${profile.displayName} (ATK ${stats.totalAttack} • DEF ${stats.totalDefense})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "VALOR ${battle.valor}% • PET ${battle.petSynergy}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (battle.valor >= 100 || battle.petSynergy >= 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TacticalStatBar(
                    label = if (battle.heroBarrier > 0) "Hero HP (+${battle.heroBarrier} Aegis Barrier)" else "Hero HP",
                    current = profile.currentHp,
                    maximum = stats.computedMaxHp.toLong(),
                    barColor = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
                TacticalStatBar(
                    label = "Solar Stride Energy",
                    current = profile.strideEnergy,
                    maximum = stats.computedMaxEnergy.toLong(),
                    barColor = MaterialTheme.colorScheme.primary
                )

                if (battle.heroStatuses.isNotEmpty() || battle.activeWager > 0L || battle.hardcoreActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (battle.hardcoreActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Text(
                                    text = "IRONSTRIDE OATH",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (battle.activeWager > 0L) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "WAGER ${battle.activeWager} COINS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        battle.heroStatuses.forEach { st ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, ext.borderStrong)
                            ) {
                                Text(
                                    text = "${st.type.badge} ${st.type.label} (${st.turnsRemaining}T)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Oversized High-Contrast Tactical Command Pad (Class Active Skills + Ultimate & Pet Combo!)
        item {
            val ultimateReady = battle.valor >= 100 || battle.petSynergy >= 100
            val activeClassSkills = remember(profile.heroClass) {
                ClassSkillCatalog.activeSkillsFor(profile.resolvedClass())
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (worldFog.fogCritBonusPct > 0 || worldFog.restedClarityBattlesLeft > 0 || worldFog.fogLanternCharges > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        val buffs = buildList {
                            if (worldFog.fogCritBonusPct > 0) add("Fog Clarity +${worldFog.fogCritBonusPct}% Crit")
                            if (worldFog.restedClarityBattlesLeft > 0) add("Rested Clarity +${worldFog.restedXpBonusPct}% XP (${worldFog.restedClarityBattlesLeft}B)")
                            if (worldFog.fogLanternCharges > 0) add("Fog Lantern (${worldFog.fogLanternCharges})")
                        }.joinToString(" • ")
                        Text(
                            text = "World Buffs: $buffs",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // 3 Class Active Skills Row (4-Tier Mastery Evolution • Unlocked by Level, Upgraded by Sol Coins)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeClassSkills.forEachIndexed { idx, skill ->
                        val unlocked = skill.isUnlockedAt(profile.level.toInt())
                        val rank = ClassSkillCatalog.effectiveRank(profile, skill)
                        val masteryTier = skill.masteryTierForRank(rank)
                        val effectiveValorCost = skill.effectiveValorCost(rank)
                        val evolvedName = skill.evolvedNameForRank(rank)
                        val cooldown = battle.skillCooldowns.getOrElse(idx) { 0 }
                        val canCast = !expeditionLocked &&
                            unlocked &&
                            cooldown == 0 &&
                            battle.valor >= effectiveValorCost

                        Button(
                            onClick = { onClassActiveSkill(idx) },
                            enabled = canCast,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .testTag("battle_class_skill_$idx"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = evolvedName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = when {
                                        !unlocked -> "Lv.${skill.unlockLevel} Lock"
                                        cooldown > 0 -> "CD ${cooldown}T"
                                        else -> "${masteryTier.roman} R$rank • ${effectiveValorCost}V"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStrike,
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("battle_strike_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Gavel, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Strike (${stats.primaryAffinity.symbol})", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onSolarSkill,
                        enabled = !expeditionLocked && profile.strideEnergy >= 20L,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("battle_skill_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (battle.valor >= 100) "Solar Surge!" else "Surge (20 EN)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Class Specialization Ultimate + Pet Synergy Combo Button
                Button(
                    onClick = onUltimateCombo,
                    enabled = !expeditionLocked && !battle.pendingEnemyTelegraph && ultimateReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("battle_ultimate_combo_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(Icons.Filled.Pets, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    val petTag = equippedPet?.resolvedType()?.activeSkillName?.let { " + $it" } ?: ""
                    Text(
                        text = if (ultimateReady) {
                            "UNLEASH: ${stats.activeSpecialization.ultimateName}$petTag!"
                        } else {
                            "Ultimate + Pet Combo (Valor ${battle.valor}% / Synergy ${battle.petSynergy}%)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pet Evolution Signature Burst Skill Button
                if (equippedPet != null) {
                    val petForm = equippedPet.resolvedForm()
                    val burstReady = battle.petBurstCharge >= 100
                    Button(
                        onClick = onPetEvolutionBurst,
                        enabled = !expeditionLocked && !battle.pendingEnemyTelegraph && burstReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (burstReady) Color(petForm.auraHex) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (burstReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("battle_pet_evolution_burst_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Pet Evolution Burst")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (burstReady) {
                                "★ ${petForm.burstSkillName.uppercase()} (READY!)"
                            } else {
                                "${petForm.formName} Burst: ${battle.petBurstCharge}% (Parry & Strike to charge)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onGuard,
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("battle_guard_button"),
                        border = BorderStroke(2.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guard (+Aegis)", style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(
                        onClick = onUseElixir,
                        enabled = !expeditionLocked && elixirCount > 0L && battle.elixirCooldownTurns == 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("battle_elixir_button"),
                        border = BorderStroke(2.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        val elixirLabel = if (battle.elixirCooldownTurns > 0) {
                            "Elixir (${battle.elixirCooldownTurns}T CD)"
                        } else {
                            "Elixir ($elixirCount)"
                        }
                        Text(elixirLabel, style = MaterialTheme.typography.titleSmall)
                    }
                }

                OutlinedButton(
                    onClick = onRetreat,
                    enabled = !expeditionLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("battle_retreat_button"),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val retreatText = if (battle.activeWager > 0L) {
                        "Tactical Retreat (Forfeit ${battle.activeWager} Coins)"
                    } else {
                        "Tactical Retreat"
                    }
                    Text(retreatText, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Crisp Combat Log
        item {
            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Combat Feed (Voice: Strike · Skill · Guard · Heal)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                battle.combatLog.reversed().forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HardcoreOathAndWagerCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    expeditionLocked: Boolean,
    onToggleHardcoreOath: () -> Unit,
    onSelectSolCoinWager: (Long) -> Unit
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (hardcoreState.hardcoreOathEnabled) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        strongBorder = hardcoreState.hardcoreOathEnabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = "Ironstride Hardcore Oath",
                    tint = if (hardcoreState.hardcoreOathEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ironstride Hardcore Oath",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "+35% Enemy Power & Boss Doom Timer • +60% Sol Coins, +50% XP & 2x Rune/Mythic Odds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = hardcoreState.hardcoreOathEnabled,
                onCheckedChange = { onToggleHardcoreOath() },
                enabled = !expeditionLocked,
                modifier = Modifier.testTag("toggle_hardcore_oath_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Blood-Oath Sol Coin Wager (Win 2x stake profit on Victory • Lose stake + 15% Coin Toll & +1 Battle Wound on Defeat)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CombatEngine.wagerTiers.forEach { tier ->
                val isSelected = hardcoreState.activeSolCoinWager == tier
                val affordable = profile.solCoins >= tier
                Surface(
                    onClick = { onSelectSolCoinWager(tier) },
                    enabled = !expeditionLocked && (tier == 0L || affordable),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else ext.borderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wager_tier_$tier")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (tier == 0L) "No Wager" else "$tier Coins",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (tier == 0L) "Safe" else "Win +${tier * 2}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

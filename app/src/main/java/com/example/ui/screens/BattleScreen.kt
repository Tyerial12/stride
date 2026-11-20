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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DerivedHeroStats
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.OwnedPet
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.EnemyIntentType
import com.example.domain.ExpeditionEngine
import com.example.domain.ProgressionEngine
import com.example.ui.components.AnimatedBattleStage
import com.example.ui.components.ExpeditionLockBanner
import com.example.ui.components.HeroShowcaseCard
import com.example.ui.components.SolCoinBadge
import com.example.ui.components.SolstrideCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleScreen(
    profile: HeroProfile,
    stats: DerivedHeroStats,
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
    onGuard: () -> Unit,
    onUseElixir: () -> Unit,
    onRetreat: () -> Unit,
    onOpenDroppedChest: (LootChest) -> Unit,
    onDismissOutcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val elixirCount = inventory.filter { it.resolvedSlot() == ItemSlot.CONSUMABLE }.sumOf { it.quantity }
    val currentRealm = ProgressionEngine.realmSpec(profile.currentRealm.toInt())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when {
                                outcome.wasBossVictory -> "BOSS VICTORY CLAIMED"
                                outcome.victory -> "VICTORY CLAIMED"
                                outcome.retreated -> "RETREATED SAFELY"
                                else -> "REGROUP AT CAMP"
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
                                    text = "+${outcome.solCoinsEarned} SOL COINS EARNED (Boss Victory Reward)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
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
                                            text = "Ultra Rare Odds: ${chest.ultraRareChancePct}%",
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
                            Text(if (outcome.mode == BattleMode.ENDLESS_SPIRE) "Next Floor" else "Next Stage")
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
            // Mode Switcher: Realm Campaign vs Endless Spire Gauntlet
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    BattleMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = battleMode == mode,
                            onClick = { onSelectBattleMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, BattleMode.entries.size),
                            modifier = Modifier.testTag("battle_mode_${mode.name.lowercase()}"),
                            icon = {
                                Icon(
                                    imageVector = if (mode == BattleMode.REALM_CAMPAIGN) Icons.Filled.Map else Icons.Filled.Stairs,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Visible 2D Hero Preview ready for combat
            item {
                HeroShowcaseCard(
                    profile = profile,
                    stats = stats,
                    inventory = inventory,
                    equippedPet = equippedPet,
                    compact = true
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
                                text = if (battleMode == BattleMode.REALM_CAMPAIGN) {
                                    "Realm ${currentRealm.realmNumber}: ${currentRealm.name}"
                                } else {
                                    "Endless Spire • Floor ${profile.currentSpireFloor}"
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = if (battleMode == BattleMode.REALM_CAMPAIGN) {
                                    "Stage ${profile.realmStage}/5 • Guardian: ${currentRealm.guardianName}"
                                } else {
                                    val nextBossFloor = (((profile.currentSpireFloor - 1L) / 5L) + 1L) * 5L
                                    "Highest Cleared: Floor ${profile.highestSpireFloor} • Spire Boss at Floor $nextBossFloor"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SolCoinBadge(solCoins = profile.solCoins)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (battleMode == BattleMode.REALM_CAMPAIGN) {
                            "${currentRealm.biomeSubtitle}. Defeat Stage 5 Guardians to unlock the next Realm and earn +${ExpeditionEngine.BOSS_VICTORY_SOL_COINS} Sol Coins & Boss Vault Chests!"
                        } else {
                            "Infinite scaling gauntlet! Every 5th floor spawns a Spire Sovereign Boss awarding +${ExpeditionEngine.BOSS_VICTORY_SOL_COINS} Sol Coins & Spire Sovereign Chests with rising Ultra Rare Relic odds."
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
                                text = when {
                                    battleMode == BattleMode.ENDLESS_SPIRE -> "Fight F${profile.currentSpireFloor}"
                                    profile.encounterProgress >= 100L -> "Fight R${profile.currentRealm}-${profile.realmStage}"
                                    else -> "Scout (15 EN)"
                                }
                            )
                        }
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
            return@LazyColumn
        }

        // LIVE 2D ANIMATED HERO VS ENEMY BATTLE STAGE!
        item {
            AnimatedBattleStage(
                profile = profile,
                inventory = inventory,
                equippedPet = equippedPet,
                battle = battle,
                enemy = enemy
            )
        }

        // Enemy HP Bar & Intent Telegraph Card
        item {
            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                TacticalStatBar(
                    label = "${enemy.name} HP (Shield ${enemy.shield})",
                    current = enemy.currentHp.toLong(),
                    maximum = enemy.maxHp.toLong(),
                    barColor = MaterialTheme.colorScheme.error
                )
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
                        val intentDesc = if (enemy.nextIntent.type == EnemyIntentType.AEGIS_WARD) {
                            "Intent: ${enemy.nextIntent.type.label} (+${enemy.nextIntent.shieldGain} Shield)"
                        } else {
                            "Intent: ${enemy.nextIntent.type.label} (~${enemy.nextIntent.expectedDamage} DMG)"
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

        // Player Combat Vitals, Valor & Active Weapon/Armor Perks
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
                        text = "VALOR ${battle.valor}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (battle.valor >= 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TacticalStatBar(
                    label = "Hero HP",
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

                val combatPerks = remember(stats) {
                    buildList {
                        add("Crit ${stats.critChancePercent}%")
                        if (stats.armorPiercePercent > 0) add("Pierce ${stats.armorPiercePercent}%")
                        if (stats.lifestealPercent > 0) add("Leech ${stats.lifestealPercent}%")
                        if (stats.damageReductionPercent > 0) add("-${stats.damageReductionPercent}% DMG")
                        if (stats.thornsPercent > 0) add("Thorns ${stats.thornsPercent}%")
                        if (stats.turnRegenHp > 0) add("+${stats.turnRegenHp} Regen")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    combatPerks.forEach { perkChip ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ext.borderSubtle)
                        ) {
                            Text(
                                text = perkChip,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Oversized High-Contrast Tactical Command Pad
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text("Strike", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = onSolarSkill,
                        enabled = !expeditionLocked && profile.strideEnergy >= 20L,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("battle_skill_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (battle.valor >= 100) "Overdrive!" else "Skill (20)",
                            style = MaterialTheme.typography.titleMedium
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
                        Text("Guard (+EN)", style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(
                        onClick = onUseElixir,
                        enabled = !expeditionLocked && elixirCount > 0L,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("battle_elixir_button"),
                        border = BorderStroke(2.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Elixir ($elixirCount)", style = MaterialTheme.typography.titleSmall)
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
                    Text("Tactical Retreat", color = MaterialTheme.colorScheme.error)
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

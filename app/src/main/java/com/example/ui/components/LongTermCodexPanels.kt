package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityMasteryType
import com.example.data.model.BestiaryMasteryRank
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroProfile
import com.example.data.model.RelicSetSpec
import com.example.data.model.SeasonCatalog
import com.example.data.model.TownBuildingCatalog
import com.example.data.model.TownBuildingId
import com.example.domain.ProgressionEngine

/**
 * 30-Tier Seasonal Journey track with one-tap reward claims.
 */
@Composable
fun SeasonJourneyPanel(
    profile: HeroProfile,
    onClaimSeasonTier: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTier = SeasonCatalog.currentTierForXp(profile.seasonXp)
    val claimedSet = profile.claimedSeasonTiers.map { it.toInt() }.toSet()
    val nextTierSpec = SeasonCatalog.tiers.firstOrNull { it.tier == currentTier + 1 }
    val progress = if (nextTierSpec != null) {
        val prevXp = SeasonCatalog.tiers.firstOrNull { it.tier == currentTier }?.xpRequired ?: 0L
        val span = (nextTierSpec.xpRequired - prevXp).coerceAtLeast(1L)
        ((profile.seasonXp - prevXp).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } else 1f

    // Show claimable tiers + next 5 upcoming tiers for crisp readability
    val visibleTiers = SeasonCatalog.tiers.filter { spec ->
        val unlocked = profile.seasonXp >= spec.xpRequired
        val claimed = spec.tier in claimedSet
        (unlocked && !claimed) || spec.tier in (currentTier.coerceAtLeast(1)..(currentTier + 5).coerceAtMost(SeasonCatalog.MAX_TIERS))
    }.distinctBy { it.tier }.take(7)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "Season Journey", tint = Color(0xFFF59E0B))
                    Column {
                        Text(
                            text = SeasonCatalog.CURRENT_SEASON_TITLE.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tier $currentTier / ${SeasonCatalog.MAX_TIERS} • ${profile.seasonXp} Season XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = Color(0xFFF59E0B)
            )

            visibleTiers.forEach { tierSpec ->
                val unlocked = profile.seasonXp >= tierSpec.xpRequired
                val claimed = tierSpec.tier in claimedSet
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (unlocked && !claimed) Color(0xFFF59E0B).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tier ${tierSpec.tier} (${tierSpec.xpRequired} XP)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = tierSpec.rewardLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when {
                            claimed -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Claimed",
                                tint = Color(0xFF10B981)
                            )
                            unlocked -> Button(
                                onClick = { onClaimSeasonTier(tierSpec.tier) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("claim_season_tier_${tierSpec.tier}")
                            ) {
                                Text("Claim", fontWeight = FontWeight.Bold)
                            }
                            else -> Text(
                                text = "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5 Long-Term Activity Mastery Tracks (Combat, Wilds Exploration, Anvil Smithing, Cauldron Alchemy, Companion Bond).
 */
@Composable
fun ActivityMasteriesPanel(
    hardcoreState: HardcoreRpgState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Activity Masteries", tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("ACTIVITY MASTERIES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Permanent account-wide stat bonuses earned by playing.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ActivityMasteryType.entries.forEach { mastery ->
                val xp = hardcoreState.masteryXp(mastery)
                val lvl = ActivityMasteryType.levelForXp(xp)
                val frac = ActivityMasteryType.progressFractionForXp(xp)
                val accent = Color(mastery.accentHex)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(mastery.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
                        Text("Lv. $lvl / 25", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = mastery.bonusPerLevelSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accent
                    )
                }
            }
        }
    }
}

/**
 * 6 Collectible Relic Sets (2pc & 4pc permanent passive bonuses).
 * Respects Town-only shopping by linking to Zari's Market in Sunhaven Town.
 */
@Composable
fun RelicSetCollectionsPanel(
    hardcoreState: HardcoreRpgState,
    onVisitTownMarket: () -> Unit,
    modifier: Modifier = Modifier
) {
    val collected = hardcoreState.collectedRelicIds
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RELIC SET COLLECTIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${collected.size} / ${RelicSetSpec.allPieceIds.size} Found",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            RelicSetSpec.entries.forEach { set ->
                val count = set.pieces.count { it in collected }
                val accent = Color(set.accentHex)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = if (count >= 2) 0.14f else 0.06f),
                    border = BorderStroke(1.dp, accent.copy(alpha = if (count >= 4) 0.8f else 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(set.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = accent)
                            Text("$count / 4", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "2pc: ${set.twoPieceBonusLabel} ${if (count >= 2) "✓" else ""}  •  4pc: ${set.fourPieceBonusLabel} ${if (count >= 4) "✓" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onVisitTownMarket,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("relic_sets_visit_market_button")
            ) {
                Icon(Icons.Default.Storefront, contentDescription = "Zari's Market", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Buy Ancient Relic Coffers at Zari's Market (Town)")
            }
        }
    }
}

/**
 * Expanded Bestiary Mastery Panel with Bronze / Silver / Gold / Apex kill ranks.
 */
@Composable
fun ExpandedBestiaryPanel(
    hardcoreState: HardcoreRpgState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MenuBook, contentDescription = "Bestiary Codex", tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("MONSTER BESTIARY MASTERY", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Slay Realm Guardians & Raid Titans to unlock Bronze → Apex bonus DMG.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            (1..ProgressionEngine.MAX_REALM.toInt()).forEach { realmNum ->
                val spec = ProgressionEngine.realmSpec(realmNum)
                val key = "boss_realm_$realmNum"
                val kills = hardcoreState.bestiaryKills(key)
                val rank = BestiaryMasteryRank.forKills(kills)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spec.guardianName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("Realm $realmNum • $kills slain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (kills > 0) Color(0xFFF59E0B).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${rank.label} (+${rank.bonusDmgPct}% DMG)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Town Building Upgrade Card embedded inside each Sunhaven Plaza NPC modal (Town-only upgrades!).
 */
@Composable
fun TownBuildingUpgradeSection(
    buildingId: TownBuildingId,
    profile: HeroProfile,
    oreCount: Long,
    onUpgradeBuilding: (TownBuildingId) -> Unit,
    modifier: Modifier = Modifier
) {
    val level = TownBuildingCatalog.levelOf(profile.townBuildingLevels, buildingId)
    val cost = TownBuildingCatalog.upgradeCost(level)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Castle, contentDescription = "Town Building", tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    Text(
                        text = "${buildingId.title} • Lv. $level / 5",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Text(
                text = buildingId.bonusDescription(level),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cost != null) {
                val canAfford = profile.solCoins >= cost.solCoins &&
                    profile.sunshards >= cost.sunshards &&
                    oreCount >= cost.oreCost
                Text(
                    text = "Next Lv.${cost.nextLevel} Cost: ${cost.solCoins} Coins • ${cost.sunshards} Shards • ${cost.oreCost} Ore",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706)
                )
                Button(
                    onClick = { onUpgradeBuilding(buildingId) },
                    enabled = canAfford,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upgrade_building_${buildingId.name.lowercase()}")
                ) {
                    Icon(Icons.Default.Upgrade, contentDescription = "Upgrade Building", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (canAfford) "Upgrade to Lv.${cost.nextLevel}" else "Need Materials for Lv.${cost.nextLevel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = "MAX LEVEL 5 ARCHITECTURAL MASTERWORK!",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF059669)
                )
            }
        }
    }
}

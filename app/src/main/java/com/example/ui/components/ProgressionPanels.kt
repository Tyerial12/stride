package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HeroProfile
import com.example.domain.ProgressionEngine
import com.example.domain.QuestCategory
import com.example.domain.QuestStatus
import com.example.domain.SkillBranch
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

@Composable
fun SkillTreePanel(
    profile: HeroProfile,
    expeditionLocked: Boolean,
    onUpgradeNode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val ranks = remember(profile.skillRanks) { profile.parsedSkillRanks() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("skill_tree_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SolstrideCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            strongBorder = true
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Class Skill Tree",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Invest Mastery Points into permanent combat, defense & expedition survival skills. Skill ranks are kept through Prestige Rebirth!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, ext.borderStrong)
                ) {
                    Text(
                        text = "${profile.unspentPoints} PTS",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        SkillBranch.entries.forEach { branch ->
            val branchNodes = ProgressionEngine.skillNodes.filter { it.branch == branch }
            val branchIcon = when (branch) {
                SkillBranch.SOLAR_OFFENSE -> Icons.Filled.LocalFireDepartment
                SkillBranch.IRON_BASTION -> Icons.Filled.Shield
                SkillBranch.WAYFARER_MASTERY -> Icons.Filled.Explore
            }

            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = branchIcon,
                        contentDescription = branch.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = branch.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = branch.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    branchNodes.forEach { node ->
                        val rank = ranks.getOrElse(node.index) { 0 }
                        val isMaxed = rank >= node.maxRank
                        val canUpgrade = !expeditionLocked && profile.unspentPoints > 0L && !isMaxed

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (rank > 0) 1.5.dp else 1.dp,
                                color = if (rank > 0) MaterialTheme.colorScheme.primary else ext.borderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(text = node.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "$rank/${node.maxRank}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = node.perRankSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onUpgradeNode(node.index) },
                                    enabled = canUpgrade,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("skill_upgrade_${node.id}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    if (isMaxed) {
                                        Text("MAX")
                                    } else {
                                        Icon(Icons.Filled.Add, contentDescription = "Upgrade ${node.name}", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("+1")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestsAndPrestigePanel(
    profile: HeroProfile,
    questStatuses: List<QuestStatus>,
    expeditionLocked: Boolean,
    battleActive: Boolean,
    onClaimQuest: (QuestStatus) -> Unit,
    onTriggerPrestige: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    var showPrestigeConfirm by remember { mutableStateOf(false) }
    val canPrestige = ProgressionEngine.canPrestige(profile) && !expeditionLocked && !battleActive
    val currentNumeral = ProgressionEngine.prestigeRomanNumeral(profile.prestigeLevel.toInt())
    val nextNumeral = ProgressionEngine.prestigeRomanNumeral((profile.prestigeLevel + 1L).toInt())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quests_prestige_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Prestige Rebirth (Solar Ascension) Card
        SolstrideCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            strongBorder = true
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Prestige Solar Ascension",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (currentNumeral.isNotEmpty()) {
                                "Solar Ascension • Prestige $currentNumeral"
                            } else {
                                "Prestige Rebirth (Solar Ascension)"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (profile.prestigeLevel > 0L) {
                                "Active Permanent Bonus: +${profile.prestigeLevel * 15}% All Stats • +${profile.prestigeLevel * 10}% XP/Shards • +${profile.prestigeLevel * 3}% Survival"
                            } else {
                                "Unlock at Level ${ProgressionEngine.PRESTIGE_MIN_LEVEL} or after clearing Realm ${ProgressionEngine.PRESTIGE_ALT_REALM_CLEARED}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ascending to Prestige $nextNumeral resets Hero Level to 1 and Realm Chapter to I, while KEEPING all Weapons, Armor, Relics, Loot Chests, Companions, Sol Coins, Sunshards, and Skill Tree ranks — plus permanent +15% All Stats!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showPrestigeConfirm = true },
                enabled = canPrestige,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("trigger_prestige_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (canPrestige) {
                        "Ascend to Prestige $nextNumeral (+15% Permanent Stats)"
                    } else {
                        "Lvl ${profile.level}/${ProgressionEngine.PRESTIGE_MIN_LEVEL} or Realms ${profile.highestRealmCleared}/${ProgressionEngine.PRESTIGE_ALT_REALM_CLEARED}"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        // Daily, Weekly, and Lifetime Milestone Quests
        QuestCategory.entries.forEach { category ->
            val group = questStatuses.filter { it.spec.category == category }
            if (group.isNotEmpty()) {
                SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "${category.label} quests",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${category.label} Quests",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        group.forEach { status ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = if (status.canClaim) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (status.canClaim) 1.5.dp else 1.dp,
                                    color = if (status.canClaim) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = status.spec.title,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = status.spec.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (status.isClaimed) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = "Quest claimed",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Claimed",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onClaimQuest(status) },
                                                enabled = status.canClaim,
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .testTag("claim_quest_${status.spec.id}"),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text(if (status.canClaim) "Claim" else "${status.currentProgress.coerceAtMost(status.spec.targetCount)}/${status.spec.targetCount}")
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { status.progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp),
                                        color = if (status.isCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val rewardText = buildString {
                                        append("+${status.spec.sunshardReward} Shards • +${status.spec.xpReward} XP")
                                        status.spec.chestRewardTier?.let { tier ->
                                            append(" • ${tier.title} (${status.spec.chestUltraRarePct}% Ultra Rare)")
                                        }
                                    }
                                    Text(
                                        text = rewardText,
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
        }
    }

    if (showPrestigeConfirm) {
        AlertDialog(
            onDismissRequest = { showPrestigeConfirm = false },
            title = { Text("Confirm Solar Ascension ($nextNumeral)?") },
            text = {
                Text(
                    "Your Hero Level resets to 1 and Realm resets to Realm I, awarding +${2 + profile.prestigeLevel + 1} starting Mastery Points and a permanent +15% All Stats, +10% XP/Sunshards, and +3% Expedition Survival bonus.\n\nYou KEEP all Weapons, Armor, Relics, unopened Loot Chests, Pets, Sol Coins, Sunshards, and Skill Tree ranks."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrestigeConfirm = false
                        onTriggerPrestige()
                    },
                    modifier = Modifier.testTag("confirm_prestige_dialog_button")
                ) {
                    Text("Ascend Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPrestigeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
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
import com.example.domain.ClassSkillCatalog
import com.example.domain.ClassSkillKind
import com.example.domain.ProgressionEngine
import com.example.domain.QuestCategory
import com.example.domain.QuestStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

@Composable
fun SkillTreePanel(
    profile: HeroProfile,
    onVisitTownSkillTrainer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val heroClass = profile.resolvedClass()
    val classSkills = remember(heroClass) { ClassSkillCatalog.skillsFor(heroClass) }
    val activeSkills = remember(classSkills) { classSkills.filter { it.kind == ClassSkillKind.ACTIVE } }
    val passiveSkills = remember(classSkills) { classSkills.filter { it.kind == ClassSkillKind.PASSIVE } }

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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${heroClass.displayTitle} Skill Mastery",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Skills unlock automatically as your Hero levels up (Lv. 1–20). Visit Master Kaelen in Sunhaven Town to buy skill rank upgrades!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    SolCoinBadge(solCoins = profile.solCoins)
                }
                Button(
                    onClick = onVisitTownSkillTrainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("visit_town_skill_trainer_button")
                ) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Train & Buy Skill Upgrades in Town")
                }
            }
        }

        // Section 1: Active Combat Skills (Lv. 1, 5, 12)
        SolstrideCard(modifier = Modifier.fillMaxWidth(), strongBorder = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Active class skills",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Active Combat Arts (3)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Cast directly in tactical battles • Unlocked at Lv. 1, Lv. 5 & Lv. 12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeSkills.forEach { skill ->
                    val unlocked = skill.isUnlockedAt(profile.level.toInt())
                    val rank = ClassSkillCatalog.effectiveRank(profile, skill)
                    val isMaxed = rank >= skill.maxRank

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (unlocked && rank > 0) 1.5.dp else 1.dp,
                            color = if (unlocked && rank > 0) MaterialTheme.colorScheme.primary else ext.borderSubtle
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
                                    Text(text = skill.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "R$rank/${skill.maxRank}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${skill.valorCost} Valor${if (skill.cooldownTurns > 0) " • ${skill.cooldownTurns}T CD" else " • 0T CD"} • Lv.${skill.unlockLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = skill.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = skill.perRankSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    !unlocked -> MaterialTheme.colorScheme.surface
                                    isMaxed -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                border = BorderStroke(1.dp, ext.borderSubtle)
                            ) {
                                Text(
                                    text = when {
                                        !unlocked -> "Lv.${skill.unlockLevel}"
                                        isMaxed -> "MAX"
                                        else -> "R$rank/${skill.maxRank}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Class Passives (Lv. 2, 4, 7, 10, 15, 20)
        SolstrideCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = "Passive class skills",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Class Passive Mastery (6)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Permanent stat, combat & expedition upgrades unlocked at Lv. 2–20",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                passiveSkills.forEach { skill ->
                    val unlocked = skill.isUnlockedAt(profile.level.toInt())
                    val rank = ClassSkillCatalog.effectiveRank(profile, skill)
                    val isMaxed = rank >= skill.maxRank

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
                                    Text(text = skill.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "R$rank/${skill.maxRank}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = skill.perRankSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    !unlocked -> MaterialTheme.colorScheme.surface
                                    isMaxed -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                border = BorderStroke(1.dp, ext.borderSubtle)
                            ) {
                                Text(
                                    text = when {
                                        !unlocked -> "Lv.${skill.unlockLevel}"
                                        isMaxed -> "MAX"
                                        else -> "R$rank/${skill.maxRank}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
    val prestigeEval = remember(profile) { ProgressionEngine.evaluatePrestigeRequirements(profile) }
    val canPrestige = prestigeEval.allConditionsMet && !expeditionLocked && !battleActive
    val currentNumeral = ProgressionEngine.prestigeRomanNumeral(profile.prestigeLevel.toInt())
    val nextNumeral = ProgressionEngine.prestigeRomanNumeral(prestigeEval.nextPrestigeLevel.toInt())

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
                                "Meet all ${prestigeEval.totalCount} Trials of Ascension to unlock Prestige $nextNumeral"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ascending to Prestige $nextNumeral resets Hero Level to 1 and Realm Chapter to I, while KEEPING all Weapons, Armor, Relics, Loot Chests, Companions, Sunshards, remaining Sol Coins, and Skill Tree ranks — plus permanent +15% All Stats!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Trials of Solar Ascension Condition Checklist
            if (!prestigeEval.isMaxPrestigeReached) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prestige_conditions_checklist"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = if (prestigeEval.allConditionsMet) 1.5.dp else 1.dp,
                        color = if (prestigeEval.allConditionsMet) MaterialTheme.colorScheme.primary else ext.borderSubtle
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRIALS FOR PRESTIGE $nextNumeral",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${prestigeEval.metCount}/${prestigeEval.totalCount} MET",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (prestigeEval.allConditionsMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        prestigeEval.conditions.forEach { cond ->
                            val curFormatted = String.format(java.util.Locale.US, "%,d", cond.currentValue)
                            val targetFormatted = String.format(java.util.Locale.US, "%,d", cond.targetValue)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prestige_condition_${cond.id}"),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                            imageVector = if (cond.isMet) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                            contentDescription = if (cond.isMet) "Condition met" else "Condition locked",
                                            tint = if (cond.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = cond.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cond.isMet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = cond.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$curFormatted/$targetFormatted",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cond.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { cond.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp),
                                    color = if (cond.isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }

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
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        prestigeEval.isMaxPrestigeReached -> "Max Prestige Reached"
                        canPrestige -> "Ascend to Prestige $nextNumeral (-${prestigeEval.solCoinTributeCost} Coins)"
                        prestigeEval.allConditionsMet && (expeditionLocked || battleActive) -> "Finish Battle / Expedition First"
                        else -> "Complete Trials (${prestigeEval.metCount}/${prestigeEval.totalCount}) to Ascend"
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
                                        if (status.spec.solCoinReward > 0L) {
                                            append("+${status.spec.solCoinReward} Sol Coins • ")
                                        }
                                        append("+${status.spec.sunshardReward} Shards • +${status.spec.xpReward} XP")
                                        status.spec.chestRewardTier?.let { tier ->
                                            append(" • ${tier.title} (${status.spec.chestUltraRarePct}% Ultra Rare)")
                                        }
                                    }
                                    Text(
                                        text = rewardText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    status.spec.companionReward?.let { companion ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "🐾 Companion Reward: ${companion.displayName} (${companion.rarity.label})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.tertiary
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

    if (showPrestigeConfirm) {
        AlertDialog(
            onDismissRequest = { showPrestigeConfirm = false },
            title = { Text("Confirm Solar Ascension ($nextNumeral)?") },
            text = {
                Text(
                    "All ${prestigeEval.totalCount} Ascension Trials are complete!\n\nAscending offers ${prestigeEval.solCoinTributeCost} Sol Coins, resets Hero Level to 1 and Realm to Realm I, and grants +${2 + prestigeEval.nextPrestigeLevel} starting Mastery Points plus a permanent +15% All Stats, +10% XP/Sunshards, and +3% Expedition Survival bonus.\n\nYou KEEP all Weapons, Armor, Relics, unopened Loot Chests, Pets, Sunshards, remaining Sol Coins, and Skill Tree ranks."
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

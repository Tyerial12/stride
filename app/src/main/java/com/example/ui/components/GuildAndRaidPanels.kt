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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CoopRaidRoom
import com.example.data.model.GuildChatMessage
import com.example.data.model.HeroProfile
import com.example.data.model.SeasonalLeagueTier
import com.example.data.model.SolstrideGuild
import com.example.data.model.WeeklyWorldBoss
import com.example.data.model.WeeklyWorldBossCatalog
import com.example.data.model.WorldBossContribution

/**
 * Seasonal Leaderboard League Banner displayed atop Ranks tab.
 */
@Composable
fun SeasonalLeagueBanner(
    profile: HeroProfile,
    modifier: Modifier = Modifier
) {
    val score = SeasonalLeagueTier.computeLeagueScore(
        weeklySteps = profile.weeklySteps,
        highestSpireFloor = profile.highestSpireFloor,
        bossesDefeated = profile.bossesDefeated
    )
    val tier = SeasonalLeagueTier.forScore(score)
    val nextTier = SeasonalLeagueTier.entries.firstOrNull { it.minScore > score }
    val accent = Color(tier.accentHex)
    val progress = if (nextTier != null) {
        ((score - tier.minScore).toFloat() / (nextTier.minScore - tier.minScore).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    } else 1f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.3.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "Seasonal League", tint = accent)
                    Column {
                        Text(
                            text = "LEAGUE: ${tier.title.uppercase()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                        Text(
                            text = "League Score: ${"%,d".format(score)} (Weekly Steps + Spire + Bosses)",
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
                    .height(7.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = accent
            )
            Text(
                text = nextTier?.let { "Next Tier: ${it.title} at ${"%,d".format(it.minScore)} pts" }
                    ?: "Maximum Sunforged Legend League Reached!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Shared Live Weekly World Boss Card (Firestore `/world_bosses/{weekKey}`).
 */
@Composable
fun WeeklyWorldBossCard(
    profile: HeroProfile,
    worldBoss: WeeklyWorldBoss?,
    contributions: List<WorldBossContribution>,
    onStrikeWorldBoss: () -> Unit,
    onClaimWorldBossMilestone: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val myContrib = contributions.firstOrNull { it.userId == profile.userId }
    val myDamage = myContrib?.totalDamage ?: 0L
    val claimedTier = (myContrib?.claimedMilestoneTier ?: 0L).toInt()
    val nextMilestoneIdx = WeeklyWorldBossCatalog.milestoneThresholds.indexOfFirst { (dmgReq, _) ->
        myDamage >= dmgReq && (WeeklyWorldBossCatalog.milestoneThresholds.indexOfFirst { it.first == dmgReq } + 1) > claimedTier
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = "Weekly World Boss", tint = Color(0xFFEF4444))
                    Column {
                        Text(
                            text = "WEEKLY WORLD BOSS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEF4444)
                        )
                        Text(
                            text = worldBoss?.bossName ?: "Awakening World Boss...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                if (worldBoss != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = worldBoss.resolvedAffinity().label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (worldBoss != null) {
                val hpRatio = (worldBoss.currentHp.toFloat() / worldBoss.maxHp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { hpRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFFEF4444)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HP: ${"%,d".format(worldBoss.currentHp)} / ${"%,d".format(worldBoss.maxHp)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${worldBoss.totalStrikes} Global Strikes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Your Damage: ${"%,d".format(myDamage)} DMG • Milestones Claimed: $claimedTier / ${WeeklyWorldBossCatalog.milestoneThresholds.size}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStrikeWorldBoss,
                    enabled = (worldBoss?.currentHp ?: 0L) > 0L && profile.strideEnergy >= 15L,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("world_boss_strike_button")
                ) {
                    Text(
                        text = if ((worldBoss?.currentHp ?: 1L) <= 0L) "SLAIN THIS WEEK!" else "Strike Boss (15 EN)",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (nextMilestoneIdx >= 0) {
                    Button(
                        onClick = { onClaimWorldBossMilestone(nextMilestoneIdx + 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("world_boss_claim_milestone_button")
                    ) {
                        Text("Claim Tier ${nextMilestoneIdx + 1}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (contributions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TOP WORLD BOSS CONTRIBUTORS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    contributions.take(4).forEachIndexed { idx, c ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${idx + 1}. ${c.displayName}", style = MaterialTheme.typography.bodySmall)
                            Text("${"%,d".format(c.totalDamage)} DMG", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Live 2-4 Player Co-op Boss Raid Lobby & Active Raid Battle Panel.
 */
@Composable
fun CoopRaidLobbyPanel(
    profile: HeroProfile,
    raids: List<CoopRaidRoom>,
    onCreateRaid: () -> Unit,
    onJoinRaid: (String) -> Unit,
    onStrikeRaid: (String, Boolean) -> Unit,
    onClaimRaidReward: (String) -> Unit,
    onCloseRaid: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, Color(0xFFEA580C).copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CO-OP BOSS RAIDS (UP TO 4 PLAYERS)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Team up with real Striders to topple Raid Titans for Sol Coins & Evolution Shards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = onCreateRaid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_coop_raid_button")
                ) {
                    Text("Open New Co-op Raid Room", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (raids.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No open Co-op Raid rooms right now. Open a Raid Room above so nearby Striders can join your squad!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            raids.forEach { raid ->
                val isMember = profile.userId in raid.participantUids
                val isHost = raid.hostUid == profile.userId
                val hasClaimed = profile.userId in raid.claimedUids
                val hpRatio = (raid.bossCurrentHp.toFloat() / raid.bossMaxHp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (raid.status == "VICTORY") Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(raid.bossName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Squad (${raid.participantUids.size}/4): ${raid.participantNames.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (raid.status == "VICTORY") Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFEA580C).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    raid.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { hpRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (raid.status == "VICTORY") Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                        Text(
                            text = "Boss HP: ${"%,d".format(raid.bossCurrentHp)} / ${"%,d".format(raid.bossMaxHp)} • ${raid.lastActionLog}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when {
                                raid.status == "VICTORY" && isMember && !hasClaimed -> {
                                    Button(
                                        onClick = { onClaimRaidReward(raid.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("raid_claim_reward_${raid.id}")
                                    ) {
                                        Text("Claim Raid Spoils (+20 Coins, +1 Shard)", fontWeight = FontWeight.Bold)
                                    }
                                }
                                !isMember && raid.status != "VICTORY" && raid.participantUids.size < 4 -> {
                                    Button(
                                        onClick = { onJoinRaid(raid.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("raid_join_${raid.id}")
                                    ) {
                                        Text("Join Raid Squad", fontWeight = FontWeight.Bold)
                                    }
                                }
                                isMember && raid.status != "VICTORY" -> {
                                    Button(
                                        onClick = { onStrikeRaid(raid.id, false) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("raid_strike_${raid.id}")
                                    ) {
                                        Text("Strike", fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { onStrikeRaid(raid.id, true) },
                                        enabled = profile.strideEnergy >= 15L,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(48.dp)
                                            .testTag("raid_surge_${raid.id}")
                                    ) {
                                        Text("Solar Surge (15 EN)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (isHost && (raid.status == "VICTORY" || raid.participantUids.size <= 1)) {
                                OutlinedButton(
                                    onClick = { onCloseRaid(raid.id) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("raid_close_${raid.id}")
                                ) {
                                    Text("Close")
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
 * Real-Player Step-Powered Guild Hall Panel.
 * Founding a Guild requires a Guild Charter Crest bought at Zari's Market in Sunhaven Town (Town-only rule!).
 */
@Composable
fun StepPoweredGuildPanel(
    profile: HeroProfile,
    guilds: List<SolstrideGuild>,
    myGuild: SolstrideGuild?,
    guildMessages: List<GuildChatMessage>,
    onCreateGuild: (name: String, motto: String) -> Unit,
    onJoinGuild: (String) -> Unit,
    onLeaveGuild: () -> Unit,
    onSendGuildMessage: (String) -> Unit,
    onVisitTownMarketForCrest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newGuildName by remember { mutableStateOf("") }
    var newGuildMotto by remember { mutableStateOf("Every step lights the dawn.") }
    var chatDraft by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (myGuild != null) {
            val stepProgress = (myGuild.weeklyGuildSteps.toFloat() / myGuild.weeklyStepGoal.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = "Guild Emblem", tint = Color(0xFFF59E0B))
                            Column {
                                Text(myGuild.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Lv.${myGuild.guildLevel} • ${myGuild.memberUids.size}/25 Striders • \"${myGuild.motto}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onLeaveGuild,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("leave_guild_button")
                        ) {
                            Text("Leave")
                        }
                    }

                    // Shared Weekly Step Milestone Progress
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.DirectionsWalk, contentDescription = "Guild Steps", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Text(
                            text = "Weekly Guild Steps: ${"%,d".format(myGuild.weeklyGuildSteps)} / ${"%,d".format(myGuild.weeklyStepGoal)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { stepProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "Members: ${myGuild.memberNames.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Member-Only Guild Chat Feed
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GUILD HALL CHAT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                    if (guildMessages.isEmpty()) {
                        Text(
                            "Say hello to your guildmates! Every real-world step you walk boosts the guild goal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        guildMessages.takeLast(8).forEach { msg ->
                            Text(
                                text = "${msg.displayName} (Lv.${msg.heroLevel}): ${msg.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chatDraft,
                            onValueChange = { chatDraft = it.take(200) },
                            placeholder = { Text("Message guild...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("guild_chat_input")
                        )
                        IconButton(
                            onClick = {
                                if (chatDraft.isNotBlank()) {
                                    onSendGuildMessage(chatDraft)
                                    chatDraft = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("guild_chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Guild Message")
                        }
                    }
                }
            }
        } else {
            // Create a Guild Card (Town-only Guild Charter Crest requirement!)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Groups, contentDescription = "Found Guild", tint = Color(0xFFD97706))
                        Column {
                            Text("FOUND A STEP-POWERED GUILD", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Charter Crests Owned: ${profile.guildCrestsOwned}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (profile.guildCrestsOwned > 0L) {
                        OutlinedTextField(
                            value = newGuildName,
                            onValueChange = { newGuildName = it.take(32) },
                            label = { Text("Guild Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_guild_name_input")
                        )
                        OutlinedTextField(
                            value = newGuildMotto,
                            onValueChange = { newGuildMotto = it.take(80) },
                            label = { Text("Guild Motto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (newGuildName.trim().length >= 2) {
                                    onCreateGuild(newGuildName.trim(), newGuildMotto.trim())
                                    newGuildName = ""
                                }
                            },
                            enabled = newGuildName.trim().length >= 2,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_guild_submit_button")
                        ) {
                            Text("Found Guild (Uses 1 Charter Crest)", fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Text(
                            "To found a new Guild, purchase a Guild Charter Crest from Zari's Market in Sunhaven Town — or join an existing Strider Guild below for free!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onVisitTownMarketForCrest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("guild_visit_town_market_button")
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = "Zari's Market", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buy Guild Charter Crest at Zari's Market (Town)")
                        }
                    }
                }
            }

            // Directory of Real-Player Guilds
            Text("ACTIVE STRIDER GUILDS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            if (guilds.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No public guilds founded yet. Be the first Strider to buy a Charter Crest in Sunhaven Town and found one!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                guilds.forEach { guild ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(guild.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Lv.${guild.guildLevel} • ${guild.memberUids.size}/25 • ${"%,d".format(guild.weeklyGuildSteps)} weekly steps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onJoinGuild(guild.id) },
                                enabled = guild.memberUids.size < 25,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("join_guild_${guild.id}")
                            ) {
                                Text("Join")
                            }
                        }
                    }
                }
            }
        }
    }
}

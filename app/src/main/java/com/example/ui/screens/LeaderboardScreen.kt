package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HeroProfile
import com.example.data.model.LeaderboardEntry
import com.example.data.model.UiState
import com.example.data.model.currentIsoWeekKey
import com.example.ui.components.SolstrideCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import java.util.Locale

private enum class LeaderboardMode(val label: String) {
    WEEKLY_STEPS("Weekly Steps"),
    HERO_POWER("Hero Power")
}

@Composable
fun LeaderboardScreen(
    currentUserId: String,
    profile: HeroProfile,
    leaderboardState: UiState<List<LeaderboardEntry>>,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(LeaderboardMode.WEEKLY_STEPS) }
    val weekKey = remember { currentIsoWeekKey() }
    val ext = LocalSolstrideColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cloud Leaderboard",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = if (mode == LeaderboardMode.WEEKLY_STEPS) {
                        "Live Firestore Rankings • Week $weekKey"
                    } else {
                        "Global Tactical Hero Power & Bosses Slain"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Competitive rankings trophy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LeaderboardMode.entries.forEachIndexed { idx, tab ->
                SegmentedButton(
                    selected = mode == tab,
                    onClick = { mode = tab },
                    shape = SegmentedButtonDefaults.itemShape(idx, LeaderboardMode.entries.size),
                    modifier = Modifier.testTag("leaderboard_tab_${tab.name.lowercase()}"),
                    icon = {
                        Icon(
                            imageVector = if (tab == LeaderboardMode.WEEKLY_STEPS) {
                                Icons.AutoMirrored.Filled.DirectionsWalk
                            } else {
                                Icons.Filled.Shield
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text(tab.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (leaderboardState) {
            is UiState.Loading -> {
                SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text("Syncing live Cloud Leaderboard…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is UiState.Error -> {
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    strongBorder = true
                ) {
                    Text(
                        text = "Leaderboard Unavailable Offline",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = leaderboardState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is UiState.Success -> {
                val ranked = remember(leaderboardState.data, mode, weekKey) {
                    if (mode == LeaderboardMode.WEEKLY_STEPS) {
                        leaderboardState.data
                            .map { if (it.weekKey == weekKey) it else it.copy(weeklySteps = 0L) }
                            .sortedWith(compareByDescending<LeaderboardEntry> { it.weeklySteps }.thenByDescending { it.heroPower })
                    } else {
                        leaderboardState.data
                            .sortedWith(compareByDescending<LeaderboardEntry> { it.heroPower }.thenByDescending { it.heroLevel })
                    }
                }

                val myIndex = ranked.indexOfFirst { it.userId == currentUserId }
                val myRankLabel = if (myIndex >= 0) "#${myIndex + 1}" else "Unranked"

                // Pinned Your Standing Banner
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
                        Column {
                            Text(
                                text = "YOUR STANDING ($myRankLabel)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${profile.displayName} • LVL ${profile.level}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = if (mode == LeaderboardMode.WEEKLY_STEPS) {
                                "${String.format(Locale.US, "%,d", profile.weeklySteps)} steps"
                            } else {
                                "${profile.heroPower} PWR"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ranked.isEmpty()) {
                        item {
                            SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "No contenders ranked yet. Walk and sync to claim the #1 Sunlit Crest!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        itemsIndexed(ranked, key = { _, e -> e.userId }) { index, entry ->
                            val rank = index + 1
                            val isCurrentUser = entry.userId == currentUserId
                            val crestTint = when (rank) {
                                1 -> Color(0xFFD98A00)
                                2 -> Color(0xFF64748B)
                                3 -> Color(0xFF9A5B25)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = if (isCurrentUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (rank <= 3 || isCurrentUser) 2.dp else 1.5.dp,
                                    color = if (isCurrentUser) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = crestTint.copy(alpha = 0.16f),
                                        border = BorderStroke(1.5.dp, crestTint),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (rank <= 3) {
                                                Icon(
                                                    imageVector = Icons.Filled.MilitaryTech,
                                                    contentDescription = "Rank $rank medal",
                                                    tint = crestTint,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "$rank",
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = crestTint
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        val prestigeTag = com.example.domain.ProgressionEngine.prestigeRomanNumeral(entry.prestigeLevel.toInt())
                                        val titleName = buildString {
                                            append(if (isCurrentUser) "${entry.displayName} (You)" else entry.displayName)
                                            if (prestigeTag.isNotEmpty()) append(" [P$prestigeTag]")
                                        }
                                        Text(
                                            text = titleName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1
                                        )
                                        val spireSuffix = if (entry.highestSpireFloor > 0L) " • Spire F${entry.highestSpireFloor}" else ""
                                        Text(
                                            text = "LVL ${entry.heroLevel} ${entry.resolvedClass().displayTitle} • ${entry.bossesDefeated} Wins$spireSuffix",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (mode == LeaderboardMode.WEEKLY_STEPS) {
                                                String.format(Locale.US, "%,d", entry.weeklySteps)
                                            } else {
                                                "${entry.heroPower}"
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (mode == LeaderboardMode.WEEKLY_STEPS) "STEPS" else "POWER",
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
        }
    }
}

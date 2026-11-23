package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Whatshot
import com.example.data.model.CoopRaidRoom
import com.example.data.model.DuelActionKind
import com.example.data.model.GuildChatMessage
import com.example.data.model.HeroProfile
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MultiplayerChatMessage
import com.example.data.model.MultiplayerDuel
import com.example.data.model.MultiplayerPetPlaydate
import com.example.data.model.MultiplayerSubTab
import com.example.data.model.OwnedPet
import com.example.data.model.PetPlayActivity
import com.example.data.model.SolstrideGuild
import com.example.data.model.TownPresencePlayer
import com.example.data.model.UiState
import com.example.domain.ProgressionEngine
import com.example.ui.components.ChatSenderMiniAvatar
import com.example.ui.components.CoopRaidLobbyPanel
import com.example.ui.components.MultiplayerDuelArenaCanvas
import com.example.ui.components.MultiplayerHeroPetPreviewCanvas
import com.example.ui.components.PetPlaydateParkCanvas
import com.example.ui.components.SeasonalLeagueBanner
import com.example.ui.components.SolstrideCard
import com.example.ui.components.StepPoweredGuildPanel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiplayerScreen(
    currentUserId: String,
    profile: HeroProfile,
    equippedPet: OwnedPet?,
    selectedSubTab: MultiplayerSubTab,
    townPlayers: List<TownPresencePlayer>,
    chatMessages: List<MultiplayerChatMessage>,
    duels: List<MultiplayerDuel>,
    petPlaydates: List<MultiplayerPetPlaydate>,
    onSelectSubTab: (MultiplayerSubTab) -> Unit,
    onSendChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onChallengeDuel: (TownPresencePlayer) -> Unit,
    onAcceptDuel: (MultiplayerDuel) -> Unit,
    onDeclineOrCancelDuel: (MultiplayerDuel) -> Unit,
    onPerformDuelAction: (MultiplayerDuel, DuelActionKind) -> Unit,
    onForfeitDuel: (MultiplayerDuel) -> Unit,
    onDismissDuel: (MultiplayerDuel) -> Unit,
    onInvitePetPlaydate: (TownPresencePlayer) -> Unit,
    onAcceptPetPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onDeclineOrCancelPetPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onTriggerPetPlayActivity: (MultiplayerPetPlaydate, PetPlayActivity) -> Unit,
    onFinishPetPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onDismissPetPlaydate: (MultiplayerPetPlaydate) -> Unit,
    leaderboardState: UiState<List<LeaderboardEntry>> = UiState.Loading,
    coopRaids: List<CoopRaidRoom> = emptyList(),
    guilds: List<SolstrideGuild> = emptyList(),
    myGuild: SolstrideGuild? = null,
    guildChatMessages: List<GuildChatMessage> = emptyList(),
    onCreateRaid: () -> Unit = {},
    onJoinRaid: (String) -> Unit = {},
    onStrikeRaid: (String, Boolean) -> Unit = { _, _ -> },
    onClaimRaidReward: (String) -> Unit = {},
    onCloseRaid: (String) -> Unit = {},
    onCreateGuild: (String, String) -> Unit = { _, _ -> },
    onJoinGuild: (String) -> Unit = {},
    onLeaveGuild: () -> Unit = {},
    onSendGuildMessage: (String) -> Unit = {},
    onVisitTownMarketForCrest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val otherPlayers = townPlayers.filter {
        it.inTown && it.userId.isNotBlank() && it.userId != currentUserId && it.id != currentUserId
    }
    val activeDuelCount = duels.count { it.status == "ACTIVE" || (it.status == "PENDING" && it.opponentUid == currentUserId) }
    val activePlayCount = petPlaydates.count { it.status == "PLAYING" || (it.status == "PENDING" && it.guestUid == currentUserId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("multiplayer_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header strip with real-time online count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SOCIAL & MULTIPLAYER",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Real players live • Chat, Hero Duels, Pet Park & Ranks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = "Online players",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${1 + otherPlayers.size} Online",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Sub-Tab Selector (Players / Chat / Duels / Pet Park)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MultiplayerSubTab.entries.forEach { tab ->
                val badge = when (tab) {
                    MultiplayerSubTab.PLAYERS -> if (otherPlayers.isNotEmpty()) " (${otherPlayers.size})" else ""
                    MultiplayerSubTab.CHAT -> ""
                    MultiplayerSubTab.DUELS -> if (activeDuelCount > 0) " ($activeDuelCount)" else ""
                    MultiplayerSubTab.PET_PLAY -> if (activePlayCount > 0) " ($activePlayCount)" else ""
                    MultiplayerSubTab.GUILD -> if (myGuild != null) " ★" else ""
                    MultiplayerSubTab.RAIDS -> if (coopRaids.isNotEmpty()) " (${coopRaids.size})" else ""
                    MultiplayerSubTab.RANKS -> ""
                }
                val icon = when (tab) {
                    MultiplayerSubTab.PLAYERS -> Icons.Filled.Groups
                    MultiplayerSubTab.CHAT -> Icons.AutoMirrored.Filled.Chat
                    MultiplayerSubTab.DUELS -> Icons.Filled.SportsMartialArts
                    MultiplayerSubTab.PET_PLAY -> Icons.Filled.Pets
                    MultiplayerSubTab.GUILD -> Icons.Filled.Shield
                    MultiplayerSubTab.RAIDS -> Icons.Filled.Whatshot
                    MultiplayerSubTab.RANKS -> Icons.Filled.EmojiEvents
                }
                FilterChip(
                    selected = selectedSubTab == tab,
                    onClick = { onSelectSubTab(tab) },
                    label = {
                        Text(
                            text = "${tab.label}$badge",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("mp_subtab_${tab.name.lowercase()}")
                )
            }
        }

        // Active Sub-Tab Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedSubTab) {
                MultiplayerSubTab.PLAYERS -> MultiplayerPlayersRoster(
                    profile = profile,
                    equippedPet = equippedPet,
                    otherPlayers = otherPlayers,
                    onChallengeDuel = onChallengeDuel,
                    onInvitePetPlaydate = onInvitePetPlaydate
                )

                MultiplayerSubTab.CHAT -> MultiplayerGlobalChatPane(
                    currentUserId = currentUserId,
                    messages = chatMessages,
                    onSendChat = onSendChat,
                    onDeleteChat = onDeleteChat
                )

                MultiplayerSubTab.DUELS -> MultiplayerDuelsPane(
                    currentUserId = currentUserId,
                    duels = duels,
                    onAcceptDuel = onAcceptDuel,
                    onDeclineOrCancelDuel = onDeclineOrCancelDuel,
                    onPerformDuelAction = onPerformDuelAction,
                    onForfeitDuel = onForfeitDuel,
                    onDismissDuel = onDismissDuel,
                    onBrowsePlayers = { onSelectSubTab(MultiplayerSubTab.PLAYERS) }
                )

                MultiplayerSubTab.PET_PLAY -> MultiplayerPetParkPane(
                    currentUserId = currentUserId,
                    equippedPet = equippedPet,
                    playdates = petPlaydates,
                    onAcceptPlaydate = onAcceptPetPlaydate,
                    onDeclineOrCancelPlaydate = onDeclineOrCancelPetPlaydate,
                    onTriggerActivity = onTriggerPetPlayActivity,
                    onFinishPlaydate = onFinishPetPlaydate,
                    onDismissPlaydate = onDismissPetPlaydate,
                    onBrowsePlayers = { onSelectSubTab(MultiplayerSubTab.PLAYERS) }
                )

                MultiplayerSubTab.GUILD -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item {
                        StepPoweredGuildPanel(
                            profile = profile,
                            guilds = guilds,
                            myGuild = myGuild,
                            guildMessages = guildChatMessages,
                            onCreateGuild = onCreateGuild,
                            onJoinGuild = onJoinGuild,
                            onLeaveGuild = onLeaveGuild,
                            onSendGuildMessage = onSendGuildMessage,
                            onVisitTownMarketForCrest = onVisitTownMarketForCrest
                        )
                    }
                }

                MultiplayerSubTab.RAIDS -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item {
                        CoopRaidLobbyPanel(
                            profile = profile,
                            raids = coopRaids,
                            onCreateRaid = onCreateRaid,
                            onJoinRaid = onJoinRaid,
                            onStrikeRaid = onStrikeRaid,
                            onClaimRaidReward = onClaimRaidReward,
                            onCloseRaid = onCloseRaid
                        )
                    }
                }

                MultiplayerSubTab.RANKS -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SeasonalLeagueBanner(profile = profile)
                    LeaderboardScreen(
                        currentUserId = currentUserId,
                        profile = profile,
                        leaderboardState = leaderboardState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiplayerPlayersRoster(
    profile: HeroProfile,
    equippedPet: OwnedPet?,
    otherPlayers: List<TownPresencePlayer>,
    onChallengeDuel: (TownPresencePlayer) -> Unit,
    onInvitePetPlaydate: (TownPresencePlayer) -> Unit
) {
    val ext = LocalSolstrideColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        // Your Own Live Card (Hero + Equipped Pet)
        item(key = "local_player_card") {
            SolstrideCard(strongBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiplayerHeroPetPreviewCanvas(
                        heroClass = profile.resolvedClass(),
                        avatar = profile.resolvedAvatar(),
                        prestigeLevel = profile.prestigeLevel.toInt(),
                        petType = equippedPet?.resolvedType(),
                        modifier = Modifier
                            .width(116.dp)
                            .height(92.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "YOU • ${profile.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Lv.${profile.level} ${profile.resolvedClass().title} • Power ${profile.heroPower}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val petSummary = if (equippedPet != null) {
                            "🐾 ${equippedPet.resolvedType().displayName} (Lv.${equippedPet.level})"
                        } else {
                            "No Companion Pet equipped yet"
                        }
                        Text(
                            text = petSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (otherPlayers.isEmpty()) {
            item(key = "no_other_players") {
                SolstrideCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Waiting for Other Real Players",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Only real signed-in Striders appear here. When another player opens Solstride, you can inspect their Character & Pet, challenge them to a Solar Duel, or invite their pet to play in the Pet Park!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(otherPlayers, key = { it.userId.ifBlank { it.id } }) { player ->
                val playerPet = player.resolvedPetType()
                val canPetPlay = equippedPet != null && playerPet != null
                SolstrideCard(
                    modifier = Modifier.testTag("mp_player_card_${player.userId}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MultiplayerHeroPetPreviewCanvas(
                                heroClass = player.resolvedClass(),
                                avatar = player.resolvedAvatar(),
                                prestigeLevel = player.prestigeLevel.toInt(),
                                petType = playerPet,
                                modifier = Modifier
                                    .width(124.dp)
                                    .height(98.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = player.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val prestigeTag = if (player.prestigeLevel > 0L) {
                                    " • Asc.${ProgressionEngine.prestigeRomanNumeral(player.prestigeLevel.toInt())}"
                                } else ""
                                Text(
                                    text = "Lv.${player.heroLevel} ${player.resolvedClass().title}$prestigeTag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Power: ${player.heroPower}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (playerPet != null) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    border = BorderStroke(1.dp, ext.borderSubtle)
                                ) {
                                    Text(
                                        text = if (playerPet != null) {
                                            "🐾 ${playerPet.displayName} • Lv.${player.equippedPetLevel}"
                                        } else {
                                            "No Pet Equipped"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (playerPet != null) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (player.emote.isNotBlank()) {
                                    Text(
                                        text = "\"${player.emote}\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onChallengeDuel(player) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mp_duel_btn_${player.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsMartialArts,
                                    contentDescription = "Challenge to Duel",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Duel", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onInvitePetPlaydate(player) },
                                enabled = canPetPlay,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("mp_petplay_btn_${player.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Pets,
                                    contentDescription = "Invite Pet Playdate",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (canPetPlay) "Pet Play" else "Need Pets",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiplayerGlobalChatPane(
    currentUserId: String,
    messages: List<MultiplayerChatMessage>,
    onSendChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    val ext = LocalSolstrideColors.current
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("mp_chat_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (messages.isEmpty()) {
                item(key = "chat_empty") {
                    SolstrideCard {
                        Text(
                            text = "Sunhaven Global Chat is quiet right now — say hello, invite someone to a Hero Duel, or set up a Pet Playdate!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(messages, key = { it.id.ifBlank { "${it.userId}_${it.message.hashCode()}" } }) { msg ->
                    val isMine = msg.userId == currentUserId
                    val pet = msg.resolvedPetType()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMine) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ext.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ChatSenderMiniAvatar(
                                heroClass = msg.resolvedClass(),
                                avatar = msg.resolvedAvatar()
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = msg.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Lv.${msg.heroLevel} ${msg.resolvedClass().title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (pet != null) {
                                    Text(
                                        text = "🐾 ${pet.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = msg.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isMine && msg.id.isNotBlank()) {
                                IconButton(
                                    onClick = { onDeleteChat(msg.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete my chat message",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick-Chat Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("👋 Hey everyone!", "⚔ Anyone up for a Duel?", "🐾 Pet playdate?").forEach { quick ->
                Surface(
                    onClick = { onSendChat(quick) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ext.borderSubtle)
                ) {
                    Text(
                        text = quick,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Chat Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 240) draft = it },
                placeholder = { Text("Message real players in Sunhaven…") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("mp_chat_input")
            )
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onSendChat(draft)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("mp_chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send chat message",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MultiplayerDuelsPane(
    currentUserId: String,
    duels: List<MultiplayerDuel>,
    onAcceptDuel: (MultiplayerDuel) -> Unit,
    onDeclineOrCancelDuel: (MultiplayerDuel) -> Unit,
    onPerformDuelAction: (MultiplayerDuel, DuelActionKind) -> Unit,
    onForfeitDuel: (MultiplayerDuel) -> Unit,
    onDismissDuel: (MultiplayerDuel) -> Unit,
    onBrowsePlayers: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        if (duels.isEmpty()) {
            item(key = "no_duels") {
                SolstrideCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsMartialArts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Active Hero Duels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Challenge any real online player from the Players tab for a turn-based Solar Duel! Winners earn +5 Sol Coins & +35 XP honor bonus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBrowsePlayers,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Browse Online Players", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(duels, key = { it.id.ifBlank { "${it.challengerUid}_${it.opponentUid}" } }) { duel ->
                val isMyTurn = duel.status == "ACTIVE" && duel.turnUid == currentUserId
                val isChallenger = duel.challengerUid == currentUserId
                val isWinner = duel.status == "COMPLETED" && duel.winnerUid == currentUserId

                SolstrideCard(
                    strongBorder = duel.status == "ACTIVE" || (duel.status == "PENDING" && !isChallenger),
                    modifier = Modifier.testTag("mp_duel_card_${duel.id}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Top Matchup Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${duel.challengerName} vs ${duel.opponentName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    duel.status == "ACTIVE" && isMyTurn -> MaterialTheme.colorScheme.primaryContainer
                                    duel.status == "COMPLETED" && isWinner -> Color(0xFF14532D)
                                    duel.status == "PENDING" -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when {
                                        duel.status == "PENDING" && isChallenger -> "WAITING"
                                        duel.status == "PENDING" -> "CHALLENGE!"
                                        duel.status == "ACTIVE" && isMyTurn -> "YOUR TURN • T${duel.turnNumber}"
                                        duel.status == "ACTIVE" -> "THEIR TURN • T${duel.turnNumber}"
                                        duel.status == "COMPLETED" && isWinner -> "VICTORY"
                                        duel.status == "COMPLETED" -> "DEFEATED"
                                        else -> "DECLINED"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (duel.status == "COMPLETED" && isWinner) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Live 2D Duel Stage Canvas (Both Heroes + Pets)
                        MultiplayerDuelArenaCanvas(duel = duel)

                        // Dual HP Bars
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DuelHpColumn(
                                name = duel.challengerName,
                                subtitle = "Lv.${duel.challengerLevel} ${duel.resolvedChallengerClass().title}",
                                hp = duel.challengerHp,
                                maxHp = duel.challengerMaxHp,
                                guarded = duel.challengerGuard,
                                modifier = Modifier.weight(1f)
                            )
                            DuelHpColumn(
                                name = duel.opponentName,
                                subtitle = "Lv.${duel.opponentLevel} ${duel.resolvedOpponentClass().title}",
                                hp = duel.opponentHp,
                                maxHp = duel.opponentMaxHp,
                                guarded = duel.opponentGuard,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (duel.lastActionLog.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = duel.lastActionLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Action Controls by Status
                        when (duel.status) {
                            "PENDING" -> {
                                if (!isChallenger) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onAcceptDuel(duel) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Accept Duel", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onDeclineOrCancelDuel(duel) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Decline", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onDeclineOrCancelDuel(duel) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("Cancel Pending Challenge")
                                    }
                                }
                            }

                            "ACTIVE" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DuelActionKind.entries.forEach { action ->
                                        Button(
                                            onClick = { onPerformDuelAction(duel, action) },
                                            enabled = isMyTurn,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("duel_action_${action.name.lowercase()}")
                                        ) {
                                            Text(
                                                text = action.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onForfeitDuel(duel) }) {
                                        Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Yield Duel")
                                    }
                                }
                            }

                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.EmojiEvents,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (duel.status == "COMPLETED" && isWinner) {
                                                "Victory! +5 Sol Coins & +35 XP"
                                            } else if (duel.status == "COMPLETED") {
                                                "Good match — train and rematch!"
                                            } else {
                                                "Challenge closed."
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onDismissDuel(duel) },
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text("Dismiss")
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
private fun DuelHpColumn(
    name: String,
    subtitle: String,
    hp: Long,
    maxHp: Long,
    guarded: Boolean,
    modifier: Modifier = Modifier
) {
    val ratio = (hp.toFloat() / maxHp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (guarded) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Guarding",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = if (ratio > 0.35f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text(
            text = "$hp / $maxHp HP",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiplayerPetParkPane(
    currentUserId: String,
    equippedPet: OwnedPet?,
    playdates: List<MultiplayerPetPlaydate>,
    onAcceptPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onDeclineOrCancelPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onTriggerActivity: (MultiplayerPetPlaydate, PetPlayActivity) -> Unit,
    onFinishPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onDismissPlaydate: (MultiplayerPetPlaydate) -> Unit,
    onBrowsePlayers: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        if (playdates.isEmpty()) {
            item(key = "no_playdates") {
                SolstrideCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Sunhaven Pet Park",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (equippedPet != null) {
                                "Your ${equippedPet.resolvedType().displayName} is ready to play! Invite any online player with a companion pet from the Players tab."
                            } else {
                                "Adopt and equip a Companion Pet in the Pets tab so your pet can play with other players' pets here!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBrowsePlayers,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Find Playdate Friends", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(playdates, key = { it.id.ifBlank { "${it.hostUid}_${it.guestUid}" } }) { playdate ->
                val isHost = playdate.hostUid == currentUserId
                val hostPet = playdate.resolvedHostPet()
                val guestPet = playdate.resolvedGuestPet()

                SolstrideCard(
                    strongBorder = playdate.status == "PLAYING" || (playdate.status == "PENDING" && !isHost),
                    modifier = Modifier.testTag("mp_playdate_card_${playdate.id}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hostPet.displayName} & ${guestPet.displayName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${playdate.hostName} (Lv.${playdate.hostPetLevel}) • ${playdate.guestName} (Lv.${playdate.guestPetLevel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (playdate.status == "PLAYING") {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = playdate.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Animated Sunhaven Pet Park Courtyard Canvas
                        PetPlaydateParkCanvas(playdate = playdate)

                        // Happiness & Play Counter
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
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "Pet happiness",
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Pet Joy: +${playdate.happinessBonus}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Play Turns: ${playdate.playCount}/50",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (playdate.happinessBonus / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Color(0xFFEC4899)
                        )

                        if (playdate.lastPlayLog.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = playdate.lastPlayLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        when (playdate.status) {
                            "PENDING" -> {
                                if (!isHost) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onAcceptPlaydate(playdate) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Text("Join Playdate", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onDeclineOrCancelPlaydate(playdate) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Text("Decline", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onDeclineOrCancelPlaydate(playdate) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("Cancel Invite")
                                    }
                                }
                            }

                            "PLAYING" -> {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PetPlayActivity.entries.forEach { act ->
                                        val isActive = playdate.resolvedActivity() == act
                                        Button(
                                            onClick = { onTriggerActivity(playdate, act) },
                                            colors = if (isActive) {
                                                ButtonDefaults.buttonColors()
                                            } else {
                                                ButtonDefaults.filledTonalButtonColors()
                                            },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("pet_play_act_${act.id.lowercase()}")
                                        ) {
                                            Text(
                                                text = act.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onFinishPlaydate(playdate) },
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Text("Finish Playdate")
                                }
                            }

                            else -> {
                                OutlinedButton(
                                    onClick = { onDismissPlaydate(playdate) },
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

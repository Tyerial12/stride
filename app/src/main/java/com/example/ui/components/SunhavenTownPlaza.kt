package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BrewGrade
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroAvatarConfig
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.ItemRarity
import com.example.data.model.MercenaryContractSpec
import com.example.data.model.OwnedPet
import com.example.data.model.PetCatalog
import com.example.data.model.PetEvolutionCatalystOffer
import com.example.data.model.PetFoodSpec
import com.example.data.model.PetGearSpec
import com.example.data.model.PetType
import com.example.data.model.TownBuildingCatalog
import com.example.data.model.TownBuildingId
import com.example.data.model.TownMaterialOffer
import com.example.data.model.TownMerchantCatalog
import com.example.data.model.TownNpcRole
import com.example.data.model.TownPresencePlayer
import com.example.data.model.WorldFogState
import com.example.data.model.currentCalendarDateKey
import com.example.domain.AlchemistRecipeSpec
import com.example.domain.BlackMarketOfferSpec
import com.example.domain.ClassSkillCatalog
import com.example.domain.ClassSkillKind
import com.example.domain.ProgressionEngine
import com.example.domain.QuestStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Interactive 2D walkable multiplayer Sunhaven Plaza featuring strictly real live Firestore players,
 * town guild station menus below the plaza (Quests, Rare Materials, Class Skills, Forge),
 * dynamic Step & Sleep Fog-of-War atmosphere, and quick emotes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunhavenTownPlazaCard(
    profile: HeroProfile,
    worldFog: WorldFogState,
    townPlayers: List<TownPresencePlayer>,
    localPos: Pair<Float, Float>,
    localFacingRight: Boolean,
    localEmote: String,
    unclaimedQuestCount: Int,
    onWalkTo: (Float, Float) -> Unit,
    onSendEmote: (String) -> Unit,
    onOpenNpc: (TownNpcRole) -> Unit,
    onInspectPlayer: (TownPresencePlayer) -> Unit,
    onOpenCharacterMirror: () -> Unit,
    equippedPet: OwnedPet? = null,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val animX by animateFloatAsState(
        targetValue = localPos.first,
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.82f),
        label = "town_hero_x"
    )
    val animY by animateFloatAsState(
        targetValue = localPos.second,
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.82f),
        label = "town_hero_y"
    )

    val infinite = rememberInfiniteTransition(label = "town_plaza_anim")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "town_phase"
    )
    val auraPulse by infinite.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "town_aura"
    )

    val otherPlayers = townPlayers.filter {
        it.inTown && it.userId.isNotBlank() && it.userId != profile.userId && it.id != profile.userId
    }
    val totalInTown = 1 + otherPlayers.size
    val plazaNpcs = listOf(
        TownNpcRole.PET_STORE,
        TownNpcRole.MATERIALS_MERCHANT,
        TownNpcRole.SKILL_MENTOR,
        TownNpcRole.BLACKSMITH,
        TownNpcRole.ALCHEMIST_SPIRE
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sunhaven_town_plaza_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, ext.borderStrong)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Plaza Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SUNHAVEN PLAZA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "$totalInTown Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        text = "Tap to walk • Pet Store, Market, Training Hall, Forge & Alchemist Spire",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onOpenCharacterMirror,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("open_character_mirror_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Face,
                        contentDescription = "Customize character appearance",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mirror", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Walkable 2D Multiplayer Town Canvas (Real live players + Blacksmith, Skill Trainer & Material Vendor NPCs)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .pointerInput(otherPlayers, profile.userId) {
                        detectTapGestures { tapOffset ->
                            val nx = (tapOffset.x / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                            val ny = (tapOffset.y / size.height.toFloat()).coerceIn(0.24f, 0.88f)

                            // Check if user tapped one of the 3 Town NPCs (Blacksmith, Skill Trainer, Material Vendor)
                            val hitNpc = plazaNpcs.minByOrNull { npc ->
                                hypot((nx - npc.posX).toDouble(), (ny - npc.posY).toDouble())
                            }?.takeIf { npc ->
                                hypot((nx - npc.posX).toDouble(), (ny - npc.posY).toDouble()) < 0.13
                            }
                            if (hitNpc != null) {
                                val approachX = (hitNpc.posX + if (hitNpc.posX > 0.5f) -0.10f else 0.10f).coerceIn(0.10f, 0.90f)
                                onWalkTo(approachX, hitNpc.posY)
                                onOpenNpc(hitNpc)
                                return@detectTapGestures
                            }

                            // Check if user tapped another real player to inspect them
                            val hitPlayer = otherPlayers.minByOrNull { p ->
                                hypot(nx - p.posX, ny - p.posY)
                            }?.takeIf { p ->
                                hypot(nx - p.posX, ny - p.posY) < 0.11
                            }
                            if (hitPlayer != null) {
                                onInspectPlayer(hitPlayer)
                                return@detectTapGestures
                            }

                            // Otherwise walk smoothly to tapped tile!
                            onWalkTo(nx, ny)
                        }
                    }
                    .testTag("sunhaven_walkable_canvas")
            ) {
                val canvasWidthDp = maxWidth
                val canvasHeightDp = maxHeight

                Image(
                    painter = painterResource(id = R.drawable.img_sunhaven_plaza),
                    contentDescription = "Sunhaven Plaza painterly town courtyard",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x44100D14), Color(0x22100D14), Color(0x88140D08))
                            )
                        )
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Center glowing Sundial Fountain ring
                    val fountainCenter = Offset(w * 0.50f, h * 0.54f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x44FFB800), Color.Transparent),
                            center = fountainCenter,
                            radius = 36.dp.toPx() * auraPulse
                        ),
                        radius = 36.dp.toPx() * auraPulse,
                        center = fountainCenter
                    )
                    drawCircle(
                        color = Color(0xAAFFD166),
                        radius = 22.dp.toPx(),
                        center = fountainCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw the 3 Interactive Town NPCs (Material Vendor, Skill Trainer, Blacksmith)
                    plazaNpcs.forEachIndexed { idx, npc ->
                        val nx = w * npc.posX
                        val ny = h * npc.posY
                        val badgeColor = Color(npc.badgeColorHex)
                        val npcBob = sin(phase + idx * 1.4f) * 1.5.dp.toPx()

                        // Station glowing floor ring & lantern aura
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(badgeColor.copy(alpha = 0.42f * auraPulse), Color.Transparent),
                                center = Offset(nx, ny - 6.dp.toPx()),
                                radius = 26.dp.toPx()
                            ),
                            radius = 26.dp.toPx(),
                            center = Offset(nx, ny - 6.dp.toPx())
                        )
                        drawOval(
                            color = badgeColor.copy(alpha = 0.85f),
                            topLeft = Offset(nx - 17.dp.toPx(), ny - 5.dp.toPx()),
                            size = Size(34.dp.toPx(), 10.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        val (npcClass, npcAvatar, npcRarity) = when (npc) {
                            TownNpcRole.PET_STORE -> Triple(
                                HeroClass.RANGER,
                                HeroAvatarConfig(hairStyle = 3, hairColor = 0, skinTone = 1, eyeColor = 1, outfitPalette = 1, aura = 2),
                                ItemRarity.SOLAR
                            )
                            TownNpcRole.MATERIALS_MERCHANT -> Triple(
                                HeroClass.RANGER,
                                HeroAvatarConfig(hairStyle = 2, hairColor = 1, skinTone = 3, eyeColor = 2, outfitPalette = 2, aura = 4),
                                ItemRarity.RARE
                            )
                            TownNpcRole.SKILL_MENTOR -> Triple(
                                HeroClass.ARCANIST,
                                HeroAvatarConfig(hairStyle = 4, hairColor = 3, skinTone = 1, eyeColor = 3, outfitPalette = 3, aura = 3),
                                ItemRarity.EPIC
                            )
                            TownNpcRole.BLACKSMITH -> Triple(
                                HeroClass.VANGUARD,
                                HeroAvatarConfig(hairStyle = 0, hairColor = 2, skinTone = 2, eyeColor = 0, outfitPalette = 0, aura = 1),
                                ItemRarity.SOLAR
                            )
                            TownNpcRole.ALCHEMIST_SPIRE -> Triple(
                                HeroClass.ARCANIST,
                                HeroAvatarConfig(hairStyle = 1, hairColor = 4, skinTone = 0, eyeColor = 4, outfitPalette = 4, aura = 2),
                                ItemRarity.EPIC
                            )
                            TownNpcRole.QUESTMASTER -> Triple(
                                HeroClass.VANGUARD,
                                HeroAvatarConfig.defaultForClass(HeroClass.VANGUARD),
                                ItemRarity.RARE
                            )
                        }

                        drawHeroSprite(
                            centerX = nx,
                            groundY = ny,
                            scale = 0.48f,
                            bobY = npcBob,
                            heroClass = npcClass,
                            avatar = npcAvatar,
                            weaponRarity = npcRarity,
                            prestigeLevel = 0,
                            auraPulse = auraPulse,
                            facingRight = npc.posX < 0.5f
                        )
                        if (npc == TownNpcRole.PET_STORE) {
                            drawPetSprite(
                                centerX = nx + 20.dp.toPx(),
                                groundY = ny + npcBob * 0.6f,
                                petType = PetType.SUNFOX_KIT,
                                scale = 0.44f,
                                evolutionStage = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE).coerceIn(1, 3)
                            )
                        }
                    }

                    // Draw ONLY real online players from Firestore /town_presence
                    otherPlayers.forEachIndexed { idx, player ->
                        val px = w * player.posX.toFloat().coerceIn(0.08f, 0.92f)
                        val py = h * player.posY.toFloat().coerceIn(0.24f, 0.88f)
                        val bob = sin(phase + idx * 1.1f) * 2.dp.toPx()
                        drawHeroSprite(
                            centerX = px,
                            groundY = py,
                            scale = 0.44f,
                            bobY = bob,
                            heroClass = player.resolvedClass(),
                            avatar = player.resolvedAvatar(),
                            weaponRarity = ItemRarity.RARE,
                            prestigeLevel = player.prestigeLevel.toInt(),
                            auraPulse = auraPulse,
                            facingRight = player.facingRight
                        )
                        player.resolvedPetType()?.let { petType ->
                            val petOffsetX = if (player.facingRight) -20.dp.toPx() else 20.dp.toPx()
                            drawPetSprite(
                                centerX = px + petOffsetX,
                                groundY = py + bob * 0.4f,
                                petType = petType,
                                scale = 0.44f,
                                evolutionStage = player.equippedPetStage.toInt().coerceIn(1, 3),
                                evolutionBranch = player.equippedPetBranch
                            )
                        }
                    }

                    // Draw Local Player with animated walking position, equipped pet, & gold selection ring
                    val localPx = w * animX
                    val localPy = h * animY
                    drawOval(
                        color = Color(0xFFFFD166).copy(alpha = 0.55f * auraPulse),
                        topLeft = Offset(localPx - 18.dp.toPx(), localPy - 5.dp.toPx()),
                        size = Size(36.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawHeroSprite(
                        centerX = localPx,
                        groundY = localPy,
                        scale = 0.50f,
                        bobY = sin(phase * 1.3f) * 2.2.dp.toPx(),
                        heroClass = profile.resolvedClass(),
                        avatar = profile.resolvedAvatar(),
                        weaponRarity = ItemRarity.SOLAR,
                        prestigeLevel = profile.prestigeLevel.toInt(),
                        auraPulse = auraPulse,
                        facingRight = localFacingRight
                    )
                    PetType.entries.firstOrNull { it.name == profile.equippedPetId }?.let { localPet ->
                        val localPetOffsetX = if (localFacingRight) -22.dp.toPx() else 22.dp.toPx()
                        drawPetSprite(
                            centerX = localPx + localPetOffsetX,
                            groundY = localPy + sin(phase * 1.6f) * 1.8.dp.toPx(),
                            petType = localPet,
                            scale = 0.48f,
                            evolutionStage = equippedPet?.resolvedStage() ?: 1,
                            evolutionBranch = equippedPet?.resolvedBranch()?.name ?: "NONE"
                        )
                    }

                    // Dynamic Step & Sleep Volumetric Fog-of-War overlay
                    drawVolumetricFogOverlay(
                        fogDensity = worldFog.visualFogDensity * 0.60f,
                        isDreamMist = worldFog.isDreamMistActive,
                        driftPhase = phase
                    )
                }

                // Interactive Floating Nameplate Badges with Town Building Level for all 5 landmarks
                plazaNpcs.forEach { npc ->
                    val pillWidth = 112.dp
                    val xOffset = (canvasWidthDp * npc.posX - pillWidth / 2f).coerceIn(6.dp, canvasWidthDp - pillWidth - 6.dp)
                    val yOffset = (canvasHeightDp * npc.posY + 4.dp).coerceIn(6.dp, canvasHeightDp - 26.dp)
                    val npcColor = Color(npc.badgeColorHex)
                    val icon = when (npc) {
                        TownNpcRole.PET_STORE -> Icons.Filled.Pets
                        TownNpcRole.MATERIALS_MERCHANT -> Icons.Filled.ShoppingBag
                        TownNpcRole.SKILL_MENTOR -> Icons.Filled.School
                        TownNpcRole.BLACKSMITH -> Icons.Filled.Hardware
                        TownNpcRole.ALCHEMIST_SPIRE -> Icons.Filled.Science
                        TownNpcRole.QUESTMASTER -> Icons.Filled.Assignment
                    }
                    val bldgId = TownBuildingId.entries.firstOrNull { it.npcRole == npc }
                    val lvlTag = bldgId?.let { " Lv.${TownBuildingCatalog.levelOf(profile, it)}" }.orEmpty()
                    Surface(
                        onClick = {
                            val approachX = (npc.posX + if (npc.posX > 0.5f) -0.10f else 0.10f).coerceIn(0.10f, 0.90f)
                            onWalkTo(approachX, npc.posY)
                            onOpenNpc(npc)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xE614111B),
                        border = BorderStroke(1.2.dp, npcColor),
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .testTag("plaza_npc_badge_${npc.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = npc.npcName,
                                tint = npcColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${npc.title}$lvlTag",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFF8E7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Local Emote Speech Pill (top center when active)
                if (localEmote.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xEE18141E),
                        border = BorderStroke(1.dp, Color(0xFFFFD166))
                    ) {
                        Text(
                            text = "${profile.displayName}: $localEmote",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFF8E7),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // World Fog & Watch-Sleep Rested Clarity Bar
            WorldFogStatusBanner(
                worldFog = worldFog,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // 4 Quick-Tap Town Service Station Buttons (accessible 48dp touch targets)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TownNpcRole.entries.forEach { role ->
                    val icon = when (role) {
                        TownNpcRole.QUESTMASTER -> Icons.Filled.Assignment
                        TownNpcRole.PET_STORE -> Icons.Filled.Pets
                        TownNpcRole.MATERIALS_MERCHANT -> Icons.Filled.ShoppingBag
                        TownNpcRole.SKILL_MENTOR -> Icons.Filled.School
                        TownNpcRole.BLACKSMITH -> Icons.Filled.Hardware
                        TownNpcRole.ALCHEMIST_SPIRE -> Icons.Filled.Science
                    }
                    val shortLabel = when (role) {
                        TownNpcRole.QUESTMASTER -> "Quests"
                        TownNpcRole.PET_STORE -> "Pet Store"
                        TownNpcRole.MATERIALS_MERCHANT -> "Market"
                        TownNpcRole.SKILL_MENTOR -> "Training Hall"
                        TownNpcRole.BLACKSMITH -> "Sun-Forge"
                        TownNpcRole.ALCHEMIST_SPIRE -> "Alchemist Spire"
                    }
                    val extraBadge = if (role == TownNpcRole.QUESTMASTER && unclaimedQuestCount > 0) {
                        " ($unclaimedQuestCount)"
                    } else {
                        TownBuildingId.entries.firstOrNull { it.npcRole == role }?.let {
                            " Lv.${TownBuildingCatalog.levelOf(profile, it)}"
                        }.orEmpty()
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(role.badgeColorHex).copy(alpha = 0.14f),
                        border = BorderStroke(1.2.dp, Color(role.badgeColorHex).copy(alpha = 0.65f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable { onOpenNpc(role) }
                            .testTag("town_npc_chip_${role.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = role.npcName,
                                tint = Color(role.badgeColorHex),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$shortLabel$extraBadge",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Walk Pad + Quick Emotes + Live Real Player Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Directional Step Buttons for walking in plaza
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TownStepButton(Icons.AutoMirrored.Filled.ArrowBack, "Walk Left") {
                        onWalkTo(localPos.first - 0.12f, localPos.second)
                    }
                    TownStepButton(Icons.Filled.ArrowUpward, "Walk Up") {
                        onWalkTo(localPos.first, localPos.second - 0.12f)
                    }
                    TownStepButton(Icons.Filled.ArrowDownward, "Walk Down") {
                        onWalkTo(localPos.first, localPos.second + 0.12f)
                    }
                    TownStepButton(Icons.AutoMirrored.Filled.ArrowForward, "Walk Right") {
                        onWalkTo(localPos.first + 0.12f, localPos.second)
                    }
                }

                // Quick Social Emotes
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("👋 Hi!", "⚔ Duel?", "🔥 Quest").forEach { emote ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, ext.borderSubtle),
                            modifier = Modifier
                                .clickable { onSendEmote(emote) }
                                .testTag("town_emote_${emote.take(4)}")
                        ) {
                            Text(
                                text = emote,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Live Adventurers Present in Town (Real Firestore players only — zero AI/NPCs)
            if (otherPlayers.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    otherPlayers.take(8).forEach { other ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clickable { onInspectPlayer(other) }
                                .testTag("inspect_player_${other.userId}")
                        ) {
                            Text(
                                text = "${other.displayName} • Lv.${other.heroLevel} ${other.resolvedClass().title}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Only you are in Sunhaven Plaza right now — real players appear here live when online.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TownStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, LocalSolstrideColors.current.borderSubtle),
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun WorldFogStatusBanner(
    worldFog: WorldFogState,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val isDream = worldFog.isDreamMistActive
    val tint = if (isDream) Color(0xFF6366F1) else MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("world_fog_status_banner"),
        shape = MaterialTheme.shapes.medium,
        color = if (isDream) Color(0xFF151C3A) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (isDream) Color(0xFF818CF8) else ext.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDream) Icons.Filled.Bedtime else Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = worldFog.statusHeadline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDream) Color(0xFFE0E7FF) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${worldFog.realmFogClearedPct}% CLEAR",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDream) Color(0xFFA5B4FC) else tint
                )
            }
            LinearProgressIndicator(
                progress = { (worldFog.realmFogClearedPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = tint,
                trackColor = if (isDream) Color(0xFF2E3768) else MaterialTheme.colorScheme.surface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Steps Clear Fog: ${worldFog.realmStepsWalked}/${worldFog.realmStepsRequired} (+${worldFog.fogCritBonusPct}% Crit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDream) Color(0xFFC7D2FE) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (worldFog.restedClarityBattlesLeft > 0 || worldFog.fogLanternCharges > 0) {
                    Text(
                        text = "+${worldFog.restedExpeditionWardBonusPct}% Surv • +${worldFog.restedXpBonusPct}% XP",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isDream) Color(0xFF86EFAC) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TownNpcModalSheet(
    npcRole: TownNpcRole,
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    quests: List<QuestStatus>,
    ownedPets: List<OwnedPet> = emptyList(),
    equippedPet: OwnedPet? = null,
    onDismiss: () -> Unit,
    onClaimQuest: (QuestStatus) -> Unit,
    onBuyMaterialOffer: (TownMaterialOffer) -> Unit,
    onUpgradeClassSkill: (Int) -> Unit,
    onOpenForgeTab: () -> Unit,
    onAdoptPet: (PetType) -> Unit = {},
    onEquipPet: (PetType) -> Unit = {},
    onBuyPetFood: (PetFoodSpec) -> Unit = {},
    onBuyPetGear: (PetGearSpec) -> Unit = {},
    onOpenPetsTab: () -> Unit = {},
    onBuyBlackMarketOffer: (BlackMarketOfferSpec) -> Unit = {},
    onHireMercenaryContract: (MercenaryContractSpec) -> Unit = {},
    onUpgradeBuilding: (TownBuildingId) -> Unit = {},
    onBuyPetCatalyst: (PetEvolutionCatalystOffer) -> Unit = {},
    onBrewAlchemistInteractive: (AlchemistRecipeSpec, BrewGrade) -> Unit = { _, _ -> }
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = LocalSolstrideColors.current
    val todayKey = currentCalendarDateKey()
    var activeBrewRecipe by remember { mutableStateOf<AlchemistRecipeSpec?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        var marketSection by remember(npcRole) { mutableStateOf(0) }
        var petShopSection by remember(npcRole) { mutableStateOf(1) } // Default to Adopt so buying pets is immediately visible!

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Compact Sticky Header: NPC Name + Sol Coin Purse + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = npcRole.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(npcRole.badgeColorHex),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = npcRole.npcName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonetizationOn,
                            contentDescription = "Sol Coins",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${profile.solCoins}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("town_npc_sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close shop"
                    )
                }
            }

            // Sticky Horizontally Scrollable Category Tabs (Roomy & Never Truncated on Mobile)
            if (npcRole == TownNpcRole.PET_STORE) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val petTabs = listOf(
                        Triple(1, "Adopt Pets", Icons.Filled.Pets),
                        Triple(0, "Pet Treats", Icons.Filled.Restaurant),
                        Triple(2, "Pet Gear", Icons.Filled.Shield),
                        Triple(3, "Evolution Catalysts", Icons.Filled.AutoAwesome)
                    )
                    items(petTabs, key = { it.first }) { (idx, label, icon) ->
                        FilterChip(
                            selected = petShopSection == idx,
                            onClick = { petShopSection = idx },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pet_store_tab_$idx")
                        )
                    }
                }
            } else if (npcRole == TownNpcRole.MATERIALS_MERCHANT) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val marketTabs = listOf(
                        0 to "Materials & Relics",
                        1 to "Black Market",
                        2 to "Mercenaries"
                    )
                    items(marketTabs, key = { it.first }) { (idx, label) ->
                        FilterChip(
                            selected = marketSection == idx,
                            onClick = { marketSection = idx },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("town_market_tab_$idx")
                        )
                    }
                }
            }

            // Single Unified Full-Height Scrollable Shop Body — Everything Scrollable, Zero Clipping!
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "npc_greeting") {
                    Text(
                        text = npcRole.greeting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (npcRole) {
                    TownNpcRole.QUESTMASTER -> {
                        items(quests, key = { it.spec.id }) { status ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (status.canClaim) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${status.spec.category.label.uppercase()} • ${status.spec.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (status.isClaimed) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Claimed",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = status.spec.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LinearProgressIndicator(
                                        progress = { status.progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                    val chestTag = if (status.spec.chestRewardTier != null) " + Chest" else ""
                                    val petTag = status.spec.companionReward?.let { " • ${it.displayName}" } ?: ""
                                    Text(
                                        text = "+${status.spec.solCoinReward} Sol Coins • +${status.spec.xpReward} XP • +${status.spec.sunshardReward} Shards$chestTag$petTag",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Button(
                                        onClick = { onClaimQuest(status) },
                                        enabled = status.canClaim,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("npc_claim_quest_${status.spec.id}")
                                    ) {
                                        Text(
                                            text = when {
                                                status.isClaimed -> "Completed"
                                                status.canClaim -> "Claim Quest Rewards"
                                                else -> "Progress: ${status.currentProgress} / ${status.spec.targetCount}"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TownNpcRole.MATERIALS_MERCHANT -> {
                        val expeditionLocked = profile.hasActiveExpedition()
                        item(key = "market_stockpile") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Stockpile: ${hardcoreState.sunsteelOre} Ore • ${hardcoreState.aetherHerbs} Herbs • ${hardcoreState.elementalEssence} Essence • ${hardcoreState.starforgedIngots} Ingots",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        item(key = "market_building_upgrade") {
                            TownBuildingUpgradeSection(
                                buildingId = TownBuildingId.MARKET,
                                profile = profile,
                                oreCount = hardcoreState.sunsteelOre,
                                onUpgradeBuilding = onUpgradeBuilding
                            )
                        }

                        when (marketSection) {
                            0 -> {
                                val extraStock = TownBuildingCatalog.computeBonuses(profile).extraMerchantStock
                                items(TownMerchantCatalog.offers, key = { it.id }) { offer ->
                                    val bought = hardcoreState.townPurchasesToday(offer.id, todayKey)
                                    val effectiveLimit = offer.dailyStockLimit + extraStock
                                    val remaining = (effectiveLimit - bought).coerceAtLeast(0)
                                    val canAfford = !expeditionLocked && profile.solCoins >= offer.solCoinCost && remaining > 0
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = offer.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(npcRole.badgeColorHex).copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = offer.tagLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(npcRole.badgeColorHex),
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = offer.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Daily Stock: $remaining / $effectiveLimit",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = if (remaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            Button(
                                                onClick = { onBuyMaterialOffer(offer) },
                                                enabled = canAfford,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("buy_town_offer_${offer.id}")
                                            ) {
                                                Text(
                                                    text = if (remaining <= 0) "Sold Out Today" else "Buy for ${offer.solCoinCost} Sol Coins",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                items(ProgressionEngine.blackMarketOffers, key = { it.id }) { offer ->
                                    val canBuy = !expeditionLocked && profile.solCoins >= offer.solCoinCost
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = offer.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = offer.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onBuyBlackMarketOffer(offer) },
                                                enabled = canBuy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("town_black_market_${offer.id}")
                                            ) {
                                                Text(
                                                    text = "Purchase for ${offer.solCoinCost} Sol Coins",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                item(key = "mercs_status") {
                                    Text(
                                        text = if (hardcoreState.mercenaryBattlesLeft > 0) {
                                            "Active Contract: ${MercenaryContractSpec.fromId(hardcoreState.mercenaryContractId)?.name ?: "Active"} (${hardcoreState.mercenaryBattlesLeft} battles left)"
                                        } else {
                                            "Hire an elite mercenary with Sol Coins for the next 5 battles"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                items(MercenaryContractSpec.entries, key = { it.id }) { contract ->
                                    val isCurrent = hardcoreState.mercenaryContractId == contract.id && hardcoreState.mercenaryBattlesLeft > 0
                                    val canHire = !expeditionLocked && profile.solCoins >= contract.solCoinCost
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = contract.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${contract.summary} (${contract.durationBattles} Battles)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onHireMercenaryContract(contract) },
                                                enabled = canHire,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("town_hire_mercenary_${contract.id}")
                                            ) {
                                                Text(
                                                    text = if (isCurrent) "Renew Contract (${contract.solCoinCost} Coins)" else "Hire for ${contract.solCoinCost} Sol Coins",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TownNpcRole.SKILL_MENTOR -> {
                        val classSkills = ClassSkillCatalog.skillsFor(profile.resolvedClass())
                        item(key = "mentor_subtitle") {
                            Text(
                                text = "${profile.resolvedClass().title} Arts • Hero Lv.${profile.level} (Unlock by Level, upgrade with Sol Coins)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        item(key = "mentor_building_upgrade") {
                            TownBuildingUpgradeSection(
                                buildingId = TownBuildingId.TRAINING_HALL,
                                profile = profile,
                                oreCount = hardcoreState.sunsteelOre,
                                onUpgradeBuilding = onUpgradeBuilding
                            )
                        }
                        items(classSkills, key = { it.id }) { skill ->
                            val unlocked = skill.isUnlockedAt(profile.level.toInt())
                            val rank = ClassSkillCatalog.effectiveRank(profile, skill)
                            val maxed = rank >= skill.maxRank
                            val cost = if (!maxed) ClassSkillCatalog.upgradeSolCoinCost(rank) else 0L
                            val canUpgrade = unlocked && !maxed && profile.solCoins >= cost
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (skill.kind == ClassSkillKind.ACTIVE) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ext.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${if (skill.kind == ClassSkillKind.ACTIVE) "⚡ ACTIVE" else "✦ PASSIVE"} • ${skill.name} (Rank $rank/${skill.maxRank})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
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
                                    Spacer(Modifier.height(2.dp))
                                    Button(
                                        onClick = { onUpgradeClassSkill(skill.index) },
                                        enabled = canUpgrade,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("mentor_upgrade_skill_${skill.index}")
                                    ) {
                                        if (!unlocked) {
                                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unlocks at Hero Lv.${skill.unlockLevel}", fontWeight = FontWeight.Bold)
                                        } else if (maxed) {
                                            Text("MAX RANK ACHIEVED", fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("Upgrade Rank ($cost Sol Coins)", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TownNpcRole.PET_STORE -> {
                        val ownedMap = ownedPets.associateBy { it.resolvedType() }
                        val expeditionLocked = profile.hasActiveExpedition()

                        item(key = "pet_store_equipped_strip") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (equippedPet != null) {
                                                "Active Pet: ${equippedPet.resolvedForm().formName} • Lv.${equippedPet.level}"
                                            } else {
                                                "No Companion Equipped"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = if (equippedPet != null) {
                                                "XP: ${equippedPet.xp}/${equippedPet.xpToNextLevel} • Stage ${equippedPet.resolvedStage()}/3"
                                            } else {
                                                "Adopt a companion below to unlock care & evolution!"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = "${ownedPets.size}/6",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        item(key = "pet_store_building_upgrade") {
                            TownBuildingUpgradeSection(
                                buildingId = TownBuildingId.MENAGERIE,
                                profile = profile,
                                oreCount = hardcoreState.sunsteelOre,
                                onUpgradeBuilding = onUpgradeBuilding
                            )
                        }

                        when (petShopSection) {
                            1 -> {
                                items(PetType.entries, key = { it.name }) { petType ->
                                    val owned = ownedMap[petType]
                                    val isEquipped = equippedPet?.resolvedType() == petType
                                    val unlock = PetCatalog.evaluateUnlock(petType, profile)
                                    val rarityColor = petRarityColorFor(petType.rarity)
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.2.dp,
                                            if (isEquipped) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color(0xFF131826)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        drawPetSprite(
                                                            centerX = size.width * 0.5f,
                                                            groundY = size.height * 0.82f,
                                                            petType = petType,
                                                            scale = 0.85f,
                                                            evolutionStage = owned?.resolvedStage() ?: 1,
                                                            evolutionBranch = owned?.resolvedBranch()?.name ?: "NONE"
                                                        )
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = petType.displayName,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = rarityColor.copy(alpha = 0.18f)
                                                        ) {
                                                            Text(
                                                                text = petType.rarity.label.uppercase(),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = rarityColor,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = if (owned != null) {
                                                            "Owned Lv.${owned.level} • ${PetCatalog.computeBonus(owned).summaryText}"
                                                        } else {
                                                            "Lv.1 Bonus: ${PetCatalog.previewBonusAtLevel1(petType)}"
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (owned == null && !unlock.unlocked) {
                                                        Text(
                                                            text = "Unlock: ${petType.unlockRequirementSummary} (${unlock.progressLabel})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    } else {
                                                        Text(
                                                            text = petType.lore,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            when {
                                                owned != null -> Button(
                                                    onClick = { onEquipPet(petType) },
                                                    enabled = !expeditionLocked,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isEquipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("town_equip_pet_${petType.name.lowercase()}")
                                                ) {
                                                    Text(
                                                        text = if (isEquipped) "Currently Equipped (Tap to Unequip)" else "Equip ${petType.displayName}",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                unlock.unlocked -> Button(
                                                    onClick = { onAdoptPet(petType) },
                                                    enabled = !expeditionLocked && profile.solCoins >= petType.coinCost,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("town_adopt_pet_${petType.name.lowercase()}")
                                                ) {
                                                    Text(
                                                        text = if (profile.solCoins >= petType.coinCost) {
                                                            "Adopt ${petType.displayName} (${petType.coinCost} Sol Coins)"
                                                        } else {
                                                            "Need ${petType.coinCost} Sol Coins to Adopt"
                                                        },
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                else -> OutlinedButton(
                                                    onClick = {},
                                                    enabled = false,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Locked (${unlock.progressLabel})")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            0 -> {
                                items(PetCatalog.foodShop, key = { it.id }) { food ->
                                    val canFeed = !expeditionLocked &&
                                        equippedPet != null &&
                                        equippedPet.level < PetCatalog.MAX_PET_LEVEL &&
                                        profile.solCoins >= food.coinCost
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = food.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "+${food.xpGain} Pet XP",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = food.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onBuyPetFood(food) },
                                                enabled = canFeed,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("town_buy_pet_food_${food.id}")
                                            ) {
                                                Text(
                                                    text = if (equippedPet == null) {
                                                        "Equip a Pet First (${food.coinCost} Coins)"
                                                    } else {
                                                        "Feed Now (${food.coinCost} Sol Coins)"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                val allGear = PetCatalog.weaponShop + PetCatalog.armorShop
                                items(allGear, key = { it.id }) { gear ->
                                    val alreadyEquipped = equippedPet?.equippedWeaponId == gear.id ||
                                        equippedPet?.equippedArmorId == gear.id
                                    val canBuy = !expeditionLocked &&
                                        equippedPet != null &&
                                        !alreadyEquipped &&
                                        profile.solCoins >= gear.coinCost
                                    val statLine = buildList {
                                        if (gear.attackBonus > 0) add("+${gear.attackBonus} ATK")
                                        if (gear.defenseBonus > 0) add("+${gear.defenseBonus} DEF")
                                        if (gear.hpBonus > 0) add("+${gear.hpBonus} HP")
                                    }.joinToString(" • ")
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            if (alreadyEquipped) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = gear.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "T${gear.tier} ${gear.slot.name}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "$statLine — ${gear.description}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onBuyPetGear(gear) },
                                                enabled = canBuy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("town_buy_pet_gear_${gear.id}")
                                            ) {
                                                Text(
                                                    text = when {
                                                        alreadyEquipped -> "Equipped on Active Pet"
                                                        equippedPet == null -> "Equip a Pet First (${gear.coinCost} Coins)"
                                                        else -> "Buy & Equip (${gear.coinCost} Sol Coins)"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                val menagerieLv = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE)
                                item(key = "catalyst_pouch") {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Evolution Pouch: ${profile.evolutionShards} Shards • ${profile.mythicCores} Mythic Cores (Menagerie Lv.$menagerieLv)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                items(PetCatalog.catalystShop, key = { it.id }) { cat ->
                                    val unlocked = menagerieLv >= cat.requiredMenagerieLevel
                                    val canBuy = !expeditionLocked && unlocked && profile.solCoins >= cat.coinCost
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cat.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { onBuyPetCatalyst(cat) },
                                                enabled = canBuy,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                                    .testTag("town_buy_catalyst_${cat.id}")
                                            ) {
                                                Text(
                                                    text = if (!unlocked) {
                                                        "Requires Menagerie Lv.${cat.requiredMenagerieLevel}"
                                                    } else {
                                                        "Buy for ${cat.coinCost} Sol Coins"
                                                    },
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "pet_store_open_sanctuary") {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenPetsTab()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("npc_open_pets_sanctuary_button")
                            ) {
                                Icon(Icons.Filled.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Pet Sanctuary & Evolution Tree", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TownNpcRole.BLACKSMITH -> {
                        item(key = "forge_building_upgrade") {
                            TownBuildingUpgradeSection(
                                buildingId = TownBuildingId.FORGE,
                                profile = profile,
                                oreCount = hardcoreState.sunsteelOre,
                                onUpgradeBuilding = onUpgradeBuilding
                            )
                        }
                        item(key = "forge_stockpile_card") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Forge Stockpile Ready",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• Sunsteel Ore: ${hardcoreState.sunsteelOre}\n• Aether Herbs: ${hardcoreState.aetherHerbs}\n• Elemental Essence: ${hardcoreState.elementalEssence}\n• Starforged Masterwork Ingots: ${hardcoreState.starforgedIngots}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenForgeTab()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("npc_go_to_blacksmith_button")
                                    ) {
                                        Icon(Icons.Filled.Hardware, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Interactive Anvil & Gear Vault", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    TownNpcRole.ALCHEMIST_SPIRE -> {
                        item(key = "spire_building_upgrade") {
                            TownBuildingUpgradeSection(
                                buildingId = TownBuildingId.ALCHEMIST_SPIRE,
                                profile = profile,
                                oreCount = hardcoreState.sunsteelOre,
                                onUpgradeBuilding = onUpgradeBuilding
                            )
                        }
                        item(key = "spire_reagents") {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Reagents: ${hardcoreState.aetherHerbs} Herbs • ${hardcoreState.elementalEssence} Essence • ${hardcoreState.sunsteelOre} Ore • ${profile.solCoins} Coins",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                        items(ProgressionEngine.alchemistRecipes, key = { it.id }) { recipe ->
                            val canBrew = hardcoreState.aetherHerbs >= recipe.herbCost &&
                                hardcoreState.elementalEssence >= recipe.essenceCost &&
                                hardcoreState.sunsteelOre >= recipe.oreCost &&
                                profile.solCoins >= recipe.solCoinCost
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = recipe.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = recipe.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val costParts = buildList {
                                        if (recipe.herbCost > 0) add("${recipe.herbCost} Herbs")
                                        if (recipe.essenceCost > 0) add("${recipe.essenceCost} Essence")
                                        if (recipe.oreCost > 0) add("${recipe.oreCost} Ore")
                                        if (recipe.solCoinCost > 0) add("${recipe.solCoinCost} Coins")
                                    }.joinToString(" • ")
                                    Text(
                                        text = "Cost: $costParts",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Button(
                                        onClick = { activeBrewRecipe = recipe },
                                        enabled = canBrew,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("town_spire_brew_${recipe.id}")
                                    ) {
                                        Text(
                                            text = if (canBrew) "Brew in Cauldron" else "Need More Reagents",
                                            fontWeight = FontWeight.Bold
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

    val brewTarget = activeBrewRecipe
    if (brewTarget != null) {
        AlchemistFlameBrewDialog(
            recipeName = brewTarget.name,
            relaxedTiming = hardcoreState.relaxedTimingAssist,
            onComplete = { grade ->
                activeBrewRecipe = null
                onBrewAlchemistInteractive(brewTarget, grade)
            },
            onDismiss = { activeBrewRecipe = null }
        )
    }
}

@Composable
fun InspectTownPlayerDialog(
    player: TownPresencePlayer,
    hasLocalPet: Boolean = false,
    onChallengeDuel: ((TownPresencePlayer) -> Unit)? = null,
    onInvitePetPlaydate: ((TownPresencePlayer) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val playerPet = player.resolvedPetType()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = player.displayName,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MultiplayerHeroPetPreviewCanvas(
                    heroClass = player.resolvedClass(),
                    avatar = player.resolvedAvatar(),
                    prestigeLevel = player.prestigeLevel.toInt(),
                    petType = playerPet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                )
                val prestigeTag = if (player.prestigeLevel > 0L) {
                    " • Ascension ${ProgressionEngine.prestigeRomanNumeral(player.prestigeLevel.toInt())}"
                } else ""
                Text(
                    text = "Lv.${player.heroLevel} ${player.resolvedClass().title}$prestigeTag",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hero Power: ${player.heroPower}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = JetBrainsMonoFontFamily
                )
                Text(
                    text = if (playerPet != null) {
                        "🐾 Companion: ${playerPet.displayName} (Lv.${player.equippedPetLevel})"
                    } else {
                        "No Companion Pet Equipped"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (playerPet != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (player.emote.isNotBlank()) {
                    Text(
                        text = "\"${player.emote}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onChallengeDuel != null || onInvitePetPlaydate != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onChallengeDuel != null) {
                            Button(
                                onClick = { onChallengeDuel(player) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("inspect_duel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SportsMartialArts,
                                    contentDescription = "Duel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Duel", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onInvitePetPlaydate != null) {
                            OutlinedButton(
                                onClick = { onInvitePetPlaydate(player) },
                                enabled = hasLocalPet && playerPet != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("inspect_petplay_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Pets,
                                    contentDescription = "Pet Play",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pet Play", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

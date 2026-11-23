package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.HeroProfile
import com.example.data.model.OwnedPet
import com.example.data.model.PetEvolutionBranch
import com.example.data.model.PetEvolutionCatalog
import com.example.data.model.PetEvolutionFormSpec
import com.example.data.model.PetType
import com.example.data.model.TownBuildingCatalog
import com.example.data.model.TownBuildingId
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class EvolutionAtlasMode(val label: String) {
    BRANCHING_TREE("Branching Tree"),
    ALL_APEX_ABILITIES("All 12 Apex Abilities")
}

private data class SelectedTreeNodeKey(
    val stage: Int,
    val branch: PetEvolutionBranch
)

/**
 * Interactive 3-Stage Branching Pet Evolution Tree Visualizer ("Celestial Lineage Atlas").
 *
 * Lets players visually inspect the full 5-node branching evolution tree for every PetType:
 *   Stage 1 (Base) -> Stage 2 (Solar Vanguard vs. Astral Mystic) -> Stage 3 (Apex Mythic Final Forms)
 * including live Canvas-rendered sprites at each stage, animated branching energy conduits,
 * tap-to-inspect node dossiers, exact hard Evolution Trial gates, and a comparative directory
 * of all 12 unique final-stage Apex Mythic abilities.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetEvolutionTreeAtlas(
    profile: HeroProfile,
    ownedPets: List<OwnedPet>,
    focusedPetType: PetType,
    onSelectPetType: (PetType) -> Unit,
    onVisitTownPetStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ownedByType = remember(ownedPets) { ownedPets.associateBy { it.resolvedType() } }
    var viewMode by remember { mutableStateOf(EvolutionAtlasMode.BRANCHING_TREE) }
    var inspectedNode by remember(focusedPetType) {
        val owned = ownedByType[focusedPetType]
        val initialStage = owned?.resolvedStage() ?: 3
        val initialBranch = when {
            initialStage <= 1 -> PetEvolutionBranch.NONE
            owned != null && owned.resolvedBranch() != PetEvolutionBranch.NONE -> owned.resolvedBranch()
            else -> PetEvolutionBranch.VANGUARD
        }
        mutableStateOf(SelectedTreeNodeKey(initialStage, initialBranch))
    }

    LaunchedEffect(focusedPetType) {
        val owned = ownedByType[focusedPetType]
        val defaultStage = owned?.resolvedStage() ?: 3
        val defaultBranch = when {
            defaultStage <= 1 -> PetEvolutionBranch.NONE
            owned != null && owned.resolvedBranch() != PetEvolutionBranch.NONE -> owned.resolvedBranch()
            else -> PetEvolutionBranch.VANGUARD
        }
        inspectedNode = SelectedTreeNodeKey(defaultStage, defaultBranch)
    }

    val ownedFocused = ownedByType[focusedPetType]
    val stage1Spec = remember(focusedPetType) {
        PetEvolutionCatalog.formFor(focusedPetType, 1, PetEvolutionBranch.NONE)
    }
    val stage2Vanguard = remember(focusedPetType) {
        PetEvolutionCatalog.formFor(focusedPetType, 2, PetEvolutionBranch.VANGUARD)
    }
    val stage2Mystic = remember(focusedPetType) {
        PetEvolutionCatalog.formFor(focusedPetType, 2, PetEvolutionBranch.MYSTIC)
    }
    val stage3Vanguard = remember(focusedPetType) {
        PetEvolutionCatalog.formFor(focusedPetType, 3, PetEvolutionBranch.VANGUARD)
    }
    val stage3Mystic = remember(focusedPetType) {
        PetEvolutionCatalog.formFor(focusedPetType, 3, PetEvolutionBranch.MYSTIC)
    }

    val infinite = rememberInfiniteTransition(label = "evolution_atlas_pulse")
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "conduit_pulse"
    )

    val obsidianBg = Color(0xFF0E111B)
    val obsidianElevated = Color(0xFF161C2D)
    val solarGold = Color(0xFFF59E0B)
    val astralCyan = Color(0xFF38BDF8)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = obsidianBg,
        border = BorderStroke(1.5.dp, solarGold.copy(alpha = 0.55f)),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("pet_evolution_tree_atlas")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14192A), obsidianBg, Color(0xFF0B0E17))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Atlas Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(solarGold, Color(0xFFEA580C)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountTree,
                            contentDescription = "Evolution Tree Atlas",
                            tint = Color(0xFF0B0E17),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "PET EVOLUTION TREE ATLAS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFF8E7)
                        )
                        Text(
                            text = "6 Species · 3 Stages · 12 Apex Mythic Paths",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = solarGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, solarGold.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "ENDGAME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = solarGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Segmented Mode Toggle: Visual Tree vs. All 12 Final-Stage Abilities
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(obsidianElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EvolutionAtlasMode.entries.forEach { mode ->
                    val selected = viewMode == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) solarGold else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewMode = mode }
                            .testTag("evo_atlas_mode_${mode.name.lowercase(Locale.US)}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) Color(0xFF0B0E17) else Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (viewMode == EvolutionAtlasMode.BRANCHING_TREE) {
                // 6-Pet Species Carousel
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PetType.entries, key = { it.name }) { type ->
                        val isSelected = type == focusedPetType
                        val owned = ownedByType[type]
                        val rarityColor = petRarityColorFor(type.rarity)
                        SpeciesSelectorChip(
                            petType = type,
                            ownedPet = owned,
                            selected = isSelected,
                            rarityColor = rarityColor,
                            onClick = { onSelectPetType(type) }
                        )
                    }
                }

                // Visual 3-Stage Branching Tree Graph
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // TIER 1: Stage 1 Root Node
                    Text(
                        text = "STAGE 1 · BASE COMPANION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(6.dp))
                    TreeStageNodeCard(
                        spec = stage1Spec,
                        isSelected = inspectedNode.stage == 1,
                        isAchieved = ownedFocused != null,
                        isCurrentStage = ownedFocused?.resolvedStage() == 1,
                        isAlternateBranchLocked = false,
                        compact = false,
                        pulsePhase = pulsePhase,
                        onClick = {
                            inspectedNode = SelectedTreeNodeKey(1, PetEvolutionBranch.NONE)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .testTag("evo_tree_node_s1")
                    )

                    // STAGE 1 -> STAGE 2 BRANCHING CONDUIT CANVAS
                    BranchSplitConduit(
                        activeBranch = ownedFocused?.resolvedBranch() ?: PetEvolutionBranch.NONE,
                        selectedBranch = inspectedNode.branch,
                        pulsePhase = pulsePhase,
                        trialBadge = "STAGE 2 TRIAL · Lv.${PetEvolutionCatalog.STAGE_2_LEVEL} · 15k Steps · ${PetEvolutionCatalog.STAGE_2_BOSS_WINS} Bosses"
                    )

                    // TIER 2: Branch Split Headers + Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val ownedStage = ownedFocused?.resolvedStage() ?: 0
                        val ownedBranch = ownedFocused?.resolvedBranch() ?: PetEvolutionBranch.NONE
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SOLAR VANGUARD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = solarGold
                            )
                            TreeStageNodeCard(
                                spec = stage2Vanguard,
                                isSelected = inspectedNode.stage == 2 && inspectedNode.branch == PetEvolutionBranch.VANGUARD,
                                isAchieved = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.VANGUARD,
                                isCurrentStage = ownedStage == 2 && ownedBranch == PetEvolutionBranch.VANGUARD,
                                isAlternateBranchLocked = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.MYSTIC,
                                compact = true,
                                pulsePhase = pulsePhase,
                                onClick = {
                                    inspectedNode = SelectedTreeNodeKey(2, PetEvolutionBranch.VANGUARD)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("evo_tree_node_s2_vanguard")
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ASTRAL MYSTIC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = astralCyan
                            )
                            TreeStageNodeCard(
                                spec = stage2Mystic,
                                isSelected = inspectedNode.stage == 2 && inspectedNode.branch == PetEvolutionBranch.MYSTIC,
                                isAchieved = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.MYSTIC,
                                isCurrentStage = ownedStage == 2 && ownedBranch == PetEvolutionBranch.MYSTIC,
                                isAlternateBranchLocked = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.VANGUARD,
                                compact = true,
                                pulsePhase = pulsePhase,
                                onClick = {
                                    inspectedNode = SelectedTreeNodeKey(2, PetEvolutionBranch.MYSTIC)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("evo_tree_node_s2_mystic")
                            )
                        }
                    }

                    // STAGE 2 -> STAGE 3 DUAL ASCENSION CONDUIT CANVAS
                    DualAscensionConduit(
                        activeBranch = ownedFocused?.resolvedBranch() ?: PetEvolutionBranch.NONE,
                        selectedBranch = inspectedNode.branch,
                        pulsePhase = pulsePhase,
                        trialBadge = "APEX MYTHIC TRIAL · Lv.${PetEvolutionCatalog.STAGE_3_LEVEL} · 50k Steps · ${PetEvolutionCatalog.STAGE_3_CORES} Cores"
                    )

                    // TIER 3: Stage 3 Apex Mythic Final Forms + Unique Final-Stage Abilities
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val ownedStage = ownedFocused?.resolvedStage() ?: 0
                        val ownedBranch = ownedFocused?.resolvedBranch() ?: PetEvolutionBranch.NONE
                        ApexFinalStageCard(
                            spec = stage3Vanguard,
                            isSelected = inspectedNode.stage == 3 && inspectedNode.branch == PetEvolutionBranch.VANGUARD,
                            isAchieved = ownedStage >= 3 && ownedBranch == PetEvolutionBranch.VANGUARD,
                            isAlternateBranchLocked = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.MYSTIC,
                            pulsePhase = pulsePhase,
                            onClick = {
                                inspectedNode = SelectedTreeNodeKey(3, PetEvolutionBranch.VANGUARD)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("evo_tree_node_s3_vanguard")
                        )
                        ApexFinalStageCard(
                            spec = stage3Mystic,
                            isSelected = inspectedNode.stage == 3 && inspectedNode.branch == PetEvolutionBranch.MYSTIC,
                            isAchieved = ownedStage >= 3 && ownedBranch == PetEvolutionBranch.MYSTIC,
                            isAlternateBranchLocked = ownedStage >= 2 && ownedBranch == PetEvolutionBranch.VANGUARD,
                            pulsePhase = pulsePhase,
                            onClick = {
                                inspectedNode = SelectedTreeNodeKey(3, PetEvolutionBranch.MYSTIC)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("evo_tree_node_s3_mystic")
                        )
                    }
                }

                // Tap-to-Inspect Dossier Panel for Selected Node
                val inspectedSpec = PetEvolutionCatalog.formFor(
                    focusedPetType,
                    inspectedNode.stage,
                    inspectedNode.branch
                )
                AnimatedContent(
                    targetState = inspectedSpec,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                    label = "node_dossier_anim"
                ) { spec ->
                    SelectedEvolutionNodeDossier(
                        spec = spec,
                        profile = profile,
                        ownedPet = ownedFocused,
                        onVisitTownPetStore = onVisitTownPetStore
                    )
                }
            } else {
                // Comparative Codex: All 6 Species & All 12 Unique Final-Stage Apex Abilities
                AllApexAbilitiesComparisonList(
                    ownedByType = ownedByType,
                    onInspectSpecies = { type, branch ->
                        onSelectPetType(type)
                        inspectedNode = SelectedTreeNodeKey(3, branch)
                        viewMode = EvolutionAtlasMode.BRANCHING_TREE
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeciesSelectorChip(
    petType: PetType,
    ownedPet: OwnedPet?,
    selected: Boolean,
    rarityColor: Color,
    onClick: () -> Unit
) {
    val stage = ownedPet?.resolvedStage() ?: 1
    val branch = ownedPet?.resolvedBranch() ?: PetEvolutionBranch.NONE
    val borderColor = if (selected) Color(0xFFF59E0B) else Color(0xFF334155)
    val bgColor = if (selected) Color(0xFF232B42) else Color(0xFF14192A)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .width(106.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("evo_atlas_species_${petType.name.lowercase(Locale.US)}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(rarityColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPetSprite(
                        centerX = size.width * 0.5f,
                        groundY = size.height * 0.80f,
                        petType = petType,
                        scale = 0.72f,
                        evolutionStage = stage,
                        evolutionBranch = branch.name
                    )
                }
            }
            Text(
                text = petType.displayName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (ownedPet != null) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.6f)
            ) {
                Text(
                    text = if (ownedPet != null) "STAGE $stage/3" else petType.rarity.label.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (ownedPet != null) Color(0xFF34D399) else rarityColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun TreeStageNodeCard(
    spec: PetEvolutionFormSpec,
    isSelected: Boolean,
    isAchieved: Boolean,
    isCurrentStage: Boolean,
    isAlternateBranchLocked: Boolean,
    compact: Boolean,
    pulsePhase: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(spec.auraHex)
    val dimAlpha = if (isAlternateBranchLocked) 0.48f else 1f
    val borderColor = when {
        isSelected -> accent
        isCurrentStage -> Color(0xFF10B981)
        isAchieved -> accent.copy(alpha = 0.65f)
        else -> Color(0xFF334155)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFF1E263D) else Color(0xFF151B2C),
        border = BorderStroke(if (isSelected || isCurrentStage) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 68.dp else 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C101B)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val glowPulse = 0.25f + 0.12f * sin(pulsePhase * 6.28318f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = glowPulse * dimAlpha), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.55f),
                            radius = size.minDimension * 0.56f
                        ),
                        radius = size.minDimension * 0.56f,
                        center = Offset(size.width * 0.5f, size.height * 0.55f)
                    )
                    drawPetSprite(
                        centerX = size.width * 0.5f,
                        groundY = size.height * 0.84f,
                        petType = spec.petType,
                        scale = if (compact) 0.88f else 1.02f,
                        evolutionStage = spec.stage,
                        evolutionBranch = spec.branch.name
                    )
                }
                if (isCurrentStage) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF059669),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                } else if (isAlternateBranchLocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Alternate branch locked",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(14.dp)
                    )
                }
            }
            Text(
                text = spec.formName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFF8E7).copy(alpha = dimAlpha),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${String.format(Locale.US, "%.2fx", spec.statScaleMultiplier)} Stats · ${spec.burstSkillName}",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = dimAlpha),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ApexFinalStageCard(
    spec: PetEvolutionFormSpec,
    isSelected: Boolean,
    isAchieved: Boolean,
    isAlternateBranchLocked: Boolean,
    pulsePhase: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(spec.auraHex)
    val dimAlpha = if (isAlternateBranchLocked) 0.50f else 1f
    val borderBrush = Brush.linearGradient(
        listOf(
            accent.copy(alpha = if (isSelected) 1f else 0.7f * dimAlpha),
            Color(spec.primaryColorHex).copy(alpha = if (isSelected) 1f else 0.6f * dimAlpha)
        )
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) Color(0xFF21263F) else Color(0xFF15192B),
        border = BorderStroke(if (isSelected || isAchieved) 2.2.dp else 1.3.dp, borderBrush),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Stage 3 Mythic Header Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Apex Stage 3",
                    tint = accent.copy(alpha = dimAlpha),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "STAGE 3 APEX",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent.copy(alpha = dimAlpha)
                )
            }

            // Evolved Stage 3 Sprite Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0D18)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val wave = 0.34f + 0.16f * sin(pulsePhase * 6.28318f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = wave * dimAlpha), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.54f),
                            radius = size.minDimension * 0.62f
                        ),
                        radius = size.minDimension * 0.62f,
                        center = Offset(size.width * 0.5f, size.height * 0.54f)
                    )
                    drawPetSprite(
                        centerX = size.width * 0.5f,
                        groundY = size.height * 0.84f,
                        petType = spec.petType,
                        scale = 1.02f,
                        evolutionStage = 3,
                        evolutionBranch = spec.branch.name
                    )
                }
                if (isAchieved) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Achieved",
                        tint = Color(0xFF34D399),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(16.dp)
                    )
                }
            }

            Text(
                text = spec.formName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFF8E7).copy(alpha = dimAlpha),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Unique Final-Stage Ability Spotlight
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f * dimAlpha),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f * dimAlpha)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = spec.burstSkillName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent.copy(alpha = dimAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = spec.burstSkillDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE2E8F0).copy(alpha = dimAlpha),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stat Multiplier Pill
            Text(
                text = "${(spec.burstDamageMult * 100).roundToInt()}% DMG · ${String.format(Locale.US, "%.2fx", spec.statScaleMultiplier)} Stats",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1).copy(alpha = dimAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BranchSplitConduit(
    activeBranch: PetEvolutionBranch,
    selectedBranch: PetEvolutionBranch,
    pulsePhase: Float,
    trialBadge: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val leftX = w * 0.25f
            val rightX = w * 0.75f

            val vanguardHighlight = activeBranch == PetEvolutionBranch.VANGUARD || selectedBranch == PetEvolutionBranch.VANGUARD
            val mysticHighlight = activeBranch == PetEvolutionBranch.MYSTIC || selectedBranch == PetEvolutionBranch.MYSTIC

            val leftPath = Path().apply {
                moveTo(cx, 0f)
                cubicTo(cx, h * 0.58f, leftX, h * 0.42f, leftX, h)
            }
            val rightPath = Path().apply {
                moveTo(cx, 0f)
                cubicTo(cx, h * 0.58f, rightX, h * 0.42f, rightX, h)
            }

            drawPath(
                path = leftPath,
                color = Color(0xFFF59E0B).copy(alpha = if (vanguardHighlight) 0.92f else 0.30f),
                style = Stroke(width = if (vanguardHighlight) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = rightPath,
                color = Color(0xFF38BDF8).copy(alpha = if (mysticHighlight) 0.92f else 0.30f),
                style = Stroke(width = if (mysticHighlight) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Animated energy sparks traveling down both branches
            val t = pulsePhase
            val lx = cx + (leftX - cx) * t
            val rx = cx + (rightX - cx) * t
            val sy = h * t
            drawCircle(
                color = Color(0xFFFDE047),
                radius = if (vanguardHighlight) 4.dp.toPx() else 2.5.dp.toPx(),
                center = Offset(lx, sy)
            )
            drawCircle(
                color = Color(0xFF67E8F9),
                radius = if (mysticHighlight) 4.dp.toPx() else 2.5.dp.toPx(),
                center = Offset(rx, sy)
            )
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF111729),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = trialBadge,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFDE68A),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun DualAscensionConduit(
    activeBranch: PetEvolutionBranch,
    selectedBranch: PetEvolutionBranch,
    pulsePhase: Float,
    trialBadge: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftX = size.width * 0.25f
            val rightX = size.width * 0.75f
            val h = size.height

            val vanguardHighlight = activeBranch == PetEvolutionBranch.VANGUARD || selectedBranch == PetEvolutionBranch.VANGUARD
            val mysticHighlight = activeBranch == PetEvolutionBranch.MYSTIC || selectedBranch == PetEvolutionBranch.MYSTIC

            drawLine(
                color = Color(0xFFF59E0B).copy(alpha = if (vanguardHighlight) 0.92f else 0.30f),
                start = Offset(leftX, 0f),
                end = Offset(leftX, h),
                strokeWidth = if (vanguardHighlight) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = if (mysticHighlight) 0.92f else 0.30f),
                start = Offset(rightX, 0f),
                end = Offset(rightX, h),
                strokeWidth = if (mysticHighlight) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            val sy = h * pulsePhase
            drawCircle(Color(0xFFFFD166), radius = 3.5.dp.toPx(), center = Offset(leftX, sy))
            drawCircle(Color(0xFFA5B4FC), radius = 3.5.dp.toPx(), center = Offset(rightX, sy))
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF141129),
            border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.65f)),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = trialBadge,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE9D5FF),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedEvolutionNodeDossier(
    spec: PetEvolutionFormSpec,
    profile: HeroProfile,
    ownedPet: OwnedPet?,
    onVisitTownPetStore: () -> Unit
) {
    val accent = Color(spec.auraHex)
    val req = when (spec.stage) {
        2 -> PetEvolutionCatalog.requirementForNextStage(1)
        3 -> PetEvolutionCatalog.requirementForNextStage(2)
        else -> null
    }
    val menagerieLv = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF161C2E),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("evo_atlas_node_dossier")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0D18)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.52f),
                                radius = size.minDimension * 0.56f
                            ),
                            radius = size.minDimension * 0.56f,
                            center = Offset(size.width * 0.5f, size.height * 0.52f)
                        )
                        drawPetSprite(
                            centerX = size.width * 0.5f,
                            groundY = size.height * 0.84f,
                            petType = spec.petType,
                            scale = 0.92f,
                            evolutionStage = spec.stage,
                            evolutionBranch = spec.branch.name
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "STAGE ${spec.stage} · ${spec.branch.label.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    Text(
                        text = spec.formName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFF8E7)
                    )
                    Text(
                        text = spec.formTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Text(
                text = spec.lore,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1)
            )

            // Signature Burst Breakdown
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Signature Ability",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (spec.stage == 3) "UNIQUE APEX ABILITY: ${spec.burstSkillName}" else "BURST SKILL: ${spec.burstSkillName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                    }
                    Text(
                        text = spec.burstSkillDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF8FAFC)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricPill(Icons.Filled.Whatshot, "${(spec.burstDamageMult * 100).roundToInt()}% DMG", Color(0xFFF97316))
                        if (spec.burstHealPct > 0f) {
                            MetricPill(Icons.Filled.Bolt, "${(spec.burstHealPct * 100).roundToInt()}% Heal", Color(0xFF10B981))
                        }
                        if (spec.burstBarrierPct > 0f) {
                            MetricPill(Icons.Filled.Shield, "${(spec.burstBarrierPct * 100).roundToInt()}% Barrier", Color(0xFF38BDF8))
                        }
                        MetricPill(Icons.Filled.Star, "${String.format(Locale.US, "%.2fx", spec.statScaleMultiplier)} Aura", Color(0xFFF59E0B))
                    }
                }
            }

            // Hard Evolution Trial Checklist (for Stage 2 & Stage 3)
            if (req != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (spec.stage == 3) "STAGE 3 APEX TRIAL REQUIREMENTS (PRESTIGE)" else "STAGE 2 AWAKENING TRIAL REQUIREMENTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFDE68A)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrialGateChip(
                            label = "Pet Lv.${req.minLevel}",
                            met = (ownedPet?.level ?: 0L) >= req.minLevel
                        )
                        TrialGateChip(
                            label = "Bond ${"%,d".format(req.minBondXp)}",
                            met = (ownedPet?.bondXp ?: 0L) >= req.minBondXp
                        )
                        TrialGateChip(
                            label = "${"%,d".format(req.minStepsWalked)} Steps",
                            met = (ownedPet?.stepsWalkedWithPet ?: 0L) >= req.minStepsWalked
                        )
                        TrialGateChip(
                            label = "${req.minTrueBossWins} Boss Wins",
                            met = profile.trueBossWins >= req.minTrueBossWins
                        )
                        TrialGateChip(
                            label = "Realm ${req.minRealmReached}+",
                            met = profile.currentRealm >= req.minRealmReached
                        )
                        TrialGateChip(
                            label = "Menagerie Lv.${req.minMenagerieLevel}",
                            met = menagerieLv >= req.minMenagerieLevel
                        )
                        if (req.evolutionShardsNeeded > 0L) {
                            TrialGateChip(
                                label = "${req.evolutionShardsNeeded} Evo Shards",
                                met = profile.evolutionShards >= req.evolutionShardsNeeded
                            )
                        }
                        if (req.mythicCoresNeeded > 0L) {
                            TrialGateChip(
                                label = "${req.mythicCoresNeeded} Mythic Cores",
                                met = profile.mythicCores >= req.mythicCoresNeeded
                            )
                        }
                        if (req.solCoinTribute > 0L) {
                            TrialGateChip(
                                label = "${req.solCoinTribute} Coins",
                                met = profile.solCoins >= req.solCoinTribute
                            )
                        }
                        if (req.sunshardTribute > 0L) {
                            TrialGateChip(
                                label = "${"%,d".format(req.sunshardTribute)} Sunshards",
                                met = profile.sunshards >= req.sunshardTribute
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onVisitTownPetStore,
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("evo_atlas_visit_town_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = "Pip's Pet Store",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Earn Catalysts & Upgrade Menagerie in Town",
                            color = Color(0xFFFFF8E7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllApexAbilitiesComparisonList(
    ownedByType: Map<PetType, OwnedPet>,
    onInspectSpecies: (PetType, PetEvolutionBranch) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Tap any Apex Mythic form below to jump directly into its full 3-stage branching evolution tree.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )
        PetType.entries.forEach { petType ->
            val owned = ownedByType[petType]
            val vanguardApex = PetEvolutionCatalog.formFor(petType, 3, PetEvolutionBranch.VANGUARD)
            val mysticApex = PetEvolutionCatalog.formFor(petType, 3, PetEvolutionBranch.MYSTIC)
            val rarityColor = petRarityColorFor(petType.rarity)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF151B2C),
                border = BorderStroke(1.dp, rarityColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
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
                            text = "${petType.displayName} Lineage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFF8E7)
                        )
                        Text(
                            text = if (owned != null) "Owned · Stage ${owned.resolvedStage()}/3" else petType.rarity.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (owned != null) Color(0xFF34D399) else rarityColor
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ApexMiniComparisonTile(
                            spec = vanguardApex,
                            branchLabel = "VANGUARD APEX",
                            onClick = { onInspectSpecies(petType, PetEvolutionBranch.VANGUARD) },
                            modifier = Modifier.weight(1f)
                        )
                        ApexMiniComparisonTile(
                            spec = mysticApex,
                            branchLabel = "MYSTIC APEX",
                            onClick = { onInspectSpecies(petType, PetEvolutionBranch.MYSTIC) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApexMiniComparisonTile(
    spec: PetEvolutionFormSpec,
    branchLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(spec.auraHex)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0E1220),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = branchLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = spec.formName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFF8E7),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.burstSkillName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFDE68A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.burstSkillDescription,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

@Composable
private fun TrialGateChip(
    label: String,
    met: Boolean
) {
    val tint = if (met) Color(0xFF34D399) else Color(0xFFF59E0B)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = if (met) 0.16f else 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (met) 0.55f else 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                contentDescription = if (met) "Requirement met" else "Requirement locked",
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (met) Color(0xFFA7F3D0) else Color(0xFFFDE68A)
            )
        }
    }
}

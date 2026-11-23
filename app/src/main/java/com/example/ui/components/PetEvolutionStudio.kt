package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.HeroProfile
import com.example.data.model.OwnedPet
import com.example.data.model.PetEvolutionBranch
import com.example.data.model.PetEvolutionCatalog
import com.example.data.model.PetEvolutionFormSpec
import com.example.data.model.TownBuildingCatalog
import com.example.data.model.TownBuildingId
import com.example.data.model.currentCalendarDateKey
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Interactive 2D Companion Care Sanctuary:
 * - Tap your companion directly to pet & boost Bond XP (floating glow feedback)
 * - Toss a Treat (from owned Pet Food) or run a Sparring Drill (up to 5 care actions/day)
 * - Strictly respects Town-only shopping: directs player to Pip's Pet Store in Town if low on Treats.
 */
@Composable
fun InteractivePetCareHabitatCard(
    pet: OwnedPet,
    petFoodCount: Long,
    onPetCareAction: (String) -> Unit,
    onVisitPipsPetStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val form = pet.resolvedForm()
    val todayKey = currentCalendarDateKey()
    val usedToday = if (pet.lastCareDateKey == todayKey) pet.dailyCareActions.toInt() else 0
    val dailyCap = PetEvolutionCatalog.DAILY_CARE_LIMIT
    val remainingCare = (dailyCap - usedToday).coerceAtLeast(0)

    val bounce = remember { Animatable(0f) }
    val heartBurst = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val infinite = rememberInfiniteTransition(label = "pet_habitat_idle")
    val idleWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pet_bob"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.3.dp, Color(form.auraHex).copy(alpha = 0.6f)),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${form.formName} • Stage ${pet.resolvedStage()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${form.title} • Bond XP: ${pet.bondXp} • Steps Together: ${"%,d".format(pet.stepsWalkedWithPet)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(form.auraHex).copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "$remainingCare/$dailyCap Care Left",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(form.auraHex),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Interactive Tap-to-Pet 2D Sanctuary Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(pet.petType, remainingCare) {
                        detectTapGestures {
                            scope.launch {
                                bounce.snapTo(1f)
                                heartBurst.snapTo(1f)
                                launch {
                                    bounce.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                                launch {
                                    heartBurst.animateTo(0f, tween(650))
                                }
                            }
                            onPetCareAction("PET")
                        }
                    }
                    .testTag("pet_care_habitat_canvas"),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF78350F))
                        ),
                        size = size
                    )
                    // Sanctuary meadow floor
                    drawOval(
                        color = Color(0xFF065F46),
                        topLeft = Offset(w * 0.14f, h * 0.68f),
                        size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.28f)
                    )
                    val bobY = sin(idleWave) * 4.dp.toPx() - bounce.value * 14.dp.toPx()
                    drawPetSprite(
                        centerX = w * 0.5f,
                        groundY = h * 0.78f + bobY,
                        petType = pet.resolvedType(),
                        scale = 1.45f,
                        evolutionStage = pet.resolvedStage(),
                        evolutionBranch = pet.resolvedBranch().name
                    )
                    // Heart / Bond sparkle burst on tap
                    val hb = heartBurst.value
                    if (hb > 0.02f) {
                        val rise = (1f - hb) * 34.dp.toPx()
                        drawCircle(
                            color = Color(0xFFF43F5E).copy(alpha = hb),
                            radius = 6.dp.toPx(),
                            center = Offset(w * 0.44f, h * 0.34f - rise)
                        )
                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = hb),
                            radius = 5.dp.toPx(),
                            center = Offset(w * 0.57f, h * 0.30f - rise * 1.2f)
                        )
                    }
                }
                Text(
                    text = "Tap ${form.formName} to Pet (+Bond XP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFF8E7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(Color(0x880F172A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            // Care Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onPetCareAction("TREAT_TOSS") },
                    enabled = petFoodCount > 0L && remainingCare > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pet_care_treat_toss_button")
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Toss Treat", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Toss Treat ($petFoodCount)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { onPetCareAction("SPAR_DRILL") },
                    enabled = remainingCare > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("pet_care_spar_drill_button")
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = "Sparring Drill", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Spar Drill", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (petFoodCount <= 0L) {
                OutlinedButton(
                    onClick = onVisitPipsPetStore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pet_care_visit_town_button")
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = "Pip's Pet Store", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buy Pet Treats & Catalysts at Pip's Pet Store (Town)")
                }
            }
        }
    }
}

/**
 * 3-Stage Branching Pet Evolution Tree & Evolution Trigger Card.
 * Displays Stage 1 -> Stage 2 (Solar Vanguard vs. Astral Mystic) -> Stage 3 Apex Mythic forms,
 * live requirement checklists, and quick-travel to Pip's Pet Store in Town for Evolution Shards / Mythic Cores.
 */
@Composable
fun PetEvolutionTreeCard(
    profile: HeroProfile,
    pet: OwnedPet,
    onEvolvePet: (OwnedPet, PetEvolutionBranch) -> Unit,
    onVisitPipsPetStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = pet.resolvedStage()
    val branch = pet.resolvedBranch()
    val petType = pet.resolvedType()
    val req = PetEvolutionCatalog.nextRequirement(stage)
    var selectedBranch by remember(pet.petType, branch) {
        mutableStateOf(if (branch != PetEvolutionBranch.NONE) branch else PetEvolutionBranch.VANGUARD)
    }

    val stage2Vanguard = PetEvolutionCatalog.formFor(petType, 2, PetEvolutionBranch.VANGUARD)
    val stage2Mystic = PetEvolutionCatalog.formFor(petType, 2, PetEvolutionBranch.MYSTIC)
    val stage3Target = PetEvolutionCatalog.formFor(petType, 3, selectedBranch)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.3.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Pet Evolution", tint = Color(0xFFF59E0B))
                    Column {
                        Text(
                            text = "3-STAGE BRANCHING EVOLUTION",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Shards: ${profile.evolutionShards} • Mythic Cores: ${profile.mythicCores}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "STAGE $stage / 3",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Current Evolved Signature Burst Skill preview
            val curForm = pet.resolvedForm()
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(curForm.auraHex).copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "ACTIVE BURST: ${curForm.burstSkillName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(curForm.auraHex)
                    )
                    Text(
                        text = curForm.burstSkillDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (req == null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "MAX APEX MYTHIC FORM ACHIEVED! ${curForm.formName} has reached Stage 3 Ascension.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // Stage 1 -> Stage 2 Branch Picker (Solar Vanguard vs Astral Mystic)
                if (stage == 1) {
                    Text(
                        text = "Choose Stage 2 Evolution Branch:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BranchOptionCard(
                            spec = stage2Vanguard,
                            branch = PetEvolutionBranch.VANGUARD,
                            selected = selectedBranch == PetEvolutionBranch.VANGUARD,
                            onSelect = { selectedBranch = PetEvolutionBranch.VANGUARD },
                            modifier = Modifier.weight(1f)
                        )
                        BranchOptionCard(
                            spec = stage2Mystic,
                            branch = PetEvolutionBranch.MYSTIC,
                            selected = selectedBranch == PetEvolutionBranch.MYSTIC,
                            onSelect = { selectedBranch = PetEvolutionBranch.MYSTIC },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Stage 2 -> Stage 3 Apex Preview
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(stage3Target.auraHex).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(stage3Target.auraHex)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "NEXT APEX FORM: ${stage3Target.formName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(stage3Target.auraHex)
                            )
                            Text(
                                text = "${stage3Target.burstSkillName} — ${stage3Target.burstSkillDescription}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Hard Prestige Evolution Trial Progress Bars
                val menagerieLv = TownBuildingCatalog.levelOf(profile, TownBuildingId.MENAGERIE)
                val lvlOk = pet.level >= req.minPetLevel
                val bondOk = pet.bondXp >= req.minBondXp
                val stepsOk = pet.stepsWalkedWithPet >= req.minStepsWithPet
                val bossOk = profile.trueBossWins >= req.minTrueBossWins
                val realmOk = profile.currentRealm >= req.minRealmReached
                val menagerieOk = menagerieLv >= req.minMenagerieLevel
                val shardsOk = profile.evolutionShards >= req.evolutionShardsCost
                val coresOk = profile.mythicCores >= req.mythicCoresCost
                val coinsOk = profile.solCoins >= req.solCoinTribute
                val sunshardsOk = profile.sunshards >= req.sunshardTribute
                val metCount = listOf(
                    lvlOk, bondOk, stepsOk, bossOk, realmOk, menagerieOk,
                    shardsOk, coresOk, coinsOk, sunshardsOk
                ).count { it }
                val totalCount = 10
                val canEvolve = metCount == totalCount

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFD97706).copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PRESTIGE EVOLUTION TRIAL ($metCount / $totalCount GATES MET) — True long-term dedication required",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvolutionReqRow("Pet Level", "Lv.${pet.level} / ${req.minPetLevel}", (pet.level.toFloat() / req.minPetLevel).coerceIn(0f, 1f), lvlOk)
                    EvolutionReqRow("Companion Bond XP", "${"%,d".format(pet.bondXp)} / ${"%,d".format(req.minBondXp)}", (pet.bondXp.toFloat() / req.minBondXp).coerceIn(0f, 1f), bondOk)
                    EvolutionReqRow("Steps Walked Together", "${"%,d".format(pet.stepsWalkedWithPet)} / ${"%,d".format(req.minStepsWithPet)}", (pet.stepsWalkedWithPet.toFloat() / req.minStepsWithPet).coerceIn(0f, 1f), stepsOk)
                    EvolutionReqRow("True Boss Victories", "${profile.trueBossWins} / ${req.minTrueBossWins}", (profile.trueBossWins.toFloat() / req.minTrueBossWins.coerceAtLeast(1L)).coerceIn(0f, 1f), bossOk)
                    EvolutionReqRow("Campaign Realm Reached", "Realm ${profile.currentRealm} / ${req.minRealmReached}", (profile.currentRealm.toFloat() / req.minRealmReached.coerceAtLeast(1L)).coerceIn(0f, 1f), realmOk)
                    EvolutionReqRow("Pip's Menagerie (Town)", "Lv.$menagerieLv / Lv.${req.minMenagerieLevel}", (menagerieLv.toFloat() / req.minMenagerieLevel.coerceAtLeast(1)).coerceIn(0f, 1f), menagerieOk)
                    if (req.evolutionShardsCost > 0L) {
                        EvolutionReqRow("Evolution Shards", "${profile.evolutionShards} / ${req.evolutionShardsCost}", (profile.evolutionShards.toFloat() / req.evolutionShardsCost).coerceIn(0f, 1f), shardsOk)
                    }
                    if (req.mythicCoresCost > 0L) {
                        EvolutionReqRow("Mythic Apex Cores", "${profile.mythicCores} / ${req.mythicCoresCost}", (profile.mythicCores.toFloat() / req.mythicCoresCost).coerceIn(0f, 1f), coresOk)
                    }
                    if (req.solCoinTribute > 0L) {
                        EvolutionReqRow("Sol Coin Ritual Tribute", "${profile.solCoins} / ${req.solCoinTribute}", (profile.solCoins.toFloat() / req.solCoinTribute).coerceIn(0f, 1f), coinsOk)
                    }
                    if (req.sunshardTribute > 0L) {
                        EvolutionReqRow("Sunshard Ritual Tribute", "${"%,d".format(profile.sunshards)} / ${"%,d".format(req.sunshardTribute)}", (profile.sunshards.toFloat() / req.sunshardTribute).coerceIn(0f, 1f), sunshardsOk)
                    }
                }

                Button(
                    onClick = { onEvolvePet(pet, selectedBranch) },
                    enabled = canEvolve,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("evolve_pet_confirm_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Evolve Pet", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (canEvolve) "ASCEND TO STAGE ${req.targetStage}!" else "Complete Trial ($metCount/$totalCount Gates)",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (!shardsOk || !coresOk || !menagerieOk) {
                    OutlinedButton(
                        onClick = onVisitPipsPetStore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("evo_visit_pips_store_button")
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = "Pip's Pet Store", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upgrade Menagerie & Buy Catalysts in Town")
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchOptionCard(
    spec: PetEvolutionFormSpec,
    branch: PetEvolutionBranch,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(spec.auraHex)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("evo_branch_${branch.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = branch.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Text(
                text = spec.formName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = spec.burstSkillName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EvolutionReqRow(
    label: String,
    valueText: String,
    progress: Float,
    met: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (met) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (met) Color(0xFF10B981) else Color(0xFFF59E0B)
        )
    }
}

/**
 * Celebratory animated Evolution Ceremony Dialog displayed when a pet evolves to Stage 2 or Stage 3.
 */
@Composable
fun PetEvolutionCeremonyDialog(
    evolvedPet: OwnedPet,
    onDismiss: () -> Unit
) {
    val form = evolvedPet.resolvedForm()
    val scaleAnim = remember { Animatable(0.4f) }
    LaunchedEffect(evolvedPet) {
        scaleAnim.animateTo(1.1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, Color(form.auraHex)),
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "COMPANION EVOLVED!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(form.auraHex)
                )
                Text(
                    text = form.formName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(form.auraHex).copy(alpha = 0.55f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.52f),
                                radius = size.minDimension * 0.48f * scaleAnim.value
                            ),
                            radius = size.minDimension * 0.48f * scaleAnim.value,
                            center = Offset(size.width * 0.5f, size.height * 0.52f)
                        )
                        drawPetSprite(
                            centerX = size.width * 0.5f,
                            groundY = size.height * 0.80f,
                            petType = evolvedPet.resolvedType(),
                            scale = 1.55f * scaleAnim.value.coerceIn(0.6f, 1.15f),
                            evolutionStage = evolvedPet.resolvedStage(),
                            evolutionBranch = evolvedPet.resolvedBranch().name
                        )
                    }
                }
                Text(
                    text = "${form.title} • ${form.burstSkillName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(form.auraHex),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = form.burstSkillDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(form.auraHex)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("evolution_ceremony_close_button")
                ) {
                    Text("Unleash New Power!", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DerivedHeroStats
import com.example.data.model.GearPerkType
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.OwnedPet
import com.example.data.model.PetType
import com.example.domain.BattleAnimAction
import com.example.domain.BattleAnimEvent
import com.example.domain.BattleMode
import com.example.domain.BattleState
import com.example.domain.CombatEnemy
import com.example.domain.EnemyArtKey
import com.example.domain.ProgressionEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun gearAccentColor(rarity: ItemRarity?): Color = when (rarity) {
    ItemRarity.ULTRA_RARE -> Color(0xFFFF4D9D)
    ItemRarity.SOLAR -> Color(0xFFFFB800)
    ItemRarity.EPIC -> Color(0xFFA855F7)
    ItemRarity.RARE -> Color(0xFF38BDF8)
    ItemRarity.COMMON -> Color(0xFFB8A99A)
    null -> Color(0xFF6B7280)
}

private fun petAccentColor(petType: PetType): Color = when (petType) {
    PetType.SUNFOX_KIT -> Color(0xFFFF9F1C)
    PetType.AEGIS_SCARAB -> Color(0xFF38BDF8)
    PetType.DUNE_FALCON -> Color(0xFFFDE047)
    PetType.CINDER_LYNX -> Color(0xFFF97316)
    PetType.OASIS_TORTOISE -> Color(0xFF34D399)
    PetType.SOLAR_PHOENIX -> Color(0xFFFF4D6D)
}

@Composable
fun HeroSleepingBanner(
    sleepSourceLabel: String,
    activeSleepMinutes: Long,
    onWakeHero: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_sleeping_banner"),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF141B36),
        border = BorderStroke(1.5.dp, Color(0xFF7C9BFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = "Hero sleeping from watch sleep detection",
                tint = Color(0xFFA5B4FC),
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hero Sleeping • Watch Rest Active",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEEF2FF)
                )
                val sleepReward = com.example.domain.ProgressionEngine.computeSleepSolCoinReward(activeSleepMinutes)
                val durText = if (activeSleepMinutes > 0L) {
                    val h = activeSleepMinutes / 60L
                    val m = activeSleepMinutes % 60L
                    if (h > 0L) "${h}h ${m}m" else "${m}m"
                } else {
                    "Live"
                }
                Text(
                    text = "${sleepSourceLabel.ifBlank { "Smartwatch" }} ($durText • ${sleepReward.sleepPercent}%) — Grants +${sleepReward.solCoinsEarned}/50 Sol Coins, HP, Energy & cures Wounds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC7D2FE)
                )
            }
            Button(
                onClick = onWakeHero,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("wake_hero_now_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Wake")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeroShowcaseCard(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    inventory: List<InventoryItem>,
    equippedPet: OwnedPet?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isSleeping: Boolean = false,
    sleepSourceLabel: String = ""
) {
    val ext = LocalSolstrideColors.current
    val weapon = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.WEAPON } }
    val armor = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.ARMOR } }
    val helm = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.HELM } }
    val boots = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.BOOTS } }
    val ring = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.RING } }
    val relic = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.RELIC } }

    val infinite = rememberInfiniteTransition(label = "hero_idle")
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "breath_phase"
    )
    val auraPulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura_pulse"
    )
    val sleepDrift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sleep_drift"
    )

    val prestigeBadge = ProgressionEngine.prestigeRomanNumeral(profile.prestigeLevel.toInt())

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_showcase_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (isSleeping) Color(0xFF7C9BFF) else ext.borderStrong)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 166.dp else 188.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = if (isSleeping) {
                                listOf(Color(0xFF0A0F29), Color(0xFF151E42), Color(0xFF1D243C))
                            } else {
                                listOf(Color(0xFF161925), Color(0xFF232136), Color(0xFF2D1E17))
                            }
                        )
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val groundY = size.height * 0.80f
                    if (isSleeping) {
                        // Moonlit aura & crescent moon with twinkling night stars
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x44818CF8),
                                    Color(0x186366F1),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.25f, size.height * 0.42f),
                                radius = size.height * 0.65f
                            ),
                            radius = size.height * 0.65f,
                            center = Offset(size.width * 0.25f, size.height * 0.42f)
                        )
                        val moonCenter = Offset(size.width * 0.11f, size.height * 0.20f)
                        drawCircle(
                            color = Color(0xFFE0E7FF),
                            radius = 12.dp.toPx(),
                            center = moonCenter
                        )
                        drawCircle(
                            color = Color(0xFF0E1535),
                            radius = 10.dp.toPx(),
                            center = Offset(moonCenter.x + 5.dp.toPx(), moonCenter.y - 3.dp.toPx())
                        )
                        val starCoords = listOf(
                            Offset(size.width * 0.20f, size.height * 0.15f),
                            Offset(size.width * 0.34f, size.height * 0.18f),
                            Offset(size.width * 0.06f, size.height * 0.38f),
                            Offset(size.width * 0.37f, size.height * 0.34f)
                        )
                        starCoords.forEachIndexed { idx, pt ->
                            val alpha = (0.35f + 0.55f * ((sin(breathPhase + idx * 1.3f) + 1f) * 0.5f))
                            drawCircle(
                                color = Color(0xFFE0E7FF).copy(alpha = alpha),
                                radius = (1.6f + (idx % 2) * 0.7f).dp.toPx(),
                                center = pt
                            )
                        }
                    } else {
                        // Sunlit sky halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x44FFB800),
                                    Color(0x16FF6B35),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.25f, size.height * 0.42f),
                                radius = size.height * 0.65f
                            ),
                            radius = size.height * 0.65f,
                            center = Offset(size.width * 0.25f, size.height * 0.42f)
                        )
                    }
                    // Horizon & stone dais
                    drawRect(
                        color = if (isSleeping) Color(0xFF1F2744) else Color(0xFF3A281E),
                        topLeft = Offset(0f, groundY),
                        size = Size(size.width, size.height - groundY)
                    )
                    drawLine(
                        color = if (isSleeping) Color(0x66818CF8) else Color(0x66FFD166),
                        start = Offset(0f, groundY),
                        end = Offset(size.width, groundY),
                        strokeWidth = 2.dp.toPx()
                    )

                    val heroCenterX = size.width * 0.25f
                    val heroBob = if (isSleeping) {
                        sin(breathPhase * 0.5f) * 1.8.dp.toPx() + 5.dp.toPx()
                    } else {
                        sin(breathPhase) * 3.5.dp.toPx()
                    }
                    drawHeroSprite(
                        centerX = heroCenterX,
                        groundY = groundY,
                        scale = if (compact) 0.90f else 1.02f,
                        bobY = heroBob,
                        heroClass = profile.resolvedClass(),
                        weaponRarity = weapon?.resolvedRarity(),
                        armorRarity = armor?.resolvedRarity() ?: helm?.resolvedRarity(),
                        relicRarity = relic?.resolvedRarity() ?: ring?.resolvedRarity(),
                        prestigeLevel = profile.prestigeLevel.toInt(),
                        auraPulse = if (isSleeping) 0.45f else auraPulse,
                        facingRight = true,
                        swingProgress = 0f,
                        guardAlpha = 0f
                    )

                    if (equippedPet != null) {
                        val petBob = if (isSleeping) {
                            sin(breathPhase * 0.5f + 1.0f) * 1.5.dp.toPx() + 3.dp.toPx()
                        } else {
                            sin(breathPhase + 1.4f) * 4.dp.toPx()
                        }
                        drawPetSprite(
                            centerX = heroCenterX - 50.dp.toPx(),
                            groundY = groundY - 6.dp.toPx() + petBob,
                            petType = equippedPet.resolvedType(),
                            scale = if (compact) 0.80f else 0.90f
                        )
                    }
                }

                // Floating moonlit Z z z particles when Hero is sleeping
                if (isSleeping) {
                    val driftOffsetDp = (sleepDrift * 14f).dp
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 66.dp, top = (24.dp - driftOffsetDp).coerceAtLeast(6.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Z  z  z",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFC7D2FE).copy(alpha = 0.55f + 0.45f * (1f - sleepDrift))
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xCC1E293B),
                        border = BorderStroke(1.dp, Color(0xFF818CF8))
                    ) {
                        Text(
                            text = "🌙 Sleeping • ${sleepSourceLabel.ifBlank { "Watch Rest" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE0E7FF),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Right-side Hero Info Overlay (6-Slot Equipment Grid)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.60f)
                        .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFF8E7),
                            maxLines = 1
                        )
                        if (prestigeBadge.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFB800)
                            ) {
                                Text(
                                    text = "P$prestigeBadge",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1205),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "LVL ${profile.level} ${stats.activeSpecialization.title.uppercase()} • ${stats.activeTitle.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSleeping) Color(0xFFA5B4FC) else Color(0xFFFFD166),
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1
                    )
                    Text(
                        text = "PWR ${stats.computedHeroPower} • ${stats.primaryAffinity.symbol} ${stats.primaryAffinity.label.uppercase()} • R${profile.currentRealm}-${profile.realmStage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF86EFAC),
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GearSlotMiniLine("WPN", weapon)
                            GearSlotMiniLine("ARM", armor)
                            GearSlotMiniLine("HLM", helm)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            GearSlotMiniLine("BTS", boots)
                            GearSlotMiniLine("RNG", ring)
                            GearSlotMiniLine("RLC", relic)
                        }
                    }
                }
            }

            // Active Gear Set Resonance, Specialization, Wounds & Combat Perk Strip
            val activePerks = remember(stats) {
                buildList {
                    if (stats.setBonus.activeTier != null) add(stats.setBonus.label)
                    add("Ult: ${stats.activeSpecialization.ultimateName}")
                    if (stats.woundPenaltyPercent > 0) add("WOUNDED -${stats.woundPenaltyPercent}%")
                    if (stats.brokenGearCount > 0) add("${stats.brokenGearCount} SHATTERED GEAR")
                    if (stats.mercenarySummary.isNotBlank()) add(stats.mercenarySummary)
                    if (stats.critChancePercent > 8) add("Crit ${stats.critChancePercent}%")
                    if (stats.armorPiercePercent > 0) add("Pierce ${stats.armorPiercePercent}%")
                    if (stats.lifestealPercent > 0) add("Leech ${stats.lifestealPercent}%")
                    if (stats.damageReductionPercent > 0) add("-${stats.damageReductionPercent}% DMG")
                }
            }
            if (activePerks.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activePerks.forEachIndexed { idx, chip ->
                        val isSet = idx == 0 && stats.setBonus.activeTier != null
                        val isWarning = chip.startsWith("WOUNDED") || chip.contains("SHATTERED")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isWarning -> MaterialTheme.colorScheme.errorContainer
                                isSet -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isWarning -> MaterialTheme.colorScheme.error
                                    isSet -> MaterialTheme.colorScheme.primary
                                    else -> ext.borderSubtle
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (isSet) {
                                    Icon(
                                        imageVector = Icons.Filled.WorkspacePremium,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = if (isSet || isWarning) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isWarning -> MaterialTheme.colorScheme.onErrorContainer
                                        isSet -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
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
private fun GearSlotMiniLine(prefix: String, item: InventoryItem?) {
    val accent = gearAccentColor(item?.resolvedRarity())
    val runeTag = item?.resolvedRune()?.symbol?.let { " $it" } ?: ""
    val name = item?.let {
        if (it.upgradeLevel > 0L) "+${it.upgradeLevel}$runeTag ${it.name}" else "${it.name}$runeTag"
    } ?: "Empty"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = accent.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, accent)
        ) {
            Text(
                text = prefix,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (item != null) Color(0xFFFFF8E7) else Color(0xFF9CA3AF),
            maxLines = 1
        )
    }
}

@Composable
fun AnimatedBattleStage(
    profile: HeroProfile,
    inventory: List<InventoryItem>,
    equippedPet: OwnedPet?,
    battle: BattleState,
    enemy: CombatEnemy,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val weapon = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.WEAPON } }
    val armor = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.ARMOR } }
    val relic = remember(inventory) { inventory.firstOrNull { it.equipped && it.resolvedSlot() == ItemSlot.RELIC } }

    val infinite = rememberInfiniteTransition(label = "battle_idle")
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "battle_breath"
    )
    val auraPulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "battle_aura"
    )

    // Turn-driven combat animation values
    val heroLunge = remember { Animatable(0f) }
    val slashProgress = remember { Animatable(0f) }
    val solarBeamAlpha = remember { Animatable(0f) }
    val heroGuardAlpha = remember { Animatable(0f) }
    val enemyRecoil = remember { Animatable(0f) }
    val enemyHitFlash = remember { Animatable(0f) }
    val enemyLunge = remember { Animatable(0f) }
    val heroHitFlash = remember { Animatable(0f) }
    val popupFloat = remember { Animatable(0f) }
    val popupAlpha = remember { Animatable(0f) }

    var displayedEvent by remember { mutableStateOf<BattleAnimEvent?>(null) }

    LaunchedEffect(battle.lastAnimEvent?.sequenceId) {
        val ev = battle.lastAnimEvent ?: return@LaunchedEffect
        displayedEvent = ev
        heroLunge.snapTo(0f)
        slashProgress.snapTo(0f)
        solarBeamAlpha.snapTo(0f)
        heroGuardAlpha.snapTo(0f)
        enemyRecoil.snapTo(0f)
        enemyHitFlash.snapTo(0f)
        enemyLunge.snapTo(0f)
        heroHitFlash.snapTo(0f)
        popupFloat.snapTo(0f)
        popupAlpha.snapTo(1f)

        // Phase 1: Hero action animation
        when (ev.heroAction) {
            BattleAnimAction.HERO_STRIKE -> {
                launch { heroLunge.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
                launch { slashProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
                delay(120)
                launch { enemyHitFlash.animateTo(1f, tween(90)) }
                launch { enemyRecoil.animateTo(1f, tween(140)) }
                delay(160)
                launch { heroLunge.animateTo(0f, tween(180)) }
                launch { enemyRecoil.animateTo(0f, tween(180)) }
                launch { enemyHitFlash.animateTo(0f, tween(200)) }
                launch { slashProgress.animateTo(0f, tween(140)) }
            }
            BattleAnimAction.HERO_SOLAR_SKILL,
            BattleAnimAction.HERO_ULTIMATE_COMBO -> {
                launch { heroLunge.animateTo(0.88f, tween(160)) }
                launch { solarBeamAlpha.animateTo(1f, tween(150)) }
                launch { slashProgress.animateTo(1f, tween(240)) }
                delay(130)
                launch { enemyHitFlash.animateTo(1f, tween(100)) }
                launch { enemyRecoil.animateTo(1.35f, tween(170)) }
                delay(230)
                launch { solarBeamAlpha.animateTo(0f, tween(200)) }
                launch { slashProgress.animateTo(0f, tween(160)) }
                launch { heroLunge.animateTo(0f, tween(180)) }
                launch { enemyRecoil.animateTo(0f, tween(180)) }
                launch { enemyHitFlash.animateTo(0f, tween(200)) }
            }
            BattleAnimAction.HERO_GUARD -> {
                launch { heroGuardAlpha.animateTo(1f, tween(180)) }
                if (ev.heroDamageDealt > 0) {
                    delay(100)
                    launch { enemyHitFlash.animateTo(0.8f, tween(100)) }
                    launch { enemyRecoil.animateTo(0.6f, tween(130)) }
                    delay(140)
                    launch { enemyHitFlash.animateTo(0f, tween(160)) }
                    launch { enemyRecoil.animateTo(0f, tween(160)) }
                } else {
                    delay(200)
                }
            }
            BattleAnimAction.HERO_ELIXIR -> {
                launch { heroGuardAlpha.animateTo(0.75f, tween(200)) }
                delay(240)
                launch { heroGuardAlpha.animateTo(0f, tween(200)) }
            }
            else -> {}
        }

        // Phase 2: Enemy counter-attack / ward animation
        if (ev.enemyAction == BattleAnimAction.ENEMY_ATTACK) {
            delay(80)
            launch { enemyLunge.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
            delay(110)
            if (ev.enemyWasGuarded) {
                launch { heroGuardAlpha.animateTo(1f, tween(120)) }
            } else {
                launch { heroHitFlash.animateTo(1f, tween(100)) }
            }
            if (ev.thornsReflected > 0) {
                launch { enemyHitFlash.animateTo(0.9f, tween(100)) }
            }
            delay(170)
            launch { enemyLunge.animateTo(0f, tween(180)) }
            launch { heroHitFlash.animateTo(0f, tween(220)) }
            launch { enemyHitFlash.animateTo(0f, tween(200)) }
            launch { heroGuardAlpha.animateTo(0f, tween(260)) }
        } else if (ev.enemyAction == BattleAnimAction.ENEMY_WARD) {
            delay(80)
            launch { enemyRecoil.animateTo(-0.35f, tween(180)) }
            delay(180)
            launch { enemyRecoil.animateTo(0f, tween(180)) }
            launch { heroGuardAlpha.animateTo(0f, tween(220)) }
        }

        // Float & fade combat callout numbers
        launch { popupFloat.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        delay(550)
        popupAlpha.animateTo(0f, tween(350))
    }

    val realmColors = when {
        battle.mode == BattleMode.WEEKLY_BOSS_RAID -> listOf(Color(0xFF280911), Color(0xFF4C1022), Color(0xFF230A10))
        battle.mode == BattleMode.DAILY_ELEMENTAL_RIFT -> listOf(Color(0xFF0E1D2A), Color(0xFF1F3C5A), Color(0xFF152536))
        battle.mode == BattleMode.ENDLESS_SPIRE -> listOf(Color(0xFF140D28), Color(0xFF2B144A), Color(0xFF1D1128))
        battle.realmNumber in listOf(2, 6) -> listOf(Color(0xFF1C1018), Color(0xFF3E1922), Color(0xFF281214))
        battle.realmNumber in listOf(3, 8) -> listOf(Color(0xFF0E1B2B), Color(0xFF1A344D), Color(0xFF142230))
        battle.realmNumber in listOf(4, 7) -> listOf(Color(0xFF24130D), Color(0xFF472013), Color(0xFF2E150E))
        battle.realmNumber >= 9 -> listOf(Color(0xFF1A142D), Color(0xFF3A2454), Color(0xFF2C1E10))
        else -> listOf(Color(0xFF171A26), Color(0xFF2C2334), Color(0xFF3B2416))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("animated_battle_stage"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, ext.borderStrong)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .background(Brush.verticalGradient(realmColors))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val groundY = size.height * 0.80f
                val w = size.width
                val h = size.height

                // Distant sunlit / spire backdrop crest
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x38FFB800), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.36f),
                        radius = w * 0.36f
                    ),
                    radius = w * 0.36f,
                    center = Offset(w * 0.5f, h * 0.36f)
                )

                // Arena floor
                drawRect(
                    color = Color(0xFF241A15),
                    topLeft = Offset(0f, groundY),
                    size = Size(w, h - groundY)
                )
                drawLine(
                    color = Color(0x88FFB800),
                    start = Offset(0f, groundY),
                    end = Offset(w, groundY),
                    strokeWidth = 2.dp.toPx()
                )

                // Positions with live spring lunges & recoils
                val heroBaseX = w * 0.26f
                val enemyBaseX = w * 0.74f
                val lungeDist = w * 0.25f

                val heroX = heroBaseX + (heroLunge.value * lungeDist) - (heroHitFlash.value * 10.dp.toPx())
                val enemyX = enemyBaseX - (enemyLunge.value * lungeDist) + (enemyRecoil.value * 18.dp.toPx())
                val heroBob = sin(breathPhase) * 3.dp.toPx()
                val enemyBob = sin(breathPhase + 2.0f) * 4.dp.toPx()

                // Pet companion beside hero
                if (equippedPet != null) {
                    drawPetSprite(
                        centerX = heroBaseX - 50.dp.toPx() + (heroLunge.value * 16.dp.toPx()),
                        groundY = groundY - 4.dp.toPx() + sin(breathPhase + 1.2f) * 4.dp.toPx(),
                        petType = equippedPet.resolvedType(),
                        scale = 0.85f
                    )
                }

                // Draw Hero (left)
                drawHeroSprite(
                    centerX = heroX,
                    groundY = groundY,
                    scale = 1.02f,
                    bobY = heroBob,
                    heroClass = profile.resolvedClass(),
                    weaponRarity = weapon?.resolvedRarity(),
                    armorRarity = armor?.resolvedRarity(),
                    relicRarity = relic?.resolvedRarity(),
                    prestigeLevel = profile.prestigeLevel.toInt(),
                    auraPulse = auraPulse,
                    facingRight = true,
                    swingProgress = slashProgress.value,
                    guardAlpha = heroGuardAlpha.value,
                    hitFlash = heroHitFlash.value
                )

                // Draw Enemy (right)
                drawEnemySprite(
                    centerX = enemyX,
                    groundY = groundY,
                    bobY = enemyBob,
                    artKey = enemy.artKey,
                    isBoss = enemy.isBoss,
                    hasShield = enemy.shield > 0,
                    hitFlash = enemyHitFlash.value,
                    auraPulse = auraPulse
                )

                // Slash Arc VFX
                if (slashProgress.value > 0.05f) {
                    val slashTint = gearAccentColor(weapon?.resolvedRarity() ?: ItemRarity.SOLAR)
                    drawArc(
                        color = slashTint.copy(alpha = (1f - slashProgress.value * 0.5f).coerceIn(0f, 1f)),
                        startAngle = -65f,
                        sweepAngle = 130f * slashProgress.value,
                        useCenter = false,
                        topLeft = Offset(enemyX - 58.dp.toPx(), groundY - 102.dp.toPx()),
                        size = Size(92.dp.toPx(), 96.dp.toPx()),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White.copy(alpha = (1f - slashProgress.value * 0.6f).coerceIn(0f, 1f)),
                        startAngle = -55f,
                        sweepAngle = 110f * slashProgress.value,
                        useCenter = false,
                        topLeft = Offset(enemyX - 52.dp.toPx(), groundY - 96.dp.toPx()),
                        size = Size(82.dp.toPx(), 86.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Solar Overdrive Beam VFX
                if (solarBeamAlpha.value > 0.02f) {
                    val beamStartY = groundY - 56.dp.toPx()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFFD166).copy(alpha = solarBeamAlpha.value),
                                Color(0xFFFF6B35).copy(alpha = solarBeamAlpha.value),
                                Color(0xFFFFF8E7).copy(alpha = solarBeamAlpha.value)
                            ),
                            startX = heroX + 20.dp.toPx(),
                            endX = enemyX
                        ),
                        start = Offset(heroX + 20.dp.toPx(), beamStartY),
                        end = Offset(enemyX, beamStartY - 8.dp.toPx()),
                        strokeWidth = 22.dp.toPx() * solarBeamAlpha.value,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = solarBeamAlpha.value),
                        start = Offset(heroX + 22.dp.toPx(), beamStartY),
                        end = Offset(enemyX, beamStartY - 8.dp.toPx()),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Top HUD Pills (Turn / Wave / Mode / Boss Phase Badge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xDD111318),
                    border = BorderStroke(1.dp, Color(0xFFFFB800))
                ) {
                    val waveTag = if (battle.totalWaves > 1) " • W${battle.currentWave}/${battle.totalWaves}" else ""
                    val stageTag = when (battle.mode) {
                        BattleMode.ENDLESS_SPIRE -> "SPIRE F${battle.spireFloor}$waveTag • T${battle.turnNumber}"
                        BattleMode.DAILY_ELEMENTAL_RIFT -> "DAILY RIFT$waveTag • T${battle.turnNumber}"
                        BattleMode.WEEKLY_BOSS_RAID -> "TITAN RAID • T${battle.turnNumber}"
                        BattleMode.REALM_CAMPAIGN -> "R${battle.realmNumber}-${battle.stageNumber}$waveTag • T${battle.turnNumber}"
                    }
                    Text(
                        text = stageTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color(0xFFFFF8E7),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                if (enemy.isBoss) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (enemy.phase >= 2) Color(0xDD991B1B) else Color(0xDDB91C1C),
                        border = BorderStroke(1.dp, Color(0xFFFFD166))
                    ) {
                        val bossBadge = buildString {
                            append(if (enemy.phase >= 2) "PHASE II ENRAGED" else "BOSS ${enemy.affinity.symbol}")
                            if (enemy.doomTurnsRemaining > 0) append(" • DOOM ${enemy.doomTurnsRemaining}T")
                        }
                        Text(
                            text = bossBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xDD1F2937),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD))
                    ) {
                        Text(
                            text = "${enemy.affinity.symbol} ${enemy.affinity.label.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Floating Combat Damage & Perk Callouts
            val ev = displayedEvent
            if (ev != null && popupAlpha.value > 0.05f) {
                val liftDp = (popupFloat.value * 26f).dp
                // Enemy damage popup (top-right)
                if (ev.heroDamageDealt > 0 || ev.thornsReflected > 0) {
                    val totalEnemyHit = ev.heroDamageDealt + ev.thornsReflected
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = (52.dp - liftDp).coerceAtLeast(36.dp), end = 20.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (ev.heroWasCrit) Color(0xEEFF6B00) else Color(0xDD111318),
                            border = BorderStroke(1.5.dp, if (ev.heroWasCrit) Color(0xFFFFF066) else Color(0xFFFFB800))
                        ) {
                            Text(
                                text = if (ev.heroWasCrit) "CRIT! -$totalEnemyHit" else "-$totalEnemyHit DMG",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (ev.perkCallouts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = ev.perkCallouts.take(2).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD166)
                            )
                        }
                    }
                }

                // Hero damage / heal popup (top-left)
                if (ev.enemyDamageDealt > 0 || ev.heroLifestealHeal > 0 || ev.heroElixirHeal > 0 || ev.heroTurnRegenHeal > 0) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = (52.dp - liftDp).coerceAtLeast(36.dp), start = 18.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (ev.enemyDamageDealt > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (ev.enemyWasGuarded) Color(0xDD1E3A8A) else Color(0xDD991B1B),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = if (ev.enemyWasGuarded) "BLOCKED -${ev.enemyDamageDealt}" else "-${ev.enemyDamageDealt} HP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        val totalHeal = ev.heroLifestealHeal + ev.heroElixirHeal + ev.heroTurnRegenHeal
                        if (totalHeal > 0) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xDD065F46)
                            ) {
                                Text(
                                    text = "+$totalHeal HP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA7F3D0),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Fighter Nameplates
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF8E7),
                        maxLines = 1
                    )
                    Text(
                        text = "LVL ${profile.level} ${profile.resolvedClass().displayTitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF93C5FD)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = enemy.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166),
                        maxLines = 1
                    )
                    Text(
                        text = "LVL ${enemy.level} • Shield ${enemy.shield}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeroSprite(
    centerX: Float,
    groundY: Float,
    scale: Float,
    bobY: Float,
    heroClass: HeroClass,
    weaponRarity: ItemRarity?,
    armorRarity: ItemRarity?,
    relicRarity: ItemRarity?,
    prestigeLevel: Int,
    auraPulse: Float,
    facingRight: Boolean,
    swingProgress: Float,
    guardAlpha: Float,
    hitFlash: Float = 0f
) {
    val dir = if (facingRight) 1f else -1f
    val u = 1.dp.toPx() * scale
    val armorColor = if (hitFlash > 0.2f) Color(0xFFFF5A5F) else gearAccentColor(armorRarity)
    val weaponColor = gearAccentColor(weaponRarity)
    val relicColor = gearAccentColor(relicRarity)

    // Ground shadow
    drawOval(
        color = Color(0x66000000),
        topLeft = Offset(centerX - 24 * u, groundY - 4 * u),
        size = Size(48 * u, 10 * u)
    )

    // Floating Relic Astrolabe Orb behind hero
    if (relicRarity != null) {
        val orbCenter = Offset(centerX - 22 * u * dir, groundY - 68 * u + bobY * 1.4f)
        drawCircle(
            color = relicColor.copy(alpha = 0.28f * auraPulse),
            radius = 15 * u,
            center = orbCenter
        )
        drawCircle(
            color = relicColor,
            radius = 6 * u,
            center = orbCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = 10 * u,
            center = orbCenter,
            style = Stroke(width = 1.5f * u)
        )
    }

    // Hero Cape (flowing behind)
    val capeColor = when (heroClass) {
        HeroClass.VANGUARD -> Color(0xFF991B1B)
        HeroClass.STRIDER -> Color(0xFF0F766E)
        HeroClass.SUNBLADE -> Color(0xFFB45309)
    }
    val capePath = Path().apply {
        moveTo(centerX - 8 * u * dir, groundY - 64 * u + bobY)
        lineTo(centerX - 26 * u * dir, groundY - 12 * u + bobY * 0.5f)
        lineTo(centerX - 6 * u * dir, groundY - 16 * u)
        close()
    }
    drawPath(capePath, color = capeColor)

    // Boots / Legs
    drawRoundRect(
        color = Color(0xFF1F2430),
        topLeft = Offset(centerX - 11 * u, groundY - 26 * u),
        size = Size(8 * u, 25 * u),
        cornerRadius = CornerRadius(3 * u, 3 * u)
    )
    drawRoundRect(
        color = Color(0xFF1F2430),
        topLeft = Offset(centerX + 3 * u, groundY - 26 * u),
        size = Size(8 * u, 25 * u),
        cornerRadius = CornerRadius(3 * u, 3 * u)
    )

    // Armored Torso (colored by equipped Armor rarity!)
    drawRoundRect(
        color = armorColor,
        topLeft = Offset(centerX - 16 * u, groundY - 66 * u + bobY),
        size = Size(32 * u, 42 * u),
        cornerRadius = CornerRadius(8 * u, 8 * u)
    )
    // Chest Emblem / Trim
    drawRoundRect(
        color = Color(0xFF1A1D26),
        topLeft = Offset(centerX - 11 * u, groundY - 58 * u + bobY),
        size = Size(22 * u, 16 * u),
        cornerRadius = CornerRadius(4 * u, 4 * u)
    )
    drawCircle(
        color = Color(0xFFFFD166),
        radius = 4.5f * u,
        center = Offset(centerX, groundY - 50 * u + bobY)
    )
    // Shoulder Pauldrons
    drawCircle(
        color = armorColor,
        radius = 8 * u,
        center = Offset(centerX - 16 * u, groundY - 60 * u + bobY)
    )
    drawCircle(
        color = armorColor,
        radius = 8 * u,
        center = Offset(centerX + 16 * u, groundY - 60 * u + bobY)
    )

    // Head & Class Helm
    val headY = groundY - 82 * u + bobY
    drawCircle(
        color = Color(0xFFF3D2B3),
        radius = 12 * u,
        center = Offset(centerX, headY)
    )
    // Helm / Hood by Class
    when (heroClass) {
        HeroClass.VANGUARD -> {
            drawArc(
                color = armorColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(centerX - 13 * u, headY - 14 * u),
                size = Size(26 * u, 22 * u)
            )
            // Golden crest plume
            drawLine(
                color = Color(0xFFFFD166),
                start = Offset(centerX, headY - 14 * u),
                end = Offset(centerX - 6 * u * dir, headY - 24 * u),
                strokeWidth = 3.5f * u,
                cap = StrokeCap.Round
            )
        }
        HeroClass.STRIDER -> {
            val hood = Path().apply {
                moveTo(centerX - 15 * u, headY)
                lineTo(centerX, headY - 18 * u)
                lineTo(centerX + 15 * u, headY)
                close()
            }
            drawPath(hood, color = Color(0xFF0F766E))
        }
        HeroClass.SUNBLADE -> {
            drawCircle(
                color = Color(0xFFFFB800).copy(alpha = 0.45f * auraPulse),
                radius = 16 * u,
                center = Offset(centerX, headY - 2 * u),
                style = Stroke(width = 2.5f * u)
            )
            drawArc(
                color = Color(0xFFFFD166),
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(centerX - 13 * u, headY - 13 * u),
                size = Size(26 * u, 18 * u),
                style = Stroke(width = 3 * u)
            )
        }
    }
    // Visor / Eyes
    drawCircle(
        color = Color(0xFF111318),
        radius = 2 * u,
        center = Offset(centerX + 5 * u * dir, headY - 1 * u)
    )

    // Prestige Solar Crown above head!
    if (prestigeLevel > 0) {
        val crownY = headY - 22 * u
        val crownPath = Path().apply {
            moveTo(centerX - 10 * u, crownY + 5 * u)
            lineTo(centerX - 8 * u, crownY - 4 * u)
            lineTo(centerX - 3 * u, crownY + 1 * u)
            lineTo(centerX, crownY - 7 * u)
            lineTo(centerX + 3 * u, crownY + 1 * u)
            lineTo(centerX + 8 * u, crownY - 4 * u)
            lineTo(centerX + 10 * u, crownY + 5 * u)
            close()
        }
        drawPath(crownPath, color = Color(0xFFFFD166))
        drawPath(crownPath, color = Color(0xFFFF6B00), style = Stroke(width = 1.2f * u))
    }

    // Equipped Weapon (glows and swings forward on strike!)
    val swingAngleOffset = swingProgress * 46 * u
    val hiltPos = Offset(centerX + 18 * u * dir, groundY - 48 * u + bobY)
    val tipPos = Offset(
        centerX + (38 * u + swingAngleOffset * 0.45f) * dir,
        groundY - 84 * u + bobY + swingAngleOffset
    )
    if (weaponRarity == ItemRarity.SOLAR || weaponRarity == ItemRarity.ULTRA_RARE) {
        drawLine(
            color = weaponColor.copy(alpha = 0.40f * auraPulse),
            start = hiltPos,
            end = tipPos,
            strokeWidth = 12 * u,
            cap = StrokeCap.Round
        )
    }
    drawLine(
        color = weaponColor,
        start = hiltPos,
        end = tipPos,
        strokeWidth = 5.5f * u,
        cap = StrokeCap.Round
    )
    // Crossguard
    drawLine(
        color = Color(0xFFFFD166),
        start = Offset(hiltPos.x - 5 * u, hiltPos.y + 3 * u),
        end = Offset(hiltPos.x + 5 * u, hiltPos.y - 3 * u),
        strokeWidth = 3.5f * u,
        cap = StrokeCap.Round
    )

    // Vanguard off-hand shield or active Aegis Guard dome
    if (heroClass == HeroClass.VANGUARD) {
        drawRoundRect(
            color = armorColor,
            topLeft = Offset(centerX + 6 * u * dir, groundY - 56 * u + bobY),
            size = Size(12 * u, 22 * u),
            cornerRadius = CornerRadius(4 * u, 4 * u)
        )
        drawRoundRect(
            color = Color(0xFFFFD166),
            topLeft = Offset(centerX + 6 * u * dir, groundY - 56 * u + bobY),
            size = Size(12 * u, 22 * u),
            cornerRadius = CornerRadius(4 * u, 4 * u),
            style = Stroke(width = 1.8f * u)
        )
    }

    if (guardAlpha > 0.05f) {
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.25f * guardAlpha),
            radius = 44 * u,
            center = Offset(centerX, groundY - 50 * u + bobY)
        )
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.90f * guardAlpha),
            radius = 44 * u,
            center = Offset(centerX, groundY - 50 * u + bobY),
            style = Stroke(width = 3 * u)
        )
    }
}

private fun DrawScope.drawPetSprite(
    centerX: Float,
    groundY: Float,
    petType: PetType,
    scale: Float
) {
    val u = 1.dp.toPx() * scale
    val accent = petAccentColor(petType)

    drawOval(
        color = Color(0x55000000),
        topLeft = Offset(centerX - 12 * u, groundY - 2 * u),
        size = Size(24 * u, 6 * u)
    )
    // Winged or grounded pet body
    val isFlying = petType == PetType.DUNE_FALCON || petType == PetType.SOLAR_PHOENIX
    val bodyY = if (isFlying) groundY - 34 * u else groundY - 14 * u

    if (isFlying) {
        val wingLeft = Path().apply {
            moveTo(centerX, bodyY)
            lineTo(centerX - 18 * u, bodyY - 10 * u)
            lineTo(centerX - 10 * u, bodyY + 4 * u)
            close()
        }
        val wingRight = Path().apply {
            moveTo(centerX, bodyY)
            lineTo(centerX + 18 * u, bodyY - 10 * u)
            lineTo(centerX + 10 * u, bodyY + 4 * u)
            close()
        }
        drawPath(wingLeft, color = accent.copy(alpha = 0.85f))
        drawPath(wingRight, color = accent.copy(alpha = 0.85f))
    }

    drawCircle(
        color = accent,
        radius = 10 * u,
        center = Offset(centerX, bodyY)
    )
    // Ears / Crest
    drawCircle(
        color = Color(0xFFFFF8E7),
        radius = 2.2f * u,
        center = Offset(centerX + 4 * u, bodyY - 2 * u)
    )
}

private fun DrawScope.drawEnemySprite(
    centerX: Float,
    groundY: Float,
    bobY: Float,
    artKey: EnemyArtKey,
    isBoss: Boolean,
    hasShield: Boolean,
    hitFlash: Float,
    auraPulse: Float
) {
    val bossScale = if (isBoss) 1.22f else 1.0f
    val u = 1.dp.toPx() * bossScale
    val baseTint = if (hitFlash > 0.25f) {
        Color(0xFFFFF8E7)
    } else if (artKey == EnemyArtKey.OBSIDIAN_GOLEM) {
        Color(0xFF475569)
    } else {
        Color(0xFFB45309)
    }
    val coreGlow = if (isBoss) Color(0xFFEF4444) else Color(0xFFFFB800)

    // Boss intimidation aura
    if (isBoss) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55EF4444), Color.Transparent),
                center = Offset(centerX, groundY - 52 * u + bobY),
                radius = 60 * u * auraPulse
            ),
            radius = 60 * u * auraPulse,
            center = Offset(centerX, groundY - 52 * u + bobY)
        )
    }

    // Shadow
    drawOval(
        color = Color(0x77000000),
        topLeft = Offset(centerX - 30 * u, groundY - 5 * u),
        size = Size(60 * u, 11 * u)
    )

    if (artKey == EnemyArtKey.OBSIDIAN_GOLEM) {
        // Massive Golem Legs
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX - 18 * u, groundY - 28 * u),
            size = Size(13 * u, 28 * u),
            cornerRadius = CornerRadius(4 * u, 4 * u)
        )
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX + 5 * u, groundY - 28 * u),
            size = Size(13 * u, 28 * u),
            cornerRadius = CornerRadius(4 * u, 4 * u)
        )
        // Monolith Torso
        drawRoundRect(
            color = baseTint,
            topLeft = Offset(centerX - 24 * u, groundY - 76 * u + bobY),
            size = Size(48 * u, 50 * u),
            cornerRadius = CornerRadius(10 * u, 10 * u)
        )
        // Heavy Stone Arms
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(centerX - 36 * u, groundY - 68 * u + bobY),
            size = Size(14 * u, 36 * u),
            cornerRadius = CornerRadius(5 * u, 5 * u)
        )
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(centerX + 22 * u, groundY - 68 * u + bobY),
            size = Size(14 * u, 36 * u),
            cornerRadius = CornerRadius(5 * u, 5 * u)
        )
        // Glowing Runic Core
        drawCircle(
            color = coreGlow,
            radius = 9 * u,
            center = Offset(centerX, groundY - 52 * u + bobY)
        )
        // Head & Glowing Eyes
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(centerX - 13 * u, groundY - 94 * u + bobY),
            size = Size(26 * u, 20 * u),
            cornerRadius = CornerRadius(5 * u, 5 * u)
        )
        drawCircle(color = coreGlow, radius = 3 * u, center = Offset(centerX - 6 * u, groundY - 84 * u + bobY))
        drawCircle(color = coreGlow, radius = 3 * u, center = Offset(centerX + 3 * u, groundY - 84 * u + bobY))
    } else {
        // Winged Gilded Wyvern
        val flyY = groundY - 56 * u + bobY * 1.6f
        val leftWing = Path().apply {
            moveTo(centerX, flyY)
            lineTo(centerX - 38 * u, flyY - 30 * u)
            lineTo(centerX - 24 * u, flyY + 8 * u)
            close()
        }
        val rightWing = Path().apply {
            moveTo(centerX, flyY)
            lineTo(centerX + 36 * u, flyY - 28 * u)
            lineTo(centerX + 26 * u, flyY + 10 * u)
            close()
        }
        drawPath(leftWing, color = Color(0xFF92400E))
        drawPath(rightWing, color = Color(0xFF92400E))
        // Wyvern Body
        drawOval(
            color = baseTint,
            topLeft = Offset(centerX - 20 * u, flyY - 14 * u),
            size = Size(40 * u, 28 * u)
        )
        // Horned Dragon Head facing left
        val headCenter = Offset(centerX - 22 * u, flyY - 12 * u)
        drawCircle(color = baseTint, radius = 12 * u, center = headCenter)
        drawLine(
            color = Color(0xFFFFD166),
            start = Offset(headCenter.x + 2 * u, headCenter.y - 10 * u),
            end = Offset(headCenter.x + 12 * u, headCenter.y - 22 * u),
            strokeWidth = 3 * u,
            cap = StrokeCap.Round
        )
        drawCircle(color = coreGlow, radius = 3 * u, center = Offset(headCenter.x - 4 * u, headCenter.y - 2 * u))
    }

    // Active Enemy Shield Barrier
    if (hasShield) {
        drawCircle(
            color = Color(0xFF60A5FA).copy(alpha = 0.22f),
            radius = 46 * u,
            center = Offset(centerX, groundY - 52 * u + bobY)
        )
        drawCircle(
            color = Color(0xFF93C5FD),
            radius = 46 * u,
            center = Offset(centerX, groundY - 52 * u + bobY),
            style = Stroke(width = 2.5f * u)
        )
    }
}

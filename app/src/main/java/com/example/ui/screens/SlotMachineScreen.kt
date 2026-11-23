package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HeroProfile
import com.example.data.model.OwnedPet
import com.example.domain.SlotMachineCatalog
import com.example.domain.SlotMachineUiState
import com.example.domain.SlotPrizeIconKind
import com.example.domain.SlotPrizeRarity
import com.example.domain.SlotSpinResult
import com.example.domain.SlotWheelPrize
import com.example.ui.components.SolstrideCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun SlotMachineScreen(
    profile: HeroProfile,
    equippedPet: OwnedPet?,
    bonusPetCareCharges: Int,
    slotState: SlotMachineUiState,
    expeditionLocked: Boolean,
    onSpinRequested: () -> Unit,
    onWheelPegTick: () -> Unit,
    onSpinRevealCompleted: (SlotSpinResult) -> Unit,
    onNavigateToPets: () -> Unit,
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    val prizes = SlotMachineCatalog.wheelPrizes
    val wedgeCount = prizes.size
    val sweepAngle = 360f / wedgeCount.toFloat()

    val wheelRotation = remember { Animatable(0f) }
    val leverPull = remember { Animatable(0f) }
    val burstProgress = remember { Animatable(0f) }

    var isAnimatingSpin by remember { mutableStateOf(false) }
    var lastHandledSpinId by remember { mutableLongStateOf(slotState.lastResult?.spinId ?: 0L) }
    var revealedResult by remember { mutableStateOf(slotState.lastResult) }

    // Synchronized 3-reel symbols above the spinning wheel
    var reel1Symbol by remember { mutableStateOf("🐾✨") }
    var reel2Symbol by remember { mutableStateOf("🪙") }
    var reel3Symbol by remember { mutableStateOf("🎁") }
    var reel1Locked by remember { mutableStateOf(true) }
    var reel2Locked by remember { mutableStateOf(true) }
    var reel3Locked by remember { mutableStateOf(true) }

    // Marquee light chase + idle shimmer
    val infinite = rememberInfiniteTransition(label = "slot_marquee")
    val marqueePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAnimatingSpin) 550 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_phase"
    )
    val shimmerPulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_pulse"
    )

    // Track peg crossings for tactile click + pointer kick
    var lastPegBucket by remember { mutableIntStateOf(0) }
    var pointerKick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(wheelRotation.value, isAnimatingSpin) {
        if (isAnimatingSpin) {
            val normalized = ((wheelRotation.value % 360f) + 360f) % 360f
            val bucket = floor((normalized / sweepAngle).toDouble()).toInt()
            if (bucket != lastPegBucket) {
                lastPegBucket = bucket
                pointerKick = 1f
                onWheelPegTick()
            } else if (pointerKick > 0f) {
                pointerKick = (pointerKick - 0.16f).coerceAtLeast(0f)
            }
        } else {
            pointerKick = 0f
        }
    }

    // Trigger physical spin animation whenever ViewModel emits a new spinId
    LaunchedEffect(slotState.lastResult?.spinId) {
        val result = slotState.lastResult ?: return@LaunchedEffect
        if (result.spinId == lastHandledSpinId) return@LaunchedEffect
        lastHandledSpinId = result.spinId
        isAnimatingSpin = true
        reel1Locked = false
        reel2Locked = false
        reel3Locked = false
        burstProgress.snapTo(0f)

        // Lever pull spring animation
        launch {
            leverPull.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
            leverPull.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }

        // Synchronized 3-reel rapid spin that locks staggered before wheel stops
        val reelJob = launch {
            val symbols = prizes.map { it.symbol }
            var step = 0
            while (isAnimatingSpin) {
                if (!reel1Locked) reel1Symbol = symbols[(step * 3) % symbols.size]
                if (!reel2Locked) reel2Symbol = symbols[(step * 5 + 2) % symbols.size]
                if (!reel3Locked) reel3Symbol = symbols[(step * 7 + 4) % symbols.size]
                step++
                delay(65L)
            }
        }
        launch {
            delay(1750L)
            reel1Symbol = result.prize.symbol
            reel1Locked = true
            onWheelPegTick()
            delay(650L)
            reel2Symbol = result.prize.symbol
            reel2Locked = true
            onWheelPegTick()
            delay(750L)
            reel3Symbol = result.prize.symbol
            reel3Locked = true
        }

        // Compute exact target rotation so `result.wedgeIndex` lands dead-center under 12 o'clock pointer (-90 deg)
        val currentAngle = wheelRotation.value
        val currentMod = ((currentAngle % 360f) + 360f) % 360f
        val wedgeCenterOffset = result.wedgeIndex * sweepAngle + (sweepAngle * 0.5f)
        // We draw wedge i starting at (-90f + i*sweepAngle + rotation).
        // For wedge i's center to align at -90f (12 o'clock), (rotation + wedgeCenterOffset) mod 360 == 0
        // => desiredMod = (360f - wedgeCenterOffset) mod 360.
        val desiredMod = ((360f - wedgeCenterOffset) % 360f + 360f) % 360f
        val deltaToTarget = ((desiredMod - currentMod) + 360f) % 360f
        val fullRotations = 6f * 360f
        val targetAngle = currentAngle + fullRotations + deltaToTarget

        wheelRotation.animateTo(
            targetValue = targetAngle,
            animationSpec = tween(
                durationMillis = 3350,
                easing = CubicBezierEasing(0.10f, 0.86f, 0.20f, 1.0f)
            )
        )

        reel1Symbol = result.prize.symbol
        reel2Symbol = result.prize.symbol
        reel3Symbol = result.prize.symbol
        reel1Locked = true
        reel2Locked = true
        reel3Locked = true
        isAnimatingSpin = false
        reelJob.cancel()

        revealedResult = result
        onSpinRevealCompleted(result)

        // Celebration shockwave burst
        burstProgress.snapTo(0.01f)
        burstProgress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }

    val canAfford = profile.solCoins >= SlotMachineCatalog.SPIN_COST_SOL_COINS
    val spinEnabled = canAfford && !isAnimatingSpin && !expeditionLocked

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("slot_machine_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Vault & Bonus Care Telemetry
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1A1226),
                border = BorderStroke(1.5.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2B143D), Color(0xFF1E132F), Color(0xFF3A2010))
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFFBBF24))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Casino,
                                    contentDescription = "Sunhaven Fortune Wheel",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "SUNHAVEN FORTUNE WHEEL",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFF8E1),
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "50 Sol Coins / Spin • 4% Ultra Rare Extra Pet Care",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF120C1C),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = "SOL COINS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD6C7E8),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "🪙 ${profile.solCoins}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24),
                                    modifier = Modifier.testTag("slot_sol_coins_balance")
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF120C1C),
                            border = BorderStroke(
                                1.dp,
                                if (bonusPetCareCharges > 0) Color(0xFFEC4899) else Color(0xFF6B4E8A)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = "EXTRA PET CARE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF9A8D4),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "🐾 +$bonusPetCareCharges Bonus",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (bonusPetCareCharges > 0) Color(0xFFF472B6) else Color(0xFFE9D5FF),
                                    modifier = Modifier.testTag("slot_bonus_pet_care_count")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Gilded Slot Cabinet & Spinning Wheel Centerpiece
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF160E24),
                border = BorderStroke(2.dp, Color(0xFFD97706))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF28133D), Color(0xFF150D22), Color(0xFF23140E))
                            )
                        )
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Synchronized 3-Reel Mechanical Slot Window
                    SlotThreeReelHeader(
                        reel1 = reel1Symbol,
                        reel2 = reel2Symbol,
                        reel3 = reel3Symbol,
                        reel1Locked = reel1Locked,
                        reel2Locked = reel2Locked,
                        reel3Locked = reel3Locked,
                        isUltraRareGlow = revealedResult?.isUltraRare == true && !isAnimatingSpin,
                        shimmerAlpha = shimmerPulse
                    )

                    // Spinning Fortune Wheel + Animated Brass Pull Lever
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .testTag("slot_spinning_wheel_canvas"),
                            contentAlignment = Alignment.Center
                        ) {
                            SpinningLootWheelCanvas(
                                prizes = prizes,
                                rotationDegrees = wheelRotation.value,
                                marqueePhase = marqueePhase,
                                pointerKick = pointerKick,
                                burstProgress = burstProgress.value,
                                isUltraRareWin = revealedResult?.isUltraRare == true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Interactive Brass Pull Lever
                        SlotPullLeverGraphic(
                            pullFraction = leverPull.value,
                            enabled = spinEnabled,
                            onPull = {
                                if (spinEnabled) onSpinRequested()
                            },
                            modifier = Modifier
                                .width(34.dp)
                                .height(160.dp)
                                .testTag("slot_machine_pull_lever")
                        )
                    }

                    // Primary Spin Action Button
                    Button(
                        onClick = onSpinRequested,
                        enabled = spinEnabled,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color(0xFF1E1204),
                            disabledContainerColor = Color(0xFF3E2F4D),
                            disabledContentColor = Color(0xFFBBA9CE)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("slot_machine_spin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isAnimatingSpin -> "SPINNING FORTUNE WHEEL…"
                                expeditionLocked -> "HERO ON EXPEDITION — WAIT TO SPIN"
                                !canAfford -> "NEED 50 SOL COINS (HAVE ${profile.solCoins})"
                                else -> "SPIN WHEEL • 50 SOL COINS"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (!canAfford && !isAnimatingSpin) {
                        Text(
                            text = "Earn Sol Coins from Battles, Watch Sleep, Quests, and Expeditions.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFCD34D),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Animated Loot Reveal Banner
        if (revealedResult != null && !isAnimatingSpin) {
            val res = revealedResult!!
            val accentColor = Color(res.prize.rarity.badgeColorHex)
            item(key = "reveal_${res.spinId}") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.92f)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slot_reveal_result_card"),
                        shape = RoundedCornerShape(20.dp),
                        color = if (res.isUltraRare) Color(0xFF360B28) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (res.isUltraRare) 2.5.dp else 1.5.dp, accentColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = accentColor.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, accentColor)
                                ) {
                                    Text(
                                        text = if (res.isUltraRare) "★ ULTRA RARE JACKPOT ★" else res.prize.rarity.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accentColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = res.prize.symbol,
                                    fontSize = 22.sp
                                )
                            }

                            Text(
                                text = res.headline,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (res.isUltraRare) Color(0xFFFFF1F2) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = res.detailLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (res.isUltraRare) Color(0xFFFBCFE8) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (res.bonusPetCareGranted > 0) {
                                Button(
                                    onClick = onNavigateToPets,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEC4899),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("slot_go_to_pets_bonus_care_button")
                                ) {
                                    Icon(Icons.Filled.Pets, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (equippedPet != null) {
                                            "Use Extra Care on ${equippedPet.resolvedForm().formName} →"
                                        } else {
                                            "Visit Pet Sanctuary →"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (res.awardedItemName != null || res.awardedChestLabel != null) {
                                OutlinedButton(
                                    onClick = onNavigateToVault,
                                    border = BorderStroke(1.5.dp, accentColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("slot_go_to_vault_button")
                                ) {
                                    Icon(Icons.Filled.Backpack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Inspect Loot in Hero Vault →", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ultra Rare Spotlight + Full Transparent Drop Rate Table
        item {
            SolstrideCard(
                modifier = Modifier.fillMaxWidth(),
                strongBorder = true
            ) {
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
                            imageVector = Icons.Filled.Stars,
                            contentDescription = "Wheel Loot Odds",
                            tint = Color(0xFFEC4899),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Wheel Loot & Drop Rates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEC4899).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "4% Ultra Rare Care",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEC4899),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Extra Pet Care charges bypass the 4/4 daily limit in both the Pet Sanctuary and the Home Screen Pet Widget — saved until you use them!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Sort Ultra Rare & Solar to top of odds table for clear scanning
                val sortedOdds = remember {
                    prizes.sortedBy { it.weightOutOf1000 }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sortedOdds.forEach { prize ->
                        val rarityColor = Color(prize.rarity.badgeColorHex)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (prize.rarity == SlotPrizeRarity.ULTRA_RARE) {
                                rarityColor.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                if (prize.rarity == SlotPrizeRarity.ULTRA_RARE) 1.3.dp else 1.dp,
                                if (prize.rarity == SlotPrizeRarity.ULTRA_RARE) rarityColor else ext.borderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = rarityColor.copy(alpha = 0.22f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = prize.symbol, fontSize = 14.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = prize.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = prize.rewardSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = prize.probabilityPctText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = rarityColor
                                    )
                                    Text(
                                        text = prize.rarity.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = rarityColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Session Spin History
        if (slotState.recentHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Spins (${slotState.totalSpinsThisSession} total)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(slotState.recentHistory, key = { it.spinId }) { entry ->
                val accent = Color(entry.prize.rarity.badgeColorHex)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = entry.prize.symbol, fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.headline,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.detailLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = entry.prize.rarity.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotThreeReelHeader(
    reel1: String,
    reel2: String,
    reel3: String,
    reel1Locked: Boolean,
    reel2Locked: Boolean,
    reel3Locked: Boolean,
    isUltraRareGlow: Boolean,
    shimmerAlpha: Float
) {
    val frameColor = if (isUltraRareGlow) Color(0xFFEC4899) else Color(0xFFF59E0B)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0E0817),
        border = BorderStroke(1.8.dp, frameColor.copy(alpha = shimmerAlpha))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                reel1 to reel1Locked,
                reel2 to reel2Locked,
                reel3 to reel3Locked
            ).forEach { (symbol, locked) ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (locked) Color(0xFF23163B) else Color(0xFF19102A),
                    border = BorderStroke(
                        width = if (locked) 1.5.dp else 1.dp,
                        color = if (locked) frameColor.copy(alpha = 0.85f) else Color(0xFF4C346B)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = symbol,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotPullLeverGraphic(
    pullFraction: Float,
    enabled: Boolean,
    onPull: () -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onPull)
    ) {
        val w = size.width
        val h = size.height
        val baseCenterY = h * 0.68f

        // Brass mount bracket
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFF78350F))),
            topLeft = Offset(0f, baseCenterY - 20.dp.toPx()),
            size = Size(14.dp.toPx(), 40.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Lever arm & glowing ruby/gold knob animating from top to bottom
        val startX = 11.dp.toPx()
        val knobX = w * 0.66f
        val knobY = (h * 0.14f) + (h * 0.62f * pullFraction)
        drawLine(
            color = if (enabled) Color(0xFFFDE68A) else Color(0xFF7C6F8C),
            start = Offset(startX, baseCenterY),
            end = Offset(knobX, knobY),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Knob shadow & glossy highlight
        val knobRadius = 11.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (enabled) {
                    listOf(Color(0xFFFF80AB), Color(0xFFEC4899), Color(0xFF831843))
                } else {
                    listOf(Color(0xFF8B7D9E), Color(0xFF4B3D5E))
                },
                center = Offset(knobX - 3.dp.toPx(), knobY - 3.dp.toPx()),
                radius = knobRadius * 1.4f
            ),
            radius = knobRadius,
            center = Offset(knobX, knobY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = 3.2.dp.toPx(),
            center = Offset(knobX - 3.5.dp.toPx(), knobY - 3.5.dp.toPx())
        )
    }
}

@Composable
private fun SpinningLootWheelCanvas(
    prizes: List<SlotWheelPrize>,
    rotationDegrees: Float,
    marqueePhase: Float,
    pointerKick: Float,
    burstProgress: Float,
    isUltraRareWin: Boolean,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val radius = diameter * 0.455f
        val center = Offset(size.width * 0.5f, size.height * 0.52f)
        val wedgeCount = prizes.size
        val sweep = 360f / wedgeCount.toFloat()

        // Outer ambient halo glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isUltraRareWin && burstProgress in 0.01f..0.99f) {
                        Color(0xFFEC4899).copy(alpha = 0.45f)
                    } else {
                        Color(0xFFF59E0B).copy(alpha = 0.28f)
                    },
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.18f
            ),
            radius = radius * 1.18f,
            center = center
        )

        // Outer metallic gold cabinet bezel
        val bezelRadius = radius + 12.dp.toPx()
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFFDE68A),
                    Color(0xFFD97706),
                    Color(0xFF92400E),
                    Color(0xFFF59E0B),
                    Color(0xFFFDE68A)
                ),
                center = center
            ),
            radius = bezelRadius,
            center = center
        )
        drawCircle(
            color = Color(0xFF1F122E),
            radius = radius + 3.dp.toPx(),
            center = center
        )

        // 16 chasing marquee bulbs along bezel
        val bulbCount = 16
        val bulbRingR = radius + 7.5.dp.toPx()
        for (i in 0 until bulbCount) {
            val angleRad = (i * (360f / bulbCount) - 90f) * (PI / 180.0)
            val bx = center.x + (bulbRingR * cos(angleRad)).toFloat()
            val by = center.y + (bulbRingR * sin(angleRad)).toFloat()
            val lit = ((i + marqueePhase.toInt()) % 2 == 0)
            val bulbColor = when {
                isUltraRareWin && burstProgress in 0.01f..0.99f -> if (lit) Color(0xFFFF80AB) else Color(0xFFFBBF24)
                lit -> Color(0xFFFFFBEB)
                else -> Color(0xFFB45309)
            }
            drawCircle(
                color = bulbColor,
                radius = if (lit) 3.4.dp.toPx() else 2.4.dp.toPx(),
                center = Offset(bx, by)
            )
        }

        // Rotating Wheel Surface
        rotate(degrees = rotationDegrees, pivot = center) {
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            prizes.forEachIndexed { idx, prize ->
                val startAngle = -90f + idx * sweep
                val wedgeColorStart = Color(prize.rarity.wedgeStartHex)
                val wedgeColorEnd = Color(prize.rarity.wedgeEndHex)

                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(wedgeColorStart, wedgeColorEnd),
                        center = center,
                        radius = radius
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize
                )

                // Shimmering highlight border for Ultra Rare wedges
                if (prize.rarity == SlotPrizeRarity.ULTRA_RARE) {
                    drawArc(
                        color = Color(0xFFFFD1E8).copy(alpha = 0.72f),
                        startAngle = startAngle + 1.2f,
                        sweepAngle = sweep - 2.4f,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Golden divider spoke
                val spokeRad = startAngle * (PI / 180.0)
                val sx = center.x + (radius * cos(spokeRad)).toFloat()
                val sy = center.y + (radius * sin(spokeRad)).toFloat()
                drawLine(
                    color = Color(0xFFFBBF24).copy(alpha = 0.85f),
                    start = center,
                    end = Offset(sx, sy),
                    strokeWidth = 1.6.dp.toPx()
                )

                // Draw custom loot icon & label oriented along wedge midline
                val midAngleDeg = startAngle + sweep * 0.5f
                val midRad = midAngleDeg * (PI / 180.0)
                val iconDist = radius * 0.72f
                val ix = center.x + (iconDist * cos(midRad)).toFloat()
                val iy = center.y + (iconDist * sin(midRad)).toFloat()

                drawWedgeLootGraphic(
                    center = Offset(ix, iy),
                    iconSize = radius * 0.145f,
                    kind = prize.iconKind,
                    accent = Color(prize.rarity.badgeColorHex)
                )

                // Radial short text label
                val labelDist = radius * 0.45f
                val lx = center.x + (labelDist * cos(midRad)).toFloat()
                val ly = center.y + (labelDist * sin(midRad)).toFloat()
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(lx, ly)
                    canvas.nativeCanvas.rotate(midAngleDeg + 90f)
                    labelPaint.color = prize.rarity.textColorHex.toInt()
                    labelPaint.textSize = (radius * 0.088f).coerceIn(18f, 30f)
                    canvas.nativeCanvas.drawText(prize.shortWheelLabel, 0f, 4f, labelPaint)
                    canvas.nativeCanvas.restore()
                }
            }

            // Perimeter brass pegs at every wedge seam
            for (i in 0 until wedgeCount) {
                val pegRad = (-90f + i * sweep) * (PI / 180.0)
                val px = center.x + ((radius - 3.dp.toPx()) * cos(pegRad)).toFloat()
                val py = center.y + ((radius - 3.dp.toPx()) * sin(pegRad)).toFloat()
                drawCircle(
                    color = Color(0xFFFFFBEB),
                    radius = 2.8.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Center Ornate Solar Hub Medallion (stationary)
        val hubRadius = radius * 0.21f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF8E1), Color(0xFFF59E0B), Color(0xFF92400E)),
                center = center,
                radius = hubRadius
            ),
            radius = hubRadius,
            center = center
        )
        drawCircle(
            color = Color(0xFF1F122E),
            radius = hubRadius * 0.72f,
            center = center
        )
        // Mini 8-ray sunburst inside hub
        for (r in 0 until 8) {
            val rayRad = (r * 45f) * (PI / 180.0)
            drawLine(
                color = Color(0xFFFBBF24),
                start = center,
                end = Offset(
                    center.x + (hubRadius * 0.56f * cos(rayRad)).toFloat(),
                    center.y + (hubRadius * 0.56f * sin(rayRad)).toFloat()
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color(0xFFFFF8E1),
            radius = hubRadius * 0.24f,
            center = center
        )

        // Top 12 o'clock Brass Flapper Pointer (kicks on peg crossing)
        val pointerTipY = center.y - radius + 12.dp.toPx()
        val pointerBaseY = center.y - bezelRadius - 4.dp.toPx()
        rotate(degrees = -pointerKick * 18f, pivot = Offset(center.x, pointerBaseY)) {
            val pointerPath = Path().apply {
                moveTo(center.x, pointerTipY)
                lineTo(center.x - 11.dp.toPx(), pointerBaseY)
                lineTo(center.x + 11.dp.toPx(), pointerBaseY)
                close()
            }
            drawPath(
                path = pointerPath,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFF59E0B), Color(0xFFB45309)))
            )
            drawPath(
                path = pointerPath,
                color = Color(0xFF451A03),
                style = Stroke(width = 1.8.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFEC4899),
                radius = 4.dp.toPx(),
                center = Offset(center.x, pointerBaseY + 2.dp.toPx())
            )
        }

        // Win Celebration Particle Burst
        if (burstProgress in 0.01f..0.99f) {
            val alpha = (1f - burstProgress).coerceIn(0f, 1f)
            val burstColors = if (isUltraRareWin) {
                listOf(Color(0xFFEC4899), Color(0xFFFBBF24), Color(0xFF38BDF8), Color(0xFFA855F7))
            } else {
                listOf(Color(0xFFFBBF24), Color(0xFFFFF8E1), Color(0xFF34D399))
            }
            val particleCount = if (isUltraRareWin) 24 else 14
            for (p in 0 until particleCount) {
                val angle = (p * (360f / particleCount)) * (PI / 180.0)
                val dist = radius * (0.28f + burstProgress * 0.92f)
                val px = center.x + (dist * cos(angle)).toFloat()
                val py = center.y + (dist * sin(angle)).toFloat()
                drawCircle(
                    color = burstColors[p % burstColors.size].copy(alpha = alpha),
                    radius = (if (isUltraRareWin) 5.dp.toPx() else 3.8.dp.toPx()) * (1.2f - burstProgress * 0.5f),
                    center = Offset(px, py)
                )
            }
            drawCircle(
                color = (if (isUltraRareWin) Color(0xFFEC4899) else Color(0xFFFBBF24)).copy(alpha = alpha * 0.65f),
                radius = radius * (0.25f + burstProgress * 0.88f),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

private fun DrawScope.drawWedgeLootGraphic(
    center: Offset,
    iconSize: Float,
    kind: SlotPrizeIconKind,
    accent: Color
) {
    // Backing medallion disc
    drawCircle(
        color = Color(0xAA0E0817),
        radius = iconSize * 0.96f,
        center = center
    )
    drawCircle(
        color = accent.copy(alpha = 0.8f),
        radius = iconSize * 0.96f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    when (kind) {
        SlotPrizeIconKind.PET_CARE_DOUBLE, SlotPrizeIconKind.PET_CARE_SINGLE -> {
            // Paw print with golden star halo
            val pawColor = if (kind == SlotPrizeIconKind.PET_CARE_DOUBLE) Color(0xFFFFD1E8) else Color(0xFFF9A8D4)
            drawCircle(color = pawColor, radius = iconSize * 0.30f, center = Offset(center.x, center.y + iconSize * 0.16f))
            drawCircle(color = pawColor, radius = iconSize * 0.15f, center = Offset(center.x - iconSize * 0.34f, center.y - iconSize * 0.12f))
            drawCircle(color = pawColor, radius = iconSize * 0.16f, center = Offset(center.x - iconSize * 0.12f, center.y - iconSize * 0.32f))
            drawCircle(color = pawColor, radius = iconSize * 0.16f, center = Offset(center.x + iconSize * 0.12f, center.y - iconSize * 0.32f))
            drawCircle(color = pawColor, radius = iconSize * 0.15f, center = Offset(center.x + iconSize * 0.34f, center.y - iconSize * 0.12f))
        }
        SlotPrizeIconKind.COIN_JACKPOT -> {
            drawCircle(color = Color(0xFFFBBF24), radius = iconSize * 0.56f, center = center)
            drawCircle(color = Color(0xFF92400E), radius = iconSize * 0.38f, center = center, style = Stroke(width = 1.6.dp.toPx()))
            drawCircle(color = Color(0xFFFFFBEB), radius = iconSize * 0.16f, center = center)
        }
        SlotPrizeIconKind.LOOT_CHEST -> {
            val w = iconSize * 1.1f
            val h = iconSize * 0.8f
            drawRoundRect(
                color = Color(0xFFA855F7),
                topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawLine(
                color = Color(0xFFFBBF24),
                start = Offset(center.x - w / 2f, center.y - h * 0.05f),
                end = Offset(center.x + w / 2f, center.y - h * 0.05f),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = Color(0xFFFFFBEB), radius = iconSize * 0.14f, center = Offset(center.x, center.y - h * 0.05f))
        }
        SlotPrizeIconKind.MYTHIC_CORE -> {
            val gem = Path().apply {
                moveTo(center.x, center.y - iconSize * 0.62f)
                lineTo(center.x + iconSize * 0.50f, center.y)
                lineTo(center.x, center.y + iconSize * 0.62f)
                lineTo(center.x - iconSize * 0.50f, center.y)
                close()
            }
            drawPath(gem, color = Color(0xFF38BDF8))
            drawPath(gem, color = Color(0xFFFFFBEB), style = Stroke(width = 1.5.dp.toPx()))
        }
        SlotPrizeIconKind.EPIC_BLADE -> {
            drawLine(
                color = Color(0xFFE9D5FF),
                start = Offset(center.x - iconSize * 0.45f, center.y + iconSize * 0.45f),
                end = Offset(center.x + iconSize * 0.48f, center.y - iconSize * 0.48f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFBBF24),
                start = Offset(center.x - iconSize * 0.34f, center.y + iconSize * 0.06f),
                end = Offset(center.x - iconSize * 0.04f, center.y + iconSize * 0.36f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        SlotPrizeIconKind.FORGE_INGOT -> {
            val bar = Path().apply {
                moveTo(center.x - iconSize * 0.54f, center.y + iconSize * 0.32f)
                lineTo(center.x - iconSize * 0.34f, center.y - iconSize * 0.30f)
                lineTo(center.x + iconSize * 0.34f, center.y - iconSize * 0.30f)
                lineTo(center.x + iconSize * 0.54f, center.y + iconSize * 0.32f)
                close()
            }
            drawPath(bar, color = Color(0xFF93C5FD))
            drawPath(bar, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.3.dp.toPx()))
        }
        SlotPrizeIconKind.ALCHEMIST_VIAL -> {
            drawCircle(color = Color(0xFF34D399), radius = iconSize * 0.42f, center = Offset(center.x, center.y + iconSize * 0.14f))
            drawRect(
                color = Color(0xFFD1FAE5),
                topLeft = Offset(center.x - iconSize * 0.14f, center.y - iconSize * 0.48f),
                size = Size(iconSize * 0.28f, iconSize * 0.34f)
            )
        }
        SlotPrizeIconKind.PET_FEAST -> {
            drawOval(
                color = Color(0xFFFB923C),
                topLeft = Offset(center.x - iconSize * 0.48f, center.y - iconSize * 0.30f),
                size = Size(iconSize * 0.96f, iconSize * 0.60f)
            )
            drawCircle(color = Color(0xFFFFFBEB), radius = iconSize * 0.15f, center = Offset(center.x + iconSize * 0.44f, center.y))
        }
        SlotPrizeIconKind.SUNSHARDS -> {
            val star = Path().apply {
                for (i in 0 until 8) {
                    val r = if (i % 2 == 0) iconSize * 0.58f else iconSize * 0.24f
                    val a = (i * 45f - 90f) * (PI / 180.0)
                    val px = center.x + (r * cos(a)).toFloat()
                    val py = center.y + (r * sin(a)).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(star, color = Color(0xFFFDE047))
        }
    }
}

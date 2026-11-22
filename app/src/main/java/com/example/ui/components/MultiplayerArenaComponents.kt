package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HeroAvatarConfig
import com.example.data.model.HeroClass
import com.example.data.model.ItemRarity
import com.example.data.model.MultiplayerDuel
import com.example.data.model.MultiplayerPetPlaydate
import com.example.data.model.PetPlayActivity
import com.example.data.model.PetType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a real player's customized 2D Character alongside their equipped Companion Pet (if any).
 */
@Composable
fun MultiplayerHeroPetPreviewCanvas(
    heroClass: HeroClass,
    avatar: HeroAvatarConfig,
    prestigeLevel: Int,
    petType: PetType?,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "mp_hero_pet_preview")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "mp_preview_phase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mp_preview_pulse"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1D182A), Color(0xFF2E1E16))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val groundY = h * 0.84f

            // Warm sunlit floor glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44FFB800), Color.Transparent),
                    center = Offset(w * 0.5f, groundY),
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = Offset(w * 0.5f, groundY)
            )

            val heroX = if (petType != null) w * 0.36f else w * 0.50f
            drawHeroSprite(
                centerX = heroX,
                groundY = groundY,
                scale = 0.72f,
                bobY = sin(phase) * 2.dp.toPx(),
                heroClass = heroClass,
                avatar = avatar,
                weaponRarity = ItemRarity.SOLAR,
                prestigeLevel = prestigeLevel,
                auraPulse = pulse,
                facingRight = true
            )

            if (petType != null) {
                val petBob = sin(phase * 1.4f + 0.8f) * 3.dp.toPx()
                drawPetSprite(
                    centerX = w * 0.72f,
                    groundY = groundY + petBob * 0.4f,
                    petType = petType,
                    scale = 0.88f
                )
            }
        }
    }
}

/**
 * Compact circular mini-portrait for chat senders.
 */
@Composable
fun ChatSenderMiniAvatar(
    heroClass: HeroClass,
    avatar: HeroAvatarConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF221B30), Color(0xFF382418)))
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHeroSprite(
                centerX = size.width * 0.5f,
                groundY = size.height * 0.94f,
                scale = 0.34f,
                bobY = 0f,
                heroClass = heroClass,
                avatar = avatar,
                weaponRarity = ItemRarity.RARE,
                prestigeLevel = 0,
                auraPulse = 0.85f,
                facingRight = true
            )
        }
    }
}

/**
 * Animated 2D PvP Hero Duel Stage showing both duelists, their pets, guard shields, and turn glow.
 */
@Composable
fun MultiplayerDuelArenaCanvas(
    duel: MultiplayerDuel,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "pvp_duel_stage")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "duel_phase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "duel_pulse"
    )

    val challengerPet = duel.resolvedChallengerPet()
    val opponentPet = duel.resolvedOpponentPet()
    val isChallengerTurn = duel.status == "ACTIVE" && duel.turnUid == duel.challengerUid
    val isOpponentTurn = duel.status == "ACTIVE" && duel.turnUid == duel.opponentUid

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(172.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_sunhaven_plaza),
            contentDescription = "Sunhaven PvP Duel Arena backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x88120E1D), Color(0x5518121E), Color(0xBB160E0A))
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val groundY = h * 0.82f

            // Center clash solar sigil
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55FFB800), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.54f),
                    radius = 48.dp.toPx() * pulse
                ),
                radius = 48.dp.toPx() * pulse,
                center = Offset(w * 0.5f, h * 0.54f)
            )

            // Active turn spotlight ring
            if (isChallengerTurn) {
                drawCircle(
                    color = Color(0xAAFFD166).copy(alpha = 0.45f * pulse),
                    radius = 34.dp.toPx(),
                    center = Offset(w * 0.27f, groundY - 22.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            } else if (isOpponentTurn) {
                drawCircle(
                    color = Color(0xAAFF7B54).copy(alpha = 0.45f * pulse),
                    radius = 34.dp.toPx(),
                    center = Offset(w * 0.73f, groundY - 22.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Challenger (Left, facing right)
            val cBob = sin(phase) * 2.5.dp.toPx()
            drawHeroSprite(
                centerX = w * 0.27f,
                groundY = groundY,
                scale = 0.78f,
                bobY = cBob,
                heroClass = duel.resolvedChallengerClass(),
                avatar = duel.resolvedChallengerAvatar(),
                weaponRarity = ItemRarity.SOLAR,
                prestigeLevel = 0,
                auraPulse = pulse,
                facingRight = true
            )
            if (duel.challengerGuard) {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.7f * pulse),
                    radius = 28.dp.toPx(),
                    center = Offset(w * 0.27f, groundY - 26.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            if (challengerPet != null) {
                drawPetSprite(
                    centerX = w * 0.12f,
                    groundY = groundY + sin(phase * 1.4f) * 2.dp.toPx(),
                    petType = challengerPet,
                    scale = 0.74f
                )
            }

            // Opponent (Right, facing left)
            val oBob = sin(phase + 1.6f) * 2.5.dp.toPx()
            drawHeroSprite(
                centerX = w * 0.73f,
                groundY = groundY,
                scale = 0.78f,
                bobY = oBob,
                heroClass = duel.resolvedOpponentClass(),
                avatar = duel.resolvedOpponentAvatar(),
                weaponRarity = ItemRarity.RARE,
                prestigeLevel = 0,
                auraPulse = pulse,
                facingRight = false
            )
            if (duel.opponentGuard) {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.7f * pulse),
                    radius = 28.dp.toPx(),
                    center = Offset(w * 0.73f, groundY - 26.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            if (opponentPet != null) {
                drawPetSprite(
                    centerX = w * 0.88f,
                    groundY = groundY + sin(phase * 1.4f + 1.2f) * 2.dp.toPx(),
                    petType = opponentPet,
                    scale = 0.74f
                )
            }
        }
    }
}

/**
 * Animated 2D Sunhaven Pet Park canvas where two real players' companion pets play together!
 */
@Composable
fun PetPlaydateParkCanvas(
    playdate: MultiplayerPetPlaydate,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "pet_park_anim")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "park_phase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "park_pulse"
    )

    val hostPet = playdate.resolvedHostPet()
    val guestPet = playdate.resolvedGuestPet()
    val activity = playdate.resolvedActivity()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(166.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_sunhaven_plaza),
            contentDescription = "Sunhaven Pet Park courtyard",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x5518221A), Color(0x33202A18), Color(0x88151D12))
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val baseGroundY = h * 0.80f

            // Sunlit meadow glow in the center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55FFE082), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.62f),
                    radius = w * 0.36f * pulse
                ),
                radius = w * 0.36f * pulse,
                center = Offset(w * 0.5f, h * 0.62f)
            )

            val hostPos: Pair<Float, Float>
            val guestPos: Pair<Float, Float>

            when (activity) {
                PetPlayActivity.CHASE -> {
                    val loopX1 = w * (0.34f + 0.14f * cos(phase))
                    val hop1 = abs(sin(phase * 2f)) * 12.dp.toPx()
                    val loopX2 = w * (0.64f + 0.14f * cos(phase - 0.8f))
                    val hop2 = abs(sin(phase * 2f - 0.8f)) * 12.dp.toPx()
                    hostPos = loopX1 to (baseGroundY - hop1)
                    guestPos = loopX2 to (baseGroundY - hop2)
                }
                PetPlayActivity.FETCH -> {
                    val orbT = (sin(phase) + 1f) * 0.5f // 0..1
                    val orbX = w * (0.24f + 0.52f * orbT)
                    val orbArcY = h * 0.58f - sin(orbT * PI.toFloat()) * 46.dp.toPx()
                    // Glowing Astral Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF176), Color(0x99FFB300), Color.Transparent),
                            center = Offset(orbX, orbArcY),
                            radius = 16.dp.toPx()
                        ),
                        radius = 16.dp.toPx(),
                        center = Offset(orbX, orbArcY)
                    )
                    drawCircle(
                        color = Color(0xFFFFFDE7),
                        radius = 5.dp.toPx(),
                        center = Offset(orbX, orbArcY)
                    )
                    val hostLeap = if (orbT < 0.35f) (1f - orbT / 0.35f) * 14.dp.toPx() else 0f
                    val guestLeap = if (orbT > 0.65f) ((orbT - 0.65f) / 0.35f) * 14.dp.toPx() else 0f
                    hostPos = (w * 0.25f) to (baseGroundY - hostLeap)
                    guestPos = (w * 0.75f) to (baseGroundY - guestLeap)
                }
                PetPlayActivity.SPAR -> {
                    val lunge = abs(sin(phase * 1.5f)) * 0.10f
                    hostPos = (w * (0.33f + lunge)) to (baseGroundY - abs(sin(phase * 3f)) * 8.dp.toPx())
                    guestPos = (w * (0.67f - lunge)) to (baseGroundY - abs(cos(phase * 3f)) * 8.dp.toPx())
                    // Playful spark burst between pets
                    val sparkCenter = Offset(w * 0.5f, baseGroundY - 22.dp.toPx())
                    drawCircle(
                        color = Color(0xAAFFD166).copy(alpha = pulse * 0.75f),
                        radius = 12.dp.toPx() * pulse,
                        center = sparkCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                PetPlayActivity.NAP -> {
                    val breath = sin(phase) * 2.dp.toPx()
                    hostPos = (w * 0.43f) to (baseGroundY + breath * 0.3f)
                    guestPos = (w * 0.57f) to (baseGroundY - breath * 0.3f)
                    // Floating cozy dream rings above sleeping pets
                    val ringY = h * 0.34f - (phase / (2f * PI.toFloat())) * 18.dp.toPx()
                    drawCircle(
                        color = Color(0xAAFFE082).copy(alpha = 0.65f * pulse),
                        radius = 6.dp.toPx(),
                        center = Offset(w * 0.48f, ringY),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xAA80CBC4).copy(alpha = 0.55f * pulse),
                        radius = 4.dp.toPx(),
                        center = Offset(w * 0.55f, ringY + 10.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            drawPetSprite(
                centerX = hostPos.first,
                groundY = hostPos.second,
                petType = hostPet,
                scale = 1.05f
            )
            drawPetSprite(
                centerX = guestPos.first,
                groundY = guestPos.second,
                petType = guestPet,
                scale = 1.05f
            )
        }
    }
}

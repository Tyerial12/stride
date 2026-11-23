package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HeroProfile
import com.example.data.model.LiveWorldEventSpec
import com.example.data.model.OwnedPet
import com.example.data.model.WildsFieldNode
import com.example.data.model.WildsNodeType
import com.example.domain.ProgressionEngine
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Live 4-hour rotating World Event Banner shown atop the Expedition hub.
 */
@Composable
fun LiveWorldEventBanner(
    event: LiveWorldEventSpec,
    remainingMinutes: Int,
    modifier: Modifier = Modifier
) {
    val accent = Color(event.accentHex)
    val hrs = remainingMinutes / 60
    val mins = remainingMinutes % 60
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Live World Event",
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "LIVE EVENT • ${event.title}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                }
                Text(
                    text = event.bonusSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.22f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Event Timer",
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${hrs}h ${mins}m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }
    }
}

/**
 * Interactive Walkable 2D Realm Wilds Field.
 * Tap anywhere to walk your Hero & evolved Companion to Chests, Ore Veins, Herb Groves,
 * Evolution Crystals, Shrines, Roaming Stalkers, or the Realm Guardian!
 */
@Composable
fun WalkableRealmWildsCanvas(
    profile: HeroProfile,
    equippedPet: OwnedPet?,
    nodes: List<WildsFieldNode>,
    fogDensity: Float,
    onInteractNode: (WildsFieldNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val realm = ProgressionEngine.realmSpec(profile.currentRealm.toInt())
    val heroX = remember { Animatable(0.50f) }
    val heroY = remember { Animatable(0.54f) }
    var facingRight by remember { mutableStateOf(true) }
    var targetX by remember { mutableFloatStateOf(0.50f) }
    var targetY by remember { mutableFloatStateOf(0.54f) }
    val scope = rememberCoroutineScope()

    val infinite = rememberInfiniteTransition(label = "wilds_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wilds_wave"
    )

    // Find closest interactive node within proximity
    val nearbyNode = nodes
        .filter { !it.harvested }
        .minByOrNull { hypot((it.posX - heroX.value).toDouble(), (it.posY - heroY.value).toDouble()) }
        ?.takeIf { hypot((it.posX - heroX.value).toDouble(), (it.posY - heroY.value).toDouble()) <= 0.19 }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Realm Wilds Map",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "REALM ${realm.realmNumber} WILDS: ${realm.name.uppercase()}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tap the 2D field to explore chests, shards, shrines & foes!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val remainingCount = nodes.count { !it.harvested && it.type != WildsNodeType.ROAMING_FOE && it.type != WildsNodeType.GUARDIAN }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$remainingCount Nodes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Interactive 2D Walkable Field Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(nodes) {
                        detectTapGestures { offset ->
                            val nx = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                            val ny = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.14f, 0.88f)
                            facingRight = nx >= heroX.value
                            targetX = nx
                            targetY = ny
                            val dist = hypot((nx - heroX.value).toDouble(), (ny - heroY.value).toDouble()).toFloat()
                            val moveMs = (dist * 900f).toInt().coerceIn(180, 720)
                            scope.launch {
                                launch { heroX.animateTo(nx, tween(moveMs, easing = LinearEasing)) }
                                launch { heroY.animateTo(ny, tween(moveMs, easing = LinearEasing)) }
                            }
                        }
                    }
                    .testTag("walkable_wilds_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val (skyTop, groundBottom) = when (realm.realmNumber % 5) {
                        1 -> Color(0xFF451A03) to Color(0xFF92400E)
                        2 -> Color(0xFF0C4A6E) to Color(0xFF0F766E)
                        3 -> Color(0xFF312E81) to Color(0xFF1E293B)
                        4 -> Color(0xFF4C0519) to Color(0xFF7C2D12)
                        else -> Color(0xFF14532D) to Color(0xFF064E3B)
                    }

                    // Biome terrain backdrop
                    drawRect(
                        brush = Brush.verticalGradient(listOf(skyTop, groundBottom)),
                        size = size
                    )

                    // Winding sandstone expedition trails
                    drawCircle(
                        color = Color(0x22FFF8E7),
                        radius = w * 0.38f,
                        center = Offset(w * 0.5f, h * 0.52f),
                        style = Stroke(width = 16.dp.toPx())
                    )

                    // Target tap ripple marker
                    drawCircle(
                        color = Color(0x66FFD166),
                        radius = 10.dp.toPx() + sin(pulse) * 2.dp.toPx(),
                        center = Offset(w * targetX, h * targetY),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw interactive field nodes
                    nodes.forEach { node ->
                        val nx = w * node.posX
                        val ny = h * node.posY
                        val nodeColor = Color(node.type.accentHex)
                        val bob = sin(pulse + node.posX * 6f) * 3.dp.toPx()

                        if (node.harvested) {
                            drawCircle(
                                color = Color(0x44CBD5E1),
                                radius = 9.dp.toPx(),
                                center = Offset(nx, ny)
                            )
                        } else {
                            // Glow halo
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(nodeColor.copy(alpha = 0.55f), Color.Transparent),
                                    center = Offset(nx, ny + bob),
                                    radius = 22.dp.toPx()
                                ),
                                radius = 22.dp.toPx(),
                                center = Offset(nx, ny + bob)
                            )
                            when (node.type) {
                                WildsNodeType.CHEST -> {
                                    drawRect(
                                        color = Color(0xFF92400E),
                                        topLeft = Offset(nx - 10.dp.toPx(), ny - 7.dp.toPx() + bob),
                                        size = Size(20.dp.toPx(), 14.dp.toPx())
                                    )
                                    drawRect(
                                        color = Color(0xFFF59E0B),
                                        topLeft = Offset(nx - 10.dp.toPx(), ny - 2.dp.toPx() + bob),
                                        size = Size(20.dp.toPx(), 4.dp.toPx())
                                    )
                                }
                                WildsNodeType.GUARDIAN, WildsNodeType.ROAMING_FOE -> {
                                    drawCircle(
                                        color = nodeColor,
                                        radius = if (node.type == WildsNodeType.GUARDIAN) 13.dp.toPx() else 10.dp.toPx(),
                                        center = Offset(nx, ny + bob)
                                    )
                                    drawCircle(
                                        color = Color(0xFFFFF8E7),
                                        radius = 3.dp.toPx(),
                                        center = Offset(nx - 3.dp.toPx(), ny - 2.dp.toPx() + bob)
                                    )
                                    drawCircle(
                                        color = Color(0xFFFFF8E7),
                                        radius = 3.dp.toPx(),
                                        center = Offset(nx + 3.dp.toPx(), ny - 2.dp.toPx() + bob)
                                    )
                                }
                                else -> {
                                    drawCircle(
                                        color = nodeColor,
                                        radius = 10.dp.toPx(),
                                        center = Offset(nx, ny + bob)
                                    )
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.75f),
                                        radius = 4.dp.toPx(),
                                        center = Offset(nx - 2.dp.toPx(), ny - 2.dp.toPx() + bob)
                                    )
                                }
                            }
                        }
                    }

                    // Draw Player Hero + Evolved Pet Sprite on the walkable field!
                    val hx = w * heroX.value
                    val hy = h * heroY.value
                    val stepBob = sin(pulse * 2f) * 2.dp.toPx()

                    if (equippedPet != null) {
                        val petOffsetX = if (facingRight) -24.dp.toPx() else 24.dp.toPx()
                        drawPetSprite(
                            centerX = hx + petOffsetX,
                            groundY = hy + 6.dp.toPx() + stepBob,
                            petType = equippedPet.resolvedType(),
                            scale = 0.56f,
                            evolutionStage = equippedPet.resolvedStage(),
                            evolutionBranch = equippedPet.resolvedBranch().name
                        )
                    }

                    // Compact heroic token + class aura
                    drawCircle(
                        color = Color(0x44F59E0B),
                        radius = 18.dp.toPx(),
                        center = Offset(hx, hy - 6.dp.toPx() + stepBob)
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 11.dp.toPx(),
                        center = Offset(hx, hy - 6.dp.toPx() + stepBob)
                    )
                    drawCircle(
                        color = Color(0xFFFFF8E7),
                        radius = 5.dp.toPx(),
                        center = Offset(hx + if (facingRight) 3.dp.toPx() else -3.dp.toPx(), hy - 8.dp.toPx() + stepBob)
                    )

                    // Subtle fog veil if steps haven't cleared mist yet
                    if (fogDensity > 0.08f) {
                        drawRect(
                            color = Color(0xFF0F172A).copy(alpha = (fogDensity * 0.35f).coerceIn(0f, 0.38f)),
                            size = size
                        )
                    }
                }
            }

            // Proximity Action Bar
            if (nearbyNode != null) {
                val accent = Color(nearbyNode.type.accentHex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nearbyNode.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                        Text(
                            text = when (nearbyNode.type) {
                                WildsNodeType.EVOLUTION_CRYSTAL -> "Yields +1 Sunstone Evolution Shard & Exploration XP"
                                WildsNodeType.CHEST -> "Grants Sol Coins, Sunshards & a chance for a Relic piece!"
                                WildsNodeType.ORE_VEIN -> "Yields +8 Sunsteel Ore & Exploration XP"
                                WildsNodeType.HERB_GROVE -> "Yields +8 Aether Herbs & Exploration XP"
                                WildsNodeType.SHRINE -> "Restores +35% HP & grants +18 Stride Energy!"
                                WildsNodeType.ROAMING_FOE -> "Launches active combat with +15% Exploration bonus!"
                                WildsNodeType.GUARDIAN -> "Challenge the Realm ${realm.realmNumber} Guardian Boss!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onInteractNode(nearbyNode) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wilds_interact_${nearbyNode.type.name.lowercase()}")
                    ) {
                        Text(nearbyNode.type.actionVerb, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            } else {
                Text(
                    text = "Walk next to any glowing node (Gold = Chest, Cyan = Evo Shard, Green = Herbs, Purple = Shrine, Red = Battle) to interact!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GearPerkType
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.LootChestTier
import com.example.domain.ChestOpenResult
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import java.util.Locale

private fun chestPrimaryColor(tier: LootChestTier): Color = when (tier) {
    LootChestTier.HIGHLAND -> Color(0xFFB45309)
    LootChestTier.BOSS_VAULT -> Color(0xFFB91C1C)
    LootChestTier.EXPEDITION -> Color(0xFFD97706)
    LootChestTier.SPIRE_SOVEREIGN -> Color(0xFF7C3AED)
}

private fun chestTrimColor(tier: LootChestTier): Color = when (tier) {
    LootChestTier.HIGHLAND -> Color(0xFFFDE047)
    LootChestTier.BOSS_VAULT -> Color(0xFFFFD166)
    LootChestTier.EXPEDITION -> Color(0xFFFF4D9D)
    LootChestTier.SPIRE_SOVEREIGN -> Color(0xFFF472B6)
}

@Composable
fun InteractiveLootChestDialog(
    pendingChest: LootChest?,
    openedResult: ChestOpenResult?,
    isOpening: Boolean,
    expeditionLocked: Boolean,
    onTapOpenChest: (LootChest) -> Unit,
    onStoreInVault: () -> Unit,
    onEquipRevealed: (InventoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (pendingChest == null && openedResult == null) return
    val ext = LocalSolstrideColors.current

    Dialog(
        onDismissRequest = {
            if (!isOpening) {
                if (pendingChest != null) onStoreInVault() else onDismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = !isOpening, dismissOnClickOutside = !isOpening)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("loot_chest_modal"),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.5.dp, if (openedResult?.wasUltraRare == true) ext.rarityUltraRare else MaterialTheme.colorScheme.primary)
        ) {
            if (openedResult != null) {
                val item = openedResult.item
                val rarity = item.resolvedRarity()
                val rarityTint = rarityColorFor(rarity)
                val (perk, perkVal) = item.resolvedPerk()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Loot revealed",
                        tint = rarityTint,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (openedResult.wasUltraRare) "ULTRA RARE RELIC UNLOCKED!" else "CHEST LOOTED!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = rarityTint,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = openedResult.chest.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Chest permanently consumed • Cannot be opened again",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(2.dp, rarityTint)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = rarityTint.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, rarityTint)
                            ) {
                                Text(
                                    text = "${rarity.label.uppercase(Locale.US)} • ${item.resolvedSlot().label.uppercase(Locale.US)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = rarityTint,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            EquipmentArtworkForItem(
                                item = item,
                                size = 84.dp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val statLine = buildList {
                                if (item.attackBonus > 0L) add("+${item.attackBonus} ATK")
                                if (item.defenseBonus > 0L) add("+${item.defenseBonus} DEF")
                                if (item.hpBonus > 0L) add("+${item.hpBonus} HP")
                                if (item.energyBonus > 0L) add("+${item.energyBonus} EN")
                                if (item.healAmount > 0L) add("HEALS +${item.healAmount} HP")
                            }.joinToString(" • ")
                            if (statLine.isNotBlank()) {
                                Text(
                                    text = statLine,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (perk != GearPerkType.NONE && perkVal > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = "PERK: ${perk.formatSummary(perkVal)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (item.resolvedSlot() != ItemSlot.CONSUMABLE) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Gear Power Score: ${item.gearPowerScore()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    if (item.resolvedSlot() != ItemSlot.CONSUMABLE && !expeditionLocked) {
                        Button(
                            onClick = { onEquipRevealed(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("chest_equip_now_button")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Equip Now", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("chest_collect_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Text("Store in Armory")
                    }
                }
            } else if (pendingChest != null) {
                val tier = pendingChest.resolvedTier()
                val infinite = rememberInfiniteTransition(label = "chest_wobble")
                val wobble by infinite.animateFloat(
                    initialValue = -5f,
                    targetValue = 5f,
                    animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "wobble"
                )
                val glow by infinite.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "glow"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tier.title.uppercase(Locale.US),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = pendingChest.sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Interactive Tappable 2D Treasure Chest Canvas
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .clickable(enabled = !isOpening) { onTapOpenChest(pendingChest) }
                            .testTag("tap_treasure_chest_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(164.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val trim = chestTrimColor(tier)
                            val body = chestPrimaryColor(tier)

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(trim.copy(alpha = 0.45f * glow), Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = size.minDimension * 0.48f
                                ),
                                radius = size.minDimension * 0.48f,
                                center = Offset(cx, cy)
                            )

                            rotate(degrees = if (isOpening) wobble * 2.2f else wobble, pivot = Offset(cx, cy + 12.dp.toPx())) {
                                // Chest base
                                drawRoundRect(
                                    color = body,
                                    topLeft = Offset(cx - 46.dp.toPx(), cy - 6.dp.toPx()),
                                    size = Size(92.dp.toPx(), 48.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                )
                                // Chest domed lid
                                drawRoundRect(
                                    color = body.copy(alpha = 0.92f),
                                    topLeft = Offset(cx - 48.dp.toPx(), cy - 34.dp.toPx()),
                                    size = Size(96.dp.toPx(), 32.dp.toPx()),
                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                )
                                // Gold / Prismatic straps
                                drawRoundRect(
                                    color = trim,
                                    topLeft = Offset(cx - 48.dp.toPx(), cy - 10.dp.toPx()),
                                    size = Size(96.dp.toPx(), 8.dp.toPx()),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                                drawRoundRect(
                                    color = trim,
                                    topLeft = Offset(cx - 28.dp.toPx(), cy - 34.dp.toPx()),
                                    size = Size(10.dp.toPx(), 76.dp.toPx()),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                                drawRoundRect(
                                    color = trim,
                                    topLeft = Offset(cx + 18.dp.toPx(), cy - 34.dp.toPx()),
                                    size = Size(10.dp.toPx(), 76.dp.toPx()),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )
                                // Central lock clasp
                                drawRoundRect(
                                    color = Color(0xFF1F2430),
                                    topLeft = Offset(cx - 10.dp.toPx(), cy - 14.dp.toPx()),
                                    size = Size(20.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                drawRoundRect(
                                    color = trim,
                                    topLeft = Offset(cx - 10.dp.toPx(), cy - 14.dp.toPx()),
                                    size = Size(20.dp.toPx(), 24.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                                drawCircle(
                                    color = Color(0xFFFFF8E7),
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(cx, cy - 2.dp.toPx())
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ext.rarityUltraRare.copy(alpha = 0.14f),
                        border = BorderStroke(1.5.dp, ext.rarityUltraRare)
                    ) {
                        Text(
                            text = "Ultra Rare Relic Odds: ${pendingChest.ultraRareChancePct}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = ext.rarityUltraRare,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Single-use chest • Permanently consumed once opened",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onTapOpenChest(pendingChest) },
                        enabled = !isOpening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("open_loot_chest_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isOpening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlocking Relics…")
                        } else {
                            Icon(Icons.Filled.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tap to Open Chest!", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onStoreInVault,
                        enabled = !isOpening,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("store_chest_vault_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Loot Vault")
                    }
                }
            }
        }
    }
}

@Composable
fun LootVaultSection(
    chests: List<LootChest>,
    onOpenChest: (LootChest) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("loot_vault_section"),
        strongBorder = chests.isNotEmpty()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = "Sealed treasure vault",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Loot Vault (${chests.size} Sealed)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Single-use chests • Permanently consumed once opened",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (chests.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Vault is empty. Win battles, complete Timed Expeditions, or claim Quests to collect sealed treasure chests!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chests.forEach { chest ->
                    val tier = chest.resolvedTier()
                    val accent = chestPrimaryColor(tier)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.5.dp, accent)
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
                                    text = tier.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = chest.sourceLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Ultra Rare Odds: ${chest.ultraRareChancePct}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.rarityUltraRare
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onOpenChest(chest) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("vault_open_chest_${chest.id}")
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

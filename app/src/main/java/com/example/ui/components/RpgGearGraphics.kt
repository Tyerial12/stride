package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ElementalAffinity
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import kotlin.math.cos
import kotlin.math.sin

private val GearSolarGold = Color(0xFFFFD166)
private val GearSolarAmber = Color(0xFFF59E0B)
private val GearObsidianSurface = Color(0xFF10131C)
private val GearArcanePurple = Color(0xFFA855F7)
private val GearCrimsonEmber = Color(0xFFEF4444)
private val GearSunbeamCyan = Color(0xFF38BDF8)
private val GearVerdantLife = Color(0xFF34D399)

enum class WeaponGraphicStyle {
    GREATSWORD,
    ARCANE_STAFF,
    RECURVE_BOW,
    TWIN_DAGGERS
}

fun resolveWeaponGraphicStyle(itemName: String): WeaponGraphicStyle {
    val lower = itemName.lowercase()
    return when {
        lower.contains("staff") || lower.contains("wand") || lower.contains("scepter") || lower.contains("rod") || lower.contains("tome") || lower.contains("orb") ->
            WeaponGraphicStyle.ARCANE_STAFF
        lower.contains("bow") || lower.contains("longbow") || lower.contains("arbalest") || lower.contains("quiver") ->
            WeaponGraphicStyle.RECURVE_BOW
        lower.contains("dagger") || lower.contains("fang") || lower.contains("stiletto") || lower.contains("kris") || lower.contains("claw") || lower.contains("dirk") ->
            WeaponGraphicStyle.TWIN_DAGGERS
        else -> WeaponGraphicStyle.GREATSWORD
    }
}

fun elementPalette(affinity: ElementalAffinity?): Pair<Color, Color> = when (affinity) {
    ElementalAffinity.SOLAR -> GearSolarGold to Color(0xFFFF6D00)
    ElementalAffinity.EMBER -> Color(0xFFFF7043) to GearCrimsonEmber
    ElementalAffinity.FROST -> GearSunbeamCyan to Color(0xFF80D8FF)
    ElementalAffinity.TERRA -> GearVerdantLife to Color(0xFFA7F3D0)
    ElementalAffinity.VOID -> GearArcanePurple to Color(0xFF6D28D9)
    null -> Color(0xFFCFD8DC) to GearSolarAmber
}

fun gearRarityAccent(rarity: ItemRarity): Color = when (rarity) {
    ItemRarity.COMMON -> Color(0xFFB8A99A)
    ItemRarity.RARE -> Color(0xFF38BDF8)
    ItemRarity.EPIC -> Color(0xFFA855F7)
    ItemRarity.SOLAR -> Color(0xFFFFB800)
    ItemRarity.ULTRA_RARE -> Color(0xFFFF4D9D)
}

@Composable
fun EquipmentArtworkForItem(
    item: InventoryItem,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    EquipmentArtworkBadge(
        item = item,
        slot = item.resolvedSlot(),
        rarity = item.resolvedRarity(),
        affinity = item.resolvedRune(),
        upgradeLevel = item.upgradeLevel,
        size = size,
        modifier = modifier
    )
}

@Composable
fun EquipmentArtworkBadge(
    item: InventoryItem? = null,
    slot: ItemSlot,
    rarity: ItemRarity = item?.resolvedRarity() ?: ItemRarity.COMMON,
    affinity: ElementalAffinity? = item?.resolvedRune(),
    upgradeLevel: Long = item?.upgradeLevel ?: 0L,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val rColor = gearRarityAccent(rarity)
    val (elemPrimary, elemSecondary) = elementPalette(affinity)
    val weaponStyle = resolveWeaponGraphicStyle(item?.name ?: "")
    val cornerRadius = (size.value * 0.22f).dp

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        rColor.copy(alpha = if (item != null) 0.34f else 0.12f),
                        elemPrimary.copy(alpha = if (item != null) 0.16f else 0.05f),
                        GearObsidianSurface.copy(alpha = 0.96f)
                    )
                )
            )
            .border(
                width = if (rarity == ItemRarity.ULTRA_RARE || rarity == ItemRarity.SOLAR) 1.8.dp else 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        rColor.copy(alpha = if (item != null) 0.95f else 0.38f),
                        elemPrimary.copy(alpha = if (item != null) 0.75f else 0.25f),
                        rColor.copy(alpha = if (item != null) 0.55f else 0.20f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val cy = h * 0.5f

            // Ornate inner halo & sunburst rays for high-tier weapons and armor
            drawCircle(
                color = rColor.copy(alpha = if (item != null) 0.22f else 0.08f),
                radius = w * 0.42f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.02f)
            )
            if (rarity == ItemRarity.SOLAR || rarity == ItemRarity.ULTRA_RARE || upgradeLevel >= 3L) {
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45.0))
                    val rIn = w * 0.37f
                    val rOut = w * 0.43f
                    drawLine(
                        color = rColor.copy(alpha = 0.48f),
                        start = Offset(cx + (cos(angle) * rIn).toFloat(), cy + (sin(angle) * rIn).toFloat()),
                        end = Offset(cx + (cos(angle) * rOut).toFloat(), cy + (sin(angle) * rOut).toFloat()),
                        strokeWidth = w * 0.022f,
                        cap = StrokeCap.Round
                    )
                }
            }

            val alphaMul = if (item != null) 1f else 0.52f
            when (slot) {
                ItemSlot.WEAPON -> {
                    when (weaponStyle) {
                        WeaponGraphicStyle.GREATSWORD -> drawSculptedGreatsword(
                            w = w,
                            h = h,
                            rarityColor = rColor.copy(alpha = alphaMul),
                            elemPrimary = elemPrimary.copy(alpha = alphaMul),
                            elemSecondary = elemSecondary.copy(alpha = alphaMul)
                        )
                        WeaponGraphicStyle.ARCANE_STAFF -> drawSculptedArcaneStaff(
                            w = w,
                            h = h,
                            rarityColor = rColor.copy(alpha = alphaMul),
                            elemPrimary = elemPrimary.copy(alpha = alphaMul),
                            elemSecondary = elemSecondary.copy(alpha = alphaMul)
                        )
                        WeaponGraphicStyle.RECURVE_BOW -> drawSculptedRecurveBow(
                            w = w,
                            h = h,
                            rarityColor = rColor.copy(alpha = alphaMul),
                            elemPrimary = elemPrimary.copy(alpha = alphaMul),
                            elemSecondary = elemSecondary.copy(alpha = alphaMul)
                        )
                        WeaponGraphicStyle.TWIN_DAGGERS -> drawSculptedTwinDaggers(
                            w = w,
                            h = h,
                            rarityColor = rColor.copy(alpha = alphaMul),
                            elemPrimary = elemPrimary.copy(alpha = alphaMul),
                            elemSecondary = elemSecondary.copy(alpha = alphaMul)
                        )
                    }
                }
                ItemSlot.ARMOR -> drawSculptedPlateCuirass(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
                ItemSlot.HELM -> drawSculptedPaladinGreathelm(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
                ItemSlot.BOOTS -> drawSculptedWingedSabatons(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
                ItemSlot.RING -> drawSculptedSignetRing(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
                ItemSlot.RELIC -> drawSculptedCelestialAstrolabe(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
                ItemSlot.CONSUMABLE -> drawSculptedAlchemistFlask(
                    w = w,
                    h = h,
                    rarityColor = rColor.copy(alpha = alphaMul),
                    elemPrimary = elemPrimary.copy(alpha = alphaMul),
                    elemSecondary = elemSecondary.copy(alpha = alphaMul)
                )
            }
        }

        if (upgradeLevel > 0L && size >= 38.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(GearObsidianSurface.copy(alpha = 0.88f))
                    .border(0.8.dp, GearSolarGold.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "+$upgradeLevel",
                    color = GearSolarGold,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private fun DrawScope.drawSculptedGreatsword(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    rotate(degrees = -42f, pivot = Offset(cx, cy)) {
        drawRoundRect(
            color = elemPrimary.copy(alpha = 0.25f),
            topLeft = Offset(cx - w * 0.13f, h * 0.10f),
            size = Size(w * 0.26f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )

        val leftBlade = Path().apply {
            moveTo(cx, h * 0.08f)
            lineTo(cx - w * 0.095f, h * 0.20f)
            lineTo(cx - w * 0.075f, h * 0.62f)
            lineTo(cx, h * 0.62f)
            close()
        }
        drawPath(
            path = leftBlade,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFE0F2F1), Color(0xFF90A4AE))
            )
        )

        val rightBlade = Path().apply {
            moveTo(cx, h * 0.08f)
            lineTo(cx + w * 0.095f, h * 0.20f)
            lineTo(cx + w * 0.075f, h * 0.62f)
            lineTo(cx, h * 0.62f)
            close()
        }
        drawPath(
            path = rightBlade,
            brush = Brush.verticalGradient(
                colors = listOf(elemPrimary, Color(0xFFB0BEC5), Color(0xFF546E7A))
            )
        )

        drawLine(
            color = elemSecondary,
            start = Offset(cx, h * 0.15f),
            end = Offset(cx, h * 0.58f),
            strokeWidth = w * 0.026f,
            cap = StrokeCap.Round
        )

        val fullBlade = Path().apply {
            moveTo(cx, h * 0.08f)
            lineTo(cx - w * 0.095f, h * 0.20f)
            lineTo(cx - w * 0.075f, h * 0.62f)
            lineTo(cx + w * 0.075f, h * 0.62f)
            lineTo(cx + w * 0.095f, h * 0.20f)
            close()
        }
        drawPath(
            path = fullBlade,
            color = rarityColor,
            style = Stroke(width = w * 0.02f, join = StrokeJoin.Round)
        )

        val guardPath = Path().apply {
            moveTo(cx - w * 0.25f, h * 0.58f)
            lineTo(cx - w * 0.09f, h * 0.61f)
            lineTo(cx + w * 0.09f, h * 0.61f)
            lineTo(cx + w * 0.25f, h * 0.58f)
            lineTo(cx + w * 0.20f, h * 0.67f)
            lineTo(cx, h * 0.70f)
            lineTo(cx - w * 0.20f, h * 0.67f)
            close()
        }
        drawPath(
            path = guardPath,
            brush = Brush.horizontalGradient(
                colors = listOf(GearSolarAmber, GearSolarGold, Color(0xFFFFF8E1), GearSolarGold, GearSolarAmber)
            )
        )
        drawPath(
            path = guardPath,
            color = Color(0xFF3E2723),
            style = Stroke(width = w * 0.016f)
        )

        drawRoundRect(
            color = Color(0xFF4E342E),
            topLeft = Offset(cx - w * 0.042f, h * 0.68f),
            size = Size(w * 0.084f, h * 0.17f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )
        for (i in 0..2) {
            val gy = h * (0.71f + i * 0.042f)
            drawLine(
                color = GearSolarGold.copy(alpha = 0.75f),
                start = Offset(cx - w * 0.04f, gy),
                end = Offset(cx + w * 0.04f, gy + h * 0.015f),
                strokeWidth = w * 0.015f
            )
        }

        drawCircle(
            color = GearSolarGold,
            radius = w * 0.07f,
            center = Offset(cx, h * 0.88f)
        )
        drawCircle(
            color = elemPrimary,
            radius = w * 0.038f,
            center = Offset(cx, h * 0.88f)
        )
        drawCircle(
            color = elemPrimary,
            radius = w * 0.046f,
            center = Offset(cx, h * 0.64f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.018f,
            center = Offset(cx - w * 0.012f, h * 0.628f)
        )
    }
}

private fun DrawScope.drawSculptedArcaneStaff(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    rotate(degrees = 36f, pivot = Offset(cx, cy)) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF4E342E), GearSolarAmber, GearSolarGold, Color(0xFF4E342E))
            ),
            topLeft = Offset(cx - w * 0.036f, h * 0.30f),
            size = Size(w * 0.072f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        listOf(0.42f, 0.58f, 0.76f).forEach { ratio ->
            drawRoundRect(
                color = GearSolarGold,
                topLeft = Offset(cx - w * 0.055f, h * ratio),
                size = Size(w * 0.11f, h * 0.032f),
                cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
            )
        }

        val cradlePath = Path().apply {
            moveTo(cx, h * 0.35f)
            cubicTo(
                cx - w * 0.28f, h * 0.34f,
                cx - w * 0.26f, h * 0.08f,
                cx - w * 0.08f, h * 0.08f
            )
            cubicTo(
                cx - w * 0.17f, h * 0.14f,
                cx - w * 0.16f, h * 0.28f,
                cx, h * 0.29f
            )
            cubicTo(
                cx + w * 0.16f, h * 0.28f,
                cx + w * 0.17f, h * 0.14f,
                cx + w * 0.08f, h * 0.08f
            )
            cubicTo(
                cx + w * 0.26f, h * 0.08f,
                cx + w * 0.28f, h * 0.34f,
                cx, h * 0.35f
            )
            close()
        }
        drawPath(
            path = cradlePath,
            brush = Brush.verticalGradient(colors = listOf(Color.White, GearSolarGold, GearSolarAmber))
        )
        drawPath(
            path = cradlePath,
            color = rarityColor,
            style = Stroke(width = w * 0.018f)
        )

        drawCircle(
            color = elemPrimary.copy(alpha = 0.32f),
            radius = w * 0.15f,
            center = Offset(cx, h * 0.19f)
        )
        val crystalPath = Path().apply {
            moveTo(cx, h * 0.08f)
            lineTo(cx + w * 0.085f, h * 0.19f)
            lineTo(cx, h * 0.29f)
            lineTo(cx - w * 0.085f, h * 0.19f)
            close()
        }
        drawPath(
            path = crystalPath,
            brush = Brush.linearGradient(
                colors = listOf(Color.White, elemPrimary, elemSecondary)
            )
        )
        drawPath(
            path = crystalPath,
            color = Color.White.copy(alpha = 0.85f),
            style = Stroke(width = w * 0.016f)
        )
    }
}

private fun DrawScope.drawSculptedRecurveBow(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cy = h * 0.5f

    val stringPath = Path().apply {
        moveTo(w * 0.26f, h * 0.16f)
        lineTo(w * 0.20f, cy)
        lineTo(w * 0.26f, h * 0.84f)
    }
    drawPath(
        path = stringPath,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    val bowPath = Path().apply {
        moveTo(w * 0.25f, h * 0.15f)
        cubicTo(
            w * 0.58f, h * 0.08f,
            w * 0.78f, h * 0.28f,
            w * 0.64f, cy
        )
        cubicTo(
            w * 0.78f, h * 0.72f,
            w * 0.58f, h * 0.92f,
            w * 0.25f, h * 0.85f
        )
    }
    drawPath(
        path = bowPath,
        brush = Brush.verticalGradient(
            colors = listOf(GearSolarGold, GearSolarAmber, Color(0xFF5D4037), GearSolarAmber, GearSolarGold)
        ),
        style = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
    )
    drawPath(
        path = bowPath,
        color = rarityColor,
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round)
    )

    drawCircle(
        color = elemPrimary,
        radius = w * 0.055f,
        center = Offset(w * 0.65f, cy)
    )

    drawLine(
        color = elemSecondary,
        start = Offset(w * 0.20f, cy),
        end = Offset(w * 0.84f, cy),
        strokeWidth = w * 0.036f,
        cap = StrokeCap.Round
    )
    val arrowHead = Path().apply {
        moveTo(w * 0.90f, cy)
        lineTo(w * 0.74f, cy - h * 0.09f)
        lineTo(w * 0.78f, cy)
        lineTo(w * 0.74f, cy + h * 0.09f)
        close()
    }
    drawPath(
        path = arrowHead,
        brush = Brush.horizontalGradient(colors = listOf(elemPrimary, Color.White))
    )
}

private fun DrawScope.drawSculptedTwinDaggers(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    listOf(-34f to -w * 0.08f, 34f to w * 0.08f).forEach { (angle, xShift) ->
        rotate(degrees = angle, pivot = Offset(cx + xShift * 0.3f, cy)) {
            val bx = cx + xShift
            val blade = Path().apply {
                moveTo(bx, h * 0.14f)
                cubicTo(
                    bx - w * 0.10f, h * 0.26f,
                    bx - w * 0.08f, h * 0.48f,
                    bx - w * 0.055f, h * 0.58f
                )
                lineTo(bx + w * 0.055f, h * 0.58f)
                cubicTo(
                    bx + w * 0.04f, h * 0.44f,
                    bx + w * 0.06f, h * 0.26f,
                    bx, h * 0.14f
                )
                close()
            }
            drawPath(
                path = blade,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, elemPrimary, Color(0xFF455A64))
                )
            )
            drawPath(
                path = blade,
                color = rarityColor,
                style = Stroke(width = w * 0.018f)
            )

            drawRoundRect(
                color = GearSolarGold,
                topLeft = Offset(bx - w * 0.12f, h * 0.57f),
                size = Size(w * 0.24f, h * 0.045f),
                cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
            )
            drawRoundRect(
                color = Color(0xFF37474F),
                topLeft = Offset(bx - w * 0.032f, h * 0.61f),
                size = Size(w * 0.064f, h * 0.15f),
                cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
            )
            drawCircle(
                color = elemSecondary,
                radius = w * 0.042f,
                center = Offset(bx, h * 0.79f),
                style = Stroke(width = w * 0.022f)
            )
        }
    }
}

private fun DrawScope.drawSculptedPlateCuirass(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f

    val leftPauldron = Path().apply {
        moveTo(w * 0.10f, h * 0.34f)
        cubicTo(w * 0.10f, h * 0.16f, w * 0.28f, h * 0.14f, w * 0.36f, h * 0.22f)
        lineTo(w * 0.32f, h * 0.42f)
        lineTo(w * 0.14f, h * 0.44f)
        close()
    }
    val rightPauldron = Path().apply {
        moveTo(w * 0.90f, h * 0.34f)
        cubicTo(w * 0.90f, h * 0.16f, w * 0.72f, h * 0.14f, w * 0.64f, h * 0.22f)
        lineTo(w * 0.68f, h * 0.42f)
        lineTo(w * 0.86f, h * 0.44f)
        close()
    }
    drawPath(
        path = leftPauldron,
        brush = Brush.linearGradient(colors = listOf(GearSolarGold, GearSolarAmber, Color(0xFF455A64)))
    )
    drawPath(
        path = rightPauldron,
        brush = Brush.linearGradient(colors = listOf(Color(0xFF455A64), GearSolarAmber, GearSolarGold))
    )
    drawPath(path = leftPauldron, color = rarityColor, style = Stroke(width = w * 0.02f))
    drawPath(path = rightPauldron, color = rarityColor, style = Stroke(width = w * 0.02f))

    val cuirassPath = Path().apply {
        moveTo(w * 0.32f, h * 0.22f)
        lineTo(cx, h * 0.27f)
        lineTo(w * 0.68f, h * 0.22f)
        lineTo(w * 0.74f, h * 0.48f)
        lineTo(w * 0.66f, h * 0.74f)
        lineTo(cx, h * 0.82f)
        lineTo(w * 0.34f, h * 0.74f)
        lineTo(w * 0.26f, h * 0.48f)
        close()
    }
    drawPath(
        path = cuirassPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFECEFF1),
                Color(0xFF90A4AE),
                Color(0xFF455A64),
                Color(0xFF263238)
            )
        )
    )
    drawPath(
        path = cuirassPath,
        color = rarityColor,
        style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
    )

    drawLine(
        color = GearSolarGold,
        start = Offset(w * 0.30f, h * 0.50f),
        end = Offset(cx, h * 0.60f),
        strokeWidth = w * 0.025f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = GearSolarGold,
        start = Offset(w * 0.70f, h * 0.50f),
        end = Offset(cx, h * 0.60f),
        strokeWidth = w * 0.025f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = GearSolarGold.copy(alpha = 0.8f),
        start = Offset(w * 0.33f, h * 0.65f),
        end = Offset(cx, h * 0.73f),
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = GearSolarGold.copy(alpha = 0.8f),
        start = Offset(w * 0.67f, h * 0.65f),
        end = Offset(cx, h * 0.73f),
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round
    )

    drawCircle(
        color = elemPrimary.copy(alpha = 0.35f),
        radius = w * 0.13f,
        center = Offset(cx, h * 0.43f)
    )
    val diamondCore = Path().apply {
        moveTo(cx, h * 0.33f)
        lineTo(cx + w * 0.08f, h * 0.43f)
        lineTo(cx, h * 0.53f)
        lineTo(cx - w * 0.08f, h * 0.43f)
        close()
    }
    drawPath(
        path = diamondCore,
        brush = Brush.linearGradient(colors = listOf(Color.White, elemPrimary, elemSecondary))
    )
    drawPath(
        path = diamondCore,
        color = GearSolarGold,
        style = Stroke(width = w * 0.018f)
    )
}

private fun DrawScope.drawSculptedPaladinGreathelm(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f

    val leftWing = Path().apply {
        moveTo(w * 0.25f, h * 0.52f)
        lineTo(w * 0.08f, h * 0.20f)
        lineTo(w * 0.20f, h * 0.26f)
        lineTo(w * 0.15f, h * 0.12f)
        lineTo(w * 0.30f, h * 0.32f)
        close()
    }
    val rightWing = Path().apply {
        moveTo(w * 0.75f, h * 0.52f)
        lineTo(w * 0.92f, h * 0.20f)
        lineTo(w * 0.80f, h * 0.26f)
        lineTo(w * 0.85f, h * 0.12f)
        lineTo(w * 0.70f, h * 0.32f)
        close()
    }
    drawPath(path = leftWing, brush = Brush.verticalGradient(listOf(Color.White, GearSolarGold, GearSolarAmber)))
    drawPath(path = rightWing, brush = Brush.verticalGradient(listOf(Color.White, GearSolarGold, GearSolarAmber)))

    val helmBody = Path().apply {
        moveTo(w * 0.26f, h * 0.36f)
        cubicTo(w * 0.26f, h * 0.14f, w * 0.74f, h * 0.14f, w * 0.74f, h * 0.36f)
        lineTo(w * 0.76f, h * 0.68f)
        lineTo(cx, h * 0.84f)
        lineTo(w * 0.24f, h * 0.68f)
        close()
    }
    drawPath(
        path = helmBody,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFECEFF1), Color(0xFF90A4AE), Color(0xFF37474F))
        )
    )
    drawPath(
        path = helmBody,
        color = rarityColor,
        style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
    )

    drawRoundRect(
        color = GearSolarGold,
        topLeft = Offset(cx - w * 0.035f, h * 0.15f),
        size = Size(w * 0.07f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )
    val visorPath = Path().apply {
        moveTo(w * 0.31f, h * 0.46f)
        lineTo(cx, h * 0.51f)
        lineTo(w * 0.69f, h * 0.46f)
        lineTo(w * 0.66f, h * 0.54f)
        lineTo(cx + w * 0.04f, h * 0.57f)
        lineTo(cx + w * 0.035f, h * 0.76f)
        lineTo(cx - w * 0.035f, h * 0.76f)
        lineTo(cx - w * 0.04f, h * 0.57f)
        lineTo(w * 0.34f, h * 0.54f)
        close()
    }
    drawPath(
        path = visorPath,
        brush = Brush.verticalGradient(colors = listOf(Color.White, elemPrimary, elemSecondary))
    )
}

private fun DrawScope.drawSculptedWingedSabatons(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val bootPath = Path().apply {
        moveTo(w * 0.32f, h * 0.16f)
        lineTo(w * 0.62f, h * 0.18f)
        lineTo(w * 0.58f, h * 0.58f)
        lineTo(w * 0.84f, h * 0.70f)
        cubicTo(w * 0.88f, h * 0.76f, w * 0.84f, h * 0.84f, w * 0.74f, h * 0.84f)
        lineTo(w * 0.24f, h * 0.84f)
        lineTo(w * 0.24f, h * 0.60f)
        close()
    }
    drawPath(
        path = bootPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFECEFF1), Color(0xFF78909C), Color(0xFF37474F))
        )
    )
    drawPath(
        path = bootPath,
        color = rarityColor,
        style = Stroke(width = w * 0.025f, join = StrokeJoin.Round)
    )

    val wingPath = Path().apply {
        moveTo(w * 0.32f, h * 0.52f)
        lineTo(w * 0.10f, h * 0.34f)
        lineTo(w * 0.18f, h * 0.54f)
        lineTo(w * 0.10f, h * 0.62f)
        lineTo(w * 0.34f, h * 0.64f)
        close()
    }
    drawPath(
        path = wingPath,
        brush = Brush.horizontalGradient(colors = listOf(Color.White, GearSolarGold, elemPrimary))
    )
    drawPath(
        path = wingPath,
        color = GearSolarAmber,
        style = Stroke(width = w * 0.016f)
    )

    drawLine(
        color = elemSecondary,
        start = Offset(w * 0.26f, h * 0.81f),
        end = Offset(w * 0.78f, h * 0.81f),
        strokeWidth = w * 0.035f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSculptedSignetRing(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.56f

    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(GearSolarGold, Color.White, GearSolarAmber, GearSolarGold, Color(0xFF8D6E63), GearSolarGold)
        ),
        radius = w * 0.26f,
        center = Offset(cx, cy),
        style = Stroke(width = w * 0.095f)
    )
    drawCircle(
        color = rarityColor,
        radius = w * 0.31f,
        center = Offset(cx, cy),
        style = Stroke(width = w * 0.018f)
    )

    val gemCx = cx
    val gemCy = h * 0.26f
    drawCircle(
        color = elemPrimary.copy(alpha = 0.36f),
        radius = w * 0.19f,
        center = Offset(gemCx, gemCy)
    )
    val gemPath = Path().apply {
        moveTo(gemCx, gemCy - h * 0.14f)
        lineTo(gemCx + w * 0.14f, gemCy - h * 0.03f)
        lineTo(gemCx + w * 0.09f, gemCy + h * 0.12f)
        lineTo(gemCx - w * 0.09f, gemCy + h * 0.12f)
        lineTo(gemCx - w * 0.14f, gemCy - h * 0.03f)
        close()
    }
    drawPath(
        path = gemPath,
        brush = Brush.linearGradient(colors = listOf(Color.White, elemPrimary, elemSecondary))
    )
    drawPath(
        path = gemPath,
        color = GearSolarGold,
        style = Stroke(width = w * 0.024f, join = StrokeJoin.Round)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = w * 0.032f,
        center = Offset(gemCx - w * 0.035f, gemCy - h * 0.04f)
    )
}

private fun DrawScope.drawSculptedCelestialAstrolabe(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45.0))
        val rInner = w * 0.16f
        val rOuter = if (i % 2 == 0) w * 0.38f else w * 0.29f
        drawLine(
            color = if (i % 2 == 0) GearSolarGold else elemPrimary,
            start = Offset(cx + (cos(angle) * rInner).toFloat(), cy + (sin(angle) * rInner).toFloat()),
            end = Offset(cx + (cos(angle) * rOuter).toFloat(), cy + (sin(angle) * rOuter).toFloat()),
            strokeWidth = if (i % 2 == 0) w * 0.034f else w * 0.022f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = GearSolarGold,
        radius = w * 0.30f,
        center = Offset(cx, cy),
        style = Stroke(width = w * 0.035f)
    )
    drawCircle(
        color = rarityColor,
        radius = w * 0.21f,
        center = Offset(cx, cy),
        style = Stroke(width = w * 0.024f)
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, elemPrimary, elemSecondary)
        ),
        radius = w * 0.12f,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawSculptedAlchemistFlask(
    w: Float,
    h: Float,
    rarityColor: Color,
    elemPrimary: Color,
    elemSecondary: Color
) {
    val cx = w * 0.5f

    drawRoundRect(
        color = Color(0xFF8D6E63),
        topLeft = Offset(cx - w * 0.06f, h * 0.12f),
        size = Size(w * 0.12f, h * 0.09f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
    )
    drawRoundRect(
        color = GearSolarGold,
        topLeft = Offset(cx - w * 0.09f, h * 0.20f),
        size = Size(w * 0.18f, h * 0.06f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
    )

    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(elemPrimary, elemSecondary, GearCrimsonEmber)
        ),
        radius = w * 0.24f,
        center = Offset(cx, h * 0.60f)
    )
    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = w * 0.032f, center = Offset(cx - w * 0.07f, h * 0.56f))
    drawCircle(color = Color.White.copy(alpha = 0.7f), radius = w * 0.022f, center = Offset(cx + w * 0.06f, h * 0.64f))
    drawCircle(color = Color.White.copy(alpha = 0.85f), radius = w * 0.018f, center = Offset(cx + w * 0.02f, h * 0.49f))

    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = w * 0.26f,
        center = Offset(cx, h * 0.60f),
        style = Stroke(width = w * 0.026f)
    )
    drawCircle(
        color = rarityColor,
        radius = w * 0.29f,
        center = Offset(cx, h * 0.60f),
        style = Stroke(width = w * 0.016f)
    )
}

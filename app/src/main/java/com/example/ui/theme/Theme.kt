package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class SolstrideDisplayMode {
    SUNLIT_PARCHMENT,
    OBSIDIAN_OLED
}

data class SolstrideExtendedColors(
    val borderStrong: Color,
    val borderSubtle: Color,
    val rarityCommon: Color,
    val rarityRare: Color,
    val rarityEpic: Color,
    val raritySolar: Color,
    val rarityUltraRare: Color,
    val isOledBatterySaver: Boolean
)

val LocalSolstrideColors = staticCompositionLocalOf {
    SolstrideExtendedColors(
        borderStrong = SunlitBorderInk,
        borderSubtle = ForgeBronzeSecondary,
        rarityCommon = RarityCommonLight,
        rarityRare = RarityRareLight,
        rarityEpic = RarityEpicLight,
        raritySolar = RaritySolarLight,
        rarityUltraRare = RarityUltraRareLight,
        isOledBatterySaver = false
    )
}

private val SunlitLightColorScheme = lightColorScheme(
    primary = SolarGoldPrimary,
    onPrimary = Color.White,
    primaryContainer = SolarGoldContainer,
    onPrimaryContainer = SolarGoldOnContainer,
    secondary = ForgeBronzeSecondary,
    onSecondary = Color.White,
    secondaryContainer = ForgeBronzeContainer,
    onSecondaryContainer = ForgeBronzeOnContainer,
    tertiary = VerdantJadeTertiary,
    onTertiary = Color.White,
    tertiaryContainer = VerdantJadeContainer,
    onTertiaryContainer = VerdantJadeOnContainer,
    error = VermilionCrimsonError,
    onError = Color.White,
    errorContainer = VermilionErrorContainer,
    onErrorContainer = VermilionOnErrorContainer,
    background = SunlitIvoryBg,
    onBackground = SunlitObsidianInk,
    surface = SunlitAlabasterSurface,
    onSurface = SunlitObsidianInk,
    surfaceVariant = SunlitLimestoneVariant,
    onSurfaceVariant = SunlitCharcoalMuted,
    outline = SunlitBorderInk,
    outlineVariant = ForgeBronzeSecondary
)

private val ObsidianOledColorScheme = darkColorScheme(
    primary = OledAmberPrimary,
    onPrimary = OledAmberOnPrimary,
    primaryContainer = OledAmberContainer,
    onPrimaryContainer = OledAmberOnContainer,
    secondary = OledBronzeSecondary,
    onSecondary = OledBronzeOnSecondary,
    secondaryContainer = OledBronzeContainer,
    onSecondaryContainer = OledBronzeOnContainer,
    tertiary = OledJadeTertiary,
    onTertiary = OledJadeOnTertiary,
    tertiaryContainer = OledJadeContainer,
    onTertiaryContainer = OledJadeOnContainer,
    error = OledCrimsonError,
    onError = OledCrimsonOnError,
    errorContainer = OledCrimsonContainer,
    onErrorContainer = OledCrimsonOnContainer,
    background = OledPureBlackBg,
    onBackground = OledBoneIvoryInk,
    surface = OledCharcoalSurface,
    onSurface = OledBoneIvoryInk,
    surfaceVariant = OledSlateVariant,
    onSurfaceVariant = OledMutedSilver,
    outline = OledBorderGold,
    outlineVariant = Color(0xFF474E5D)
)

private val SolstrideShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SolstrideTheme(
    displayMode: SolstrideDisplayMode = SolstrideDisplayMode.SUNLIT_PARCHMENT,
    content: @Composable () -> Unit
) {
    val isOled = displayMode == SolstrideDisplayMode.OBSIDIAN_OLED
    val colorScheme = if (isOled) ObsidianOledColorScheme else SunlitLightColorScheme
    val extended = if (isOled) {
        SolstrideExtendedColors(
            borderStrong = OledBorderGold,
            borderSubtle = Color(0xFF474E5D),
            rarityCommon = RarityCommonDark,
            rarityRare = RarityRareDark,
            rarityEpic = RarityEpicDark,
            raritySolar = RaritySolarDark,
            rarityUltraRare = RarityUltraRareDark,
            isOledBatterySaver = true
        )
    } else {
        SolstrideExtendedColors(
            borderStrong = SunlitBorderInk,
            borderSubtle = Color(0xFF8C7355),
            rarityCommon = RarityCommonLight,
            rarityRare = RarityRareLight,
            rarityEpic = RarityEpicLight,
            raritySolar = RaritySolarLight,
            rarityUltraRare = RarityUltraRareLight,
            isOledBatterySaver = false
        )
    }

    CompositionLocalProvider(LocalSolstrideColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SolstrideTypography,
            shapes = SolstrideShapes,
            content = content
        )
    }
}

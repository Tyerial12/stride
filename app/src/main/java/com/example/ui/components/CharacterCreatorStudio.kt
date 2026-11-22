package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HeroAvatarConfig
import com.example.data.model.HeroClass
import com.example.data.model.HeroNameCatalog
import com.example.data.model.HeroProfile
import com.example.data.model.ItemRarity
import com.example.domain.ClassSkillCatalog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCreatorStudio(
    profile: HeroProfile,
    isInitialCreation: Boolean,
    onConfirm: (name: String, heroClass: HeroClass, avatar: HeroAvatarConfig) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isInitialCreation) {
        BackHandler { onDismiss() }
    }

    val ext = LocalSolstrideColors.current
    var heroName by remember(profile.userId, profile.characterCreated) {
        mutableStateOf(
            if (isInitialCreation && (profile.displayName.isBlank() || profile.displayName == "Solar Vanguard")) {
                HeroNameCatalog.randomName(profile.resolvedClass())
            } else {
                profile.displayName.take(24)
            }
        )
    }
    var selectedClass by remember(profile.userId, profile.characterCreated) {
        mutableStateOf(profile.resolvedClass())
    }
    var avatar by remember(profile.userId, profile.characterCreated) {
        mutableStateOf(
            if (isInitialCreation && profile.avatarConfig == HeroAvatarConfig.DEFAULT_RAW) {
                HeroAvatarConfig.defaultForClass(profile.resolvedClass())
            } else {
                profile.resolvedAvatar()
            }
        )
    }

    val infinite = rememberInfiniteTransition(label = "creator_stage")
    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "creator_breath"
    )
    val auraPulse by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "creator_aura"
    )

    val trimmedName = heroName.trim()
    val nameValid = trimmedName.length in 2..24

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("character_creator_studio"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isInitialCreation) "FORGE YOUR STRIDER" else "SANCTUARY MIRROR",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isInitialCreation) "Create Your Hero" else "Customize Hero & Class",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                if (!isInitialCreation) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_character_mirror_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Sanctuary Mirror"
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live 2D Animated Hero Pedestal Preview
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(2.dp, ext.borderStrong),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(196.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_forge_pedestal),
                                contentDescription = "Hero creation forge pedestal",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0x55120D0A), Color(0x33161420), Color(0xBB1A110A))
                                        )
                                    )
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val groundY = size.height * 0.80f
                                drawHeroSprite(
                                    centerX = size.width * 0.50f,
                                    groundY = groundY,
                                    scale = 1.24f,
                                    bobY = sin(breathPhase) * 3.5.dp.toPx(),
                                    heroClass = selectedClass,
                                    avatar = avatar,
                                    weaponRarity = ItemRarity.SOLAR,
                                    armorRarity = null,
                                    relicRarity = ItemRarity.RARE,
                                    prestigeLevel = profile.prestigeLevel.toInt(),
                                    auraPulse = auraPulse,
                                    facingRight = true
                                )
                                drawVolumetricFogOverlay(
                                    fogDensity = 0.24f,
                                    isDreamMist = false,
                                    driftPhase = breathPhase
                                )
                            }

                            // Bottom Name & Class Tag
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xDD141118),
                                border = BorderStroke(1.dp, Color(0xFFFFB800))
                            ) {
                                Text(
                                    text = "${trimmedName.ifBlank { "Unnamed Hero" }} • ${selectedClass.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFF8E7),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 1. Hero Name Input + Randomizer
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1. HERO NAME",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = heroName,
                                    onValueChange = { if (it.length <= 24) heroName = it },
                                    label = { Text("Enter Hero Name (2–24 chars)") },
                                    singleLine = true,
                                    isError = !nameValid,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hero_name_input")
                                )
                                OutlinedButton(
                                    onClick = { heroName = HeroNameCatalog.randomName(selectedClass) },
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("random_hero_name_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Casino,
                                        contentDescription = "Generate random fantasy name",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Choose Class (4 Classes with Active Skills Preview)
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "2. CHOOSE YOUR CLASS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            HeroClass.entries.forEach { heroClass ->
                                val selected = heroClass == selectedClass
                                val activeSkills = ClassSkillCatalog.activeSkillsFor(heroClass)
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedClass = heroClass
                                            if (isInitialCreation) {
                                                avatar = HeroAvatarConfig.defaultForClass(heroClass)
                                            }
                                        }
                                        .testTag("creator_class_${heroClass.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                    imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Shield,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = heroClass.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Text(
                                                text = heroClass.roleTag,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = heroClass.passiveSummary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Active Skills: " + activeSkills.joinToString(" • ") {
                                                "${it.name} (Lv.${it.unlockLevel})"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Custom 2D Avatar Appearance Designer
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "3. DESIGN YOUR 2D AVATAR",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Hair Style
                            Text("Hair Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HeroAvatarConfig.HAIR_STYLE_LABELS.forEachIndexed { idx, label ->
                                    val selected = avatar.hairStyle == idx
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ext.borderSubtle),
                                        modifier = Modifier
                                            .clickable { avatar = avatar.copy(hairStyle = idx) }
                                            .testTag("hair_style_$idx")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            // Hair Color
                            SwatchRowSection(
                                label = "Hair Color (${HeroAvatarConfig.HAIR_COLOR_LABELS[avatar.hairColor]})",
                                hexColors = HeroAvatarConfig.HAIR_COLOR_HEXES,
                                selectedIndex = avatar.hairColor,
                                tagPrefix = "hair_color",
                                onSelect = { avatar = avatar.copy(hairColor = it) }
                            )

                            // Skin Tone
                            SwatchRowSection(
                                label = "Skin Tone (${HeroAvatarConfig.SKIN_TONE_LABELS[avatar.skinTone]})",
                                hexColors = HeroAvatarConfig.SKIN_TONE_HEXES,
                                selectedIndex = avatar.skinTone,
                                tagPrefix = "skin_tone",
                                onSelect = { avatar = avatar.copy(skinTone = it) }
                            )

                            // Eye Color
                            SwatchRowSection(
                                label = "Eye Color (${HeroAvatarConfig.EYE_COLOR_LABELS[avatar.eyeColor]})",
                                hexColors = HeroAvatarConfig.EYE_COLOR_HEXES,
                                selectedIndex = avatar.eyeColor,
                                tagPrefix = "eye_color",
                                onSelect = { avatar = avatar.copy(eyeColor = it) }
                            )

                            // Outfit & Cloak Palette
                            Text("Outfit & Cloak Palette", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HeroAvatarConfig.OUTFIT_PALETTES.forEachIndexed { idx, spec ->
                                    val selected = avatar.outfitPalette == idx
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else ext.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .clickable { avatar = avatar.copy(outfitPalette = idx) }
                                            .testTag("outfit_palette_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(spec.primaryHex))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(spec.trimHex))
                                            )
                                            Text(
                                                text = spec.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Radiant Aura
                            SwatchRowSection(
                                label = "Radiant Aura (${HeroAvatarConfig.AURA_LABELS[avatar.aura]})",
                                hexColors = HeroAvatarConfig.AURA_HEXES,
                                selectedIndex = avatar.aura,
                                tagPrefix = "aura_style",
                                onSelect = { avatar = avatar.copy(aura = it) }
                            )
                        }
                    }
                }
            }

            // Bottom Confirm Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isInitialCreation) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(52.dp)
                                .weight(0.38f)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Button(
                        onClick = { onConfirm(trimmedName, selectedClass, avatar) },
                        enabled = nameValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .weight(if (isInitialCreation) 1f else 0.62f)
                            .testTag("confirm_character_creator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInitialCreation) "Enter Sunhaven Plaza" else "Save Hero Look",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRowSection(
    label: String,
    hexColors: List<Long>,
    selectedIndex: Int,
    tagPrefix: String,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hexColors.forEachIndexed { idx, hex ->
                val selected = idx == selectedIndex
                Surface(
                    shape = CircleShape,
                    color = Color(hex),
                    border = BorderStroke(
                        if (selected) 3.dp else 1.5.dp,
                        if (selected) MaterialTheme.colorScheme.primary else Color(0x66FFFFFF)
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onSelect(idx) }
                        .testTag("${tagPrefix}_$idx")
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = if (hex == 0xFFFCE7D6L || hex == 0xFFE2E8F0L || hex == 0xFFF5CFAAL) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

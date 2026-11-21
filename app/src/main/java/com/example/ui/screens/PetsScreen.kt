package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HeroProfile
import com.example.data.model.OwnedPet
import com.example.data.model.PetCatalog
import com.example.data.model.PetFoodSpec
import com.example.data.model.PetGearSpec
import com.example.data.model.PetRarity
import com.example.data.model.PetType
import com.example.ui.components.ExpeditionLockBanner
import com.example.ui.components.PetRarityBadge
import com.example.ui.components.SolCoinBadge
import com.example.ui.components.SolstrideCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.components.petRarityColorFor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors
import com.example.widget.WidgetSnapshotState
import java.util.Locale

private enum class PetsSubSection(val label: String) {
    COMPANIONS("Companions (6)"),
    PET_SHOP("Pet Gear & Widget")
}

@Composable
fun PetsScreen(
    profile: HeroProfile,
    ownedPets: List<OwnedPet>,
    equippedPet: OwnedPet?,
    expeditionLocked: Boolean,
    expeditionRemainingMs: Long,
    onJumpToExpedition: () -> Unit,
    onAdoptPet: (PetType) -> Unit,
    onEquipPet: (PetType) -> Unit,
    @Suppress("UNUSED_PARAMETER") onBuyPetFood: (PetFoodSpec) -> Unit,
    onBuyPetGear: (PetGearSpec) -> Unit,
    widgetSnapshot: WidgetSnapshotState = WidgetSnapshotState(),
    onPinPetWidget: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSection by remember { mutableStateOf(PetsSubSection.COMPANIONS) }
    var selectedRarityFilter by remember { mutableStateOf<PetRarity?>(null) }
    val ext = LocalSolstrideColors.current
    val ownedMap = remember(ownedPets) { ownedPets.associateBy { it.resolvedType() } }
    val filteredPets = remember(selectedRarityFilter) {
        if (selectedRarityFilter == null) PetType.entries
        else PetType.entries.filter { it.rarity == selectedRarityFilter }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sanctuary Header Banner
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(2.dp, ext.borderStrong)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pets_sanctuary),
                        contentDescription = "Sunlit companion sanctuary pavilion",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xE0111318), Color(0x99111318), Color(0x33111318))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "COMPANION SANCTUARY",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color(0xFFFFF8E7)
                                )
                                Text(
                                    text = "${ownedPets.size} / 6 Adopted · 1 Equipped at a Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFFD166)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SolCoinBadge(
                                solCoins = profile.solCoins,
                                modifier = Modifier.testTag("pets_sol_coins_badge")
                            )
                            Text(
                                text = "Boss Wins: ${profile.trueBossWins} (+10 Coins/Win)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFF8E7)
                            )
                        }
                    }
                }
            }
        }

        if (expeditionLocked) {
            item {
                ExpeditionLockBanner(
                    remainingMs = expeditionRemainingMs,
                    onJumpToExpedition = onJumpToExpedition
                )
            }
        }

        // Equipped Companion Showcase
        item {
            EquippedCompanionCard(
                equippedPet = equippedPet,
                onSwitchToShop = { activeSection = PetsSubSection.PET_SHOP }
            )
        }

        // Exclusive Home Screen Pet Arena Widget Card (Only way to level up pets!)
        item {
            PetWidgetExclusiveArenaCard(
                equippedPet = equippedPet,
                widgetSnapshot = widgetSnapshot,
                onPinPetWidget = onPinPetWidget
            )
        }

        // Segmented Sub-View Switcher (Companions Roster vs Pet Shop)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PetsSubSection.entries.forEach { section ->
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        leadingIcon = {
                            Icon(
                                imageVector = if (section == PetsSubSection.COMPANIONS) Icons.Filled.Pets else Icons.Filled.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(section.label, style = MaterialTheme.typography.titleSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pets_section_${section.name.lowercase(Locale.US)}")
                    )
                }
            }
        }

        if (activeSection == PetsSubSection.COMPANIONS) {
            // Rarity Tier Filter Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedRarityFilter == null,
                            onClick = { selectedRarityFilter = null },
                            label = { Text("All (${PetType.entries.size})") },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pet_rarity_filter_all")
                        )
                    }
                    items(PetRarity.entries) { rarity ->
                        val count = PetType.entries.count { it.rarity == rarity }
                        val rarityColor = petRarityColorFor(rarity)
                        FilterChip(
                            selected = selectedRarityFilter == rarity,
                            onClick = {
                                selectedRarityFilter = if (selectedRarityFilter == rarity) null else rarity
                            },
                            label = {
                                Text(
                                    text = "${rarity.label} ($count)",
                                    color = if (selectedRarityFilter == rarity) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        rarityColor
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pet_rarity_filter_${rarity.name.lowercase(Locale.US)}")
                        )
                    }
                }
            }

            items(filteredPets, key = { it.name }) { petType ->
                val owned = ownedMap[petType]
                val isEquipped = equippedPet?.resolvedType() == petType
                val unlock = PetCatalog.evaluateUnlock(petType, profile)
                CompanionRosterCard(
                    petType = petType,
                    ownedPet = owned,
                    isEquipped = isEquipped,
                    unlockUnlocked = unlock.unlocked,
                    unlockLabel = unlock.progressLabel,
                    unlockFraction = unlock.progressFraction,
                    solCoins = profile.solCoins,
                    expeditionLocked = expeditionLocked,
                    onAdopt = { onAdoptPet(petType) },
                    onEquip = { onEquipPet(petType) }
                )
            }
        } else {
            // Pet Shop for Currently Equipped Pet
            item {
                SolstrideCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = "Pet Shop target companion",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (equippedPet != null) {
                                        "Outfitting: ${equippedPet.resolvedType().displayName} (Lv.${equippedPet.level})"
                                    } else {
                                        "No Companion Equipped"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                if (equippedPet != null) {
                                    PetRarityBadge(
                                        rarity = equippedPet.resolvedType().rarity,
                                        compact = true,
                                        modifier = Modifier.testTag("pet_shop_equipped_rarity_badge")
                                    )
                                }
                            }
                            Text(
                                text = if (equippedPet != null) {
                                    "Weapons & Armor equip directly to your pet. Level up your pet exclusively via the Home Screen Pet Arena Widget while Solstride is closed."
                                } else {
                                    "Adopt and equip a companion first to buy Pet Weapons, Pet Armor, and train on the Home Screen Pet Arena Widget."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Section: Widget-Exclusive Pet Training Reminder
            item {
                ShopSectionHeader(
                    icon = Icons.Filled.Widgets,
                    title = "Pet Leveling (Home Screen Widget Exclusive)",
                    subtitle = "Close Solstride to unlock the Pet Arena Widget: Feed with Sol Coins / Step Treats & Fight Wild Pets for exclusive Pet XP!"
                )
            }

            // Section: Pet Weapons
            item {
                ShopSectionHeader(
                    icon = Icons.Filled.Gavel,
                    title = "Pet Weapons (Equipped Pet Slot)",
                    subtitle = "Equips onto your active companion to raise Hero Attack power."
                )
            }
            items(PetCatalog.weaponShop, key = { it.id }) { weapon ->
                val isAlreadyEquipped = equippedPet?.equippedWeaponId == weapon.id
                PetGearShopCard(
                    gear = weapon,
                    alreadyEquipped = isAlreadyEquipped,
                    canBuy = !expeditionLocked && equippedPet != null && !isAlreadyEquipped && profile.solCoins >= weapon.coinCost,
                    onBuy = { onBuyPetGear(weapon) }
                )
            }

            // Section: Pet Armor
            item {
                ShopSectionHeader(
                    icon = Icons.Filled.Shield,
                    title = "Pet Armor (Equipped Pet Slot)",
                    subtitle = "Equips onto your active companion to raise Hero Defense & Max HP."
                )
            }
            items(PetCatalog.armorShop, key = { it.id }) { armor ->
                val isAlreadyEquipped = equippedPet?.equippedArmorId == armor.id
                PetGearShopCard(
                    gear = armor,
                    alreadyEquipped = isAlreadyEquipped,
                    canBuy = !expeditionLocked && equippedPet != null && !isAlreadyEquipped && profile.solCoins >= armor.coinCost,
                    onBuy = { onBuyPetGear(armor) }
                )
            }
        }
    }
}

@Composable
private fun EquippedCompanionCard(
    equippedPet: OwnedPet?,
    onSwitchToShop: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        strongBorder = true
    ) {
        if (equippedPet == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = "No companion equipped",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No Active Companion Equipped",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Win a Highland Boss battle (+10 Sol Coins) to adopt your first unique pet below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@SolstrideCard
        }

        val type = equippedPet.resolvedType()
        val bonus = PetCatalog.computeBonus(equippedPet)
        val weapon = PetCatalog.weaponById(equippedPet.equippedWeaponId)
        val armor = PetCatalog.armorById(equippedPet.equippedArmorId)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PetMedallionBadge(petType = type, sizeDp = 64)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = type.displayName, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PetRarityBadge(
                        rarity = type.rarity,
                        modifier = Modifier.testTag("equipped_pet_rarity_badge")
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, ext.borderStrong)
                    ) {
                        Text(
                            text = "LV.${equippedPet.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${type.elementalAffinity.symbol} ${type.elementalAffinity.label} Affinity • ${bonus.summaryText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Combat Combo: ${type.activeSkillName} (${type.activeSkillSummary})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TacticalStatBar(
            label = if (equippedPet.level >= PetCatalog.MAX_PET_LEVEL) "Pet Level MAX" else "Pet XP to Level ${equippedPet.level + 1}",
            current = equippedPet.xp,
            maximum = equippedPet.xpToNextLevel,
            barColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "PET WEAPON",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = weapon?.name ?: "None Equipped",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    if (weapon != null) {
                        Text(
                            text = "+${weapon.attackBonus} ATK" + if (weapon.defenseBonus > 0) " · +${weapon.defenseBonus} DEF" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "PET ARMOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = armor?.name ?: "None Equipped",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    if (armor != null) {
                        Text(
                            text = "+${armor.defenseBonus} DEF · +${armor.hpBonus} HP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSwitchToShop,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_pet_shop_quick_button"),
            border = BorderStroke(1.5.dp, ext.borderStrong)
        ) {
            Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Buy Pet Weapons & Armor")
        }
    }
}

@Composable
private fun PetWidgetExclusiveArenaCard(
    equippedPet: OwnedPet?,
    widgetSnapshot: WidgetSnapshotState,
    onPinPetWidget: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pet_widget_exclusive_arena_card"),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = "Pet Arena Widget Exclusive",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Pet Arena Home Widget (Only Way to Level)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Feed & Fight Wild Pets on your Home Screen when Solstride is closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, ext.borderStrong)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Widget locked while app is open",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "LOCKED IN-APP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("MOOD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "❤ ${widgetSnapshot.petMoodPct}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("WILD STAMINA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "⚡ ${widgetSnapshot.petWildStamina}/5",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("STEP TREATS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "🍖 ${widgetSnapshot.petStepTreats}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("WILD WINS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "🏆 ${widgetSnapshot.petTotalWildWins}",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Last Widget Arena Log: ${widgetSnapshot.petLastBattleSummary}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "• Walk 250 steps = +1 Wild Stamina • Walk 500 steps = +1 Free Treat • Auto-syncs XP, Level-Ups & Forge Ore when Solstride opens, then locks until app closes.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onPinPetWidget,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("pin_pet_arena_widget_button")
        ) {
            Icon(Icons.Filled.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (equippedPet != null) "Pin ${equippedPet.resolvedType().displayName} Arena Widget"
                else "Pin Pet Arena Widget to Home Screen"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionRosterCard(
    petType: PetType,
    ownedPet: OwnedPet?,
    isEquipped: Boolean,
    unlockUnlocked: Boolean,
    unlockLabel: String,
    unlockFraction: Float,
    solCoins: Long,
    expeditionLocked: Boolean,
    onAdopt: () -> Unit,
    onEquip: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val activeBonusText = if (ownedPet != null) {
        PetCatalog.computeBonus(ownedPet).summaryText
    } else {
        PetCatalog.previewBonusAtLevel1(petType)
    }

    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        strongBorder = isEquipped || petType.rarity == PetRarity.LEGENDARY
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            PetMedallionBadge(
                petType = petType,
                sizeDp = 56,
                locked = ownedPet == null && !unlockUnlocked
            )
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = petType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    PetRarityBadge(
                        rarity = petType.rarity,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .testTag("pet_rarity_badge_${petType.name.lowercase(Locale.US)}")
                    )
                    if (isEquipped) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Equipped companion",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(18.dp)
                        )
                    } else if (ownedPet == null && !unlockUnlocked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked companion",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${petType.elementalAffinity.symbol} ${petType.elementalAffinity.label} • " +
                        if (ownedPet != null) "Lv.${ownedPet.level} • $activeBonusText" else "Lv.1 Bonus: $activeBonusText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Active Combo: ${petType.activeSkillName} — ${petType.activeSkillSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = petType.lore,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (ownedPet == null && !unlockUnlocked) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ext.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Unlock Condition: ${petType.unlockRequirementSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { unlockFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$unlockLabel • Cost once unlocked: ${petType.coinCost} Sol Coins",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        when {
            ownedPet != null -> {
                Button(
                    onClick = onEquip,
                    enabled = !expeditionLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("equip_pet_${petType.name.lowercase(Locale.US)}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEquipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isEquipped) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (isEquipped) "Equipped (Tap to Unequip)" else "Equip ${petType.displayName}")
                }
            }
            unlockUnlocked -> {
                Button(
                    onClick = onAdopt,
                    enabled = !expeditionLocked && solCoins >= petType.coinCost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("adopt_pet_${petType.name.lowercase(Locale.US)}")
                ) {
                    Icon(Icons.Filled.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (solCoins >= petType.coinCost) {
                            "Adopt for ${petType.coinCost} Sol Coins"
                        } else {
                            "Need ${petType.coinCost} Sol Coins"
                        }
                    )
                }
            }
            else -> {
                OutlinedButton(
                    onClick = onAdopt,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("locked_pet_${petType.name.lowercase(Locale.US)}")
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Locked — Complete Milestone First")
                }
            }
        }
    }
}

@Composable
private fun ShopSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PetGearShopCard(
    gear: PetGearSpec,
    alreadyEquipped: Boolean,
    canBuy: Boolean,
    onBuy: () -> Unit
) {
    val statSummary = buildList {
        if (gear.attackBonus > 0) add("+${gear.attackBonus} ATK")
        if (gear.defenseBonus > 0) add("+${gear.defenseBonus} DEF")
        if (gear.hpBonus > 0) add("+${gear.hpBonus} HP")
    }.joinToString(" • ")

    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        strongBorder = alreadyEquipped
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = gear.name, style = MaterialTheme.typography.titleMedium)
                    if (alreadyEquipped) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Equipped on pet",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "TIER ${gear.tier} • $statSummary",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gear.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onBuy,
                enabled = canBuy,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("buy_${gear.id}")
            ) {
                if (alreadyEquipped) {
                    Text("Equipped")
                } else {
                    Icon(Icons.Filled.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${gear.coinCost}")
                }
            }
        }
    }
}

@Composable
private fun PetMedallionBadge(
    petType: PetType,
    sizeDp: Int,
    locked: Boolean = false
) {
    val ext = LocalSolstrideColors.current
    val accent = petRarityColorFor(petType.rarity)
    val surfaceBg = if (locked) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.16f)

    Surface(
        modifier = Modifier.size(sizeDp.dp),
        shape = RoundedCornerShape(14.dp),
        color = surfaceBg,
        border = BorderStroke(2.dp, if (locked) ext.borderSubtle else accent)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val tint = if (locked) Color.Gray else accent

            // Outer astrolabe halo
            drawCircle(
                color = tint.copy(alpha = 0.3f),
                radius = minOf(w, h) * 0.48f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Distinct geometric silhouette per pet archetype
            val path = Path()
            when (petType) {
                PetType.SUNFOX_KIT -> {
                    // Pointed fox ears & diamond snout
                    path.moveTo(w * 0.2f, h * 0.22f)
                    path.lineTo(w * 0.42f, h * 0.40f)
                    path.lineTo(w * 0.58f, h * 0.40f)
                    path.lineTo(w * 0.8f, h * 0.22f)
                    path.lineTo(w * 0.78f, h * 0.62f)
                    path.lineTo(cx, h * 0.86f)
                    path.lineTo(w * 0.22f, h * 0.62f)
                    path.close()
                }
                PetType.AEGIS_SCARAB -> {
                    // Armored hexagon carapace
                    path.moveTo(cx, h * 0.14f)
                    path.lineTo(w * 0.82f, h * 0.36f)
                    path.lineTo(w * 0.76f, h * 0.78f)
                    path.lineTo(cx, h * 0.88f)
                    path.lineTo(w * 0.24f, h * 0.78f)
                    path.lineTo(w * 0.18f, h * 0.36f)
                    path.close()
                }
                PetType.DUNE_FALCON -> {
                    // Swept falcon wings
                    path.moveTo(cx, h * 0.2f)
                    path.lineTo(w * 0.9f, h * 0.38f)
                    path.lineTo(w * 0.65f, h * 0.56f)
                    path.lineTo(cx, h * 0.85f)
                    path.lineTo(w * 0.35f, h * 0.56f)
                    path.lineTo(w * 0.1f, h * 0.38f)
                    path.close()
                }
                PetType.CINDER_LYNX -> {
                    // Sharp twin-flame predator crest
                    path.moveTo(w * 0.25f, h * 0.16f)
                    path.lineTo(cx, h * 0.35f)
                    path.lineTo(w * 0.75f, h * 0.16f)
                    path.lineTo(w * 0.84f, h * 0.58f)
                    path.lineTo(cx, h * 0.86f)
                    path.lineTo(w * 0.16f, h * 0.58f)
                    path.close()
                }
                PetType.OASIS_TORTOISE -> {
                    // Domed sandstone bastion shell
                    path.moveTo(w * 0.16f, h * 0.68f)
                    path.lineTo(w * 0.28f, h * 0.34f)
                    path.lineTo(w * 0.5f, h * 0.22f)
                    path.lineTo(w * 0.72f, h * 0.34f)
                    path.lineTo(w * 0.84f, h * 0.68f)
                    path.close()
                }
                PetType.SOLAR_PHOENIX -> {
                    // Radiant 4-point solar crown & wings
                    path.moveTo(cx, h * 0.10f)
                    path.lineTo(w * 0.62f, h * 0.38f)
                    path.lineTo(w * 0.92f, h * 0.45f)
                    path.lineTo(w * 0.64f, h * 0.62f)
                    path.lineTo(cx, h * 0.90f)
                    path.lineTo(w * 0.36f, h * 0.62f)
                    path.lineTo(w * 0.08f, h * 0.45f)
                    path.lineTo(w * 0.38f, h * 0.38f)
                    path.close()
                }
            }
            drawPath(path = path, color = tint)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

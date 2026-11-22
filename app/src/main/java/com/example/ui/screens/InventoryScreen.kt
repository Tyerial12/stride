package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassSpecialization
import com.example.data.model.DerivedHeroStats
import com.example.data.model.ElementalAffinity
import com.example.data.model.GearPerkType
import com.example.data.model.HardcoreRpgState
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.HeroTitleSpec
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.MercenaryContractSpec
import com.example.data.model.OwnedPet
import com.example.data.model.WorldFogState
import com.example.domain.AlchemistRecipeSpec
import com.example.domain.BlackMarketOfferSpec
import com.example.domain.ExpeditionEngine
import com.example.domain.ForgeBlueprintTier
import com.example.domain.ProgressionEngine
import com.example.domain.QuestStatus
import com.example.ui.MasteryAttribute
import com.example.ui.components.ExpeditionLockBanner
import com.example.ui.components.HeroShowcaseCard
import com.example.ui.components.LootVaultSection
import com.example.ui.components.QuestsAndPrestigePanel
import com.example.ui.components.SkillTreePanel
import com.example.ui.components.SolCoinBadge
import com.example.ui.components.SolstrideCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.components.rarityColorFor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

private enum class HeroSubTab(val label: String) {
    GEAR("Gear"),
    CRAFT("Craft"),
    BUILD("Build"),
    QUESTS("Quests")
}

private enum class InventoryFilter(val label: String, val slotFilter: ItemSlot? = null) {
    ALL("All"),
    EQUIPPED("Equipped"),
    WEAPON("Weapon", ItemSlot.WEAPON),
    ARMOR("Armor", ItemSlot.ARMOR),
    HELM("Helm", ItemSlot.HELM),
    BOOTS("Boots", ItemSlot.BOOTS),
    RING("Ring", ItemSlot.RING),
    RELIC("Relic", ItemSlot.RELIC),
    ELIXIRS("Elixirs", ItemSlot.CONSUMABLE)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryScreen(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    inventory: List<InventoryItem>,
    chests: List<LootChest>,
    equippedPet: OwnedPet?,
    questStatuses: List<QuestStatus>,
    expeditionLocked: Boolean,
    expeditionRemainingMs: Long,
    battleActive: Boolean,
    onJumpToExpedition: () -> Unit,
    onAllocateMastery: (MasteryAttribute) -> Unit,
    onUpgradeSkillNode: (Int) -> Unit,
    onClaimQuest: (QuestStatus) -> Unit,
    onTriggerPrestige: () -> Unit,
    onOpenChest: (LootChest) -> Unit,
    onSelectClass: (HeroClass) -> Unit,
    onSelectSpecialization: (ClassSpecialization) -> Unit = {},
    onSelectHeroTitle: (HeroTitleSpec) -> Unit = {},
    onEquipOrUse: (InventoryItem) -> Unit,
    onUpgradeItem: (InventoryItem, Boolean) -> Unit,
    onRepairItem: (InventoryItem) -> Unit = {},
    onRepairAllEquipped: () -> Unit = {},
    onRerollItemPerk: (InventoryItem) -> Unit = {},
    onAscendItemRarity: (InventoryItem) -> Unit = {},
    onSocketRune: (InventoryItem, ElementalAffinity) -> Unit = { _, _ -> },
    onForgeCustomEquipment: (ItemSlot, ForgeBlueprintTier, ElementalAffinity?, Boolean) -> Unit = { _, _, _, _ -> },
    onBrewAlchemistRecipe: (AlchemistRecipeSpec) -> Unit = {},
    onVisitTownMarket: () -> Unit = {},
    onVisitTownSkillTrainer: () -> Unit = {},
    onResetAllProgress: () -> Unit = {},
    onSalvageItem: (InventoryItem) -> Unit,
    onOpenCharacterMirror: () -> Unit = {},
    worldFog: WorldFogState = WorldFogState(),
    isHeroSleeping: Boolean = false,
    sleepSourceLabel: String = "",
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(HeroSubTab.GEAR) }
    var selectedFilter by remember { mutableStateOf(InventoryFilter.ALL) }
    var useForgeStabilizer by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val ext = LocalSolstrideColors.current

    val claimableQuestCount = remember(questStatuses) { questStatuses.count { it.canClaim } }

    val filteredItems = remember(inventory, selectedFilter) {
        when {
            selectedFilter == InventoryFilter.ALL -> inventory
            selectedFilter == InventoryFilter.EQUIPPED -> inventory.filter { it.equipped }
            selectedFilter.slotFilter != null -> inventory.filter { it.resolvedSlot() == selectedFilter.slotFilter }
            else -> inventory
        }.sortedWith(
            compareByDescending<InventoryItem> { it.equipped }
                .thenByDescending { it.resolvedRarity().ordinal }
                .thenByDescending { it.upgradeLevel }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "WARNING: You are about to lose everything!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently reset all progress? You will lose your Hero Level (${profile.level}), Prestige (${profile.prestigeLevel}), all ${profile.solCoins} Sol Coins, ${profile.sunshards} Sunshards, all 6-Slot Equipped Gear, Sealed Loot Chests, Companion Pets, Bestiary Codex, and Step History. Your hero will restart at Level 1 Novice.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetAllProgress()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_reset_all_progress_button")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yes, Lose Everything & Reset")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_reset_all_progress_button")
                ) {
                    Text("Cancel (Keep Progress)")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (expeditionLocked) {
            item {
                ExpeditionLockBanner(
                    remainingMs = expeditionRemainingMs,
                    onJumpToExpedition = onJumpToExpedition
                )
            }
        }

        // 4-Way Purpose-Sorted Hero Switcher: Gear | Craft | Build | Quests
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HeroSubTab.entries.forEachIndexed { idx, tab ->
                    val label = when (tab) {
                        HeroSubTab.GEAR -> if (chests.isNotEmpty()) "Gear (${chests.size})" else tab.label
                        HeroSubTab.CRAFT -> tab.label
                        HeroSubTab.BUILD -> if (profile.unspentPoints > 0L) "Build (${profile.unspentPoints})" else tab.label
                        HeroSubTab.QUESTS -> if (claimableQuestCount > 0) "Quests ($claimableQuestCount)" else tab.label
                    }
                    SegmentedButton(
                        selected = activeSubTab == tab,
                        onClick = { activeSubTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(idx, HeroSubTab.entries.size),
                        modifier = Modifier.testTag("hero_subtab_${tab.name.lowercase()}"),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    HeroSubTab.GEAR -> Icons.Filled.Shield
                                    HeroSubTab.CRAFT -> Icons.Filled.Build
                                    HeroSubTab.BUILD -> Icons.Filled.AccountTree
                                    HeroSubTab.QUESTS -> Icons.Filled.EmojiEvents
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        when (activeSubTab) {
            HeroSubTab.CRAFT -> {
                // Custom Weapon & Armor Forging Anvil (Craft from Step & Battle Materials)
                item {
                    CustomGearForgeCard(
                        profile = profile,
                        hardcoreState = hardcoreState,
                        expeditionLocked = expeditionLocked,
                        onForgeCustomEquipment = onForgeCustomEquipment
                    )
                }

                // Alchemist Crafting & Town Market Redirect (All buying happens strictly in Sunhaven Plaza)
                item {
                    AlchemistCraftingPanel(
                        profile = profile,
                        hardcoreState = hardcoreState,
                        expeditionLocked = expeditionLocked,
                        onBrewAlchemistRecipe = onBrewAlchemistRecipe,
                        onVisitTownMarket = onVisitTownMarket
                    )
                }
            }
            HeroSubTab.BUILD -> {
                item {
                    ClassSpecializationPanel(
                        profile = profile,
                        stats = stats,
                        hardcoreState = hardcoreState,
                        expeditionLocked = expeditionLocked,
                        onSelectClass = onSelectClass,
                        onSelectSpecialization = onSelectSpecialization
                    )
                }

                // Hero Leveling & Core Mastery Allocation Card
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
                            Column {
                                Text(
                                    text = "Level ${profile.level} Mastery",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "Power ${stats.computedHeroPower} • ATK ${stats.totalAttack} • DEF ${stats.totalDefense}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (profile.unspentPoints > 0L) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.5.dp, ext.borderStrong)
                            ) {
                                Text(
                                    text = "${profile.unspentPoints} PTS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (profile.unspentPoints > 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        TacticalStatBar(
                            label = "XP to Level ${profile.level + 1}",
                            current = profile.xp,
                            maximum = profile.xpToNextLevel,
                            barColor = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        MasteryRow(
                            label = "Might",
                            detail = "+3 Attack per rank",
                            rank = profile.might,
                            canAllocate = !expeditionLocked && profile.unspentPoints > 0L,
                            testTag = "allocate_might_button",
                            onAllocate = { onAllocateMastery(MasteryAttribute.MIGHT) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MasteryRow(
                            label = "Vigor",
                            detail = "+10 Max HP & +2 DEF",
                            rank = profile.vigor,
                            canAllocate = !expeditionLocked && profile.unspentPoints > 0L,
                            testTag = "allocate_vigor_button",
                            onAllocate = { onAllocateMastery(MasteryAttribute.VIGOR) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MasteryRow(
                            label = "Endurance",
                            detail = "+5 Max Stride Energy",
                            rank = profile.endurance,
                            canAllocate = !expeditionLocked && profile.unspentPoints > 0L,
                            testTag = "allocate_endurance_button",
                            onAllocate = { onAllocateMastery(MasteryAttribute.ENDURANCE) }
                        )
                    }
                }

                item {
                    SkillTreePanel(
                        profile = profile,
                        onVisitTownSkillTrainer = onVisitTownSkillTrainer
                    )
                }
            }
            HeroSubTab.QUESTS -> {
                item {
                    QuestsAndPrestigePanel(
                        profile = profile,
                        questStatuses = questStatuses,
                        expeditionLocked = expeditionLocked,
                        battleActive = battleActive,
                        onClaimQuest = onClaimQuest,
                        onTriggerPrestige = onTriggerPrestige
                    )
                }
                item {
                    BestiaryAndTitlesPanel(
                        profile = profile,
                        stats = stats,
                        hardcoreState = hardcoreState,
                        onSelectHeroTitle = onSelectHeroTitle
                    )
                }
                item {
                    DangerZoneResetCard(
                        onOpenResetConfirmation = { showResetConfirmDialog = true }
                    )
                }
            }
            HeroSubTab.GEAR -> {
                // Live 2D Hero Showcase with Custom Avatar, 6-Slot Equipped Gear & Active Perks
                item {
                    HeroShowcaseCard(
                        profile = profile,
                        stats = stats,
                        inventory = inventory,
                        equippedPet = equippedPet,
                        isSleeping = isHeroSleeping,
                        sleepSourceLabel = sleepSourceLabel,
                        worldFog = worldFog
                    )
                }

                // Sanctuary Mirror (Rename Hero, Change Class & Customize 2D Appearance)
                item {
                    OutlinedButton(
                        onClick = onOpenCharacterMirror,
                        enabled = !expeditionLocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("hero_tab_character_mirror_button"),
                        border = BorderStroke(1.5.dp, ext.borderStrong)
                    ) {
                        Icon(Icons.Filled.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sanctuary Mirror — Edit Name, Class & 2D Look",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Persistent Sealed Loot Chest Vault
                item {
                    LootVaultSection(
                        chests = chests,
                        onOpenChest = onOpenChest
                    )
                }

                // Blacksmith Masterwork Forge Controls (Durability Repair All, Stabilizer Seal, Rune Pouch)
                item {
                    BlacksmithForgeHeaderCard(
                        profile = profile,
                        stats = stats,
                        hardcoreState = hardcoreState,
                        inventory = inventory,
                        expeditionLocked = expeditionLocked,
                        useStabilizer = useForgeStabilizer,
                        onToggleStabilizer = { useForgeStabilizer = !useForgeStabilizer },
                        onRepairAllEquipped = onRepairAllEquipped
                    )
                }

                // Inventory Header + Currencies + 6-Slot Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "6-Slot Armory (${inventory.size})",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SolCoinBadge(solCoins = profile.solCoins)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.5.dp, ext.borderStrong)
                            ) {
                                Text(
                                    text = "${profile.sunshards} SHARDS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InventoryFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("inv_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    item {
                        SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Inventory2,
                                    contentDescription = "Empty inventory category",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "No items in this slot filter yet. Open Sealed Chests or visit Zari's Market in Sunhaven Town!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredItems, key = { it.id.ifBlank { it.name + it.slot } }) { item ->
                        InventoryItemCard(
                            item = item,
                            sunshards = profile.sunshards,
                            solCoins = profile.solCoins,
                            hardcoreState = hardcoreState,
                            useStabilizer = useForgeStabilizer,
                            expeditionLocked = expeditionLocked,
                            onEquipOrUse = { onEquipOrUse(item) },
                            onUpgrade = { onUpgradeItem(item, useForgeStabilizer) },
                            onRepair = { onRepairItem(item) },
                            onRerollPerk = { onRerollItemPerk(item) },
                            onAscendRarity = { onAscendItemRarity(item) },
                            onSocketRune = { affinity -> onSocketRune(item, affinity) },
                            onSalvage = { onSalvageItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomGearForgeCard(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    expeditionLocked: Boolean,
    onForgeCustomEquipment: (ItemSlot, ForgeBlueprintTier, ElementalAffinity?, Boolean) -> Unit
) {
    val ext = LocalSolstrideColors.current
    val craftableSlots = remember {
        listOf(
            ItemSlot.WEAPON,
            ItemSlot.ARMOR,
            ItemSlot.HELM,
            ItemSlot.BOOTS,
            ItemSlot.RING,
            ItemSlot.RELIC
        )
    }
    var selectedSlot by remember { mutableStateOf(ItemSlot.ARMOR) }
    var selectedTier by remember { mutableStateOf(ForgeBlueprintTier.BRONZE_SURVIVOR) }
    var selectedElement by remember { mutableStateOf<ElementalAffinity?>(null) }
    var useStarforgedIngot by remember { mutableStateOf(false) }

    val ingotAvailable = hardcoreState.starforgedIngots > 0L
    val effectiveUseIngot = useStarforgedIngot && ingotAvailable
    val essenceCost = selectedTier.essenceCost + if (selectedElement != null) 4L else 0L
    val meetsLevel = profile.level >= selectedTier.minHeroLevel.toLong()
    val canAfford = meetsLevel &&
        hardcoreState.sunsteelOre >= selectedTier.oreCost &&
        hardcoreState.aetherHerbs >= selectedTier.herbCost &&
        hardcoreState.elementalEssence >= essenceCost &&
        profile.sunshards >= selectedTier.sunshardCost

    SolstrideCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_gear_forge_card"),
        strongBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Custom Weapon & Armor Anvil",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Forge your own Weapons & Armor from gathered materials (22% Masterwork Crit!)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "1. Select Gear Slot",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            craftableSlots.forEach { slot ->
                FilterChip(
                    selected = selectedSlot == slot,
                    onClick = { selectedSlot = slot },
                    label = { Text(slot.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("forge_slot_${slot.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "2. Select Blueprint Tier",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ForgeBlueprintTier.entries.forEach { tier ->
                val unlocked = profile.level >= tier.minHeroLevel.toLong()
                FilterChip(
                    selected = selectedTier == tier,
                    onClick = { selectedTier = tier },
                    label = {
                        Text(
                            text = if (unlocked) tier.label else "${tier.label} (Lv.${tier.minHeroLevel})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("forge_tier_${tier.tierNumber}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "3. Elemental Infusion (Optional, +4 Essence)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedElement == null,
                onClick = { selectedElement = null },
                label = { Text("Pure Steel", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("forge_elem_none")
            )
            ElementalAffinity.entries.forEach { aff ->
                FilterChip(
                    selected = selectedElement == aff,
                    onClick = { selectedElement = aff },
                    label = { Text("${aff.symbol} ${aff.label}", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("forge_elem_${aff.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, ext.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Recipe Cost • ${selectedTier.baseRarity.label}+ ${selectedSlot.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "⛏ ${selectedTier.oreCost} Ore (have ${hardcoreState.sunsteelOre})  •  🌿 ${selectedTier.herbCost} Herbs (have ${hardcoreState.aetherHerbs})",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "✨ $essenceCost Essence (have ${hardcoreState.elementalEssence})  •  ☀ ${selectedTier.sunshardCost} Shards (have ${profile.sunshards})",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "4. Starforged Masterwork Ingot (Owned: ${hardcoreState.starforgedIngots})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Buy from the Rare Materials Market in Sunhaven Plaza • Guarantees +1 Rarity Tier & +18% Power",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = effectiveUseIngot,
                onCheckedChange = { useStarforgedIngot = it },
                enabled = !expeditionLocked && ingotAvailable,
                modifier = Modifier.testTag("forge_starforged_ingot_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onForgeCustomEquipment(selectedSlot, selectedTier, selectedElement, effectiveUseIngot) },
            enabled = !expeditionLocked && canAfford,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("forge_custom_gear_button")
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    !meetsLevel -> "Requires Hero Level ${selectedTier.minHeroLevel}"
                    canAfford && effectiveUseIngot -> "Masterwork Forge ${selectedSlot.label} (+1 Tier!)"
                    canAfford -> "Forge Custom ${selectedSlot.label} (${selectedTier.baseRarity.label}+)"
                    else -> "Gather More Materials (Walk / Battle / Town)"
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlacksmithForgeHeaderCard(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    inventory: List<InventoryItem>,
    expeditionLocked: Boolean,
    useStabilizer: Boolean,
    onToggleStabilizer: () -> Unit,
    onRepairAllEquipped: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val damagedCount = inventory.count { it.equipped && it.resolvedSlot().isEquipment && hardcoreState.durabilityFor(it.id) < 100 }

    SolstrideCard(modifier = Modifier.fillMaxWidth(), strongBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Blacksmith Masterwork Forge", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Ore: ${hardcoreState.sunsteelOre} • Herbs: ${hardcoreState.aetherHerbs} • Essence: ${hardcoreState.elementalEssence}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(
                onClick = onRepairAllEquipped,
                enabled = !expeditionLocked && damagedCount > 0,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("repair_all_equipped_button"),
                border = BorderStroke(1.5.dp, ext.borderStrong)
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (damagedCount > 0) "Repair All ($damagedCount)" else "Gear 100%")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stabilizer Seal (+${ExpeditionEngine.FORGE_STABILIZER_SOL_COIN_COST} Sol Coins on +6..+15)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Guarantees 100% success on risky +6 to +15 Masterwork Tempering (prevents durability loss)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useStabilizer,
                onCheckedChange = { onToggleStabilizer() },
                enabled = !expeditionLocked,
                modifier = Modifier.testTag("forge_stabilizer_switch")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Rune Pouch (Tap any Rune badge on a Gear card below to socket it):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ElementalAffinity.entries.forEach { aff ->
                val count = hardcoreState.runeCount(aff)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (count > 0) MaterialTheme.colorScheme.primary else ext.borderSubtle)
                ) {
                    Text(
                        text = "${aff.symbol} ${aff.label}: $count",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlchemistCraftingPanel(
    profile: HeroProfile,
    hardcoreState: HardcoreRpgState,
    expeditionLocked: Boolean,
    onBrewAlchemistRecipe: (AlchemistRecipeSpec) -> Unit,
    onVisitTownMarket: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Town Market Callout (Buying happens strictly in Sunhaven Plaza)
        SolstrideCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            strongBorder = true
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Need Materials, Black-Market Goods or Mercenaries?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "All shopping takes place exclusively in Sunhaven Plaza.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onVisitTownMarket,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("craft_visit_town_market_button")
                ) {
                    Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Town Market")
                }
            }
        }

        // Alchemist Apothecary (Brew Elixirs, Cure Battle Wounds & Distill Runes from Step Materials)
        SolstrideCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Alchemist Apothecary", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Herbs: ${hardcoreState.aetherHerbs} • Ore: ${hardcoreState.sunsteelOre} • Essence: ${hardcoreState.elementalEssence}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressionEngine.alchemistRecipes.forEach { recipe ->
                    val canBrew = !expeditionLocked &&
                        profile.solCoins >= recipe.solCoinCost &&
                        hardcoreState.aetherHerbs >= recipe.herbCost &&
                        hardcoreState.sunsteelOre >= recipe.oreCost &&
                        hardcoreState.elementalEssence >= recipe.essenceCost
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ext.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(recipe.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = recipe.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val costLine = buildList {
                                    if (recipe.herbCost > 0L) add("${recipe.herbCost} Herbs")
                                    if (recipe.oreCost > 0L) add("${recipe.oreCost} Ore")
                                    if (recipe.essenceCost > 0L) add("${recipe.essenceCost} Essence")
                                    if (recipe.solCoinCost > 0L) add("${recipe.solCoinCost} Coins")
                                }.joinToString(" + ")
                                Text(
                                    text = "Requires: $costLine",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onBrewAlchemistRecipe(recipe) },
                                enabled = canBrew,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("brew_recipe_${recipe.id}")
                            ) {
                                Text("Brew")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassSpecializationPanel(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    expeditionLocked: Boolean,
    onSelectClass: (HeroClass) -> Unit,
    onSelectSpecialization: (ClassSpecialization) -> Unit
) {
    val ext = LocalSolstrideColors.current
    SolstrideCard(modifier = Modifier.fillMaxWidth(), strongBorder = true) {
        Text(text = "Base Class & Advanced Specialization", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeroClass.entries.forEach { cls ->
                val active = profile.resolvedClass() == cls
                FilterChip(
                    selected = active,
                    enabled = !expeditionLocked,
                    onClick = { onSelectClass(cls) },
                    label = { Text(cls.displayTitle) },
                    modifier = Modifier.testTag("class_${cls.name.lowercase()}_chip")
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Choose Class Specialization (Unlocks at Level 3 • Grants Elemental Ultimate Ability):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassSpecialization.forClass(profile.resolvedClass()).forEach { spec ->
                val isSelected = stats.activeSpecialization == spec
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else ext.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${spec.title} (${spec.affinity.symbol} ${spec.affinity.label})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Passive: ${spec.passiveSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Ultimate: ${spec.ultimateName} — ${spec.ultimateSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSelectSpecialization(spec) },
                            enabled = !expeditionLocked && !isSelected,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("select_spec_${spec.name.lowercase()}")
                        ) {
                            Text(if (isSelected) "Active" else "Specialize")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BestiaryAndTitlesPanel(
    profile: HeroProfile,
    stats: DerivedHeroStats,
    hardcoreState: HardcoreRpgState,
    onSelectHeroTitle: (HeroTitleSpec) -> Unit
) {
    val ext = LocalSolstrideColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero Achievement Titles
        SolstrideCard(modifier = Modifier.fillMaxWidth(), strongBorder = true) {
            Text("Achievement Titles (Permanent Stat Bonus)", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Equipped Title: ${stats.activeTitle.title} (${stats.activeTitle.bonusSummary})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroTitleSpec.entries.forEach { title ->
                    val isEquipped = stats.activeTitle == title
                    val unlocked = title.isUnlocked(profile, hardcoreState.codexTotalKills())
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEquipped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isEquipped) MaterialTheme.colorScheme.primary else ext.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${title.bonusSummary} • ${title.requirementLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onSelectHeroTitle(title) },
                                enabled = unlocked && !isEquipped,
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("select_title_${title.id}")
                            ) {
                                Text(
                                    when {
                                        isEquipped -> "Equipped"
                                        unlocked -> "Equip"
                                        else -> "Locked"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bestiary Codex
        SolstrideCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Bestiary Codex (${hardcoreState.bestiaryDefeatsMap.size}/${ProgressionEngine.bestiaryCatalog.size} Discovered)",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Collection Bonus: +${stats.codexBonusSummary}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressionEngine.bestiaryCatalog.forEach { entry ->
                    val kills = hardcoreState.bestiaryKills(entry.bossKey)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (kills > 0) MaterialTheme.colorScheme.primary else ext.borderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.affinity.symbol} ${entry.bossName} (Realm ${entry.realmNumber})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = entry.lore,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (kills > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = if (kills > 0) "SLAIN x$kills" else "UNSEEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
private fun DangerZoneResetCard(
    onOpenResetConfirmation: () -> Unit
) {
    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        strongBorder = true
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Danger Zone Reset All Progress",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Danger Zone: Reset All Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Permanently wipes your Hero Level, Prestige, Sol Coins, Sunshards, 6-Slot Gear, Chests, Pets & Step History back to a Level 1 Novice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onOpenResetConfirmation,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_reset_all_progress_dialog_button")
        ) {
            Icon(Icons.Filled.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Progress (Lose Everything)", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MasteryRow(
    label: String,
    detail: String,
    rank: Long,
    canAllocate: Boolean,
    testTag: String,
    onAllocate: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, ext.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "$label  •  Rank $rank", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onAllocate,
                enabled = canAllocate,
                modifier = Modifier
                    .height(48.dp)
                    .testTag(testTag),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Upgrade $label", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+1")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    sunshards: Long,
    solCoins: Long,
    hardcoreState: HardcoreRpgState,
    useStabilizer: Boolean,
    expeditionLocked: Boolean,
    onEquipOrUse: () -> Unit,
    onUpgrade: () -> Unit,
    onRepair: () -> Unit,
    onRerollPerk: () -> Unit,
    onAscendRarity: () -> Unit,
    onSocketRune: (ElementalAffinity) -> Unit,
    onSalvage: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val rarity = item.resolvedRarity()
    val slot = item.resolvedSlot()
    val rarityTint = rarityColorFor(rarity)
    val upgradeShardCost = item.upgradeCost()
    val upgradeCoinCost = item.temperSolCoinCost() +
        if (useStabilizer && item.upgradeLevel >= 5L) ExpeditionEngine.FORGE_STABILIZER_SOL_COIN_COST else 0L
    val temperChance = if (useStabilizer) 100 else item.temperSuccessChancePct()
    val isUltraRare = rarity == ItemRarity.ULTRA_RARE
    val (perk, perkVal) = item.resolvedPerk()
    val socketedRune = item.resolvedRune()
    val durabilityPct = if (slot.isEquipment) hardcoreState.durabilityFor(item.id) else 100

    SolstrideCard(
        modifier = Modifier.fillMaxWidth(),
        strongBorder = item.equipped || isUltraRare
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.upgradeLevel > 0L) "${item.name} +${item.upgradeLevel}" else item.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (item.equipped) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Equipped item",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val runeSuffix = socketedRune?.let { " • ${it.symbol} ${it.label.uppercase()} RUNE" } ?: ""
                Text(
                    text = "${rarity.label.uppercase()} • ${slot.label.uppercase()}" +
                        (if (slot == ItemSlot.CONSUMABLE) " • QTY ${item.quantity}" else " • PWR ${item.gearPowerScore(durabilityPct)} • DUR $durabilityPct%") +
                        runeSuffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = rarityTint,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!item.equipped) {
                IconButton(
                    onClick = onSalvage,
                    enabled = !expeditionLocked,
                    modifier = Modifier.testTag("salvage_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Salvage ${item.name} for Sunshards & Ore",
                        tint = if (expeditionLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (slot.isEquipment && durabilityPct < 100) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (durabilityPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (durabilityPct == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRepair,
                    enabled = !expeditionLocked &&
                        solCoins >= ExpeditionEngine.REPAIR_ITEM_SOL_COIN_COST &&
                        hardcoreState.sunsteelOre >= ExpeditionEngine.REPAIR_ITEM_ORE_COST,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("repair_${item.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (durabilityPct == 0) "SHATTERED • Repair (4C+2O)" else "Repair (4C+2O)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val statLine = buildList {
            if (item.attackBonus > 0L) add("+${item.attackBonus} ATK")
            if (item.defenseBonus > 0L) add("+${item.defenseBonus} DEF")
            if (item.hpBonus > 0L) add("+${item.hpBonus} HP")
            if (item.energyBonus > 0L) add("+${item.energyBonus} ENERGY")
            if (item.healAmount > 0L) add("HEALS +${item.healAmount} HP")
        }.joinToString("  •  ")

        if (statLine.isNotBlank()) {
            Text(
                text = statLine,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (perk != GearPerkType.NONE && perkVal > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "PERK: ${perk.formatSummary(perkVal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        val cleanDesc = item.cleanDescription()
        if (cleanDesc.isNotBlank()) {
            Text(
                text = cleanDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEquipOrUse,
                enabled = !expeditionLocked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("equip_use_${item.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.equipped) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (item.equipped) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = when {
                        slot == ItemSlot.CONSUMABLE -> "Drink Elixir"
                        item.equipped -> "Unequip"
                        else -> "Equip"
                    }
                )
            }
            if (slot != ItemSlot.CONSUMABLE) {
                OutlinedButton(
                    onClick = onUpgrade,
                    enabled = !expeditionLocked &&
                        item.upgradeLevel < 15L &&
                        sunshards >= upgradeShardCost &&
                        solCoins >= upgradeCoinCost,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("forge_${item.id}"),
                    border = BorderStroke(1.5.dp, ext.borderStrong)
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    val coinTag = if (upgradeCoinCost > 0L) "+${upgradeCoinCost}C" else ""
                    Text(
                        text = if (item.upgradeLevel >= 15L) "MAX +15" else "Temper ($upgradeShardCost$coinTag • $temperChance%)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Secondary Blacksmith Actions: Perk Reroll, Rarity Ascension, and Rune Socketing
        if (slot.isEquipment) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRerollPerk,
                    enabled = !expeditionLocked &&
                        solCoins >= ExpeditionEngine.PERK_REROLL_SOL_COIN_COST &&
                        sunshards >= ExpeditionEngine.PERK_REROLL_SUNSHARD_COST,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("reroll_perk_${item.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reroll (${ExpeditionEngine.PERK_REROLL_SOL_COIN_COST}C)", style = MaterialTheme.typography.labelSmall)
                }
                if (rarity != ItemRarity.ULTRA_RARE) {
                    OutlinedButton(
                        onClick = onAscendRarity,
                        enabled = !expeditionLocked &&
                            solCoins >= ExpeditionEngine.RARITY_ASCEND_SOL_COIN_COST &&
                            hardcoreState.sunsteelOre >= ExpeditionEngine.RARITY_ASCEND_ORE_COST &&
                            hardcoreState.elementalEssence >= ExpeditionEngine.RARITY_ASCEND_ESSENCE_COST,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("ascend_rarity_${item.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.Upgrade, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ascend (${ExpeditionEngine.RARITY_ASCEND_SOL_COIN_COST}C)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val ownedRunes = ElementalAffinity.entries.filter { hardcoreState.runeCount(it) > 0 }
            if (ownedRunes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ownedRunes.forEach { aff ->
                        Surface(
                            onClick = { onSocketRune(aff) },
                            enabled = !expeditionLocked,
                            shape = RoundedCornerShape(8.dp),
                            color = if (socketedRune == aff) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("socket_rune_${item.id}_${aff.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Socket ${aff.symbol} ${aff.label} (${hardcoreState.runeCount(aff)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

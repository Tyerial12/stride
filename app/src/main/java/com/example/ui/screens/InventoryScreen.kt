package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DerivedHeroStats
import com.example.data.model.GearPerkType
import com.example.data.model.HeroClass
import com.example.data.model.HeroProfile
import com.example.data.model.InventoryItem
import com.example.data.model.ItemRarity
import com.example.data.model.ItemSlot
import com.example.data.model.LootChest
import com.example.data.model.OwnedPet
import com.example.domain.QuestStatus
import com.example.ui.MasteryAttribute
import com.example.ui.components.ExpeditionLockBanner
import com.example.ui.components.HeroShowcaseCard
import com.example.ui.components.LootVaultSection
import com.example.ui.components.QuestsAndPrestigePanel
import com.example.ui.components.SkillTreePanel
import com.example.ui.components.SolstrideCard
import com.example.ui.components.TacticalStatBar
import com.example.ui.components.rarityColorFor
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSolstrideColors

private enum class HeroSubTab(val label: String) {
    GEAR_VAULT("Gear & Vault"),
    SKILL_TREE("Skills"),
    QUESTS_PRESTIGE("Quests")
}

private enum class InventoryFilter(val label: String) {
    ALL("All"),
    EQUIPPED("Equipped"),
    GEAR("Gear"),
    ELIXIRS("Elixirs")
}

@Composable
fun InventoryScreen(
    profile: HeroProfile,
    stats: DerivedHeroStats,
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
    onEquipOrUse: (InventoryItem) -> Unit,
    onUpgradeItem: (InventoryItem) -> Unit,
    onSalvageItem: (InventoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(HeroSubTab.GEAR_VAULT) }
    var selectedFilter by remember { mutableStateOf(InventoryFilter.ALL) }
    val ext = LocalSolstrideColors.current

    val claimableQuestCount = remember(questStatuses) { questStatuses.count { it.canClaim } }

    val filteredItems = remember(inventory, selectedFilter) {
        when (selectedFilter) {
            InventoryFilter.ALL -> inventory
            InventoryFilter.EQUIPPED -> inventory.filter { it.equipped }
            InventoryFilter.GEAR -> inventory.filter { it.resolvedSlot() != ItemSlot.CONSUMABLE }
            InventoryFilter.ELIXIRS -> inventory.filter { it.resolvedSlot() == ItemSlot.CONSUMABLE }
        }.sortedWith(
            compareByDescending<InventoryItem> { it.equipped }
                .thenByDescending { it.resolvedRarity().ordinal }
                .thenByDescending { it.upgradeLevel }
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

        // 3-Way Hero Hub Switcher: Gear & Vault | Skills | Quests & Prestige
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HeroSubTab.entries.forEachIndexed { idx, tab ->
                    val label = when (tab) {
                        HeroSubTab.GEAR_VAULT -> if (chests.isNotEmpty()) "Gear (${chests.size})" else tab.label
                        HeroSubTab.SKILL_TREE -> if (profile.unspentPoints > 0L) "Skills (${profile.unspentPoints})" else tab.label
                        HeroSubTab.QUESTS_PRESTIGE -> if (claimableQuestCount > 0) "Quests ($claimableQuestCount)" else tab.label
                    }
                    SegmentedButton(
                        selected = activeSubTab == tab,
                        onClick = { activeSubTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(idx, HeroSubTab.entries.size),
                        modifier = Modifier.testTag("hero_subtab_${tab.name.lowercase()}"),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    HeroSubTab.GEAR_VAULT -> Icons.Filled.Shield
                                    HeroSubTab.SKILL_TREE -> Icons.Filled.AccountTree
                                    HeroSubTab.QUESTS_PRESTIGE -> Icons.Filled.EmojiEvents
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
            HeroSubTab.SKILL_TREE -> {
                item {
                    SkillTreePanel(
                        profile = profile,
                        expeditionLocked = expeditionLocked,
                        onUpgradeNode = onUpgradeSkillNode
                    )
                }
            }
            HeroSubTab.QUESTS_PRESTIGE -> {
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
            }
            HeroSubTab.GEAR_VAULT -> {
                // Live 2D Hero Showcase with Visible Equipped Gear & Active Perks
                item {
                    HeroShowcaseCard(
                        profile = profile,
                        stats = stats,
                        inventory = inventory,
                        equippedPet = equippedPet
                    )
                }

                // Persistent Sealed Loot Chest Vault
                item {
                    LootVaultSection(
                        chests = chests,
                        onOpenChest = onOpenChest
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

                // Tactical Class Specialization Switcher
                item {
                    SolstrideCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Tactical Class Archetype", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroClass.entries.forEach { cls ->
                                val active = profile.resolvedClass() == cls
                                FilterChip(
                                    selected = active,
                                    enabled = !expeditionLocked,
                                    onClick = { onSelectClass(cls) },
                                    label = { Text(cls.displayTitle) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("class_${cls.name.lowercase()}_chip")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Active Perk: ${profile.resolvedClass().perkSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (stats.petBonusSummary != "No companion equipped") {
                            Text(
                                text = "Companion Bonus: ${stats.petBonusSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Inventory Header + Sunshards Balance + Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Armory & Satchel (${inventory.size})",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.5.dp, ext.borderStrong)
                        ) {
                            Text(
                                text = "${profile.sunshards} SUNSHARDS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = "No items in this category yet. Loot treasure chests from battles, quests, or Timed Expeditions!",
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
                            expeditionLocked = expeditionLocked,
                            onEquipOrUse = { onEquipOrUse(item) },
                            onUpgrade = { onUpgradeItem(item) },
                            onSalvage = { onSalvageItem(item) }
                        )
                    }
                }
            }
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

@Composable
private fun InventoryItemCard(
    item: InventoryItem,
    sunshards: Long,
    expeditionLocked: Boolean,
    onEquipOrUse: () -> Unit,
    onUpgrade: () -> Unit,
    onSalvage: () -> Unit
) {
    val ext = LocalSolstrideColors.current
    val rarity = item.resolvedRarity()
    val slot = item.resolvedSlot()
    val rarityTint = rarityColorFor(rarity)
    val upgradeCost = item.upgradeCost()
    val isUltraRare = rarity == ItemRarity.ULTRA_RARE
    val (perk, perkVal) = item.resolvedPerk()

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
                Text(
                    text = "${rarity.label.uppercase()} • ${slot.label.uppercase()}" +
                        if (slot == ItemSlot.CONSUMABLE) " • QTY ${item.quantity}" else " • GEAR PWR ${item.gearPowerScore()}",
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
                        contentDescription = "Salvage ${item.name} for Sunshards",
                        tint = if (expeditionLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
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

        if (item.description.isNotBlank()) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
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
                    enabled = !expeditionLocked && sunshards >= upgradeCost,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("forge_${item.id}"),
                    border = BorderStroke(1.5.dp, ext.borderStrong)
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Forge ($upgradeCost)")
                }
            }
        }
    }
}

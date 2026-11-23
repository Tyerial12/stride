package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Compact 2D hero customization configuration persisted as "hairStyle,hairColor,skinTone,eyeColor,outfitPalette,aura"
 * on HeroProfile.avatarConfig and TownPresencePlayer.avatarConfig.
 */
data class HeroAvatarConfig(
    val hairStyle: Int = 0,
    val hairColor: Int = 0,
    val skinTone: Int = 1,
    val eyeColor: Int = 0,
    val outfitPalette: Int = 0,
    val aura: Int = 0
) {
    fun normalized(): HeroAvatarConfig = HeroAvatarConfig(
        hairStyle = hairStyle.coerceIn(0, HAIR_STYLE_LABELS.lastIndex),
        hairColor = hairColor.coerceIn(0, HAIR_COLOR_HEXES.lastIndex),
        skinTone = skinTone.coerceIn(0, SKIN_TONE_HEXES.lastIndex),
        eyeColor = eyeColor.coerceIn(0, EYE_COLOR_HEXES.lastIndex),
        outfitPalette = outfitPalette.coerceIn(0, OUTFIT_PALETTES.lastIndex),
        aura = aura.coerceIn(0, AURA_LABELS.lastIndex)
    )

    fun serialize(): String {
        val n = normalized()
        return "${n.hairStyle},${n.hairColor},${n.skinTone},${n.eyeColor},${n.outfitPalette},${n.aura}"
    }

    companion object {
        const val DEFAULT_RAW = "0,0,1,0,0,0"

        val HAIR_STYLE_LABELS = listOf(
            "Spiked Crest",
            "Flowing Mane",
            "Braided Crown",
            "Rogue Sweep",
            "Scholar Cut",
            "Crowned Halo"
        )

        val HAIR_COLOR_LABELS = listOf(
            "Obsidian", "Sunlit Gold", "Copper Flame", "Silver Frost",
            "Chestnut", "Midnight Violet", "Emerald Moss", "Crimson Ember"
        )
        val HAIR_COLOR_HEXES = listOf(
            0xFF1F2430L, 0xFFF5B700L, 0xFFD95B26L, 0xFFE2E8F0L,
            0xFF6B3F24L, 0xFF5B3A8CL, 0xFF1E6F5CL, 0xFFB91C1CL
        )

        val SKIN_TONE_LABELS = listOf(
            "Alabaster", "Warm Ivory", "Golden Tan", "Bronze Olive", "Deep Umber", "Astral Dusk"
        )
        val SKIN_TONE_HEXES = listOf(
            0xFFFCE7D6L, 0xFFF5CFAAL, 0xFFD9A372L, 0xFFA66F45L, 0xFF694128L, 0xFF9EA8C4L
        )

        val EYE_COLOR_LABELS = listOf(
            "Amber Gold", "Azure Sky", "Emerald Jade", "Amethyst", "Crimson Spark", "Frost Silver"
        )
        val EYE_COLOR_HEXES = listOf(
            0xFFF59E0BL, 0xFF38BDF8L, 0xFF22C55EL, 0xFFA855F7L, 0xFFF43F5EL, 0xFFE2E8F0L
        )

        data class OutfitPaletteSpec(
            val label: String,
            val primaryHex: Long,
            val cloakHex: Long,
            val trimHex: Long
        )

        val OUTFIT_PALETTES = listOf(
            OutfitPaletteSpec("Solar Crimson", 0xFF991B1BL, 0xFF7F1D1DL, 0xFFFFB800L),
            OutfitPaletteSpec("Royal Cobalt", 0xFF1E3A8AL, 0xFF172554L, 0xFF38BDF8L),
            OutfitPaletteSpec("Verdant Strider", 0xFF0F766EL, 0xFF115E59L, 0xFFFDE047L),
            OutfitPaletteSpec("Astral Violet", 0xFF5B21B6L, 0xFF3B0764L, 0xFFE879F9L),
            OutfitPaletteSpec("Obsidian Gold", 0xFF1F2937L, 0xFF111827L, 0xFFF59E0BL),
            OutfitPaletteSpec("Ivory Paladin", 0xFF475569L, 0xFF334155L, 0xFFFDE68AL)
        )

        val AURA_LABELS = listOf(
            "Sunbeam Gold", "Ember Flame", "Glacial Halo", "Void Eclipse", "Verdant Wind", "Celestial Prism"
        )
        val AURA_HEXES = listOf(
            0xFFFFB800L, 0xFFF97316L, 0xFF38BDF8L, 0xFFA855F7L, 0xFF34D399L, 0xFFF472B6L
        )

        fun parse(raw: String?): HeroAvatarConfig {
            if (raw.isNullOrBlank()) return HeroAvatarConfig()
            val parts = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size < 6) return HeroAvatarConfig()
            return HeroAvatarConfig(
                hairStyle = parts[0],
                hairColor = parts[1],
                skinTone = parts[2],
                eyeColor = parts[3],
                outfitPalette = parts[4],
                aura = parts[5]
            ).normalized()
        }

        fun defaultForClass(heroClass: HeroClass): HeroAvatarConfig = when (heroClass) {
            HeroClass.VANGUARD -> HeroAvatarConfig(0, 1, 1, 0, 0, 0)
            HeroClass.ARCANIST -> HeroAvatarConfig(4, 5, 0, 3, 3, 1)
            HeroClass.RANGER -> HeroAvatarConfig(2, 4, 2, 2, 2, 4)
            HeroClass.SHADOWBLADE -> HeroAvatarConfig(3, 0, 2, 4, 4, 3)
        }
    }
}

object HeroNameCatalog {
    private val prefixes = mapOf(
        HeroClass.VANGUARD to listOf("Aurelia", "Valen", "Cassian", "Theron", "Solara", "Bastian"),
        HeroClass.ARCANIST to listOf("Lyra", "Seraphina", "Ignis", "Oriana", "Caelum", "Vesper"),
        HeroClass.RANGER to listOf("Rowan", "Kaelen", "Sylva", "Talia", "Corvus", "Faelan"),
        HeroClass.SHADOWBLADE to listOf("Nyx", "Kael", "Vexis", "Dorian", "Sable", "Riven")
    )
    private val epithets = listOf(
        "Dawnstride", "Sunforged", "Emberheart", "Galeborn",
        "Eclipse", "Starweaver", "Ironcrest", "Ashwalker"
    )

    fun randomName(heroClass: HeroClass): String {
        val p = (prefixes[heroClass] ?: prefixes.getValue(HeroClass.VANGUARD)).random()
        val e = epithets.random()
        return "$p $e"
    }
}

/**
 * Real-time multiplayer presence document stored in /town_presence/{userId}.
 * Contains zero PII—only public RPG hero identity, custom 2D avatar, and Sunhaven Plaza position.
 */
@Keep
@IgnoreExtraProperties
data class TownPresencePlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Strider",
    val heroClass: String = HeroClass.VANGUARD.name,
    val heroLevel: Long = 1L,
    val heroPower: Long = 100L,
    val prestigeLevel: Long = 0L,
    val avatarConfig: String = HeroAvatarConfig.DEFAULT_RAW,
    val equippedPetId: String = "",
    val equippedPetLevel: Long = 1L,
    val equippedPetStage: Long = 1L,
    val equippedPetBranch: String = PetEvolutionBranch.NONE.id,
    val posX: Double = 0.50,
    val posY: Double = 0.58,
    val facingRight: Boolean = true,
    val emote: String = "",
    val inTown: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedClass(): HeroClass = HeroClass.fromRaw(heroClass)
    fun resolvedAvatar(): HeroAvatarConfig = HeroAvatarConfig.parse(avatarConfig)
    fun resolvedPetType(): PetType? = PetType.entries.firstOrNull { it.name == equippedPetId }
    fun resolvedPetStage(): Int = equippedPetStage.toInt().coerceIn(1, 3)
    fun resolvedPetBranch(): PetEvolutionBranch = PetEvolutionBranch.fromRaw(equippedPetBranch)
}

enum class TownNpcRole(
    val id: String,
    val npcName: String,
    val title: String,
    val greeting: String,
    val posX: Float,
    val posY: Float,
    val badgeColorHex: Long
) {
    QUESTMASTER(
        id = "npc_questmaster",
        npcName = "Sunhaven Quest Board",
        title = "Guild Bounties",
        greeting = "Complete daily, weekly, and milestone bounties to earn Sol Coins, single-use Relic Chests, and higher-level Companions.",
        posX = 0.18f,
        posY = 0.34f,
        badgeColorHex = 0xFFD97706L
    ),
    PET_STORE(
        id = "npc_pet_store",
        npcName = "Pip • Pet Store",
        title = "Pet Store",
        greeting = "Welcome to Sunhaven Pet Store! Adopt companions, feed your pet with Sol Coin treats, and equip pet weapons & armor.",
        posX = 0.20f,
        posY = 0.36f,
        badgeColorHex = 0xFFF59E0BL
    ),
    MATERIALS_MERCHANT(
        id = "npc_merchant",
        npcName = "Zari • Material Vendor",
        title = "Material Vendor",
        greeting = "Welcome, Strider! Trade your Sol Coins for Sunsteel Ore, Starforged Masterwork Ingots, Elemental Essence, and Fog-Lantern Oil.",
        posX = 0.80f,
        posY = 0.36f,
        badgeColorHex = 0xFF0D9488L
    ),
    SKILL_MENTOR(
        id = "npc_mentor",
        npcName = "Master Kaelen • Skill Trainer",
        title = "Skill Trainer",
        greeting = "Hone your craft! Unlock new class arts as you level up and invest Sol Coins to upgrade your Active & Passive skill ranks.",
        posX = 0.20f,
        posY = 0.78f,
        badgeColorHex = 0xFF7C3AEDL
    ),
    BLACKSMITH(
        id = "npc_blacksmith",
        npcName = "Torin • Blacksmith",
        title = "Blacksmith",
        greeting = "The Sun-Forge burns hot! Strike the molten anvil with timing to forge Masterwork gear and upgrade the Sun-Forge.",
        posX = 0.80f,
        posY = 0.78f,
        badgeColorHex = 0xFFE11D48L
    ),
    ALCHEMIST_SPIRE(
        id = "npc_alchemist",
        npcName = "Sable • Alchemist Spire",
        title = "Alchemist Spire",
        greeting = "Balance the cauldron flame to brew Potent & Double Elixirs, cure Battle Wounds, and upgrade the Alchemist Spire!",
        posX = 0.50f,
        posY = 0.28f,
        badgeColorHex = 0xFF10B981L
    )
}

data class TownMaterialOffer(
    val id: String,
    val title: String,
    val subtitle: String,
    val solCoinCost: Long,
    val dailyStockLimit: Int,
    val oreGrant: Long = 0L,
    val herbGrant: Long = 0L,
    val essenceGrant: Long = 0L,
    val starforgedIngotGrant: Long = 0L,
    val fogLanternGrant: Int = 0,
    val randomRuneGrant: Boolean = false,
    val guildCrestGrant: Long = 0L,
    val relicPieceGrant: Boolean = false,
    val tagLabel: String
)

object TownMerchantCatalog {
    val offers: List<TownMaterialOffer> = listOf(
        TownMaterialOffer(
            id = "mat_ore_crate",
            title = "Sunsteel Ore Crate",
            subtitle = "+20 Sunsteel Ore for forging Weapons & Armor",
            solCoinCost = 12L,
            dailyStockLimit = 3,
            oreGrant = 20L,
            tagLabel = "FORGE"
        ),
        TownMaterialOffer(
            id = "mat_herb_satchel",
            title = "Aether Herb Satchel",
            subtitle = "+18 Aether Herbs for Elixirs & Wound Remedies",
            solCoinCost = 10L,
            dailyStockLimit = 3,
            herbGrant = 18L,
            tagLabel = "ALCHEMY"
        ),
        TownMaterialOffer(
            id = "mat_essence_phial",
            title = "Elemental Essence Phial",
            subtitle = "+12 Elemental Essence for Tier II–IV Blueprints",
            solCoinCost = 16L,
            dailyStockLimit = 3,
            essenceGrant = 12L,
            tagLabel = "ESSENCE"
        ),
        TownMaterialOffer(
            id = "mat_starforged_ingot",
            title = "Starforged Masterwork Ingot",
            subtitle = "+1 Ingot • Guarantees a Masterwork Critical Forge (+1 Rarity Tier & +18% Power)",
            solCoinCost = 28L,
            dailyStockLimit = 1,
            starforgedIngotGrant = 1L,
            tagLabel = "RARE"
        ),
        TownMaterialOffer(
            id = "mat_fog_lantern",
            title = "Sunfire Fog-Lantern Oil",
            subtitle = "+3 Charges • Pierces Realm Fog (+25% Fog Clarity, +8% Crit & +6% Survival)",
            solCoinCost = 14L,
            dailyStockLimit = 2,
            fogLanternGrant = 3,
            tagLabel = "FOG ARTIFACT"
        ),
        TownMaterialOffer(
            id = "mat_rune_coffer",
            title = "Astral Rune Coffer",
            subtitle = "+1 Random Elemental Rune & +6 Elemental Essence",
            solCoinCost = 22L,
            dailyStockLimit = 1,
            essenceGrant = 6L,
            randomRuneGrant = true,
            tagLabel = "RUNE"
        ),
        TownMaterialOffer(
            id = "mat_relic_coffer",
            title = "Ancient Relic Set Coffer",
            subtitle = "Unlocks +1 uncollected Relic Set piece toward 2pc/4pc permanent Hero bonuses",
            solCoinCost = 30L,
            dailyStockLimit = 1,
            relicPieceGrant = true,
            tagLabel = "RELIC SET"
        ),
        TownMaterialOffer(
            id = "mat_guild_crest",
            title = "Guild Charter Crest",
            subtitle = "Town-exclusive Royal Charter required to found your own Step-Powered Guild",
            solCoinCost = 40L,
            dailyStockLimit = 1,
            guildCrestGrant = 1L,
            tagLabel = "GUILD"
        )
    )
}

/**
 * Dynamic Step & Sleep Fog-of-War RPG state computed for Sunhaven Plaza & Battle Realms.
 */
data class WorldFogState(
    val realmNumber: Int = 1,
    val realmStepsWalked: Int = 0,
    val realmStepsRequired: Int = 2000,
    val realmFogClearedPct: Int = 0,
    val visualFogDensity: Float = 0.65f, // 0f = crystal clear sunlit sky, 0.75f = dense mystic mist
    val isDreamMistActive: Boolean = false,
    val restedClarityBattlesLeft: Int = 0,
    val restedClarityPct: Int = 0,
    val fogLanternCharges: Int = 0,
    val fogCritBonusPct: Int = 0,
    val restedExpeditionWardBonusPct: Int = 0,
    val restedXpBonusPct: Int = 0,
    val statusHeadline: String = "Frontier Mist Veils the Path"
)

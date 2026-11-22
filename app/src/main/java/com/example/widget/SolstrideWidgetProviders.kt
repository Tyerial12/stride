package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.hardware.StepBackgroundSyncScheduler

abstract class BaseSolstrideWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        StepBackgroundSyncScheduler.ensureScheduled(context)
        WidgetAutoSyncScheduler.syncSchedule(context)
        WidgetAutoSyncScheduler.triggerImmediateSync(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetAutoSyncScheduler.cancelAllIfNoWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == SolstrideWidgetUpdater.ACTION_REFRESH_SOLSTRIDE_WIDGETS) {
            // Trigger immediate hardware pedometer + Health Connect + full Hero/Pet widget sync
            StepBackgroundSyncScheduler.enqueueImmediateSync(context)
            WidgetAutoSyncScheduler.syncSchedule(context)
            WidgetAutoSyncScheduler.triggerImmediateSync(context)
            SolstrideWidgetUpdater.touchRefreshTimestampAndUpdate(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = SolstrideWidgetSnapshotStore.read(context)
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, createViews(context, snapshot))
        }
        val autoSyncConfig = SolstrideWidgetSnapshotStore.readAutoSyncConfig(context)
        if (autoSyncConfig.enabled) {
            StepBackgroundSyncScheduler.ensureScheduled(context)
            WidgetAutoSyncScheduler.syncSchedule(context)
            StepBackgroundSyncScheduler.enqueueImmediateSync(context)
            WidgetAutoSyncScheduler.triggerImmediateSync(context)
        }
    }

    protected abstract fun createViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews

    companion object {
        fun deepLinkPendingIntent(
            context: Context,
            targetTab: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(SolstrideWidgetUpdater.EXTRA_WIDGET_TARGET_TAB, targetTab)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun refreshPendingIntent(
            context: Context,
            providerClass: Class<*>,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, providerClass).apply {
                action = SolstrideWidgetUpdater.ACTION_REFRESH_SOLSTRIDE_WIDGETS
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/**
 * 1. Battle & Campaign Radar Widget (4x2 — Crimson/Ember Theme):
 * Focuses exclusively on Campaign Realm/Stage, Endless Spire floor, Boss Encounter Radar,
 * Actionable Alerts (Unopened Chests, Claimable Quests, Unspent Skill Points), and Quick Action Dock.
 */
class SolstrideCommandWidgetProvider : BaseSolstrideWidgetProvider() {

    override fun createViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews =
        buildRemoteViews(context, snapshot)

    companion object {
        fun buildRemoteViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_solstride_command)
            val hcTag = if (snapshot.hardcoreOathEnabled) " 💀HC" else ""

            views.setTextViewText(
                R.id.cmd_realm_title,
                "⚔ R${snapshot.realmNumber}-${snapshot.realmStage} • ${snapshot.realmName}$hcTag"
            )
            views.setTextViewText(
                R.id.cmd_spire_subtitle,
                "Spire Floor ${snapshot.spireFloor} • Bosses Slain: ${snapshot.bossesDefeated}"
            )

            val encounterReady = snapshot.encounterProgressPct >= 100
            if (encounterReady) {
                views.setTextViewText(R.id.cmd_encounter_badge, "⚔ READY!")
                views.setInt(R.id.cmd_encounter_badge, "setBackgroundResource", R.drawable.widget_bg_alert_crimson)
                views.setTextColor(R.id.cmd_encounter_badge, Color.parseColor("#FFE4E2"))
                views.setTextViewText(R.id.cmd_radar_label, "⚔ BOSS / ENCOUNTER LOCKED ON — ENGAGE NOW!")
            } else {
                views.setTextViewText(R.id.cmd_encounter_badge, "Radar ${snapshot.encounterProgressPct}%")
                views.setInt(R.id.cmd_encounter_badge, "setBackgroundResource", R.drawable.widget_bg_badge_gold)
                views.setTextColor(R.id.cmd_encounter_badge, Color.parseColor("#FFD166"))
                views.setTextViewText(
                    R.id.cmd_radar_label,
                    "Encounter Radar: ${snapshot.encounterProgressPct}% to Next Ambush"
                )
            }
            views.setProgressBar(R.id.cmd_radar_progress, 100, snapshot.encounterProgressPct.coerceIn(0, 100), false)

            // 3 Actionable Tiles: Vault Chests, Claimable Quests, Unspent Skill Points
            views.setTextViewText(R.id.cmd_chests_value, "🎁 ${snapshot.unopenedChestCount}")
            views.setTextViewText(
                R.id.cmd_chests_label,
                if (snapshot.unopenedChestCount > 0) "OPEN VAULT" else "CHESTS"
            )
            views.setTextColor(
                R.id.cmd_chests_value,
                if (snapshot.unopenedChestCount > 0) Color.parseColor("#FFD166") else Color.parseColor("#B89A97")
            )

            views.setTextViewText(R.id.cmd_quests_value, "📜 ${snapshot.claimableQuestCount}")
            views.setTextViewText(
                R.id.cmd_quests_label,
                if (snapshot.claimableQuestCount > 0) "CLAIM NOW" else "QUESTS"
            )
            views.setTextColor(
                R.id.cmd_quests_value,
                if (snapshot.claimableQuestCount > 0) Color.parseColor("#4ADE80") else Color.parseColor("#B89A97")
            )

            views.setTextViewText(R.id.cmd_points_value, "⭐ ${snapshot.unspentMasteryPoints}")
            views.setTextViewText(
                R.id.cmd_points_label,
                if (snapshot.unspentMasteryPoints > 0L) "SPEND PTS" else "SKILL PTS"
            )
            views.setTextColor(
                R.id.cmd_points_value,
                if (snapshot.unspentMasteryPoints > 0L) Color.parseColor("#C084FC") else Color.parseColor("#B89A97")
            )

            views.setTextViewText(
                R.id.cmd_battle_cta,
                if (encounterReady) "⚔ FIGHT!" else "⚔ BATTLE"
            )

            // Tailored deep-links per zone
            views.setOnClickPendingIntent(
                R.id.cmd_header_zone,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_BATTLE, 101)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_radar_zone,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_BATTLE, 102)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_encounter_badge,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_BATTLE, 103)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_chests_tile,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_INVENTORY, 104)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_quests_tile,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_EXPEDITION, 105)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_points_tile,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_INVENTORY, 106)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_battle_cta,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_BATTLE, 107)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_forge_cta,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_INVENTORY, 108)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_map_cta,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_EXPEDITION, 109)
            )
            views.setOnClickPendingIntent(
                R.id.cmd_refresh_btn,
                refreshPendingIntent(context, SolstrideCommandWidgetProvider::class.java, 110)
            )

            return views
        }
    }
}

/**
 * 2. Hero Build & Forge Armory Widget (3x2 — Obsidian/Molten Amber Theme):
 * Focuses exclusively on Hero Vitals (HP/Energy), ATK/DEF/CRIT, 6-Slot Gear Loadout,
 * Durability/Wound alerts, Forge Materials Treasury, and Blacksmith Anvil shortcut.
 */
class SolstrideHeroWidgetProvider : BaseSolstrideWidgetProvider() {

    override fun createViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews =
        buildRemoteViews(context, snapshot)

    companion object {
        fun buildRemoteViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_solstride_hero)

            views.setTextViewText(
                R.id.hero_title,
                "Lv.${snapshot.level}${snapshot.prestigeTag()} ${snapshot.displayName}"
            )
            views.setTextViewText(
                R.id.hero_spec,
                "${snapshot.elementSymbol} ${snapshot.elementLabel} • ${snapshot.specializationTitle}"
            )
            views.setTextViewText(R.id.hero_power_badge, "PWR ${snapshot.heroPower}")

            views.setTextViewText(R.id.hero_hp_label, "HP ${snapshot.currentHp}/${snapshot.maxHp}")
            views.setProgressBar(R.id.hero_hp_progress, 100, snapshot.hpPercent(), false)

            views.setTextViewText(R.id.hero_energy_label, "EN ${snapshot.strideEnergy}/${snapshot.maxStrideEnergy}")
            views.setProgressBar(R.id.hero_energy_progress, 100, snapshot.energyPercent(), false)

            views.setTextViewText(
                R.id.hero_stats_line,
                "ATK ${snapshot.attack} • DEF ${snapshot.defense} • CRIT ${snapshot.critChancePct}%"
            )

            val gearSummary = snapshot.gearLoadoutSummary()
            views.setTextViewText(R.id.hero_gear_line, gearSummary)
            when {
                snapshot.hasHardcoreAlert() ->
                    views.setTextColor(R.id.hero_gear_line, Color.parseColor("#FF7A7A"))
                snapshot.equippedArmorSlots <= 0 ->
                    views.setTextColor(R.id.hero_gear_line, Color.parseColor("#FBBF24"))
                else ->
                    views.setTextColor(R.id.hero_gear_line, Color.parseColor("#86EFAC"))
            }

            views.setTextViewText(
                R.id.hero_treasury_line,
                "🪙 ${snapshot.solCoins} • ☀ ${snapshot.sunshards} • ⛏ ${snapshot.sunsteelOre} • ✨ ${snapshot.elementalEssence}"
            )

            views.setOnClickPendingIntent(
                R.id.hero_root,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_INVENTORY, 201)
            )
            views.setOnClickPendingIntent(
                R.id.hero_forge_cta,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_INVENTORY, 202)
            )
            views.setOnClickPendingIntent(
                R.id.hero_refresh_btn,
                refreshPendingIntent(context, SolstrideHeroWidgetProvider::class.java, 203)
            )

            return views
        }
    }
}

/**
 * 3. Interactive Pet Arena Widget (4x2 — Twilight Violet Theme):
 * Exclusive way to level pets (Feed + Wild Pet Battles) while Solstride is closed;
 * locks out automatically while the app is open and auto-syncs all progress on launch.
 */
class SolstridePetWidgetProvider : BaseSolstrideWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            SolstrideWidgetUpdater.ACTION_PET_WIDGET_FEED -> {
                if (!SolstrideWidgetSnapshotStore.isAppForegroundLocked(context)) {
                    SolstrideWidgetSnapshotStore.performWidgetFeedAction(context)
                } else {
                    SolstrideWidgetUpdater.updateAllWidgets(context)
                }
            }
            SolstrideWidgetUpdater.ACTION_PET_WIDGET_WILD_FIGHT -> {
                if (!SolstrideWidgetSnapshotStore.isAppForegroundLocked(context)) {
                    SolstrideWidgetSnapshotStore.performWidgetWildFightAction(context)
                } else {
                    SolstrideWidgetUpdater.updateAllWidgets(context)
                }
            }
        }
    }

    override fun createViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews =
        buildRemoteViews(context, snapshot)

    companion object {
        private fun petActionPendingIntent(
            context: Context,
            actionName: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, SolstridePetWidgetProvider::class.java).apply {
                action = actionName
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildRemoteViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_solstride_pet)
            val locked = snapshot.isAppForegroundLocked

            if (locked) {
                views.setViewVisibility(R.id.pet_lock_banner, View.VISIBLE)
                views.setTextViewText(
                    R.id.pet_lock_banner,
                    "🔒 APP OPEN — Close Solstride to Feed or Fight Wild Pets"
                )
            } else {
                views.setViewVisibility(R.id.pet_lock_banner, View.GONE)
            }

            views.setTextViewText(
                R.id.pet_stamina_badge,
                "⚡ ${snapshot.petWildStamina}/${WildPetBattleEngine.MAX_WILD_STAMINA}"
            )

            if (snapshot.hasPet) {
                views.setTextViewText(
                    R.id.pet_name,
                    "${snapshot.petElementSymbol} ${snapshot.petName} • Lv.${snapshot.petLevel}"
                )
                val rarityPrefix = if (snapshot.petRarityLabel.isNotBlank()) "${snapshot.petRarityLabel} " else ""
                views.setTextViewText(
                    R.id.pet_meta,
                    "$rarityPrefix${snapshot.petElementLabel} • Wpn T${snapshot.petFoodLevel} • Arm T${snapshot.petGearLevel}"
                )
                views.setTextViewText(
                    R.id.pet_xp_label,
                    "XP ${snapshot.petXp}/${snapshot.petXpToNext} (${snapshot.petXpPercent()}%)"
                )
                views.setTextViewText(
                    R.id.pet_mood_wins_label,
                    "❤ ${snapshot.petMoodPct}% • 🪙 ${snapshot.solCoins}c • 🏆 ${snapshot.petTotalWildWins}W"
                )
                views.setProgressBar(R.id.pet_xp_progress, 100, snapshot.petXpPercent(), false)
                views.setTextViewText(R.id.pet_synergy_line, "Synergy: ${snapshot.petBonusSummary}")
                views.setTextViewText(R.id.pet_battle_log, snapshot.petLastBattleSummary)
            } else {
                views.setTextViewText(R.id.pet_name, "🐾 Interactive Pet Arena")
                views.setTextViewText(R.id.pet_meta, "No Companion Equipped • Visit Sanctuary")
                views.setTextViewText(R.id.pet_xp_label, "Exclusive Widget Pet Leveling")
                views.setTextViewText(R.id.pet_mood_wins_label, "🪙 ${snapshot.solCoins} Sol Coins")
                views.setProgressBar(R.id.pet_xp_progress, 100, 0, false)
                views.setTextViewText(R.id.pet_synergy_line, "Adopt a companion for Hero ATK/DEF Synergy")
                views.setTextViewText(
                    R.id.pet_battle_log,
                    "Close app & tap Feed (${WildPetBattleEngine.FEED_SOL_COIN_COST} Coins) or Wild Fight!"
                )
            }

            if (locked) {
                views.setTextViewText(R.id.pet_feed_btn, "🔒 LOCKED")
                views.setInt(R.id.pet_feed_btn, "setBackgroundResource", R.drawable.widget_bg_alert_crimson)
                views.setTextViewText(R.id.pet_fight_btn, "🔒 APP OPEN")
                views.setInt(R.id.pet_fight_btn, "setBackgroundResource", R.drawable.widget_bg_alert_crimson)
            } else {
                views.setTextViewText(R.id.pet_feed_btn, "🍖 FEED (${WildPetBattleEngine.FEED_SOL_COIN_COST}c)")
                views.setInt(R.id.pet_feed_btn, "setBackgroundResource", R.drawable.widget_bg_badge_emerald)
                views.setTextViewText(R.id.pet_fight_btn, "⚔ WILD FIGHT (-1⚡)")
                views.setInt(R.id.pet_fight_btn, "setBackgroundResource", R.drawable.widget_bg_badge_violet)
            }

            views.setOnClickPendingIntent(
                R.id.pet_feed_btn,
                petActionPendingIntent(context, SolstrideWidgetUpdater.ACTION_PET_WIDGET_FEED, 310)
            )
            views.setOnClickPendingIntent(
                R.id.pet_fight_btn,
                petActionPendingIntent(context, SolstrideWidgetUpdater.ACTION_PET_WIDGET_WILD_FIGHT, 311)
            )
            views.setOnClickPendingIntent(
                R.id.pet_open_btn,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_PETS, 302)
            )
            views.setOnClickPendingIntent(
                R.id.pet_root,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_PETS, 301)
            )
            views.setOnClickPendingIntent(
                R.id.pet_refresh_btn,
                refreshPendingIntent(context, SolstridePetWidgetProvider::class.java, 303)
            )

            return views
        }
    }
}

/**
 * 4. Wayfarer Steps, Watch Sleep & Expedition Timer Widget (3x2 — Deep Starlight Cyan Theme):
 * Focuses exclusively on Real-World Daily & Weekly Steps, Galaxy Watch Link, Real-Life Watch Sleep
 * Detection, Steps-to-Next-Pet-Stamina, Aether Herbs, and Timed Expedition Progress/Countdown.
 */
class SolstrideStepWidgetProvider : BaseSolstrideWidgetProvider() {

    override fun createViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews =
        buildRemoteViews(context, snapshot)

    companion object {
        fun buildRemoteViews(context: Context, snapshot: WidgetSnapshotState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_solstride_steps)
            val nowMs = System.currentTimeMillis()

            views.setTextViewText(R.id.steps_big_count, "${snapshot.formattedSteps()} Steps")
            views.setTextViewText(
                R.id.steps_goal_sub,
                "Goal ${snapshot.formattedGoal()} (${snapshot.stepGoalPercent()}%) • Week ${snapshot.formattedWeeklySteps()}"
            )
            views.setProgressBar(R.id.steps_goal_progress, 100, snapshot.stepGoalPercent(), false)

            views.setTextViewText(
                R.id.steps_watch_badge,
                if (snapshot.isWatchConnected) "⌚ Watch" else "📱 Phone"
            )

            views.setTextViewText(R.id.steps_sleep_line, snapshot.sleepStatusSummary())
            views.setTextColor(
                R.id.steps_sleep_line,
                if (snapshot.isHeroSleeping) Color.parseColor("#C4B5FD") else Color.parseColor("#93C5FD")
            )

            val staminaRewardLine = if (snapshot.petWildStamina >= WildPetBattleEngine.MAX_WILD_STAMINA) {
                "⭐ Steps→Hero XP • ⚡ Pet Stamina FULL (${snapshot.petWildStamina}/5)"
            } else {
                "⭐ Steps→Hero XP • ⚡ +1 Pet Stamina in ${snapshot.stepsToNextStamina} steps"
            }
            views.setTextViewText(R.id.steps_reward_line, staminaRewardLine)

            val expProgress = snapshot.expeditionProgressPercent(nowMs)
            if (snapshot.hasActiveExpedition && snapshot.expeditionEndMs > 0L) {
                views.setViewVisibility(R.id.steps_expedition_progress, View.VISIBLE)
                views.setProgressBar(R.id.steps_expedition_progress, 100, expProgress, false)
            } else {
                views.setViewVisibility(R.id.steps_expedition_progress, View.GONE)
            }

            views.setTextViewText(
                R.id.steps_expedition_line,
                snapshot.expeditionStatusLine(nowMs)
            )

            views.setOnClickPendingIntent(
                R.id.steps_root,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_EXPEDITION, 401)
            )
            views.setOnClickPendingIntent(
                R.id.steps_open_cta,
                deepLinkPendingIntent(context, SolstrideWidgetUpdater.TAB_EXPEDITION, 402)
            )
            views.setOnClickPendingIntent(
                R.id.steps_refresh_btn,
                refreshPendingIntent(context, SolstrideStepWidgetProvider::class.java, 403)
            )

            return views
        }
    }
}

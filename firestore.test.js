const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vanguard_01";
const BOB_UID = "bob_strider_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validHeroProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Aurelia Vanguard",
    heroClass: "VANGUARD",
    level: 1,
    xp: 0,
    xpToNextLevel: 120,
    might: 5,
    vigor: 5,
    endurance: 5,
    unspentPoints: 0,
    currentHp: 120,
    maxHp: 120,
    strideEnergy: 20,
    maxStrideEnergy: 100,
    encounterProgress: 0,
    sunshards: 50,
    solCoins: 0,
    totalSteps: 0,
    weeklySteps: 0,
    weekKey: "2026-W47",
    bossesDefeated: 0,
    trueBossWins: 0,
    heroPower: 145,
    equippedPetId: "",
    expeditionsCompleted: 0,
    expeditionsFailed: 0,
    activeExpeditionStartMs: 0,
    activeExpeditionDurationMin: 0,
    activeExpeditionEndMs: 0,
    currentRealm: 1,
    realmStage: 1,
    highestRealmCleared: 0,
    currentSpireFloor: 1,
    highestSpireFloor: 0,
    prestigeLevel: 0,
    skillRanks: "0,0,0,0,0,0,0,0,0",
    dailyQuestDateKey: "2026-11-20",
    dailyBattlesWon: 0,
    dailyExpeditionsDone: 0,
    dailyChestsOpened: 0,
    claimedQuestIds: [],
    chestsOpenedTotal: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function legacyHeroProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Legacy Strider",
    heroClass: "STRIDER",
    level: 2,
    xp: 40,
    xpToNextLevel: 185,
    might: 6,
    vigor: 5,
    endurance: 6,
    unspentPoints: 0,
    currentHp: 140,
    maxHp: 140,
    strideEnergy: 30,
    maxStrideEnergy: 110,
    encounterProgress: 10,
    sunshards: 80,
    totalSteps: 500,
    weeklySteps: 500,
    weekKey: "2026-W47",
    bossesDefeated: 1,
    heroPower: 190,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validInventoryItem(uid, ts, rarity = "RARE") {
  return {
    userId: uid,
    name: "Sunforged Gladius",
    slot: "WEAPON",
    rarity,
    attackBonus: 12,
    defenseBonus: 2,
    hpBonus: 10,
    energyBonus: 5,
    healAmount: 0,
    upgradeLevel: 1,
    quantity: 1,
    equipped: true,
    description: "Tempered bronze blade gleaming in midday sun.",
    perkType: "CRIT_CHANCE",
    perkValue: 12,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLootChest(uid, ts, tier = "EXPEDITION") {
  return {
    userId: uid,
    chestTier: tier,
    sourceLabel: "Zenith Odyssey (23h)",
    heroLevelSnapshot: 5,
    durationMinutes: tier === "EXPEDITION" ? 1380 : 0,
    ultraRareChancePct: tier === "EXPEDITION" ? 100 : 28,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validOwnedPet(uid, petType, ts) {
  return {
    userId: uid,
    petType,
    level: 1,
    xp: 0,
    xpToNextLevel: 60,
    equippedWeaponId: "",
    equippedArmorId: "",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLeaderboardEntry(uid, ts) {
  return {
    userId: uid,
    displayName: "Aurelia Vanguard",
    heroClass: "VANGUARD",
    heroLevel: 1,
    heroPower: 145,
    weeklySteps: 1200,
    weekKey: "2026-W47",
    bossesDefeated: 2,
    prestigeLevel: 1,
    highestSpireFloor: 12,
    publicVisible: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write profiles, inventory, chests, pets, or leaderboard", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validHeroProfile(ALICE_UID, ts)));
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("chests").doc("chest_1").set(
      validLootChest(ALICE_UID, ts)
    )
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("pets").doc("SUNFOX_KIT").set(
      validOwnedPet(ALICE_UID, "SUNFOX_KIT", ts)
    )
  );
  await assertFails(unauthDb.collection("leaderboard").where("publicVisible", "==", true).get());
});

test("HeroProfile: owner create, RPG progression/skill/quest/prestige updates, legacy migration, and guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validHeroProfile(ALICE_UID, created))
  );

  // Full RPG update: Realm, Spire, Skill Tree, Prestige, Quests, Expedition Stop/Fail
  const startMs = Date.now() - 1000;
  const durationMin = 120;
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      solCoins: 10,
      trueBossWins: 1,
      equippedPetId: "SUNFOX_KIT",
      activeExpeditionStartMs: startMs,
      activeExpeditionDurationMin: durationMin,
      activeExpeditionEndMs: startMs + durationMin * 60 * 1000,
      expeditionsCompleted: 1,
      expeditionsFailed: 1,
      currentRealm: 2,
      realmStage: 3,
      highestRealmCleared: 1,
      currentSpireFloor: 6,
      highestSpireFloor: 5,
      prestigeLevel: 1,
      skillRanks: "2,1,0,1,0,0,1,0,0",
      dailyQuestDateKey: "2026-11-20",
      dailyBattlesWon: 3,
      dailyExpeditionsDone: 1,
      dailyChestsOpened: 2,
      claimedQuestIds: ["daily_battles_3", "mile_chests_5"],
      chestsOpenedTotal: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Legacy returning user profile without new fields still updates cleanly
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set(legacyHeroProfile(BOB_UID, created));
  });
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      solCoins: 10,
      currentRealm: 1,
      skillRanks: "1,0,0,0,0,0,0,0,0",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject malformed skillRanks vector
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      skillRanks: "9,9,9,9,9,9,9,9,9",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject out-of-range realm (> 10)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      currentRealm: 11,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation & ghost fields & future timestamps
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      solCoins: 999,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Inventory subcollection: gear perks, ULTRA_RARE drops, owner CRUD, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const invCol = aliceDb.collection("users").doc(ALICE_UID).collection("inventory");

  await assertSucceeds(invCol.get());
  await assertSucceeds(invCol.doc("ultra_drop_1").set(validInventoryItem(ALICE_UID, created, "ULTRA_RARE")));

  // Reject invalid perkType
  await assertFails(
    invCol.doc("bad_perk").set({
      ...validInventoryItem(ALICE_UID, created, "EPIC"),
      perkType: "GOD_MODE",
    })
  );

  // Reject @DocumentId id key
  await assertFails(
    invCol.doc("bad_blade").set({
      ...validInventoryItem(ALICE_UID, created, "ULTRA_RARE"),
      id: "bad_blade",
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inventory").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("inventory").doc("ultra_drop_1").delete());

  // Valid partial update (including perkValue forge boost) & owner delete
  await assertSucceeds(
    invCol.doc("ultra_drop_1").update({
      upgradeLevel: 2,
      attackBonus: 28,
      perkValue: 15,
      equipped: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(invCol.doc("ultra_drop_1").delete());
});

test("Chests subcollection: owner create/list/delete, immutability, bounds, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const chestsCol = aliceDb.collection("users").doc(ALICE_UID).collection("chests");

  // Empty list succeeds
  await assertSucceeds(chestsCol.get());

  // Create valid Expedition & Boss chests
  await assertSucceeds(chestsCol.doc("chest_exp_1").set(validLootChest(ALICE_UID, created, "EXPEDITION")));
  await assertSucceeds(chestsCol.doc("chest_boss_1").set(validLootChest(ALICE_UID, created, "BOSS_VAULT")));

  // Reject invalid chest tier or forged ultraRareChancePct > 100
  await assertFails(
    chestsCol.doc("chest_bad_tier").set({
      ...validLootChest(ALICE_UID, created),
      chestTier: "INFINITE_MYTHIC",
    })
  );
  await assertFails(
    chestsCol.doc("chest_bad_pct").set({
      ...validLootChest(ALICE_UID, created),
      ultraRareChancePct: 150,
    })
  );

  // Chests are immutable after creation (cannot tamper with odds via update)
  await assertFails(
    chestsCol.doc("chest_boss_1").update({
      ultraRareChancePct: 100,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Strict cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chests").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("chests").doc("chest_exp_1").delete());

  // Owner deletes chest upon opening
  await assertSucceeds(chestsCol.doc("chest_exp_1").delete());
});

test("Pets subcollection: owner adopt, list, feed/level & equip pet gear, validation & cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const petsCol = aliceDb.collection("users").doc(ALICE_UID).collection("pets");

  await assertSucceeds(petsCol.get());
  await assertSucceeds(petsCol.doc("SUNFOX_KIT").set(validOwnedPet(ALICE_UID, "SUNFOX_KIT", created)));
  await assertFails(petsCol.doc("WRONG_ID").set(validOwnedPet(ALICE_UID, "AEGIS_SCARAB", created)));

  await assertSucceeds(
    petsCol.doc("SUNFOX_KIT").update({
      level: 2,
      xp: 30,
      xpToNextLevel: 100,
      equippedWeaponId: "pet_wpn_sunsteel_talon",
      equippedArmorId: "pet_arm_obsidian_barding",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("pets").get());
});

test("Leaderboard: publicVisible query succeeds, prestige/spire fields validated, owner-only writes", async () => {
  const created = new Date(Date.now() - 10000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set(validLeaderboardEntry(ALICE_UID, created))
  );
  await assertSucceeds(bobDb.collection("leaderboard").where("publicVisible", "==", true).get());
  await assertFails(bobDb.collection("leaderboard").get());
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      weeklySteps: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).update({
      weeklySteps: 5400,
      heroPower: 340,
      heroLevel: 3,
      bossesDefeated: 5,
      prestigeLevel: 2,
      highestSpireFloor: 20,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("HeroProfile: accepts 4 remade classes, avatarConfig, and characterCreated; rejects malformed avatarConfig", async () => {
  const created = new Date(Date.now() - 10000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const base = validHeroProfile(ALICE_UID, created);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...base,
      displayName: "Lyra Dawnweaver",
      heroClass: "ARCANIST",
      avatarConfig: "2,4,1,3,5,2",
      characterCreated: true,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      heroClass: "SHADOWBLADE",
      avatarConfig: "5,7,2,5,3,4",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      heroClass: "RANGER",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      avatarConfig: "invalid_format",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("TownPresence: owner writes & filtered inTown list succeed; unauthenticated, unfiltered list, and cross-user writes fail", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const alicePresence = {
    userId: ALICE_UID,
    displayName: "Aurelia",
    heroClass: "VANGUARD",
    heroLevel: 8,
    heroPower: 420,
    prestigeLevel: 1,
    avatarConfig: "1,2,1,0,3,2",
    equippedPetId: "SUNFOX_KIT",
    posX: 0.45,
    posY: 0.62,
    facingRight: true,
    emote: "Hail, Striders!",
    inTown: true,
    updatedAt: ts,
  };

  // Unauthenticated cannot read or write town presence
  await assertFails(unauthDb.collection("town_presence").where("inTown", "==", true).get());
  await assertFails(unauthDb.collection("town_presence").doc(ALICE_UID).set(alicePresence));

  // Owner writes valid town presence
  await assertSucceeds(aliceDb.collection("town_presence").doc(ALICE_UID).set(alicePresence));

  // Another signed-in player can list players with inTown == true
  await assertSucceeds(bobDb.collection("town_presence").where("inTown", "==", true).limit(25).get());

  // Unfiltered list fails
  await assertFails(bobDb.collection("town_presence").get());

  // Cross-user write/delete rejected
  await assertFails(
    bobDb.collection("town_presence").doc(ALICE_UID).set({
      ...alicePresence,
      emote: "Hijacked",
    })
  );
  await assertFails(bobDb.collection("town_presence").doc(ALICE_UID).delete());

  // Out-of-bounds coordinates rejected
  await assertFails(
    aliceDb.collection("town_presence").doc(ALICE_UID).set({
      ...alicePresence,
      posX: 1.5,
    })
  );

  // Owner can leave town cleanly
  await assertSucceeds(aliceDb.collection("town_presence").doc(ALICE_UID).delete());
});

test("MultiplayerChat: signed-in global chat create/list/owner-delete succeed; unauth, unfiltered, update & cross-user delete fail", async () => {
  const ts = new Date(Date.now() - 1000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const msg = {
    userId: ALICE_UID,
    displayName: "Aurelia",
    heroClass: "VANGUARD",
    heroLevel: 10,
    avatarConfig: "1,2,1,0,3,2",
    equippedPetId: "SUNFOX_KIT",
    message: "Anyone up for a friendly duel?",
    channel: "GLOBAL",
    createdAt: ts,
  };

  await assertFails(unauthDb.collection("multiplayer_chat").where("channel", "==", "GLOBAL").get());
  await assertFails(unauthDb.collection("multiplayer_chat").doc("msg_1").set(msg));

  await assertSucceeds(aliceDb.collection("multiplayer_chat").doc("msg_1").set(msg));
  await assertSucceeds(bobDb.collection("multiplayer_chat").where("channel", "==", "GLOBAL").limit(60).get());
  await assertFails(bobDb.collection("multiplayer_chat").get());

  // Messages are immutable & cannot be deleted by other users
  await assertFails(aliceDb.collection("multiplayer_chat").doc("msg_1").update({ message: "Edited" }));
  await assertFails(bobDb.collection("multiplayer_chat").doc("msg_1").delete());

  // Sender can delete own message
  await assertSucceeds(aliceDb.collection("multiplayer_chat").doc("msg_1").delete());
});

test("MultiplayerDuels: participant-scoped challenge, accept, turn play, completion & outsider isolation", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_outsider_03").firestore();

  const duel = {
    participantUids: [ALICE_UID, BOB_UID],
    challengerUid: ALICE_UID,
    challengerName: "Aurelia",
    challengerClass: "VANGUARD",
    challengerLevel: 10,
    challengerPower: 420,
    challengerAvatar: "1,2,1,0,3,2",
    challengerPetId: "SUNFOX_KIT",
    challengerHp: 300,
    challengerMaxHp: 300,
    challengerGuard: false,
    opponentUid: BOB_UID,
    opponentName: "Kael",
    opponentClass: "RANGER",
    opponentLevel: 9,
    opponentPower: 390,
    opponentAvatar: "0,1,2,3,1,0",
    opponentPetId: "DUNE_FALCON",
    opponentHp: 280,
    opponentMaxHp: 280,
    opponentGuard: false,
    status: "PENDING",
    turnUid: ALICE_UID,
    turnNumber: 1,
    winnerUid: "",
    lastActionLog: "Aurelia challenged Kael to a Solar Duel!",
    createdAt: ts,
    updatedAt: ts,
  };

  // Challenger creates PENDING duel
  await assertSucceeds(aliceDb.collection("multiplayer_duels").doc("duel_1").set(duel));

  // Participants query via array-contains; outsider cannot read or list
  await assertSucceeds(bobDb.collection("multiplayer_duels").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("multiplayer_duels").doc("duel_1").get());
  await assertFails(bobDb.collection("multiplayer_duels").get());

  // Challenger cannot self-accept
  await assertFails(
    aliceDb.collection("multiplayer_duels").doc("duel_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Opponent accepts duel
  await assertSucceeds(
    bobDb.collection("multiplayer_duels").doc("duel_1").update({
      status: "ACTIVE",
      lastActionLog: "Kael accepted! Aurelia moves first.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Active turn player (Alice) strikes and passes turn to Bob
  await assertSucceeds(
    aliceDb.collection("multiplayer_duels").doc("duel_1").update({
      opponentHp: 210,
      turnUid: BOB_UID,
      turnNumber: 2,
      lastActionLog: "Aurelia used Solar Strike for 70 DMG!",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Finish duel and delete
  await assertSucceeds(
    bobDb.collection("multiplayer_duels").doc("duel_1").update({
      challengerHp: 0,
      status: "COMPLETED",
      winnerUid: BOB_UID,
      lastActionLog: "Kael won the duel!",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(aliceDb.collection("multiplayer_duels").doc("duel_1").delete());
});

test("MultiplayerPetPlaydates: participant-scoped invite, accept, live play actions & outsider isolation", async () => {
  const ts = new Date(Date.now() - 2000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_outsider_03").firestore();

  const playdate = {
    participantUids: [ALICE_UID, BOB_UID],
    hostUid: ALICE_UID,
    hostName: "Aurelia",
    hostPetType: "SUNFOX_KIT",
    hostPetLevel: 5,
    guestUid: BOB_UID,
    guestName: "Kael",
    guestPetType: "DUNE_FALCON",
    guestPetLevel: 4,
    status: "PENDING",
    activity: "CHASE",
    playCount: 0,
    happinessBonus: 0,
    lastPlayLog: "Sunfox Kit invited Dune Falcon to play!",
    createdAt: ts,
    updatedAt: ts,
  };

  await assertSucceeds(aliceDb.collection("multiplayer_pet_playdates").doc("play_1").set(playdate));
  await assertSucceeds(bobDb.collection("multiplayer_pet_playdates").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("multiplayer_pet_playdates").doc("play_1").get());

  // Guest accepts playdate
  await assertSucceeds(
    bobDb.collection("multiplayer_pet_playdates").doc("play_1").update({
      status: "PLAYING",
      activity: "FETCH",
      playCount: 1,
      happinessBonus: 15,
      lastPlayLog: "Sunfox Kit & Dune Falcon played Astral Orb Fetch!",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Host triggers another play activity & then finishes
  await assertSucceeds(
    aliceDb.collection("multiplayer_pet_playdates").doc("play_1").update({
      activity: "SPAR",
      playCount: 2,
      happinessBonus: 30,
      lastPlayLog: "Friendly Paw Spar! Both companions are beaming!",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("multiplayer_pet_playdates").doc("play_1").update({
      status: "FINISHED",
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertSucceeds(bobDb.collection("multiplayer_pet_playdates").doc("play_1").delete());
});


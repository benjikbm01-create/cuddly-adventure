package dev.hollowatlas.client.dev;

import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.client.ClientConfig;
import dev.hollowatlas.client.ClientState;
import dev.hollowatlas.client.ui.AtlasScreen;
import dev.hollowatlas.client.ui.CraftScreen;
import dev.hollowatlas.client.ui.DialogueScreen;
import dev.hollowatlas.client.ui.JournalScreen;
import dev.hollowatlas.client.ui.SatchelScreen;
import dev.hollowatlas.client.ui.ShopScreen;
import dev.hollowatlas.combat.EncounterService;
import dev.hollowatlas.combat.EnemyEntity;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.npc.NpcEntity;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * DEVELOPMENT-ONLY end-to-end test driver. Active only with {@code -Dhollowatlas.autotest=phase1|phase2}.
 * Drives the real client through actual gameplay inputs (entity/block interaction packets, dialogue
 * choices, key intents) and checks the authoritative server state. Results are written to
 * {@code run/autotest_results.txt}. Teleports are used only to skip walking between test points;
 * the harness never calls quest-completion code directly.
 */
public final class AutoTest {
    private record Step(String name, int delay, Runnable action, String section) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();
    private static int index = 0, wait = 300;
    private static String phase;
    private static int pass = 0, fail = 0;

    private AutoTest() {}

    public static void init() {
        phase = System.getProperty("hollowatlas.autotest");
        if (phase == null || phase.isBlank()) return;
        if (phase.equals("phase1")) phase1(); else phase2();
        String only = System.getProperty("hollowatlas.autotest.only", "");
        if (!only.isBlank()) {
            java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(only.split(",")));
            keep.add("core");   // setup/opening always runs; other sections depend on it
            STEPS.removeIf(st -> !keep.contains(st.section()));
        }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
        HollowAtlas.LOGGER.info("[autotest] {} with {} steps", phase, STEPS.size());
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.getServer() == null) return;
        if (pendingTicks > 0 && --pendingTicks == 0 && pendingMove != null) { pendingMove.run(); pendingMove = null; }
        if (wait > 0) { wait--; return; }
        if (index >= STEPS.size()) {
            finish(mc);
            return;
        }
        Step s = STEPS.get(index++);
        try {
            s.action().run();
        } catch (Throwable t) {
            check(s.name() + " (exception: " + t + ")", false);
            HollowAtlas.LOGGER.error("[autotest] step {} threw", s.name(), t);
        }
        wait = index < STEPS.size() ? STEPS.get(index).delay() : 40;
    }

    private static boolean finished = false;
    private static int coinsBeforeDeath;
    private static int potatoesBefore;
    private static int shopCoins;

    private static void finish(MinecraftClient mc) {
        if (finished) return;
        finished = true;
        String summary = "[autotest] " + phase + " finished: " + pass + " passed, " + fail + " failed";
        RESULTS.add(summary);
        HollowAtlas.LOGGER.info(summary);
        try {
            Path out = mc.runDirectory.toPath().resolve("autotest_results_" + phase + ".txt");
            Files.write(out, RESULTS);
        } catch (IOException e) {
            HollowAtlas.LOGGER.error("could not write results", e);
        }
        mc.scheduleStop();
    }

    // ------------------------------------------------------------- helpers

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    private static <T> T srv(Function<MinecraftServer, T> f) {
        MinecraftServer s = mc().getServer();
        return s.submit(() -> f.apply(s)).join();
    }

    private static ServerPlayerEntity sp(MinecraftServer s) { return s.getPlayerManager().getPlayerList().get(0); }

    private static PlayerProfile profile() {
        return srv(s -> GameState.get(s).profile(sp(s).getUuid()));
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        String line = (ok ? "PASS " : "FAIL ") + name;
        RESULTS.add(line);
        HollowAtlas.LOGGER.info("[autotest] {}", line);
    }

    private static String section = "core";
    private static final double SCALE = Double.parseDouble(System.getProperty("hollowatlas.autotest.scale", "0.65"));

    /** Marks the start of an independently runnable section (select with -Dhollowatlas.autotest.only=a,b). */
    private static void section(String name) { section = name; }

    private static void step(String name, int delay, Runnable r) {
        STEPS.add(new Step(name, Math.max(3, (int) Math.round(delay * SCALE)), r, section));
    }

    private static Runnable pendingMove;
    private static int pendingTicks;

    /** Move like a player arriving: wait out of sight below ground while the area loads, then step into place. */
    private static void tp(String anchor, double dx, double dy, double dz, float yaw, float pitch, long time) {
        BlockPos b = LocationIndex.pos(anchor);
        srv(s -> {
            ServerPlayerEntity p = sp(s);
            if (time >= 0) s.getOverworld().setTimeOfDay(time);
            s.getOverworld().setWeather(12000, 0, false, false);
            // harness-only: protected while parked out of sight inside the ground
            p.getAbilities().invulnerable = true;
            p.getAbilities().allowFlying = true;
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
            p.teleport(s.getOverworld(), b.getX() + 0.5 + dx, b.getY() - 14, b.getZ() + 0.5 + dz, yaw, pitch);
            return null;
        });
        pendingMove = () -> srv(s -> {
            ServerPlayerEntity p = sp(s);
            p.teleport(s.getOverworld(), b.getX() + 0.5 + dx, b.getY() + dy, b.getZ() + 0.5 + dz, yaw, pitch);
            p.getAbilities().invulnerable = false;
            p.getAbilities().allowFlying = false;
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            return null;
        });
        pendingTicks = 35;
    }

    private static NpcEntity npc(String id) {
        NpcEntity best = null;
        for (Entity e : mc().world.getEntities()) {
            if (e instanceof NpcEntity n && id.equals(n.npcId())) best = n;
        }
        return best;
    }

    private static void approach(String id) {
        NpcEntity n = npc(id);
        if (n == null) return;
        Vec3d at = n.getPos();
        double[][] offs = {{1.2, 0}, {-1.2, 0}, {0, 1.2}, {0, -1.2}, {1.2, 1.2}, {-1.2, -1.2}, {1.2, -1.2}, {-1.2, 1.2}};
        Vec3d spot = at;
        for (double[] o : offs) {
            BlockPos feet = BlockPos.ofFloored(at.x + o[0], at.y, at.z + o[1]);
            if (mc().world.getBlockState(feet).getCollisionShape(mc().world, feet).isEmpty()
                    && mc().world.getBlockState(feet.up()).getCollisionShape(mc().world, feet.up()).isEmpty()) {
                spot = new Vec3d(at.x + o[0], at.y, at.z + o[1]);
                break;
            }
        }
        Vec3d go = spot;
        srv(s -> { sp(s).teleport(s.getOverworld(), go.x, go.y, go.z, 0, 0); return null; });
    }

    private static void interactNpc(String id) {
        if (mc().currentScreen != null) mc().currentScreen.close();   // never talk "through" a leftover screen
        NpcEntity n = npc(id);
        if (n == null) { check("resident present: " + id, false); return; }
        mc().player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, n.getEyePos());
        mc().interactionManager.interactEntity(mc().player, n, Hand.MAIN_HAND);
    }

    /** Adds the two steps needed to talk to a resident: walk up to them, then interact. */
    private static void talkSteps(String id, int delay) {
        // the server validates reach against its own (already teleported) player position, so step up and talk at once
        step("talk " + id, delay, () -> { approach(id); interactNpc(id); });
        step("retry talk " + id, 15, () -> {
            if (mc().currentScreen instanceof DialogueScreen) return;
            NpcEntity n = npc(id);
            HollowAtlas.LOGGER.info("[autotest] retrying talk with {} (distance {})", id, n == null ? -1 : mc().player.distanceTo(n));
            approach(id);
            interactNpc(id);
        });
    }

    private static void useBlock(String anchor) {
        BlockPos pos = LocationIndex.pos(anchor);
        useBlock(pos);
    }

    private static void useBlock(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc().interactionManager.interactBlock(mc().player, Hand.MAIN_HAND, hit);
    }

    private static void choose(String part) {
        if (!(mc().currentScreen instanceof DialogueScreen ds)) { check("dialogue open for '" + part + "'", false); return; }
        int i = ds.indexOf(part);
        if (i < 0) { check("choice '" + part + "' offered (have " + ds.choiceTexts() + ")", false); return; }
        ds.choose(i);
    }

    private static int count(Item item) {
        return srv(s -> RewardService.count(sp(s), item));
    }

    private static Item item(String id) { return Logic.item(id); }

    private static String questStage(String q) {
        PlayerProfile.QuestProgress qp = profile().quest(q);
        return qp == null ? "none" : qp.status == PlayerProfile.QuestProgress.Status.ACTIVE ? qp.stage : qp.status.name();
    }

    private static void shot(String name) {
        ScreenshotRecorder.saveScreenshot(mc().runDirectory, "autotest_" + name + ".png", mc().getFramebuffer(), t -> { });
    }

    private static void mode(GameMode m) {
        srv(s -> { sp(s).changeGameMode(m); return null; });
    }

    // ------------------------------------------------------------- phase 1

    private static void phase1() {
        step("setup", 0, () -> {
            ClientConfig.get().dialogueSpeed = 0;
            check("opening quest started on first join", questStage("m01").equals("arrive"));
            check("starting coins are 12", profile().coins == 12);
            check("starting weapon in pack", count(item("hollowatlas:traveler_shortsword")) == 1);
            check("adventure mode enforced", srv(s -> sp(s).interactionManager.getGameMode() == GameMode.ADVENTURE));
            check("first-join rain", srv(s -> s.getOverworld().isRaining()));
        });
        step("arrival shot", 40, () -> shot("01_arrival_hud"));
        step("protection: break attempt", 10, () -> {
            BlockPos below = mc().player.getBlockPos().down();
            mc().interactionManager.attackBlock(below, Direction.UP);
            mc().interactionManager.updateBlockBreakingProgress(below, Direction.UP);
        });
        step("protection: place attempt", 30, () -> {
            BlockPos below = mc().player.getBlockPos().down();
            check("ground still intact after break attempt", !mc().world.getBlockState(below).isAir());
            srv(s -> { sp(s).getInventory().setStack(8, new ItemStack(Items.DIRT, 4)); return null; });
        });
        step("protection: place attempt 2", 10, () -> {
            mc().player.getInventory().selectedSlot = 8;
            BlockPos below = mc().player.getBlockPos().down();
            useBlock(below);
        });
        step("protection: verify place", 20, () -> {
            BlockPos above = mc().player.getBlockPos().down().up();
            check("dirt was not placed", srv(s -> s.getOverworld().getBlockState(above).isAir() || !s.getOverworld().getBlockState(above).isOf(Blocks.DIRT)));
            srv(s -> { sp(s).getInventory().setStack(8, ItemStack.EMPTY); return null; });
            mc().player.getInventory().selectedSlot = 0;
        });
        step("walk to plaza", 10, () -> tp("plaza", 0, 0, 3, 180, 0, 3000));
        step("reach objective", 90, () -> {
            check("m01 advanced to 'inn' on reaching the village", questStage("m01").equals("inn"));
            check("village charted in atlas", profile().discovered.contains("brindlecross"));
        });
        step("go to inn", 5, () -> tp("inn.counter_front", 0, 0, 0, 90, 10, 3000));
        talkSteps("nella", 120);
        step("dialogue opened", 20, () -> {
            check("dialogue screen opens on talking to the innkeeper", mc().currentScreen instanceof DialogueScreen);
            shot("02_dialogue");
        });
        step("ask room", 5, () -> choose("I need a room"));
        step("background", 15, () -> choose("From the coast"));
        step("work", 15, () -> choose("Is there work"));
        step("thanks", 15, () -> choose("Thanks"));
        step("after nella", 20, () -> {
            check("dialogue closed after goodbye", !(mc().currentScreen instanceof DialogueScreen));
            check("received room key", count(item("hollowatlas:room_key")) == 1);
            check("background flag recorded", profile().has("background_coast"));
            check("m01 advanced to 'settle'", questStage("m01").equals("settle"));
            check("checkpoint moved to the inn", profile().checkpoint.equals("inn.common_room"));
        });
        step("rest in room", 5, () -> { tp("inn.room_guest", 0, 0, 0, 180, 30, 3000); });
        step("use bed", 70, () -> useBlock("inn.bed_room_guest"));
        step("rest result", 20, () -> check("optional rest objective counted", profile().quest("m01").counters.getOrDefault("settle/rest", 0) == 1));
        step("notice board", 5, () -> tp("notice_board.read", 0, 0, 0, 180, 0, 3000));
        step("read board", 70, () -> useBlock("notice_board.sign"));
        step("board dialogue", 20, () -> {
            check("notice board opens a readable panel", mc().currentScreen instanceof DialogueScreen);
            shot("03_notice_board");
        });
        step("close board", 5, () -> choose("Done reading"));
        step("m01 done", 30, () -> {
            check("m01 completed through play", questStage("m01").equals("COMPLETED"));
            check("m01 rewards: 2 hearth stew", count(item("hollowatlas:hearth_stew")) == 2);
        });
        step("journal", 5, () -> { ClientState.send("journal_request"); mc().setScreen(new JournalScreen(0)); });
        step("journal shot", 30, () -> { shot("04_journal"); mc().setScreen(null); });
        step("satchel", 5, () -> mc().setScreen(new SatchelScreen()));
        step("satchel shot", 30, () -> { shot("05_satchel"); mc().setScreen(null); });
        step("atlas", 5, () -> mc().setScreen(new AtlasScreen()));
        step("atlas shot", 30, () -> { shot("06_atlas"); mc().setScreen(null); });

        // ---- M03 offer from Corin (morning at the square)
        section("courier");
        step("find corin", 5, () -> tp("plaza_east", 0, 0, 2, 180, 0, 3000));
        talkSteps("corin", 120);
        step("corin 1", 20, () -> choose("Depends where"));
        step("corin 2", 15, () -> choose("Happy to help"));
        step("corin 3", 15, () -> choose("I'll see it there"));
        step("m03 started", 20, () -> {
            check("courier quest accepted via dialogue", questStage("m03").equals("relay"));
            check("sealed parcel received", count(item("hollowatlas:sealed_parcel")) == 1);
        });
        step("drop key item", 5, () -> {
            int slot = mc().player.getInventory().getSlotWithStack(new ItemStack(item("hollowatlas:sealed_parcel")));
            if (slot >= 0 && slot < 9) { mc().player.getInventory().selectedSlot = slot; mc().player.dropSelectedItem(true); }
            else check("parcel on hotbar for drop test (slot " + slot + ")", false);
        });
        step("drop verify", 30, () -> {
            check("key item can't be dropped", count(item("hollowatlas:sealed_parcel")) == 1);
            mc().player.getInventory().selectedSlot = 0;
        });

        // ---- M02 orchard pump
        section("pump");
        step("find tobin", 5, () -> tp("field_wheat", 0, 0, 0, 0, 0, 2000));
        talkSteps("tobin", 120);
        step("tobin 1", 20, () -> choose("I can take a look"));
        step("tobin 2", 15, () -> choose("I'll see what I can find"));
        step("m02 started", 20, () -> check("pump quest started", questStage("m02").equals("look")));
        step("pump", 5, () -> tp("orchard_pump.stand", 0, 0, 0, 180, 30, 2000));
        step("inspect pump", 70, () -> useBlock("orchard_pump.body"));
        step("inspect trough", 20, () -> useBlock("orchard_pump.trough"));
        step("ledger", 20, () -> tp("farmhouse.inside", 0, 0, 0, 0, 30, 2000));
        step("read ledger", 70, () -> useBlock("farmhouse.table_block"));
        step("m02 parts", 20, () -> {
            check("three clues advance the pump quest", questStage("m02").equals("parts"));
            check("clue recorded as evidence", profile().evidence.contains("pump_pin_sheared") && profile().evidence.contains("ledger_order"));
        });
        step("revisit clue", 5, () -> useBlock("farmhouse.table_block"));
        step("no dup", 20, () -> check("revisiting a clue doesn't duplicate evidence", profile().evidence.stream().filter(e -> e.equals("ledger_order")).count() == 1));
        step("smithy", 5, () -> tp("smithy.counter_front", 0, 0, 0, 180, 0, 3000));
        talkSteps("garrick", 120);
        step("garrick 1", 20, () -> choose("Can you make one now"));
        step("garrick 2", 15, () -> choose("Thank you"));
        step("pin", 20, () -> check("smith forged the pin", count(item("hollowatlas:pump_pin")) == 1));
        step("store", 5, () -> tp("store.counter_front", 0, 0, 0, 90, 0, 4000));
        talkSteps("ada", 120);
        step("ada leather", 20, () -> choose("I need leather"));
        step("ada pay", 15, () -> choose("Here's six"));
        step("leather", 20, () -> {
            check("bought valve leather for 6 coins", count(item("hollowatlas:valve_leather")) == 1 && profile().coins == 6);
            check("pump quest ready for repair", questStage("m02").equals("repair"));
        });
        step("back to pump", 5, () -> tp("orchard_pump.stand", 0, 0, 0, 180, 30, 4000));
        step("repair", 70, () -> useBlock("orchard_pump.body"));
        step("repaired", 30, () -> {
            check("pump repaired (world flag)", srv(s -> GameState.get(s).flag("hollowatlas:repaired_pump")));
            BlockPos trough = LocationIndex.pos("orchard_pump.trough");
            check("trough visibly filled with water", srv(s -> s.getOverworld().getBlockState(trough).isOf(Blocks.WATER_CAULDRON)));
            check("parts consumed", count(item("hollowatlas:pump_pin")) == 0 && count(item("hollowatlas:valve_leather")) == 0);
            shot("07_pump_repaired");
        });
        step("tell tobin", 5, () -> tp("field_wheat", 0, 0, 0, 0, 0, 4000));
        talkSteps("tobin", 120);
        step("tobin done", 20, () -> choose("It should hold"));
        step("m02 complete", 30, () -> {
            check("pump quest completed", questStage("m02").equals("COMPLETED"));
            check("pump reward paid once (26 coins)", profile().coins == 26);
        });

        // ---- shop buy / sell
        section("shop");
        step("shop", 5, () -> {
            srv(s -> { RewardService.give(sp(s), "minecraft:apple", 1); RewardService.coins(sp(s), 5); return null; });
            tp("store.counter_front", 0, 0, 0, 90, 0, 5000);
        });
        talkSteps("ada", 120);
        step("trade", 20, () -> choose("Let's trade"));
        step("shop open", 20, () -> { check("shop window opens", mc().currentScreen instanceof ShopScreen); shot("08_shop"); });
        step("buy bread", 5, () -> {
            shopCoins = profile().coins; JsonObject o = new JsonObject(); o.addProperty("shop", "general_store"); o.addProperty("item", "minecraft:bread"); ClientState.send("shop_buy", o); });
        step("bought", 20, () -> check("bought bread: 2 coins spent", profile().coins == shopCoins - 2));
        step("sell apple", 5, () -> {
            int slot = mc().player.getInventory().getSlotWithStack(new ItemStack(Items.APPLE));
            JsonObject o = new JsonObject(); o.addProperty("shop", "general_store"); o.addProperty("slot", slot); ClientState.send("shop_sell", o);
        });
        step("sold", 20, () -> check("sold an apple for 1 coin", profile().coins == shopCoins - 1));
        step("sell key", 5, () -> {
            int slot = mc().player.getInventory().getSlotWithStack(new ItemStack(item("hollowatlas:room_key")));
            JsonObject o = new JsonObject(); o.addProperty("shop", "general_store"); o.addProperty("slot", slot); ClientState.send("shop_sell", o);
        });
        step("key kept", 20, () -> { check("key item can't be sold", count(item("hollowatlas:room_key")) == 1 && profile().coins == shopCoins - 1); mc().setScreen(null); });

        // ---- gathering + cooking
        section("gather");
        step("carrots", 5, () -> tp("carrots_1", 0, 0, 2, 180, 40, 5000));
        step("pick carrots", 70, () -> useBlock("carrots_1"));
        step("potatoes", 20, () -> tp("potatoes_1", 0, 0, 2, 180, 40, 5000));
        step("pick potatoes", 70, () -> useBlock("potatoes_1"));
        step("gather twice", 20, () -> {
            potatoesBefore = count(Items.POTATO);
            useBlock("potatoes_1");
            check("gathered carrots and potatoes", count(Items.CARROT) >= 2 && potatoesBefore >= 2);
        });
        step("regrow check", 20, () -> check("node on cooldown gives nothing extra (" + potatoesBefore + " -> " + count(Items.POTATO) + ")",
                count(Items.POTATO) == potatoesBefore));
        step("mutton", 5, () -> srv(s -> { RewardService.give(sp(s), "minecraft:mutton", 1); return null; }));
        step("hearth", 5, () -> tp("inn.hearth", 0, 0, 0, 90, 20, 5000));
        step("use hearth", 70, () -> useBlock("inn.hearth_block"));
        step("craft window", 20, () -> { check("cooking window opens at the hearth", mc().currentScreen instanceof CraftScreen); shot("09_cooking"); });
        step("cook", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("station", "hearth"); o.addProperty("recipe", "hearth_stew"); ClientState.send("craft", o); });
        step("cooked", 20, () -> { check("cooked hearth stew from gathered food", count(item("hollowatlas:hearth_stew")) == 3); mc().setScreen(null); });

        // ---- combat
        section("combat");
        step("combat approach", 5, () -> { mode(GameMode.ADVENTURE); tp("abandoned_wagon.ambush", -14, 0, 0, -90, 0, 5000); });
        step("encounter", 130, () -> {
            check("road encounter spawns as the player approaches", EncounterService.activeCount() == 2);
            shot("10_encounter");
        });
        step("heavy", 5, () -> ClientState.send("heavy"));
        step("heavy check", 25, () -> check("heavy attack spends stamina", profile().stamina < 100));
        step("dodge", 30, () -> { JsonObject o = new JsonObject(); o.addProperty("x", 0); o.addProperty("z", -1); ClientState.send("dodge", o); });
        step("guard", 20, () -> { JsonObject o = new JsonObject(); o.addProperty("on", true); ClientState.send("guard", o); });
        step("guard shot", 20, () -> { check("guard state active", ClientState.b(ClientState.hud, "guarding")); shot("11_combat_hud"); });
        step("unguard", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("on", false); ClientState.send("guard", o); });
        for (int k = 0; k < 2; k++) step("defeat (harness) " + k, 10, () -> srv(s -> {
            // NOTE: the harness ends the fight directly; real melee is a manual QA item.
            for (EnemyEntity e : s.getOverworld().getEntitiesByClass(EnemyEntity.class, sp(s).getBoundingBox().expand(96), x -> true)) {
                float before = e.getHealth();
                boolean hit = e.damage(s.getOverworld().getDamageSources().playerAttack(sp(s)), 200f);
                HollowAtlas.LOGGER.info("[autotest] harness hit {}: {} -> {} (accepted={}, hurtTime={}, blocking={})",
                        e.kind(), before, e.getHealth(), hit, e.timeUntilRegen, e.isBlocking());
            }
            return null;
        }));
        step("defeat check", 10, () -> {
            int alive = srv(s -> s.getOverworld().getEntitiesByClass(EnemyEntity.class, sp(s).getBoundingBox().expand(96), EnemyEntity::isAlive).size());
            check("player-sourced damage defeats brigands (" + alive + " still alive)", alive == 0);
            if (alive > 0) srv(s -> {   // unblock the rest of the script; failure above is still reported
                s.getOverworld().getEntitiesByClass(EnemyEntity.class, sp(s).getBoundingBox().expand(96), x -> true).forEach(e -> e.kill());
                return null;
            });
        });
        step("cleared", 40, () -> {
            String diag = srv(s -> {
                StringBuilder b = new StringBuilder("active=" + EncounterService.activeCount() + " near:");
                for (EnemyEntity e : s.getOverworld().getEntitiesByClass(EnemyEntity.class, sp(s).getBoundingBox().expand(96), x -> true))
                    b.append(' ').append(e.kind()).append('@').append((int) e.getHealth()).append(e.isAlive() ? "" : "(dead)");
                return b.toString();
            });
            check("encounter cleared flag set [" + diag + "]", srv(s -> GameState.get(s).flag("hollowatlas:encounter_cleared:wagon_ambush")));
        });

        // ---- ability from Ilse
        step("find ilse", 5, () -> {
            Vec3d at = srv(s -> { NpcEntity n = NpcDirector.live("ilse"); return n == null ? null : n.getPos(); });
            if (at == null) tp("patrol_east", 0, 0, 0, 0, 0, 5000);
            else srv(s -> { sp(s).teleport(s.getOverworld(), at.x + 1.5, at.y, at.z, 90, 0); return null; });
        });
        talkSteps("ilse", 100);
        step("ilse train", 20, () -> choose("teach me"));
        step("ilse pay", 15, () -> choose("They're dealt with"));
        step("ilse bye", 20, () -> choose("Goodbye"));
        step("windstep", 15, () -> {
            check("learned Windstep from Ilse", profile().abilities.contains("windstep"));
            check("ability token added", count(item("hollowatlas:technique_windstep")) == 1);
        });
        step("use ability", 30, () -> { JsonObject o = new JsonObject(); o.addProperty("slot", 0); ClientState.send("ability", o); });
        step("ability used", 10, () -> {
            check("ability goes on cooldown", srv(s -> GameState.get(s).profile(sp(s).getUuid()).cooldowns.getOrDefault("windstep", 0L) > s.getOverworld().getTime()));
            shot("12_ability_cooldown");
        });
        step("spam ability", 2, () -> { JsonObject o = new JsonObject(); o.addProperty("slot", 0); ClientState.send("ability", o); });


        // ---- SQ01: investigation, case board, persistent routine + village change
        section("ribbon");
        step("sabine", 5, () -> tp("weaver_house.door_out", 0, 0, 0, 180, 0, 3000));
        talkSteps("sabine", 120);
        step("sabine 1", 20, () -> choose("When did it go missing"));
        step("sabine 2", 15, () -> choose("look into it"));
        step("sabine 3", 15, () -> choose("Goodbye"));
        step("sq01 started", 20, () -> check("ribbon quest started", questStage("sq01").equals("look")));
        step("to hook", 5, () -> tp("weaver_house.door_out", 0, 0, 0, 180, 30, 3000));
        step("hook", 70, () -> useBlock("weaver_house.post"));
        step("bakery", 20, () -> tp("bakery.door_out", 0, 0, 0, 180, 0, 3000));
        step("thread", 70, () -> useBlock("bakery.post"));
        step("bram", 20, () -> tp("smithy.counter_front", 0, 0, 0, 180, 0, 3000));
        talkSteps("bram", 120);
        step("bram 1", 20, () -> choose("Which way did the fox go"));
        step("bram 2", 15, () -> choose("Goodbye"));
        step("sq01 think", 20, () -> {
            check("clues + witness advance the ribbon quest", questStage("sq01").equals("think"));
            check("evidence kinds recorded (fact + testimony)", profile().evidence.contains("fox_tracks") && profile().evidence.contains("bram_saw_fox"));
        });
        step("journal case", 5, () -> { ClientState.send("journal_request"); mc().setScreen(new JournalScreen(1)); });
        step("case board shot", 30, () -> shot("15_case_board"));
        step("wrong theory", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("case", "ribbon_case"); o.addProperty("id", "maren_took_it"); ClientState.send("hypothesis", o); });
        step("change theory", 20, () -> {
            check("a theory can be recorded", "maren_took_it".equals(profile().hypotheses.get("ribbon_case")));
            JsonObject o = new JsonObject(); o.addProperty("case", "ribbon_case"); o.addProperty("id", "fox_took_it"); ClientState.send("hypothesis", o);
        });
        step("theory changed", 20, () -> {
            check("theory can be revised without losing evidence", "fox_took_it".equals(profile().hypotheses.get("ribbon_case")) && profile().evidence.contains("blue_thread"));
            check("ribbon quest moves on after a theory", questStage("sq01").equals("find"));
            mc().setScreen(null);
        });
        step("forged theory", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("case", "relay_case"); o.addProperty("id", "false_manifest"); ClientState.send("hypothesis", o); });
        step("forged rejected", 20, () -> check("server rejects a theory without its evidence", !profile().hypotheses.containsKey("relay_case")));
        step("den", 5, () -> tp("fox_den", 0, 0, 2, 180, 50, 3000));
        step("dig den", 70, () -> useBlock(LocationIndex.pos("fox_den").down()));
        step("ribbon", 20, () -> check("found the ribbon", count(item("hollowatlas:blue_ribbon")) == 1 && questStage("sq01").equals("return")));
        step("back to sabine", 5, () -> tp("weaver_house.door_out", 0, 0, 0, 180, 0, 3000));
        talkSteps("sabine", 120);
        step("return 1", 20, () -> choose("fox den"));
        step("return 2", 15, () -> choose("Goodbye"));
        step("returned", 20, () -> check("ribbon returned (key item taken back)", count(item("hollowatlas:blue_ribbon")) == 0 && questStage("sq01").equals("stall")));
        step("ada stall", 5, () -> tp("store.counter_front", 0, 0, 0, 90, 0, 4000));
        talkSteps("ada", 120);
        step("ada decide", 20, () -> choose("Both"));
        step("ada bye", 15, () -> choose("Goodbye"));
        step("stall outcome", 30, () -> {
            check("side quest completed", questStage("sq01").equals("COMPLETED"));
            check("resident routines changed (variants)", srv(s -> "shared_stall".equals(GameState.get(s).npc("maren").variant) && "shared_stall".equals(GameState.get(s).npc("sabine").variant)));
            BlockPos canopy = LocationIndex.pos("stall_b.work").add(0, 3, 0);
            check("market stall visibly changed", srv(s -> s.getOverworld().getBlockState(canopy).isOf(Blocks.LIGHT_BLUE_WOOL) || s.getOverworld().getBlockState(canopy).isOf(Blocks.YELLOW_WOOL)));
        });
        step("stall shot", 5, () -> tp("plaza", 0, 4, 10, 180, 15, 6500));
        step("stall view", 90, () -> shot("16_market_stall_reopened"));

        // ---- reward overflow, HUD truth, difficulty
        section("systems");
        step("fill pack", 5, () -> srv(s -> {
            var inv = sp(s).getInventory();
            for (int i = 0; i < inv.main.size(); i++) if (inv.main.get(i).isEmpty()) inv.main.set(i, new ItemStack(Items.COBBLESTONE, 64));
            RewardService.give(sp(s), "minecraft:honeycomb", 3);
            return null;
        }));
        step("queued", 10, () -> check("reward queued when the pack is full", profile().pendingRewards.getOrDefault("minecraft:honeycomb", 0) == 3));
        step("free space", 5, () -> srv(s -> {
            var inv = sp(s).getInventory();
            for (int i = 0; i < inv.main.size(); i++) if (inv.main.get(i).isOf(Items.COBBLESTONE)) inv.main.set(i, ItemStack.EMPTY);
            return null;
        }));
        step("delivered", 60, () -> check("queued reward delivered once space frees", profile().pendingRewards.isEmpty()));
        step("hud truth", 10, () -> {
            check("HUD coins match server (" + ClientState.i(ClientState.hud, "coins", -1) + ")", ClientState.i(ClientState.hud, "coins", -1) == profile().coins);
            check("HUD stamina tracks server", Math.abs(ClientState.f(ClientState.hud, "stamina", -100) - profile().stamina) < 8);
        });
        step("difficulty", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("key", "difficulty"); o.addProperty("value", "hard"); ClientState.send("setting", o); });
        step("difficulty set", 20, () -> check("difficulty can be changed in settings", srv(s -> GameState.get(s).difficulty.equals("hard"))));
        step("difficulty back", 5, () -> { JsonObject o = new JsonObject(); o.addProperty("key", "difficulty"); o.addProperty("value", "normal"); ClientState.send("setting", o); });

        // ---- residents: sleeping and talking to each other
        section("residents");
        step("night inn", 5, () -> tp("inn.room_c", 0, 0, -1, 180, 25, 20000));
        step("asleep", 420, () -> {
            NpcEntity n = npc("nella");
            String diag = srv(s -> {
                NpcEntity e = NpcDirector.live("nella");
                if (e == null) return "not live";
                BlockPos bed = LocationIndex.pos("inn.bed_room_c");
                return "pos=" + e.getBlockPos().toShortString() + " bed=" + bed.toShortString() + " block=" + s.getOverworld().getBlockState(bed).getBlock()
                        + " phase=" + (e.npcBrain() == null ? "?" : e.npcBrain().phase() + " entry=" + e.npcBrain().entry().activity + " stuck=" + e.npcBrain().stuckEvents())
                        + " sleeping=" + e.isSleeping();
            });
            check("innkeeper asleep in her bed at night [" + diag + "]", n != null && n.isSleeping());
            shot("17_night_asleep");
        });
        step("talk asleep", 5, () -> { approach("nella"); });
        step("talk asleep 2", 12, () -> interactNpc("nella"));
        step("asleep reply", 20, () -> {
            check("talking to a sleeping resident is safe", !(mc().currentScreen instanceof DialogueScreen) || ((DialogueScreen) mc().currentScreen).choiceTexts().size() >= 1);
            if (mc().currentScreen != null) mc().currentScreen.close();
        });
        step("evening farm", 5, () -> tp("farmhouse.door_out", 2, 0, 3, 180, 0, 12600));
        step("chatter", 400, () -> check("two residents held a conversation (" + dev.hollowatlas.npc.AmbientChatter.played() + ")", dev.hollowatlas.npc.AmbientChatter.played() > 0));
        step("third person", 5, () -> { mode(GameMode.ADVENTURE); mc().options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); });
        step("third person heavy", 20, () -> ClientState.send("heavy"));
        step("third person shot", 20, () -> { shot("18_third_person"); mc().options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON); });

        // ---- death and checkpoint
        section("death");
        step("die", 30, () -> {
            coinsBeforeDeath = profile().coins;
            srv(s -> { sp(s).damage(s.getOverworld().getDamageSources().genericKill(), 1000f); return null; });
        });
        step("respawn", 40, () -> mc().player.requestRespawn());
        step("after death", 60, () -> {
            BlockPos cp = LocationIndex.pos("inn.room_guest");
            check("respawned at the last checkpoint", mc().player.getBlockPos().getSquaredDistance(cp) < 9);
            check("inventory kept on death (key=" + count(item("hollowatlas:room_key")) + " sword=" + count(item("hollowatlas:traveler_shortsword"))
                    + " parcel=" + count(item("hollowatlas:sealed_parcel")) + ")", count(item("hollowatlas:room_key")) == 1 && count(item("hollowatlas:sealed_parcel")) == 1);
            check("small transparent coin cost (" + coinsBeforeDeath + " -> " + profile().coins + ")",
                    profile().coins == coinsBeforeDeath - Math.min(25, coinsBeforeDeath / 10));
            check("stamina reset on respawn", profile().stamina >= 99);
        });

        // ---- residents
        section("evening");
        step("village evening", 5, () -> tp("plaza", -10, 6, 16, -150, 20, 12500));
        step("residents", 140, () -> {
            int live = srv(s -> (int) NpcDirector.liveView().values().stream().filter(e -> !e.isRemoved()).count());
            check("several residents live near the square (" + live + ")", live >= 4);
            shot("13_village_evening");
        });
        step("save state", 5, () -> {
            PlayerProfile p = profile();
            try {
                Files.writeString(mc().runDirectory.toPath().resolve("autotest_expect.txt"), p.coins + "\n");
            } catch (IOException ignored) { }
        });
    }

    // ------------------------------------------------------------- phase 2: after relaunch

    private static void phase2() {
        step("persistence", 0, () -> {
            int expectCoins = -1;
            try { expectCoins = Integer.parseInt(Files.readString(mc().runDirectory.toPath().resolve("autotest_expect.txt")).trim()); } catch (Exception ignored) { }
            PlayerProfile p = profile();
            check("quest progress persisted across relaunch", "COMPLETED".equals(questStage("m01")) && "COMPLETED".equals(questStage("m02")) && questStage("m03").equals("relay"));
            check("coins persisted (" + p.coins + ")", p.coins == expectCoins);
            check("evidence persisted", p.evidence.contains("ledger_order"));
            check("ability persisted", p.abilities.contains("windstep"));
            check("key items persisted", count(item("hollowatlas:room_key")) == 1 && count(item("hollowatlas:sealed_parcel")) == 1);
            check("world change persisted (pump trough)", srv(s -> s.getOverworld().getBlockState(LocationIndex.pos("orchard_pump.trough")).isOf(Blocks.WATER_CAULDRON)));
            check("encounter cleared persisted", srv(s -> GameState.get(s).flag("hollowatlas:encounter_cleared:wagon_ambush")));
            check("resident records persisted", srv(s -> GameState.get(s).npcs.size() >= 15));
            check("routine variant persisted", srv(s -> "shared_stall".equals(GameState.get(s).npc("maren").variant)));
            check("case theory persisted", "fox_took_it".equals(p.hypotheses.get("ribbon_case")));
        });
        step("hud after reload", 60, () -> shot("14_after_reload"));
    }
}

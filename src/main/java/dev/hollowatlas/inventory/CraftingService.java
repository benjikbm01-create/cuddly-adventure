package dev.hollowatlas.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.investigation.Interactions;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.quest.QuestEngine;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Station crafting (cooking hearth, workbench...). Recipes are data; validation is server-side. */
public final class CraftingService {
    public static final Map<String, Recipe> RECIPES = ContentManager.register("recipes", Recipe.class);

    public static final class Recipe implements ContentManager.Validatable {
        public String id;
        public String station;
        public String name;
        public List<Stack> inputs = new ArrayList<>();
        public Stack output;
        public String requires_flag;    // recipe must be learned
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            if (output == null) problems.add(file + ": recipe without output");
            if (inputs.isEmpty()) problems.add(file + ": recipe without inputs");
        }
    }

    public static final class Stack { public String item; public int count = 1; }

    private CraftingService() {}

    public static void init() {
        Net.on("craft", (player, body) -> craft(player, body.get("station").getAsString(), body.get("recipe").getAsString()));
    }

    private static boolean nearStation(ServerPlayerEntity player, String station) {
        for (Map.Entry<BlockPos, Interactions.Def> e : Interactions.all().entrySet()) {
            if ("station".equals(e.getValue().type) && station.equals(e.getValue().station)
                    && player.squaredDistanceTo(e.getKey().toCenterPos()) < 6 * 6) return true;
        }
        return false;
    }

    private static boolean known(ServerPlayerEntity player, Recipe r) {
        if (r.requires_flag == null) return true;
        return Logic.test(player, Logic.flagCond(r.requires_flag), Logic.Ctx.NONE);
    }

    public static void open(ServerPlayerEntity player, String station) {
        JsonObject o = new JsonObject();
        o.addProperty("station", station);
        JsonArray arr = new JsonArray();
        for (Recipe r : RECIPES.values()) {
            if (!r.station.equals(station) || !known(player, r)) continue;
            JsonObject j = new JsonObject();
            j.addProperty("id", r.id);
            j.addProperty("name", r.name);
            j.addProperty("output", r.output.item);
            j.addProperty("output_count", r.output.count);
            JsonArray in = new JsonArray();
            boolean can = true;
            for (Stack s : r.inputs) {
                JsonObject ji = new JsonObject();
                int have = RewardService.count(player, Logic.item(s.item));
                ji.addProperty("item", s.item);
                ji.addProperty("count", s.count);
                ji.addProperty("have", have);
                if (have < s.count) can = false;
                in.add(ji);
            }
            j.add("inputs", in);
            j.addProperty("can", can);
            arr.add(j);
        }
        o.add("recipes", arr);
        Net.send(player, "craft", o);
    }

    private static void craft(ServerPlayerEntity player, String station, String recipeId) {
        Recipe r = RECIPES.get(recipeId);
        if (r == null || !r.station.equals(station) || !known(player, r) || !nearStation(player, station)) return;
        for (Stack s : r.inputs) if (RewardService.count(player, Logic.item(s.item)) < s.count) { open(player, station); return; }
        for (Stack s : r.inputs) RewardService.take(player, Logic.item(s.item), s.count);
        RewardService.give(player, r.output.item, r.output.count);
        player.playSoundToPlayer(SoundEvents.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS, 1f, 1f);
        player.sendMessage(Text.translatable("message.hollowatlas.crafted", r.name).formatted(Formatting.GREEN), true);
        QuestEngine.event(player, "craft", r.id);
        open(player, station);
    }
}

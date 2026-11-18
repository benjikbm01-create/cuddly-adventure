package dev.hollowatlas.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.hollowatlas.combat.EncounterService;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.quest.QuestDef;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/**
 * Operator-only developer tools. Deliberately absent: any way to complete or advance a story quest.
 * Quest inspection prints ids and stage ids only (no story text) so logs stay spoiler-free.
 */
public final class DevCommands {
    private DevCommands() {}

    public static void init() {
        AtlasCommands.extend(root -> {
            root.then(CommandManager.literal("validate").executes(ctx -> {
                List<String> rep = ContentManager.report();
                List<String> anchorProblems = LocationIndex.model().warnings;
                String msg = "Content problems: " + rep.size() + (rep.isEmpty() ? " (all content valid)" : "\n" + String.join("\n", rep))
                        + "\nRegion warnings: " + anchorProblems.size() + " | anchors: " + LocationIndex.names().size();
                ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                return rep.isEmpty() ? 1 : 0;
            }));
            root.then(CommandManager.literal("profile").executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal(NpcDirector.profile()
                        + " | active enemies: " + EncounterService.activeCount()), false);
                return 1;
            }));
            root.then(CommandManager.literal("quest")
                    .then(CommandManager.literal("list").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        PlayerProfile prof = GameState.get(p.getServer()).profile(p.getUuid());
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, PlayerProfile.QuestProgress> e : prof.quests.entrySet()) {
                            // Spoiler-safe: story quests show only their opaque id and stage number, never stage names.
                            QuestDef def = QuestEngine.QUESTS.get(e.getKey());
                            String stage = e.getValue().stage;
                            if (def != null && !dev.hollowatlas.content.ContentManager.isDevtest(def)) {
                                int idx = 0;
                                for (int i = 0; i < def.stages.size(); i++) if (def.stages.get(i).id.equals(stage)) idx = i + 1;
                                stage = "#" + idx + "/" + def.stages.size();
                            }
                            sb.append(e.getKey()).append(" ").append(e.getValue().status).append(" stage=").append(stage)
                                    .append(" counters=").append(e.getValue().counters.size()).append('\n');
                        }
                        ctx.getSource().sendFeedback(() -> Text.literal(sb.isEmpty() ? "no quests" : sb.toString()), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("reset_sandbox").executes(ctx -> {
                        // Only the non-canonical sandbox quest may be reset.
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        GameState gs = GameState.get(p.getServer());
                        PlayerProfile prof = gs.profile(p.getUuid());
                        int n = 0;
                        for (QuestDef d : QuestEngine.QUESTS.values()) {
                            if ("sandbox".equals(d.category) && prof.quests.remove(d.id) != null) n++;
                        }
                        prof.flags.removeIf(f -> f.startsWith("sandbox_") || f.startsWith("int:sandbox_"));
                        gs.markDirty();
                        QuestEngine.sync(p);
                        int fn = n;
                        ctx.getSource().sendFeedback(() -> Text.literal("Reset " + fn + " sandbox quest(s)."), true);
                        return 1;
                    })));
            root.then(CommandManager.literal("difficulty")
                    .then(CommandManager.argument("level", StringArgumentType.word())
                            .suggests((c, b) -> CommandSource.suggestMatching(List.of("story", "normal", "hard", "expert"), b))
                            .executes(ctx -> {
                                String lvl = StringArgumentType.getString(ctx, "level");
                                if (!List.of("story", "normal", "hard", "expert").contains(lvl)) return 0;
                                GameState gs = GameState.get(ctx.getSource().getServer());
                                gs.difficulty = lvl;
                                gs.markDirty();
                                ctx.getSource().sendFeedback(() -> Text.literal("Difficulty: " + lvl), true);
                                return 1;
                            })));
        });
    }
}

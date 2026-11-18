package dev.hollowatlas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.ProtectionService;
import dev.hollowatlas.world.RegionBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Developer/test commands under {@code /atlas}. Every subcommand requires permission level 2
 * (operator / cheats enabled), so they are unavailable in normal single-player play.
 * Other systems contribute subcommands through {@link #extend(Consumer)}.
 */
public final class AtlasCommands {
    private static final List<Consumer<LiteralArgumentBuilder<ServerCommandSource>>> EXTENSIONS = new ArrayList<>();

    private AtlasCommands() {}

    public static void extend(Consumer<LiteralArgumentBuilder<ServerCommandSource>> extension) {
        EXTENSIONS.add(extension);
    }

    public static final SuggestionProvider<ServerCommandSource> ANCHORS =
            (ctx, b) -> CommandSource.suggestMatching(LocationIndex.names(), b);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("atlas")
                .requires(src -> src.hasPermissionLevel(2));

        root.then(CommandManager.literal("build").executes(ctx -> {
            boolean ok = RegionBuilder.start(ctx.getSource().getServer().getOverworld(), LocationIndex.model(), null);
            ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Region build started." : "A build is already running."), true);
            return ok ? 1 : 0;
        }));
        root.then(CommandManager.literal("status").executes(ctx -> {
            GameState s = GameState.get(ctx.getSource().getServer());
            ctx.getSource().sendFeedback(() -> Text.literal("Region built: " + s.flag(RegionBuilder.BUILT_FLAG)
                    + " | builder: " + RegionBuilder.status() + " | world flags: " + s.worldFlags.size()
                    + " | profiles: " + s.players.size()), false);
            return 1;
        }));
        root.then(CommandManager.literal("builder")
                .then(CommandManager.literal("on").executes(ctx -> builder(ctx, true)))
                .then(CommandManager.literal("off").executes(ctx -> builder(ctx, false))));
        root.then(CommandManager.literal("tp")
                .then(CommandManager.argument("anchor", StringArgumentType.greedyString()).suggests(ANCHORS)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "anchor");
                            if (!LocationIndex.has(name)) {
                                ctx.getSource().sendError(Text.literal("Unknown anchor " + name));
                                return 0;
                            }
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            BlockPos pos = LocationIndex.pos(name);
                            p.teleport(ctx.getSource().getServer().getOverworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYaw(), p.getPitch());
                            return 1;
                        })));
        root.then(CommandManager.literal("rendermap").executes(ctx -> {
            dev.hollowatlas.HollowAtlas.renderMap(ctx.getSource().getServer());
            ctx.getSource().sendFeedback(() -> Text.literal("Rendered <world>/hollowatlas/region_map.png"), false);
            return 1;
        }));
        root.then(CommandManager.literal("where").executes(ctx -> {
            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
            String n = LocationIndex.nearest(p.getBlockPos());
            ctx.getSource().sendFeedback(() -> Text.literal("Nearest anchor: " + n + " " + LocationIndex.pos(n).toShortString()), false);
            return 1;
        }));
        root.then(CommandManager.literal("anchors")
                .then(CommandManager.argument("prefix", StringArgumentType.word()).executes(ctx -> {
                    String pre = StringArgumentType.getString(ctx, "prefix");
                    List<String> hits = LocationIndex.names().stream().filter(n -> n.startsWith(pre)).sorted().toList();
                    ctx.getSource().sendFeedback(() -> Text.literal(hits.size() + ": " + String.join(", ", hits)), false);
                    return hits.size();
                })));
        for (Consumer<LiteralArgumentBuilder<ServerCommandSource>> e : EXTENSIONS) e.accept(root);
        dispatcher.register(root);
    }

    private static int builder(CommandContext<ServerCommandSource> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ProtectionService.setBuilder(p, on);
        ctx.getSource().sendFeedback(() -> Text.literal("Builder mode " + (on ? "ON (protection bypassed)" : "OFF")), true);
        return 1;
    }
}

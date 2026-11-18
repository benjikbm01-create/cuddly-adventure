package dev.hollowatlas.npc;

import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.state.GameState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Background conversations between residents. A conversation plays only when its speakers are
 * actually standing together, a player is close enough to overhear, and its time/weather/flag
 * conditions hold. Lines are delivered a few seconds apart; speakers face each other.
 */
public final class AmbientChatter {
    public static final Map<String, Conversation> CONVERSATIONS = ContentManager.register("ambient", Conversation.class);

    public static final class Conversation implements ContentManager.Validatable {
        public String id;
        public List<String> speakers = new ArrayList<>();
        public double from = 0, to = 24;
        public String weather = "any";            // any | rain | clear
        public List<String> requires_flags = new ArrayList<>();  // world flags
        public List<String> forbids_flags = new ArrayList<>();
        public double radius = 6;
        public int cooldown_minutes = 20;          // real-time minutes between repeats
        public List<List<String>> lines = new ArrayList<>();   // [speakerId, text]

        @Override public String id() { return id; }

        @Override
        public void validate(String file, List<String> problems) {
            if (speakers.size() < 2) problems.add(file + ": ambient conversation needs two speakers");
            for (List<String> l : lines) {
                if (l.size() != 2 || !speakers.contains(l.get(0))) problems.add(file + ": bad line " + l);
            }
        }

        boolean timeOk(double h) { return from <= to ? h >= from && h < to : h >= from || h < to; }
    }

    private record Pending(long at, String speaker, String text, Conversation conv) {}

    private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
    private static final Map<String, Long> LAST = new HashMap<>();
    private static int played;

    private AmbientChatter() {}

    public static int played() { return played; }

    /** Forces class init so the content kind is registered before the first data reload. */
    public static void init() { }

    public static void tick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        long now = world.getTime();
        while (!QUEUE.isEmpty() && QUEUE.peek().at() <= now) {
            Pending p = QUEUE.poll();
            NpcEntity speaker = NpcDirector.live(p.speaker());
            if (speaker == null) { QUEUE.removeIf(q -> q.conv() == p.conv()); continue; }
            for (String other : p.conv().speakers) {
                NpcEntity o = NpcDirector.live(other);
                if (o != null && o != speaker) {
                    speaker.getLookControl().lookAt(o, 30f, 30f);
                    if (speaker.npcBrain() != null) speaker.npcBrain().pause(70);
                }
            }
            NpcDirector.say(world, speaker, p.text(), 16);
        }
        if (now % 100 != 0 || !QUEUE.isEmpty()) return;
        double hour = ScheduleService.hour(world);
        GameState state = GameState.get(server);
        for (Conversation c : CONVERSATIONS.values()) {
            Long last = LAST.get(c.id);
            if (last != null && now - last < c.cooldown_minutes * 1200L) continue;
            if (!c.timeOk(hour)) continue;
            if (c.weather.equals("rain") && !world.isRaining()) continue;
            if (c.weather.equals("clear") && world.isRaining()) continue;
            if (!c.requires_flags.stream().allMatch(state::flag)) continue;
            if (c.forbids_flags.stream().anyMatch(state::flag)) continue;
            List<NpcEntity> who = new ArrayList<>();
            for (String s : c.speakers) {
                NpcEntity e = NpcDirector.live(s);
                if (e == null || e.isSleeping()) { who.clear(); break; }
                who.add(e);
            }
            if (who.size() < 2) continue;
            boolean together = who.stream().allMatch(e -> e.squaredDistanceTo(who.get(0)) <= c.radius * c.radius);
            if (!together) continue;
            boolean overheard = false;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (!p.isSpectator() && p.squaredDistanceTo(who.get(0)) < 14 * 14) overheard = true;
            }
            if (!overheard) continue;
            LAST.put(c.id, now);
            long t = now;
            for (List<String> line : c.lines) {
                QUEUE.add(new Pending(t, line.get(0), line.get(1), c));
                t += 50 + line.get(1).length();
            }
            played++;
            return;
        }
    }
}

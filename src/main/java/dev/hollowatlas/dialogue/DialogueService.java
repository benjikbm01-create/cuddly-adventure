package dev.hollowatlas.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.npc.NpcDefinition;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.npc.NpcEntity;
import dev.hollowatlas.npc.NpcInteractions;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Branching conversations. Trees live in {@code data/hollowatlas/dialogue/<id>.json}. The server owns
 * the session; the client only renders nodes and returns the index of a choice it was offered, tagged
 * with the session sequence number so duplicate clicks or stale packets are ignored.
 */
public final class DialogueService {
    public static final Map<String, Tree> TREES = ContentManager.register("dialogue", Tree.class);

    public static final class Tree implements ContentManager.Validatable {
        public String id;
        public String npc;
        /** Ordered entry rules; the first whose condition passes picks the opening node. */
        public List<Entry> entries = new ArrayList<>();
        public Map<String, Node> nodes = new LinkedHashMap<>();

        @Override public String id() { return id; }

        @Override
        public void validate(String file, List<String> problems) {
            for (Entry e : entries) if (!nodes.containsKey(e.node)) problems.add(file + ": entry to missing node " + e.node);
            for (Map.Entry<String, Node> n : nodes.entrySet()) {
                Node node = n.getValue();
                if (node.next != null && !node.next.equals("end") && !nodes.containsKey(node.next))
                    problems.add(file + ": node " + n.getKey() + " -> missing " + node.next);
                for (Choice c : node.choices) {
                    if (c.go != null && !c.go.equals("end") && !nodes.containsKey(c.go))
                        problems.add(file + ": choice in " + n.getKey() + " -> missing node " + c.go);
                    Logic.validateActions(file + "#" + n.getKey(), c.actions, problems);
                }
                Logic.validateActions(file + "#" + n.getKey(), node.actions, problems);
                if (node.text == null && node.variants.isEmpty()) problems.add(file + ": node " + n.getKey() + " has no text");
            }
        }
    }

    public static final class Entry { public JsonElement when; public String node; }

    public static final class Node {
        public String speaker;              // npc id, "player" or "narrator"; default = tree npc
        public String text;
        public List<Variant> variants = new ArrayList<>();   // first matching replaces text
        public JsonElement actions;          // run when the node is shown (once per visit)
        public List<Choice> choices = new ArrayList<>();
        public String next;                  // auto-continue target when there are no choices
    }

    public static final class Variant { public JsonElement when; public String text; }

    public static final class Choice {
        public String text;
        public String tag;                   // ask | investigate | persuade | intimidate | evidence | leave | quest
        public JsonElement when;             // hidden unless true
        public JsonElement enabled_when;     // shown but greyed out unless true (e.g. needs evidence)
        public String disabled_hint;
        public JsonElement actions;
        public String go;                    // node id or "end"
        public boolean once = false;
    }

    private static final class Session {
        final String tree;
        final String npc;
        final int entityId;
        String node;
        int seq;
        List<Integer> offered = new ArrayList<>();
        final Deque<String> history = new ArrayDeque<>();
        Session(String tree, String npc, int entityId) { this.tree = tree; this.npc = npc; this.entityId = entityId; }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int seqCounter = 1;

    private DialogueService() {}

    public static void init() {
        NpcInteractions.setHandler(DialogueService::open);
        Net.on("dlg_choose", (player, body) -> choose(player, body.get("seq").getAsInt(), body.get("index").getAsInt()));
        Net.on("dlg_back", (player, body) -> back(player, body.get("seq").getAsInt()));
        Net.on("dlg_close", (player, body) -> {
            Session s = SESSIONS.get(player.getUuid());
            if (s != null && s.seq == body.get("seq").getAsInt()) SESSIONS.remove(player.getUuid());
        });
    }

    public static boolean inDialogue(ServerPlayerEntity p) { return SESSIONS.containsKey(p.getUuid()); }

    public static void open(ServerPlayerEntity player, NpcEntity npc) {
        NpcDefinition def = NpcDirector.DEFS.get(npc.npcId());
        if (def == null || def.dialogue == null) { NpcDirector.bark(npc, "greet"); return; }
        Tree tree = TREES.get(def.dialogue);
        if (tree == null) { NpcDirector.bark(npc, "greet"); return; }
        QuestEngine.event(player, "talk", def.id);
        Logic.Ctx ctx = new Logic.Ctx(def.id, null);
        String start = null;
        if (npc.isSleeping()) start = tree.nodes.containsKey("asleep") ? "asleep" : null;
        if (start == null && npc.isSleeping()) { NpcDirector.say(player.getServerWorld(), npc, "Zzz...", 6); return; }
        if (start == null) {
            for (Entry e : tree.entries) if (Logic.test(player, e.when, ctx)) { start = e.node; break; }
        }
        if (start == null) { NpcDirector.bark(npc, "greet"); return; }
        Session s = new Session(tree.id, def.id, npc.getId());
        SESSIONS.put(player.getUuid(), s);
        show(player, s, start, true);
    }

    /** Start a conversation from a non-resident source (notice board, letter...). */
    public static void openTree(ServerPlayerEntity player, String treeId, String node) {
        Tree tree = TREES.get(treeId);
        if (tree == null) return;
        Session s = new Session(tree.id, tree.npc, -1);
        SESSIONS.put(player.getUuid(), s);
        show(player, s, node, true);
    }

    private static void show(ServerPlayerEntity player, Session s, String nodeId, boolean runActions) {
        Tree tree = TREES.get(s.tree);
        if (tree == null || nodeId == null || nodeId.equals("end") || !tree.nodes.containsKey(nodeId)) { close(player, s); return; }
        Node node = tree.nodes.get(nodeId);
        Logic.Ctx ctx = new Logic.Ctx(s.npc, null);
        s.node = nodeId;
        s.seq = seqCounter++;
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        p.flags.add("seen:" + s.tree + ":" + nodeId);
        if (runActions) Logic.run(player, node.actions, ctx);
        // session may have been replaced by an action (e.g. shop opened)
        if (SESSIONS.get(player.getUuid()) != s) return;
        String text = node.text;
        for (Variant v : node.variants) if (Logic.test(player, v.when, ctx)) { text = v.text; break; }
        JsonObject out = new JsonObject();
        out.addProperty("seq", s.seq);
        out.addProperty("tree", s.tree);
        out.addProperty("node", nodeId);
        String speakerId = node.speaker != null ? node.speaker : s.npc;
        NpcDefinition sd = speakerId == null ? null : NpcDirector.DEFS.get(speakerId);
        out.addProperty("speaker", "player".equals(speakerId) ? player.getName().getString()
                : "narrator".equals(speakerId) ? "" : sd != null ? sd.name : speakerId);
        out.addProperty("title", sd != null && sd.title != null ? sd.title : "");
        out.addProperty("entity", s.entityId);
        out.addProperty("key", "dialogue.hollowatlas." + s.tree + "." + nodeId);
        out.addProperty("text", fill(text, player, p));
        out.addProperty("can_back", !s.history.isEmpty());
        JsonArray choices = new JsonArray();
        s.offered.clear();
        for (int i = 0; i < node.choices.size(); i++) {
            Choice c = node.choices.get(i);
            if (c.once && p.flags.contains("chose:" + s.tree + ":" + nodeId + ":" + i)) continue;
            if (!Logic.test(player, c.when, ctx)) continue;
            boolean enabled = Logic.test(player, c.enabled_when, ctx);
            JsonObject jc = new JsonObject();
            jc.addProperty("key", "dialogue.hollowatlas." + s.tree + "." + nodeId + ".c" + i);
            jc.addProperty("text", fill(c.text, player, p));
            jc.addProperty("tag", c.tag == null ? "" : c.tag);
            jc.addProperty("enabled", enabled);
            if (!enabled && c.disabled_hint != null) jc.addProperty("hint", c.disabled_hint);
            choices.add(jc);
            s.offered.add(enabled ? i : -1 - i);
        }
        if (s.offered.isEmpty()) {
            JsonObject jc = new JsonObject();
            boolean more = node.next != null && !node.next.equals("end");
            jc.addProperty("key", more ? "dialogue.hollowatlas.continue" : "dialogue.hollowatlas.goodbye");
            jc.addProperty("text", more ? "(Continue)" : "(Leave)");
            jc.addProperty("tag", more ? "continue" : "leave");
            jc.addProperty("enabled", true);
            choices.add(jc);
            s.offered.add(Integer.MIN_VALUE);
        }
        out.add("choices", choices);
        GameState.get(player.getServer()).markDirty();
        Net.send(player, "dialogue", out);
    }

    private static String fill(String text, ServerPlayerEntity player, PlayerProfile p) {
        if (text == null) return "";
        return text.replace("{player}", player.getName().getString()).replace("{coins}", Integer.toString(p.coins));
    }

    private static void choose(ServerPlayerEntity player, int seq, int index) {
        Session s = SESSIONS.get(player.getUuid());
        if (s == null || s.seq != seq || index < 0 || index >= s.offered.size()) return; // stale or forged
        int raw = s.offered.get(index);
        Tree tree = TREES.get(s.tree);
        Node node = tree.nodes.get(s.node);
        s.seq = -1; // consume: a second click with the same seq is ignored
        if (raw == Integer.MIN_VALUE) {
            String next = node.next;
            if (next == null || next.equals("end")) { close(player, s); return; }
            s.history.push(s.node);
            show(player, s, next, true);
            return;
        }
        if (raw < 0) { s.seq = seq; return; } // disabled choice: keep session
        Choice c = node.choices.get(raw);
        Logic.Ctx ctx = new Logic.Ctx(s.npc, null);
        if (!Logic.test(player, c.when, ctx) || !Logic.test(player, c.enabled_when, ctx)) { show(player, s, s.node, false); return; }
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        if (c.once) p.flags.add("chose:" + s.tree + ":" + s.node + ":" + raw);
        boolean hadActions = c.actions != null && !c.actions.isJsonNull();
        Logic.run(player, c.actions, ctx);
        if (SESSIONS.get(player.getUuid()) != s) return;
        if (c.go == null || c.go.equals("end")) { close(player, s); return; }
        if (hadActions) s.history.clear(); else s.history.push(s.node);
        show(player, s, c.go, true);
    }

    private static void back(ServerPlayerEntity player, int seq) {
        Session s = SESSIONS.get(player.getUuid());
        if (s == null || s.seq != seq || s.history.isEmpty()) return;
        show(player, s, s.history.pop(), false);
    }

    private static void close(ServerPlayerEntity player, Session s) {
        SESSIONS.remove(player.getUuid());
        JsonObject out = new JsonObject();
        out.addProperty("close", true);
        Net.send(player, "dialogue", out);
        QuestEngine.sync(player);
    }

    public static void cancel(ServerPlayerEntity player) {
        Session s = SESSIONS.remove(player.getUuid());
        if (s != null) HollowAtlas.LOGGER.debug("dialogue cancelled for {}", player.getName().getString());
    }
}

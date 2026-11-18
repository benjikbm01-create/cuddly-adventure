package dev.hollowatlas.investigation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Case board: evidence entries are classified as observed FACT, TESTIMONY or RUMOR. Hypotheses are the
 * player's GUESSES; choosing one never deletes evidence and can always be changed later, so a wrong
 * theory can't corrupt the save or dead-end the investigation.
 */
public final class CaseService {
    public static final Map<String, CaseDef> CASES = ContentManager.register("cases", CaseDef.class);

    public static final class CaseDef implements ContentManager.Validatable {
        public String id;
        public String title;
        public String summary;
        public JsonElement visible_when;            // condition for the case to appear on the board
        public List<Evidence> evidence = new ArrayList<>();
        public List<Hypothesis> hypotheses = new ArrayList<>();

        @Override public String id() { return id; }

        @Override
        public void validate(String file, List<String> problems) {
            for (Hypothesis h : hypotheses) for (String need : h.needs) {
                if (evidence.stream().noneMatch(e -> e.id.equals(need))) problems.add(file + ": hypothesis " + h.id + " needs unknown evidence " + need);
            }
            for (Evidence e : evidence) {
                if (!List.of("fact", "testimony", "rumor").contains(e.kind)) problems.add(file + ": evidence " + e.id + " has bad kind " + e.kind);
            }
        }
    }

    public static final class Evidence {
        public String id, title, text, kind = "fact", source;
    }

    public static final class Hypothesis {
        public String id, text;
        public List<String> needs = new ArrayList<>();
        public String reaction;     // what the journal notes when this guess is chosen
    }

    private CaseService() {}

    public static void init() {
        Net.on("hypothesis", (player, body) -> {
            String caseId = body.get("case").getAsString();
            String hypId = body.get("id").getAsString();
            CaseDef c = CASES.get(caseId);
            if (c == null) return;
            Hypothesis h = c.hypotheses.stream().filter(x -> x.id.equals(hypId)).findFirst().orElse(null);
            if (h == null) return;
            GameState state = GameState.get(player.getServer());
            PlayerProfile p = state.profile(player.getUuid());
            if (!p.evidence.containsAll(h.needs)) return;   // UI can't offer it; reject forged requests
            p.hypotheses.put(caseId, hypId);
            state.markDirty();
            if (h.reaction != null) player.sendMessage(Text.literal(h.reaction).formatted(Formatting.GRAY, Formatting.ITALIC), false);
            QuestEngine.event(player, "hypothesis", caseId + ":" + hypId);
            QuestEngine.poll(player);
            QuestEngine.sync(player);
        });
    }

    public static Evidence find(String evidenceId) {
        for (CaseDef c : CASES.values()) for (Evidence e : c.evidence) if (e.id.equals(evidenceId)) return e;
        return null;
    }

    public static void addEvidence(ServerPlayerEntity player, String id) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        if (!p.evidence.add(id)) return;      // revisiting a clue never duplicates it
        state.markDirty();
        Evidence e = find(id);
        player.sendMessage(Text.translatable("message.hollowatlas.evidence_noted", e == null ? id : e.title)
                .formatted(Formatting.DARK_AQUA), false);
        player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.0f);
        QuestEngine.event(player, "evidence", id);
        QuestEngine.sync(player);
    }

    public static JsonArray journal(ServerPlayerEntity player) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        JsonArray out = new JsonArray();
        for (CaseDef c : CASES.values()) {
            boolean any = c.evidence.stream().anyMatch(e -> p.evidence.contains(e.id));
            if (c.visible_when != null && !Logic.test(player, c.visible_when, Logic.Ctx.NONE)) continue;
            if (!any && c.visible_when == null) continue;
            JsonObject jc = new JsonObject();
            jc.addProperty("id", c.id);
            jc.addProperty("title", c.title);
            jc.addProperty("summary", c.summary == null ? "" : c.summary);
            JsonArray ev = new JsonArray();
            for (Evidence e : c.evidence) {
                if (!p.evidence.contains(e.id)) continue;
                JsonObject je = new JsonObject();
                je.addProperty("id", e.id);
                je.addProperty("title", e.title);
                je.addProperty("text", e.text);
                je.addProperty("kind", e.kind);
                je.addProperty("source", e.source == null ? "" : e.source);
                ev.add(je);
            }
            jc.add("evidence", ev);
            JsonArray hy = new JsonArray();
            for (Hypothesis h : c.hypotheses) {
                JsonObject jh = new JsonObject();
                jh.addProperty("id", h.id);
                jh.addProperty("text", h.text);
                jh.addProperty("available", p.evidence.containsAll(h.needs));
                jh.addProperty("missing", (int) h.needs.stream().filter(n -> !p.evidence.contains(n)).count());
                hy.add(jh);
            }
            jc.add("hypotheses", hy);
            jc.addProperty("guess", p.hypotheses.getOrDefault(c.id, ""));
            out.add(jc);
        }
        return out;
    }
}

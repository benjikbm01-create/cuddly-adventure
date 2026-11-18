package dev.hollowatlas.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hollowatlas.world.RegionLayout;
import dev.hollowatlas.world.RegionModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks over ALL shipped content (story and developer-test), without starting Minecraft.
 * Failures about story content name only opaque ids (quest/stage/file ids), never story text.
 */
class ContentGraphTest {
    record Doc(String kind, String file, JsonObject json, boolean devtest) {}

    static final List<Doc> DOCS = new ArrayList<>();
    static final Set<String> ANCHORS = new HashSet<>();
    static Path dataRoot;

    @BeforeAll
    static void load() throws IOException, URISyntaxException {
        dataRoot = Path.of(ContentGraphTest.class.getResource("/data/hollowatlas/region/westreach_brindlecross.json").toURI()).getParent().getParent();
        try (Stream<Path> files = Files.walk(dataRoot)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                Path rel = dataRoot.relativize(p);
                String kind = rel.getName(0).toString();
                if (kind.equals("region")) continue;
                DOCS.add(new Doc(kind, rel.toString(), JsonParser.parseString(Files.readString(p)).getAsJsonObject(), rel.toString().contains("devtest")));
            }
        }
        for (String id : List.of("westreach_brindlecross", "devtest_grounds", "boreal_sableforge")) {
            if (RegionLayout.class.getResource("/data/hollowatlas/region/" + id + ".json") == null) continue;
            ANCHORS.addAll(new RegionModel(RegionLayout.loadBuiltin(id)).anchors.keySet());
        }
    }

    static List<Doc> of(String kind) { return DOCS.stream().filter(d -> d.kind.equals(kind)).toList(); }

    static String id(Doc d) { return d.json.get("id").getAsString(); }

    /** Every JSON object anywhere under an element. */
    static void walk(JsonElement e, java.util.function.Consumer<JsonObject> f) {
        if (e == null || e.isJsonNull()) return;
        if (e.isJsonArray()) { for (JsonElement x : e.getAsJsonArray()) walk(x, f); return; }
        if (!e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        f.accept(o);
        for (String k : o.keySet()) walk(o.get(k), f);
    }

    static List<JsonObject> actions(boolean devtest, String type) {
        List<JsonObject> out = new ArrayList<>();
        for (Doc d : DOCS) {
            if (d.devtest != devtest) continue;
            walk(d.json, o -> { if (o.has("type") && o.get("type").isJsonPrimitive() && o.get("type").getAsString().equals(type)) out.add(o); });
        }
        return out;
    }

    static String s(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : null; }

    // ------------------------------------------------------------- quest structure

    @Test
    void everyQuestStageIsReachableAndEveryNextExists() {
        List<String> problems = new ArrayList<>();
        for (Doc q : of("quests")) {
            JsonArray stages = q.json.getAsJsonArray("stages");
            Map<String, JsonObject> byId = new LinkedHashMap<>();
            for (JsonElement e : stages) byId.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
            Set<String> reached = new HashSet<>();
            List<String> todo = new ArrayList<>(List.of(byId.keySet().iterator().next()));
            boolean completes = false;
            while (!todo.isEmpty()) {
                String sid = todo.remove(0);
                if (!reached.add(sid)) continue;
                JsonElement next = byId.get(sid).get("next");
                List<String> targets = new ArrayList<>();
                if (next == null || next.isJsonNull()) targets.add("complete");
                else if (next.isJsonPrimitive()) targets.add(next.getAsString());
                else for (JsonElement b : next.getAsJsonArray()) targets.add(b.getAsJsonObject().get("stage").getAsString());
                for (String t : targets) {
                    if (t.equals("complete")) { completes = true; continue; }
                    if (!byId.containsKey(t)) problems.add(id(q) + "/" + sid + " -> missing stage " + t);
                    else todo.add(t);
                }
            }
            for (String sid : byId.keySet()) if (!reached.contains(sid)) problems.add(id(q) + "/" + sid + " unreachable");
            if (!completes) problems.add(id(q) + " can never complete");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyQuestCanBeStarted() {
        List<String> problems = new ArrayList<>();
        for (Doc q : of("quests")) {
            String qid = id(q);
            if (qid.equals("m01")) continue; // started automatically on a story world's first join
            boolean started = actions(q.devtest, "start_quest").stream().anyMatch(a -> qid.equals(s(a, "quest")));
            if (!started) problems.add(qid + " is never started by any content");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ------------------------------------------------------------- objectives can be produced

    @Test
    void everyObjectiveCanActuallyBeProduced() {
        List<String> problems = new ArrayList<>();
        for (boolean dev : new boolean[]{false, true}) {
            Set<String> talk = new HashSet<>(), inspect = new HashSet<>(), solve = new HashSet<>(), craft = new HashSet<>(),
                    cases = new HashSet<>(), evidence = new HashSet<>(), kinds = new HashSet<>(), items = new HashSet<>(), flags = new HashSet<>(), sites = new HashSet<>();
            for (JsonObject a : actions(dev, "quest_event")) if ("talk".equals(s(a, "event"))) talk.add(s(a, "target"));
            for (Doc n : of("npcs")) if (n.devtest == dev) talk.add(id(n));
            for (Doc d : of("interactions")) if (d.devtest == dev) {
                for (JsonElement e : d.json.getAsJsonArray("interactions")) {
                    JsonObject i = e.getAsJsonObject();
                    inspect.add(s(i, "id"));
                    if (i.has("variants")) for (JsonElement v : i.getAsJsonArray("variants")) inspect.add(s(v.getAsJsonObject(), "id"));
                    if (i.has("evidence")) evidence.add(s(i, "evidence"));
                    if (i.has("variants")) for (JsonElement v : i.getAsJsonArray("variants")) if (v.getAsJsonObject().has("evidence")) evidence.add(s(v.getAsJsonObject(), "evidence"));
                    if (i.has("loot")) for (JsonElement l : i.getAsJsonArray("loot")) items.add(s(l.getAsJsonObject(), "item"));
                }
                if (d.json.has("puzzles")) for (JsonElement p : d.json.getAsJsonArray("puzzles")) {
                    solve.add(s(p.getAsJsonObject(), "id"));
                    if (p.getAsJsonObject().has("solved_flag")) flags.add(s(p.getAsJsonObject(), "solved_flag"));
                }
            }
            for (Doc r : of("recipes")) { craft.add(id(r)); items.add(s(r.json.getAsJsonObject("output"), "item")); }
            for (Doc c : of("cases")) if (c.devtest == dev) cases.add(id(c));
            for (JsonObject a : actions(dev, "evidence")) evidence.add(s(a, "id"));
            for (Doc e : of("encounters")) if (e.devtest == dev) {
                flags.add("hollowatlas:encounter_cleared:" + id(e));
                for (JsonElement sp : e.json.getAsJsonArray("spawns")) kinds.add("hollowatlas:" + s(sp.getAsJsonObject(), "kind"));
            }
            for (JsonObject a : actions(dev, "give_item")) items.add(s(a, "item"));
            for (Doc sh : of("shops")) for (JsonElement o : sh.json.getAsJsonArray("sells")) items.add(s(o.getAsJsonObject(), "item"));
            for (JsonObject a : actions(dev, "set_flag")) flags.add(s(a, "flag"));
            for (JsonObject a : actions(dev, "world_flag")) flags.add(s(a, "flag"));
            for (Doc st : of("sites")) if (st.devtest == dev) for (JsonElement x : st.json.getAsJsonArray("sites")) sites.add(s(x.getAsJsonObject(), "id"));

            for (Doc q : of("quests")) {
                if (q.devtest != dev) continue;
                for (JsonElement se : q.json.getAsJsonArray("stages")) {
                    JsonObject st = se.getAsJsonObject();
                    for (JsonElement oe : st.getAsJsonArray("objectives")) {
                        JsonObject o = oe.getAsJsonObject();
                        String type = s(o, "type"), target = s(o, "target"), where = id(q) + "/" + s(st, "id") + "/" + s(o, "id");
                        boolean ok = switch (type) {
                            case "talk" -> talk.contains(target);
                            case "inspect" -> inspect.contains(target);
                            case "solve" -> solve.contains(target);
                            case "craft" -> craft.contains(target);
                            case "hypothesis" -> cases.contains(target);
                            case "evidence" -> evidence.contains(target);
                            case "kill" -> kinds.contains(target);
                            case "gather" -> items.contains(target);
                            case "reach" -> ANCHORS.contains(target);
                            case "flag" -> o.has("when") || flags.contains(target);
                            case "discover" -> sites.contains(target);
                            default -> true;
                        };
                        if (!ok) problems.add(where + " (" + type + ") has no producer in " + (dev ? "test" : "story") + " content");
                        if (o.has("marker") && !ANCHORS.contains(s(o, "marker"))) problems.add(where + " marker anchor missing");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ------------------------------------------------------------- conditions are satisfiable

    @Test
    void everyConditionCanBecomeTrue() {
        List<String> problems = new ArrayList<>();
        for (boolean dev : new boolean[]{false, true}) {
            Set<String> flags = new HashSet<>(), evidence = new HashSet<>(), choices = new HashSet<>(), abilities = new HashSet<>(), quests = new HashSet<>();
            for (JsonObject a : actions(dev, "set_flag")) flags.add(s(a, "flag"));
            for (JsonObject a : actions(dev, "world_flag")) flags.add(s(a, "flag"));
            for (JsonObject a : actions(dev, "evidence")) evidence.add(s(a, "id"));
            for (JsonObject a : actions(dev, "choice")) choices.add(s(a, "quest") + "|" + s(a, "key") + "|" + s(a, "value"));
            for (JsonObject a : actions(dev, "unlock_ability")) abilities.add(s(a, "ability"));
            for (Doc q : of("quests")) if (q.devtest == dev) quests.add(id(q));
            for (Doc d : of("interactions")) if (d.devtest == dev) {
                walk(d.json, o -> { if (o.has("evidence") && o.get("evidence").isJsonPrimitive()) evidence.add(o.get("evidence").getAsString()); });
                if (d.json.has("puzzles")) for (JsonElement p : d.json.getAsJsonArray("puzzles")) flags.add(s(p.getAsJsonObject(), "solved_flag"));
            }
            for (Doc e : of("encounters")) if (e.devtest == dev) flags.add("hollowatlas:encounter_cleared:" + id(e));
            // flags the engine itself sets
            flags.add("visited_brindlecross");
            for (String q : quests) flags.add("quest_done:" + q);

            for (Doc d : DOCS) {
                if (d.devtest != dev) continue;
                walk(d.json, o -> {
                    check(o, "flag", flags, d, problems);
                    check(o, "not_flag", flags, d, problems);
                    check(o, "world_flag", flags, d, problems);
                    check(o, "not_world_flag", flags, d, problems);
                    check(o, "ability", abilities, d, problems);
                    for (String fk : List.of("flags", "not_flags"))
                        if (o.has(fk) && o.get(fk).isJsonArray() && !o.has("type"))
                            for (JsonElement e : o.getAsJsonArray(fk)) if (e.isJsonPrimitive() && !flags.contains(e.getAsString()))
                                problems.add(d.file + ": condition " + fk + " can never hold (" + (d.devtest ? e.getAsString() : "id #" + Math.abs(e.getAsString().hashCode() % 997)) + ")");
                    for (String qk : List.of("quest_active", "quest_done", "quest_not_started")) check(o, qk, quests, d, problems);
                    if (o.has("evidence") && o.get("evidence").isJsonArray())
                        for (JsonElement e : o.getAsJsonArray("evidence")) if (e.isJsonPrimitive() && !evidence.contains(e.getAsString())) problems.add(d.file + ": evidence condition can never hold (" + e.getAsString() + ")");
                    if (o.has("choice") && o.get("choice").isJsonObject()) {
                        JsonObject c = o.getAsJsonObject("choice");
                        if (!choices.contains(s(c, "quest") + "|" + s(c, "key") + "|" + s(c, "value"))) problems.add(d.file + ": branch condition never set (" + s(c, "quest") + "." + s(c, "key") + ")");
                    }
                });
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void check(JsonObject o, String key, Set<String> known, Doc d, List<String> problems) {
        if (!o.has(key) || o.has("type")) return;          // actions also use "flag"; only conditions are checked here
        JsonElement v = o.get(key);
        if (!v.isJsonPrimitive()) return;
        String x = v.getAsString();
        if (x.startsWith("background_") || x.startsWith("recipe_") || x.startsWith("report_choice_")) {
            boolean set = DOCS.stream().filter(dd -> dd.devtest == d.devtest).anyMatch(dd -> dd.json.toString().contains("\"" + x + "\""));
            if (!set) problems.add(d.file + ": condition " + key + " can never hold");
            return;
        }
        if (!known.contains(x)) problems.add(d.file + ": condition " + key + " can never hold (" + (d.devtest ? x : "id #" + Math.abs(x.hashCode() % 997)) + ")");
    }

    // ------------------------------------------------------------- references

    @Test
    void dialogueAndActionReferencesExist() {
        List<String> problems = new ArrayList<>();
        Set<String> trees = new HashSet<>(), shops = new HashSet<>(), quests = new HashSet<>(), npcs = new HashSet<>(), abilities = new HashSet<>();
        Map<String, Set<String>> variants = new HashMap<>();
        for (Doc d : of("dialogue")) trees.add(id(d));
        for (Doc d : of("shops")) shops.add(id(d));
        for (Doc d : of("quests")) quests.add(id(d));
        for (Doc d : of("abilities")) abilities.add(id(d));
        for (Doc d : of("npcs")) {
            npcs.add(id(d));
            variants.put(id(d), d.json.has("variants") ? d.json.getAsJsonObject("variants").keySet() : Set.of());
        }
        for (Doc d : of("dialogue")) {
            JsonObject nodes = d.json.getAsJsonObject("nodes");
            for (JsonElement e : d.json.getAsJsonArray("entries")) if (!nodes.has(s(e.getAsJsonObject(), "node"))) problems.add(d.file + " entry -> missing node");
            for (String n : nodes.keySet()) {
                JsonObject node = nodes.getAsJsonObject(n);
                if (node.has("next") && !"end".equals(s(node, "next")) && !nodes.has(s(node, "next"))) problems.add(d.file + "#" + n + " next missing");
                if (node.has("choices")) for (JsonElement c : node.getAsJsonArray("choices")) {
                    String go = s(c.getAsJsonObject(), "go");
                    if (go != null && !go.equals("end") && !nodes.has(go)) problems.add(d.file + "#" + n + " choice -> missing node");
                }
            }
        }
        for (Doc d : DOCS) walk(d.json, o -> {
            String t = o.has("type") && o.get("type").isJsonPrimitive() ? o.get("type").getAsString() : "";
            switch (t) {
                case "start_quest" -> { if (!quests.contains(s(o, "quest"))) problems.add(d.file + ": start_quest unknown"); }
                case "open_shop" -> { if (!shops.contains(s(o, "shop"))) problems.add(d.file + ": open_shop unknown"); }
                case "open_dialogue" -> { if (!trees.contains(s(o, "tree"))) problems.add(d.file + ": open_dialogue unknown"); }
                case "unlock_ability" -> { if (!abilities.contains(s(o, "ability"))) problems.add(d.file + ": unlock_ability unknown"); }
                case "set_variant" -> {
                    if (!npcs.contains(s(o, "npc")) || !variants.getOrDefault(s(o, "npc"), Set.of()).contains(s(o, "variant"))) problems.add(d.file + ": set_variant unknown");
                }
                case "checkpoint" -> { if (!ANCHORS.contains(s(o, "anchor"))) problems.add(d.file + ": checkpoint anchor unknown"); }
                default -> { }
            }
        });
        for (Doc n : of("npcs")) walk(n.json, o -> {
            if (o.has("anchor") && !ANCHORS.contains(s(o, "anchor"))) problems.add(n.file + ": schedule anchor unknown");
            if (o.has("route")) for (JsonElement r : o.getAsJsonArray("route")) if (!ANCHORS.contains(r.getAsString())) problems.add(n.file + ": route anchor unknown");
            if (o.has("dialogue") && !trees.contains(s(o, "dialogue"))) problems.add(n.file + ": dialogue tree unknown");
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ------------------------------------------------------------- story / test isolation

    static final Set<String> SHARED_KINDS = Set.of("abilities", "recipes");

    @Test
    void testContentAndStoryContentNeverReferenceEachOther() {
        Set<String> storyIds = new HashSet<>(), testIds = new HashSet<>();
        for (Doc d : DOCS) {
            if (!d.json.has("id") || SHARED_KINDS.contains(d.kind)) continue;   // abilities/recipes are shared game systems
            (d.devtest ? testIds : storyIds).add(id(d));
        }
        List<String> problems = new ArrayList<>();
        for (Doc d : DOCS) {
            String text = d.json.toString();
            if (d.devtest) {
                for (String sid : storyIds) if (text.contains("\"" + sid + "\"")) problems.add(d.file + " references a story id");
                walk(d.json, o -> {
                    for (String k : List.of("anchor", "marker")) if (o.has(k) && o.get(k).isJsonPrimitive() && !o.get(k).getAsString().startsWith("dt_")) problems.add(d.file + " uses a non-test location");
                });
            } else {
                for (String tid : testIds) if (text.contains("\"" + tid + "\"")) problems.add(d.file + " references test content " + tid);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ------------------------------------------------------------- spoiler guard

    /** Words that are fine to share between story and test text. */
    static final Set<String> COMMON = new HashSet<>(List.of("test", "the", "and", "road", "stall", "hall", "store", "house", "farm", "pond", "inn", "east", "west",
            "north", "south", "village", "watch", "smith", "mill", "relay", "bridge", "case", "board", "ribbon", "rain", "lantern", "notice",
            "general", "goods", "daughters", "cottage", "hut", "tower", "barn", "bakehouse", "smithy", "courier", "herbalist", "farmer", "keeper",
            "orchard", "watchtower", "meadows", "crossing", "closed", "overlook", "lone", "oak", "pool", "heron", "track", "cart", "hollow",
            "shrine", "old", "milestone", "stew", "hearth", "bun", "honey", "tonic", "trail", "yarrow", "feverfew", "coin", "locket", "silver",
            "hatchet", "quarterstaff", "shortsword", "traveler", "charm", "atlas", "proving", "grounds", "developer", "world", "remedy", "innkeeper",
            "blacksmith", "apprentice", "shopkeeper", "baker", "weaver", "retired", "miller", "bridgewarden", "orchard keeper", "village watch",
            "relay keeper", "store", "items", "test", "rest", "your", "room", "mountain", "clinic"));

    static Set<String> storyNames() {
        Set<String> names = new HashSet<>();
        java.util.function.Consumer<String> add = t -> {
            if (t == null) return;
            for (String w : t.split("[^A-Za-z]+")) {
                if (w.length() >= 4 && Character.isUpperCase(w.charAt(0)) && !COMMON.contains(w.toLowerCase())) names.add(w);
            }
        };
        for (Doc d : DOCS) {
            if (d.devtest) continue;
            switch (d.kind) {
                case "npcs" -> add.accept(s(d.json, "name"));
                case "sites" -> { for (JsonElement x : d.json.getAsJsonArray("sites")) add.accept(s(x.getAsJsonObject(), "name")); }
                default -> { }
            }
        }
        RegionLayout story = RegionLayout.loadBuiltin("westreach_brindlecross");
        add.accept(story.name);
        for (RegionLayout.Building b : story.buildings) add.accept(b.label);
        // A proper noun is never written in lower case. Drop ordinary words that story text also uses in lower case.
        StringBuilder all = new StringBuilder();
        for (Doc d : DOCS) if (!d.devtest) all.append(d.json).append(' ');
        String text = all.toString();
        names.removeIf(n -> java.util.regex.Pattern.compile("\\b" + n.toLowerCase() + "\\b").matcher(text).find());
        return names;
    }

    static List<String> leaks(String where, String text, Set<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (java.util.regex.Pattern.compile("\\b" + n + "\\b").matcher(text).find()) {
                // never print the story word itself: report a masked token
                out.add(where + " contains story name " + n.charAt(0) + "*".repeat(n.length() - 1));
            }
        }
        return out;
    }

    @Test
    void spoilerGuardTestContentHasNoStoryNames() {
        Set<String> names = storyNames();
        assertTrue(names.size() > 10, "story name list unexpectedly small");
        List<String> problems = new ArrayList<>();
        for (Doc d : DOCS) if (d.devtest) problems.addAll(leaks(d.file, d.json.toString(), names));
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void spoilerGuardPublicDocsHaveNoStoryNames() throws IOException {
        Set<String> names = storyNames();
        Path repo = dataRoot;
        while (repo != null && !Files.exists(repo.resolve("settings.gradle"))) repo = repo.getParent();
        if (repo == null) return;
        List<String> problems = new ArrayList<>();
        List<Path> publicDocs = new ArrayList<>(List.of(repo.resolve("README.md"), repo.resolve("QUICKSTART.txt"),
                repo.resolve("tools/devtest_src/devtest_content.py")));
        try (Stream<Path> docs = Files.list(repo.resolve("docs"))) { docs.filter(p -> p.toString().endsWith(".md")).forEach(publicDocs::add); }
        for (Path p : publicDocs) if (Files.exists(p)) problems.addAll(leaks(repo.relativize(p).toString(), Files.readString(p), names));
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}

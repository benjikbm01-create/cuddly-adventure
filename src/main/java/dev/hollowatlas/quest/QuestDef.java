package dev.hollowatlas.quest;

import com.google.gson.JsonElement;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.logic.Logic;

import java.util.ArrayList;
import java.util.List;

/** Authored quest: {@code data/hollowatlas/quests/<id>.json}. Text fields are English fallbacks for lang keys. */
public final class QuestDef implements ContentManager.Validatable {
    public String id;
    public String title;
    public String category = "side";        // main | side | sandbox
    public String giver;
    public String summary;
    public String region = "westreach";
    public JsonElement prereq;               // condition to be offered/started
    public boolean abandonable = false;
    public List<Stage> stages = new ArrayList<>();
    public JsonElement on_start;
    public JsonElement rewards;              // actions run once on completion
    public String completion_text;

    public static final class Stage {
        public String id;
        public String text;                  // journal description
        public String hint;                  // optional guidance (hidden if guidance disabled)
        public List<Objective> objectives = new ArrayList<>();
        public JsonElement on_complete;
        /** Next stage: either a stage id, "complete", or branch list [{"when":cond,"stage":id}]. */
        public JsonElement next;
    }

    public static final class Objective {
        public String id;
        public String type;                  // talk | inspect | reach | gather | kill | solve | flag | hypothesis | event
        public String target;                // event target / anchor / item id / flag
        public int count = 1;
        public double radius = 4;
        public boolean optional = false;
        public String text;
        public String marker;                // optional anchor for guidance markers
        public JsonElement when;             // for type=flag: arbitrary condition
    }

    public Stage stage(String sid) {
        for (Stage s : stages) if (s.id.equals(sid)) return s;
        return null;
    }

    @Override public String id() { return id; }

    @Override
    public void validate(String file, List<String> problems) {
        if (title == null) problems.add(file + ": quest needs a title");
        if (stages.isEmpty()) problems.add(file + ": quest has no stages");
        for (Stage s : stages) {
            if (s.id == null) problems.add(file + ": stage without id");
            for (Objective o : s.objectives) {
                if (o.id == null || o.type == null) problems.add(file + ": objective missing id/type in stage " + s.id);
                if (o.type != null && !List.of("talk", "inspect", "reach", "gather", "kill", "solve", "flag", "hypothesis", "event").contains(o.type))
                    problems.add(file + ": unknown objective type " + o.type);
            }
            Logic.validateActions(file + "#" + s.id, s.on_complete, problems);
        }
        Logic.validateActions(file + "#rewards", rewards, problems);
    }
}

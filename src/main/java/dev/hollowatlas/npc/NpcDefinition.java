package dev.hollowatlas.npc;

import dev.hollowatlas.content.ContentManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Authored resident definition loaded from {@code data/hollowatlas/npcs/<id>.json}. */
public final class NpcDefinition implements ContentManager.Validatable {
    public String id;
    public String name;
    public String title;            // occupation shown in UI
    public String skin;
    public String home;             // building id
    public String work;             // building id
    public List<String> traits = new ArrayList<>();
    public String dialogue;         // dialogue tree id
    public int tier = 1;            // 1 = authored detail, 2 = simplified resident
    public double speed = 0.6;
    public String held = "";        // item shown in hand while working
    public List<ScheduleEntry> schedule = new ArrayList<>();
    public List<ScheduleEntry> rain = new ArrayList<>();
    /** variant id -> replacement schedule (set by world changes). */
    public Map<String, List<ScheduleEntry>> variants = new HashMap<>();
    /** context -> lines; contexts: greet, morning, day, evening, night, rain, work, sleep */
    public Map<String, List<String>> barks = new HashMap<>();
    /** npc id -> relation (friend, spouse, rival, mentor, apprentice) */
    public Map<String, String> relations = new HashMap<>();
    /** Fallback anchor where quest-critical interaction is always possible. */
    public String fallback_anchor;

    public static final class ScheduleEntry {
        public double from, to;      // hours, 0-24; wraps past midnight if from > to
        public String activity = "idle"; // sleep, work, patrol, wander, social, eat, idle
        public String style = "";       // work animation: hammer, tend, serve, vend, read, bake, fish, pack, guard, sweep
        public String anchor;
        public List<String> route = new ArrayList<>();
        public int radius = 0;
        public boolean outdoors = false;

        public boolean covers(double h) {
            return from <= to ? (h >= from && h < to) : (h >= from || h < to);
        }
    }

    @Override public String id() { return id; }

    @Override
    public void validate(String file, List<String> problems) {
        if (name == null || name.isBlank()) problems.add(file + ": NPC needs a name");
        if (schedule.isEmpty()) problems.add(file + ": NPC '" + id + "' has no schedule");
        double covered = 0;
        for (int h = 0; h < 24; h++) {
            final double hour = h + 0.5;
            if (schedule.stream().anyMatch(e -> e.covers(hour))) covered++;
        }
        if (covered < 24) problems.add(file + ": schedule for '" + id + "' leaves " + (int) (24 - covered) + " hour(s) uncovered");
    }
}

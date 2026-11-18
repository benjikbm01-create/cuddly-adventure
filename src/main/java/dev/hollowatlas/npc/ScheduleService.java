package dev.hollowatlas.npc;

import net.minecraft.world.World;

import java.util.List;

/** Pure schedule resolution: which entry applies at a given hour, weather and routine variant. */
public final class ScheduleService {
    private ScheduleService() {}

    /** Hour of day in [0,24): Minecraft time 0 is 06:00. */
    public static double hour(World world) {
        long t = world.getTimeOfDay() % 24000L;
        return ((t / 1000.0) + 6.0) % 24.0;
    }

    public static NpcDefinition.ScheduleEntry resolve(NpcDefinition def, double hour, boolean raining, String variant) {
        List<NpcDefinition.ScheduleEntry> base = def.schedule;
        if (variant != null && !variant.isEmpty() && def.variants.containsKey(variant)) base = def.variants.get(variant);
        NpcDefinition.ScheduleEntry hit = find(base, hour);
        if (raining && hit != null && hit.outdoors) {
            NpcDefinition.ScheduleEntry wet = find(def.rain, hour);
            if (wet != null) return wet;
        }
        if (hit == null && !def.schedule.isEmpty()) hit = def.schedule.get(0);
        return hit;
    }

    private static NpcDefinition.ScheduleEntry find(List<NpcDefinition.ScheduleEntry> list, double hour) {
        for (NpcDefinition.ScheduleEntry e : list) if (e.covers(hour)) return e;
        return null;
    }

    /** For patrols: which route waypoint the resident should be heading to (used for abstract simulation). */
    public static String anchorFor(NpcDefinition.ScheduleEntry e, double hour) {
        if (e.route != null && !e.route.isEmpty()) {
            int idx = (int) Math.floor(hour * 6) % e.route.size(); // a new waypoint every 10 game minutes
            return e.route.get(Math.max(0, idx));
        }
        return e.anchor;
    }
}

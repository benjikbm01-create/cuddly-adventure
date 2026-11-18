package dev.hollowatlas.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authored source description of one map region (the "map source").
 * All coordinates inside are LOCAL to the region origin; {@link #origin} maps them into world space
 * so that the region can be placed into the final 8,000 x 8,000 world without editing content.
 */
public final class RegionLayout {
    public String id;
    public String name;
    /** World X/Z of local (0,0). */
    public int[] origin = {0, 0};
    /** Region spans [-halfSize, halfSize] on both local axes. */
    public int half_size = 375;
    public int base_y = 66;
    public int water_y = 62;
    public long seed = 1;
    public int border_size = 740;
    /** World flag that must be set before players may enter this region (null/empty = always open). */
    public String open_flag;
    /** Anchor where new players arrive (first region of each mode). */
    public String arrival_anchor = "arrival";
    /** "story" (real game) or "devtest" (noncanonical developer testing world). */
    public String mode = "story";
    /** Snow cover on terrain at or above this Y (0 = none). */
    public int snow_line = 0;
    /** Default building pad style for this region's terrain surface: "grass" or "mountain". */
    public String surface_style = "grass";

    public List<Hill> hills = new ArrayList<>();
    public List<Flat> flats = new ArrayList<>();
    public EdgeRidge edge_ridge = new EdgeRidge();
    public List<River> rivers = new ArrayList<>();
    public List<Pond> ponds = new ArrayList<>();
    public List<Road> roads = new ArrayList<>();
    public List<Woods> woods = new ArrayList<>();
    public List<Building> buildings = new ArrayList<>();
    public List<Cave> caves = new ArrayList<>();
    public List<Field> fields = new ArrayList<>();
    public List<Feature> features = new ArrayList<>();
    /** Named ground anchors: [x, yOffset, z]. yOffset is added to terrain height + 1. */
    public Map<String, int[]> anchors = new LinkedHashMap<>();

    public static final class Hill { public String id; public int x, z, r; public double h; }

    public static final class Flat {
        public String id; public int x, z, r, blend;
        /** Target height; 0 means "use the unflattened terrain height at the center". */
        public int y;
    }

    public static final class EdgeRidge {
        public int width = 55;
        public double height = 26;
        public List<Gap> gaps = new ArrayList<>();
        public static final class Gap { public String edge; public int at; public int half; }
    }

    public static final class River {
        public String id;
        public double width = 8;
        public double depth = 3;
        public int water_y = 0; // 0 => region water_y
        public boolean gorge = false;
        public List<int[]> points = new ArrayList<>();
    }

    public static final class Pond { public String id; public int x, z, r; public int depth = 3; }

    public static final class Road {
        public String id;
        public double width = 4;
        public String surface = "path"; // path | lane | cobble
        public boolean broken_bridge = false;
        public List<int[]> points = new ArrayList<>();
    }

    public static final class Woods {
        public String id; public int x, z, r;
        public double density = 0.5;
        public List<String> mix = new ArrayList<>();
    }

    public static final class Building {
        public String id;
        public String type;          // house, inn, store, smithy, hall, bakery, farmhouse, barn, mill, relay, tower, stall
        public int x, z;             // center (local)
        public int w, d;             // canonical width (along front) and depth
        public String facing = "south";
        public int floors = 1;
        public String wall = "minecraft:oak_planks";
        public String frame = "minecraft:stripped_spruce_log";
        public String floor = "minecraft:spruce_planks";
        public String roof = "spruce";       // wood/stone set for stairs+slabs
        public String foundation = "minecraft:cobblestone";
        public int beds = 1;
        public String label;          // display name of the place
    }

    public static final class Cave {
        public String id;
        /** Chain of carve spheres [x, y, z, radius] in local coords. */
        public List<int[]> nodes = new ArrayList<>();
    }

    public static final class Field {
        public String id; public String crop; // wheat, carrots, potatoes, beetroots, orchard
        public int x1, z1, x2, z2;
    }

    /** Small authored set-pieces (wells, stalls, overlooks, shrines, wagons...). */
    public static final class Feature {
        public String id; public String type;
        public int x, z;
        /** 0 = blocks are placed relative to local ground; otherwise y=0 of the plan is this absolute Y. */
        public int y = 0;
        public String facing = "south";
    }

    public static RegionLayout parse(Reader reader) {
        Gson gson = new GsonBuilder().create();
        RegionLayout layout = gson.fromJson(reader, RegionLayout.class);
        if (layout == null || layout.id == null) {
            throw new IllegalArgumentException("Region layout has no id");
        }
        return layout;
    }

    public static RegionLayout loadBuiltin(String regionId) {
        String path = "/data/hollowatlas/region/" + regionId + ".json";
        try (InputStream in = RegionLayout.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing region layout resource " + path);
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    public Building building(String id) {
        for (Building b : buildings) if (b.id.equals(id)) return b;
        return null;
    }
}

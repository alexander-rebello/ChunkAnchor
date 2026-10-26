package io.github.alexanderrebello.chunkanchor.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parsed, validated view of config.yml.
 */
public final class PluginConfig {

    public enum DrainMode { ALWAYS, WHEN_UNATTENDED }

    public record RecipeSpec(List<String> shape, Map<Character, String> ingredients, int amount) {
    }

    public record FuelConfig(Material material, String name, List<String> lore, boolean glint,
                             double charge, RecipeSpec recipe) {
    }

    public record TierConfig(String id, String name, List<String> lore, Material block, int radius,
                             double capacity, double drainPerMinute, boolean glint, RecipeSpec recipe) {

        /** Side length of the loaded square in chunks, e.g. 3 for radius 1. */
        public int side() {
            return radius * 2 + 1;
        }

        public String areaText() {
            return side() + "x" + side();
        }
    }

    private final int tickIntervalSeconds;
    private final DrainMode drainMode;
    private final double unattendedDistance;
    private final int maxAnchorsPerPlayer;
    private final Set<String> disabledWorlds;
    private final boolean onlyOwnerCanAccess;
    private final boolean onlyOwnerCanBreak;
    private final boolean explosionProof;
    private final boolean particles;
    private final boolean notifyOwnerWhenEmpty;
    private final boolean autoDiscoverRecipes;
    private final FuelConfig fuel;
    private final Map<String, TierConfig> tiers;
    private final FileConfiguration raw;

    private PluginConfig(FileConfiguration c, Logger log) {
        this.raw = c;
        ConfigurationSection s = section(c, "settings");
        this.tickIntervalSeconds = Math.max(1, s.getInt("tick-interval-seconds", 5));
        String mode = s.getString("drain-mode", "always").trim().toUpperCase(Locale.ROOT).replace('-', '_');
        DrainMode parsedMode;
        try {
            parsedMode = DrainMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            log.warning("Unknown settings.drain-mode '" + mode + "', using 'always'.");
            parsedMode = DrainMode.ALWAYS;
        }
        this.drainMode = parsedMode;
        this.unattendedDistance = Math.max(0, s.getDouble("unattended-distance", 128));
        this.maxAnchorsPerPlayer = Math.max(0, s.getInt("max-anchors-per-player", 3));
        Set<String> worlds = new HashSet<>();
        for (String w : s.getStringList("disabled-worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        this.disabledWorlds = Collections.unmodifiableSet(worlds);
        this.onlyOwnerCanAccess = s.getBoolean("only-owner-can-access", false);
        this.onlyOwnerCanBreak = s.getBoolean("only-owner-can-break", true);
        this.explosionProof = s.getBoolean("explosion-proof", true);
        this.particles = s.getBoolean("particles", true);
        this.notifyOwnerWhenEmpty = s.getBoolean("notify-owner-when-empty", true);
        this.autoDiscoverRecipes = s.getBoolean("auto-discover-recipes", true);

        ConfigurationSection f = section(c, "fuel");
        double fuelCharge = f.getDouble("charge", 60);
        if (fuelCharge <= 0) {
            log.warning("fuel.charge must be positive, using 60.");
            fuelCharge = 60;
        }
        this.fuel = new FuelConfig(
                material(f.getString("material"), Material.AMETHYST_SHARD, "fuel.material", log),
                f.getString("name", "Anchor Charge"),
                f.getStringList("lore"),
                f.getBoolean("glint", true),
                fuelCharge,
                recipe(f.getConfigurationSection("recipe")));

        Map<String, TierConfig> parsed = new LinkedHashMap<>();
        ConfigurationSection t = section(c, "tiers");
        for (String id : t.getKeys(false)) {
            ConfigurationSection ts = t.getConfigurationSection(id);
            if (ts == null) {
                continue;
            }
            Material block = material(ts.getString("block"), Material.LODESTONE, "tiers." + id + ".block", log);
            if (!block.isBlock()) {
                log.warning("tiers." + id + ".block '" + block + "' is not a block, using LODESTONE.");
                block = Material.LODESTONE;
            }
            double capacity = ts.getDouble("capacity", 1440);
            if (capacity < fuelCharge) {
                log.warning("tiers." + id + ".capacity is smaller than one fuel item; raising it to " + fuelCharge + ".");
                capacity = fuelCharge;
            }
            parsed.put(id, new TierConfig(
                    id,
                    ts.getString("name", id),
                    ts.getStringList("lore"),
                    block,
                    Math.max(0, Math.min(16, ts.getInt("radius", 0))),
                    capacity,
                    Math.max(0, ts.getDouble("drain-per-minute", 1.0)),
                    ts.getBoolean("glint", true),
                    recipe(ts.getConfigurationSection("recipe"))));
        }
        if (parsed.isEmpty()) {
            log.severe("No anchor tiers are configured - anchors can't be crafted.");
        }
        this.tiers = Collections.unmodifiableMap(parsed);
    }

    public static PluginConfig load(FileConfiguration c, Logger log) {
        return new PluginConfig(c, log);
    }

    private static ConfigurationSection section(ConfigurationSection c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        return s != null ? s : c.createSection(path);
    }

    private static Material material(String name, Material fallback, String path, Logger log) {
        if (name == null) {
            return fallback;
        }
        Material m = Material.matchMaterial(name);
        if (m == null || m.isAir()) {
            log.warning(path + ": unknown material '" + name + "', using " + fallback + ".");
            return fallback;
        }
        return m;
    }

    private static RecipeSpec recipe(ConfigurationSection r) {
        if (r == null) {
            return null;
        }
        List<String> shape = new ArrayList<>(r.getStringList("shape"));
        Map<Character, String> ingredients = new LinkedHashMap<>();
        ConfigurationSection ing = r.getConfigurationSection("ingredients");
        if (ing != null) {
            for (String key : ing.getKeys(false)) {
                if (key.length() == 1 && ing.getString(key) != null) {
                    ingredients.put(key.charAt(0), ing.getString(key));
                }
            }
        }
        return new RecipeSpec(shape, ingredients, Math.max(1, Math.min(64, r.getInt("amount", 1))));
    }

    public int tickIntervalSeconds() { return tickIntervalSeconds; }
    public DrainMode drainMode() { return drainMode; }
    public double unattendedDistance() { return unattendedDistance; }
    public int maxAnchorsPerPlayer() { return maxAnchorsPerPlayer; }
    public boolean isWorldDisabled(String world) { return disabledWorlds.contains(world.toLowerCase(Locale.ROOT)); }
    public boolean onlyOwnerCanAccess() { return onlyOwnerCanAccess; }
    public boolean onlyOwnerCanBreak() { return onlyOwnerCanBreak; }
    public boolean explosionProof() { return explosionProof; }
    public boolean particles() { return particles; }
    public boolean notifyOwnerWhenEmpty() { return notifyOwnerWhenEmpty; }
    public boolean autoDiscoverRecipes() { return autoDiscoverRecipes; }
    public FuelConfig fuel() { return fuel; }
    public Map<String, TierConfig> tiers() { return tiers; }
    public TierConfig tier(String id) { return id == null ? null : tiers.get(id); }
    public FileConfiguration raw() { return raw; }
}

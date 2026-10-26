package io.github.alexanderrebello.chunkanchor.anchor;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import io.github.alexanderrebello.chunkanchor.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry of placed anchors: persistence, the drain loop and keeping chunk tickets in sync.
 */
public final class AnchorManager {

    private final ChunkAnchorPlugin plugin;
    private final ChunkTicketManager tickets;
    private final File dataFile;
    private final Map<BlockKey, Anchor> anchors = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean dirty;

    public AnchorManager(ChunkAnchorPlugin plugin, ChunkTicketManager tickets) {
        this.plugin = plugin;
        this.tickets = tickets;
        this.dataFile = new File(plugin.getDataFolder(), "anchors.yml");
    }

    // ---- registry ----------------------------------------------------------------------------

    public Anchor get(Block block) {
        return anchors.get(BlockKey.of(block));
    }

    public Anchor get(BlockKey key) {
        return anchors.get(key);
    }

    public Collection<Anchor> all() {
        return Collections.unmodifiableCollection(anchors.values());
    }

    public List<Anchor> ownedBy(UUID owner) {
        List<Anchor> out = new ArrayList<>();
        for (Anchor anchor : anchors.values()) {
            if (anchor.owner().equals(owner)) {
                out.add(anchor);
            }
        }
        return out;
    }

    public Anchor create(Block block, Player owner, String tierId) {
        Anchor anchor = new Anchor(BlockKey.of(block), owner.getUniqueId(), owner.getName(), tierId, 0, 0, true);
        anchors.put(anchor.key(), anchor);
        markDirty();
        return anchor;
    }

    public void remove(Anchor anchor) {
        anchors.remove(anchor.key());
        plugin.menus().close(anchor);
        changed();
    }

    /** Call after anything that may change whether an anchor is running. */
    public void changed() {
        markDirty();
        tickets.refresh(anchors.values());
    }

    public void markDirty() {
        dirty = true;
    }

    // ---- drain loop --------------------------------------------------------------------------

    public void start() {
        stop();
        long period = plugin.settings().tickIntervalSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        PluginConfig cfg = plugin.settings();
        double fuelValue = cfg.fuel().charge();
        double minutes = cfg.tickIntervalSeconds() / 60.0;
        boolean stateChanged = false;

        for (Anchor anchor : new ArrayList<>(anchors.values())) {
            World world = anchor.key().bukkitWorld();
            TierConfig tier = cfg.tier(anchor.tierId());
            if (world == null || tier == null || cfg.isWorldDisabled(world.getName())) {
                continue;
            }
            // Remove anchors whose block was replaced by other means (e.g. WorldEdit)
            if (world.isChunkLoaded(anchor.key().chunkX(), anchor.key().chunkZ())
                    && world.getBlockAt(anchor.key().x(), anchor.key().y(), anchor.key().z()).getType() != tier.block()) {
                plugin.getLogger().info("Anchor at " + anchor.key().describe() + " no longer exists, removing it.");
                anchors.remove(anchor.key());
                plugin.menus().close(anchor);
                stateChanged = true;
                dirty = true;
                continue;
            }
            if (!anchor.isRunning()) {
                continue;
            }
            Location center = anchor.key().center(world);
            if (cfg.drainMode() == PluginConfig.DrainMode.ALWAYS || !playerNearby(world, center, cfg.unattendedDistance())) {
                anchor.consume(tier.drainPerMinute() * minutes, fuelValue);
                dirty = true;
            }
            if (!anchor.hasCharge()) {
                stateChanged = true;
                onRanEmpty(anchor, tier, world, center);
            } else if (cfg.particles() && world.isChunkLoaded(anchor.key().chunkX(), anchor.key().chunkZ())) {
                world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.6, 0), 6, 0.25, 0.25, 0.25, 0.02);
            }
        }

        if (stateChanged) {
            tickets.refresh(anchors.values());
        }
        plugin.menus().refreshAll();
        if (dirty) {
            save();
        }
    }

    private void onRanEmpty(Anchor anchor, TierConfig tier, World world, Location center) {
        if (world.isChunkLoaded(anchor.key().chunkX(), anchor.key().chunkZ())) {
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1f);
        }
        if (plugin.settings().notifyOwnerWhenEmpty()) {
            Player owner = plugin.getServer().getPlayer(anchor.owner());
            if (owner != null) {
                plugin.text().send(owner, "ran-empty",
                        Text.c("anchor", Text.parse(tier.name())),
                        Text.p("location", anchor.key().describe()));
            }
        }
    }

    private static boolean playerNearby(World world, Location center, double distance) {
        double max = distance * distance;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && player.getLocation().distanceSquared(center) <= max) {
                return true;
            }
        }
        return false;
    }

    // ---- persistence -------------------------------------------------------------------------

    public void load() {
        anchors.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("anchors");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            BlockKey key = BlockKey.parse(id);
            ConfigurationSection s = section.getConfigurationSection(id);
            if (key == null || s == null) {
                plugin.getLogger().warning("Skipping invalid anchor entry '" + id + "' in anchors.yml.");
                continue;
            }
            UUID owner;
            try {
                owner = UUID.fromString(s.getString("owner", ""));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping anchor '" + id + "' with invalid owner.");
                continue;
            }
            String tier = s.getString("tier");
            if (plugin.settings().tier(tier) == null) {
                plugin.getLogger().warning("Anchor at " + key.describe() + " uses unknown tier '" + tier + "'; it stays inactive.");
            }
            anchors.put(key, new Anchor(key, owner, s.getString("owner-name", "?"), tier,
                    s.getInt("stored-fuel", 0), s.getDouble("burn-remaining", 0), s.getBoolean("enabled", true)));
        }
        plugin.getLogger().info("Loaded " + anchors.size() + " chunk anchor(s).");
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection section = data.createSection("anchors");
        for (Anchor anchor : anchors.values()) {
            ConfigurationSection s = section.createSection(anchor.key().serialize());
            s.set("owner", anchor.owner().toString());
            s.set("owner-name", anchor.ownerName());
            s.set("tier", anchor.tierId());
            s.set("stored-fuel", anchor.storedFuel());
            s.set("burn-remaining", anchor.burnRemaining());
            s.set("enabled", anchor.enabled());
        }
        try {
            data.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save anchors.yml", e);
        }
    }
}

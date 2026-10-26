package io.github.alexanderrebello.chunkanchor.anchor;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import org.bukkit.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps Paper plugin chunk tickets in sync with the running anchors.
 * <p>
 * Paper allows one ticket per plugin per chunk, so overlapping anchors share tickets. The full
 * set of wanted chunks is recomputed and diffed on every refresh, which means a chunk stays
 * loaded as long as at least one running anchor covers it. Chunks are loaded asynchronously
 * through Paper's chunk system before the ticket is added, so starting an anchor never blocks
 * the main thread on chunk generation.
 */
public final class ChunkTicketManager {

    private final ChunkAnchorPlugin plugin;
    /** World -> chunk keys this plugin currently holds a ticket for (or is loading one for). */
    private final Map<UUID, Set<Long>> ticketed = new HashMap<>();

    public ChunkTicketManager(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh(Collection<Anchor> anchors) {
        Map<UUID, Set<Long>> wanted = new HashMap<>();
        for (Anchor anchor : anchors) {
            if (!anchor.isRunning()) {
                continue;
            }
            TierConfig tier = plugin.settings().tier(anchor.tierId());
            World world = anchor.key().bukkitWorld();
            if (tier == null || world == null || plugin.settings().isWorldDisabled(world.getName())) {
                continue;
            }
            Set<Long> set = wanted.computeIfAbsent(world.getUID(), k -> new HashSet<>());
            int r = tier.radius();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    set.add(chunkKey(anchor.key().chunkX() + dx, anchor.key().chunkZ() + dz));
                }
            }
        }

        // Release chunks nobody needs any more
        Iterator<Map.Entry<UUID, Set<Long>>> worlds = ticketed.entrySet().iterator();
        while (worlds.hasNext()) {
            Map.Entry<UUID, Set<Long>> entry = worlds.next();
            World world = plugin.getServer().getWorld(entry.getKey());
            Set<Long> keep = wanted.getOrDefault(entry.getKey(), Set.of());
            Iterator<Long> it = entry.getValue().iterator();
            while (it.hasNext()) {
                long key = it.next();
                if (!keep.contains(key)) {
                    if (world != null) {
                        world.removePluginChunkTicket(chunkX(key), chunkZ(key), plugin);
                    }
                    it.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                worlds.remove();
            }
        }

        // Add tickets for newly wanted chunks
        for (Map.Entry<UUID, Set<Long>> entry : wanted.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            Set<Long> held = ticketed.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            for (long key : entry.getValue()) {
                if (held.add(key)) {
                    addTicket(world, key);
                }
            }
        }
    }

    private void addTicket(World world, long key) {
        UUID worldId = world.getUID();
        world.getChunkAtAsync(chunkX(key), chunkZ(key)).thenAccept(chunk -> {
            // The anchor may have stopped while the chunk was loading
            if (plugin.isEnabled() && isTicketed(worldId, key)) {
                chunk.addPluginChunkTicket(plugin);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Could not load chunk " + chunkX(key) + ", " + chunkZ(key)
                    + " in " + world.getName() + ": " + ex.getMessage());
            Set<Long> held = ticketed.get(worldId);
            if (held != null) {
                held.remove(key);
            }
            return null;
        });
    }

    private boolean isTicketed(UUID world, long key) {
        Set<Long> held = ticketed.get(world);
        return held != null && held.contains(key);
    }

    /** Called when a world unloads; Paper drops its tickets with it. */
    public void forgetWorld(UUID world) {
        ticketed.remove(world);
    }

    public void releaseAll() {
        for (Map.Entry<UUID, Set<Long>> entry : ticketed.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world != null) {
                for (long key : entry.getValue()) {
                    world.removePluginChunkTicket(chunkX(key), chunkZ(key), plugin);
                }
            }
        }
        ticketed.clear();
    }

    public int ticketCount() {
        int count = 0;
        for (Set<Long> set : ticketed.values()) {
            count += set.size();
        }
        return count;
    }

    private static long chunkKey(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    private static int chunkX(long key) {
        return (int) key;
    }

    private static int chunkZ(long key) {
        return (int) (key >> 32);
    }
}

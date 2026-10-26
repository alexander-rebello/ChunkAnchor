package io.github.alexanderrebello.chunkanchor.anchor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Position of an anchor block.
 */
public record BlockKey(UUID world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public Location center(World w) {
        return new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static BlockKey parse(String s) {
        String[] parts = s.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Human-readable "world x, y, z". */
    public String describe() {
        World w = bukkitWorld();
        return (w != null ? w.getName() : world.toString()) + " " + x + ", " + y + ", " + z;
    }
}

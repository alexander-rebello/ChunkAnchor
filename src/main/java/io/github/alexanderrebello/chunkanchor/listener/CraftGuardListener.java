package io.github.alexanderrebello.chunkanchor.listener;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import org.bukkit.Keyed;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom items are built on vanilla materials (e.g. the default eternal anchor is a netherite
 * block). This stops them from being used as that material in vanilla recipes, and makes sure
 * plugin recipes only accept custom items in the slots meant for them.
 */
public final class CraftGuardListener implements Listener {

    private final ChunkAnchorPlugin plugin;

    public CraftGuardListener(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!allowed(event.getRecipe(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        BlockState state = event.getBlock().getState();
        if (state instanceof Container container
                && !allowed(event.getRecipe(), container.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    private boolean allowed(Recipe recipe, ItemStack[] matrix) {
        Map<String, Integer> found = new HashMap<>();
        for (ItemStack item : matrix) {
            String id = plugin.items().customId(item);
            if (id != null) {
                found.merge(id, 1, Integer::sum);
            }
        }
        if (found.isEmpty()) {
            return true;
        }
        if (!(recipe instanceof Keyed keyed)) {
            return false;
        }
        Map<String, Integer> expected = plugin.recipes().expectedCustoms(keyed.getKey());
        return expected != null && expected.equals(found);
    }
}

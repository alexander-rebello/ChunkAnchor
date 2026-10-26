package io.github.alexanderrebello.chunkanchor.item;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.RecipeSpec;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registers the configurable recipes and remembers which custom items each one expects,
 * so {@link io.github.alexanderrebello.chunkanchor.listener.CraftGuardListener} can stop
 * custom items from being used as their plain base material.
 */
public final class RecipeManager {

    private final ChunkAnchorPlugin plugin;
    /** Recipe key -> custom item id -> how many of it the recipe needs. */
    private final Map<NamespacedKey, Map<String, Integer>> expectedCustoms = new HashMap<>();

    public RecipeManager(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        unregisterAll();
        RecipeSpec fuelRecipe = plugin.settings().fuel().recipe();
        if (fuelRecipe != null) {
            register("fuel", fuelRecipe, plugin.items().fuel(fuelRecipe.amount()));
        }
        for (TierConfig tier : plugin.settings().tiers().values()) {
            if (tier.recipe() != null) {
                register("anchor_" + tier.id().toLowerCase(Locale.ROOT), tier.recipe(),
                        plugin.items().anchor(tier, tier.recipe().amount()));
            }
        }
        Bukkit.updateRecipes();
    }

    public void unregisterAll() {
        for (NamespacedKey key : expectedCustoms.keySet()) {
            Bukkit.removeRecipe(key);
        }
        expectedCustoms.clear();
    }

    private void register(String id, RecipeSpec spec, ItemStack result) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        if (spec.shape().isEmpty() || spec.shape().size() > 3) {
            plugin.getLogger().warning("Recipe '" + id + "' needs a shape of 1-3 rows; skipping it.");
            return;
        }
        try {
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(spec.shape().toArray(new String[0]));
            Map<String, Integer> customs = new HashMap<>();
            for (Map.Entry<Character, String> e : spec.ingredients().entrySet()) {
                char symbol = e.getKey();
                int uses = countSymbol(spec.shape(), symbol);
                if (uses == 0) {
                    continue;
                }
                RecipeChoice choice = choice(id, e.getValue());
                if (choice == null) {
                    return;
                }
                recipe.setIngredient(symbol, choice);
                String custom = customIdOf(e.getValue());
                if (custom != null) {
                    customs.merge(custom, uses, Integer::sum);
                }
            }
            if (!Bukkit.addRecipe(recipe)) {
                plugin.getLogger().warning("Could not register recipe '" + id + "'.");
                return;
            }
            expectedCustoms.put(key, Collections.unmodifiableMap(customs));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            plugin.getLogger().warning("Invalid recipe '" + id + "': " + ex.getMessage());
        }
    }

    private RecipeChoice choice(String recipeId, String ingredient) {
        String value = ingredient.trim();
        if (value.equalsIgnoreCase(ItemFactory.FUEL_ID)) {
            return new RecipeChoice.ExactChoice(plugin.items().fuel(1));
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("anchor:")) {
            TierConfig tier = plugin.settings().tier(value.substring("anchor:".length()));
            if (tier == null) {
                plugin.getLogger().warning("Recipe '" + recipeId + "' references unknown tier '" + value + "'; skipping it.");
                return null;
            }
            return new RecipeChoice.ExactChoice(plugin.items().anchor(tier, 1));
        }
        Material material = Material.matchMaterial(value);
        if (material == null || material.isAir() || !material.isItem()) {
            plugin.getLogger().warning("Recipe '" + recipeId + "' has unknown ingredient '" + value + "'; skipping it.");
            return null;
        }
        return new RecipeChoice.MaterialChoice(material);
    }

    private static String customIdOf(String ingredient) {
        String value = ingredient.trim();
        if (value.equalsIgnoreCase(ItemFactory.FUEL_ID)) {
            return ItemFactory.FUEL_ID;
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("anchor:")) {
            return "anchor:" + value.substring("anchor:".length());
        }
        return null;
    }

    private static int countSymbol(List<String> shape, char symbol) {
        int count = 0;
        for (String row : shape) {
            for (char ch : row.toCharArray()) {
                if (ch == symbol) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Custom items a plugin recipe expects, or null if the key is not one of ours. */
    public Map<String, Integer> expectedCustoms(NamespacedKey key) {
        return expectedCustoms.get(key);
    }

    public void discover(Player player) {
        if (!expectedCustoms.isEmpty()) {
            player.discoverRecipes(new ArrayList<>(expectedCustoms.keySet()));
        }
    }
}

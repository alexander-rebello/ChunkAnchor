package io.github.alexanderrebello.chunkanchor.item;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.FuelConfig;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import io.github.alexanderrebello.chunkanchor.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Creates and identifies the plugin's custom items. Items are identified by persistent data,
 * never by name or material.
 */
public final class ItemFactory {

    /** Identity key used when counting custom items in crafting grids. */
    public static final String FUEL_ID = "fuel";

    private final ChunkAnchorPlugin plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey fuelKey;

    public ItemFactory(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "anchor_tier");
        this.fuelKey = new NamespacedKey(plugin, "anchor_fuel");
    }

    public ItemStack anchor(TierConfig tier, int amount) {
        ItemStack item = new ItemStack(tier.block(), Math.max(1, amount));
        item.editMeta(meta -> {
            meta.displayName(Text.parse(tier.name()));
            meta.lore(Text.parse(tier.lore(),
                    Text.p("area", tier.areaText()),
                    Text.p("radius", tier.radius()),
                    Text.p("capacity", Text.number(tier.capacity())),
                    Text.p("drain", Text.number(tier.drainPerMinute()))));
            if (tier.glint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.id());
        });
        return item;
    }

    public ItemStack fuel(int amount) {
        FuelConfig fuel = plugin.settings().fuel();
        ItemStack item = new ItemStack(fuel.material(), Math.max(1, amount));
        item.editMeta(meta -> {
            meta.displayName(Text.parse(fuel.name()));
            meta.lore(Text.parse(fuel.lore(), Text.p("charge", Text.number(fuel.charge()))));
            if (fuel.glint()) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.getPersistentDataContainer().set(fuelKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public Component fuelName() {
        return Text.parse(plugin.settings().fuel().name());
    }

    /** Tier id stored on an anchor item, or null if the item is not an anchor. */
    public String anchorTier(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        return pdc == null ? null : pdc.get(tierKey, PersistentDataType.STRING);
    }

    public boolean isFuel(ItemStack item) {
        PersistentDataContainer pdc = pdc(item);
        return pdc != null && pdc.has(fuelKey, PersistentDataType.BYTE);
    }

    /** "anchor:&lt;tier&gt;", "fuel", or null for ordinary items. */
    public String customId(ItemStack item) {
        if (isFuel(item)) {
            return FUEL_ID;
        }
        String tier = anchorTier(item);
        return tier == null ? null : "anchor:" + tier;
    }

    private static PersistentDataContainer pdc(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}

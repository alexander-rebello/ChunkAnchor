package io.github.alexanderrebello.chunkanchor.menu;

import io.github.alexanderrebello.chunkanchor.anchor.Anchor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder that marks an inventory as an anchor menu and links it to its anchor.
 */
public final class AnchorMenu implements InventoryHolder {

    private final Anchor anchor;
    private Inventory inventory;

    AnchorMenu(Anchor anchor) {
        this.anchor = anchor;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Anchor anchor() {
        return anchor;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

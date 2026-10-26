package io.github.alexanderrebello.chunkanchor.menu;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.anchor.Anchor;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import io.github.alexanderrebello.chunkanchor.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

/**
 * The anchor menu: renders it, keeps open menus up to date and handles clicks.
 * The menu is fully locked; fuel is moved by the plugin, never by dragging items into slots.
 */
public final class MenuManager implements Listener {

    private static final int SIZE = 27;
    private static final int SLOT_INFO = 4;
    private static final int BAR_START = 10;
    private static final int BAR_LENGTH = 7;
    private static final int SLOT_TOGGLE = 19;
    private static final int SLOT_INSERT_ONE = 21;
    private static final int SLOT_INSERT_ALL = 22;
    private static final int SLOT_WITHDRAW = 23;
    private static final int SLOT_CLOSE = 25;

    private final ChunkAnchorPlugin plugin;

    public MenuManager(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- opening & rendering -----------------------------------------------------------------

    public void open(Player player, Anchor anchor) {
        TierConfig tier = plugin.settings().tier(anchor.tierId());
        if (tier == null) {
            return;
        }
        AnchorMenu holder = new AnchorMenu(anchor);
        Component title = plugin.text().menu("title", Text.c("anchor", Text.parse(tier.name())));
        Inventory inventory = plugin.getServer().createInventory(holder, SIZE, title);
        holder.inventory(inventory);
        render(holder);
        player.openInventory(inventory);
    }

    public void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AnchorMenu menu) {
                render(menu);
            }
        }
    }

    public void close(Anchor anchor) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AnchorMenu menu
                    && menu.anchor() == anchor) {
                player.closeInventory();
            }
        }
    }

    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AnchorMenu) {
                player.closeInventory();
            }
        }
    }

    private void render(AnchorMenu menu) {
        Anchor anchor = menu.anchor();
        PluginConfig cfg = plugin.settings();
        TierConfig tier = cfg.tier(anchor.tierId());
        Inventory inv = menu.getInventory();
        if (tier == null || inv == null) {
            return;
        }
        double fuelValue = cfg.fuel().charge();
        double charge = anchor.charge(fuelValue);
        double ratio = Math.max(0, Math.min(1, charge / tier.capacity()));
        int percent = (int) Math.floor(ratio * 100);
        String status = anchor.isRunning() ? "status-running" : anchor.enabled() ? "status-empty" : "status-off";
        double minutesLeft = tier.drainPerMinute() <= 0 ? Double.POSITIVE_INFINITY : charge / tier.drainPerMinute();
        int burning = (int) Math.round(anchor.burnRemaining() / fuelValue * 100);

        TagResolver placeholders = TagResolver.resolver(
                Text.c("anchor", Text.parse(tier.name())),
                Text.c("status", plugin.text().menu(status)),
                Text.p("area", tier.areaText()),
                Text.p("owner", anchor.ownerName()),
                Text.p("charge", Text.number(Math.floor(charge))),
                Text.p("capacity", Text.number(tier.capacity())),
                Text.p("percent", percent),
                Text.p("time", anchor.hasCharge() ? Text.duration(minutesLeft) : "-"),
                Text.p("stored", anchor.storedFuel()),
                Text.p("burning", burning));

        Material fillerType = Material.matchMaterial(cfg.raw().getString("menu.filler", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = button(fillerType == null || !fillerType.isItem() ? Material.GRAY_STAINED_GLASS_PANE : fillerType,
                Component.text(" "), List.of());
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_INFO, button(tier.block(), Text.parse(tier.name()),
                plugin.text().menuLines("info-lore", placeholders)));

        int filled = (int) Math.ceil(ratio * BAR_LENGTH - 1e-9);
        Material full = ratio > 0.5 ? Material.LIME_STAINED_GLASS_PANE
                : ratio > 0.2 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        Component barName = plugin.text().menu("bar-name", placeholders);
        List<Component> barLore = plugin.text().menuLines("bar-lore", placeholders);
        for (int i = 0; i < BAR_LENGTH; i++) {
            inv.setItem(BAR_START + i, button(i < filled ? full : Material.BLACK_STAINED_GLASS_PANE, barName, barLore));
        }

        inv.setItem(SLOT_TOGGLE, button(anchor.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                plugin.text().menu(anchor.enabled() ? "toggle-on" : "toggle-off", placeholders), List.of()));

        List<Component> insertLore = plugin.text().menuLines("insert-lore", placeholders);
        inv.setItem(SLOT_INSERT_ONE, button(cfg.fuel().material(), plugin.text().menu("insert-one", placeholders), insertLore));
        inv.setItem(SLOT_INSERT_ALL, button(Material.HOPPER, plugin.text().menu("insert-all", placeholders), insertLore));

        ItemStack withdraw = button(Material.CHEST, plugin.text().menu("withdraw", placeholders),
                plugin.text().menuLines("withdraw-lore", placeholders));
        withdraw.setAmount(Math.max(1, Math.min(64, anchor.storedFuel())));
        inv.setItem(SLOT_WITHDRAW, withdraw);

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, plugin.text().menu("close", placeholders), List.of()));
    }

    private static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
        });
        return item;
    }

    // ---- clicks ------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AnchorMenu) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AnchorMenu menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        Anchor anchor = menu.anchor();
        if (plugin.anchors().get(anchor.key()) != anchor) {
            player.closeInventory();
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        // Clicking fuel in the player's own inventory inserts it
        if (clicked.equals(event.getView().getBottomInventory())) {
            ItemStack current = event.getCurrentItem();
            if (plugin.items().isFuel(current)) {
                int wanted = event.isShiftClick() ? current.getAmount() : 1;
                int accepted = insert(player, anchor, wanted, current);
                if (accepted > 0) {
                    current.setAmount(current.getAmount() - accepted);
                    event.setCurrentItem(current.getAmount() > 0 ? current : null);
                }
            }
            return;
        }

        // Clicking the menu while holding fuel on the cursor inserts it
        ItemStack cursor = event.getCursor();
        if (plugin.items().isFuel(cursor)) {
            int wanted = event.getClick() == ClickType.RIGHT ? 1 : cursor.getAmount();
            int accepted = insert(player, anchor, wanted, cursor);
            if (accepted > 0) {
                cursor.setAmount(cursor.getAmount() - accepted);
                player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            }
            return;
        }
        switch (event.getRawSlot()) {
            case SLOT_TOGGLE -> {
                anchor.enabled(!anchor.enabled());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, anchor.enabled() ? 1.2f : 0.8f);
                plugin.anchors().changed();
                render(menu);
            }
            case SLOT_INSERT_ONE -> insertFromInventory(player, anchor, 1);
            case SLOT_INSERT_ALL -> insertFromInventory(player, anchor, Integer.MAX_VALUE);
            case SLOT_WITHDRAW -> withdraw(player, anchor);
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    /**
     * Accepts up to {@code wanted} fuel items into the anchor. The caller removes the returned
     * amount from wherever the items came from.
     */
    private int insert(Player player, Anchor anchor, int wanted, ItemStack source) {
        TierConfig tier = plugin.settings().tier(anchor.tierId());
        if (tier == null || source == null) {
            return 0;
        }
        int space = anchor.freeFuelSlots(tier.capacity(), plugin.settings().fuel().charge());
        int accepted = Math.min(Math.min(wanted, space), source.getAmount());
        if (accepted <= 0) {
            plugin.text().send(player, "tank-full");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return 0;
        }
        boolean wasRunning = anchor.isRunning();
        anchor.addFuel(accepted);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f);
        plugin.text().send(player, "inserted", Text.p("amount", accepted));
        if (wasRunning != anchor.isRunning()) {
            plugin.anchors().changed();
        } else {
            plugin.anchors().markDirty();
        }
        refreshAll();
        return accepted;
    }

    private void insertFromInventory(Player player, Anchor anchor, int wanted) {
        PlayerInventory inv = player.getInventory();
        int remaining = wanted;
        int insertedTotal = 0;
        boolean foundAny = false;
        TierConfig tier = plugin.settings().tier(anchor.tierId());
        if (tier == null) {
            return;
        }
        int space = anchor.freeFuelSlots(tier.capacity(), plugin.settings().fuel().charge());
        if (space <= 0) {
            plugin.text().send(player, "tank-full");
            return;
        }
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0 && space > 0; i++) {
            ItemStack stack = contents[i];
            if (!plugin.items().isFuel(stack)) {
                continue;
            }
            foundAny = true;
            int take = Math.min(Math.min(remaining, space), stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            contents[i] = stack.getAmount() > 0 ? stack : null;
            remaining -= take;
            space -= take;
            insertedTotal += take;
        }
        if (!foundAny) {
            plugin.text().send(player, "no-fuel-in-inventory");
            return;
        }
        inv.setStorageContents(contents);
        boolean wasRunning = anchor.isRunning();
        anchor.addFuel(insertedTotal);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f);
        plugin.text().send(player, "inserted", Text.p("amount", insertedTotal));
        if (wasRunning != anchor.isRunning()) {
            plugin.anchors().changed();
        } else {
            plugin.anchors().markDirty();
        }
        refreshAll();
    }

    private void withdraw(Player player, Anchor anchor) {
        if (anchor.storedFuel() <= 0) {
            plugin.text().send(player, "nothing-to-withdraw");
            return;
        }
        boolean wasRunning = anchor.isRunning();
        int amount = anchor.takeStoredFuel();
        give(player, amount);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1f);
        plugin.text().send(player, "withdrawn", Text.p("amount", amount));
        if (wasRunning != anchor.isRunning()) {
            plugin.anchors().changed();
        } else {
            plugin.anchors().markDirty();
        }
        refreshAll();
    }

    /** Gives fuel items to the player, dropping what doesn't fit. */
    private void give(Player player, int amount) {
        int maxStack = plugin.items().fuel(1).getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(maxStack, amount);
            amount -= n;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.items().fuel(n));
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }
}

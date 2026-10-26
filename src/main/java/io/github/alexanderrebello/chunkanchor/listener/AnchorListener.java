package io.github.alexanderrebello.chunkanchor.listener;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.anchor.Anchor;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import io.github.alexanderrebello.chunkanchor.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Placing, using, breaking and protecting anchor blocks.
 */
public final class AnchorListener implements Listener {

    private static final String PERM_USE = "chunkanchor.use";
    private static final String PERM_ADMIN = "chunkanchor.admin";
    private static final String PERM_BYPASS_LIMIT = "chunkanchor.bypass.limit";

    private final ChunkAnchorPlugin plugin;

    public AnchorListener(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- placing -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        String tierId = plugin.items().anchorTier(event.getItemInHand());
        if (tierId == null) {
            return;
        }
        Player player = event.getPlayer();
        PluginConfig cfg = plugin.settings();
        TierConfig tier = cfg.tier(tierId);
        if (tier == null) {
            event.setCancelled(true);
            plugin.text().send(player, "unknown-tier", Text.p("tier", tierId));
            return;
        }
        if (!player.hasPermission(PERM_USE)) {
            event.setCancelled(true);
            plugin.text().send(player, "no-permission");
            return;
        }
        if (cfg.isWorldDisabled(event.getBlockPlaced().getWorld().getName())) {
            event.setCancelled(true);
            plugin.text().send(player, "world-disabled");
            return;
        }
        int limit = cfg.maxAnchorsPerPlayer();
        if (limit > 0 && !player.hasPermission(PERM_BYPASS_LIMIT)
                && plugin.anchors().ownedBy(player.getUniqueId()).size() >= limit) {
            event.setCancelled(true);
            plugin.text().send(player, "limit-reached", Text.p("limit", limit));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String tierId = plugin.items().anchorTier(event.getItemInHand());
        TierConfig tier = plugin.settings().tier(tierId);
        if (tier == null) {
            return;
        }
        plugin.anchors().create(event.getBlockPlaced(), event.getPlayer(), tierId);
        plugin.text().send(event.getPlayer(), "placed", Text.c("anchor", Text.parse(tier.name())));
        event.getBlockPlaced().getWorld().playSound(event.getBlockPlaced().getLocation().toCenterLocation(),
                Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.3f);
    }

    // ---- opening the menu --------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Anchor anchor = plugin.anchors().get(event.getClickedBlock());
        if (anchor == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        // Sneaking with a block in hand places it against the anchor, like with vanilla containers
        if (player.isSneaking() && hand != null && !hand.isEmpty()) {
            return;
        }
        // Block vanilla interaction (e.g. linking a compass to a lodestone) for both hands
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.settings().tier(anchor.tierId()) == null) {
            plugin.text().send(player, "unknown-tier", Text.p("tier", anchor.tierId()));
            return;
        }
        if (!player.hasPermission(PERM_USE)) {
            plugin.text().send(player, "no-permission");
            return;
        }
        if (plugin.settings().onlyOwnerCanAccess() && !isOwnerOrAdmin(player, anchor)) {
            plugin.text().send(player, "not-owner", Text.p("owner", anchor.ownerName()));
            return;
        }
        if (anchor.owner().equals(player.getUniqueId()) && !player.getName().equals(anchor.ownerName())) {
            anchor.ownerName(player.getName());
            plugin.anchors().markDirty();
        }
        plugin.menus().open(player, anchor);
    }

    // ---- breaking ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Anchor anchor = plugin.anchors().get(event.getBlock());
        if (anchor == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.settings().onlyOwnerCanBreak() && !isOwnerOrAdmin(player, anchor)) {
            event.setCancelled(true);
            plugin.text().send(player, "not-owner", Text.p("owner", anchor.ownerName()));
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        TierConfig tier = plugin.settings().tier(anchor.tierId());
        dropContents(anchor, event.getBlock(), player.getGameMode() != GameMode.CREATIVE);
        plugin.anchors().remove(anchor);
        if (tier != null) {
            plugin.text().send(player, "broken", Text.c("anchor", Text.parse(tier.name())));
        }
    }

    /** Drops the unused fuel and, if wanted, the anchor item itself. The burning fuel is lost. */
    private void dropContents(Anchor anchor, Block block, boolean dropAnchor) {
        Location at = block.getLocation().toCenterLocation();
        int fuel = anchor.takeStoredFuel();
        int maxStack = plugin.items().fuel(1).getMaxStackSize();
        while (fuel > 0) {
            int n = Math.min(maxStack, fuel);
            fuel -= n;
            block.getWorld().dropItemNaturally(at, plugin.items().fuel(n));
        }
        TierConfig tier = plugin.settings().tier(anchor.tierId());
        if (dropAnchor && tier != null) {
            block.getWorld().dropItemNaturally(at, plugin.items().anchor(tier, 1));
        }
    }

    // ---- protection --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        blocks.removeIf(block -> {
            Anchor anchor = plugin.anchors().get(block);
            if (anchor == null) {
                return false;
            }
            if (!plugin.settings().explosionProof()) {
                dropContents(anchor, block, true);
                plugin.anchors().remove(anchor);
                block.setType(Material.AIR);
            }
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.anchors().get(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (plugin.anchors().get(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (plugin.anchors().get(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    // ---- worlds & players --------------------------------------------------------------------

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.anchors().changed();
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.tickets().forgetWorld(event.getWorld().getUID());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.settings().autoDiscoverRecipes()) {
            plugin.recipes().discover(event.getPlayer());
        }
    }

    private static boolean isOwnerOrAdmin(Player player, Anchor anchor) {
        return anchor.owner().equals(player.getUniqueId()) || player.hasPermission(PERM_ADMIN);
    }
}

package io.github.alexanderrebello.chunkanchor.command;

import io.github.alexanderrebello.chunkanchor.ChunkAnchorPlugin;
import io.github.alexanderrebello.chunkanchor.anchor.Anchor;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig.TierConfig;
import io.github.alexanderrebello.chunkanchor.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /chunkanchor list|give|fuel|debug|reload
 */
public final class AnchorCommand implements BasicCommand {

    private static final String PERM_USE = "chunkanchor.use";
    private static final String PERM_ADMIN = "chunkanchor.admin";

    private final ChunkAnchorPlugin plugin;

    public AnchorCommand(ChunkAnchorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            plugin.text().send(sender, "usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender, args);
            case "give" -> give(sender, args);
            case "fuel" -> fuel(sender, args);
            case "debug" -> debug(sender);
            case "reload" -> {
                if (requireAdmin(sender)) {
                    plugin.reload();
                    plugin.text().send(sender, "reloaded");
                }
            }
            default -> plugin.text().send(sender, "usage");
        }
    }

    private void list(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            OfflinePlayer player = findPlayer(args[1]);
            if (player == null) {
                plugin.text().send(sender, "player-not-found", Text.p("player", args[1]));
                return;
            }
            target = player.getUniqueId();
            name = player.getName() != null ? player.getName() : args[1];
        } else if (sender instanceof Player player) {
            if (!sender.hasPermission(PERM_USE)) {
                plugin.text().send(sender, "no-permission");
                return;
            }
            target = player.getUniqueId();
            name = player.getName();
        } else {
            plugin.text().send(sender, "usage");
            return;
        }
        List<Anchor> anchors = plugin.anchors().ownedBy(target);
        if (anchors.isEmpty()) {
            plugin.text().send(sender, "list-empty", Text.p("player", name));
            return;
        }
        plugin.text().send(sender, "list-header", Text.p("player", name), Text.p("count", anchors.size()));
        double fuelValue = plugin.settings().fuel().charge();
        for (Anchor anchor : anchors) {
            TierConfig tier = plugin.settings().tier(anchor.tierId());
            sender.sendMessage(plugin.text().message("list-entry",
                    Text.c("anchor", tier != null ? Text.parse(tier.name()) : Component.text(anchor.tierId())),
                    Text.p("location", anchor.key().describe()),
                    Text.c("status", status(anchor)),
                    Text.p("charge", Text.number(Math.floor(anchor.charge(fuelValue)))),
                    Text.p("capacity", tier != null ? Text.number(tier.capacity()) : "?")));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            plugin.text().send(sender, "usage");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.text().send(sender, "player-not-found", Text.p("player", args[1]));
            return;
        }
        TierConfig tier = plugin.settings().tier(args[2]);
        if (tier == null) {
            plugin.text().send(sender, "tier-not-found", Text.p("tier", args[2]),
                    Text.p("tiers", String.join(", ", plugin.settings().tiers().keySet())));
            return;
        }
        int amount = parseAmount(args, 3);
        giveItems(target, plugin.items().anchor(tier, 1), amount);
        plugin.text().send(sender, "given", Text.p("amount", amount), Text.c("item", Text.parse(tier.name())),
                Text.p("player", target.getName()));
    }

    private void fuel(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            plugin.text().send(sender, "usage");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.text().send(sender, "player-not-found", Text.p("player", args[1]));
            return;
        }
        int amount = parseAmount(args, 2);
        giveItems(target, plugin.items().fuel(1), amount);
        plugin.text().send(sender, "given", Text.p("amount", amount), Text.c("item", plugin.items().fuelName()),
                Text.p("player", target.getName()));
    }

    private void debug(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        Collection<Anchor> all = plugin.anchors().all();
        long running = all.stream().filter(Anchor::isRunning).count();
        plugin.text().send(sender, "debug-header", Text.p("tickets", plugin.tickets().ticketCount()),
                Text.p("running", running), Text.p("total", all.size()));
        for (Anchor anchor : all) {
            World world = anchor.key().bukkitWorld();
            String level;
            if (world == null) {
                level = "world not loaded";
            } else if (!world.isChunkLoaded(anchor.key().chunkX(), anchor.key().chunkZ())) {
                level = "UNLOADED";
            } else {
                level = world.getChunkAt(anchor.key().chunkX(), anchor.key().chunkZ()).getLoadLevel().name();
            }
            sender.sendMessage(plugin.text().message("debug-entry",
                    Text.p("location", anchor.key().describe()),
                    Text.c("status", status(anchor)),
                    Text.p("level", level)));
        }
    }

    private Component status(Anchor anchor) {
        String key = anchor.isRunning() ? "status-running" : anchor.enabled() ? "status-empty" : "status-off";
        return plugin.text().menu(key);
    }

    private void giveItems(Player target, ItemStack template, int amount) {
        int maxStack = template.getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(maxStack, amount);
            amount -= n;
            ItemStack stack = template.clone();
            stack.setAmount(n);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
        }
    }

    private static int parseAmount(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(2304, Integer.parseInt(args[index])));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer findPlayer(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        return cached != null ? cached : null;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(PERM_ADMIN)) {
            return true;
        }
        plugin.text().send(sender, "no-permission");
        return false;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(PERM_ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.add("list");
            if (admin) {
                options.addAll(List.of("give", "fuel", "debug", "reload"));
            }
        } else if (admin && args.length == 2 && List.of("give", "fuel", "list").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                options.add(player.getName());
            }
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(plugin.settings().tiers().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}

package io.github.alexanderrebello.chunkanchor;

import io.github.alexanderrebello.chunkanchor.anchor.AnchorManager;
import io.github.alexanderrebello.chunkanchor.anchor.ChunkTicketManager;
import io.github.alexanderrebello.chunkanchor.command.AnchorCommand;
import io.github.alexanderrebello.chunkanchor.config.PluginConfig;
import io.github.alexanderrebello.chunkanchor.item.ItemFactory;
import io.github.alexanderrebello.chunkanchor.item.RecipeManager;
import io.github.alexanderrebello.chunkanchor.listener.AnchorListener;
import io.github.alexanderrebello.chunkanchor.listener.CraftGuardListener;
import io.github.alexanderrebello.chunkanchor.menu.MenuManager;
import io.github.alexanderrebello.chunkanchor.util.Text;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ChunkAnchorPlugin extends JavaPlugin {

    private PluginConfig settings;
    private Text text;
    private ItemFactory items;
    private RecipeManager recipes;
    private ChunkTicketManager tickets;
    private AnchorManager anchors;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        settings = PluginConfig.load(getConfig(), getLogger());
        text = new Text(getConfig());

        items = new ItemFactory(this);
        recipes = new RecipeManager(this);
        tickets = new ChunkTicketManager(this);
        anchors = new AnchorManager(this, tickets);
        menus = new MenuManager(this);

        anchors.load();
        recipes.registerAll();

        getServer().getPluginManager().registerEvents(new AnchorListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftGuardListener(this), this);
        getServer().getPluginManager().registerEvents(menus, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("chunkanchor", "Manage Chunk Anchors", List.of("anchor"), new AnchorCommand(this)));

        anchors.start();
        anchors.changed();
        if (settings.autoDiscoverRecipes()) {
            for (Player player : getServer().getOnlinePlayers()) {
                recipes.discover(player);
            }
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (anchors != null) {
            anchors.stop();
            anchors.save();
        }
        if (tickets != null) {
            tickets.releaseAll();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
    }

    /** Reloads config.yml and applies it to recipes, timers, chunk tickets and open menus. */
    public void reload() {
        reloadConfig();
        settings = PluginConfig.load(getConfig(), getLogger());
        text.update(getConfig());
        recipes.registerAll();
        anchors.start();
        anchors.changed();
        menus.refreshAll();
        if (settings.autoDiscoverRecipes()) {
            for (Player player : getServer().getOnlinePlayers()) {
                recipes.discover(player);
            }
        }
    }

    public PluginConfig settings() { return settings; }
    public Text text() { return text; }
    public ItemFactory items() { return items; }
    public RecipeManager recipes() { return recipes; }
    public ChunkTicketManager tickets() { return tickets; }
    public AnchorManager anchors() { return anchors; }
    public MenuManager menus() { return menus; }
}

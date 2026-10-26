package io.github.alexanderrebello.chunkanchor.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MiniMessage helpers for config-driven text.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private FileConfiguration config;

    public Text(FileConfiguration config) {
        this.config = config;
    }

    public void update(FileConfiguration config) {
        this.config = config;
    }

    /** Parses a MiniMessage string; item text is non-italic unless the string says otherwise. */
    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null) {
            return Component.empty();
        }
        return MM.deserialize(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> parse(List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(parse(line, resolvers));
        }
        return out;
    }

    /** A message from the messages section, with prefix. */
    public Component message(String key, TagResolver... resolvers) {
        String prefix = config.getString("messages.prefix", "");
        String body = config.getString("messages." + key, key);
        return MM.deserialize(prefix + body, resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(message(key, resolvers));
    }

    /** A string from the menu section. */
    public Component menu(String key, TagResolver... resolvers) {
        return parse(config.getString("menu." + key, key), resolvers);
    }

    public List<Component> menuLines(String key, TagResolver... resolvers) {
        return parse(config.getStringList("menu." + key), resolvers);
    }

    public static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String duration(double minutes) {
        if (Double.isInfinite(minutes) || Double.isNaN(minutes)) {
            return "∞";
        }
        long total = (long) Math.floor(minutes);
        long days = total / 1440;
        long hours = (total % 1440) / 60;
        long mins = total % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }
}

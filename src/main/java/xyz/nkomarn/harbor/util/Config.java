package xyz.nkomarn.harbor.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private final Harbor harbor;

    public Config(@NotNull Harbor harbor) {
        this.harbor = harbor;
        harbor.saveDefaultConfig();
    }

    /**
     * Translates legacy '&' color codes and hex color codes ('&#RRGGBB' or '#RRGGBB').
     *
     * @param text The text to colorize.
     * @return The colorized string.
     */
    @NotNull
    public static String colorize(@NotNull String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder(text.length() + 32);
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Fetches an instance of the FileConfiguration.
     *
     * @return The configuration for this server.
     */
    @NotNull
    public FileConfiguration getConfig() {
        return harbor.getConfig();
    }

    /**
     * Reloads the configuration, loading any new changes.
     */
    public void reload() {
        harbor.reloadConfig();
    }

    /**
     * Returns the prefix for Harbor messages.
     * @return Harbor message prefix.
     */
    @NotNull
    public String getPrefix() {
        return colorize(getString("messages.miscellaneous.chat-prefix"));
    }

    /**
     * Fetches a boolean from the configuration
     * if location is not found, false is returned
     *
     * @param location Configuration location of the boolean
     */
    public boolean getBoolean(@NotNull String location) {
        return getConfig().getBoolean(location, false);
    }

    /**
     * Fetches a string from the configuration
     * if location is not found, empty string is returned
     *
     * @param location Configuration location of the string
     */
    @NotNull
    public String getString(@NotNull String location) {
        return getConfig().getString(location, "");
    }

    /**
     * Fetches an integer from the configuration
     * if location is not found, 0 is returned
     *
     * @param location Configuration location of the integer
     */
    public int getInteger(@NotNull String location) {
        return getConfig().getInt(location, 0);
    }

    /**
     * Fetches a double from the configuration
     * if location is not found, 0.0 is returned
     *
     * @param location Configuration location of the double
     */
    public double getDouble(@NotNull String location) {
        return getConfig().getDouble(location, 0.0);
    }

    @NotNull
    public List<String> getStringList(@NotNull String location) {
        return getConfig().getStringList(location);
    }
}

package xyz.nkomarn.harbor.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([A-Fa-f0-9]{6})");
    private static final List<String> SUPPORTED_LANGUAGES = List.of("en", "es", "ca");
    private final Harbor harbor;
    private FileConfiguration langConfig;

    public Config(@NotNull Harbor harbor) {
        this.harbor = harbor;
        harbor.saveDefaultConfig();
        saveDefaultLanguages();
        loadLanguage();
    }

    private void saveDefaultLanguages() {
        for (String lang : SUPPORTED_LANGUAGES) {
            String fileName = "messages_" + lang + ".yml";
            File langFile = new File(harbor.getDataFolder(), fileName);
            if (!langFile.exists()) {
                harbor.saveResource(fileName, false);
            }
        }
    }

    private void loadLanguage() {
        String lang = getConfig().getString("language", "en").toLowerCase(Locale.ROOT).trim();
        if (lang.equals("custom") || lang.isEmpty()) {
            langConfig = null;
            return;
        }

        if (lang.equals("español") || lang.equals("castellano") || lang.equals("spanish")) {
            lang = "es";
        } else if (lang.equals("catala") || lang.equals("català") || lang.equals("catalan")) {
            lang = "ca";
        } else if (lang.equals("english") || lang.equals("ingles") || lang.equals("inglés")) {
            lang = "en";
        }

        String fileName = "messages_" + lang + ".yml";
        File langFile = new File(harbor.getDataFolder(), fileName);
        if (!langFile.exists()) {
            if (SUPPORTED_LANGUAGES.contains(lang)) {
                harbor.saveResource(fileName, false);
            } else {
                harbor.getLogger().warning("Language file '" + fileName + "' not found. Falling back to config.yml messages.");
                langConfig = null;
                return;
            }
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream defStream = harbor.getResource(fileName)) {
            if (defStream != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
        langConfig = loaded;
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
        saveDefaultLanguages();
        loadLanguage();
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
        if (langConfig != null && location.startsWith("messages.") && langConfig.contains(location)) {
            return langConfig.getString(location, "");
        }
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
        if (langConfig != null && location.startsWith("messages.") && langConfig.contains(location)) {
            return langConfig.getStringList(location);
        }
        return getConfig().getStringList(location);
    }
}

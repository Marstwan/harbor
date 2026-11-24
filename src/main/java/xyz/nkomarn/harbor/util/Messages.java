package xyz.nkomarn.harbor.util;

import com.google.common.base.Enums;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.task.Checker;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Messages implements Listener {

    private final Harbor harbor;
    private final Config config;
    private final Map<UUID, BossBar> bossBars;
    private final boolean papiPresent;

    public Messages(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.config = harbor.getConfiguration();
        this.bossBars = new HashMap<>();
        this.papiPresent = harbor.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        for (World world : Bukkit.getWorlds()) {
            if (harbor.getChecker().isBlacklisted(world)) {
                continue;
            }

            registerBar(world);
        }
    }

    /**
     * Converts Minecraft world ticks (0 = 06:00) into 24-hour HH:MM format.
     *
     * @param world The world context.
     * @return Formatted time string (e.g. "23:45").
     */
    @NotNull
    public static String formatWorldTime(@NotNull World world) {
        long time = world.getTime();
        int totalMinutes = (int) (((time + 6000) % 24000) * 60 / 1000);
        int hours = (totalMinutes / 60) % 24;
        int minutes = totalMinutes % 60;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    /**
     * Sends a message to all players in a given world.
     *
     * @param world   The world context.
     * @param message The message to send.
     */
    public void sendWorldChatMessage(@NotNull World world, @NotNull String message) {
        if (!config.getBoolean("messages.chat.enabled") || message.isEmpty()) {
            return;
        }

        String preparedMessage = prepareMessage(world, message);
        for (Player player : world.getPlayers()) {
            player.sendMessage(preparedMessage);
        }
    }

    /**
     * Sends an actionbar message to all players in a given world.
     *
     * @param world   The world context.
     * @param message The message to send.
     */
    public void sendActionBarMessage(@NotNull World world, @NotNull String message) {
        if (!config.getBoolean("messages.actionbar.enabled") || message.isEmpty()) {
            return;
        }

        BaseComponent preparedMessage = TextComponent.fromLegacy(prepareMessage(world, message));
        for (Player player : world.getPlayers()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, preparedMessage);
        }
    }

    /**
     * Sends a center-screen title and subtitle to all players in a given world.
     *
     * @param world    The world context.
     * @param title    The main title text.
     * @param subtitle The subtitle text.
     * @param fadeIn   Fade-in duration in ticks.
     * @param stay     Stay duration in ticks.
     * @param fadeOut  Fade-out duration in ticks.
     */
    public void sendWorldTitle(@NotNull World world, @NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut) {
        sendWorldTitle(world, null, title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Sends a center-screen title and subtitle to all players in a given world, with optional player placeholders.
     *
     * @param world         The world context.
     * @param contextPlayer Optional player context for [player] and [displayname] placeholders.
     * @param title         The main title text.
     * @param subtitle      The subtitle text.
     * @param fadeIn        Fade-in duration in ticks.
     * @param stay          Stay duration in ticks.
     * @param fadeOut       Fade-out duration in ticks.
     */
    public void sendWorldTitle(@NotNull World world, @Nullable Player contextPlayer, @NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!config.getBoolean("messages.title.enabled")) {
            return;
        }
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }

        String preparedTitle = contextPlayer != null
                ? prepareMessage(world, preparePlayerPlaceholders(contextPlayer, title))
                : prepareMessage(world, title);
        String preparedSubtitle = contextPlayer != null
                ? prepareMessage(world, preparePlayerPlaceholders(contextPlayer, subtitle))
                : prepareMessage(world, subtitle);

        for (Player player : world.getPlayers()) {
            player.sendTitle(preparedTitle, preparedSubtitle, fadeIn, stay, fadeOut);
        }
    }

    /**
     * Selects a random message from a string list and sends it to the given world.
     *
     * @param world        The world context.
     * @param listLocation The location of the message list in the configuration.
     */
    public void sendRandomChatMessage(@NotNull World world, @NotNull String listLocation) {
        List<String> messages = config.getStringList(listLocation);

        if (messages.isEmpty()) {
            return;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        sendWorldChatMessage(world, messages.get(index));
    }

    /**
     * Sets the message for the given world's bossbar.
     *
     * @param world      The world in which the bossbar exists.
     * @param message    The message to set.
     * @param color      The bossbar color to set.
     * @param percentage The bossbar percentage to set.
     */
    public void sendBossBarMessage(@NotNull World world, @NotNull String message, @NotNull String color, double percentage) {
        if (!config.getBoolean("messages.bossbar.enabled") || message.isEmpty()) {
            return;
        }

        BossBar bar = bossBars.get(world.getUID());

        if (bar == null) {
            return;
        }

        if (percentage == 0) {
            bar.removeAll();
            return;
        }

        bar.setTitle(prepareMessage(world, message));
        bar.setColor(Enums.getIfPresent(BarColor.class, color.toUpperCase(Locale.ROOT)).or(BarColor.BLUE));
        bar.setProgress(Math.max(0.0, Math.min(1.0, percentage)));
        world.getPlayers().forEach(bar::addPlayer);
    }

    /**
     * Replaces all available world placeholders in a given string and colorizes it.
     *
     * @param world   The world context.
     * @param message The raw message with placeholders.
     * @return The provided message with placeholders replaced with correct values for the world context.
     */
    @NotNull
    public String prepareMessage(@NotNull World world, @NotNull String message) {
        Checker checker = harbor.getChecker();
        int sleeping = checker.getSleepingPlayers(world).size();
        int players = checker.getPlayers(world);
        int needed = checker.getSkipAmount(players);
        int more = Math.max(0, needed - sleeping);
        return Config.colorize(message
                .replace("[time]", formatWorldTime(world))
                .replace("[sleeping]", String.valueOf(sleeping))
                .replace("[players]", String.valueOf(players))
                .replace("[needed]", String.valueOf(needed))
                .replace("[more]", String.valueOf(more)));
    }

    /**
     * Replaces player placeholders without translating color codes yet (so world placeholders can be applied next).
     */
    @NotNull
    private String preparePlayerPlaceholders(@NotNull Player player, @NotNull String message) {
        String output = message
                .replace("[player]", player.getName())
                .replace("[displayname]", player.getDisplayName());

        if (papiPresent) {
            output = PlaceholderAPI.setPlaceholders(player, output);
        }
        return output;
    }

    @NotNull
    public String prepareMessage(@NotNull Player player, @NotNull String message) {
        return Config.colorize(preparePlayerPlaceholders(player, message));
    }

    @NotNull
    public String prepareMessage(@NotNull World world, @NotNull Player player, @NotNull String message) {
        return prepareMessage(world, preparePlayerPlaceholders(player, message));
    }

    /**
     * Creates a new bossbar for the given world if one isn't already present.
     *
     * @param world The world in which to create the bossbar.
     */
    public void registerBar(@NotNull World world) {
        bossBars.computeIfAbsent(world.getUID(), uuid -> Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID));
    }

    /**
     * Hides the bossbar for the given world if one is present.
     *
     * @param world The world in which to hide the bossbar.
     */
    public void clearBar(@NotNull World world) {
        Optional.ofNullable(bossBars.get(world.getUID())).ifPresent(BossBar::removeAll);
    }

    @EventHandler
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        World world = event.getWorld();
        harbor.getChecker().syncWorldGameRule(world);
        if (!harbor.getChecker().isBlacklisted(world)) {
            registerBar(world);
        }
    }

    @EventHandler
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        Optional.ofNullable(bossBars.get(event.getFrom().getUID())).ifPresent(bossBar -> bossBar.removePlayer(event.getPlayer()));
    }
}

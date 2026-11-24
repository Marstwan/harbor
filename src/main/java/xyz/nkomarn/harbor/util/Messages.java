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
import org.bukkit.scheduler.BukkitTask;
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
    private final Map<UUID, BukkitTask> transientBarTasks;
    private final boolean papiPresent;

    public Messages(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.config = harbor.getConfiguration();
        this.bossBars = new HashMap<>();
        this.transientBarTasks = new HashMap<>();
        this.papiPresent = harbor.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");

        for (World world : Bukkit.getWorlds()) {
            if (harbor.getChecker().isBlacklisted(world)) {
                continue;
            }

            registerBar(world);
        }
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
        sendActionBarMessage(world, null, message, 0);
    }

    /**
     * Sends an actionbar message to all players in a given world with optional player and progress context.
     */
    public void sendActionBarMessage(@NotNull World world, @Nullable Player contextPlayer, @NotNull String message, int progressPercent) {
        if (!config.getBoolean("messages.actionbar.enabled") || message.isEmpty()) {
            return;
        }

        String raw = contextPlayer != null ? preparePlayerPlaceholders(contextPlayer, message) : message;
        String prepared = prepareMessage(world, raw.replace("[progress]", String.valueOf(progressPercent)));
        BaseComponent component = TextComponent.fromLegacy(prepared);
        for (Player player : world.getPlayers()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, component);
        }
    }

    /**
     * Selects a random message from a string list and sends it to the given world's chat.
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
     * @param percentage The bossbar percentage to set (0.0 to 1.0).
     */
    public void sendBossBarMessage(@NotNull World world, @NotNull String message, @NotNull String color, double percentage) {
        if (!config.getBoolean("messages.bossbar.enabled") || message.isEmpty()) {
            return;
        }

        cancelTransientBarTask(world.getUID());

        BossBar bar = bossBars.get(world.getUID());
        if (bar == null) {
            registerBar(world);
            bar = bossBars.get(world.getUID());
            if (bar == null) {
                return;
            }
        }

        if (percentage <= 0) {
            bar.removeAll();
            return;
        }

        double clamped = Math.max(0.0, Math.min(1.0, percentage));
        int progressPercent = (int) Math.round(clamped * 100.0);

        bar.setTitle(prepareMessage(world, message.replace("[progress]", String.valueOf(progressPercent))));
        bar.setColor(Enums.getIfPresent(BarColor.class, color.toUpperCase(Locale.ROOT)).or(BarColor.BLUE));
        bar.setProgress(clamped);
        for (Player player : world.getPlayers()) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    /**
     * Shows a temporary BossBar banner (e.g. Good Morning or Cancelled) for a short duration, then clears it.
     */
    public void sendTransientBossBar(@NotNull World world, @NotNull String message, @NotNull String color, double percentage, long durationTicks) {
        if (!config.getBoolean("messages.bossbar.enabled") || message.isEmpty()) {
            return;
        }

        UUID uid = world.getUID();
        cancelTransientBarTask(uid);

        sendBossBarMessage(world, message, color, Math.max(0.01, percentage));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(harbor, () -> {
            transientBarTasks.remove(uid);
            // Only clear if no players are actively sleeping again
            if (!harbor.getChecker().isSkipping(world) && harbor.getChecker().getSleepingPlayers(world).isEmpty()) {
                Optional.ofNullable(bossBars.get(uid)).ifPresent(BossBar::removeAll);
            }
        }, durationTicks);
        transientBarTasks.put(uid, task);
    }

    public boolean hasActiveTransientBar(@NotNull World world) {
        return transientBarTasks.containsKey(world.getUID());
    }

    private void cancelTransientBarTask(@NotNull UUID worldUid) {
        BukkitTask existing = transientBarTasks.remove(worldUid);
        if (existing != null) {
            existing.cancel();
        }
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
                .replace("[sleeping]", String.valueOf(sleeping))
                .replace("[players]", String.valueOf(players))
                .replace("[needed]", String.valueOf(needed))
                .replace("[more]", String.valueOf(more)));
    }

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
     * Hides the bossbar for the given world if one is present (unless a transient banner is currently displayed).
     *
     * @param world The world in which to hide the bossbar.
     */
    public void clearBar(@NotNull World world) {
        if (hasActiveTransientBar(world)) {
            return;
        }
        Optional.ofNullable(bossBars.get(world.getUID())).ifPresent(BossBar::removeAll);
    }

    /**
     * Forcefully hides the bossbar and cancels any transient timer (used on plugin disable).
     */
    public void forceClearBar(@NotNull World world) {
        cancelTransientBarTask(world.getUID());
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

package xyz.nkomarn.harbor.task;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.api.ExclusionProvider;
import xyz.nkomarn.harbor.provider.GameModeExclusionProvider;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class Checker extends BukkitRunnable {
    private final Set<ExclusionProvider> providers;
    private final Harbor harbor;
    private final Set<UUID> skippingWorlds;
    private final Set<UUID> forcedWorlds;
    private final Set<UUID> wakingWorlds;
    private final Map<UUID, Integer> savedSleepingPercentages;

    public Checker(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.skippingWorlds = new HashSet<>();
        this.forcedWorlds = new HashSet<>();
        this.wakingWorlds = new HashSet<>();
        this.savedSleepingPercentages = new HashMap<>();
        this.providers = new HashSet<>();

        // GameModeExclusionProvider checks each case on its own
        providers.add(new GameModeExclusionProvider(harbor));

        // The others are simple enough that we can use lambdas
        providers.add(player -> player.hasMetadata("NPC"));
        providers.add(player -> harbor.getConfig().getBoolean("exclusions.ignored-permission", true) && player.hasPermission("harbor.ignored"));
        providers.add(player -> harbor.getConfig().getBoolean("exclusions.exclude-vanished", false) && isVanished(player));
        providers.add(player -> harbor.getConfig().getBoolean("exclusions.exclude-afk", false) && harbor.getPlayerManager().isAfk(player));

        syncAllWorldGameRules();

        int interval = harbor.getConfiguration().getInteger("interval");
        if (interval <= 0) {
            interval = 1;
        }
        runTaskTimer(harbor, 1L, interval * 20L);
    }

    /**
     * Synchronizes PLAYERS_SLEEPING_PERCENTAGE on all loaded worlds so vanilla Minecraft
     * does not abruptly interrupt Harbor's smooth night-skip animation after 100 ticks in bed.
     */
    public void syncAllWorldGameRules() {
        for (World world : Bukkit.getWorlds()) {
            syncWorldGameRule(world);
            if (!isBlacklisted(world) && harbor.getMessages() != null) {
                harbor.getMessages().registerBar(world);
            }
        }
    }

    public void syncWorldGameRule(@NotNull World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        UUID uid = world.getUID();
        boolean managedByHarbor = harbor.getConfiguration().getBoolean("night-skip.enabled") && !isBlacklisted(world);
        if (managedByHarbor) {
            Integer current = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
            if (current != null && current <= 100) {
                savedSleepingPercentages.putIfAbsent(uid, current);
                world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
            }
        } else {
            restoreWorldGameRule(world);
        }
    }

    public void restoreAllWorldGameRules() {
        for (World world : Bukkit.getWorlds()) {
            restoreWorldGameRule(world);
        }
        savedSleepingPercentages.clear();
    }

    private void restoreWorldGameRule(@NotNull World world) {
        Integer previous = savedSleepingPercentages.remove(world.getUID());
        if (previous != null) {
            Integer current = world.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
            if (current != null && current == 101) {
                world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, previous);
            }
        }
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            if (validateWorld(world)) {
                checkWorld(world);
            }
        }
    }

    /**
     * Immediately evaluates a world (called when a player enters or leaves a bed).
     *
     * @param world The world to evaluate.
     */
    public void checkWorldNow(@NotNull World world) {
        if (validateWorld(world)) {
            checkWorld(world);
        }
    }

    /**
     * Checks if a given world is applicable for night/storm skipping.
     *
     * @param world The world to check.
     * @return Whether Harbor should run the night skipping check below.
     */
    private boolean validateWorld(@NotNull World world) {
        if (skippingWorlds.contains(world.getUID()) || isBlacklisted(world)) {
            return false;
        }
        if (!isSleepPossible(world)) {
            Messages messages = harbor.getMessages();
            if (messages != null) {
                messages.clearBar(world);
            }
            return false;
        }
        return true;
    }

    /**
     * Checks if enough people are sleeping, and in the case there are, starts the night skip task.
     *
     * @param world The world to check.
     */
    private void checkWorld(@NotNull World world) {
        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        int sleeping = 0;
        int activePlayers = 0;
        for (Player player : world.getPlayers()) {
            if (player.hasMetadata("NPC")) {
                continue;
            }
            boolean playerSleeping = isPlayerSleeping(player);
            if (playerSleeping) {
                sleeping++;
            }
            if (playerSleeping || !isExcluded(player)) {
                activePlayers++;
            }
        }

        if (sleeping < 1) {
            messages.clearBar(world);
            return;
        }

        int skipAmount = Math.max(1, getSkipAmount(activePlayers));
        int needed = Math.max(0, skipAmount - sleeping);

        if (needed > 0) {
            double sleepingPercentage = Math.min(1.0, (double) sleeping / skipAmount);

            messages.sendActionBarMessage(world, config.getString("messages.actionbar.players-sleeping"));
            messages.sendBossBarMessage(world, config.getString("messages.bossbar.players-sleeping.message"),
                    config.getString("messages.bossbar.players-sleeping.color"), sleepingPercentage);
        } else {
            messages.sendActionBarMessage(world, config.getString("messages.actionbar.night-skipping"));
            messages.sendBossBarMessage(world, config.getString("messages.bossbar.night-skipping.message"),
                    config.getString("messages.bossbar.night-skipping.color"), 1);

            if (!config.getBoolean("night-skip.enabled")) {
                return;
            }

            syncWorldGameRule(world);

            // Daytime thunderstorm: clear weather & wake players without spinning the clock 24h
            if (!isNight(world) && world.isThundering()) {
                skippingWorlds.add(world.getUID());
                Bukkit.getScheduler().runTask(harbor, () -> {
                    if (config.getBoolean("night-skip.reset-phantom-statistic")) {
                        world.getPlayers().forEach(player -> player.setStatistic(Statistic.TIME_SINCE_REST, 0));
                    }
                    clearWeather(world);
                    finishSkip(world);
                });
                return;
            }

            if (config.getBoolean("night-skip.instant-skip")) {
                skippingWorlds.add(world.getUID());
                Bukkit.getScheduler().runTask(harbor, () -> {
                    int dayTime = Math.max(150, config.getInteger("night-skip.daytime-ticks"));
                    long skipAmountTicks = (dayTime - world.getTime() + 24000) % 24000;
                    TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, skipAmountTicks);
                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        world.setTime(world.getTime() + event.getSkipAmount());
                        if (config.getBoolean("night-skip.reset-phantom-statistic")) {
                            world.getPlayers().forEach(player -> player.setStatistic(Statistic.TIME_SINCE_REST, 0));
                        }
                        clearWeather(world);
                        finishSkip(world);
                    } else {
                        skippingWorlds.remove(world.getUID());
                    }
                });
                return;
            }

            skippingWorlds.add(world.getUID());
            forcedWorlds.remove(world.getUID());
            new AccelerateNightTask(harbor, this, world);
        }
    }

    private static boolean isPlayerSleeping(@NotNull Player player) {
        return player.isSleeping() || player.getPose() == Pose.SLEEPING;
    }

    /**
     * Checks if players can currently sleep in the provided world (nighttime or thunderstorm).
     */
    public boolean isSleepPossible(@NotNull World world) {
        return isNight(world) || world.isThundering();
    }

    /**
     * Checks if the time in a given world is considered to be night (matching vanilla bed window 12541..23999).
     *
     * @param world The world to check.
     * @return Whether it is currently night in the provided world.
     */
    public boolean isNight(@NotNull World world) {
        long time = world.getTime();
        return time >= 12541 && time <= 23990;
    }

    /**
     * Checks if a current world has been blacklisted (or whitelisted) in the configuration.
     *
     * @param world The world to check.
     * @return Whether a world is excluded from Harbor checks.
     */
    public boolean isBlacklisted(@NotNull World world) {
        boolean blacklisted = harbor.getConfiguration().getStringList("blacklisted-worlds").contains(world.getName());

        if (harbor.getConfiguration().getBoolean("whitelist-mode")) {
            return !blacklisted;
        }

        return blacklisted;
    }

    /**
     * Checks if a given player is in a vanished state.
     *
     * @param player The player to check.
     * @return Whether the provided player is vanished.
     */
    public static boolean isVanished(@NotNull Player player) {
        for (MetadataValue metadata : player.getMetadata("vanished")) {
            if (metadata.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the amount of players that should be counted for Harbor's checks, ignoring excluded players.
     *
     * @param world The world for which to check player count.
     * @return The amount of players in a given world, minus excluded players.
     */
    public int getPlayers(@NotNull World world) {
        int count = 0;
        for (Player player : world.getPlayers()) {
            if (player.hasMetadata("NPC")) {
                continue;
            }
            if (isPlayerSleeping(player) || !isExcluded(player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a list of all sleeping players in a given world.
     *
     * @param world The world in which to check for sleeping players.
     * @return A list of all currently sleeping players in the provided world.
     */
    @NotNull
    public List<Player> getSleepingPlayers(@NotNull World world) {
        return world.getPlayers().stream()
                .filter(player -> !player.hasMetadata("NPC") && isPlayerSleeping(player))
                .collect(toList());
    }

    /**
     * Returns the amount of players that must be sleeping to skip the night in the given world.
     *
     * @param world The world for which to check skip amount.
     * @return The amount of players that need to sleep to skip the night.
     */
    public int getSkipAmount(@NotNull World world) {
        return getSkipAmount(getPlayers(world));
    }

    /**
     * Returns the amount of players that must be sleeping to skip the night for a given active player count.
     *
     * @param activePlayers The number of non-excluded players in the world.
     * @return The amount of players that need to sleep to skip the night.
     */
    public int getSkipAmount(int activePlayers) {
        return Math.max(1, (int) Math.ceil(activePlayers * (harbor.getConfiguration().getDouble("night-skip.percentage") / 100)));
    }

    /**
     * Returns the amount of players that are still needed to skip the night in a given world.
     *
     * @param world The world for which to check the amount of needed players.
     * @return The amount of players that still need to get into bed to start the night skipping task.
     */
    public int getNeeded(@NotNull World world) {
        return Math.max(0, getSkipAmount(world) - getSleepingPlayers(world).size());
    }

    /**
     * Checks if a given player is considered excluded from Harbor's checks.
     *
     * @param player The player to check.
     * @return Whether the given player is excluded.
     */
    private boolean isExcluded(@NotNull Player player) {
        for (ExclusionProvider provider : providers) {
            if (provider.isExcluded(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the night is currently being skipped in the given world.
     *
     * @param world The world to check.
     * @return Whether the night is currently skipping in the provided world.
     */
    public boolean isSkipping(@NotNull World world) {
        return skippingWorlds.contains(world.getUID());
    }

    public boolean isForcedSkip(@NotNull World world) {
        return forcedWorlds.contains(world.getUID());
    }

    public boolean isWakingUp(@NotNull World world) {
        return wakingWorlds.contains(world.getUID());
    }

    /**
     * Forces a world to begin skipping the night, skipping over the player sleep count checks.
     *
     * @param world The world in which to force night skipping.
     */
    public void forceSkip(@NotNull World world) {
        if (!isNight(world) && world.hasStorm()) {
            skippingWorlds.add(world.getUID());
            clearWeather(world);
            finishSkip(world);
            return;
        }
        skippingWorlds.add(world.getUID());
        forcedWorlds.add(world.getUID());
        new AccelerateNightTask(harbor, this, world);
    }

    /**
     * Cancels an in-progress night skip when players leave their beds.
     *
     * @param world The world in which night skipping was cancelled.
     */
    public void cancelSkip(@NotNull World world) {
        UUID uid = world.getUID();
        skippingWorlds.remove(uid);
        forcedWorlds.remove(uid);

        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        messages.clearBar(world);
        messages.sendWorldTitle(
                world,
                config.getString("messages.title.skip-cancelled.title"),
                config.getString("messages.title.skip-cancelled.subtitle"),
                5, 45, 15
        );
        messages.sendWorldChatMessage(world, config.getString("messages.chat.skip-cancelled"));

        // Re-evaluate world state so BossBar reflects any remaining sleeping players
        checkWorldNow(world);
    }

    /**
     * Completes a night skip, wakes up players cleanly, clears bars, and sends morning notifications.
     *
     * @param world The world for which to complete the skip.
     */
    public void finishSkip(@NotNull World world) {
        UUID uid = world.getUID();
        wakingWorlds.add(uid);
        wakeUpPlayers(world);

        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();
        messages.clearBar(world);
        messages.sendWorldTitle(
                world,
                config.getString("messages.title.night-skipped.title"),
                config.getString("messages.title.night-skipped.subtitle"),
                10, 50, 20
        );

        harbor.getServer().getScheduler().runTaskLater(harbor, () -> {
            skippingWorlds.remove(uid);
            forcedWorlds.remove(uid);
            wakingWorlds.remove(uid);
            harbor.getPlayerManager().clearCooldowns();
            messages.clearBar(world);
            messages.sendRandomChatMessage(world, "messages.chat.night-skipped");
        }, 20L);
    }

    /**
     * Alias kept for backwards API compatibility.
     */
    public void resetStatus(@NotNull World world) {
        finishSkip(world);
    }

    /**
     * Kicks all sleeping players out of bed in the provided world.
     *
     * @param world The world for which to kick players out of bed.
     */
    public void wakeUpPlayers(@NotNull World world) {
        ensureMain(() -> world.getPlayers().stream()
                .filter(LivingEntity::isSleeping)
                .forEach(player -> player.wakeup(true)));
    }

    /**
     * Resets the weather states in the provided world.
     *
     * @param world The world for which to clear weather.
     */
    public void clearWeather(@NotNull World world) {
        ensureMain(() -> {
            Config config = harbor.getConfiguration();

            if (world.hasStorm() && config.getBoolean("night-skip.clear-rain")) {
                world.setStorm(false);
            }

            if (world.isThundering() && config.getBoolean("night-skip.clear-thunder")) {
                world.setThundering(false);
            }
        });
    }

    /**
     * Ensures the provided task is ran on the server thread.
     *
     * @param runnable The task to run on the server thread.
     */
    public void ensureMain(@NotNull Runnable runnable) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(harbor, runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Adds an {@link ExclusionProvider}, which will be checked as a condition. All Exclusions will be ORed together
     * on which to exclude a given player
     */
    public void addExclusionProvider(ExclusionProvider provider) {
        providers.add(provider);
    }

    /**
     * Removes an {@link ExclusionProvider}
     */
    public void removeExclusionProvider(ExclusionProvider provider) {
        providers.remove(provider);
    }
}

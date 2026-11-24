package xyz.nkomarn.harbor.task;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

public class AccelerateNightTask extends BukkitRunnable {

    private final Harbor harbor;
    private final Checker checker;
    private final World world;
    private int tickCounter = 0;

    public AccelerateNightTask(@NotNull Harbor harbor, @NotNull Checker checker, @NotNull World world) {
        this.harbor = harbor;
        this.checker = checker;
        this.world = world;

        harbor.getMessages().sendRandomChatMessage(world, "messages.chat.night-skipping");
        runTaskTimer(harbor, 1L, 1L);
    }

    @Override
    public void run() {
        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        // Cancel night acceleration if players left their beds and threshold is no longer met (unless /forceskip)
        if (!checker.isForcedSkip(world) && config.getBoolean("night-skip.cancel-on-wake")) {
            if (checker.getNeeded(world) > 0 || checker.getSleepingPlayers(world).isEmpty()) {
                checker.cancelSkip(world);
                cancel();
                return;
            }
        }

        long time = world.getTime();
        double timeRate = Math.max(1, config.getInteger("night-skip.time-rate"));
        int dayTime = Math.max(0, Math.min(23999, config.getInteger("night-skip.daytime-ticks")));
        int sleeping = checker.getSleepingPlayers(world).size();

        if (config.getBoolean("night-skip.proportional-acceleration")) {
            int playerCount = world.getPlayers().size();
            if (playerCount > 0) {
                timeRate = Math.max(1, Math.min(timeRate, Math.round(timeRate / playerCount * Math.max(1, sleeping))));
            }
        }

        long step = (long) timeRate;
        long remainingToDay = (dayTime - time + 24000) % 24000;

        // If we reached or are about to reach/cross dayTime on this step, finish cleanly!
        if (remainingToDay <= step || (!checker.isNight(world) && time >= dayTime && time < 12541)) {
            if (remainingToDay > 0 && remainingToDay <= step) {
                TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, remainingToDay);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    world.setTime(dayTime);
                }
            } else {
                world.setTime(dayTime);
            }

            if (config.getBoolean("night-skip.reset-phantom-statistic")) {
                world.getPlayers().forEach(player -> player.setStatistic(Statistic.TIME_SINCE_REST, 0));
            }

            checker.clearWeather(world);
            checker.finishSkip(world);
            cancel();
            return;
        }

        TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, step);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            world.setTime(time + event.getSkipAmount());
        }

        // Update live center-screen clock & bossbar smoothly every 2 ticks without fade-in flicker
        if (tickCounter++ % 2 == 0) {
            messages.sendWorldTitle(
                    world,
                    config.getString("messages.title.night-skipping.title"),
                    config.getString("messages.title.night-skipping.subtitle"),
                    0, 15, 5
            );
            messages.sendBossBarMessage(
                    world,
                    config.getString("messages.bossbar.night-skipping.message"),
                    config.getString("messages.bossbar.night-skipping.color"),
                    1.0
            );
        }
    }
}

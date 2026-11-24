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
    private final long totalTicksToSkip;
    private int tickCounter = 0;

    public AccelerateNightTask(@NotNull Harbor harbor, @NotNull Checker checker, @NotNull World world) {
        this.harbor = harbor;
        this.checker = checker;
        this.world = world;

        int dayTime = Math.max(0, Math.min(23999, harbor.getConfiguration().getInteger("night-skip.daytime-ticks")));
        this.totalTicksToSkip = Math.max(1L, (dayTime - world.getTime() + 24000) % 24000);

        checker.clearWeather(world);
        runTaskTimer(harbor, 1L, 1L);
    }

    @Override
    public void run() {
        Config config = harbor.getConfiguration();
        Messages messages = harbor.getMessages();

        long time = world.getTime();

        // Only allow cancel-on-wake while it is still night (12541..23457), before vanilla dawn auto-wakes players at 23458+
        if (!checker.isForcedSkip(world)
                && config.getBoolean("night-skip.cancel-on-wake")
                && checker.isNight(world)) {
            if (checker.getNeeded(world) > 0 || checker.getSleepingPlayers(world).isEmpty()) {
                checker.cancelSkip(world);
                cancel();
                return;
            }
        }

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

        // Finish cleanly as soon as we reach or cross dayTime
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

        long updatedRemaining = (dayTime - world.getTime() + 24000) % 24000;
        double progress = Math.max(0.02, Math.min(1.0, 1.0 - ((double) updatedRemaining / totalTicksToSkip)));
        int progressPercent = (int) Math.round(progress * 100.0);

        // Update BossBar smooth progress on every tick and ActionBar every 2 ticks
        messages.sendBossBarMessage(
                world,
                config.getString("messages.bossbar.night-skipping.message"),
                config.getString("messages.bossbar.night-skipping.color"),
                progress
        );
        if (tickCounter++ % 2 == 0) {
            messages.sendActionBarMessage(
                    world,
                    null,
                    config.getString("messages.actionbar.night-skipping"),
                    progressPercent
            );
        }
    }
}

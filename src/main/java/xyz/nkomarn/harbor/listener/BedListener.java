package xyz.nkomarn.harbor.listener;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.task.Checker;

public class BedListener implements Listener {

    private final Harbor harbor;

    public BedListener(@NotNull Harbor harbor) {
        this.harbor = harbor;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        Checker checker = harbor.getChecker();

        if (checker.isBlacklisted(world) || Checker.isVanished(player) || player.hasMetadata("NPC")) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(harbor, () -> {
            if (!player.isOnline()) {
                return;
            }
            // Immediately update BossBar / start night skip if threshold is reached
            checker.checkWorldNow(world);
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        Checker checker = harbor.getChecker();

        if (checker.isBlacklisted(world) || Checker.isVanished(player) || player.hasMetadata("NPC")) {
            return;
        }

        // Ignore natural morning wakeups (tick >= 23458 or daytime) or wakeups triggered by Harbor finishing the skip
        if (checker.isWakingUp(world) || !checker.isSleepPossible(world)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(harbor, () -> {
            if (!player.isOnline() || checker.isWakingUp(world) || !checker.isSleepPossible(world)) {
                return;
            }
            if (checker.isSkipping(world)) {
                return;
            }
            checker.checkWorldNow(world);
        }, 1L);
    }
}

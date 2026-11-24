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
import xyz.nkomarn.harbor.util.Config;
import xyz.nkomarn.harbor.util.Messages;

public class BedListener implements Listener {

    private final Harbor harbor;
    private final Messages messages;

    public BedListener(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.messages = harbor.getMessages();
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

            Config config = harbor.getConfiguration();

            // Notify all players in the world via subtle ActionBar above the hotbar
            messages.sendActionBarMessage(world, player, config.getString("messages.actionbar.player-sleeping"), 0);

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

            Config config = harbor.getConfiguration();

            // Notify all players in the world via subtle ActionBar above the hotbar
            messages.sendActionBarMessage(world, player, config.getString("messages.actionbar.player-left-bed"), 0);

            // If night was currently accelerating, AccelerateNightTask handles the cancellation BossBar
            if (checker.isSkipping(world)) {
                return;
            }

            checker.checkWorldNow(world);
        }, 1L);
    }
}

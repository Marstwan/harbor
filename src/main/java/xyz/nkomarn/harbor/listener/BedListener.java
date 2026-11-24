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
import xyz.nkomarn.harbor.util.PlayerManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class BedListener implements Listener {

    private final Harbor harbor;
    private final Messages messages;
    private final PlayerManager playerManager;

    public BedListener(@NotNull Harbor harbor) {
        this.harbor = harbor;
        this.messages = harbor.getMessages();
        this.playerManager = harbor.getPlayerManager();
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

            // Send subtle ActionBar notification to all players in the world showing who got in bed
            messages.sendActionBarMessage(world, player, config.getString("messages.actionbar.player-sleeping"), 0);

            // Send classic Chat notification (respecting cooldown) BEFORE starting skip so everyone sees who slept
            if (!checker.isSkipping(world) && !isChatCooldownActive(player)) {
                playerManager.setCooldown(player, Instant.now());
                String raw = config.getString("messages.chat.player-sleeping");
                if (config.getBoolean("messages.chat.enabled") && !raw.isEmpty()) {
                    String prepared = messages.prepareMessage(world, player, raw);
                    world.getPlayers().forEach(p -> p.sendMessage(prepared));
                }
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

        // Ignore natural morning wakeups or wakeups triggered by Harbor finishing the skip
        if (checker.isWakingUp(world) || (!checker.isSkipping(world) && !checker.isSleepPossible(world))) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(harbor, () -> {
            if (!player.isOnline() || checker.isWakingUp(world)) {
                return;
            }

            Config config = harbor.getConfiguration();

            // Send subtle ActionBar notification to everyone in the world showing who left bed
            messages.sendActionBarMessage(world, player, config.getString("messages.actionbar.player-left-bed"), 0);

            // If night was currently accelerating, AccelerateNightTask handles the cancellation banner & chat message
            if (checker.isSkipping(world)) {
                return;
            }

            checker.checkWorldNow(world);

            if (!isChatCooldownActive(player)) {
                playerManager.setCooldown(player, Instant.now());
                String raw = config.getString("messages.chat.player-left-bed");
                if (config.getBoolean("messages.chat.enabled") && !raw.isEmpty()) {
                    String prepared = messages.prepareMessage(world, player, raw);
                    world.getPlayers().forEach(p -> p.sendMessage(prepared));
                }
            }
        }, 1L);
    }

    private boolean isChatCooldownActive(@NotNull Player player) {
        int cooldown = harbor.getConfiguration().getInteger("messages.chat.message-cooldown");
        return playerManager.getCooldown(player).until(Instant.now(), ChronoUnit.SECONDS) < cooldown;
    }
}

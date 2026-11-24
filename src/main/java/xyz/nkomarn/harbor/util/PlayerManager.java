package xyz.nkomarn.harbor.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.harbor.Harbor;
import xyz.nkomarn.harbor.api.AFKProvider;
import xyz.nkomarn.harbor.api.LogicType;
import xyz.nkomarn.harbor.provider.DefaultAFKProvider;
import xyz.nkomarn.harbor.provider.EssentialsAFKProvider;

import java.util.HashSet;
import java.util.Set;

public class PlayerManager {
    private final Set<AFKProvider> andedProviders;
    private final Set<AFKProvider> oredProviders;
    private final DefaultAFKProvider defaultProvider;

    public PlayerManager(@NotNull Harbor harbor) {
        this.andedProviders = new HashSet<>();
        this.oredProviders = new HashSet<>();
        this.defaultProvider = new DefaultAFKProvider(harbor);

        updateListeners();
        if (harbor.getEssentials().isPresent()) {
            addAfkProvider(new EssentialsAFKProvider(harbor, harbor.getEssentials().get()),
                    LogicType.fromConfig(harbor.getConfig(), "afk-detection.essentials-detection-mode", LogicType.AND));
        } else {
            harbor.getLogger().info("Essentials not present - not registering Essentials integration");
        }
    }

    /**
     * Checks if a player is considered "AFK" for Harbor's player checks.
     *
     * @param player The player to check.
     * @return Whether the player is considered AFK.
     */
    public boolean isAfk(@NotNull Player player) {
        // If there are no providers registered, we go to the default provider
        if (oredProviders.isEmpty() && andedProviders.isEmpty()) {
            return defaultProvider.isAFK(player);
        }
        return oredProviders.stream().anyMatch(provider -> provider.isAFK(player)) ||
                (!andedProviders.isEmpty() && andedProviders.stream().allMatch(provider -> provider.isAFK(player)));
    }

    /**
     * Add an AFK Provider to harbor, so an external plugin can provide an AFK status to harbor
     *
     * @param provider  The {@link AFKProvider} to be added
     * @param logicType The type of logic (And or Or, {@link LogicType}) to be used with the given provider
     */
    public void addAfkProvider(@NotNull AFKProvider provider, @NotNull LogicType logicType) {
        switch (logicType) {
            case AND -> andedProviders.add(provider);
            case OR -> oredProviders.add(provider);
        }
        updateListeners();
    }

    /**
     * Remove an AFK provider from Harbor, provided for external plugins.
     *
     * @param provider the {@link AFKProvider} to be removed.
     */
    public void removeAfkProvider(@NotNull AFKProvider provider) {
        andedProviders.remove(provider);
        oredProviders.remove(provider);
        updateListeners();
    }

    private void updateListeners() {
        if (andedProviders.isEmpty() && oredProviders.isEmpty()) {
            defaultProvider.enableListeners();
        } else {
            defaultProvider.disableListeners();
        }
    }
}

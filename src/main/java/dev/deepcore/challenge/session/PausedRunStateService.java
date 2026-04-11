package dev.deepcore.challenge.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/** Captures and restores player run state when pausing to lobby. */
public final class PausedRunStateService {
    private final double defaultMaxHealth;
    private final Map<UUID, PausedPlayerSnapshot> pausedSnapshots = new HashMap<>();

    /**
     * Creates a paused-run state service.
     *
     * @param defaultMaxHealth default max-health baseline used for snapshot restore
     */
    public PausedRunStateService(double defaultMaxHealth) {
        this.defaultMaxHealth = defaultMaxHealth;
    }

    /** Clears all stored paused-run player snapshots. */
    public void clearSnapshots() {
        pausedSnapshots.clear();
    }

    /**
     * Captures a player's current run state and optionally teleports to lobby.
     *
     * @param player             player whose state should be captured
     * @param teleportToLobby    true to teleport player to lobby after snapshot
     * @param lobbySpawnSupplier supplier for lobby spawn location
     */
    public void stashRunStateForLobby(Player player, boolean teleportToLobby, Supplier<Location> lobbySpawnSupplier) {
        pausedSnapshots.put(player.getUniqueId(), PausedPlayerSnapshot.capture(player, defaultMaxHealth));
        clearInventoryForLobby(player);

        if (teleportToLobby) {
            Location lobbySpawn = lobbySpawnSupplier.get();
            if (lobbySpawn != null) {
                player.teleport(lobbySpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    /**
     * Applies a previously captured snapshot to the player when available.
     *
     * @param player player whose snapshot should be restored
     */
    public void applySnapshotIfPresent(Player player) {
        PausedPlayerSnapshot snapshot = pausedSnapshots.get(player.getUniqueId());
        if (snapshot != null) {
            snapshot.applyTo(player);
        }
    }

    private void clearInventoryForLobby(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[1]);
        player.setItemOnCursor(null);
        player.updateInventory();
    }

    private record PausedPlayerSnapshot(
            Location location,
            ItemStack[] storageContents,
            ItemStack[] armorContents,
            ItemStack[] extraContents,
            double health,
            double maxHealth,
            int foodLevel,
            float saturation,
            float exhaustion,
            int fireTicks,
            int remainingAir,
            int level,
            float exp,
            int totalExperience,
            GameMode gameMode,
            boolean allowFlight,
            boolean flying,
            List<PotionEffect> potionEffects) {
        private static PausedPlayerSnapshot capture(Player player, double defaultMaxHealth) {
            PlayerInventory inventory = player.getInventory();
            double maxHealth = defaultMaxHealth;
            AttributeInstance attribute = player.getAttribute(resolveMaxHealthAttribute());
            if (attribute != null) {
                maxHealth = attribute.getBaseValue();
            }

            return new PausedPlayerSnapshot(
                    player.getLocation().clone(),
                    cloneContents(inventory.getStorageContents()),
                    cloneContents(inventory.getArmorContents()),
                    cloneContents(inventory.getExtraContents()),
                    player.getHealth(),
                    maxHealth,
                    player.getFoodLevel(),
                    player.getSaturation(),
                    player.getExhaustion(),
                    player.getFireTicks(),
                    player.getRemainingAir(),
                    player.getLevel(),
                    player.getExp(),
                    player.getTotalExperience(),
                    player.getGameMode(),
                    player.getAllowFlight(),
                    player.isFlying(),
                    new ArrayList<>(player.getActivePotionEffects()));
        }

        private void applyTo(Player player) {
            if (location.getWorld() != null) {
                player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            PlayerInventory inventory = player.getInventory();
            inventory.setStorageContents(cloneContents(storageContents));
            inventory.setArmorContents(cloneContents(armorContents));
            inventory.setExtraContents(cloneContents(extraContents));

            AttributeInstance attribute = player.getAttribute(resolveMaxHealthAttribute());
            if (attribute != null && attribute.getBaseValue() != maxHealth) {
                attribute.setBaseValue(maxHealth);
            }

            double clampedHealth = Math.max(0.0D, Math.min(maxHealth, health));
            player.setHealth(clampedHealth <= 0.0D ? 0.5D : clampedHealth);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setFireTicks(fireTicks);
            player.setRemainingAir(remainingAir);
            player.setLevel(level);
            player.setExp(exp);
            player.setTotalExperience(totalExperience);
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(flying);

            for (PotionEffect active : player.getActivePotionEffects()) {
                player.removePotionEffect(active.getType());
            }
            for (PotionEffect effect : potionEffects) {
                player.addPotionEffect(effect);
            }

            player.updateInventory();
        }

        private static ItemStack[] cloneContents(ItemStack[] contents) {
            ItemStack[] cloned = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                cloned[i] = contents[i] == null ? null : contents[i].clone();
            }
            return cloned;
        }

        private static Attribute resolveMaxHealthAttribute() {
            try {
                return (Attribute) Attribute.class.getField("MAX_HEALTH").get(null);
            } catch (ReflectiveOperationException ignored) {
                try {
                    return (Attribute)
                            Attribute.class.getField("GENERIC_MAX_HEALTH").get(null);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("MAX_HEALTH attribute constant not found.", ex);
                }
            }
        }
    }
}

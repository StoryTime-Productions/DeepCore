package dev.deepcore.challenge.vitals;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Synchronizes team health and hunger state for shared-vitals challenge mode.
 */
public final class SharedVitalsService {
    private final JavaPlugin plugin;
    private final Supplier<List<Player>> playersForSharedVitals;

    private boolean syncingHealth;
    private boolean syncingHunger;

    /**
     * Creates a shared-vitals synchronizer.
     *
     * @param plugin                 plugin instance used for scheduler tasks
     * @param playersForSharedVitals supplier providing current shared-vitals
     *                               participants
     */
    public SharedVitalsService(JavaPlugin plugin, Supplier<List<Player>> playersForSharedVitals) {
        this.plugin = plugin;
        this.playersForSharedVitals = playersForSharedVitals;
    }

    /**
     * Returns whether a health sync task is currently applying state.
     *
     * @return true while health synchronization is in progress
     */
    public boolean isSyncingHealth() {
        return syncingHealth;
    }

    /**
     * Returns whether a hunger sync task is currently applying state.
     *
     * @return true while hunger synchronization is in progress
     */
    public boolean isSyncingHunger() {
        return syncingHunger;
    }

    /** Resets internal sync guard flags. */
    public void resetSyncFlags() {
        syncingHealth = false;
        syncingHunger = false;
    }

    /**
     * Syncs team health to a target value without damage effects.
     *
     * @param targetHealth target health value to apply to non-spectator
     *                     participants
     */
    public void syncHealthAcrossParticipants(double targetHealth) {
        syncHealthAcrossParticipants(targetHealth, null, false);
    }

    /**
     * Syncs team health to a target value with optional hurt feedback.
     *
     * @param targetHealth    target health value to apply to non-spectator
     *                        participants
     * @param damageSourceId  source player UUID for optional hurt feedback
     *                        filtering
     * @param applyHurtEffect true to play hurt effects for affected non-source
     *                        participants
     */
    public void syncHealthAcrossParticipants(double targetHealth, UUID damageSourceId, boolean applyHurtEffect) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncingHealth = true;
            try {
                for (Player participant : playersForSharedVitals.get()) {
                    if (participant.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    double previousHealth = participant.getHealth();
                    double maxHealth = participant
                            .getAttribute(resolveMaxHealthAttribute())
                            .getValue();
                    double clamped = Math.max(0.0D, Math.min(maxHealth, targetHealth));
                    if (clamped <= 0.0D) {
                        participant.setHealth(0.0D);
                    } else {
                        participant.setHealth(clamped);
                    }

                    if (applyHurtEffect
                            && damageSourceId != null
                            && !participant.getUniqueId().equals(damageSourceId)
                            && clamped < previousHealth) {
                        participant.playHurtAnimation(participant.getLocation().getYaw());
                        participant.playSound(
                                participant.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    }
                }
            } finally {
                syncingHealth = false;
            }
        });
    }

    /** Copies health from the first active participant to the entire team. */
    public void syncSharedHealthFromFirstParticipant() {
        List<Player> onlineParticipants = playersForSharedVitals.get();
        for (Player participant : onlineParticipants) {
            if (participant.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            syncHealthAcrossParticipants(participant.getHealth());
            return;
        }
    }

    /**
     * Syncs team hunger and saturation to the supplied target values.
     *
     * @param targetFoodLevel  target food level to apply
     * @param targetSaturation target saturation value to apply
     */
    public void syncHungerAcrossParticipants(int targetFoodLevel, float targetSaturation) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            syncingHunger = true;
            try {
                int clampedFood = Math.max(0, Math.min(20, targetFoodLevel));
                float clampedSaturation = Math.max(0.0F, Math.min(20.0F, targetSaturation));
                for (Player participant : playersForSharedVitals.get()) {
                    if (participant.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    participant.setFoodLevel(clampedFood);
                    participant.setSaturation(clampedSaturation);
                }
            } finally {
                syncingHunger = false;
            }
        });
    }

    /** Syncs team hunger from the most-filled active participant. */
    public void syncSharedHungerFromMostFilledParticipant() {
        int maxFood = -1;
        float maxSaturation = 0.0F;
        boolean found = false;

        for (Player participant : playersForSharedVitals.get()) {
            if (participant.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            found = true;
            if (participant.getFoodLevel() > maxFood) {
                maxFood = participant.getFoodLevel();
            }
            if (participant.getSaturation() > maxSaturation) {
                maxSaturation = participant.getSaturation();
            }
        }

        if (found) {
            syncHungerAcrossParticipants(maxFood, maxSaturation);
        }
    }

    /**
     * Derives shared hunger targets from a food-level event and applies sync.
     *
     * @param source         player whose food-level change triggered this sync
     * @param eventFoodLevel proposed food level from the event
     */
    public void syncHungerFromFoodLevelChange(Player source, int eventFoodLevel) {
        int currentFood = source.getFoodLevel();
        int proposedFood = Math.max(0, Math.min(20, eventFoodLevel));
        boolean hungerIncreased = proposedFood > currentFood;

        int targetFoodLevel = proposedFood;
        float targetSaturation = source.getSaturation();
        for (Player participant : playersForSharedVitals.get()) {
            if (participant.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            if (hungerIncreased) {
                targetFoodLevel = Math.max(targetFoodLevel, participant.getFoodLevel());
                targetSaturation = Math.max(targetSaturation, participant.getSaturation());
            } else {
                targetFoodLevel = Math.min(targetFoodLevel, participant.getFoodLevel());
                targetSaturation = Math.min(targetSaturation, participant.getSaturation());
            }
        }

        syncHungerAcrossParticipants(targetFoodLevel, targetSaturation);
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

package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.vitals.SharedVitalsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Coordinates shared-vitals event handling and initial-half-heart health state.
 */
public final class RunHealthCoordinatorService {
    private final ChallengeManager challengeManager;
    private final Supplier<SharedVitalsService> sharedVitalsServiceSupplier;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Predicate<Player> isChallengeActive;
    private final BooleanSupplier degradingInventoryEnabled;
    private final Consumer<Player> clearLockedBarrierSlots;
    private final double halfHeartHealth;
    private final double defaultMaxHealth;

    private final Map<UUID, Double> initialHalfHeartOriginalMaxHealth = new HashMap<>();

    /**
     * Creates a run-health coordination service for shared vitals and half-heart
     * rules.
     *
     * @param challengeManager            challenge settings and component manager
     * @param sharedVitalsServiceSupplier supplier for shared vitals service
     * @param onlineParticipantsSupplier  supplier for currently online participants
     * @param isChallengeActive           predicate testing participant challenge
     *                                    activity
     * @param degradingInventoryEnabled   supplier indicating degrading mode state
     * @param clearLockedBarrierSlots     consumer clearing locked barrier slots
     * @param halfHeartHealth             configured half-heart health value
     * @param defaultMaxHealth            default max-health base value
     */
    public RunHealthCoordinatorService(
            ChallengeManager challengeManager,
            Supplier<SharedVitalsService> sharedVitalsServiceSupplier,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Predicate<Player> isChallengeActive,
            BooleanSupplier degradingInventoryEnabled,
            Consumer<Player> clearLockedBarrierSlots,
            double halfHeartHealth,
            double defaultMaxHealth) {
        this.challengeManager = challengeManager;
        this.sharedVitalsServiceSupplier = sharedVitalsServiceSupplier;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.isChallengeActive = isChallengeActive;
        this.degradingInventoryEnabled = degradingInventoryEnabled;
        this.clearLockedBarrierSlots = clearLockedBarrierSlots;
        this.halfHeartHealth = halfHeartHealth;
        this.defaultMaxHealth = defaultMaxHealth;
    }

    /**
     * Handles health regain events and applies configured shared-health policies.
     *
     * @param event health regain event to process
     */
    public void handleEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (sharedVitalsService == null || !isChallengeActive.test(player) || sharedVitalsService.isSyncingHealth()) {
            return;
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)) {
            event.setCancelled(true);
            return;
        }

        if (!challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL)) {
            event.setCancelled(true);
            return;
        }

        if (challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            event.setCancelled(true);
            double base = player.getHealth();
            double target = base + event.getAmount();
            sharedVitalsService.syncHealthAcrossParticipants(target);
        }
    }

    /**
     * Handles damage events and broadcasts resulting shared-health updates.
     *
     * @param event damage event to process
     */
    public void handleEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!isChallengeActive.test(player)) {
            return;
        }

        if (degradingInventoryEnabled.getAsBoolean()) {
            double remainingHealth = player.getHealth() - event.getFinalDamage();
            if (remainingHealth <= 0.0D) {
                clearLockedBarrierSlots.accept(player);
            }
        }

        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (sharedVitalsService == null || sharedVitalsService.isSyncingHealth()) {
            return;
        }

        if (!challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            return;
        }

        double target = Math.max(0.0D, player.getHealth() - event.getFinalDamage());
        sharedVitalsService.syncHealthAcrossParticipants(target, player.getUniqueId(), true);
    }

    /**
     * Handles hunger changes and synchronizes food state across participants.
     *
     * @param event food level change event to process
     */
    public void handleFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (sharedVitalsService == null || !isChallengeActive.test(player) || sharedVitalsService.isSyncingHunger()) {
            return;
        }

        if (!challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            return;
        }

        sharedVitalsService.syncHungerFromFoodLevelChange(player, event.getFoodLevel());
    }

    /**
     * Applies shared health and hunger baselines when shared vitals are enabled.
     */
    public void applySharedVitalsIfEnabled() {
        if (!challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)) {
            return;
        }

        syncSharedHealthFromFirstParticipant();
        syncSharedHungerFromMostFilledParticipant();
    }

    /** Synchronizes shared health from the first active participant snapshot. */
    public void syncSharedHealthFromFirstParticipant() {
        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (sharedVitalsService != null) {
            sharedVitalsService.syncSharedHealthFromFirstParticipant();
        }
    }

    /**
     * Synchronizes shared hunger from the participant with the highest food level.
     */
    public void syncSharedHungerFromMostFilledParticipant() {
        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (sharedVitalsService != null) {
            sharedVitalsService.syncSharedHungerFromMostFilledParticipant();
        }
    }

    /** Applies initial half-heart rules to all online participants when enabled. */
    public void applyInitialHalfHeartIfEnabled() {
        if (!challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)) {
            return;
        }

        for (Player participant : onlineParticipantsSupplier.get()) {
            applyInitialHalfHeart(participant);
        }
    }

    /**
     * Applies initial half-heart max-health and health values to a player.
     *
     * @param player participant to apply half-heart values to
     */
    public void applyInitialHalfHeart(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        applyInitialHalfHeartMaxHealth(player);

        AttributeInstance maxHealthAttribute = player.getAttribute(resolveMaxHealthAttribute());
        if (maxHealthAttribute == null) {
            return;
        }

        double maxHealth = maxHealthAttribute.getValue();
        double targetHealth = Math.max(0.5D, Math.min(maxHealth, halfHeartHealth));

        SharedVitalsService sharedVitalsService = sharedVitalsServiceSupplier.get();
        if (challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH) && sharedVitalsService != null) {
            sharedVitalsService.syncHealthAcrossParticipants(targetHealth);
            return;
        }

        player.setHealth(targetHealth);
    }

    /**
     * Restores the player's original max health after half-heart mode ends.
     *
     * @param player participant whose max health should be restored
     */
    public void restoreDefaultMaxHealth(Player player) {
        AttributeInstance maxHealthAttribute = player.getAttribute(resolveMaxHealthAttribute());
        if (maxHealthAttribute == null) {
            return;
        }

        double restored = initialHalfHeartOriginalMaxHealth.getOrDefault(player.getUniqueId(), defaultMaxHealth);
        if (maxHealthAttribute.getBaseValue() != restored) {
            maxHealthAttribute.setBaseValue(restored);
        }
        initialHalfHeartOriginalMaxHealth.remove(player.getUniqueId());
        player.setHealth(maxHealthAttribute.getValue());
    }

    private void applyInitialHalfHeartMaxHealth(Player player) {
        AttributeInstance maxHealthAttribute = player.getAttribute(resolveMaxHealthAttribute());
        if (maxHealthAttribute == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        initialHalfHeartOriginalMaxHealth.putIfAbsent(playerId, maxHealthAttribute.getBaseValue());
        if (maxHealthAttribute.getBaseValue() != halfHeartHealth) {
            maxHealthAttribute.setBaseValue(halfHeartHealth);
        }
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

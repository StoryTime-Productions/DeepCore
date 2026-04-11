package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.vitals.SharedVitalsService;
import java.util.List;
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
import org.junit.jupiter.api.Test;

class RunHealthCoordinatorServiceTest {

    @Test
    void handleEntityRegainHealth_nonPlayerEntity_isIgnored() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, player -> true, () -> false, player -> {}, 1.0D, 20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(mock(Entity.class));

        service.handleEntityRegainHealth(event);

        verify(event, never()).setCancelled(true);
        verify(sharedVitals, never()).syncHealthAcrossParticipants(anyDouble());
    }

    @Test
    void handleEntityRegainHealth_initialHalfHeartCancelsRefill() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityRegainHealth(event);

        verify(event).setCancelled(true);
        verify(sharedVitals, never()).syncHealthAcrossParticipants(anyDouble());
    }

    @Test
    void handleEntityRegainHealth_healthRefillDisabled_cancels() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL))
                .thenReturn(false);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityRegainHealth(event);

        verify(event).setCancelled(true);
    }

    @Test
    void handleEntityRegainHealth_sharedHealth_syncsTargetHealth() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL))
                .thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);
        when(player.getHealth()).thenReturn(8.0D);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getAmount()).thenReturn(1.5D);

        service.handleEntityRegainHealth(event);

        verify(event).setCancelled(true);
        verify(sharedVitals).syncHealthAcrossParticipants(9.5D);
    }

    @Test
    void handleEntityRegainHealth_ignoresWhenSharedVitalsServiceIsMissing() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        Player player = mock(Player.class);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> null, () -> List.of(player), p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityRegainHealth(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleEntityRegainHealth_ignoresWhenParticipantIsNotActive() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> false,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityRegainHealth(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleEntityRegainHealth_ignoresWhenHealthSyncIsInProgress() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(sharedVitals.isSyncingHealth()).thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityRegainHealth(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void handleEntityDamage_clearsLockedSlotsOnLethalDamageWhenDegradingEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        Consumer<Player> clearLockedSlots = mock(Consumer.class);

        when(player.getHealth()).thenReturn(4.0D);
        when(player.getUniqueId()).thenReturn(playerId);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(false);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> true,
                clearLockedSlots,
                1.0D,
                20.0D);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(6.0D);

        service.handleEntityDamage(event);

        verify(clearLockedSlots).accept(player);
        verify(sharedVitals, never())
                .syncHealthAcrossParticipants(
                        anyDouble(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void handleEntityDamage_sharedHealthSyncsWithNonNegativeTarget() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();

        when(player.getHealth()).thenReturn(3.0D);
        when(player.getUniqueId()).thenReturn(playerId);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(10.0D);

        service.handleEntityDamage(event);

        verify(sharedVitals).syncHealthAcrossParticipants(0.0D, playerId, true);
    }

    @Test
    void handleEntityDamage_ignoresWhenParticipantIsNotActive() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> false,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);

        service.handleEntityDamage(event);

        verify(sharedVitals, never())
                .syncHealthAcrossParticipants(
                        anyDouble(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void handleEntityDamage_ignoresWhenHealthSyncIsInProgress() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(player.getHealth()).thenReturn(10.0D);
        when(sharedVitals.isSyncingHealth()).thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(1.0D);

        service.handleEntityDamage(event);

        verify(sharedVitals, never())
                .syncHealthAcrossParticipants(
                        anyDouble(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void handleFoodLevelChange_syncsOnlyWhenEligibleAndSharedHealthEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        FoodLevelChangeEvent event = mock(FoodLevelChangeEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFoodLevel()).thenReturn(17);

        service.handleFoodLevelChange(event);

        verify(sharedVitals).syncHungerFromFoodLevelChange(player, 17);
    }

    @Test
    void handleFoodLevelChange_doesNotSyncWhenSharedHealthDisabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        Player player = mock(Player.class);

        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(false);

        RunHealthCoordinatorService service = newService(
                challengeManager,
                () -> sharedVitals,
                () -> List.of(player),
                p -> true,
                () -> false,
                p -> {},
                1.0D,
                20.0D);

        FoodLevelChangeEvent event = mock(FoodLevelChangeEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getFoodLevel()).thenReturn(10);

        service.handleFoodLevelChange(event);

        verify(sharedVitals, never()).syncHungerFromFoodLevelChange(player, 10);
    }

    @Test
    void applySharedVitalsIfEnabled_runsBothSyncPathsWhenEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        service.applySharedVitalsIfEnabled();

        verify(sharedVitals).syncSharedHealthFromFirstParticipant();
        verify(sharedVitals).syncSharedHungerFromMostFilledParticipant();
    }

    @Test
    void applySharedVitalsIfEnabled_doesNothingWhenSharedHealthDisabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(false);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        service.applySharedVitalsIfEnabled();

        verify(sharedVitals, never()).syncSharedHealthFromFirstParticipant();
        verify(sharedVitals, never()).syncSharedHungerFromMostFilledParticipant();
    }

    @Test
    void handleEntityDamage_nonPlayerEntity_isIgnored() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(mock(Entity.class));

        service.handleEntityDamage(event);

        verify(sharedVitals, never())
                .syncHealthAcrossParticipants(
                        anyDouble(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void handleFoodLevelChange_nonPlayerEntity_isIgnored() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        FoodLevelChangeEvent event = mock(FoodLevelChangeEvent.class);
        when(event.getEntity()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));

        service.handleFoodLevelChange(event);

        verify(sharedVitals, never())
                .syncHungerFromFoodLevelChange(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void applyInitialHalfHeartIfEnabled_appliesToParticipantsOnlyWhenEnabled() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        Player participant = mock(Player.class);
        when(participant.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(participant.getUniqueId()).thenReturn(UUID.randomUUID());

        AttributeInstance maxHealth = mock(AttributeInstance.class);
        when(participant.getAttribute(maxHealthAttribute())).thenReturn(maxHealth);
        when(maxHealth.getBaseValue()).thenReturn(20.0D).thenReturn(1.0D);
        when(maxHealth.getValue()).thenReturn(20.0D);

        when(challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(false);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> null, () -> List.of(participant), p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        service.applyInitialHalfHeartIfEnabled();

        verify(participant).setHealth(1.0D);
    }

    @Test
    void applyInitialHalfHeart_handlesSpectatorNullAttributeAndSharedHealthPath() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedVitalsService sharedVitals = mock(SharedVitalsService.class);

        Player spectator = mock(Player.class);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);

        Player nullAttributePlayer = mock(Player.class);
        when(nullAttributePlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(nullAttributePlayer.getAttribute(maxHealthAttribute())).thenReturn(null);

        Player sharedPlayer = mock(Player.class);
        UUID sharedId = UUID.randomUUID();
        when(sharedPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(sharedPlayer.getUniqueId()).thenReturn(sharedId);
        AttributeInstance sharedAttr = mock(AttributeInstance.class);
        when(sharedPlayer.getAttribute(maxHealthAttribute())).thenReturn(sharedAttr);
        when(sharedAttr.getBaseValue()).thenReturn(20.0D).thenReturn(1.0D);
        when(sharedAttr.getValue()).thenReturn(20.0D);
        when(challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);

        RunHealthCoordinatorService service = newService(
                challengeManager, () -> sharedVitals, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        service.applyInitialHalfHeart(spectator);
        service.applyInitialHalfHeart(nullAttributePlayer);
        service.applyInitialHalfHeart(sharedPlayer);

        verify(sharedVitals).syncHealthAcrossParticipants(1.0D);
        verify(spectator, never()).setHealth(anyDouble());
        verify(nullAttributePlayer, never()).setHealth(anyDouble());
    }

    @Test
    void restoreDefaultMaxHealth_restoresTrackedOrDefaultValues() {
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        RunHealthCoordinatorService service =
                newService(challengeManager, () -> null, List::of, p -> true, () -> false, p -> {}, 1.0D, 20.0D);

        Player tracked = mock(Player.class);
        UUID trackedId = UUID.randomUUID();
        when(tracked.getUniqueId()).thenReturn(trackedId);
        AttributeInstance trackedAttr = mock(AttributeInstance.class);
        when(tracked.getAttribute(maxHealthAttribute())).thenReturn(trackedAttr);
        when(trackedAttr.getBaseValue()).thenReturn(1.0D).thenReturn(20.0D);

        // Seed original value into internal map by applying half-heart once.
        when(tracked.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(trackedAttr.getValue()).thenReturn(20.0D);
        service.applyInitialHalfHeart(tracked);

        service.restoreDefaultMaxHealth(tracked);
        verify(trackedAttr, org.mockito.Mockito.atLeastOnce()).setBaseValue(anyDouble());

        Player untracked = mock(Player.class);
        UUID untrackedId = UUID.randomUUID();
        when(untracked.getUniqueId()).thenReturn(untrackedId);
        AttributeInstance untrackedAttr = mock(AttributeInstance.class);
        when(untracked.getAttribute(maxHealthAttribute())).thenReturn(untrackedAttr);
        when(untrackedAttr.getBaseValue()).thenReturn(1.0D);

        service.restoreDefaultMaxHealth(untracked);
        verify(untrackedAttr, org.mockito.Mockito.atLeastOnce()).setBaseValue(anyDouble());

        Player noAttribute = mock(Player.class);
        when(noAttribute.getAttribute(maxHealthAttribute())).thenReturn(null);
        service.restoreDefaultMaxHealth(noAttribute);
    }

    private static Attribute maxHealthAttribute() {
        try {
            return (Attribute) Attribute.class.getField("MAX_HEALTH").get(null);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (Attribute)
                        Attribute.class.getField("GENERIC_MAX_HEALTH").get(null);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("MAX_HEALTH attribute constant not found in test runtime.", ex);
            }
        }
    }

    private static RunHealthCoordinatorService newService(
            ChallengeManager challengeManager,
            Supplier<SharedVitalsService> sharedVitalsServiceSupplier,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Predicate<Player> isChallengeActive,
            BooleanSupplier degradingInventoryEnabled,
            Consumer<Player> clearLockedBarrierSlots,
            double halfHeartHealth,
            double defaultMaxHealth) {
        return new RunHealthCoordinatorService(
                challengeManager,
                sharedVitalsServiceSupplier,
                onlineParticipantsSupplier,
                isChallengeActive,
                degradingInventoryEnabled,
                clearLockedBarrierSlots,
                halfHeartHealth,
                defaultMaxHealth);
    }
}

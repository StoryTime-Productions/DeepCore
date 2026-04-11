package dev.deepcore.challenge.vitals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SharedVitalsServiceTest {

    @Test
    void resetSyncFlags_clearsBothSyncGuards() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedVitalsService service = new SharedVitalsService(plugin, List::of);

        service.resetSyncFlags();

        org.junit.jupiter.api.Assertions.assertFalse(service.isSyncingHealth());
        org.junit.jupiter.api.Assertions.assertFalse(service.isSyncingHunger());
    }

    @Test
    void syncSharedHungerFromMostFilledParticipant_syncsMaximums() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player spectator = mock(Player.class);

        when(p1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(p1.getFoodLevel()).thenReturn(6);
        when(p2.getFoodLevel()).thenReturn(15);
        when(p1.getSaturation()).thenReturn(2.0F);
        when(p2.getSaturation()).thenReturn(5.0F);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(p1, p2, spectator));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncSharedHungerFromMostFilledParticipant();
        }

        verify(p1).setFoodLevel(15);
        verify(p2).setFoodLevel(15);
        verify(p1).setSaturation(5.0F);
        verify(p2).setSaturation(5.0F);
        verify(spectator, never()).setFoodLevel(any(Integer.class));
    }

    @Test
    void syncHungerFromFoodLevelChange_usesMinForDecreases() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        Player teammate = mock(Player.class);

        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(teammate.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getFoodLevel()).thenReturn(12);
        when(teammate.getFoodLevel()).thenReturn(8);
        when(source.getSaturation()).thenReturn(6.0F);
        when(teammate.getSaturation()).thenReturn(3.5F);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(source, teammate));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHungerFromFoodLevelChange(source, 4);
        }

        verify(source).setFoodLevel(4);
        verify(teammate).setFoodLevel(4);
        verify(source).setSaturation(3.5F);
        verify(teammate).setSaturation(3.5F);
    }

    @Test
    void syncHungerFromFoodLevelChange_usesMaxForIncreases() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        Player teammate = mock(Player.class);

        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(teammate.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getFoodLevel()).thenReturn(6);
        when(teammate.getFoodLevel()).thenReturn(14);
        when(source.getSaturation()).thenReturn(1.0F);
        when(teammate.getSaturation()).thenReturn(4.0F);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(source, teammate));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHungerFromFoodLevelChange(source, 10);
        }

        verify(source).setFoodLevel(14);
        verify(teammate).setFoodLevel(14);
        verify(source).setSaturation(4.0F);
        verify(teammate).setSaturation(4.0F);
    }

    @Test
    void syncSharedHungerFromMostFilledParticipant_noopsWhenAllSpectators() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player spectator = mock(Player.class);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(spectator));
        service.syncSharedHungerFromMostFilledParticipant();

        verify(spectator, never()).setFoodLevel(any(Integer.class));
        verify(spectator, never()).setSaturation(any(Float.class));
    }

    @Test
    void syncHungerAcrossParticipants_clampsFoodAndSaturationBounds() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);

        when(p1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(GameMode.SURVIVAL);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(p1, p2));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHungerAcrossParticipants(30, 40.0F);
        }

        verify(p1).setFoodLevel(20);
        verify(p2).setFoodLevel(20);
        verify(p1).setSaturation(20.0F);
        verify(p2).setSaturation(20.0F);
    }

    @Test
    void syncHungerAcrossParticipants_clampsLowerBounds() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);

        when(p1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(GameMode.SURVIVAL);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(p1, p2));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHungerAcrossParticipants(-5, -2.0F);
        }

        verify(p1).setFoodLevel(0);
        verify(p2).setFoodLevel(0);
        verify(p1).setSaturation(0.0F);
        verify(p2).setSaturation(0.0F);
    }

    @Test
    void syncHungerFromFoodLevelChange_clampsEventInputToUpperBound() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        Player teammate = mock(Player.class);

        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(teammate.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getFoodLevel()).thenReturn(18);
        when(teammate.getFoodLevel()).thenReturn(16);
        when(source.getSaturation()).thenReturn(8.0F);
        when(teammate.getSaturation()).thenReturn(5.0F);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(source, teammate));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHungerFromFoodLevelChange(source, 99);
        }

        verify(source).setFoodLevel(20);
        verify(teammate).setFoodLevel(20);
        verify(source).setSaturation(8.0F);
        verify(teammate).setSaturation(8.0F);
    }

    @Test
    void syncSharedHealthFromFirstParticipant_noopsWhenAllParticipantsAreSpectators() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player s1 = mock(Player.class);
        Player s2 = mock(Player.class);

        when(s1.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(s2.getGameMode()).thenReturn(GameMode.SPECTATOR);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(s1, s2));
        service.syncSharedHealthFromFirstParticipant();

        verify(s1, never()).setHealth(any(Double.class));
        verify(s2, never()).setHealth(any(Double.class));
    }

    @Test
    void syncHealthAcrossParticipants_clampsHealthAndSkipsSpectators() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        Player spectator = mock(Player.class);

        org.bukkit.attribute.AttributeInstance attr1 = mock(org.bukkit.attribute.AttributeInstance.class);
        org.bukkit.attribute.AttributeInstance attr2 = mock(org.bukkit.attribute.AttributeInstance.class);

        when(p1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(p1.getAttribute(maxHealthAttribute())).thenReturn(attr1);
        when(p2.getAttribute(maxHealthAttribute())).thenReturn(attr2);
        when(attr1.getValue()).thenReturn(20.0D);
        when(attr2.getValue()).thenReturn(12.0D);
        when(p1.getHealth()).thenReturn(18.0D);
        when(p2.getHealth()).thenReturn(9.0D);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(p1, p2, spectator));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHealthAcrossParticipants(50.0D);
        }

        verify(p1).setHealth(20.0D);
        verify(p2).setHealth(12.0D);
        verify(spectator, never()).setHealth(any(Double.class));
    }

    @Test
    void syncHealthAcrossParticipants_withHurtEffect_animatesOnlyNonSourceWhenHealthDrops() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player source = mock(Player.class);
        Player teammate = mock(Player.class);

        UUID sourceId = UUID.randomUUID();
        when(source.getUniqueId()).thenReturn(sourceId);
        when(teammate.getUniqueId()).thenReturn(UUID.randomUUID());

        org.bukkit.attribute.AttributeInstance sourceAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        org.bukkit.attribute.AttributeInstance teammateAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        when(source.getAttribute(maxHealthAttribute())).thenReturn(sourceAttr);
        when(teammate.getAttribute(maxHealthAttribute())).thenReturn(teammateAttr);
        when(sourceAttr.getValue()).thenReturn(20.0D);
        when(teammateAttr.getValue()).thenReturn(20.0D);

        when(source.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(teammate.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(source.getHealth()).thenReturn(18.0D);
        when(teammate.getHealth()).thenReturn(18.0D);
        org.bukkit.Location sourceLocation = mock(org.bukkit.Location.class);
        org.bukkit.Location teammateLocation = mock(org.bukkit.Location.class);
        when(sourceLocation.getYaw()).thenReturn(10.0F);
        when(teammateLocation.getYaw()).thenReturn(30.0F);
        when(source.getLocation()).thenReturn(sourceLocation);
        when(teammate.getLocation()).thenReturn(teammateLocation);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(source, teammate));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncHealthAcrossParticipants(5.0D, sourceId, true);
        }

        verify(teammate).playHurtAnimation(30.0F);
        verify(teammate)
                .playSound(
                        teammateLocation,
                        org.bukkit.Sound.ENTITY_PLAYER_HURT,
                        org.bukkit.SoundCategory.PLAYERS,
                        1.0F,
                        1.0F);
        verify(source, never()).playHurtAnimation(any(Float.class));
    }

    @Test
    void syncSharedHealthFromFirstParticipant_usesFirstActiveParticipantHealth() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player spectator = mock(Player.class);
        Player active = mock(Player.class);
        Player teammate = mock(Player.class);

        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(active.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(teammate.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(active.getHealth()).thenReturn(7.0D);

        org.bukkit.attribute.AttributeInstance activeAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        org.bukkit.attribute.AttributeInstance teammateAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        when(active.getAttribute(maxHealthAttribute())).thenReturn(activeAttr);
        when(teammate.getAttribute(maxHealthAttribute())).thenReturn(teammateAttr);
        when(activeAttr.getValue()).thenReturn(20.0D);
        when(teammateAttr.getValue()).thenReturn(20.0D);
        when(teammate.getHealth()).thenReturn(10.0D);

        SharedVitalsService service = new SharedVitalsService(plugin, () -> List.of(spectator, active, teammate));
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.syncSharedHealthFromFirstParticipant();
        }

        verify(active).setHealth(7.0D);
        verify(teammate).setHealth(7.0D);
    }

    private static org.bukkit.attribute.Attribute maxHealthAttribute() {
        try {
            return (org.bukkit.attribute.Attribute)
                    org.bukkit.attribute.Attribute.class.getField("MAX_HEALTH").get(null);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (org.bukkit.attribute.Attribute) org.bukkit.attribute.Attribute.class
                        .getField("GENERIC_MAX_HEALTH")
                        .get(null);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("MAX_HEALTH attribute constant not found in test runtime.", ex);
            }
        }
    }
}

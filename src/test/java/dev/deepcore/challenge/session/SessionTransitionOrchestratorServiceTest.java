package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.inventory.DegradingInventoryService;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.portal.PortalRoutingService;
import dev.deepcore.challenge.portal.RespawnRoutingService;
import dev.deepcore.challenge.preview.PreviewAnchorService;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.vitals.SharedVitalsService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.challenge.world.WorldStorageService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SessionTransitionOrchestratorServiceTest {

    @Test
    void initialize_andWorldLoad_andPrepBookFlows_areExecuted() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        ChallengeManager manager = mock(ChallengeManager.class);
        Set<UUID> ready = new HashSet<>();
        Set<UUID> participants = new HashSet<>();
        Set<UUID> eliminated = new HashSet<>();
        Set<UUID> recentDead = new HashSet<>();

        DegradingInventoryService degrading = mock(DegradingInventoryService.class);
        SharedVitalsService vitals = mock(SharedVitalsService.class);
        RunProgressService progress = mock(RunProgressService.class);
        RunStatusService status = mock(RunStatusService.class);
        PrepCountdownService countdown = mock(PrepCountdownService.class);
        TaskGroup tasks = mock(TaskGroup.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        CompletionReturnService completion = mock(CompletionReturnService.class);
        PausedRunStateService paused = mock(PausedRunStateService.class);
        PrepAreaService prepArea = mock(PrepAreaService.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PreviewOrchestratorService preview = mock(PreviewOrchestratorService.class);
        PreviewAnchorService anchor = mock(PreviewAnchorService.class);
        PrepBookService prepBook = mock(PrepBookService.class);
        PortalRoutingService portal = mock(PortalRoutingService.class);
        RespawnRoutingService respawn = mock(RespawnRoutingService.class);
        SharedInventorySyncService inventorySync = mock(SharedInventorySyncService.class);
        WorldStorageService storage = mock(WorldStorageService.class);
        RunHealthCoordinatorService health = mock(RunHealthCoordinatorService.class);

        Runnable syncRules = mock(Runnable.class);
        Runnable refreshPreview = mock(Runnable.class);
        Runnable refreshPrepGuis = mock(Runnable.class);
        Runnable startLobbySidebar = mock(Runnable.class);
        Runnable clearPausedSnapshots = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> clearLobbySidebar = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> stashRunState = mock(Consumer.class);

        WorldResetManager resetManager = mock(WorldResetManager.class);
        Supplier<WorldResetManager> resetSupplier = () -> resetManager;

        SessionTransitionOrchestratorService service = new SessionTransitionOrchestratorService(
                plugin,
                log,
                state,
                manager,
                ready,
                participants,
                eliminated,
                recentDead,
                degrading,
                vitals,
                progress,
                status,
                countdown,
                tasks,
                "degrading",
                "lobby",
                actionBar,
                completion,
                paused,
                prepArea,
                worldClassifier,
                preview,
                anchor,
                prepBook,
                portal,
                respawn,
                inventorySync,
                storage,
                health,
                syncRules,
                refreshPreview,
                refreshPrepGuis,
                startLobbySidebar,
                clearPausedSnapshots,
                clearLobbySidebar,
                stashRunState,
                resetSupplier);

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        World world = mock(World.class);
        when(p1.getWorld()).thenReturn(world);
        when(p2.getWorld()).thenReturn(world);
        when(worldClassifier.isLobbyOrLimboWorld(world)).thenReturn(true);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable r = invocation.getArgument(1);
                        r.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(plugin), any(Runnable.class), eq(1L));

            service.initialize();
            verify(syncRules).run();
            verify(storage).ensureAllWorldStorageDirectories();
            verify(startLobbySidebar).run();
            verify(preview, org.mockito.Mockito.times(2)).removeLobbyBlockDisplayEntities();
            verify(anchor).teleportToLobbyIfConfigured(p1);
            verify(anchor).teleportToLobbyIfConfigured(p2);
            verify(prepBook).giveIfMissing(p1);
            verify(prepBook).giveIfMissing(p2);
            verify(prepArea).applyBordersToOnlinePlayers(eq(false), any());
            verify(refreshPreview).run();

            service.ensurePrepBook(p1);
            verify(prepBook, org.mockito.Mockito.times(2)).giveIfMissing(p1);

            WorldLoadEvent loadEvent = mock(WorldLoadEvent.class);
            when(loadEvent.getWorld()).thenReturn(world);
            service.handleWorldLoad(loadEvent);
            verify(storage).ensureWorldStorageDirectories(world);
            verify(scheduler).runTaskLater(eq(plugin), eq(refreshPreview), eq(1L));

            service.clearPausedSnapshots();
            verify(paused).clearSnapshots();
        }
    }

    @Test
    void resetAndShutdownAndEndChallenge_coverStateTransitions() {
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.RUNNING);

        JavaPlugin plugin = mock(JavaPlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        ChallengeManager manager = mock(ChallengeManager.class);
        Set<UUID> ready = new HashSet<>();
        Set<UUID> participants = new HashSet<>();
        Set<UUID> eliminated = new HashSet<>();
        Set<UUID> recentDead = new HashSet<>();

        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();
        participants.add(p1Id);
        participants.add(p2Id);
        ready.add(UUID.randomUUID());
        eliminated.add(UUID.randomUUID());
        recentDead.add(UUID.randomUUID());

        DegradingInventoryService degrading = mock(DegradingInventoryService.class);
        SharedVitalsService vitals = mock(SharedVitalsService.class);
        RunProgressService progress = mock(RunProgressService.class);
        RunStatusService status = mock(RunStatusService.class);
        PrepCountdownService countdown = mock(PrepCountdownService.class);
        TaskGroup tasks = mock(TaskGroup.class);
        ActionBarTickerService actionBar = mock(ActionBarTickerService.class);
        CompletionReturnService completion = mock(CompletionReturnService.class);
        PausedRunStateService paused = mock(PausedRunStateService.class);
        PrepAreaService prepArea = mock(PrepAreaService.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PreviewOrchestratorService preview = mock(PreviewOrchestratorService.class);
        PreviewAnchorService anchor = mock(PreviewAnchorService.class);
        PrepBookService prepBook = mock(PrepBookService.class);
        PortalRoutingService portal = mock(PortalRoutingService.class);
        RespawnRoutingService respawn = mock(RespawnRoutingService.class);
        SharedInventorySyncService inventorySync = mock(SharedInventorySyncService.class);
        WorldStorageService storage = mock(WorldStorageService.class);
        RunHealthCoordinatorService health = mock(RunHealthCoordinatorService.class);

        Runnable syncRules = mock(Runnable.class);
        Runnable refreshPreview = mock(Runnable.class);
        Runnable refreshPrepGuis = mock(Runnable.class);
        Runnable startLobbySidebar = mock(Runnable.class);
        Runnable clearPausedSnapshots = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> clearLobbySidebar = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> stashRunState = mock(Consumer.class);

        WorldResetManager resetManager = mock(WorldResetManager.class);
        Supplier<WorldResetManager> resetSupplier = () -> resetManager;

        SessionTransitionOrchestratorService service = new SessionTransitionOrchestratorService(
                plugin,
                log,
                state,
                manager,
                ready,
                participants,
                eliminated,
                recentDead,
                degrading,
                vitals,
                progress,
                status,
                countdown,
                tasks,
                "degrading",
                "lobby",
                actionBar,
                completion,
                paused,
                prepArea,
                worldClassifier,
                preview,
                anchor,
                prepBook,
                portal,
                respawn,
                inventorySync,
                storage,
                health,
                syncRules,
                refreshPreview,
                refreshPrepGuis,
                startLobbySidebar,
                clearPausedSnapshots,
                clearLobbySidebar,
                stashRunState,
                resetSupplier);

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(p1Id);
        when(p2.getUniqueId()).thenReturn(p2Id);
        when(p1.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(p2.getGameMode()).thenReturn(GameMode.ADVENTURE);

        World world = mock(World.class);
        when(p1.getWorld()).thenReturn(world);
        when(p2.getWorld()).thenReturn(world);
        when(worldClassifier.isLobbyOrLimboWorld(world)).thenReturn(false);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable r = invocation.getArgument(1);
                        r.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.shutdown();
            verify(stashRunState).accept(p1);
            verify(stashRunState).accept(p2);

            service.resetForNewRun();
            assertEquals(SessionState.Phase.PREP, state.getPhase());
            assertEquals(0, ready.size());
            assertEquals(0, participants.size());
            assertEquals(0, eliminated.size());
            assertEquals(0, recentDead.size());

            verify(degrading).resetAllowedInventorySlots();
            verify(vitals).resetSyncFlags();
            verify(progress, org.mockito.Mockito.atLeastOnce()).reset();
            verify(status, org.mockito.Mockito.atLeastOnce()).reset();
            verify(portal, org.mockito.Mockito.atLeastOnce()).clearTransitCooldowns();
            verify(respawn, org.mockito.Mockito.atLeastOnce()).clearPendingRespawns();
            verify(inventorySync, org.mockito.Mockito.atLeastOnce()).clearWearableSnapshots();
            verify(anchor, org.mockito.Mockito.atLeastOnce()).teleportToLobbyIfConfigured(any(Player.class));
            verify(clearLobbySidebar, org.mockito.Mockito.atLeastOnce()).accept(any(Player.class));
            verify(health, org.mockito.Mockito.atLeastOnce()).restoreDefaultMaxHealth(any(Player.class));
            verify(prepBook, org.mockito.Mockito.atLeastOnce()).giveIfMissing(any(Player.class));
            verify(refreshPreview, org.mockito.Mockito.atLeastOnce()).run();
            verify(refreshPrepGuis).run();

            service.endChallengeAndReturnToPrep();
            verify(resetManager).selectRandomLobbyWorld();
            verify(log).info("DeepCore is now back in prep mode.");
        }
    }
}

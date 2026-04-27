package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RunStartServiceTest {

    @Test
    void startRun_blocksWhenDiscoPreviewIsActive() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        ChallengeManager manager = mock(ChallengeManager.class);
        Set<UUID> participants = new HashSet<>(Set.of(UUID.randomUUID()));
        Set<UUID> ready = new HashSet<>();
        PrepAreaService prepArea = mock(PrepAreaService.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PreviewOrchestratorService preview = mock(PreviewOrchestratorService.class);
        RunProgressService progress = mock(RunProgressService.class);
        RunStatusService status = mock(RunStatusService.class);
        PrepBookService prepBook = mock(PrepBookService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        Runnable announceBlocked = mock(Runnable.class);
        Runnable refreshOpenPrep = mock(Runnable.class);

        RunStartService service = newService(
                plugin,
                state,
                manager,
                participants,
                ready,
                prepArea,
                worldClassifier,
                preview,
                progress,
                status,
                List::of,
                () -> null,
                prepBook,
                player -> {},
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                player -> {},
                mock(Runnable.class),
                () -> false,
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                () -> true,
                announceBlocked,
                refreshOpenPrep,
                log);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.startRun();

            verify(announceBlocked).run();
            verify(prepArea).applyBordersToOnlinePlayers(eq(false), any());
            verify(scheduler).runTask(plugin, refreshOpenPrep);
            verify(log, never()).info("DeepCore run started.");
        }
    }

    @Test
    void startRun_transitionsAndLaunchesAllConfiguredSystems() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);

        ChallengeManager manager = mock(ChallengeManager.class);
        when(manager.isEnabled()).thenReturn(false);
        when(manager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)).thenReturn(true);
        when(manager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH)).thenReturn(true);

        Set<UUID> participants = new HashSet<>();
        Set<UUID> ready = new HashSet<>(Set.of(UUID.randomUUID()));

        PrepAreaService prepArea = mock(PrepAreaService.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        PreviewOrchestratorService preview = mock(PreviewOrchestratorService.class);
        RunProgressService progress = mock(RunProgressService.class);
        RunStatusService status = mock(RunStatusService.class);
        PrepBookService prepBook = mock(PrepBookService.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        List<Player> onlineParticipants = List.of(p1, p2);

        WorldResetManager worldReset = mock(WorldResetManager.class);
        World runWorld = mock(World.class);
        Location spawn = new Location(runWorld, 0.0D, 64.0D, 0.0D);
        when(worldReset.getCurrentOverworld()).thenReturn(runWorld);
        when(runWorld.getSpawnLocation()).thenReturn(spawn);

        @SuppressWarnings("unchecked")
        Consumer<Player> clearSidebar = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Runnable clearPaused = mock(Runnable.class);
        Runnable syncWorldRules = mock(Runnable.class);
        Runnable startActionBar = mock(Runnable.class);
        Runnable snapshotWearables = mock(Runnable.class);
        Runnable startDegrading = mock(Runnable.class);
        Runnable syncInventory = mock(Runnable.class);
        Runnable syncHealth = mock(Runnable.class);
        Runnable syncHunger = mock(Runnable.class);
        Runnable applyHalfHeart = mock(Runnable.class);

        RunStartService service = newService(
                plugin,
                state,
                manager,
                participants,
                ready,
                prepArea,
                worldClassifier,
                preview,
                progress,
                status,
                () -> onlineParticipants,
                () -> worldReset,
                prepBook,
                clearSidebar,
                clearPaused,
                syncWorldRules,
                startActionBar,
                snapshotWearables,
                enforceCap,
                startDegrading,
                () -> true,
                syncInventory,
                syncHealth,
                syncHunger,
                applyHalfHeart,
                () -> false,
                mock(Runnable.class),
                mock(Runnable.class),
                log);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            service.startRun();

            verify(manager).setEnabled(true);
            verify(clearPaused).run();
            verify(syncWorldRules).run();
            verify(prepArea).clearBorders();
            verify(preview).clearLobbyPreviewEntities();
            verify(progress).reset();
            verify(status).reset();

            verify(p1).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
            verify(p2).teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
            verify(startActionBar).run();
            verify(prepBook).removeFromInventory(p1);
            verify(prepBook).removeFromInventory(p2);
            verify(clearSidebar).accept(p1);
            verify(clearSidebar).accept(p2);
            verify(snapshotWearables).run();

            verify(enforceCap).accept(p1);
            verify(enforceCap).accept(p2);
            verify(startDegrading).run();
            verify(syncInventory).run();
            verify(syncHealth).run();
            verify(syncHunger).run();
            verify(applyHalfHeart).run();

            verify(log).info("Run started!");
        }
    }

    @Test
    void startRun_handlesMissingRunWorldGracefully() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SessionState state = new SessionState();
        state.setPhase(SessionState.Phase.PREP);
        ChallengeManager manager = mock(ChallengeManager.class);
        when(manager.isEnabled()).thenReturn(true);
        when(manager.isComponentEnabled(any())).thenReturn(false);

        WorldResetManager worldReset = mock(WorldResetManager.class);
        when(worldReset.getCurrentOverworld()).thenReturn(null);

        RunStartService service = newService(
                plugin,
                state,
                manager,
                new HashSet<>(),
                new HashSet<>(),
                mock(PrepAreaService.class),
                mock(WorldClassificationService.class),
                mock(PreviewOrchestratorService.class),
                mock(RunProgressService.class),
                mock(RunStatusService.class),
                List::of,
                () -> worldReset,
                mock(PrepBookService.class),
                player -> {},
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                player -> {},
                mock(Runnable.class),
                () -> false,
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class),
                () -> false,
                mock(Runnable.class),
                mock(Runnable.class),
                mock(DeepCoreLogger.class));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            service.startRun();
        }
    }

    private static RunStartService newService(
            JavaPlugin plugin,
            SessionState sessionState,
            ChallengeManager challengeManager,
            Set<UUID> participants,
            Set<UUID> readyPlayers,
            PrepAreaService prepAreaService,
            WorldClassificationService worldClassificationService,
            PreviewOrchestratorService previewOrchestratorService,
            RunProgressService runProgressService,
            RunStatusService runStatusService,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            PrepBookService prepBookService,
            Consumer<Player> clearLobbySidebar,
            Runnable clearPausedSnapshots,
            Runnable syncWorldRules,
            Runnable startActionBarTask,
            Runnable snapshotEquippedWearablesForParticipants,
            Consumer<Player> enforceInventorySlotCap,
            Runnable startDegradingInventoryTaskWithReset,
            BooleanSupplier isSharedInventoryEnabled,
            Runnable syncSharedInventoryFromFirstParticipant,
            Runnable syncSharedHealthFromFirstParticipant,
            Runnable syncSharedHungerFromMostFilledParticipant,
            Runnable applyInitialHalfHeartIfEnabled,
            BooleanSupplier isDiscoPreviewBlockingChallengeStart,
            Runnable announceDiscoPreviewStartBlocked,
            Runnable refreshOpenPrepGuis,
            DeepCoreLogger log) {
        return new RunStartService(
                plugin,
                sessionState,
                challengeManager,
                participants,
                readyPlayers,
                prepAreaService,
                worldClassificationService,
                previewOrchestratorService,
                runProgressService,
                runStatusService,
                onlineParticipantsSupplier,
                worldResetManagerSupplier,
                prepBookService,
                clearLobbySidebar,
                clearPausedSnapshots,
                syncWorldRules,
                startActionBarTask,
                snapshotEquippedWearablesForParticipants,
                enforceInventorySlotCap,
                startDegradingInventoryTaskWithReset,
                isSharedInventoryEnabled,
                syncSharedInventoryFromFirstParticipant,
                syncSharedHealthFromFirstParticipant,
                syncSharedHungerFromMostFilledParticipant,
                applyInitialHalfHeartIfEnabled,
                isDiscoPreviewBlockingChallengeStart,
                announceDiscoPreviewStartBlocked,
                refreshOpenPrepGuis,
                log);
    }
}

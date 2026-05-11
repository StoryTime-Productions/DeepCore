package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.inventory.SharedInventorySyncService;
import dev.deepcore.challenge.portal.RespawnRoutingService;
import dev.deepcore.challenge.preview.PreviewAnchorService;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SessionPlayerLifecycleServiceTest {

    @Test
    void handlePlayerJoin_inPrepPhase_refreshesPrepState() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.PREP);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            when(f.previewOrchestratorService.hasLiveLobbyPreviewEntities()).thenReturn(false);

            f.service.handlePlayerJoin(event);
        }

        verify(f.playerLobbyStateService)
                .enforceSurvivalOnWorldEntry(eq(player), eq(false), eq(false), eq(f.eliminatedPlayers));
        verify(f.playerLobbyStateService).applyLobbyInventoryLoadoutIfInLobbyWorld(player);
        verify(f.syncWorldRules).run();
        verify(f.refreshLobbyPreview).run();
        verify(f.previewAnchorService).teleportToLobbyIfConfigured(player);
        verify(f.restoreDefaultMaxHealth).accept(player);
        verify(f.prepAreaService).applyBorder(eq(player), eq(false), any());
        verify(f.prepBookService).giveIfMissing(player);
        verify(f.refreshOpenPrepGuis).run();
        verify(f.prepBookService, never()).removeFromInventory(any(Player.class));
    }

    @Test
    void handlePlayerJoin_runningPhase_appliesEnabledRunSyncs() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        f.participants.add(id);

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        when(f.isSharedInventoryEnabled.getAsBoolean()).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            f.service.handlePlayerJoin(event);
        }

        verify(f.prepBookService).removeFromInventory(player);
        verify(f.syncSharedInventoryFromFirstParticipant).run();
        verify(f.sharedInventorySyncService).capturePlayerWearableSnapshot(player);
        verify(f.enforceInventorySlotCap).accept(player);
        verify(f.syncSharedHealthFromFirstParticipant).run();
        verify(f.syncSharedHungerFromMostFilledParticipant).run();
        verify(f.applyInitialHalfHeart).accept(player);
    }

    @Test
    void handlePlayerQuit_runningAndPrepPaths_areHandled() {
        Fixture f = new Fixture();

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        f.sessionState.setPhase(SessionState.Phase.RUNNING);
        f.participants.add(id);
        f.onlineParticipants.add(mock(Player.class));
        f.onlineParticipants.add(mock(Player.class));
        when(f.runPauseResumeService.pause(any(), eq(false))).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(org.bukkit.command.ConsoleCommandSender.class));
            f.service.handlePlayerQuit(event);
        }

        verify(f.sharedInventorySyncService).removePlayerWearableSnapshot(id);
        verify(f.runPauseResumeService).pause(any(), eq(false));
        verify(f.log).warn("Alex left during the run. Challenge has been paused.");

        f.sessionState.setPhase(SessionState.Phase.PREP);
        f.readyPlayers.add(id);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            f.service.handlePlayerQuit(event);
        }

        verify(f.prepGuiCoordinatorService).onPlayerLeft(id);
        verify(f.prepReadinessService)
                .tryStartCountdown(
                        eq(f.readyPlayers),
                        eq(f.participants),
                        eq(f.isDiscoPreviewBlockingChallengeStart),
                        eq(f.announceDiscoPreviewStartBlocked),
                        eq(f.startRun));
    }

    @Test
    void handlePlayerQuit_stopsChallengeWhenLastParticipantLeaves() {
        Fixture f = new Fixture();

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        f.sessionState.setPhase(SessionState.Phase.RUNNING);
        f.participants.add(id);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            f.service.handlePlayerQuit(event);
        }

        verify(f.endChallengeAndReturnToPrep).run();
        verify(f.runPauseResumeService, never()).pause(any(), eq(false));
    }

    @Test
    void handlePlayerDeath_respawn_andWorldChange_coverRunBranches() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);

        f.participants.add(id);
        f.eliminatedPlayers.add(id);
        when(f.isChallengeActive.test(player)).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.SHARED_HEALTH))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART))
                .thenReturn(true);
        when(f.isSharedInventoryEnabled.getAsBoolean()).thenReturn(true);

        Player survivor = mock(Player.class);
        UUID survivorId = UUID.randomUUID();
        when(survivor.getUniqueId()).thenReturn(survivorId);
        when(survivor.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerInventory survivorInventory = mock(PlayerInventory.class);
        when(survivor.getInventory()).thenReturn(survivorInventory);
        f.onlineParticipants.add(player);
        f.onlineParticipants.add(survivor);

        Location runRespawn = mock(Location.class);
        when(f.respawnRoutingService.resolveRunRespawnLocation(id)).thenReturn(runRespawn);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        when(deathEvent.getPlayer()).thenReturn(player);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(player);

        PlayerChangedWorldEvent changedWorldEvent = mock(PlayerChangedWorldEvent.class);
        when(changedWorldEvent.getPlayer()).thenReturn(player);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(f.plugin), any(Runnable.class), eq(1L));

            f.service.handlePlayerDeath(deathEvent);
            f.service.handlePlayerRespawn(respawnEvent);
            f.service.handlePlayerChangedWorld(changedWorldEvent);
        }

        verify(f.clearLockedBarrierSlots).accept(player);
        verify(f.respawnRoutingService).recordDeathWorld(id, world);
        verify(f.sessionFailureService).handleHardcoreFailureIfNeeded();
        verify(respawnEvent).setRespawnLocation(runRespawn);
        verify(f.endChallengeAndReturnToPrep).run();
        verify(player, never()).setGameMode(GameMode.SPECTATOR);
        verify(f.sharedInventorySyncService).syncSharedInventoryFromSourceNow(survivor);
        verify(f.syncSharedHealthFromFirstParticipant).run();
        verify(f.syncSharedHungerFromMostFilledParticipant).run();
        verify(f.enforceInventorySlotCap).accept(player);
        verify(f.applyInitialHalfHeart).accept(player);
        verify(f.runStatusService).onParticipantWorldChanged(any(), eq(f.onlineParticipants), anyLong(), anyBoolean());
    }

    @Test
    void handlePlayerChangedWorld_enteringLobbyWorld_restoresDefaultMaxHealth() {
        // When a player is teleported back to the lobby (e.g. after the challenge ends),
        // restoreDefaultMaxHealth must be called so the half-heart health cap is lifted.
        // Previously this was only called in handlePlayerJoin, not on world change.
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.PREP);

        Player player = mock(Player.class);
        World lobbyWorld = mock(World.class);
        when(player.getWorld()).thenReturn(lobbyWorld);
        when(f.worldClassificationService.isLobbyOrLimboWorld(lobbyWorld)).thenReturn(true);

        PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        f.service.handlePlayerChangedWorld(event);

        verify(f.restoreDefaultMaxHealth).accept(player);
    }

    @Test
    void handlePlayerChangedWorld_enteringNonLobbyWorld_doesNotRestoreMaxHealth() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        World runWorld = mock(World.class);
        when(player.getWorld()).thenReturn(runWorld);
        when(f.worldClassificationService.isLobbyOrLimboWorld(runWorld)).thenReturn(false);
        when(f.worldClassificationService.isTrainingWorld(runWorld)).thenReturn(false);
        when(f.isChallengeActive.test(player)).thenReturn(false);

        PlayerChangedWorldEvent event = mock(PlayerChangedWorldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        f.service.handlePlayerChangedWorld(event);

        verify(f.restoreDefaultMaxHealth, never()).accept(any());
    }

    @Test
    void handlePlayerRespawn_hardcoreEliminated_setsSpectatorWhenOtherParticipantsStillAlive() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        UUID survivorId = UUID.randomUUID();
        f.participants.add(id);
        f.participants.add(survivorId);
        f.eliminatedPlayers.add(id);
        when(f.isChallengeActive.test(player)).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(true);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(player);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            f.service.handlePlayerRespawn(respawnEvent);
        }

        verify(player).setGameMode(GameMode.SPECTATOR);
        verify(f.log).sendWarn(player, "You were eliminated by hardcore mode.");
        verify(f.endChallengeAndReturnToPrep, never()).run();
    }

    @Test
    void handlePlayerDeath_whenHardcoreDisabled_tracksDeathWithoutFailureReset() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);

        f.participants.add(id);
        when(f.isChallengeActive.test(player)).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(false);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        when(deathEvent.getPlayer()).thenReturn(player);

        f.service.handlePlayerDeath(deathEvent);

        verify(f.clearLockedBarrierSlots).accept(player);
        verify(f.respawnRoutingService).recordDeathWorld(id, world);
        verify(f.sessionFailureService, never()).handleHardcoreFailureIfNeeded();
        verify(f.sessionFailureService).handleAllPlayersDeadFailureIfNeeded();
        org.junit.jupiter.api.Assertions.assertTrue(f.recentlyDeadPlayers.contains(id));
        org.junit.jupiter.api.Assertions.assertFalse(f.eliminatedPlayers.contains(id));
    }

    @Test
    void handlePlayerRespawn_runningPhase_overridesOnlyStaleBedRespawn() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        f.participants.add(id);

        World currentRunWorld = mock(World.class);
        UUID currentRunWorldId = UUID.randomUUID();
        when(currentRunWorld.getUID()).thenReturn(currentRunWorldId);

        Location pluginRespawn = mock(Location.class);
        when(pluginRespawn.getWorld()).thenReturn(currentRunWorld);
        when(f.respawnRoutingService.resolveRunRespawnLocation(id)).thenReturn(pluginRespawn);

        // Simulate a bed respawn set from a previous run world
        World oldWorld = mock(World.class);
        when(oldWorld.getUID()).thenReturn(UUID.randomUUID());
        Location staleOldWorldBed = new Location(oldWorld, 100.0D, 70.0D, 100.0D);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(player);
        when(respawnEvent.getRespawnLocation()).thenReturn(staleOldWorldBed);

        f.service.handlePlayerRespawn(respawnEvent);

        verify(f.respawnRoutingService).resolveRunRespawnLocation(id);
        verify(respawnEvent).setRespawnLocation(pluginRespawn);
    }

    @Test
    void handlePlayerRespawn_runningPhase_preservesBedRespawnInCurrentWorld() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        f.participants.add(id);

        World currentWorld = mock(World.class);
        UUID currentWorldId = UUID.randomUUID();
        when(currentWorld.getUID()).thenReturn(currentWorldId);

        Location runRespawn = mock(Location.class);
        when(runRespawn.getWorld()).thenReturn(currentWorld);
        when(f.respawnRoutingService.resolveRunRespawnLocation(id)).thenReturn(runRespawn);

        Location bedRespawn = mock(Location.class);
        when(bedRespawn.getWorld()).thenReturn(currentWorld);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(player);
        when(respawnEvent.getRespawnLocation()).thenReturn(bedRespawn);

        f.service.handlePlayerRespawn(respawnEvent);

        verify(f.respawnRoutingService).resolveRunRespawnLocation(id);
        verify(respawnEvent, never()).setRespawnLocation(any(Location.class));
    }

    @Test
    void handlePlayerRespawn_runningPhase_skipsRespawningPlayerAsSharedInventorySource() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player respawningPlayer = mock(Player.class);
        UUID respawningId = UUID.randomUUID();
        when(respawningPlayer.getUniqueId()).thenReturn(respawningId);
        when(respawningPlayer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(f.isChallengeActive.test(respawningPlayer)).thenReturn(true);
        when(f.isSharedInventoryEnabled.getAsBoolean()).thenReturn(true);

        Player survivor = mock(Player.class);
        UUID survivorId = UUID.randomUUID();
        when(survivor.getUniqueId()).thenReturn(survivorId);
        when(survivor.getGameMode()).thenReturn(GameMode.SURVIVAL);

        f.onlineParticipants.add(respawningPlayer);
        f.onlineParticipants.add(survivor);

        Location runRespawn = mock(Location.class);
        when(f.respawnRoutingService.resolveRunRespawnLocation(respawningId)).thenReturn(runRespawn);

        PlayerRespawnEvent respawnEvent = mock(PlayerRespawnEvent.class);
        when(respawnEvent.getPlayer()).thenReturn(respawningPlayer);
        when(respawnEvent.getRespawnLocation()).thenReturn(mock(Location.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(f.plugin), any(Runnable.class));

            f.service.handlePlayerRespawn(respawnEvent);
        }

        verify(f.sharedInventorySyncService).syncSharedInventoryFromSourceNow(survivor);
        verify(f.sharedInventorySyncService, never()).syncSharedInventoryFromSourceNow(respawningPlayer);
    }

    @Test
    void handlePlayerDeath_sharedInventoryOn_keepInventoryOff_wipesOtherParticipants() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player dyingPlayer = mock(Player.class);
        UUID dyingId = UUID.randomUUID();
        when(dyingPlayer.getUniqueId()).thenReturn(dyingId);
        when(dyingPlayer.getWorld()).thenReturn(mock(World.class));
        PlayerInventory dyingInv = mock(PlayerInventory.class);
        when(dyingPlayer.getInventory()).thenReturn(dyingInv);

        Player otherA = mock(Player.class);
        Player otherB = mock(Player.class);
        UUID otherAId = UUID.randomUUID();
        UUID otherBId = UUID.randomUUID();
        when(otherA.getUniqueId()).thenReturn(otherAId);
        when(otherB.getUniqueId()).thenReturn(otherBId);

        PlayerInventory invA = mock(PlayerInventory.class);
        PlayerInventory invB = mock(PlayerInventory.class);
        when(otherA.getInventory()).thenReturn(invA);
        when(otherB.getInventory()).thenReturn(invB);

        f.onlineParticipants.add(dyingPlayer);
        f.onlineParticipants.add(otherA);
        f.onlineParticipants.add(otherB);

        when(f.isChallengeActive.test(dyingPlayer)).thenReturn(true);
        when(f.isSharedInventoryEnabled.getAsBoolean()).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.KEEP_INVENTORY))
                .thenReturn(false);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(false);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        when(deathEvent.getPlayer()).thenReturn(dyingPlayer);

        f.service.handlePlayerDeath(deathEvent);

        verify(deathEvent).setKeepInventory(false);
        verify(invA).clear();
        verify(otherA).updateInventory();
        verify(invB).clear();
        verify(otherB).updateInventory();
        verify(dyingInv, never()).clear();
    }

    @Test
    void handlePlayerDeath_sharedInventoryOn_keepInventoryOn_doesNotWipeOtherParticipants() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.RUNNING);

        Player dyingPlayer = mock(Player.class);
        UUID dyingId = UUID.randomUUID();
        when(dyingPlayer.getUniqueId()).thenReturn(dyingId);
        when(dyingPlayer.getWorld()).thenReturn(mock(World.class));

        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory otherInv = mock(PlayerInventory.class);
        when(other.getInventory()).thenReturn(otherInv);

        f.onlineParticipants.add(dyingPlayer);
        f.onlineParticipants.add(other);

        when(f.isChallengeActive.test(dyingPlayer)).thenReturn(true);
        when(f.isSharedInventoryEnabled.getAsBoolean()).thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.KEEP_INVENTORY))
                .thenReturn(true);
        when(f.challengeManager.isComponentEnabled(ChallengeComponent.HARDCORE)).thenReturn(false);

        PlayerDeathEvent deathEvent = mock(PlayerDeathEvent.class);
        when(deathEvent.getPlayer()).thenReturn(dyingPlayer);

        f.service.handlePlayerDeath(deathEvent);

        verify(otherInv, never()).clear();
        verify(other, never()).updateInventory();
    }

    private static final class Fixture {
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final DeepCoreLogger log = mock(DeepCoreLogger.class);
        final ChallengeManager challengeManager = mock(ChallengeManager.class);
        final SessionState sessionState = new SessionState();
        final Set<UUID> readyPlayers = new HashSet<>();
        final Set<UUID> participants = new HashSet<>();
        final Set<UUID> eliminatedPlayers = new HashSet<>();
        final Set<UUID> recentlyDeadPlayers = new HashSet<>();

        final PlayerLobbyStateService playerLobbyStateService = mock(PlayerLobbyStateService.class);
        final PreviewAnchorService previewAnchorService = mock(PreviewAnchorService.class);
        final PreviewOrchestratorService previewOrchestratorService = mock(PreviewOrchestratorService.class);
        final WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        final PrepAreaService prepAreaService = mock(PrepAreaService.class);
        final PrepBookService prepBookService = mock(PrepBookService.class);
        final PrepGuiCoordinatorService prepGuiCoordinatorService = mock(PrepGuiCoordinatorService.class);
        final PrepReadinessService prepReadinessService = mock(PrepReadinessService.class);
        final RunPauseResumeService runPauseResumeService = mock(RunPauseResumeService.class);
        final RespawnRoutingService respawnRoutingService = mock(RespawnRoutingService.class);
        final SessionFailureService sessionFailureService = mock(SessionFailureService.class);
        final RunStatusService runStatusService = mock(RunStatusService.class);
        final SharedInventorySyncService sharedInventorySyncService = mock(SharedInventorySyncService.class);

        final BooleanSupplier isSharedInventoryEnabled = mock(BooleanSupplier.class);
        final Predicate<Player> isChallengeActive = mock(Predicate.class);
        final List<Player> onlineParticipants = new ArrayList<>();
        final Supplier<List<Player>> onlineParticipantsSupplier = () -> onlineParticipants;

        final Consumer<Player> enforceInventorySlotCap = mock(Consumer.class);
        final Consumer<Player> clearLockedBarrierSlots = mock(Consumer.class);
        final Consumer<Player> applyInitialHalfHeart = mock(Consumer.class);
        final Consumer<Player> restoreDefaultMaxHealth = mock(Consumer.class);

        final Runnable syncWorldRules = mock(Runnable.class);
        final Runnable refreshLobbyPreview = mock(Runnable.class);
        final Runnable refreshOpenPrepGuis = mock(Runnable.class);
        final Runnable startRun = mock(Runnable.class);
        final Runnable endChallengeAndReturnToPrep = mock(Runnable.class);
        final Runnable syncSharedInventoryFromFirstParticipant = mock(Runnable.class);
        final Runnable syncSharedHealthFromFirstParticipant = mock(Runnable.class);
        final Runnable syncSharedHungerFromMostFilledParticipant = mock(Runnable.class);

        final BooleanSupplier isDiscoPreviewBlockingChallengeStart = mock(BooleanSupplier.class);
        final Runnable announceDiscoPreviewStartBlocked = mock(Runnable.class);

        final SessionPlayerLifecycleService service = new SessionPlayerLifecycleService(
                plugin,
                log,
                challengeManager,
                sessionState,
                readyPlayers,
                participants,
                eliminatedPlayers,
                recentlyDeadPlayers,
                playerLobbyStateService,
                previewAnchorService,
                previewOrchestratorService,
                worldClassificationService,
                prepAreaService,
                prepBookService,
                prepGuiCoordinatorService,
                prepReadinessService,
                runPauseResumeService,
                respawnRoutingService,
                sessionFailureService,
                runStatusService,
                sharedInventorySyncService,
                isSharedInventoryEnabled,
                isChallengeActive,
                onlineParticipantsSupplier,
                enforceInventorySlotCap,
                clearLockedBarrierSlots,
                applyInitialHalfHeart,
                restoreDefaultMaxHealth,
                syncWorldRules,
                refreshLobbyPreview,
                refreshOpenPrepGuis,
                startRun,
                endChallengeAndReturnToPrep,
                syncSharedInventoryFromFirstParticipant,
                syncSharedHealthFromFirstParticipant,
                syncSharedHungerFromMostFilledParticipant,
                isDiscoPreviewBlockingChallengeStart,
                announceDiscoPreviewStartBlocked);
    }
}

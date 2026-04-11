package dev.deepcore.challenge.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.world.WorldResetManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class PreviewOrchestratorServiceBehaviorTest {

    @Test
    void rebuildLobbyPreview_initialDisplayPath_spawnsPreviewBlocksAndLabel() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewEnabled()).thenReturn(true);

        PreviewSampleService sampleService = mock(PreviewSampleService.class);
        PreviewAnchorService anchorService = mock(PreviewAnchorService.class);
        PreviewRuntimeService runtimeService = mock(PreviewRuntimeService.class);
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        WorldResetManager worldReset = mock(WorldResetManager.class);

        World runWorld = mock(World.class);
        World previewWorld = mock(World.class);
        Location anchor = new Location(previewWorld, 5.0D, 80.0D, -4.0D);
        Location runSpawn = new Location(runWorld, 0.0D, 64.0D, 0.0D);
        when(runWorld.getSpawnLocation()).thenReturn(runSpawn);
        when(runWorld.getBiome(0, 64, 0)).thenReturn(Biome.PLAINS);

        when(worldReset.getCurrentOverworld()).thenReturn(runWorld);
        when(worldReset.isCurrentOverworldDiscoVariant()).thenReturn(false);
        when(anchorService.resolvePreviewAnchor()).thenReturn(anchor);
        when(runtimeService.getPreviewBlockScale()).thenReturn(1.0D);
        when(runtimeService.getPreviewSpinUpdateTicks()).thenReturn(2);

        BlockData blockData = mock(BlockData.class);
        List<PreviewSampleService.PreviewBlock> sample =
                List.of(new PreviewSampleService.PreviewBlock(0.0D, 0.0D, 0.0D, blockData));
        when(sampleService.sampleSpawnSurface(runWorld)).thenReturn(sample);
        when(sampleService.orderPreviewBlocksForBuild(sample)).thenReturn(sample);
        when(sampleService.formatBiomeName(Biome.PLAINS)).thenReturn("Plains");

        when(previewWorld.spawn(
                        org.mockito.ArgumentMatchers.any(Location.class),
                        org.mockito.ArgumentMatchers.eq(BlockDisplay.class),
                        org.mockito.ArgumentMatchers.<Consumer<? super BlockDisplay>>any()))
                .thenAnswer(invocation -> {
                    BlockDisplay display = mock(BlockDisplay.class);
                    when(display.getUniqueId()).thenReturn(UUID.randomUUID());
                    @SuppressWarnings("unchecked")
                    Consumer<BlockDisplay> consumer = invocation.getArgument(2);
                    consumer.accept(display);
                    return display;
                });
        when(previewWorld.spawn(
                        org.mockito.ArgumentMatchers.any(Location.class),
                        org.mockito.ArgumentMatchers.eq(TextDisplay.class),
                        org.mockito.ArgumentMatchers.<Consumer<? super TextDisplay>>any()))
                .thenAnswer(invocation -> {
                    TextDisplay display = mock(TextDisplay.class);
                    UUID id = UUID.randomUUID();
                    when(display.getUniqueId()).thenReturn(id);
                    @SuppressWarnings("unchecked")
                    Consumer<TextDisplay> consumer = invocation.getArgument(2);
                    consumer.accept(display);
                    return display;
                });

        PreviewOrchestratorService service = newService(
                plugin,
                config,
                sampleService,
                anchorService,
                runtimeService,
                entityService,
                () -> worldReset,
                () -> false);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(org.mockito.ArgumentMatchers.any(UUID.class)))
                    .thenReturn(null);
            invokeVoid(service, "rebuildLobbyPreview");
        }

        PreviewState state = getPreviewState(service);
        assertTrue(state.initialPreviewDisplayed);
        assertTrue(state.lobbyPreviewDisplays.size() == 1);
        assertTrue(state.previewSeedDisplayId != null);
    }

    @Test
    void rebuildLobbyPreview_animatedPath_schedulesAndRunsBuildTick() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewEnabled()).thenReturn(true);

        PreviewSampleService sampleService = mock(PreviewSampleService.class);
        PreviewAnchorService anchorService = mock(PreviewAnchorService.class);
        PreviewRuntimeService runtimeService = mock(PreviewRuntimeService.class);
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        WorldResetManager worldReset = mock(WorldResetManager.class);

        World runWorld = mock(World.class);
        World previewWorld = mock(World.class);
        Location anchor = new Location(previewWorld, 1.0D, 70.0D, 1.0D);
        Location runSpawn = new Location(runWorld, 0.0D, 64.0D, 0.0D);
        when(runWorld.getSpawnLocation()).thenReturn(runSpawn);
        when(runWorld.getBiome(0, 64, 0)).thenReturn(Biome.PLAINS);

        when(worldReset.getCurrentOverworld()).thenReturn(runWorld);
        when(worldReset.isCurrentOverworldDiscoVariant()).thenReturn(false);
        when(anchorService.resolvePreviewAnchor()).thenReturn(anchor);
        when(runtimeService.getPreviewBlockScale()).thenReturn(1.0D);
        when(runtimeService.getPreviewSpinUpdateTicks()).thenReturn(1);

        BlockData blockData = mock(BlockData.class);
        List<PreviewSampleService.PreviewBlock> sample =
                List.of(new PreviewSampleService.PreviewBlock(0.0D, 0.0D, 0.0D, blockData));
        when(sampleService.sampleSpawnSurface(runWorld)).thenReturn(sample);
        when(sampleService.orderPreviewBlocksForBuild(sample)).thenReturn(sample);
        when(sampleService.calculateEasedSpawnTarget(1, 0, 45)).thenReturn(1);
        when(sampleService.formatBiomeName(Biome.PLAINS)).thenReturn("Plains");

        when(previewWorld.spawn(
                        org.mockito.ArgumentMatchers.any(Location.class),
                        org.mockito.ArgumentMatchers.eq(BlockDisplay.class),
                        org.mockito.ArgumentMatchers.<Consumer<? super BlockDisplay>>any()))
                .thenAnswer(invocation -> {
                    BlockDisplay display = mock(BlockDisplay.class);
                    when(display.getUniqueId()).thenReturn(UUID.randomUUID());
                    when(display.isValid()).thenReturn(true);
                    @SuppressWarnings("unchecked")
                    Consumer<BlockDisplay> consumer = invocation.getArgument(2);
                    consumer.accept(display);
                    return display;
                });
        when(previewWorld.spawn(
                        org.mockito.ArgumentMatchers.any(Location.class),
                        org.mockito.ArgumentMatchers.eq(TextDisplay.class),
                        org.mockito.ArgumentMatchers.<Consumer<? super TextDisplay>>any()))
                .thenAnswer(invocation -> {
                    TextDisplay display = mock(TextDisplay.class);
                    UUID id = UUID.randomUUID();
                    when(display.getUniqueId()).thenReturn(id);
                    when(display.isValid()).thenReturn(true);
                    @SuppressWarnings("unchecked")
                    Consumer<TextDisplay> consumer = invocation.getArgument(2);
                    consumer.accept(display);
                    return display;
                });

        PreviewOrchestratorService service = newService(
                plugin,
                config,
                sampleService,
                anchorService,
                runtimeService,
                entityService,
                () -> worldReset,
                () -> false);
        getPreviewState(service).initialPreviewDisplayed = true;

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask fallTask = mock(BukkitTask.class);
        BukkitTask buildTask = mock(BukkitTask.class);
        List<Runnable> scheduledTicks = new ArrayList<>();
        AtomicInteger runTaskTimerCalls = new AtomicInteger(0);

        when(scheduler.runTaskTimer(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L)))
                .thenAnswer(invocation -> {
                    scheduledTicks.add(invocation.getArgument(1));
                    return runTaskTimerCalls.getAndIncrement() == 0 ? fallTask : buildTask;
                });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getEntity(org.mockito.ArgumentMatchers.any(UUID.class)))
                    .thenReturn(null);

            invokeVoid(service, "rebuildLobbyPreview");
            scheduledTicks.get(1).run();
        }

        verify(runtimeService).playPreviewConstructionTickSounds(anchor, 1);
        verify(runtimeService).playPreviewConstructionCompleteSound(anchor);
        verify(buildTask).cancel();
    }

    @Test
    void refreshLobbyPreview_skipsSchedulingWhileDestroying() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        getPreviewState(service).previewDestroying = true;

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.refreshLobbyPreview();
        }

        verifyNoInteractions(scheduler);
    }

    @Test
    void playPreviewDestroyAnimationThenReset_resetsImmediatelyWhenNoEntries() {
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        CommandSender sender = mock(CommandSender.class);

        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> worldResetManager,
                () -> false);

        service.playPreviewDestroyAnimationThenReset(sender);

        verify(worldResetManager).resetThreeWorlds(sender);
    }

    @Test
    void playPreviewDestroyAnimationThenReset_returnsWhenManagerMissingOrAlreadyDestroying() throws Exception {
        PreviewOrchestratorService missingManagerService = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        missingManagerService.playPreviewDestroyAnimationThenReset(mock(CommandSender.class));

        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        PreviewOrchestratorService destroyingService = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> worldResetManager,
                () -> false);
        getPreviewState(destroyingService).previewDestroying = true;

        destroyingService.playPreviewDestroyAnimationThenReset(mock(CommandSender.class));

        verify(worldResetManager, never()).resetThreeWorlds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshLobbyPreview_schedulesWorkWhenNotDestroying() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.refreshLobbyPreview();
        }

        verify(scheduler)
                .runTask(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void removeLobbyBlockDisplayEntities_handlesMissingAndPresentLobbyWorld() {
        PreviewEntityService entityService = mock(PreviewEntityService.class);

        PreviewOrchestratorService missingManagerService = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> null,
                () -> false);
        missingManagerService.removeLobbyBlockDisplayEntities();

        WorldResetManager managerWithNullSpawn = mock(WorldResetManager.class);
        when(managerWithNullSpawn.getConfiguredLimboSpawn()).thenReturn(null);
        PreviewOrchestratorService nullSpawnService = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> managerWithNullSpawn,
                () -> false);
        nullSpawnService.removeLobbyBlockDisplayEntities();

        World world = mock(World.class);
        WorldResetManager manager = mock(WorldResetManager.class);
        when(manager.getConfiguredLimboSpawn()).thenReturn(new Location(world, 0.0D, 64.0D, 0.0D));
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> manager,
                () -> false);

        service.removeLobbyBlockDisplayEntities();

        verify(entityService).removeTaggedPreviewEntities(world, "deepcore_preview_entity");
    }

    @Test
    void hasLiveLobbyPreviewEntities_prefersSeedLabelThenFallsBackToDisplayList() throws Exception {
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;

        when(entityService.isLive(seedId)).thenReturn(true);
        assertTrue(service.hasLiveLobbyPreviewEntities());

        when(entityService.isLive(seedId)).thenReturn(false);
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(UUID.randomUUID(), 0.0D, 0.0D, 0.0D));
        when(entityService.hasAnyLiveAndPruneInvalid(
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertTrue(service.hasLiveLobbyPreviewEntities());
        assertNull(state.previewSeedDisplayId);
    }

    @Test
    void isPreviewEnabled_andIsPreviewDestroying_reflectCurrentState() throws Exception {
        ChallengeConfigView configView = mock(ChallengeConfigView.class);
        when(configView.previewEnabled()).thenReturn(true);

        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                configView,
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        assertTrue(service.isPreviewEnabled());
        assertFalse(service.isPreviewDestroying());

        getPreviewState(service).previewDestroying = true;
        assertTrue(service.isPreviewDestroying());
    }

    @Test
    void clearLobbyPreviewEntities_cancelsTasksAndRemovesTrackedEntities() throws Exception {
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> null,
                () -> false);

        setTaskField(service, "previewBuildTask", mock(BukkitTask.class));
        setTaskField(service, "previewFallTask", mock(BukkitTask.class));
        setTaskField(service, "previewDestroyTask", mock(BukkitTask.class));
        setTaskField(service, "previewSeedRevealTask", mock(BukkitTask.class));
        setTaskField(service, "previewSpinTask", mock(BukkitTask.class));

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        UUID displayId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(displayId, 0.0D, 0.0D, 0.0D));
        state.activeDestroyPreviewEntries.add(new DestroyPreviewEntry(UUID.randomUUID(), 0.0D, 0.0D, 0.0D));
        state.activeFallingPreviewDisplays.add(new FallingPreviewEntry(UUID.randomUUID(), 0L, 1L, 1.0D, 1.0D, 0.0F));
        state.previewDestroying = true;
        state.previewSeedRevealPending = true;
        state.previewSeedText = "123";
        state.previewBiomeText = "plains";

        service.clearLobbyPreviewEntities();

        verify(entityService).removeLive(seedId);
        verify(entityService).removeLive(displayId);
        assertTrue(state.lobbyPreviewDisplays.isEmpty());
        assertTrue(state.activeDestroyPreviewEntries.isEmpty());
        assertTrue(state.activeFallingPreviewDisplays.isEmpty());
        assertNull(state.previewSeedDisplayId);
        assertFalse(state.previewDestroying);
    }

    @Test
    void playPreviewDestroyAnimationThenReset_withActiveEntries_startsDestroyTask() throws Exception {
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> worldResetManager,
                () -> false);

        PreviewState state = getPreviewState(service);
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(UUID.randomUUID(), 0.0D, 0.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(Runnable.class),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(task);

            service.playPreviewDestroyAnimationThenReset(mock(CommandSender.class));
        }

        assertTrue(state.previewDestroying);
        verify(scheduler)
                .runTaskTimer(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L));
        verify(worldResetManager, never()).resetThreeWorlds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshLobbyPreview_runningPhase_clearsTrackedPreviewEntities() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> mock(WorldResetManager.class),
                () -> true);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        UUID displayId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(displayId, 0.0D, 0.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            org.mockito.Mockito.doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.any(Runnable.class));

            service.refreshLobbyPreview();
        }

        verify(entityService).removeLive(seedId);
        verify(entityService).removeLive(displayId);
        assertTrue(state.lobbyPreviewDisplays.isEmpty());
        assertNull(state.previewSeedDisplayId);
    }

    @Test
    void refreshLobbyPreview_disabledPreview_clearsTrackedPreviewEntities() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewEnabled()).thenReturn(false);

        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                plugin,
                config,
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> mock(WorldResetManager.class),
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        UUID displayId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(displayId, 1.0D, 2.0D, 3.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            org.mockito.Mockito.doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.any(Runnable.class));

            service.refreshLobbyPreview();
        }

        verify(entityService).removeLive(seedId);
        verify(entityService).removeLive(displayId);
        assertTrue(state.lobbyPreviewDisplays.isEmpty());
    }

    @Test
    void refreshLobbyPreview_missingRunWorldOrAnchor_clearsEntities() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewEnabled()).thenReturn(true);

        WorldResetManager worldReset = mock(WorldResetManager.class);
        when(worldReset.getCurrentOverworld()).thenReturn(null);

        PreviewAnchorService anchorService = mock(PreviewAnchorService.class);
        when(anchorService.resolvePreviewAnchor()).thenReturn(new Location(mock(World.class), 0.0D, 64.0D, 0.0D));

        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                plugin,
                config,
                mock(PreviewSampleService.class),
                anchorService,
                mock(PreviewRuntimeService.class),
                entityService,
                () -> worldReset,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID displayId = UUID.randomUUID();
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(displayId, 0.0D, 0.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            org.mockito.Mockito.doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(org.mockito.ArgumentMatchers.eq(plugin), org.mockito.ArgumentMatchers.any(Runnable.class));

            service.refreshLobbyPreview();
        }

        verify(entityService).removeLive(displayId);
        assertTrue(state.lobbyPreviewDisplays.isEmpty());
    }

    @Test
    void hasLiveLobbyPreviewEntities_returnsFalseWhenSeedDeadAndNoLiveDisplays() throws Exception {
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        when(entityService.isLive(seedId)).thenReturn(false);
        when(entityService.hasAnyLiveAndPruneInvalid(
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        assertFalse(service.hasLiveLobbyPreviewEntities());
        assertNull(state.previewSeedDisplayId);
    }

    @Test
    void completeSeedRevealNow_setsPlainSeedLabelWhenVisible() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "12345";
        state.previewDiscoLabelMode = false;
        state.previewSeedRevealPending = true;
        state.previewSeedRevealDurationTicks = 8;

        TextDisplay label = mock(TextDisplay.class);
        when(label.isValid()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(label);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of());

            invokeVoid(service, "completeSeedRevealNow");
        }

        assertFalse(state.previewSeedRevealPending);
        org.junit.jupiter.api.Assertions.assertEquals(state.previewSeedText.length(), state.previewSeedRevealedDigits);
        org.junit.jupiter.api.Assertions.assertEquals(
                state.previewSeedRevealDurationTicks, state.previewSeedRevealElapsedTicks);
        verify(label).text(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completeSeedRevealNow_setsDiscoLabelWhenEnabled() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "JACKPOT";
        state.previewDiscoLabelMode = true;

        TextDisplay label = mock(TextDisplay.class);
        when(label.isValid()).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(label);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of());

            invokeVoid(service, "completeSeedRevealNow");
        }

        verify(label).text(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startSeedRevealIfPending_marksPendingFalseWhenSeedEntityIsNotTextDisplay() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "777";
        state.previewSeedRevealPending = true;

        Entity genericEntity = mock(Entity.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(genericEntity);
            invokeVoid(service, "startSeedRevealIfPending", int.class, 5);
        }

        assertFalse(state.previewSeedRevealPending);
    }

    @Test
    void isSneakingPlayerNearPreview_requiresSneakingSameWorldAndRadius() throws Exception {
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewActiveRadius()).thenReturn(6.0D);

        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                config,
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        World previewWorld = mock(World.class);
        UUID previewWorldId = UUID.randomUUID();
        when(previewWorld.getUID()).thenReturn(previewWorldId);

        PreviewState state = getPreviewState(service);
        state.previewAnchor = new Location(previewWorld, 0.0D, 64.0D, 0.0D);

        Player sneakingNear = mock(Player.class);
        when(sneakingNear.isSneaking()).thenReturn(true);
        when(sneakingNear.getWorld()).thenReturn(previewWorld);
        when(sneakingNear.getLocation()).thenReturn(new Location(previewWorld, 1.0D, 64.0D, 1.0D));

        Player farOrDifferent = mock(Player.class);
        when(farOrDifferent.isSneaking()).thenReturn(true);
        World differentWorld = mock(World.class);
        when(differentWorld.getUID()).thenReturn(UUID.randomUUID());
        when(farOrDifferent.getWorld()).thenReturn(differentWorld);
        when(farOrDifferent.getLocation()).thenReturn(new Location(differentWorld, 0.0D, 64.0D, 0.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(sneakingNear, farOrDifferent));
            assertTrue(invokeBoolean(service, "isSneakingPlayerNearPreview"));
        }
    }

    @Test
    void removeLobbyBlockDisplayEntities_returnsWhenSpawnWorldMissing() {
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        WorldResetManager manager = mock(WorldResetManager.class);
        when(manager.getConfiguredLimboSpawn()).thenReturn(new Location(null, 0.0D, 64.0D, 0.0D));

        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                entityService,
                () -> manager,
                () -> false);

        service.removeLobbyBlockDisplayEntities();

        verifyNoInteractions(entityService);
    }

    @Test
    void completeSeedRevealNow_noopsWhenSeedTextMissing() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        state.previewSeedText = null;
        state.previewSeedRevealPending = true;

        invokeVoid(service, "completeSeedRevealNow");

        assertTrue(state.previewSeedRevealPending);
    }

    @Test
    void startSeedRevealIfPending_revealsSeedAndUpdatesLabelOnTick() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "2468";
        state.previewSeedRevealPending = true;
        state.previewDiscoLabelMode = false;

        TextDisplay label = mock(TextDisplay.class);
        when(label.isValid()).thenReturn(true);

        ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(label);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of());
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.eq(plugin),
                            tickCaptor.capture(),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(task);

            invokeVoid(service, "startSeedRevealIfPending", int.class, 1);
            tickCaptor.getValue().run();
        }

        assertFalse(state.previewSeedRevealPending);
        org.junit.jupiter.api.Assertions.assertEquals(4, state.previewSeedRevealedDigits);
        verify(label).text(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startSeedRevealIfPending_cancelsTaskWhenEntityDisappearsDuringTick() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "99";
        state.previewSeedRevealPending = true;

        TextDisplay initialLabel = mock(TextDisplay.class);
        when(initialLabel.isValid()).thenReturn(true);

        ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(initialLabel).thenReturn(null);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.eq(plugin),
                            tickCaptor.capture(),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(task);

            invokeVoid(service, "startSeedRevealIfPending", int.class, 5);
            tickCaptor.getValue().run();
        }

        assertFalse(state.previewSeedRevealPending);
        verify(task).cancel();
    }

    @Test
    void startPreviewFallTask_runningPhaseImmediatelyClearsAndCancels() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask fallTask = mock(BukkitTask.class);

        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> true);

        PreviewState state = getPreviewState(service);
        state.previewAnchor = new Location(mock(World.class), 0.0D, 64.0D, 0.0D);
        state.activeFallingPreviewDisplays.add(new FallingPreviewEntry(UUID.randomUUID(), 0L, 1L, 1.0D, 1.0D, 0.0F));

        ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.eq(plugin),
                            tickCaptor.capture(),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(fallTask);

            invokeVoid(service, "startPreviewFallTask");
            tickCaptor.getValue().run();
        }

        assertTrue(state.activeFallingPreviewDisplays.isEmpty());
        verify(fallTask).cancel();
        org.junit.jupiter.api.Assertions.assertNull(getTaskField(service, "previewFallTask"));
    }

    @Test
    void startPreviewFallTask_removesInvalidEntriesAndCancelsWhenNoBuildTask() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewRuntimeService runtimeService = mock(PreviewRuntimeService.class);
        when(runtimeService.getPreviewBlockScale()).thenReturn(1.0D);

        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                runtimeService,
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        World previewWorld = mock(World.class);
        state.previewAnchor = new Location(previewWorld, 0.0D, 64.0D, 0.0D);
        UUID missingId = UUID.randomUUID();
        state.activeFallingPreviewDisplays.add(new FallingPreviewEntry(missingId, 0L, 1L, 1.0D, 1.0D, 0.0F));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask fallTask = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> tickCaptor = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.eq(plugin),
                            tickCaptor.capture(),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(fallTask);

            bukkit.when(() -> Bukkit.getEntity(missingId)).thenReturn(null);

            invokeVoid(service, "startPreviewFallTask");
            tickCaptor.getValue().run();
        }

        assertTrue(state.activeFallingPreviewDisplays.isEmpty());
        verify(fallTask).cancel();
        org.junit.jupiter.api.Assertions.assertNull(getTaskField(service, "previewFallTask"));
    }

    @Test
    void startSeedRevealIfPending_noopsWhenRevealNotPending() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        state.previewSeedRevealPending = false;
        state.previewSeedDisplayId = UUID.randomUUID();
        state.previewSeedText = "123";

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            invokeVoid(service, "startSeedRevealIfPending", int.class, 5);
        }

        verifyNoInteractions(scheduler);
    }

    @Test
    void startPreviewFallTask_cancelsExistingTaskBeforeSchedulingNewOne() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PreviewOrchestratorService service = newService(
                plugin,
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        BukkitTask existing = mock(BukkitTask.class);
        setTaskField(service, "previewFallTask", existing);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask replacement = mock(BukkitTask.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(
                            org.mockito.ArgumentMatchers.eq(plugin),
                            org.mockito.ArgumentMatchers.any(Runnable.class),
                            org.mockito.ArgumentMatchers.eq(0L),
                            org.mockito.ArgumentMatchers.eq(1L)))
                    .thenReturn(replacement);

            invokeVoid(service, "startPreviewFallTask");
        }

        verify(existing).cancel();
        org.junit.jupiter.api.Assertions.assertEquals(replacement, getTaskField(service, "previewFallTask"));
    }

    @Test
    void startPreviewSpinTaskIfNeeded_cancelsExistingSpinTaskAndClearsField() throws Exception {
        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                mock(ChallengeConfigView.class),
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        BukkitTask spinTask = mock(BukkitTask.class);
        setTaskField(service, "previewSpinTask", spinTask);

        invokeVoid(service, "startPreviewSpinTaskIfNeeded");

        verify(spinTask).cancel();
        org.junit.jupiter.api.Assertions.assertNull(getTaskField(service, "previewSpinTask"));
    }

    @Test
    void completeSeedRevealNow_doesNotUpdateLabelWhileSneakingViewerIsNear() throws Exception {
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewActiveRadius()).thenReturn(10.0D);

        PreviewOrchestratorService service = newService(
                mock(JavaPlugin.class),
                config,
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);

        PreviewState state = getPreviewState(service);
        UUID seedId = UUID.randomUUID();
        state.previewSeedDisplayId = seedId;
        state.previewSeedText = "12345";
        state.previewAnchor = new Location(world, 0.0D, 64.0D, 0.0D);

        TextDisplay label = mock(TextDisplay.class);
        when(label.isValid()).thenReturn(true);

        Player sneakingNear = mock(Player.class);
        when(sneakingNear.isSneaking()).thenReturn(true);
        when(sneakingNear.getWorld()).thenReturn(world);
        when(sneakingNear.getLocation()).thenReturn(new Location(world, 1.0D, 64.0D, 1.0D));

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getEntity(seedId)).thenReturn(label);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(sneakingNear));

            invokeVoid(service, "completeSeedRevealNow");
        }

        verify(label, never()).text(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void playPreviewDestroyAnimationThenReset_completesDestroyTaskAndResetsWorld() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        PreviewSampleService sampleService = mock(PreviewSampleService.class);
        PreviewAnchorService anchorService = mock(PreviewAnchorService.class);
        PreviewRuntimeService runtimeService = mock(PreviewRuntimeService.class);
        PreviewEntityService entityService = mock(PreviewEntityService.class);
        WorldResetManager worldResetManager = mock(WorldResetManager.class);
        CommandSender sender = mock(CommandSender.class);

        PreviewOrchestratorService service = newService(
                plugin,
                config,
                sampleService,
                anchorService,
                runtimeService,
                entityService,
                () -> worldResetManager,
                () -> false);

        PreviewState state = getPreviewState(service);
        UUID displayId = UUID.randomUUID();
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(displayId, 0.0D, 0.0D, 0.0D));
        World world = mock(World.class);
        state.previewAnchor = new Location(world, 0.0D, 65.0D, 0.0D);

        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getLocation()).thenReturn(new Location(world, 0.0D, 66.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask destroyTask = mock(BukkitTask.class);
        List<Runnable> scheduledTasks = new ArrayList<>();
        when(scheduler.runTaskTimer(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L)))
                .thenAnswer(invocation -> {
                    scheduledTasks.add(invocation.getArgument(1));
                    return destroyTask;
                });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getEntity(displayId)).thenReturn(entity);

            service.playPreviewDestroyAnimationThenReset(sender);
            Runnable destroyTick = scheduledTasks.get(0);
            for (int tick = 0; tick < 130 && service.isPreviewDestroying(); tick++) {
                destroyTick.run();
            }
        }

        verify(worldResetManager).resetThreeWorlds(sender);
        verify(entityService).removeLive(displayId);
        verify(destroyTask).cancel();
        assertFalse(service.isPreviewDestroying());
    }

    @Test
    void startPreviewFallTask_clearsCompletedEntriesAndSnapsToRestingPosition() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        PreviewSampleService sampleService = mock(PreviewSampleService.class);
        PreviewAnchorService anchorService = mock(PreviewAnchorService.class);
        PreviewRuntimeService runtimeService = mock(PreviewRuntimeService.class);
        when(runtimeService.getPreviewBlockScale()).thenReturn(1.0D);
        PreviewEntityService entityService = mock(PreviewEntityService.class);

        PreviewOrchestratorService service = newService(
                plugin, config, sampleService, anchorService, runtimeService, entityService, () -> null, () -> false);

        PreviewState state = getPreviewState(service);
        World world = mock(World.class);
        state.previewAnchor = new Location(world, 0.0D, 64.0D, 0.0D);
        UUID entityId = UUID.randomUUID();
        state.activeFallingPreviewDisplays.add(new FallingPreviewEntry(entityId, 0L, 1L, 1.0D, 1.0D, 0.0F));
        state.lobbyPreviewDisplays.add(new PreviewDisplayEntry(entityId, 0.0D, 0.0D, 0.0D));

        BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        when(display.getLocation()).thenReturn(new Location(world, 0.0D, 65.0D, 0.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask fallTask = mock(BukkitTask.class);
        List<Runnable> scheduledTicks = new ArrayList<>();
        when(scheduler.runTaskTimer(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L)))
                .thenAnswer(invocation -> {
                    scheduledTicks.add(invocation.getArgument(1));
                    return fallTask;
                });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getEntity(entityId)).thenReturn(display);

            invokeVoid(service, "startPreviewFallTask");
            scheduledTicks.get(0).run();
        }

        verify(fallTask).cancel();
        assertTrue(state.activeFallingPreviewDisplays.isEmpty());
        assertNull(getTaskField(service, "previewFallTask"));
    }

    @Test
    void startSeedRevealIfPending_updatesBiomeAndFinalSeedLabels() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeConfigView config = mock(ChallengeConfigView.class);
        when(config.previewActiveRadius()).thenReturn(10.0D);

        PreviewOrchestratorService service = newService(
                plugin,
                config,
                mock(PreviewSampleService.class),
                mock(PreviewAnchorService.class),
                mock(PreviewRuntimeService.class),
                mock(PreviewEntityService.class),
                () -> null,
                () -> false);

        PreviewState state = getPreviewState(service);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        state.previewAnchor = new Location(world, 0.0D, 64.0D, 0.0D);
        state.previewSeedDisplayId = UUID.randomUUID();
        state.previewSeedText = "12345";
        state.previewBiomeText = "Plains";
        state.previewSeedRevealPending = true;

        TextDisplay label = mock(TextDisplay.class);
        when(label.isValid()).thenReturn(true);

        Player sneakingPlayer = mock(Player.class);
        when(sneakingPlayer.isSneaking()).thenReturn(true);
        when(sneakingPlayer.getWorld()).thenReturn(world);
        when(sneakingPlayer.getLocation()).thenReturn(new Location(world, 1.0D, 64.0D, 1.0D));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask seedTask = mock(BukkitTask.class);
        List<Runnable> scheduledTicks = new ArrayList<>();
        when(scheduler.runTaskTimer(
                        org.mockito.ArgumentMatchers.eq(plugin),
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.eq(0L),
                        org.mockito.ArgumentMatchers.eq(1L)))
                .thenAnswer(invocation -> {
                    scheduledTicks.add(invocation.getArgument(1));
                    return seedTask;
                });

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Set.of(sneakingPlayer));
            bukkit.when(() -> Bukkit.getEntity(state.previewSeedDisplayId)).thenReturn(label);

            invokeVoid(service, "startSeedRevealIfPending", int.class, 2);
            scheduledTicks.get(0).run();
            when(sneakingPlayer.isSneaking()).thenReturn(false);
            scheduledTicks.get(0).run();
        }

        verify(label, org.mockito.Mockito.atLeastOnce()).text(org.mockito.ArgumentMatchers.any());
        assertFalse(state.previewSeedRevealPending);
        assertEquals(5, state.previewSeedRevealedDigits);
    }

    private static PreviewOrchestratorService newService(
            JavaPlugin plugin,
            ChallengeConfigView configView,
            PreviewSampleService sampleService,
            PreviewAnchorService anchorService,
            PreviewRuntimeService runtimeService,
            PreviewEntityService entityService,
            java.util.function.Supplier<WorldResetManager> worldResetSupplier,
            java.util.function.BooleanSupplier runningSupplier) {
        return new PreviewOrchestratorService(
                plugin,
                configView,
                sampleService,
                anchorService,
                runtimeService,
                entityService,
                worldResetSupplier,
                runningSupplier);
    }

    private static PreviewState getPreviewState(PreviewOrchestratorService service) throws Exception {
        Field field = PreviewOrchestratorService.class.getDeclaredField("previewState");
        field.setAccessible(true);
        return (PreviewState) field.get(service);
    }

    private static void setTaskField(PreviewOrchestratorService service, String fieldName, BukkitTask task)
            throws Exception {
        Field field = PreviewOrchestratorService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, task);
    }

    private static BukkitTask getTaskField(PreviewOrchestratorService service, String fieldName) throws Exception {
        Field field = PreviewOrchestratorService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (BukkitTask) field.get(service);
    }

    private static void invokeVoid(PreviewOrchestratorService service, String methodName) throws Exception {
        Method method = PreviewOrchestratorService.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(service);
    }

    private static void invokeVoid(PreviewOrchestratorService service, String methodName, Class<?> type, Object arg)
            throws Exception {
        Method method = PreviewOrchestratorService.class.getDeclaredMethod(methodName, type);
        method.setAccessible(true);
        method.invoke(service, arg);
    }

    private static boolean invokeBoolean(PreviewOrchestratorService service, String methodName) throws Exception {
        Method method = PreviewOrchestratorService.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (boolean) method.invoke(service);
    }
}

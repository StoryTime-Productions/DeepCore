package dev.deepcore.challenge.preview;

import dev.deepcore.challenge.config.ChallengeConfigView;
import dev.deepcore.challenge.world.WorldResetManager;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Orchestrates lobby preview lifecycle, build animation, and cleanup. */
public final class PreviewOrchestratorService {
    private static final String PREVIEW_ENTITY_TAG = "deepcore_preview_entity";
    private static final String DISCO_PREVIEW_LABEL_TEXT = "I JUST HIT THE JACKPOT";
    private static final String DISCO_PREVIEW_BIOME_TEXT = "HEY HEY HEY HEY HEY HEY HEY";

    private final JavaPlugin plugin;
    private final ChallengeConfigView configView;
    private final PreviewSampleService previewSampleService;
    private final PreviewAnchorService previewAnchorService;
    private final PreviewRuntimeService previewRuntimeService;
    private final PreviewEntityService previewEntityService;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final BooleanSupplier runningPhaseSupplier;
    private final PreviewState previewState = new PreviewState();

    private BukkitTask previewBuildTask;
    private BukkitTask previewFallTask;
    private BukkitTask previewDestroyTask;
    private BukkitTask previewSeedRevealTask;
    private BukkitTask previewSpinTask;

    /**
     * Creates the preview orchestrator service.
     *
     * @param plugin                    plugin scheduler and lifecycle owner
     * @param configView                challenge config value accessor
     * @param previewSampleService      preview terrain sample generator
     * @param previewAnchorService      preview anchor/respawn resolver service
     * @param previewRuntimeService     preview task/runtime helper service
     * @param previewEntityService      preview entity spawn/cleanup service
     * @param worldResetManagerSupplier supplier for current world reset manager
     * @param runningPhaseSupplier      supplier indicating active running phase
     *                                  state
     */
    public PreviewOrchestratorService(
            JavaPlugin plugin,
            ChallengeConfigView configView,
            PreviewSampleService previewSampleService,
            PreviewAnchorService previewAnchorService,
            PreviewRuntimeService previewRuntimeService,
            PreviewEntityService previewEntityService,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            BooleanSupplier runningPhaseSupplier) {
        this.plugin = plugin;
        this.configView = configView;
        this.previewSampleService = previewSampleService;
        this.previewAnchorService = previewAnchorService;
        this.previewRuntimeService = previewRuntimeService;
        this.previewEntityService = previewEntityService;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.runningPhaseSupplier = runningPhaseSupplier;
    }

    /** Requests an asynchronous refresh of the lobby preview if allowed. */
    public void refreshLobbyPreview() {
        if (previewState.previewDestroying) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::rebuildLobbyPreview);
    }

    /**
     * Plays the preview destroy animation and triggers a world reset on completion.
     *
     * @param sender command sender that initiated the reset request
     */
    public void playPreviewDestroyAnimationThenReset(CommandSender sender) {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return;
        }

        if (previewState.previewDestroying) {
            return;
        }

        cancelTask(previewBuildTask);
        previewBuildTask = null;
        cancelTask(previewFallTask);
        previewFallTask = null;
        cancelTask(previewSeedRevealTask);
        previewSeedRevealTask = null;
        previewState.activeFallingPreviewDisplays.clear();

        previewState.activeDestroyPreviewEntries.clear();
        for (PreviewDisplayEntry entry : previewState.lobbyPreviewDisplays) {
            previewState.activeDestroyPreviewEntries.add(new DestroyPreviewEntry(
                    entry.entityId(),
                    ThreadLocalRandom.current().nextDouble(-0.08D, 0.08D),
                    ThreadLocalRandom.current().nextDouble(0.08D, 0.18D),
                    ThreadLocalRandom.current().nextDouble(-0.08D, 0.08D)));
        }

        if (previewState.previewSeedDisplayId != null) {
            previewState.activeDestroyPreviewEntries.add(new DestroyPreviewEntry(
                    previewState.previewSeedDisplayId,
                    ThreadLocalRandom.current().nextDouble(-0.02D, 0.02D),
                    ThreadLocalRandom.current().nextDouble(0.10D, 0.18D),
                    ThreadLocalRandom.current().nextDouble(-0.02D, 0.02D)));
        }

        if (previewState.activeDestroyPreviewEntries.isEmpty()) {
            clearLobbyPreviewEntities();
            worldResetManager.resetThreeWorlds(sender);
            return;
        }

        previewState.previewDestroying = true;
        playPreviewDestroyStartSound();
        final double destroyCutoffY =
                previewState.previewAnchor != null ? previewState.previewAnchor.getY() : Double.NEGATIVE_INFINITY;
        final int maxDestroyTicks = 120;
        final int[] tick = {0};

        cancelTask(previewDestroyTask);
        previewDestroyTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (runningPhaseSupplier.getAsBoolean()) {
                                cancelTask(previewDestroyTask);
                                previewDestroyTask = null;
                                previewState.previewDestroying = false;
                                clearLobbyPreviewEntities();
                                return;
                            }

                            boolean allEntitiesBelowCutoff = true;
                            for (DestroyPreviewEntry entry : previewState.activeDestroyPreviewEntries) {
                                Entity entity = Bukkit.getEntity(entry.entityId());
                                if (entity == null || !entity.isValid()) {
                                    continue;
                                }

                                Location next = entity.getLocation()
                                        .clone()
                                        .add(entry.vx(), entry.vy() - (tick[0] * 0.02D), entry.vz());
                                entity.teleport(next, PlayerTeleportEvent.TeleportCause.PLUGIN);
                                if (next.getY() >= destroyCutoffY) {
                                    allEntitiesBelowCutoff = false;
                                }
                            }

                            tick[0]++;
                            if (allEntitiesBelowCutoff || tick[0] >= maxDestroyTicks) {
                                cancelTask(previewDestroyTask);
                                previewDestroyTask = null;
                                previewState.previewDestroying = false;
                                clearLobbyPreviewEntities();
                                worldResetManager.resetThreeWorlds(sender);
                            }
                        },
                        0L,
                        1L);
    }

    /**
     * Removes tagged preview display entities around the configured lobby spawn.
     */
    public void removeLobbyBlockDisplayEntities() {
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return;
        }

        Location lobbySpawn = worldResetManager.getConfiguredLimboSpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) {
            return;
        }

        previewEntityService.removeTaggedPreviewEntities(lobbySpawn.getWorld(), PREVIEW_ENTITY_TAG);
    }

    /** Cancels preview tasks and clears all tracked lobby preview entities. */
    public void clearLobbyPreviewEntities() {
        cancelTask(previewBuildTask);
        previewBuildTask = null;
        cancelTask(previewFallTask);
        previewFallTask = null;
        cancelTask(previewDestroyTask);
        previewDestroyTask = null;
        cancelTask(previewSeedRevealTask);
        previewSeedRevealTask = null;
        cancelTask(previewSpinTask);
        previewSpinTask = null;
        previewState.previewSpinAngleDegrees = 0.0D;
        previewState.previewAnchor = null;
        previewState.previewDestroying = false;
        previewState.previewSeedRevealPending = false;
        previewState.previewSeedText = null;
        previewState.previewBiomeText = null;
        previewState.previewDiscoLabelMode = false;
        previewState.previewSeedRevealedDigits = 0;
        previewState.previewSeedRevealElapsedTicks = 0;
        previewState.previewSeedRevealDurationTicks = 1;
        previewState.activeFallingPreviewDisplays.clear();
        previewState.activeDestroyPreviewEntries.clear();

        if (previewState.previewSeedDisplayId != null) {
            previewEntityService.removeLive(previewState.previewSeedDisplayId);
            previewState.previewSeedDisplayId = null;
        }

        for (PreviewDisplayEntry entry : previewState.lobbyPreviewDisplays) {
            previewEntityService.removeLive(entry.entityId());
        }
        previewState.lobbyPreviewDisplays.clear();
    }

    /**
     * Returns whether any tracked lobby preview entities are still alive.
     *
     * @return true when any tracked preview display entity remains valid
     */
    public boolean hasLiveLobbyPreviewEntities() {
        if (previewState.previewSeedDisplayId != null) {
            if (previewEntityService.isLive(previewState.previewSeedDisplayId)) {
                return true;
            }
            previewState.previewSeedDisplayId = null;
        }

        return previewEntityService.hasAnyLiveAndPruneInvalid(
                previewState.lobbyPreviewDisplays, PreviewDisplayEntry::entityId);
    }

    /**
     * Returns whether the destroy animation is currently in progress.
     *
     * @return true when preview destroy animation is active
     */
    public boolean isPreviewDestroying() {
        return previewState.previewDestroying;
    }

    /**
     * Returns whether preview rendering is enabled by configuration.
     *
     * @return true when preview rendering is enabled
     */
    public boolean isPreviewEnabled() {
        return configView.previewEnabled();
    }

    private void rebuildLobbyPreview() {
        if (previewState.previewDestroying) {
            return;
        }

        if (runningPhaseSupplier.getAsBoolean() || !isPreviewEnabled()) {
            clearLobbyPreviewEntities();
            return;
        }

        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        if (worldResetManager == null) {
            return;
        }

        World runWorld = worldResetManager.getCurrentOverworld();
        Location anchor = previewAnchorService.resolvePreviewAnchor();
        if (runWorld == null || anchor == null || anchor.getWorld() == null) {
            clearLobbyPreviewEntities();
            return;
        }

        boolean discoPreview = worldResetManager.isCurrentOverworldDiscoVariant();
        List<PreviewSampleService.PreviewBlock> sample = discoPreview
                ? previewSampleService.buildDiscoPreviewSample()
                : previewSampleService.sampleSpawnSurface(runWorld);
        if (discoPreview && sample.isEmpty()) {
            sample = previewSampleService.sampleSpawnSurface(runWorld);
        }
        final List<PreviewSampleService.PreviewBlock> previewSample = sample;
        clearLobbyPreviewEntities();
        previewState.previewAnchor = anchor.clone();
        previewState.previewSpinAngleDegrees = 0.0D;
        double blockScale = getPreviewBlockScale();

        if (previewSample.isEmpty()) {
            return;
        }

        List<PreviewSampleService.PreviewBlock> buildOrder =
                previewSampleService.orderPreviewBlocksForBuild(previewSample);
        if (buildOrder.isEmpty()) {
            return;
        }

        World previewWorld = anchor.getWorld();
        if (discoPreview) {
            worldResetManager.ensureDiscoPreviewEffects(anchor);
        }
        float initialRotationRadians = 0.0F;
        double fallHeight = Math.max(1.0D, blockScale * 18.0D);
        int interpolationTicks = getPreviewSpinUpdateTicks();
        Location runSpawn = runWorld.getSpawnLocation();
        Biome spawnBiome = runWorld.getBiome(runSpawn.getBlockX(), runSpawn.getBlockY(), runSpawn.getBlockZ());

        if (!previewState.initialPreviewDisplayed) {
            for (PreviewSampleService.PreviewBlock block : buildOrder) {
                double scaledRelX = block.relX() * blockScale;
                double scaledRelY = block.relY() * blockScale;
                double scaledRelZ = block.relZ() * blockScale;
                Location spawnLocation = anchor.clone().add(scaledRelX, scaledRelY, scaledRelZ);
                BlockDisplay display = previewWorld.spawn(spawnLocation, BlockDisplay.class, spawned -> {
                    spawned.setGravity(false);
                    spawned.setPersistent(false);
                    spawned.setInvulnerable(true);
                    spawned.addScoreboardTag(PREVIEW_ENTITY_TAG);
                    spawned.setBlock(block.blockData());
                    spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                    spawned.setInterpolationDelay(0);
                    spawned.setInterpolationDuration(interpolationTicks);
                    applyPreviewTransform(spawned, blockScale, initialRotationRadians, 0.0D);
                });

                previewState.lobbyPreviewDisplays.add(
                        new PreviewDisplayEntry(display.getUniqueId(), scaledRelX, scaledRelY, scaledRelZ));
            }

            spawnPreviewSeedLabel(previewWorld, anchor, blockScale, previewSample, runWorld.getSeed(), spawnBiome);
            previewState.initialPreviewDisplayed = true;
            startSeedRevealIfPending(1);
            return;
        }

        previewState.initialPreviewDisplayed = true;
        final int[] index = {0};
        final int[] elapsedTicks = {0};
        final int targetAnimationTicks = Math.max(45, Math.min(240, (int) Math.ceil(buildOrder.size() / 60.0D)));
        spawnPreviewSeedLabel(previewWorld, anchor, blockScale, previewSample, runWorld.getSeed(), spawnBiome);
        startSeedRevealIfPending(targetAnimationTicks);
        startPreviewFallTask();

        cancelTask(previewBuildTask);
        previewBuildTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (runningPhaseSupplier.getAsBoolean() || previewState.previewAnchor == null) {
                                cancelTask(previewBuildTask);
                                previewBuildTask = null;
                                return;
                            }

                            int remaining = buildOrder.size() - index[0];
                            if (remaining <= 0) {
                                cancelTask(previewBuildTask);
                                previewBuildTask = null;
                                return;
                            }

                            int clampedElapsedTicks = Math.min(targetAnimationTicks, Math.max(0, elapsedTicks[0]));
                            int targetSpawned = previewSampleService.calculateEasedSpawnTarget(
                                    buildOrder.size(), clampedElapsedTicks, targetAnimationTicks);
                            int tickBatchSize = Math.max(0, targetSpawned - index[0]);
                            int maxEndBurstPerTick = Math.max(8, (int) Math.ceil(buildOrder.size() / 22.0D));
                            if (clampedElapsedTicks >= targetAnimationTicks) {
                                tickBatchSize = Math.max(1, Math.min(buildOrder.size() - index[0], maxEndBurstPerTick));
                            } else if ((double) clampedElapsedTicks / (double) targetAnimationTicks >= 0.85D) {
                                tickBatchSize = Math.min(tickBatchSize, maxEndBurstPerTick);
                            }

                            int remainingTicksForFall = Math.max(3, targetAnimationTicks - elapsedTicks[0]);
                            long remainingMillis = Math.max(1L, remainingTicksForFall * 50L);

                            int spawnedThisTick = 0;
                            while (index[0] < buildOrder.size() && spawnedThisTick < tickBatchSize) {
                                PreviewSampleService.PreviewBlock block = buildOrder.get(index[0]++);
                                double scaledRelX = block.relX() * blockScale;
                                double scaledRelY = block.relY() * blockScale;
                                double scaledRelZ = block.relZ() * blockScale;
                                Location spawnLocation = anchor.clone().add(scaledRelX, scaledRelY, scaledRelZ);
                                BlockDisplay display =
                                        previewWorld.spawn(spawnLocation, BlockDisplay.class, spawned -> {
                                            spawned.setGravity(false);
                                            spawned.setPersistent(false);
                                            spawned.setInvulnerable(true);
                                            spawned.addScoreboardTag(PREVIEW_ENTITY_TAG);
                                            spawned.setBlock(block.blockData());
                                            spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                                            spawned.setInterpolationDelay(0);
                                            spawned.setInterpolationDuration(1);
                                            applyPreviewTransform(
                                                    spawned, blockScale, initialRotationRadians, fallHeight);
                                        });

                                previewState.activeFallingPreviewDisplays.add(new FallingPreviewEntry(
                                        display.getUniqueId(),
                                        System.currentTimeMillis(),
                                        remainingMillis,
                                        fallHeight,
                                        blockScale,
                                        initialRotationRadians));

                                previewState.lobbyPreviewDisplays.add(new PreviewDisplayEntry(
                                        display.getUniqueId(), scaledRelX, scaledRelY, scaledRelZ));
                                spawnedThisTick++;
                            }

                            if (spawnedThisTick > 0) {
                                playPreviewConstructionTickSounds(spawnedThisTick);
                            }

                            elapsedTicks[0]++;

                            if (index[0] >= buildOrder.size()) {
                                completeSeedRevealNow();
                                playPreviewConstructionCompleteSound();
                                cancelTask(previewBuildTask);
                                previewBuildTask = null;
                            }
                        },
                        0L,
                        1L);
    }

    private void startPreviewSpinTaskIfNeeded() {
        cancelTask(previewSpinTask);
        previewSpinTask = null;
    }

    private void startPreviewFallTask() {
        cancelTask(previewFallTask);
        previewFallTask = null;

        previewFallTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            if (runningPhaseSupplier.getAsBoolean() || previewState.previewAnchor == null) {
                                previewState.activeFallingPreviewDisplays.clear();
                                cancelTask(previewFallTask);
                                previewFallTask = null;
                                return;
                            }

                            long now = System.currentTimeMillis();
                            Iterator<FallingPreviewEntry> iterator =
                                    previewState.activeFallingPreviewDisplays.iterator();
                            while (iterator.hasNext()) {
                                FallingPreviewEntry falling = iterator.next();
                                Entity entity = Bukkit.getEntity(falling.entityId());
                                if (!(entity instanceof BlockDisplay display) || !display.isValid()) {
                                    iterator.remove();
                                    continue;
                                }

                                long elapsed = Math.max(0L, now - falling.startTimeMillis());
                                double progress = falling.durationMillis() <= 0L
                                        ? 1.0D
                                        : Math.min(1.0D, (double) elapsed / (double) falling.durationMillis());
                                double yOffset = falling.fallHeight() * (1.0D - (progress * progress));
                                applyPreviewTransform(
                                        display, falling.blockScale(), falling.rotationRadians(), yOffset);

                                if (progress >= 1.0D) {
                                    iterator.remove();
                                }
                            }

                            if (previewState.activeFallingPreviewDisplays.isEmpty() && previewBuildTask == null) {
                                snapPreviewBlocksToRestingPosition();
                                cancelTask(previewFallTask);
                                previewFallTask = null;
                            }
                        },
                        0L,
                        1L);
    }

    private void snapPreviewBlocksToRestingPosition() {
        double blockScale = getPreviewBlockScale();
        for (PreviewDisplayEntry entry : previewState.lobbyPreviewDisplays) {
            Entity entity = Bukkit.getEntity(entry.entityId());
            if (!(entity instanceof BlockDisplay display) || !display.isValid()) {
                continue;
            }

            applyPreviewTransform(display, blockScale, 0.0F, 0.0D);
        }
    }

    private void startSeedRevealIfPending(int revealDurationTicks) {
        if (!previewState.previewSeedRevealPending
                || previewState.previewSeedDisplayId == null
                || previewState.previewSeedText == null) {
            return;
        }

        previewState.previewSeedRevealDurationTicks = Math.max(1, revealDurationTicks);
        previewState.previewSeedRevealElapsedTicks = 0;

        Entity seedEntity = Bukkit.getEntity(previewState.previewSeedDisplayId);
        if (!(seedEntity instanceof TextDisplay label) || !label.isValid()) {
            previewState.previewSeedRevealPending = false;
            return;
        }

        cancelTask(previewSeedRevealTask);
        previewSeedRevealTask = null;
        previewSeedRevealTask = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            Entity currentEntity = Bukkit.getEntity(previewState.previewSeedDisplayId);
                            if (!(currentEntity instanceof TextDisplay currentLabel) || !currentLabel.isValid()) {
                                cancelTask(previewSeedRevealTask);
                                previewSeedRevealTask = null;
                                previewState.previewSeedRevealPending = false;
                                return;
                            }

                            if (previewState.previewSeedRevealPending) {
                                previewState.previewSeedRevealElapsedTicks = Math.min(
                                        previewState.previewSeedRevealDurationTicks,
                                        previewState.previewSeedRevealElapsedTicks + 1);
                                double revealProgress = (double) previewState.previewSeedRevealElapsedTicks
                                        / (double) previewState.previewSeedRevealDurationTicks;
                                previewState.previewSeedRevealedDigits = Math.min(
                                        previewState.previewSeedText.length(), Math.max(0, (int)
                                                Math.ceil(previewState.previewSeedText.length() * revealProgress)));
                                if (previewState.previewSeedRevealedDigits >= previewState.previewSeedText.length()) {
                                    previewState.previewSeedRevealPending = false;
                                }
                            }

                            boolean showBiome = isSneakingPlayerNearPreview();
                            if (showBiome && previewState.previewBiomeText != null) {
                                if (previewState.previewDiscoLabelMode) {
                                    currentLabel.text(
                                            Component.text(previewState.previewBiomeText, NamedTextColor.AQUA));
                                } else {
                                    currentLabel.text(Component.text(
                                            "Biome: " + previewState.previewBiomeText, NamedTextColor.AQUA));
                                }
                                return;
                            }

                            if (!previewState.previewSeedRevealPending) {
                                if (previewState.previewDiscoLabelMode) {
                                    currentLabel.text(
                                            Component.text(previewState.previewSeedText, NamedTextColor.AQUA));
                                } else {
                                    currentLabel.text(Component.text(
                                            "Seed: " + previewState.previewSeedText, NamedTextColor.AQUA));
                                }
                                return;
                            }

                            String revealed =
                                    previewState.previewSeedText.substring(0, previewState.previewSeedRevealedDigits);
                            String unrevealed =
                                    previewState.previewSeedText.substring(previewState.previewSeedRevealedDigits);
                            if (previewState.previewDiscoLabelMode) {
                                currentLabel.text(Component.text(revealed, NamedTextColor.AQUA)
                                        .decoration(TextDecoration.OBFUSCATED, false)
                                        .append(Component.text(unrevealed, NamedTextColor.AQUA)
                                                .decoration(TextDecoration.OBFUSCATED, true)));
                            } else {
                                currentLabel.text(Component.text("Seed: ", NamedTextColor.AQUA)
                                        .append(Component.text(revealed, NamedTextColor.AQUA)
                                                .decoration(TextDecoration.OBFUSCATED, false))
                                        .append(Component.text(unrevealed, NamedTextColor.AQUA)
                                                .decoration(TextDecoration.OBFUSCATED, true)));
                            }
                        },
                        0L,
                        1L);
    }

    private void completeSeedRevealNow() {
        if (previewState.previewSeedText == null) {
            return;
        }

        previewState.previewSeedRevealPending = false;
        previewState.previewSeedRevealedDigits = previewState.previewSeedText.length();
        previewState.previewSeedRevealElapsedTicks = previewState.previewSeedRevealDurationTicks;

        Entity seedEntity =
                previewState.previewSeedDisplayId != null ? Bukkit.getEntity(previewState.previewSeedDisplayId) : null;
        if (seedEntity instanceof TextDisplay label && label.isValid() && !isSneakingPlayerNearPreview()) {
            if (previewState.previewDiscoLabelMode) {
                label.text(Component.text(previewState.previewSeedText, NamedTextColor.AQUA));
            } else {
                label.text(Component.text("Seed: " + previewState.previewSeedText, NamedTextColor.AQUA));
            }
        }
    }

    private boolean isSneakingPlayerNearPreview() {
        if (previewState.previewAnchor == null || previewState.previewAnchor.getWorld() == null) {
            return false;
        }

        double radius = Math.max(4.0D, configView.previewActiveRadius());
        double radiusSquared = radius * radius;
        World previewWorld = previewState.previewAnchor.getWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isSneaking()) {
                continue;
            }
            if (!player.getWorld().getUID().equals(previewWorld.getUID())) {
                continue;
            }
            if (player.getLocation().distanceSquared(previewState.previewAnchor) <= radiusSquared) {
                return true;
            }
        }

        return false;
    }

    private void spawnPreviewSeedLabel(
            World previewWorld,
            Location anchor,
            double blockScale,
            List<PreviewSampleService.PreviewBlock> sample,
            long seed,
            Biome spawnBiome) {
        double maxRelY = 0.0D;
        for (PreviewSampleService.PreviewBlock block : sample) {
            if (block.relY() > maxRelY) {
                maxRelY = block.relY();
            }
        }

        double labelYOffset = (maxRelY * blockScale) + Math.max(0.6D, blockScale * 4.2D);
        Location labelLocation = anchor.clone().add(0.0D, labelYOffset, 0.0D);
        TextDisplay label = previewWorld.spawn(labelLocation, TextDisplay.class, spawned -> {
            spawned.setGravity(false);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.addScoreboardTag(PREVIEW_ENTITY_TAG);
            spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            spawned.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            spawned.setShadowed(false);
            spawned.setSeeThrough(true);
            spawned.setDefaultBackground(false);
            spawned.setLineWidth(200);
            spawned.text(Component.empty());
            spawned.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Quaternionf(),
                    new Vector3f(0.35F, 0.35F, 0.35F),
                    new Quaternionf()));
        });

        previewState.previewSeedDisplayId = label.getUniqueId();
        WorldResetManager worldResetManager = worldResetManagerSupplier.get();
        previewState.previewDiscoLabelMode =
                worldResetManager != null && worldResetManager.isCurrentOverworldDiscoVariant();
        if (previewState.previewDiscoLabelMode) {
            previewState.previewSeedText = DISCO_PREVIEW_LABEL_TEXT;
            previewState.previewBiomeText = DISCO_PREVIEW_BIOME_TEXT;
        } else {
            previewState.previewSeedText = Long.toString(seed);
            previewState.previewBiomeText = previewSampleService.formatBiomeName(spawnBiome);
        }
        previewState.previewSeedRevealPending = true;
        previewState.previewSeedRevealedDigits = 0;
        previewState.previewSeedRevealElapsedTicks = 0;
        previewState.previewSeedRevealDurationTicks = 1;
    }

    private double getPreviewBlockScale() {
        return previewRuntimeService.getPreviewBlockScale();
    }

    private int getPreviewSpinUpdateTicks() {
        return previewRuntimeService.getPreviewSpinUpdateTicks();
    }

    private void playPreviewConstructionTickSounds(int spawnedThisTick) {
        previewRuntimeService.playPreviewConstructionTickSounds(previewState.previewAnchor, spawnedThisTick);
    }

    private void playPreviewConstructionCompleteSound() {
        previewRuntimeService.playPreviewConstructionCompleteSound(previewState.previewAnchor);
    }

    private void playPreviewDestroyStartSound() {
        previewRuntimeService.playPreviewDestroyStartSound(previewState.previewAnchor);
    }

    private void applyPreviewTransform(BlockDisplay display, double blockScale, float rotationRadians, double yOffset) {
        previewRuntimeService.applyPreviewTransform(display, blockScale, rotationRadians, yOffset);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}

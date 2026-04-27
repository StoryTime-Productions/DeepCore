package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.PrepGuiPage;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.ui.PrepGuiRenderer;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import dev.deepcore.records.RunRecord;
import dev.deepcore.records.RunRecordsService;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Coordinates prep-book and prep-GUI interactions plus GUI page state.
 */
public final class PrepGuiCoordinatorService {
    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final SessionState sessionState;
    private final Set<UUID> readyPlayers;
    private final Set<UUID> participants;
    private final ParticipantsView participantsView;
    private final ChallengeManager challengeManager;
    private final PrepGuiRenderer prepGuiRenderer;
    private final PrepBookService prepBookService;
    private final PrepGuiFlowService prepGuiFlowService;
    private final PrepReadinessService prepReadinessService;
    private final PreviewOrchestratorService previewOrchestratorService;
    private final Supplier<WorldResetManager> worldResetManagerSupplier;
    private final Supplier<RunRecordsService> recordsServiceSupplier;
    private final RunUiFormattingService runUiFormattingService;
    private final BooleanSupplier isDiscoPreviewBlockingChallengeStart;
    private final Runnable announceDiscoPreviewStartBlocked;
    private final Runnable startRun;
    private final String prepGuiTitle;
    private final DateTimeFormatter runHistoryDateFormatter;

    private final Map<UUID, PrepGuiPage> prepGuiPages = new HashMap<>();
    private final Map<UUID, Integer> runHistoryPageIndices = new HashMap<>();

    /**
     * Creates a prep GUI coordination service.
     *
     * @param plugin                               plugin scheduler and lifecycle
     *                                             owner
     * @param log                                  challenge logger for player/admin
     *                                             messaging
     * @param sessionState                         mutable session phase/state
     *                                             container
     * @param readyPlayers                         players marked ready during prep
     * @param participants                         active run participants
     * @param participantsView                     participant roster and counts
     *                                             view
     * @param challengeManager                     challenge settings and component
     *                                             manager
     * @param prepGuiRenderer                      prep GUI rendering service
     * @param prepBookService                      prep guide book service
     * @param prepGuiFlowService                   prep GUI click-flow handler
     *                                             service
     * @param prepReadinessService                 readiness/countdown coordinator
     *                                             service
     * @param previewOrchestratorService           lobby preview orchestration
     *                                             service
     * @param worldResetManagerSupplier            supplier for current world reset
     *                                             manager
     * @param recordsServiceSupplier               supplier for run records service
     * @param runUiFormattingService               run time/label formatting service
     * @param isDiscoPreviewBlockingChallengeStart supplier for disco-start blocking
     *                                             state
     * @param announceDiscoPreviewStartBlocked     runnable announcing disco start
     *                                             block
     * @param startRun                             runnable that starts a challenge
     *                                             run
     * @param prepGuiTitle                         prep GUI inventory title text
     * @param runHistoryDateFormatter              formatter for run history date
     *                                             labels
     */
    public PrepGuiCoordinatorService(
            JavaPlugin plugin,
            DeepCoreLogger log,
            SessionState sessionState,
            Set<UUID> readyPlayers,
            Set<UUID> participants,
            ParticipantsView participantsView,
            ChallengeManager challengeManager,
            PrepGuiRenderer prepGuiRenderer,
            PrepBookService prepBookService,
            PrepGuiFlowService prepGuiFlowService,
            PrepReadinessService prepReadinessService,
            PreviewOrchestratorService previewOrchestratorService,
            Supplier<WorldResetManager> worldResetManagerSupplier,
            Supplier<RunRecordsService> recordsServiceSupplier,
            RunUiFormattingService runUiFormattingService,
            BooleanSupplier isDiscoPreviewBlockingChallengeStart,
            Runnable announceDiscoPreviewStartBlocked,
            Runnable startRun,
            String prepGuiTitle,
            DateTimeFormatter runHistoryDateFormatter) {
        this.plugin = plugin;
        this.log = log;
        this.sessionState = sessionState;
        this.readyPlayers = readyPlayers;
        this.participants = participants;
        this.participantsView = participantsView;
        this.challengeManager = challengeManager;
        this.prepGuiRenderer = prepGuiRenderer;
        this.prepBookService = prepBookService;
        this.prepGuiFlowService = prepGuiFlowService;
        this.prepReadinessService = prepReadinessService;
        this.previewOrchestratorService = previewOrchestratorService;
        this.worldResetManagerSupplier = worldResetManagerSupplier;
        this.recordsServiceSupplier = recordsServiceSupplier;
        this.runUiFormattingService = runUiFormattingService;
        this.isDiscoPreviewBlockingChallengeStart = isDiscoPreviewBlockingChallengeStart;
        this.announceDiscoPreviewStartBlocked = announceDiscoPreviewStartBlocked;
        this.startRun = startRun;
        this.prepGuiTitle = prepGuiTitle;
        this.runHistoryDateFormatter = runHistoryDateFormatter;
    }

    /**
     * Clears tracked GUI state for a player that left the server.
     *
     * @param playerId unique identifier of the player who left
     */
    public void onPlayerLeft(UUID playerId) {
        prepGuiPages.remove(playerId);
        runHistoryPageIndices.remove(playerId);
    }

    /**
     * Handles prep-book interactions and opens prep GUI during prep phase.
     *
     * @param event player interaction event to evaluate
     */
    public void handlePrepBookUse(PlayerInteractEvent event) {
        if (event.getItem() == null || !prepBookService.isPrepBook(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        if (!sessionState.is(SessionState.Phase.PREP)) {
            log.sendWarn(event.getPlayer(), "Prep is locked once everyone readies up.");
            return;
        }

        openPrepGui(event.getPlayer());
    }

    /**
     * Prevents moving prep books via click interactions.
     *
     * @param event inventory click event to evaluate
     */
    public void handleProtectedPrepBookClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (prepBookService.isPrepBook(event.getCurrentItem())
                || prepBookService.isPrepBook(event.getCursor())
                || prepBookService.isPrepBook(resolveHotbarSwapItem(player, event.getHotbarButton()))) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /**
     * Prevents moving prep books via drag interactions.
     *
     * @param event inventory drag event to evaluate
     */
    public void handleProtectedPrepBookDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (prepBookService.isPrepBook(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(prepBookService::isPrepBook)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /**
     * Prevents dropping prep books from inventory.
     *
     * @param event player drop item event to evaluate
     */
    public void handleProtectedPrepBookDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!prepBookService.isPrepBook(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        player.updateInventory();
    }

    /**
     * Prevents swapping prep books between hands.
     *
     * @param event player swap hand items event to evaluate
     */
    public void handleProtectedPrepBookSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!prepBookService.isPrepBook(event.getMainHandItem())
                && !prepBookService.isPrepBook(event.getOffHandItem())) {
            return;
        }

        event.setCancelled(true);
        player.updateInventory();
    }

    /**
     * Handles prep GUI click actions including toggles and page navigation.
     *
     * @param event inventory click event within a prep GUI
     */
    public void handlePrepGuiClick(InventoryClickEvent event) {
        if (!isPrepGui(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!sessionState.is(SessionState.Phase.PREP)) {
            player.closeInventory();
            log.sendWarn(player, "Prep is locked once everyone readies up.");
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        PrepGuiPage page = prepGuiPages.getOrDefault(player.getUniqueId(), PrepGuiPage.CATEGORIES);
        prepGuiFlowService.handleClick(
                player,
                slot,
                page,
                runHistoryPageIndices,
                () -> {
                    toggleReady(player);
                    refreshOpenPrepGuis();
                    prepReadinessService.tryStartCountdown(
                            readyPlayers,
                            participants,
                            isDiscoPreviewBlockingChallengeStart,
                            announceDiscoPreviewStartBlocked,
                            startRun);
                },
                this::refreshOpenPrepGuis,
                targetPage -> openPrepGui(player, targetPage),
                player::closeInventory,
                () -> {
                    if (worldResetManagerSupplier.get() == null) {
                        log.sendError(player, "World reset manager is not available.");
                        return;
                    }

                    if (previewOrchestratorService.isPreviewDestroying()) {
                        log.sendWarn(player, "Preview destroy animation is already running.");
                        return;
                    }

                    player.closeInventory();
                    previewOrchestratorService.playPreviewDestroyAnimationThenReset(player);
                },
                () -> {
                    player.closeInventory();
                    player.performCommand("challenge train");
                });
    }

    /**
     * Cancels drag interactions in prep GUI inventories.
     *
     * @param event inventory drag event for possible prep GUI interaction
     */
    public void handlePrepGuiDrag(InventoryDragEvent event) {
        if (isPrepGui(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    /**
     * Restores prep-book state after prep GUI closes during prep phase.
     *
     * @param event inventory close event for a prep GUI
     */
    public void handlePrepGuiClose(InventoryCloseEvent event) {
        if (!isPrepGui(event.getView().getTitle())) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!sessionState.is(SessionState.Phase.PREP)) {
            return;
        }

        // Run next tick to avoid competing with close event inventory state changes.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !sessionState.is(SessionState.Phase.PREP)) {
                return;
            }

            String trainingWorldName = "deepcore_gym";
            if (plugin.getConfig() != null) {
                trainingWorldName = plugin.getConfig().getString("training.world", "deepcore_gym");
            }
            if (player.getWorld() != null && player.getWorld().getName().equalsIgnoreCase(trainingWorldName)) {
                prepBookService.removeFromInventory(player);
                return;
            }

            prepBookService.giveIfMissing(player);
            player.updateInventory();
        });
    }

    /** Re-renders prep GUI for all players currently viewing it. */
    public void refreshOpenPrepGuis() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isPrepGui(online.getOpenInventory().getTitle())) {
                PrepGuiPage page = prepGuiPages.getOrDefault(online.getUniqueId(), PrepGuiPage.CATEGORIES);
                openPrepGui(online, page);
            }
        }
    }

    private void openPrepGui(Player player) {
        openPrepGui(player, PrepGuiPage.CATEGORIES);
    }

    private void openPrepGui(Player player, PrepGuiPage page) {
        Inventory inventory = Bukkit.createInventory(player, 54, prepGuiTitle);
        prepGuiPages.put(player.getUniqueId(), page);
        prepGuiRenderer.applyPrepGuiDecorations(inventory);

        switch (page) {
            case CATEGORIES -> prepGuiRenderer.populateCategoriesPage(
                    inventory,
                    readyPlayers.contains(player.getUniqueId()),
                    readyPlayers.size(),
                    participantsView.onlineCount(),
                    previewOrchestratorService.isPreviewEnabled());
            case INVENTORY -> prepGuiRenderer.populateInventoryPage(
                    inventory,
                    challengeManager,
                    readyPlayers.contains(player.getUniqueId()),
                    readyPlayers.size(),
                    participantsView.onlineCount(),
                    previewOrchestratorService.isPreviewEnabled());
            case HEALTH -> prepGuiRenderer.populateHealthPage(
                    inventory,
                    challengeManager,
                    readyPlayers.contains(player.getUniqueId()),
                    readyPlayers.size(),
                    participantsView.onlineCount(),
                    previewOrchestratorService.isPreviewEnabled());
            case RUN_HISTORY -> {
                int requestedPage = runHistoryPageIndices.getOrDefault(player.getUniqueId(), 0);
                RunRecordsService recordsService = recordsServiceSupplier.get();
                List<RunRecord> records = recordsService != null ? recordsService.getAllRecords() : List.of();
                int resolvedPage = prepGuiRenderer.populateRunHistoryPage(
                        inventory,
                        requestedPage,
                        records,
                        runHistoryDateFormatter,
                        runUiFormattingService::formatSplitDuration);
                runHistoryPageIndices.put(player.getUniqueId(), resolvedPage);
            }
        }

        player.openInventory(inventory);
    }

    private void toggleReady(Player player) {
        UUID playerId = player.getUniqueId();
        if (readyPlayers.contains(playerId)) {
            readyPlayers.remove(playerId);
            log.sendInfo(player, "You are no longer ready.");
        } else {
            readyPlayers.add(playerId);
            log.sendInfo(player, "You are ready.");
        }
    }

    private boolean isPrepGui(String title) {
        return prepGuiTitle.equals(title);
    }

    private ItemStack resolveHotbarSwapItem(Player player, int hotbarButton) {
        if (hotbarButton < 0 || hotbarButton > 8) {
            return null;
        }
        return player.getInventory().getItem(hotbarButton);
    }
}

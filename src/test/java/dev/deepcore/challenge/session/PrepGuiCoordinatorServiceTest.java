package dev.deepcore.challenge.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.PrepGuiPage;
import dev.deepcore.challenge.preview.PreviewOrchestratorService;
import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.ui.PrepGuiRenderer;
import dev.deepcore.challenge.world.WorldResetManager;
import dev.deepcore.logging.DeepCoreLogger;
import dev.deepcore.records.RunRecord;
import dev.deepcore.records.RunRecordsService;
import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class PrepGuiCoordinatorServiceTest {

    @Test
    void handlePrepBookUse_opensGuiInPrep_andWarnsOutsidePrep() {
        Fixture f = new Fixture();
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        ItemStack item = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(item);
        when(f.prepBookService.isPrepBook(item)).thenReturn(true);

        Inventory inventory = mock(Inventory.class);
        when(f.participantsView.onlineCount()).thenReturn(1);
        when(f.previewOrchestratorService.isPreviewEnabled()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(player, 54, f.prepGuiTitle))
                    .thenReturn(inventory);

            f.sessionState.setPhase(SessionState.Phase.PREP);
            f.service.handlePrepBookUse(event);

            verify(event).setCancelled(true);
            verify(f.prepGuiRenderer).applyPrepGuiDecorations(inventory);
            verify(f.prepGuiRenderer).populateCategoriesPage(inventory, false, 0, 1, false);
            verify(player).openInventory(inventory);

            f.sessionState.setPhase(SessionState.Phase.COUNTDOWN);
            f.service.handlePrepBookUse(event);
            verify(f.log).sendWarn(player, "Prep is locked once everyone readies up.");
        }
    }

    @Test
    void handlePrepGuiClick_executesReadyAndResetFlows() {
        Fixture f = new Fixture();
        f.sessionState.setPhase(SessionState.Phase.PREP);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(event.getView()).thenReturn(view);
        when(view.getTitle()).thenReturn(f.prepGuiTitle);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(54);
        when(event.getRawSlot()).thenReturn(47);

        when(f.participantsView.onlineCount()).thenReturn(2);
        when(f.previewOrchestratorService.isPreviewEnabled()).thenReturn(true);

        AtomicReference<WorldResetManager> worldResetRef = f.worldResetRef;
        worldResetRef.set(null);

        doAnswer(invocation -> {
                    Runnable readyToggleFlow = invocation.getArgument(4);
                    Runnable refreshFlow = invocation.getArgument(5);
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<PrepGuiPage> openPrepGui = invocation.getArgument(6);
                    Runnable resetWorldFlow = invocation.getArgument(8);

                    readyToggleFlow.run();
                    refreshFlow.run();
                    openPrepGui.accept(PrepGuiPage.RUN_HISTORY);
                    resetWorldFlow.run();
                    return true;
                })
                .when(f.prepGuiFlowService)
                .handleClick(
                        eq(player),
                        eq(47),
                        eq(PrepGuiPage.CATEGORIES),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());

        Inventory categoryInventory = mock(Inventory.class);
        Inventory historyInventory = mock(Inventory.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of());
            bukkit.when(() -> Bukkit.createInventory(player, 54, f.prepGuiTitle))
                    .thenReturn(categoryInventory)
                    .thenReturn(historyInventory);

            f.service.handlePrepGuiClick(event);
        }

        verify(event).setCancelled(true);
        verify(f.log).sendInfo(player, "You are ready.");
        verify(f.prepReadinessService)
                .tryStartCountdown(
                        eq(f.readyPlayers),
                        eq(f.participants),
                        eq(f.isDiscoPreviewBlockingChallengeStart),
                        eq(f.announceDiscoPreviewStartBlocked),
                        eq(f.startRun));
        verify(f.log).sendError(player, "World reset manager is not available.");
    }

    @Test
    void handlePrepGuiDragAndClose_applyExpectedGuards() {
        Fixture f = new Fixture();

        InventoryDragEvent dragEvent = mock(InventoryDragEvent.class);
        InventoryView dragView = mock(InventoryView.class);
        when(dragEvent.getView()).thenReturn(dragView);
        when(dragView.getTitle()).thenReturn(f.prepGuiTitle);
        f.service.handlePrepGuiDrag(dragEvent);
        verify(dragEvent).setCancelled(true);

        Player player = mock(Player.class);
        InventoryCloseEvent closeEvent = mock(InventoryCloseEvent.class);
        InventoryView closeView = mock(InventoryView.class);
        when(closeEvent.getView()).thenReturn(closeView);
        when(closeView.getTitle()).thenReturn(f.prepGuiTitle);
        when(closeEvent.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);

        f.sessionState.setPhase(SessionState.Phase.PREP);
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

            f.service.handlePrepGuiClose(closeEvent);
        }

        verify(f.prepBookService).giveIfMissing(player);
        verify(player).updateInventory();
    }

    @Test
    void prepHandlers_coverGuardBranches_andPreviewDestroyingResetFlow() {
        Fixture f = new Fixture();

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerInteractEvent useEvent = mock(PlayerInteractEvent.class);
        when(useEvent.getPlayer()).thenReturn(player);
        when(useEvent.getItem()).thenReturn(null);
        f.service.handlePrepBookUse(useEvent);
        verify(useEvent, never()).setCancelled(true);

        InventoryClickEvent notPrepClick = mock(InventoryClickEvent.class);
        InventoryView notPrepView = mock(InventoryView.class);
        when(notPrepClick.getView()).thenReturn(notPrepView);
        when(notPrepView.getTitle()).thenReturn("not prep");
        f.service.handlePrepGuiClick(notPrepClick);
        verify(notPrepClick, never()).setCancelled(true);

        InventoryClickEvent outOfRangeClick = mock(InventoryClickEvent.class);
        InventoryView prepView = mock(InventoryView.class);
        Inventory inventory = mock(Inventory.class);
        when(outOfRangeClick.getView()).thenReturn(prepView);
        when(prepView.getTitle()).thenReturn(f.prepGuiTitle);
        when(outOfRangeClick.getWhoClicked()).thenReturn(player);
        when(outOfRangeClick.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(54);
        when(outOfRangeClick.getRawSlot()).thenReturn(99);
        f.sessionState.setPhase(SessionState.Phase.PREP);
        f.service.handlePrepGuiClick(outOfRangeClick);
        verify(outOfRangeClick).setCancelled(true);
        verify(f.prepGuiFlowService, never())
                .handleClick(eq(player), eq(99), any(), any(), any(), any(), any(), any(), any(), any());

        InventoryClickEvent resetClick = mock(InventoryClickEvent.class);
        InventoryView resetView = mock(InventoryView.class);
        Inventory resetInventory = mock(Inventory.class);
        when(resetClick.getView()).thenReturn(resetView);
        when(resetView.getTitle()).thenReturn(f.prepGuiTitle);
        when(resetClick.getWhoClicked()).thenReturn(player);
        when(resetClick.getInventory()).thenReturn(resetInventory);
        when(resetInventory.getSize()).thenReturn(54);
        when(resetClick.getRawSlot()).thenReturn(47);
        when(f.previewOrchestratorService.isPreviewDestroying()).thenReturn(true);
        f.worldResetRef.set(mock(WorldResetManager.class));

        doAnswer(invocation -> {
                    Runnable resetWorldFlow = invocation.getArgument(8);
                    resetWorldFlow.run();
                    return true;
                })
                .when(f.prepGuiFlowService)
                .handleClick(
                        eq(player),
                        eq(47),
                        eq(PrepGuiPage.CATEGORIES),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());

        f.service.handlePrepGuiClick(resetClick);
        verify(f.log).sendWarn(player, "Preview destroy animation is already running.");
        verify(f.previewOrchestratorService, never()).playPreviewDestroyAnimationThenReset(player);
    }

    @Test
    void refreshOpenPrepGuis_reopensOnlyPrepViews_andOnPlayerLeftClearsState() {
        Fixture f = new Fixture();
        Player prepViewer = mock(Player.class);
        Player otherViewer = mock(Player.class);

        UUID prepViewerId = UUID.randomUUID();
        UUID otherViewerId = UUID.randomUUID();
        when(prepViewer.getUniqueId()).thenReturn(prepViewerId);
        when(otherViewer.getUniqueId()).thenReturn(otherViewerId);

        InventoryView prepView = mock(InventoryView.class);
        InventoryView otherView = mock(InventoryView.class);
        when(prepViewer.getOpenInventory()).thenReturn(prepView);
        when(otherViewer.getOpenInventory()).thenReturn(otherView);
        when(prepView.getTitle()).thenReturn(f.prepGuiTitle);
        when(otherView.getTitle()).thenReturn("some other gui");

        Inventory reopenedInventory = mock(Inventory.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(prepViewer, otherViewer));
            bukkit.when(() -> Bukkit.createInventory(prepViewer, 54, f.prepGuiTitle))
                    .thenReturn(reopenedInventory);

            f.service.refreshOpenPrepGuis();
        }

        verify(prepViewer).openInventory(reopenedInventory);
        verify(otherViewer, never()).openInventory(any(Inventory.class));

        f.service.onPlayerLeft(prepViewerId);
        assertMapDoesNotContainPlayer(f.service, "prepGuiPages", prepViewerId);
        assertMapDoesNotContainPlayer(f.service, "runHistoryPageIndices", prepViewerId);
    }

    @Test
    void prepBookProtectionHandlers_cancelMoveDropAndSwap_forPrepBookItems() {
        Fixture f = new Fixture();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ItemStack prepBook = mock(ItemStack.class);
        when(f.prepBookService.isPrepBook(prepBook)).thenReturn(true);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getCurrentItem()).thenReturn(prepBook);
        when(clickEvent.getHotbarButton()).thenReturn(-1);

        f.service.handleProtectedPrepBookClick(clickEvent);
        verify(clickEvent).setCancelled(true);
        verify(player).updateInventory();

        InventoryDragEvent dragEvent = mock(InventoryDragEvent.class);
        when(dragEvent.getWhoClicked()).thenReturn(player);
        when(dragEvent.getOldCursor()).thenReturn(prepBook);
        when(dragEvent.getNewItems()).thenReturn(java.util.Map.of(0, prepBook));

        f.service.handleProtectedPrepBookDrag(dragEvent);
        verify(dragEvent).setCancelled(true);

        PlayerDropItemEvent dropEvent = mock(PlayerDropItemEvent.class);
        org.bukkit.entity.Item itemEntity = mock(org.bukkit.entity.Item.class);
        when(dropEvent.getPlayer()).thenReturn(player);
        when(dropEvent.getItemDrop()).thenReturn(itemEntity);
        when(itemEntity.getItemStack()).thenReturn(prepBook);

        f.service.handleProtectedPrepBookDrop(dropEvent);
        verify(dropEvent).setCancelled(true);

        PlayerSwapHandItemsEvent swapEvent = mock(PlayerSwapHandItemsEvent.class);
        when(swapEvent.getPlayer()).thenReturn(player);
        when(swapEvent.getMainHandItem()).thenReturn(prepBook);
        f.service.handleProtectedPrepBookSwapHands(swapEvent);
        verify(swapEvent).setCancelled(true);
    }

    @Test
    void prepBookProtectionClick_cancelsHotbarNumberSwap_whenHotbarItemIsPrepBook() {
        Fixture f = new Fixture();
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack prepBook = mock(ItemStack.class);

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(2)).thenReturn(prepBook);
        when(f.prepBookService.isPrepBook(prepBook)).thenReturn(true);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(clickEvent.getCurrentItem()).thenReturn(null);
        when(clickEvent.getCursor()).thenReturn(null);
        when(clickEvent.getHotbarButton()).thenReturn(2);

        f.service.handleProtectedPrepBookClick(clickEvent);
        verify(clickEvent).setCancelled(true);
        verify(player).updateInventory();
    }

    private static void assertMapDoesNotContainPlayer(Object target, String fieldName, UUID playerId) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, ?> map = (java.util.Map<UUID, ?>) field.get(target);
            org.junit.jupiter.api.Assertions.assertFalse(map.containsKey(playerId));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class Fixture {
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final DeepCoreLogger log = mock(DeepCoreLogger.class);
        final SessionState sessionState = new SessionState();
        final Set<UUID> readyPlayers = new java.util.HashSet<>();
        final Set<UUID> participants = new java.util.HashSet<>();
        final ParticipantsView participantsView = mock(ParticipantsView.class);
        final ChallengeManager challengeManager = mock(ChallengeManager.class);
        final PrepGuiRenderer prepGuiRenderer = mock(PrepGuiRenderer.class);
        final PrepBookService prepBookService = mock(PrepBookService.class);
        final PrepGuiFlowService prepGuiFlowService = mock(PrepGuiFlowService.class);
        final PrepReadinessService prepReadinessService = mock(PrepReadinessService.class);
        final PreviewOrchestratorService previewOrchestratorService = mock(PreviewOrchestratorService.class);
        final AtomicReference<WorldResetManager> worldResetRef = new AtomicReference<>();
        final RunRecordsService recordsService = mock(RunRecordsService.class);
        final RunUiFormattingService runUiFormattingService = mock(RunUiFormattingService.class);

        final java.util.function.BooleanSupplier isDiscoPreviewBlockingChallengeStart =
                mock(java.util.function.BooleanSupplier.class);
        final Runnable announceDiscoPreviewStartBlocked = mock(Runnable.class);
        final Runnable startRun = mock(Runnable.class);

        final String prepGuiTitle = "DeepCore Prep";

        final PrepGuiCoordinatorService service = new PrepGuiCoordinatorService(
                plugin,
                log,
                sessionState,
                readyPlayers,
                participants,
                participantsView,
                challengeManager,
                prepGuiRenderer,
                prepBookService,
                prepGuiFlowService,
                prepReadinessService,
                previewOrchestratorService,
                () -> worldResetRef.get(),
                () -> recordsService,
                runUiFormattingService,
                isDiscoPreviewBlockingChallengeStart,
                announceDiscoPreviewStartBlocked,
                startRun,
                prepGuiTitle,
                DateTimeFormatter.ISO_LOCAL_DATE);

        Fixture() {
            when(recordsService.getAllRecords()).thenReturn(List.of(mock(RunRecord.class)));
            when(runUiFormattingService.formatSplitDuration(anyLong())).thenReturn("00:10");
            when(prepGuiRenderer.populateRunHistoryPage(any(), any(Integer.class), any(), any(), any()))
                    .thenReturn(0);
        }
    }
}

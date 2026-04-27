package dev.deepcore.challenge.session;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.PrepGuiPage;
import dev.deepcore.challenge.ui.PrepGuiRenderer;
import dev.deepcore.records.RunRecordsService;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Routes prep GUI click flow for page navigation, toggles, and history paging.
 */
public final class PrepGuiFlowService {
    private final PrepSettingsService prepSettingsService;
    private final ChallengeManager challengeManager;
    private final PrepGuiRenderer prepGuiRenderer;
    private final Supplier<RunRecordsService> recordsServiceSupplier;

    /**
     * Creates a prep GUI flow service.
     *
     * @param prepSettingsService    service used to mutate prep-related component
     *                               settings
     * @param challengeManager       challenge manager used for current component
     *                               state reads
     * @param prepGuiRenderer        renderer used for run-history paging checks
     * @param recordsServiceSupplier supplier for run records data source
     */
    public PrepGuiFlowService(
            PrepSettingsService prepSettingsService,
            ChallengeManager challengeManager,
            PrepGuiRenderer prepGuiRenderer,
            Supplier<RunRecordsService> recordsServiceSupplier) {
        this.prepSettingsService = prepSettingsService;
        this.challengeManager = challengeManager;
        this.prepGuiRenderer = prepGuiRenderer;
        this.recordsServiceSupplier = recordsServiceSupplier;
    }

    /**
     * Handles a prep GUI click and executes matching navigation or action flow.
     *
     * @param player                player who clicked in prep GUI
     * @param slot                  clicked inventory slot index
     * @param page                  current prep GUI page
     * @param runHistoryPageIndices per-player run-history page index map
     * @param readyToggleFlow       action for toggling ready state
     * @param refreshOpenPrepGuis   action to refresh open prep GUIs
     * @param openPrepGui           action to open a specific prep GUI page
     * @param closeInventory        action to close the player's inventory
     * @param resetWorldFlow        action to trigger world regeneration flow
     * @param trainingTeleportFlow  action to teleport player to training world
     * @return true when the click was handled by prep GUI flow logic
     */
    public boolean handleClick(
            Player player,
            int slot,
            PrepGuiPage page,
            Map<UUID, Integer> runHistoryPageIndices,
            Runnable readyToggleFlow,
            Runnable refreshOpenPrepGuis,
            Consumer<PrepGuiPage> openPrepGui,
            Runnable closeInventory,
            Runnable resetWorldFlow,
            Runnable trainingTeleportFlow) {
        if (slot == 47 && page != PrepGuiPage.RUN_HISTORY) {
            readyToggleFlow.run();
            return true;
        }

        if (page == PrepGuiPage.CATEGORIES && slot == 51) {
            resetWorldFlow.run();
            return true;
        }

        if (page == PrepGuiPage.CATEGORIES && slot == 53) {
            trainingTeleportFlow.run();
            return true;
        }

        if (slot == 45 && page == PrepGuiPage.CATEGORIES) {
            closeInventory.run();
            return true;
        }

        if (slot == 45 && page != PrepGuiPage.CATEGORIES) {
            openPrepGui.accept(PrepGuiPage.CATEGORIES);
            return true;
        }

        if (slot == 24 && page == PrepGuiPage.CATEGORIES) {
            openPrepGui.accept(PrepGuiPage.HEALTH);
            return true;
        }

        if (slot == 22 && page == PrepGuiPage.CATEGORIES) {
            runHistoryPageIndices.put(player.getUniqueId(), 0);
            openPrepGui.accept(PrepGuiPage.RUN_HISTORY);
            return true;
        }

        if (slot == 20 && page == PrepGuiPage.CATEGORIES) {
            openPrepGui.accept(PrepGuiPage.INVENTORY);
            return true;
        }

        if (page == PrepGuiPage.INVENTORY) {
            if (slot == 20) {
                prepSettingsService.toggleComponent(ChallengeComponent.KEEP_INVENTORY);
                refreshOpenPrepGuis.run();
                return true;
            }
            if (slot == 22) {
                prepSettingsService.toggleComponent(ChallengeComponent.SHARED_INVENTORY);
                refreshOpenPrepGuis.run();
                return true;
            }
            if (slot == 24) {
                prepSettingsService.toggleComponent(ChallengeComponent.DEGRADING_INVENTORY);
                refreshOpenPrepGuis.run();
                return true;
            }
        }

        if (page == PrepGuiPage.HEALTH) {
            if (slot == 20) {
                prepSettingsService.setHealthRefill(
                        !challengeManager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL));
                refreshOpenPrepGuis.run();
                return true;
            }
            if (slot == 22) {
                prepSettingsService.toggleComponent(ChallengeComponent.SHARED_HEALTH);
                refreshOpenPrepGuis.run();
                return true;
            }
            if (slot == 24) {
                prepSettingsService.setInitialHalfHeart(
                        !challengeManager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART));
                refreshOpenPrepGuis.run();
                return true;
            }
            if (slot == 31) {
                prepSettingsService.toggleComponent(ChallengeComponent.HARDCORE);
                refreshOpenPrepGuis.run();
                return true;
            }
        }

        if (page == PrepGuiPage.RUN_HISTORY) {
            int currentPage = runHistoryPageIndices.getOrDefault(player.getUniqueId(), 0);
            RunRecordsService recordsService = recordsServiceSupplier.get();
            int totalRecords =
                    recordsService != null ? recordsService.getAllRecords().size() : 0;

            if (slot == 47 && currentPage > 0) {
                runHistoryPageIndices.put(player.getUniqueId(), currentPage - 1);
                openPrepGui.accept(PrepGuiPage.RUN_HISTORY);
                return true;
            }

            if (slot == 52 && prepGuiRenderer.hasRunHistoryNextPage(currentPage, totalRecords)) {
                runHistoryPageIndices.put(player.getUniqueId(), currentPage + 1);
                openPrepGui.accept(PrepGuiPage.RUN_HISTORY);
                return true;
            }
        }

        return false;
    }
}

package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.challenge.PrepGuiPage;
import dev.deepcore.challenge.ui.PrepGuiRenderer;
import dev.deepcore.records.RunRecord;
import dev.deepcore.records.RunRecordsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PrepGuiFlowServiceTest {

    @Test
    void categoriesNavigationAndReadyResetActions_areHandled() {
        PrepSettingsService settings = mock(PrepSettingsService.class);
        ChallengeManager manager = mock(ChallengeManager.class);
        PrepGuiRenderer renderer = mock(PrepGuiRenderer.class);
        RunRecordsService records = mock(RunRecordsService.class);
        PrepGuiFlowService service = new PrepGuiFlowService(settings, manager, renderer, () -> records);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        Map<UUID, Integer> history = new HashMap<>();
        Runnable readyToggle = mock(Runnable.class);
        Runnable refresh = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<PrepGuiPage> open = mock(Consumer.class);
        Runnable close = mock(Runnable.class);
        Runnable reset = mock(Runnable.class);
        Runnable trainingTeleport = mock(Runnable.class);

        assertTrue(service.handleClick(
                player,
                47,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(readyToggle).run();

        assertTrue(service.handleClick(
                player,
                51,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(reset).run();

        assertTrue(service.handleClick(
                player,
                53,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(trainingTeleport).run();

        assertTrue(service.handleClick(
                player,
                24,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(open).accept(PrepGuiPage.HEALTH);

        assertTrue(service.handleClick(
                player,
                22,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        assertEquals(0, history.get(id));
        verify(open).accept(PrepGuiPage.RUN_HISTORY);

        assertTrue(service.handleClick(
                player,
                20,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(open).accept(PrepGuiPage.INVENTORY);

        assertTrue(service.handleClick(
                player,
                45,
                PrepGuiPage.CATEGORIES,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(close).run();
    }

    @Test
    void inventoryAndHealthToggleSlots_invokeSettingsAndRefresh() {
        PrepSettingsService settings = mock(PrepSettingsService.class);
        ChallengeManager manager = mock(ChallengeManager.class);
        PrepGuiRenderer renderer = mock(PrepGuiRenderer.class);
        PrepGuiFlowService service = new PrepGuiFlowService(settings, manager, renderer, () -> null);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Map<UUID, Integer> history = new HashMap<>();
        Runnable readyToggle = mock(Runnable.class);
        Runnable refresh = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<PrepGuiPage> open = mock(Consumer.class);
        Runnable close = mock(Runnable.class);
        Runnable reset = mock(Runnable.class);
        Runnable trainingTeleport = mock(Runnable.class);

        assertTrue(service.handleClick(
                player,
                20,
                PrepGuiPage.INVENTORY,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(settings).toggleComponent(ChallengeComponent.KEEP_INVENTORY);

        assertTrue(service.handleClick(
                player,
                22,
                PrepGuiPage.INVENTORY,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(settings).toggleComponent(ChallengeComponent.SHARED_INVENTORY);

        assertTrue(service.handleClick(
                player,
                24,
                PrepGuiPage.INVENTORY,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        verify(settings).toggleComponent(ChallengeComponent.DEGRADING_INVENTORY);

        when(manager.isComponentEnabled(ChallengeComponent.HEALTH_REFILL)).thenReturn(false);
        when(manager.isComponentEnabled(ChallengeComponent.INITIAL_HALF_HEART)).thenReturn(true);

        assertTrue(service.handleClick(
                player, 20, PrepGuiPage.HEALTH, history, readyToggle, refresh, open, close, reset, trainingTeleport));
        verify(settings).setHealthRefill(true);

        assertTrue(service.handleClick(
                player, 22, PrepGuiPage.HEALTH, history, readyToggle, refresh, open, close, reset, trainingTeleport));
        verify(settings).toggleComponent(ChallengeComponent.SHARED_HEALTH);

        assertTrue(service.handleClick(
                player, 24, PrepGuiPage.HEALTH, history, readyToggle, refresh, open, close, reset, trainingTeleport));
        verify(settings).setInitialHalfHeart(false);

        assertTrue(service.handleClick(
                player, 31, PrepGuiPage.HEALTH, history, readyToggle, refresh, open, close, reset, trainingTeleport));
        verify(settings).toggleComponent(ChallengeComponent.HARDCORE);

        verify(refresh, org.mockito.Mockito.atLeastOnce()).run();
    }

    @Test
    void runHistoryPaging_usesPrevAndNextGuards() {
        PrepSettingsService settings = mock(PrepSettingsService.class);
        ChallengeManager manager = mock(ChallengeManager.class);
        PrepGuiRenderer renderer = mock(PrepGuiRenderer.class);
        RunRecordsService records = mock(RunRecordsService.class);
        when(records.getAllRecords())
                .thenReturn(new ArrayList<RunRecord>(List.of(mock(RunRecord.class), mock(RunRecord.class))));
        PrepGuiFlowService service = new PrepGuiFlowService(settings, manager, renderer, () -> records);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        Map<UUID, Integer> history = new HashMap<>();
        history.put(id, 1);

        Runnable readyToggle = mock(Runnable.class);
        Runnable refresh = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        Consumer<PrepGuiPage> open = mock(Consumer.class);
        Runnable close = mock(Runnable.class);
        Runnable reset = mock(Runnable.class);
        Runnable trainingTeleport = mock(Runnable.class);

        assertTrue(service.handleClick(
                player,
                47,
                PrepGuiPage.RUN_HISTORY,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        assertEquals(0, history.get(id));

        when(renderer.hasRunHistoryNextPage(0, 2)).thenReturn(true);
        assertTrue(service.handleClick(
                player,
                52,
                PrepGuiPage.RUN_HISTORY,
                history,
                readyToggle,
                refresh,
                open,
                close,
                reset,
                trainingTeleport));
        assertEquals(1, history.get(id));

        verify(open, org.mockito.Mockito.atLeastOnce()).accept(PrepGuiPage.RUN_HISTORY);
    }

    @Test
    void returnsFalseWhenNoSlotActionMatches() {
        PrepGuiFlowService service = new PrepGuiFlowService(
                mock(PrepSettingsService.class), mock(ChallengeManager.class), mock(PrepGuiRenderer.class), () -> null);

        boolean handled = service.handleClick(
                mock(Player.class),
                99,
                PrepGuiPage.HEALTH,
                new HashMap<>(),
                mock(Runnable.class),
                mock(Runnable.class),
                page -> {},
                mock(Runnable.class),
                mock(Runnable.class),
                mock(Runnable.class));

        assertFalse(handled);
    }
}

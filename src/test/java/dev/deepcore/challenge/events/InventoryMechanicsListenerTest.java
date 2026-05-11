package dev.deepcore.challenge.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.deepcore.challenge.inventory.InventoryMechanicsCoordinatorService;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryMechanicsListenerTest {
    private InventoryMechanicsCoordinatorService coordinator;
    private InventoryMechanicsListener listener;

    @BeforeEach
    void setup() {
        coordinator = mock(InventoryMechanicsCoordinatorService.class);
        listener = new InventoryMechanicsListener(coordinator);
    }

    @Test
    void delegatesInventoryEvents() {
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        listener.onInventoryClick(click);
        verify(coordinator).handleInventoryClick(click);

        InventoryCreativeEvent creative = mock(InventoryCreativeEvent.class);
        listener.onInventoryCreative(creative);
        verify(coordinator).handleInventoryCreative(creative);

        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        listener.onInventoryDrag(drag);
        verify(coordinator).handleInventoryDrag(drag);

        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        listener.onDrop(drop);
        verify(coordinator).handlePlayerDropItem(drop);
    }
}

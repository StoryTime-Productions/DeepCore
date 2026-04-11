package dev.deepcore.challenge.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class PrepBookServiceTest {

    @Test
    void giveIfMissing_addsBookOnlyWhenPlayerDoesNotAlreadyHaveTaggedPrepBook() {
        PrepBookService service = newService();

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack untagged = untaggedWrittenBook();
        when(inventory.getContents()).thenReturn(new ItemStack[] {null, untagged});
        service.giveIfMissing(player);
        verify(inventory).addItem(any(ItemStack.class));

        org.mockito.Mockito.reset(inventory);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack tagged = taggedPrepBook();
        when(inventory.getContents()).thenReturn(new ItemStack[] {tagged});

        service.giveIfMissing(player);
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test
    void removeFromInventory_clearsOnlyTaggedPrepBooks() {
        PrepBookService service = newService();

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(3);
        ItemStack tagged = taggedPrepBook();
        ItemStack untagged = untaggedWrittenBook();
        when(inventory.getItem(0)).thenReturn(tagged);
        when(inventory.getItem(1)).thenReturn(untagged);
        when(inventory.getItem(2)).thenReturn(null);

        service.removeFromInventory(player);

        verify(inventory).setItem(0, null);
        verify(inventory, never()).setItem(1, null);
        verify(player).updateInventory();
    }

    @Test
    void isPrepBook_requiresWrittenBookMetaAndMarker() {
        PrepBookService service = newService();

        assertFalse(service.isPrepBook(null));

        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        assertFalse(service.isPrepBook(stone));

        ItemStack noMeta = mock(ItemStack.class);
        when(noMeta.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(noMeta.getItemMeta()).thenReturn(null);
        assertFalse(service.isPrepBook(noMeta));

        assertFalse(service.isPrepBook(untaggedWrittenBook()));
        assertTrue(service.isPrepBook(taggedPrepBook()));
    }

    private static PrepBookService newService() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("DeepCore");
        return new PrepBookService(plugin);
    }

    private static ItemStack taggedPrepBook() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenReturn((byte) 1);
        return stack;
    }

    private static ItemStack untaggedWrittenBook() {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenReturn(null);
        return stack;
    }
}

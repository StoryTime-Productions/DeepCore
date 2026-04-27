package dev.deepcore.challenge.training;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.seeseemelk.mockbukkit.MockBukkit;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrainingReturnItemServiceTest {

    private JavaPlugin plugin;
    private TrainingManager trainingManager;
    private TrainingReturnItemService service;
    private NamespacedKey returnKey;
    private Server server;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        plugin = mock(JavaPlugin.class);
        trainingManager = mock(TrainingManager.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(BukkitScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);

        returnKey = new NamespacedKey("deepcore", "training_return_item");
        service = new TrainingReturnItemService(plugin, trainingManager, returnKey);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void isReturnItem_validatesMaterialMetaAndMarker() {
        assertFalse(service.isReturnItem(null));
        assertFalse(service.isReturnItem(new ItemStack(org.bukkit.Material.STONE)));

        ItemStack featherWithoutMarker = new ItemStack(org.bukkit.Material.FEATHER);
        assertFalse(service.isReturnItem(featherWithoutMarker));

        ItemStack marked = new ItemStack(org.bukkit.Material.FEATHER);
        ItemMeta markedMeta = marked.getItemMeta();
        markedMeta.getPersistentDataContainer().set(returnKey, PersistentDataType.BYTE, (byte) 1);
        marked.setItemMeta(markedMeta);
        assertTrue(service.isReturnItem(marked));

        ItemStack wrongMarker = new ItemStack(org.bukkit.Material.FEATHER);
        ItemMeta wrongMeta = wrongMarker.getItemMeta();
        wrongMeta.getPersistentDataContainer().set(returnKey, PersistentDataType.BYTE, (byte) 0);
        wrongMarker.setItemMeta(wrongMeta);
        assertFalse(service.isReturnItem(wrongMarker));
    }

    @Test
    void onPlayerEnterAndLeaveTraining_manageReturnItemSlot() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(inventory.getItem(TrainingReturnItemService.RETURN_ITEM_SLOT)).thenReturn(null);

        service.onPlayerEnterTraining(player);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory).setItem(eq(TrainingReturnItemService.RETURN_ITEM_SLOT), captor.capture());
        assertTrue(service.isReturnItem(captor.getValue()));
        verify(player).updateInventory();

        when(inventory.getItem(TrainingReturnItemService.RETURN_ITEM_SLOT)).thenReturn(captor.getValue());
        service.onPlayerLeaveTraining(player);

        verify(inventory).setItem(TrainingReturnItemService.RETURN_ITEM_SLOT, null);
        verify(player, atLeastOnce()).updateInventory();
    }

    @Test
    void onReturnItemClick_onlyHandlesRightClickWithReturnItem() {
        Player player = mock(Player.class);
        ItemStack returnItem = markedReturnItem();

        PlayerInteractEvent leftClick = mock(PlayerInteractEvent.class);
        when(leftClick.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
        service.onReturnItemClick(leftClick);
        verify(leftClick, never()).setCancelled(true);

        PlayerInteractEvent rightClickNonReturn = mock(PlayerInteractEvent.class);
        when(rightClickNonReturn.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(rightClickNonReturn.getItem()).thenReturn(new ItemStack(org.bukkit.Material.STONE));
        service.onReturnItemClick(rightClickNonReturn);
        verify(rightClickNonReturn, never()).setCancelled(true);

        PlayerInteractEvent rightClickReturn = mock(PlayerInteractEvent.class);
        when(rightClickReturn.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(rightClickReturn.getItem()).thenReturn(returnItem);
        when(rightClickReturn.getPlayer()).thenReturn(player);

        service.onReturnItemClick(rightClickReturn);

        verify(rightClickReturn).setCancelled(true);
        verify(trainingManager).leaveTraining(player);
    }

    @Test
    void inventoryAndDropGuards_blockMovingOrDroppingReturnItem() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack returnItem = markedReturnItem();

        org.bukkit.entity.Item itemEntity = mock(org.bukkit.entity.Item.class);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(drop.getItemDrop()).thenReturn(itemEntity);
        when(itemEntity.getItemStack()).thenReturn(returnItem);
        when(drop.getPlayer()).thenReturn(player);

        service.onReturnItemDrop(drop);
        verify(drop).setCancelled(true);
        verify(player).updateInventory();

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getCurrentItem()).thenReturn(returnItem);
        when(click.getCursor()).thenReturn(null);
        when(click.getHotbarButton()).thenReturn(-1);

        service.onReturnItemInventoryClick(click);
        verify(click).setCancelled(true);
        verify(player, atLeastOnce()).updateInventory();

        InventoryClickEvent hotbarSwap = mock(InventoryClickEvent.class);
        when(hotbarSwap.getWhoClicked()).thenReturn(player);
        when(hotbarSwap.getCurrentItem()).thenReturn(null);
        when(hotbarSwap.getCursor()).thenReturn(null);
        when(hotbarSwap.getHotbarButton()).thenReturn(2);
        when(inventory.getItem(2)).thenReturn(returnItem);

        service.onReturnItemInventoryClick(hotbarSwap);
        verify(hotbarSwap).setCancelled(true);

        InventoryDragEvent dragOldCursor = mock(InventoryDragEvent.class);
        when(dragOldCursor.getWhoClicked()).thenReturn(player);
        when(dragOldCursor.getOldCursor()).thenReturn(returnItem);

        service.onReturnItemInventoryDrag(dragOldCursor);
        verify(dragOldCursor).setCancelled(true);

        InventoryDragEvent dragNewItems = mock(InventoryDragEvent.class);
        when(dragNewItems.getWhoClicked()).thenReturn(player);
        when(dragNewItems.getOldCursor()).thenReturn(null);
        when(dragNewItems.getNewItems()).thenReturn(Map.of(0, returnItem));

        service.onReturnItemInventoryDrag(dragNewItems);
        verify(dragNewItems).setCancelled(true);
    }

    @Test
    void initializeAndRestoreTask_coverSchedulerAndCleanupBranches() throws Exception {
        BukkitTask scheduledTask = mock(BukkitTask.class);
        AtomicReference<Runnable> tick = new AtomicReference<>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L)))
                .thenAnswer(invocation -> {
                    tick.set(invocation.getArgument(1));
                    return scheduledTask;
                });

        UUID missingPlayer = UUID.randomUUID();
        UUID activePlayerId = UUID.randomUUID();
        UUID needsItemPlayerId = UUID.randomUUID();

        Player activePlayer = mock(Player.class);
        Player needsItemPlayer = mock(Player.class);
        PlayerInventory activeInventory = mock(PlayerInventory.class);
        PlayerInventory needsItemInventory = mock(PlayerInventory.class);

        when(activePlayer.getInventory()).thenReturn(activeInventory);
        when(needsItemPlayer.getInventory()).thenReturn(needsItemInventory);
        when(activePlayer.getUniqueId()).thenReturn(activePlayerId);
        when(needsItemPlayer.getUniqueId()).thenReturn(needsItemPlayerId);

        when(activeInventory.getItem(TrainingReturnItemService.RETURN_ITEM_SLOT))
                .thenReturn(markedReturnItem());
        when(needsItemInventory.getItem(TrainingReturnItemService.RETURN_ITEM_SLOT))
                .thenReturn(null);

        World world = mock(World.class);
        when(needsItemPlayer.getWorld()).thenReturn(world);
        when(needsItemPlayer.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        when(server.getPlayer(missingPlayer)).thenReturn(null);
        when(server.getPlayer(activePlayerId)).thenReturn(activePlayer);
        when(server.getPlayer(needsItemPlayerId)).thenReturn(needsItemPlayer);

        when(trainingManager.isInActiveAttempt(activePlayer)).thenReturn(true);
        when(trainingManager.isInActiveAttempt(needsItemPlayer)).thenReturn(false);

        @SuppressWarnings("unchecked")
        Set<UUID> playersInTraining = (Set<UUID>) getField(service, "playersInTraining");
        playersInTraining.add(missingPlayer);
        playersInTraining.add(activePlayerId);
        playersInTraining.add(needsItemPlayerId);

        service.initialize();
        verify(pluginManager).registerEvents(service, plugin);

        tick.get().run();

        assertFalse(playersInTraining.contains(missingPlayer));
        verify(needsItemInventory).setItem(eq(TrainingReturnItemService.RETURN_ITEM_SLOT), any(ItemStack.class));
        verify(activeInventory, never()).setItem(eq(TrainingReturnItemService.RETURN_ITEM_SLOT), any(ItemStack.class));

        service.shutdown();
        verify(scheduledTask).cancel();
        assertTrue(playersInTraining.isEmpty());
    }

    private ItemStack markedReturnItem() {
        ItemStack item = new ItemStack(org.bukkit.Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(returnKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Object getField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}

package dev.deepcore.challenge.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class InventoryMechanicsCoordinatorServiceTest {

    @Test
    void clickAndCreativeHandlers_ignoreNonPlayerActors() {
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                mock(JavaPlugin.class),
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                mock(SharedInventorySyncService.class),
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        InventoryCreativeEvent creativeEvent = mock(InventoryCreativeEvent.class);
        when(clickEvent.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
        when(creativeEvent.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));

        service.handleInventoryClick(clickEvent);
        service.handleInventoryCreative(creativeEvent);

        verify(clickEvent, never()).setCancelled(true);
        verify(creativeEvent, never()).setCancelled(true);
    }

    @Test
    void handleInventoryClick_cancelsLockedSlot_andSkipsSync() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(degradingService.shouldCancelLockedSlotClick(event, player)).thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        service.handleInventoryClick(event);

        verify(event).setCancelled(true);
        verify(enforceCap).accept(player);
        verify(player).updateInventory();
        verify(sharedService, never()).requestSharedInventorySync(any());
    }

    @Test
    void handleInventoryEvents_scheduleCapAndRequestSyncs_whenEligible() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        InventoryCreativeEvent creativeEvent = mock(InventoryCreativeEvent.class);
        CraftItemEvent craftEvent = mock(CraftItemEvent.class);
        PlayerItemConsumeEvent consumeEvent = mock(PlayerItemConsumeEvent.class);
        BlockPlaceEvent placeEvent = mock(BlockPlaceEvent.class);
        BlockMultiPlaceEvent multiPlaceEvent = mock(BlockMultiPlaceEvent.class);

        when(clickEvent.getWhoClicked()).thenReturn(player);
        when(creativeEvent.getWhoClicked()).thenReturn(player);
        when(craftEvent.getWhoClicked()).thenReturn(player);
        when(consumeEvent.getPlayer()).thenReturn(player);
        when(placeEvent.getPlayer()).thenReturn(player);
        when(multiPlaceEvent.getPlayer()).thenReturn(player);

        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handleInventoryClick(clickEvent);
            service.handleInventoryCreative(creativeEvent);
            service.handleCraftItem(craftEvent);
            service.handlePlayerItemConsume(consumeEvent);
            service.handleBlockPlace(placeEvent);
            service.handleBlockMultiPlace(multiPlaceEvent);
        }

        verify(enforceCap, org.mockito.Mockito.times(6)).accept(player);
        verify(sharedService, org.mockito.Mockito.times(6)).requestSharedInventorySync(player);
        verify(sharedService, org.mockito.Mockito.times(2)).requestWearableEquipSync(player);
    }

    @Test
    void requestInventoryCapGuard_preventsScheduledEnforcement_whenInactive() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        PlayerItemConsumeEvent consumeEvent = mock(PlayerItemConsumeEvent.class);
        when(consumeEvent.getPlayer()).thenReturn(player);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> false,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        service.handlePlayerItemConsume(consumeEvent);

        verify(challengeManager, never()).isComponentEnabled(any());
        verify(enforceCap, never()).accept(any());
        verify(sharedService).requestSharedInventorySync(player);
    }

    @Test
    void scheduledCapEnforcement_rechecksEligibility_beforeApplying() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        PlayerItemConsumeEvent consumeEvent = mock(PlayerItemConsumeEvent.class);
        when(consumeEvent.getPlayer()).thenReturn(player);

        AtomicBoolean active = new AtomicBoolean(true);
        Predicate<Player> isChallengeActive = p -> active.get();
        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                isChallengeActive,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        active.set(false);
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handlePlayerItemConsume(consumeEvent);
        }

        verify(enforceCap, never()).accept(any());
    }

    @Test
    void handleInventoryDrag_cancelsWhenBarrierProtectionTouchesLockedOrBarrierSlots() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = mock(InventoryView.class);
        ItemStack oldCursor = mock(ItemStack.class);

        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getOldCursor()).thenReturn(oldCursor);
        when(event.getNewItems()).thenReturn(java.util.Map.of(1, mock(ItemStack.class)));
        when(event.getRawSlots()).thenReturn(java.util.Set.of(1));

        when(degradingService.isBarrierProtectionActive(player)).thenReturn(true);
        when(degradingService.isBarrier(oldCursor)).thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        service.handleInventoryDrag(event);

        verify(event).setCancelled(true);
        verify(enforceCap).accept(player);
        verify(player).updateInventory();
        verify(sharedService, never()).requestSharedInventorySync(any());
    }

    @Test
    void handlePotentialWearableUse_supportsOffHandEquip_afterEquipmentChangeConfirmation() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(null, helmet);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(helmet);

        PlayerArmorChangeEvent equipmentEvent = mock(PlayerArmorChangeEvent.class);
        when(equipmentEvent.getPlayer()).thenReturn(player);
        when(equipmentEvent.getSlotType()).thenReturn(PlayerArmorChangeEvent.SlotType.HEAD);
        when(equipmentEvent.getNewItem()).thenReturn(helmet);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.handlePotentialWearableUse(event);
            service.handlePlayerArmorChanged(equipmentEvent);
        }

        verify(sharedService).consumeWearableFromOtherParticipants(Material.DIAMOND_HELMET, playerId);
        verify(sharedService).capturePlayerWearableSnapshot(player);
    }

    @Test
    void handleProjectileLaunch_handlesArrowAndThrowableConsumption() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player shooter = mock(Player.class);
        UUID shooterId = UUID.randomUUID();
        when(shooter.getUniqueId()).thenReturn(shooterId);

        AbstractArrow arrow = mock(AbstractArrow.class);
        when(arrow.getShooter()).thenReturn(shooter);

        Snowball snowball = mock(Snowball.class);
        when(snowball.getShooter()).thenReturn(shooter);

        EnderPearl pearl = mock(EnderPearl.class);
        when(pearl.getShooter()).thenReturn(shooter);

        Egg egg = mock(Egg.class);
        when(egg.getShooter()).thenReturn(shooter);

        ThrownExpBottle expBottle = mock(ThrownExpBottle.class);
        when(expBottle.getShooter()).thenReturn(shooter);

        ThrownPotion splashPotion = mock(ThrownPotion.class);
        when(splashPotion.getShooter()).thenReturn(shooter);
        ItemStack splashItem = mock(ItemStack.class);
        when(splashItem.getType()).thenReturn(Material.SPLASH_POTION);
        when(splashPotion.getItem()).thenReturn(splashItem);

        ThrownPotion lingeringPotion = mock(ThrownPotion.class);
        when(lingeringPotion.getShooter()).thenReturn(shooter);
        ItemStack lingeringItem = mock(ItemStack.class);
        when(lingeringItem.getType()).thenReturn(Material.LINGERING_POTION);
        when(lingeringPotion.getItem()).thenReturn(lingeringItem);

        ProjectileLaunchEvent arrowEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent snowballEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent pearlEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent eggEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent expBottleEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent splashEvent = mock(ProjectileLaunchEvent.class);
        ProjectileLaunchEvent lingeringEvent = mock(ProjectileLaunchEvent.class);
        when(arrowEvent.getEntity()).thenReturn(arrow);
        when(snowballEvent.getEntity()).thenReturn(snowball);
        when(pearlEvent.getEntity()).thenReturn(pearl);
        when(eggEvent.getEntity()).thenReturn(egg);
        when(expBottleEvent.getEntity()).thenReturn(expBottle);
        when(splashEvent.getEntity()).thenReturn(splashPotion);
        when(lingeringEvent.getEntity()).thenReturn(lingeringPotion);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handleProjectileLaunch(arrowEvent);
            service.handleProjectileLaunch(snowballEvent);
            service.handleProjectileLaunch(pearlEvent);
            service.handleProjectileLaunch(eggEvent);
            service.handleProjectileLaunch(expBottleEvent);
            service.handleProjectileLaunch(splashEvent);
            service.handleProjectileLaunch(lingeringEvent);
        }

        verify(sharedService).requestSharedInventorySync(shooter);
        verify(sharedService).consumeItemFromOtherParticipants(Material.SNOWBALL, shooterId);
        verify(sharedService).consumeItemFromOtherParticipants(Material.ENDER_PEARL, shooterId);
        verify(sharedService).consumeItemFromOtherParticipants(Material.EGG, shooterId);
        verify(sharedService).consumeItemFromOtherParticipants(Material.EXPERIENCE_BOTTLE, shooterId);
        verify(sharedService).consumeItemFromOtherParticipants(Material.SPLASH_POTION, shooterId);
        verify(sharedService).consumeItemFromOtherParticipants(Material.LINGERING_POTION, shooterId);
    }

    @Test
    void handleEntityResurrect_consumesTotemForOtherParticipants() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        EntityResurrectEvent event = mock(EntityResurrectEvent.class);
        when(event.getEntity()).thenReturn(player);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        service.handleEntityResurrect(event);

        verify(sharedService).consumeItemFromOtherParticipants(Material.TOTEM_OF_UNDYING, playerId);
    }

    @Test
    void handleEnderEyeUse_consumesOnlyForValidHandActionAndItem() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        ItemStack enderEye = mock(ItemStack.class);
        when(enderEye.getType()).thenReturn(Material.ENDER_EYE);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getItem()).thenReturn(enderEye);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        service.handleEnderEyeUse(event);

        verify(sharedService).consumeItemFromOtherParticipants(Material.ENDER_EYE, playerId);
    }

    @Test
    void handleEnderEyeUse_consumesForOffHandRightClick() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        ItemStack enderEye = mock(ItemStack.class);
        when(enderEye.getType()).thenReturn(Material.ENDER_EYE);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(enderEye);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        service.handleEnderEyeUse(event);

        verify(sharedService).consumeItemFromOtherParticipants(Material.ENDER_EYE, playerId);
    }

    @Test
    void handleEnderEyeUse_ignoresWhenItemUseDenied() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ItemStack enderEye = mock(ItemStack.class);
        when(enderEye.getType()).thenReturn(Material.ENDER_EYE);

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getItem()).thenReturn(enderEye);
        when(event.useItemInHand()).thenReturn(Event.Result.DENY);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        service.handleEnderEyeUse(event);

        verify(sharedService, never()).consumeItemFromOtherParticipants(Material.ENDER_EYE, player.getUniqueId());
    }

    @Test
    void handleEntityPickupItem_logsAndSyncs_whenChallengeInactive() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);

        Player picker = mock(Player.class);
        Player other = mock(Player.class);
        UUID pickerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(picker.getUniqueId()).thenReturn(pickerId);
        when(other.getUniqueId()).thenReturn(otherId);
        when(picker.getName()).thenReturn("Picker");
        when(other.getLocation()).thenReturn(mock(Location.class));

        org.bukkit.entity.Item droppedEntity = mock(org.bukkit.entity.Item.class);
        ItemStack pickedStack = mock(ItemStack.class);
        when(pickedStack.getAmount()).thenReturn(3);
        when(pickedStack.getType()).thenReturn(Material.STONE);
        when(droppedEntity.getItemStack()).thenReturn(pickedStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(picker);
        when(event.getItem()).thenReturn(droppedEntity);

        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        Supplier<List<Player>> participants = () -> List.of(picker, other);
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> false,
                () -> true,
                participants,
                enforceCap,
                log);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handleEntityPickupItem(event);
        }

        verify(log).infoConsole("[pickup] Picker picked up 3x STONE");
        verify(sharedService).requestSharedInventorySync(picker);
        verify(enforceCap, never()).accept(any());
    }

    @Test
    void handlePlayerPickupArrow_defersSharedSync() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        PlayerPickupArrowEvent event = mock(PlayerPickupArrowEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handlePlayerPickupArrow(event);
        }

        verify(enforceCap).accept(player);
        verify(sharedService).requestSharedInventorySync(player);
    }

    @Test
    void passThroughHandlers_requestCapAndSharedSync_whenNotCancelled() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        PlayerDropItemEvent dropEvent = mock(PlayerDropItemEvent.class);
        PlayerSwapHandItemsEvent swapEvent = mock(PlayerSwapHandItemsEvent.class);
        PlayerItemHeldEvent heldEvent = mock(PlayerItemHeldEvent.class);
        PlayerItemDamageEvent damageEvent = mock(PlayerItemDamageEvent.class);
        PlayerBucketEmptyEvent bucketEmptyEvent = mock(PlayerBucketEmptyEvent.class);
        PlayerBucketFillEvent bucketFillEvent = mock(PlayerBucketFillEvent.class);

        org.bukkit.entity.Item itemEntity = mock(org.bukkit.entity.Item.class);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.STONE);
        when(itemEntity.getItemStack()).thenReturn(stack);

        when(dropEvent.getPlayer()).thenReturn(player);
        when(dropEvent.getItemDrop()).thenReturn(itemEntity);
        when(swapEvent.getPlayer()).thenReturn(player);
        when(swapEvent.getMainHandItem()).thenReturn(stack);
        when(swapEvent.getOffHandItem()).thenReturn(null);
        when(heldEvent.getPlayer()).thenReturn(player);
        when(damageEvent.getPlayer()).thenReturn(player);
        when(bucketEmptyEvent.getPlayer()).thenReturn(player);
        when(bucketFillEvent.getPlayer()).thenReturn(player);

        when(degradingService.isBarrierProtectionActive(player)).thenReturn(false);
        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handlePlayerDropItem(dropEvent);
            service.handlePlayerSwapHandItems(swapEvent);
            service.handlePlayerItemHeld(heldEvent);
            service.handlePlayerItemDamage(damageEvent);
            service.handlePlayerBucketEmpty(bucketEmptyEvent);
            service.handlePlayerBucketFill(bucketFillEvent);
        }

        verify(enforceCap, org.mockito.Mockito.times(6)).accept(player);
        verify(sharedService, org.mockito.Mockito.times(6)).requestSharedInventorySync(player);
    }

    @Test
    void dropAndSwap_cancelWhenBarrierProtectionWouldMoveBarrier() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        DegradingInventoryService degradingService = mock(DegradingInventoryService.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player player = mock(Player.class);
        PlayerDropItemEvent dropEvent = mock(PlayerDropItemEvent.class);
        PlayerSwapHandItemsEvent swapEvent = mock(PlayerSwapHandItemsEvent.class);

        org.bukkit.entity.Item itemEntity = mock(org.bukkit.entity.Item.class);
        ItemStack barrier = mock(ItemStack.class);
        when(barrier.getType()).thenReturn(Material.BARRIER);
        when(itemEntity.getItemStack()).thenReturn(barrier);

        when(dropEvent.getPlayer()).thenReturn(player);
        when(dropEvent.getItemDrop()).thenReturn(itemEntity);
        when(swapEvent.getPlayer()).thenReturn(player);
        when(swapEvent.getMainHandItem()).thenReturn(barrier);
        when(swapEvent.getOffHandItem()).thenReturn(null);

        when(degradingService.isBarrierProtectionActive(player)).thenReturn(true);
        when(degradingService.isBarrier(barrier)).thenReturn(true);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                degradingService,
                sharedService,
                p -> true,
                () -> true,
                List::of,
                enforceCap,
                mock(DeepCoreLogger.class));

        service.handlePlayerDropItem(dropEvent);
        service.handlePlayerSwapHandItems(swapEvent);

        verify(dropEvent).setCancelled(true);
        verify(swapEvent).setCancelled(true);
        verify(enforceCap, org.mockito.Mockito.times(2)).accept(player);
        verify(player, org.mockito.Mockito.times(2)).updateInventory();
        verify(sharedService, never()).requestSharedInventorySync(any());
    }

    @Test
    void projectileAndPickupHandlers_ignoreUnsupportedEntities() {
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                mock(JavaPlugin.class),
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                mock(SharedInventorySyncService.class),
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        ProjectileLaunchEvent projectileEvent = mock(ProjectileLaunchEvent.class);
        org.bukkit.entity.Projectile projectile = mock(org.bukkit.entity.Projectile.class);
        when(projectile.getShooter()).thenReturn(mock(org.bukkit.projectiles.ProjectileSource.class));
        when(projectileEvent.getEntity()).thenReturn(projectile);

        EntityPickupItemEvent pickupEvent = mock(EntityPickupItemEvent.class);
        when(pickupEvent.getEntity()).thenReturn(mock(org.bukkit.entity.LivingEntity.class));

        service.handleProjectileLaunch(projectileEvent);
        service.handleEntityPickupItem(pickupEvent);
    }

    @Test
    void handleProjectileLaunch_returnsWhenSharedInventoryNotEligible() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> false,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        Player shooter = mock(Player.class);
        Snowball snowball = mock(Snowball.class);
        when(snowball.getShooter()).thenReturn(shooter);
        ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
        when(event.getEntity()).thenReturn(snowball);

        service.handleProjectileLaunch(event);

        verify(sharedService, never()).requestSharedInventorySync(any());
        verify(sharedService, never()).consumeItemFromOtherParticipants(any(), any());
    }

    @Test
    void dragAndCraftHandlers_ignoreNonPlayerActors() {
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                mock(JavaPlugin.class),
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                mock(SharedInventorySyncService.class),
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        InventoryDragEvent dragEvent = mock(InventoryDragEvent.class);
        CraftItemEvent craftEvent = mock(CraftItemEvent.class);
        when(dragEvent.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));
        when(craftEvent.getWhoClicked()).thenReturn(mock(org.bukkit.entity.HumanEntity.class));

        service.handleInventoryDrag(dragEvent);
        service.handleCraftItem(craftEvent);

        verify(dragEvent, never()).setCancelled(true);
        verify(craftEvent, never()).setCancelled(true);
    }

    @Test
    void handlePlayerArmorChanged_consumesWhenMatchingPendingHotbarEquip() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHelmet()).thenReturn(null);

        PlayerInteractEvent interactEvent = mock(PlayerInteractEvent.class);
        when(interactEvent.getPlayer()).thenReturn(player);
        when(interactEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(interactEvent.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interactEvent.getItem()).thenReturn(helmet);

        PlayerArmorChangeEvent equipmentEvent = mock(PlayerArmorChangeEvent.class);
        when(equipmentEvent.getPlayer()).thenReturn(player);
        when(equipmentEvent.getSlotType()).thenReturn(PlayerArmorChangeEvent.SlotType.HEAD);
        when(equipmentEvent.getNewItem()).thenReturn(helmet);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            service.handlePotentialWearableUse(interactEvent);
            service.handlePlayerArmorChanged(equipmentEvent);
        }

        verify(sharedService).consumeWearableFromOtherParticipants(Material.DIAMOND_HELMET, playerId);
        verify(sharedService).capturePlayerWearableSnapshot(player);
    }

    @Test
    void handlePlayerArmorChanged_ignoresWhenNoPendingHotbarEquip() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        ItemStack chestplate = new ItemStack(Material.IRON_CHESTPLATE);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerArmorChangeEvent equipmentEvent = mock(PlayerArmorChangeEvent.class);
        when(equipmentEvent.getPlayer()).thenReturn(player);
        when(equipmentEvent.getSlotType()).thenReturn(PlayerArmorChangeEvent.SlotType.CHEST);
        when(equipmentEvent.getNewItem()).thenReturn(chestplate);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        service.handlePlayerArmorChanged(equipmentEvent);

        verify(sharedService, never()).consumeWearableFromOtherParticipants(any(), any());
        verify(sharedService, never()).capturePlayerWearableSnapshot(any());
    }

    @Test
    void handlePotentialWearableUse_repeatedRightClick_dispatchesSingleConsumeOnConfirmation() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        UUID playerId = UUID.randomUUID();
        ItemStack chestplate = new ItemStack(Material.IRON_CHESTPLATE);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getChestplate()).thenReturn(null);

        PlayerInteractEvent interactEvent = mock(PlayerInteractEvent.class);
        when(interactEvent.getPlayer()).thenReturn(player);
        when(interactEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(interactEvent.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(interactEvent.getItem()).thenReturn(chestplate);

        PlayerArmorChangeEvent equipmentEvent = mock(PlayerArmorChangeEvent.class);
        when(equipmentEvent.getPlayer()).thenReturn(player);
        when(equipmentEvent.getSlotType()).thenReturn(PlayerArmorChangeEvent.SlotType.CHEST);
        when(equipmentEvent.getNewItem()).thenReturn(chestplate);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            for (int i = 0; i < 25; i++) {
                service.handlePotentialWearableUse(interactEvent);
            }
            service.handlePlayerArmorChanged(equipmentEvent);
        }

        verify(sharedService).consumeWearableFromOtherParticipants(Material.IRON_CHESTPLATE, playerId);
        verify(sharedService).capturePlayerWearableSnapshot(player);
    }

    @Test
    void handleSpecialInventoryMutatingInteract_syncsForKnownItemCases() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        Player player = mock(Player.class);
        PlayerInteractEvent jukeboxEvent = mock(PlayerInteractEvent.class);
        ItemStack disc = new ItemStack(Material.MUSIC_DISC_CAT);
        when(jukeboxEvent.getPlayer()).thenReturn(player);
        when(jukeboxEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(jukeboxEvent.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(jukeboxEvent.getItem()).thenReturn(disc);

        PlayerInteractEvent crossbowEvent = mock(PlayerInteractEvent.class);
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        when(crossbowEvent.getPlayer()).thenReturn(player);
        when(crossbowEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(crossbowEvent.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(crossbowEvent.getItem()).thenReturn(crossbow);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(plugin), any(Runnable.class), any(Long.class));

            service.handleSpecialInventoryMutatingInteract(jukeboxEvent);
            service.handleSpecialInventoryMutatingInteract(crossbowEvent);
        }

        verify(sharedService, org.mockito.Mockito.times(2)).syncSharedInventoryFromSourceNow(player);
        verify(sharedService, org.mockito.Mockito.times(2)).capturePlayerWearableSnapshot(player);
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(25L));
    }

    @Test
    void handleSpecialInventoryMutatingEntityAndBookAndLodestoneCases_syncsDeterministically() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.NAME_TAG));

        PlayerInteractEntityEvent entityEvent = mock(PlayerInteractEntityEvent.class);
        when(entityEvent.getPlayer()).thenReturn(player);
        when(entityEvent.getHand()).thenReturn(EquipmentSlot.HAND);

        PlayerEditBookEvent editBookEvent = mock(PlayerEditBookEvent.class);
        when(editBookEvent.getPlayer()).thenReturn(player);

        PlayerTakeLecternBookEvent takeLecternEvent = mock(PlayerTakeLecternBookEvent.class);
        when(takeLecternEvent.getPlayer()).thenReturn(player);

        PlayerBucketEntityEvent bucketEntityEvent = mock(PlayerBucketEntityEvent.class);
        when(bucketEntityEvent.getPlayer()).thenReturn(player);

        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(block.getType()).thenReturn(Material.LODESTONE);
        BlockBreakEvent blockBreakEvent = mock(BlockBreakEvent.class);
        when(blockBreakEvent.getPlayer()).thenReturn(player);
        when(blockBreakEvent.getBlock()).thenReturn(block);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(plugin), any(Runnable.class), eq(1L));

            service.handleSpecialInventoryMutatingEntityInteract(entityEvent);
            service.handlePlayerEditBook(editBookEvent);
            service.handlePlayerTakeLecternBook(takeLecternEvent);
            service.handlePlayerBucketEntity(bucketEntityEvent);
            service.handleLodestoneBreak(blockBreakEvent);
        }

        verify(sharedService, org.mockito.Mockito.times(5)).syncSharedInventoryFromSourceNow(player);
        verify(sharedService, org.mockito.Mockito.times(5)).capturePlayerWearableSnapshot(player);
    }

    @Test
    void handleSpecialInventoryMutatingEntityInteract_syncsForItemFramePlacement() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                mock(ChallengeManager.class),
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                List::of,
                p -> {},
                mock(DeepCoreLogger.class));

        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND));

        PlayerInteractEntityEvent entityEvent = mock(PlayerInteractEntityEvent.class);
        when(entityEvent.getPlayer()).thenReturn(player);
        when(entityEvent.getHand()).thenReturn(EquipmentSlot.HAND);
        when(entityEvent.getRightClicked()).thenReturn(mock(ItemFrame.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTaskLater(eq(plugin), any(Runnable.class), eq(1L));

            service.handleSpecialInventoryMutatingEntityInteract(entityEvent);
        }

        verify(sharedService).syncSharedInventoryFromSourceNow(player);
        verify(sharedService).capturePlayerWearableSnapshot(player);
    }

    @Test
    void handleEntityPickupItem_notifiesOtherParticipantsWhenSharedInventoryActive() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        ChallengeManager challengeManager = mock(ChallengeManager.class);
        SharedInventorySyncService sharedService = mock(SharedInventorySyncService.class);
        @SuppressWarnings("unchecked")
        Consumer<Player> enforceCap = mock(Consumer.class);

        Player picker = mock(Player.class);
        Player other = mock(Player.class);
        UUID pickerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(picker.getUniqueId()).thenReturn(pickerId);
        when(other.getUniqueId()).thenReturn(otherId);
        when(picker.getName()).thenReturn("Picker");
        when(other.getLocation()).thenReturn(mock(Location.class));
        when(challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY))
                .thenReturn(true);

        org.bukkit.entity.Item droppedEntity = mock(org.bukkit.entity.Item.class);
        ItemStack pickedStack = new ItemStack(Material.STONE, 2);
        when(droppedEntity.getItemStack()).thenReturn(pickedStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(picker);
        when(event.getItem()).thenReturn(droppedEntity);

        InventoryMechanicsCoordinatorService service = new InventoryMechanicsCoordinatorService(
                plugin,
                challengeManager,
                mock(DegradingInventoryService.class),
                sharedService,
                p -> true,
                () -> true,
                () -> List.of(picker, other),
                enforceCap,
                mock(DeepCoreLogger.class));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                        Runnable task = invocation.getArgument(1);
                        task.run();
                        return null;
                    })
                    .when(scheduler)
                    .runTask(eq(plugin), any(Runnable.class));

            service.handleEntityPickupItem(event);
        }

        verify(other)
                .playSound(
                        eq(other.getLocation()),
                        eq(Sound.ENTITY_ITEM_PICKUP),
                        eq(SoundCategory.PLAYERS),
                        eq(0.4F),
                        eq(1.0F));
    }
}

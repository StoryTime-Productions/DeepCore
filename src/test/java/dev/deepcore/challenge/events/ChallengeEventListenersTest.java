package dev.deepcore.challenge.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.deepcore.challenge.inventory.InventoryMechanicsCoordinatorService;
import dev.deepcore.challenge.portal.PortalTransitCoordinatorService;
import dev.deepcore.challenge.session.PrepGuiCoordinatorService;
import dev.deepcore.challenge.session.RunCompletionService;
import dev.deepcore.challenge.session.RunHealthCoordinatorService;
import dev.deepcore.challenge.session.SessionPlayerLifecycleService;
import dev.deepcore.challenge.session.SessionTransitionOrchestratorService;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.Server;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ChallengeEventListenersTest {

    @Test
    void inventoryMechanicsListener_delegatesAllHandlers() {
        InventoryMechanicsCoordinatorService service = mock(InventoryMechanicsCoordinatorService.class);
        InventoryMechanicsListener listener = new InventoryMechanicsListener(service);

        listener.onInventoryClick(mock(InventoryClickEvent.class));
        listener.onInventoryCreative(mock(InventoryCreativeEvent.class));
        listener.onInventoryDrag(mock(InventoryDragEvent.class));
        listener.onDrop(mock(PlayerDropItemEvent.class));
        listener.onSwapHands(mock(PlayerSwapHandItemsEvent.class));
        listener.onItemConsume(mock(PlayerItemConsumeEvent.class));
        listener.onCraftItem(mock(CraftItemEvent.class));
        listener.onHeldSlotChange(mock(PlayerItemHeldEvent.class));
        listener.onItemDurabilityDamage(mock(PlayerItemDamageEvent.class));
        listener.onBucketEmpty(mock(PlayerBucketEmptyEvent.class));
        listener.onBucketFill(mock(PlayerBucketFillEvent.class));
        listener.onPlayerArmorChanged(mock(PlayerArmorChangeEvent.class));
        listener.onPotentialWearableUse(mock(PlayerInteractEvent.class));
        listener.onSpecialInventoryMutatingInteract(mock(PlayerInteractEvent.class));
        listener.onSpecialInventoryMutatingEntityInteract(mock(PlayerInteractEntityEvent.class));
        listener.onPlayerEditBook(mock(PlayerEditBookEvent.class));
        listener.onPlayerTakeLecternBook(mock(PlayerTakeLecternBookEvent.class));
        listener.onPlayerBucketEntity(mock(PlayerBucketEntityEvent.class));
        listener.onLodestoneBreak(mock(BlockBreakEvent.class));
        listener.onProjectileLaunch(mock(org.bukkit.event.entity.ProjectileLaunchEvent.class));
        listener.onEnderEyeUse(mock(PlayerInteractEvent.class));
        listener.onPickup(mock(EntityPickupItemEvent.class));
        listener.onEntityResurrect(mock(EntityResurrectEvent.class));
        listener.onArrowPickup(mock(PlayerPickupArrowEvent.class));
        listener.onBlockPlace(mock(BlockPlaceEvent.class));
        listener.onBlockMultiPlace(mock(BlockMultiPlaceEvent.class));

        verify(service).handleInventoryClick(any(InventoryClickEvent.class));
        verify(service).handleInventoryCreative(any(InventoryCreativeEvent.class));
        verify(service).handleInventoryDrag(any(InventoryDragEvent.class));
        verify(service).handlePlayerDropItem(any(PlayerDropItemEvent.class));
        verify(service).handlePlayerSwapHandItems(any(PlayerSwapHandItemsEvent.class));
        verify(service).handlePlayerItemConsume(any(PlayerItemConsumeEvent.class));
        verify(service).handleCraftItem(any(CraftItemEvent.class));
        verify(service).handlePlayerItemHeld(any(PlayerItemHeldEvent.class));
        verify(service).handlePlayerItemDamage(any(PlayerItemDamageEvent.class));
        verify(service).handlePlayerBucketEmpty(any(PlayerBucketEmptyEvent.class));
        verify(service).handlePlayerBucketFill(any(PlayerBucketFillEvent.class));
        verify(service).handlePlayerArmorChanged(any(PlayerArmorChangeEvent.class));
        verify(service).handlePotentialWearableUse(any(PlayerInteractEvent.class));
        verify(service).handleSpecialInventoryMutatingInteract(any(PlayerInteractEvent.class));
        verify(service).handleSpecialInventoryMutatingEntityInteract(any(PlayerInteractEntityEvent.class));
        verify(service).handlePlayerEditBook(any(PlayerEditBookEvent.class));
        verify(service).handlePlayerTakeLecternBook(any(PlayerTakeLecternBookEvent.class));
        verify(service).handlePlayerBucketEntity(any(PlayerBucketEntityEvent.class));
        verify(service).handleLodestoneBreak(any(BlockBreakEvent.class));
        verify(service).handleProjectileLaunch(any(org.bukkit.event.entity.ProjectileLaunchEvent.class));
        verify(service).handleEnderEyeUse(any(PlayerInteractEvent.class));
        verify(service).handleEntityPickupItem(any(EntityPickupItemEvent.class));
        verify(service).handleEntityResurrect(any(EntityResurrectEvent.class));
        verify(service).handlePlayerPickupArrow(any(PlayerPickupArrowEvent.class));
        verify(service).handleBlockPlace(any(BlockPlaceEvent.class));
        verify(service).handleBlockMultiPlace(any(BlockMultiPlaceEvent.class));
    }

    @Test
    void portalTransitAndPrepFlowAndSharedVitalsListeners_delegateHandlers() {
        PortalTransitCoordinatorService portalService = mock(PortalTransitCoordinatorService.class);
        PortalTransitListener portalListener = new PortalTransitListener(portalService);
        portalListener.onEntityPortalReady(mock(EntityPortalReadyEvent.class));
        portalListener.onPlayerPortal(mock(PlayerPortalEvent.class));
        portalListener.onPlayerMove(mock(PlayerMoveEvent.class));

        verify(portalService).handleEntityPortalReady(any(EntityPortalReadyEvent.class));
        verify(portalService).handlePlayerPortal(any(PlayerPortalEvent.class));
        verify(portalService).handlePlayerMove(any(PlayerMoveEvent.class));

        PrepGuiCoordinatorService prepService = mock(PrepGuiCoordinatorService.class);
        PrepFlowListener prepListener = new PrepFlowListener(prepService);
        prepListener.onPrepBookUse(mock(PlayerInteractEvent.class));
        prepListener.onPrepGuiClick(mock(InventoryClickEvent.class));
        prepListener.onPrepGuiDrag(mock(InventoryDragEvent.class));
        prepListener.onPrepBookDrop(mock(PlayerDropItemEvent.class));
        prepListener.onPrepBookSwapHands(mock(PlayerSwapHandItemsEvent.class));
        prepListener.onPrepGuiClose(mock(InventoryCloseEvent.class));

        verify(prepService).handlePrepBookUse(any(PlayerInteractEvent.class));
        verify(prepService).handleProtectedPrepBookClick(any(InventoryClickEvent.class));
        verify(prepService).handlePrepGuiClick(any(InventoryClickEvent.class));
        verify(prepService).handleProtectedPrepBookDrag(any(InventoryDragEvent.class));
        verify(prepService).handlePrepGuiDrag(any(InventoryDragEvent.class));
        verify(prepService).handleProtectedPrepBookDrop(any(PlayerDropItemEvent.class));
        verify(prepService).handleProtectedPrepBookSwapHands(any(PlayerSwapHandItemsEvent.class));
        verify(prepService).handlePrepGuiClose(any(InventoryCloseEvent.class));

        RunHealthCoordinatorService healthService = mock(RunHealthCoordinatorService.class);
        SharedVitalsListener sharedListener = new SharedVitalsListener(healthService);
        sharedListener.onRegainHealth(mock(EntityRegainHealthEvent.class));
        sharedListener.onDamage(mock(EntityDamageEvent.class));
        sharedListener.onFoodLevelChange(mock(FoodLevelChangeEvent.class));

        verify(healthService).handleEntityRegainHealth(any(EntityRegainHealthEvent.class));
        verify(healthService).handleEntityDamage(any(EntityDamageEvent.class));
        verify(healthService).handleFoodLevelChange(any(FoodLevelChangeEvent.class));
    }

    @Test
    void previewAndSessionLifecycleListeners_delegateHandlers() {
        SessionTransitionOrchestratorService transitionService = mock(SessionTransitionOrchestratorService.class);
        SessionPlayerLifecycleService lifecycleService = mock(SessionPlayerLifecycleService.class);
        RunCompletionService completionService = mock(RunCompletionService.class);

        PreviewListener previewListener = new PreviewListener(transitionService, lifecycleService);
        previewListener.onWorldLoad(mock(WorldLoadEvent.class));
        previewListener.onPlayerChangedWorld(mock(PlayerChangedWorldEvent.class));

        verify(transitionService).handleWorldLoad(any(WorldLoadEvent.class));
        verify(lifecycleService).handlePlayerChangedWorld(any(PlayerChangedWorldEvent.class));

        SessionLifecycleListener lifecycleListener = new SessionLifecycleListener(lifecycleService, completionService);
        lifecycleListener.onPlayerJoin(mock(PlayerJoinEvent.class));
        lifecycleListener.onPlayerQuit(mock(PlayerQuitEvent.class));
        lifecycleListener.onEntityDeath(mock(EntityDeathEvent.class));
        lifecycleListener.onPlayerDeath(mock(org.bukkit.event.entity.PlayerDeathEvent.class));
        lifecycleListener.onPlayerRespawn(mock(PlayerRespawnEvent.class));

        verify(lifecycleService).handlePlayerJoin(any(PlayerJoinEvent.class));
        verify(lifecycleService).handlePlayerQuit(any(PlayerQuitEvent.class));
        verify(completionService).handleEntityDeath(any(EntityDeathEvent.class));
        verify(lifecycleService).handlePlayerDeath(any(org.bukkit.event.entity.PlayerDeathEvent.class));
        verify(lifecycleService).handlePlayerRespawn(any(PlayerRespawnEvent.class));
    }

    @Test
    void challengeEventRegistrar_registersAllListenerTypes() {
        PortalTransitCoordinatorService portalService = mock(PortalTransitCoordinatorService.class);
        SessionPlayerLifecycleService lifecycleService = mock(SessionPlayerLifecycleService.class);
        RunCompletionService completionService = mock(RunCompletionService.class);
        InventoryMechanicsCoordinatorService inventoryService = mock(InventoryMechanicsCoordinatorService.class);
        RunHealthCoordinatorService healthService = mock(RunHealthCoordinatorService.class);
        SessionTransitionOrchestratorService transitionService = mock(SessionTransitionOrchestratorService.class);
        PrepGuiCoordinatorService prepService = mock(PrepGuiCoordinatorService.class);

        ChallengeEventRegistrar registrar = new ChallengeEventRegistrar(
                portalService,
                lifecycleService,
                completionService,
                inventoryService,
                healthService,
                transitionService,
                prepService);

        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);

        registrar.registerAll(plugin);

        verify(pluginManager, times(6)).registerEvents(any(org.bukkit.event.Listener.class), any(JavaPlugin.class));
    }
}

package dev.deepcore.challenge.inventory;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.deepcore.challenge.ChallengeComponent;
import dev.deepcore.challenge.ChallengeManager;
import dev.deepcore.logging.DeepCoreLogger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.GlowItemFrame;
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

/**
 * Coordinates inventory-mechanics event behavior for degrading/shared inventory
 * flows.
 */
public final class InventoryMechanicsCoordinatorService {
    private final JavaPlugin plugin;
    private final ChallengeManager challengeManager;
    private final DegradingInventoryService degradingInventoryService;
    private final SharedInventorySyncService sharedInventorySyncService;
    private final Predicate<Player> isChallengeActive;
    private final Supplier<Boolean> isSharedInventoryEnabled;
    private final Supplier<List<Player>> onlineParticipantsSupplier;
    private final Consumer<Player> enforceInventorySlotCap;
    private final DeepCoreLogger log;
    private final Map<UUID, PendingWearableEquip> pendingWearableHotbarEquips;

    /**
     * Creates an inventory mechanics coordinator service.
     *
     * @param plugin                     plugin instance used for scheduled sync
     *                                   tasks
     * @param challengeManager           challenge component state manager
     * @param degradingInventoryService  degrading inventory behavior service
     * @param sharedInventorySyncService shared inventory synchronization service
     * @param isChallengeActive          predicate that checks whether a player is
     *                                   in active challenge scope
     * @param isSharedInventoryEnabled   supplier that reports whether shared
     *                                   inventory is enabled
     * @param onlineParticipantsSupplier supplier for currently online challenge
     *                                   participants
     * @param enforceInventorySlotCap    action that enforces current degrading slot
     *                                   cap for a player
     * @param log                        logger used for inventory-related
     *                                   diagnostics
     */
    public InventoryMechanicsCoordinatorService(
            JavaPlugin plugin,
            ChallengeManager challengeManager,
            DegradingInventoryService degradingInventoryService,
            SharedInventorySyncService sharedInventorySyncService,
            Predicate<Player> isChallengeActive,
            Supplier<Boolean> isSharedInventoryEnabled,
            Supplier<List<Player>> onlineParticipantsSupplier,
            Consumer<Player> enforceInventorySlotCap,
            DeepCoreLogger log) {
        this.plugin = plugin;
        this.challengeManager = challengeManager;
        this.degradingInventoryService = degradingInventoryService;
        this.sharedInventorySyncService = sharedInventorySyncService;
        this.isChallengeActive = isChallengeActive;
        this.isSharedInventoryEnabled = isSharedInventoryEnabled;
        this.onlineParticipantsSupplier = onlineParticipantsSupplier;
        this.enforceInventorySlotCap = enforceInventorySlotCap;
        this.log = log;
        this.pendingWearableHotbarEquips = new HashMap<>();
    }

    /**
     * Handles inventory click updates for cap and shared-sync behavior.
     *
     * @param event inventory click event to process
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (degradingInventoryService.shouldCancelLockedSlotClick(event, player)) {
            event.setCancelled(true);
            enforceInventorySlotCap.accept(player);
            player.updateInventory();
            return;
        }

        requestInventorySlotCapEnforcement(player);
        requestSharedInventorySync(player);
        requestWearableEquipSync(player);
    }

    /**
     * Handles creative inventory interactions for cap and shared-sync behavior.
     *
     * @param event creative inventory click event to process
     */
    public void handleInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        requestInventorySlotCapEnforcement(player);
        requestSharedInventorySync(player);
        requestWearableEquipSync(player);
    }

    /**
     * Handles drag interactions including locked-slot barrier protection rules.
     *
     * @param event inventory drag event to process
     */
    public void handleInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (degradingInventoryService.isBarrierProtectionActive(player)
                && (degradingInventoryService.isBarrier(event.getOldCursor())
                        || event.getNewItems().values().stream().anyMatch(degradingInventoryService::isBarrier)
                        || event.getRawSlots().stream()
                                .anyMatch(rawSlot -> isLockedRawSlot(event.getView(), player, rawSlot)))) {
            event.setCancelled(true);
            enforceInventorySlotCap.accept(player);
            player.updateInventory();
            return;
        }

        requestInventorySlotCapEnforcement(player);
        requestSharedInventorySync(player);
        requestWearableEquipSync(player);
    }

    /**
     * Handles item drops and blocks dropping protected barrier items.
     *
     * @param event player drop-item event to process
     */
    public void handlePlayerDropItem(PlayerDropItemEvent event) {
        if (degradingInventoryService.isBarrierProtectionActive(event.getPlayer())
                && degradingInventoryService.isBarrier(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            enforceInventorySlotCap.accept(event.getPlayer());
            event.getPlayer().updateInventory();
            return;
        }

        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles swapping hands and protects locked barrier slots from movement.
     *
     * @param event player swap-hand-items event to process
     */
    public void handlePlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (degradingInventoryService.isBarrierProtectionActive(event.getPlayer())
                && (degradingInventoryService.isBarrier(event.getMainHandItem())
                        || degradingInventoryService.isBarrier(event.getOffHandItem()))) {
            event.setCancelled(true);
            enforceInventorySlotCap.accept(event.getPlayer());
            event.getPlayer().updateInventory();
            return;
        }

        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles consumable use updates that affect shared inventory state.
     *
     * @param event player item-consume event to process
     */
    public void handlePlayerItemConsume(PlayerItemConsumeEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles crafting interactions that may change synchronized inventories.
     *
     * @param event craft event to process
     */
    public void handleCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        requestInventorySlotCapEnforcement(player);
        requestSharedInventorySync(player);
    }

    /**
     * Handles held-slot changes for cap enforcement and synchronization.
     *
     * @param event held-item-slot change event to process
     */
    public void handlePlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        requestInventorySlotCapEnforcement(player);
        requestSharedInventorySync(player);
    }

    /**
     * Handles durability damage updates for shared inventory propagation.
     *
     * @param event item-damage event to process
     */
    public void handlePlayerItemDamage(PlayerItemDamageEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles bucket-empty events for cap enforcement and inventory sync.
     *
     * @param event bucket-empty event to process
     */
    public void handlePlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles bucket-fill events for cap enforcement and inventory sync.
     *
     * @param event bucket-fill event to process
     */
    public void handlePlayerBucketFill(PlayerBucketFillEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles fish pickup and similar bucket-entity interactions.
     *
     * @param event bucket-entity interaction event to process
     */
    public void handlePlayerBucketEntity(PlayerBucketEntityEvent event) {
        log.debug("[shared-inv:edge] bucket-entity by " + event.getPlayer().getName()
                + " -> deterministic source sync (delay=1t)");
        scheduleDeterministicSourceSync(event.getPlayer(), 1L);
    }

    /**
     * Handles right-click wearable equip attempts for shared inventory mode.
     *
     * @param event player interact event to process
     */
    public void handlePotentialWearableUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack usedItem = event.getItem();
        if (usedItem == null || usedItem.getType().isAir()) {
            return;
        }

        EquipmentSlot equipSlot = usedItem.getType().getEquipmentSlot();
        if (equipSlot != EquipmentSlot.HEAD
                && equipSlot != EquipmentSlot.CHEST
                && equipSlot != EquipmentSlot.LEGS
                && equipSlot != EquipmentSlot.FEET) {
            return;
        }

        ItemStack previouslyEquipped = getEquippedItemForSlot(player, equipSlot);
        if (previouslyEquipped != null && !previouslyEquipped.getType().isAir()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PendingWearableEquip pending = new PendingWearableEquip(equipSlot, usedItem.getType());
        pendingWearableHotbarEquips.put(playerId, pending);

        // The equip decision is finalized by handlePlayerArmorChanged. Here we only
        // mark a short-lived right-click intent to avoid double-consume with inventory
        // click/drag equip paths.
        log.debug("[shared-inv:armor-hotbar] " + player.getName() + " equipped "
                + usedItem.getType().name() + " via " + event.getHand() + " -> awaiting equipment-change confirmation");

        // Fallback path for environments where armor-change callbacks are delayed,
        // suppressed, or unavailable. If equip is visible next tick, consume once.
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            PendingWearableEquip current = pendingWearableHotbarEquips.get(playerId);
                            if (current != pending) {
                                return;
                            }

                            if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
                                return;
                            }

                            ItemStack equippedNow = getEquippedItemForSlot(player, pending.slot());
                            if (equippedNow == null
                                    || equippedNow.getType().isAir()
                                    || equippedNow.getType() != pending.material()) {
                                return;
                            }

                            pendingWearableHotbarEquips.remove(playerId);
                            sharedInventorySyncService.consumeWearableFromOtherParticipants(
                                    pending.material(), playerId);
                            sharedInventorySyncService.capturePlayerWearableSnapshot(player);

                            log.debug("[shared-inv:armor-hotbar] equip fallback confirmed for " + player.getName()
                                    + " material=" + pending.material().name() + " slot=" + pending.slot()
                                    + "; consumed wearable from others and refreshed snapshot");
                        },
                        1L);

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            PendingWearableEquip current = pendingWearableHotbarEquips.get(playerId);
                            if (current == pending) {
                                pendingWearableHotbarEquips.remove(playerId);
                            }
                        },
                        8L);
    }

    /**
     * Handles post-change equipment updates to deterministically mirror right-click
     * hotbar armor equipping.
     *
     * @param event equipment changed event to process
     */
    public void handlePlayerArmorChanged(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();

        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PendingWearableEquip pending = pendingWearableHotbarEquips.get(playerId);
        if (pending == null) {
            return;
        }

        EquipmentSlot changedSlot = toEquipmentSlot(event.getSlotType());
        if (changedSlot != pending.slot()) {
            return;
        }

        ItemStack newItem = event.getNewItem();
        if (newItem == null || newItem.getType().isAir() || newItem.getType() != pending.material()) {
            return;
        }

        pendingWearableHotbarEquips.remove(playerId);
        sharedInventorySyncService.consumeWearableFromOtherParticipants(pending.material(), playerId);
        sharedInventorySyncService.capturePlayerWearableSnapshot(player);

        log.debug("[shared-inv:armor-hotbar] equip confirmed for " + player.getName()
                + " material=" + pending.material().name() + " slot=" + pending.slot()
                + "; consumed wearable from others and refreshed snapshot");
    }

    /**
     * Handles special right-click block/item interactions known to mutate
     * inventory in hard-to-capture ways.
     *
     * @param event player interact event to process
     */
    public void handleSpecialInventoryMutatingInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack usedItem = event.getItem();
        if (usedItem == null || usedItem.getType().isAir()) {
            return;
        }

        Material material = usedItem.getType();
        boolean shouldSyncNow = false;
        long delayTicks = 1L;

        if (material.name().startsWith("MUSIC_DISC_")) {
            shouldSyncNow = true;
        } else if (material == Material.COMPASS
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.LODESTONE) {
            shouldSyncNow = true;
        } else if (material == Material.ARMOR_STAND
                || material == Material.ITEM_FRAME
                || material == Material.GLOW_ITEM_FRAME
                || material == Material.END_CRYSTAL
                || material == Material.BONE_MEAL
                || material == Material.GLOWSTONE
                || material.name().endsWith("_SPAWN_EGG")) {
            shouldSyncNow = true;
        } else if (material == Material.CROSSBOW) {
            // Crossbow loading consumes ammo after draw completes, not at initial click.
            shouldSyncNow = true;
            delayTicks = 25L;
        }

        if (!shouldSyncNow) {
            return;
        }

        log.debug("[shared-inv:edge] interact " + material.name() + " by " + player.getName()
                + " action=" + action.name() + " hand=" + event.getHand()
                + " -> deterministic source sync (delay=" + delayTicks + "t)");

        scheduleDeterministicSourceSync(player, delayTicks);
    }

    /**
     * Handles special right-click entity interactions that can consume items.
     *
     * @param event player interact-entity event to process
     */
    public void handleSpecialInventoryMutatingEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack heldItem = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (heldItem == null || heldItem.getType().isAir()) {
            return;
        }

        Material material = heldItem.getType();
        boolean frameInteract =
                event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof GlowItemFrame;
        if (material == Material.SADDLE || material == Material.NAME_TAG || frameInteract) {
            log.debug("[shared-inv:edge] entity-interact " + material.name() + " by " + player.getName() + " hand="
                    + hand + " -> deterministic source sync (delay=1t)");
            scheduleDeterministicSourceSync(player, 1L);
        }
    }

    /**
     * Handles player-signed book updates that rewrite held book content.
     *
     * @param event edit-book event to process
     */
    public void handlePlayerEditBook(PlayerEditBookEvent event) {
        log.debug("[shared-inv:edge] sign/edit-book by " + event.getPlayer().getName()
                + " -> deterministic source sync (delay=1t)");
        scheduleDeterministicSourceSync(event.getPlayer(), 1L);
    }

    /**
     * Handles lectern book removal that mutates player inventory.
     *
     * @param event take-lectern-book event to process
     */
    public void handlePlayerTakeLecternBook(PlayerTakeLecternBookEvent event) {
        log.debug("[shared-inv:edge] take-lectern-book by " + event.getPlayer().getName()
                + " -> deterministic source sync (delay=1t)");
        scheduleDeterministicSourceSync(event.getPlayer(), 1L);
    }

    /**
     * Handles lodestone breaks that can desync compass state.
     *
     * @param event block break event to process
     */
    public void handleLodestoneBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.LODESTONE) {
            return;
        }

        log.debug("[shared-inv:edge] break-lodestone by " + event.getPlayer().getName()
                + " -> deterministic source sync (delay=1t)");
        scheduleDeterministicSourceSync(event.getPlayer(), 1L);
    }

    /**
     * Handles projectile launches that consume synchronized projectile items.
     *
     * @param event projectile launch event to process
     */
    public void handleProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }

        if (!isChallengeActive.test(shooter) || !isSharedInventoryEnabled.get()) {
            return;
        }

        if (event.getEntity() instanceof AbstractArrow) {
            // Defer to a full inventory sync so Infinity/non-Infinity bow behavior is
            // mirrored exactly from the shooter's real post-shot inventory state.
            Bukkit.getScheduler().runTask(plugin, () -> requestSharedInventorySync(shooter));
            return;
        }

        Material consumedItem = null;
        if (event.getEntity() instanceof Snowball) {
            consumedItem = Material.SNOWBALL;
        } else if (event.getEntity() instanceof EnderPearl) {
            consumedItem = Material.ENDER_PEARL;
        } else if (event.getEntity() instanceof Egg) {
            consumedItem = Material.EGG;
        } else if (event.getEntity() instanceof ThrownExpBottle) {
            consumedItem = Material.EXPERIENCE_BOTTLE;
        } else if (event.getEntity() instanceof ThrownPotion thrownPotion) {
            ItemStack thrownItem = thrownPotion.getItem();
            if (thrownItem != null
                    && (thrownItem.getType() == Material.SPLASH_POTION
                            || thrownItem.getType() == Material.LINGERING_POTION)) {
                consumedItem = thrownItem.getType();
            }
        }

        if (consumedItem != null) {
            sharedInventorySyncService.consumeItemFromOtherParticipants(consumedItem, shooter.getUniqueId());
        }
    }

    /**
     * Handles totem pops so consumption is mirrored to other participants.
     *
     * @param event entity resurrect event to process
     */
    public void handleEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        sharedInventorySyncService.consumeItemFromOtherParticipants(Material.TOTEM_OF_UNDYING, player.getUniqueId());
    }

    /**
     * Handles ender-eye use so consumption is mirrored to other participants.
     *
     * @param event player interact event to process
     */
    public void handleEnderEyeUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack usedItem = event.getItem();
        if (usedItem == null || usedItem.getType() != Material.ENDER_EYE) {
            return;
        }

        sharedInventorySyncService.consumeItemFromOtherParticipants(Material.ENDER_EYE, player.getUniqueId());
    }

    /**
     * Handles item pickups and broadcasts shared pickup cues to participants.
     *
     * @param event entity pickup-item event to process
     */
    public void handleEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack pickedItem = event.getItem().getItemStack();
            log.infoConsole("[pickup] " + player.getName() + " picked up " + pickedItem.getAmount() + "x "
                    + pickedItem.getType().name());

            requestInventorySlotCapEnforcement(player);
            requestSharedInventorySync(player);

            if (isChallengeActive.test(player) && isSharedInventoryEnabled.get()) {
                for (Player participant : onlineParticipantsSupplier.get()) {
                    if (participant.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    participant.playSound(
                            participant.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4F, 1.0F);
                }
            }
        }
    }

    /**
     * Handles arrow pickups and defers a shared-inventory synchronization tick.
     *
     * @param event player pickup-arrow event to process
     */
    public void handlePlayerPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        requestInventorySlotCapEnforcement(player);
        Bukkit.getScheduler().runTask(plugin, () -> requestSharedInventorySync(player));
    }

    /**
     * Handles single block placements for slot-cap and shared-sync updates.
     *
     * @param event block place event to process
     */
    public void handleBlockPlace(BlockPlaceEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    /**
     * Handles multi block placements for slot-cap and shared-sync updates.
     *
     * @param event multi block place event to process
     */
    public void handleBlockMultiPlace(BlockMultiPlaceEvent event) {
        requestInventorySlotCapEnforcement(event.getPlayer());
        requestSharedInventorySync(event.getPlayer());
    }

    private void requestSharedInventorySync(Player source) {
        sharedInventorySyncService.requestSharedInventorySync(source);
    }

    private void requestWearableEquipSync(Player source) {
        sharedInventorySyncService.requestWearableEquipSync(source);
    }

    private void requestInventorySlotCapEnforcement(Player player) {
        if (!isChallengeActive.test(player)
                || !challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isChallengeActive.test(player)
                    || !challengeManager.isComponentEnabled(ChallengeComponent.DEGRADING_INVENTORY)) {
                return;
            }
            enforceInventorySlotCap.accept(player);
        });
    }

    private boolean isLockedRawSlot(InventoryView view, Player player, int rawSlot) {
        return degradingInventoryService.isLockedRawSlot(view, player, rawSlot);
    }

    private void scheduleDeterministicSourceSync(Player player, long delayTicks) {
        if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
            return;
        }

        Runnable task = () -> {
            if (!isChallengeActive.test(player) || !isSharedInventoryEnabled.get()) {
                return;
            }

            log.debug("[shared-inv:sync] deterministic source sync from " + player.getName() + " (delay=" + delayTicks
                    + "t)");
            sharedInventorySyncService.syncSharedInventoryFromSourceNow(player);
            sharedInventorySyncService.capturePlayerWearableSnapshot(player);
        };

        if (delayTicks <= 0L) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    private ItemStack getEquippedItemForSlot(Player player, EquipmentSlot slot) {
        PlayerInventory inventory = player.getInventory();
        return switch (slot) {
            case HEAD -> inventory.getHelmet();
            case CHEST -> inventory.getChestplate();
            case LEGS -> inventory.getLeggings();
            case FEET -> inventory.getBoots();
            default -> null;
        };
    }

    private EquipmentSlot toEquipmentSlot(PlayerArmorChangeEvent.SlotType slotType) {
        return switch (slotType) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    private record PendingWearableEquip(EquipmentSlot slot, Material material) {}
}

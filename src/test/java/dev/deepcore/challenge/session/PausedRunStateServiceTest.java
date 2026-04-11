package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

class PausedRunStateServiceTest {

    @Test
    void applySnapshotIfPresent_isNoOpWhenNoSnapshotExists() {
        PausedRunStateService service = new PausedRunStateService(20.0D);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        service.applySnapshotIfPresent(player);

        verify(player, never()).getInventory();
    }

    @Test
    void stashAndApply_roundTripsPlayerState_andClearsLobbyInventory() {
        PausedRunStateService service = new PausedRunStateService(20.0D);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        AttributeInstance attribute = mock(AttributeInstance.class);
        World world = mock(World.class);

        UUID playerId = UUID.randomUUID();
        Location runLocation = new Location(world, 10.0D, 64.0D, -5.0D);
        Location lobbySpawn = new Location(world, 0.0D, 100.0D, 0.0D);

        ItemStack[] storage = new ItemStack[] {new ItemStack(Material.STONE), null};
        ItemStack[] armor = new ItemStack[] {new ItemStack(Material.DIAMOND_HELMET), null, null, null};
        ItemStack[] extra = new ItemStack[] {new ItemStack(Material.SHIELD)};

        PotionEffect active = new PotionEffect(PotionEffectType.SPEED, 120, 1);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getAttribute(any(Attribute.class))).thenReturn(attribute);
        when(attribute.getBaseValue()).thenReturn(20.0D, 10.0D);
        when(player.getLocation()).thenReturn(runLocation);
        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.getArmorContents()).thenReturn(armor);
        when(inventory.getExtraContents()).thenReturn(extra);
        when(player.getHealth()).thenReturn(30.0D);
        when(player.getFoodLevel()).thenReturn(18);
        when(player.getSaturation()).thenReturn(5.0F);
        when(player.getExhaustion()).thenReturn(1.25F);
        when(player.getFireTicks()).thenReturn(40);
        when(player.getRemainingAir()).thenReturn(250);
        when(player.getLevel()).thenReturn(12);
        when(player.getExp()).thenReturn(0.65F);
        when(player.getTotalExperience()).thenReturn(345);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(true);
        when(player.isFlying()).thenReturn(false);
        when(player.getActivePotionEffects()).thenReturn(List.of(active));

        Supplier<Location> lobbySupplier = () -> lobbySpawn;
        service.stashRunStateForLobby(player, true, lobbySupplier);
        service.applySnapshotIfPresent(player);

        verify(inventory).clear();
        verify(inventory, atLeastOnce()).setArmorContents(argThat(items -> items.length == 4));
        verify(inventory, atLeastOnce()).setExtraContents(argThat(items -> items.length == 1));
        verify(player).setItemOnCursor(null);

        verify(player).teleport(lobbySpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        verify(player).teleport(runLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);

        verify(inventory)
                .setStorageContents(argThat(
                        items -> items.length == 2 && items[0] != null && items[0].getType() == Material.STONE));
        verify(attribute).setBaseValue(20.0D);
        verify(player).setHealth(20.0D);
        verify(player).setFoodLevel(18);
        verify(player).setSaturation(5.0F);
        verify(player).setExhaustion(1.25F);
        verify(player).setFireTicks(40);
        verify(player).setRemainingAir(250);
        verify(player).setLevel(12);
        verify(player).setExp(0.65F);
        verify(player).setTotalExperience(345);
        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(player).setAllowFlight(true);
        verify(player).setFlying(false);
        verify(player).removePotionEffect(PotionEffectType.SPEED);
        verify(player).addPotionEffect(active);
    }

    @Test
    void stashRunStateForLobby_skipsTeleportWhenDisabledOrSpawnMissing() {
        PausedRunStateService service = new PausedRunStateService(20.0D);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getAttribute(any(Attribute.class))).thenReturn(null);
        when(player.getLocation()).thenReturn(new Location(null, 1.0D, 2.0D, 3.0D));
        when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(inventory.getExtraContents()).thenReturn(new ItemStack[0]);
        when(player.getHealth()).thenReturn(10.0D);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getSaturation()).thenReturn(0.0F);
        when(player.getExhaustion()).thenReturn(0.0F);
        when(player.getFireTicks()).thenReturn(0);
        when(player.getRemainingAir()).thenReturn(300);
        when(player.getLevel()).thenReturn(0);
        when(player.getExp()).thenReturn(0.0F);
        when(player.getTotalExperience()).thenReturn(0);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(false);
        when(player.isFlying()).thenReturn(false);
        when(player.getActivePotionEffects()).thenReturn(List.of());

        service.stashRunStateForLobby(player, false, () -> null);
        service.stashRunStateForLobby(player, true, () -> null);

        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void clearSnapshots_removesPreviouslyCapturedSnapshot() {
        PausedRunStateService service = new PausedRunStateService(20.0D);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getAttribute(any(Attribute.class))).thenReturn(null);
        when(player.getLocation()).thenReturn(new Location(null, 0.0D, 0.0D, 0.0D));
        when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(inventory.getExtraContents()).thenReturn(new ItemStack[0]);
        when(player.getHealth()).thenReturn(1.0D);
        when(player.getFoodLevel()).thenReturn(1);
        when(player.getSaturation()).thenReturn(0.0F);
        when(player.getExhaustion()).thenReturn(0.0F);
        when(player.getFireTicks()).thenReturn(0);
        when(player.getRemainingAir()).thenReturn(0);
        when(player.getLevel()).thenReturn(0);
        when(player.getExp()).thenReturn(0.0F);
        when(player.getTotalExperience()).thenReturn(0);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(false);
        when(player.isFlying()).thenReturn(false);
        when(player.getActivePotionEffects()).thenReturn(List.of());

        service.stashRunStateForLobby(player, false, () -> null);
        service.clearSnapshots();

        assertDoesNotThrow(() -> service.applySnapshotIfPresent(player));
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
    }
}

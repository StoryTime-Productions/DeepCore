package dev.deepcore.challenge.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.ui.PrepBookService;
import dev.deepcore.challenge.world.WorldClassificationService;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class PlayerLobbyStateServiceTest {

    @Test
    void enforceSurvivalOnWorldEntry_keepsEliminatedHardcorePlayerUntouchedDuringRun() {
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PlayerLobbyStateService service = new PlayerLobbyStateService(worldClassificationService, prepBookService);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        service.enforceSurvivalOnWorldEntry(player, true, true, Set.of(playerId));

        verify(player, never()).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void enforceSurvivalOnWorldEntry_setsSurvivalForNonEliminatedOrNonHardcore() {
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PlayerLobbyStateService service = new PlayerLobbyStateService(worldClassificationService, prepBookService);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);

        service.enforceSurvivalOnWorldEntry(player, true, true, Set.of(UUID.randomUUID()));

        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void applyLobbyInventoryLoadoutIfInLobbyWorld_resetsInventoryAndGivesPrepBook() {
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PlayerLobbyStateService service = new PlayerLobbyStateService(worldClassificationService, prepBookService);

        Player player = mock(Player.class);
        World world = mock(World.class);
        PlayerInventory inventory = mock(PlayerInventory.class);

        when(player.getWorld()).thenReturn(world);
        when(worldClassificationService.isLobbyOrLimboWorld(world)).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getExtraContents()).thenReturn(new org.bukkit.inventory.ItemStack[2]);

        service.applyLobbyInventoryLoadoutIfInLobbyWorld(player);

        verify(inventory).clear();
        verify(inventory).setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        verify(inventory).setExtraContents(new org.bukkit.inventory.ItemStack[2]);
        verify(player).setItemOnCursor(null);
        verify(prepBookService).giveIfMissing(player);
        verify(player).updateInventory();
    }

    @Test
    void applyLobbyInventoryLoadoutIfInLobbyWorld_doesNothingOutsideLobby() {
        WorldClassificationService worldClassificationService = mock(WorldClassificationService.class);
        PrepBookService prepBookService = mock(PrepBookService.class);
        PlayerLobbyStateService service = new PlayerLobbyStateService(worldClassificationService, prepBookService);

        Player player = mock(Player.class);
        World world = mock(World.class);

        when(player.getWorld()).thenReturn(world);
        when(worldClassificationService.isLobbyOrLimboWorld(world)).thenReturn(false);

        service.applyLobbyInventoryLoadoutIfInLobbyWorld(player);

        verify(player, never()).getInventory();
        verify(prepBookService, never()).giveIfMissing(player);
    }
}

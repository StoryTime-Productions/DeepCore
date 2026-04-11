package dev.deepcore.challenge.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.challenge.session.RunUiFormattingService;
import dev.deepcore.challenge.session.SidebarModel;
import dev.deepcore.challenge.world.WorldClassificationService;
import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class LobbySidebarCoordinatorServiceTest {

    @Test
    void refreshForOnlinePlayers_clearsNonLobbyAndAppliesLobbySidebar() {
        LobbySidebarService sidebar = mock(LobbySidebarService.class);
        WorldClassificationService worldClassifier = mock(WorldClassificationService.class);
        RunUiFormattingService formatting = mock(RunUiFormattingService.class);

        LobbySidebarCoordinatorService service =
                new LobbySidebarCoordinatorService(sidebar, worldClassifier, formatting);

        Player lobbyPlayer = mock(Player.class);
        Player nonLobbyPlayer = mock(Player.class);
        World lobbyWorld = mock(World.class);
        World runWorld = mock(World.class);

        when(lobbyPlayer.getWorld()).thenReturn(lobbyWorld);
        when(nonLobbyPlayer.getWorld()).thenReturn(runWorld);
        when(worldClassifier.isLobbyOrLimboWorld(lobbyWorld)).thenReturn(true);
        when(worldClassifier.isLobbyOrLimboWorld(runWorld)).thenReturn(false);

        SidebarModel model = new SidebarModel(1, 2, 3, 4, 5, 6, 7, "Prep", 8);

        service.refreshForOnlinePlayers(List.of(lobbyPlayer, nonLobbyPlayer), model);

        verify(sidebar)
                .applyLobbySidebar(
                        org.mockito.ArgumentMatchers.eq(lobbyPlayer),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.eq(3L),
                        org.mockito.ArgumentMatchers.eq(4L),
                        org.mockito.ArgumentMatchers.eq(5L),
                        org.mockito.ArgumentMatchers.eq(6L),
                        org.mockito.ArgumentMatchers.eq(7),
                        org.mockito.ArgumentMatchers.eq("Prep"),
                        org.mockito.ArgumentMatchers.eq(8),
                        org.mockito.ArgumentMatchers.any());
        verify(sidebar).clearLobbySidebar(nonLobbyPlayer);
    }
}

package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ParticipantsViewTest {

    @Test
    void onlineQueries_andParticipantFilters_returnExpectedSnapshots() {
        ParticipantsView view = new ParticipantsView();
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(p1.getUniqueId()).thenReturn(id1);
        when(p2.getUniqueId()).thenReturn(id2);
        when(p1.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p2.getGameMode()).thenReturn(GameMode.SPECTATOR);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(p1, p2));

            assertEquals(2, view.onlineCount());
            assertEquals(2, view.onlinePlayers().size());
            assertEquals(Set.of(id1, id2), view.onlinePlayerIds());
            assertEquals(List.of(p1), view.onlineParticipants(Set.of(id1)));
            assertEquals(List.of(p1), view.activeParticipants(Set.of(id1, id2)));
            assertEquals(List.of(p1), view.sharedVitalsParticipants(true, Set.of(id1)));
            assertEquals(2, view.sharedVitalsParticipants(false, Set.of()).size());
        }
    }
}

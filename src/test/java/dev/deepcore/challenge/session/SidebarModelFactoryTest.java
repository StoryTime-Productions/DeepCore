package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.deepcore.records.RunRecordsService;
import org.junit.jupiter.api.Test;

class SidebarModelFactoryTest {

    @Test
    void create_withNullRecordsUsesFallbacksAndPhaseMapping() {
        SidebarModelFactory factory = new SidebarModelFactory();

        SidebarModel prep = factory.create(null, SessionState.Phase.PREP, 1, 2);
        SidebarModel countdown = factory.create(null, SessionState.Phase.COUNTDOWN, 1, 2);
        SidebarModel paused = factory.create(null, SessionState.Phase.PAUSED, 1, 2);
        SidebarModel running = factory.create(null, SessionState.Phase.RUNNING, 1, 2);

        assertEquals(-1L, prep.bestOverall());
        assertEquals(2, prep.onlineCount());
        assertEquals(1, prep.readyCount());
        assertTrue(prep.phaseText().contains("Prep"));
        assertTrue(countdown.phaseText().contains("Countdown"));
        assertTrue(paused.phaseText().contains("Paused"));
        assertTrue(running.phaseText().contains("Running"));
    }

    @Test
    void create_withRecordsServiceReadsAllBestSplits() {
        SidebarModelFactory factory = new SidebarModelFactory();
        RunRecordsService records = mock(RunRecordsService.class);
        when(records.getBestOverallTime()).thenReturn(10L);
        when(records.getBestSectionTime("overworld_to_nether")).thenReturn(20L);
        when(records.getBestSectionTime("nether_to_blaze_rods")).thenReturn(30L);
        when(records.getBestSectionTime("blaze_rods_to_end")).thenReturn(40L);
        when(records.getBestSectionTime("nether_to_end")).thenReturn(50L);
        when(records.getBestSectionTime("end_to_dragon")).thenReturn(60L);

        SidebarModel model = factory.create(records, SessionState.Phase.RUNNING, 3, 4);

        assertEquals(10L, model.bestOverall());
        assertEquals(20L, model.bestOverworldToNether());
        assertEquals(30L, model.bestNetherToBlaze());
        assertEquals(40L, model.bestBlazeToEnd());
        assertEquals(50L, model.bestNetherToEnd());
        assertEquals(60L, model.bestEndToDragon());
    }
}

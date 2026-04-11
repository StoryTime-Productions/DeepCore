package dev.deepcore.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunRecordTest {

    @Test
    void gettersAndParticipants_areExposed() {
        RunRecord record = new RunRecord(1000L, 2000L, 300L, 400L, 500L, 600L, 700L, "Alice, Bob ,  Charlie");

        assertEquals(1000L, record.getTimestamp());
        assertEquals(2000L, record.getOverallTimeMs());
        assertEquals(300L, record.getOverworldToNetherMs());
        assertEquals(400L, record.getNetherToBlazeRodsMs());
        assertEquals(500L, record.getBlazeRodsToEndMs());
        assertEquals(600L, record.getNetherToEndMs());
        assertEquals(700L, record.getEndToDragonMs());
        assertEquals("Alice, Bob ,  Charlie", record.getParticipantsCsv());
        assertEquals(List.of("Alice", "Bob", "Charlie"), record.getParticipants());
    }

    @Test
    void getParticipants_handlesBlankAndEmptyEntries() {
        RunRecord blank = new RunRecord(0L, 0L, 0L, 0L, 0L, 0L, 0L, "  ");
        RunRecord sparse = new RunRecord(0L, 0L, 0L, 0L, 0L, 0L, 0L, "A,, ,B");

        assertTrue(blank.getParticipants().isEmpty());
        assertEquals(List.of("A", "B"), sparse.getParticipants());
    }

    @Test
    void toString_containsCoreFields() {
        RunRecord record = new RunRecord(1L, 2L, 3L, 4L, 5L, 6L, 7L, "P1,P2");
        String text = record.toString();

        assertTrue(text.contains("overallTimeMs=2"));
        assertTrue(text.contains("participantsCsv='P1,P2'"));
    }
}

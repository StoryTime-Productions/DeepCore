package dev.deepcore.challenge.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class RunUiFormattingServiceTest {

    @Test
    void formatSplitDuration_handlesMinuteAndHourFormats() {
        RunUiFormattingService service = new RunUiFormattingService();

        assertEquals("00:00", service.formatSplitDuration(-1L));
        assertEquals("01:05", service.formatSplitDuration(65_000L));
        assertEquals("01:01:01", service.formatSplitDuration(3_661_000L));
    }

    @Test
    void formatElapsedTime_accountsForPausedDurations() {
        RunUiFormattingService service = new RunUiFormattingService();

        assertEquals("00:00", service.formatElapsedTime(0L, 2_000L, 0L, false, 0L));
        assertEquals("00:10", service.formatElapsedTime(1_000L, 11_000L, 0L, false, 0L));
        assertEquals("00:08", service.formatElapsedTime(1_000L, 11_000L, 1_000L, true, 10_000L));
    }

    @Test
    void buildRunActionBarMessage_returnsComponent() {
        RunUiFormattingService service = new RunUiFormattingService();

        assertNotNull(service.buildRunActionBarMessage("Enter the Nether", "00:15"));
    }
}

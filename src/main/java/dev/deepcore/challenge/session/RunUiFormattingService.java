package dev.deepcore.challenge.session;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Formats run status text for action bars and split logging.
 */
public final class RunUiFormattingService {
    /**
     * Formats a split duration in mm:ss or hh:mm:ss format.
     *
     * @param durationMs split duration in milliseconds
     * @return formatted duration string suitable for status displays
     */
    public String formatSplitDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Formats elapsed run time accounting for paused duration.
     *
     * @param runStartMillis          timestamp when the run started
     * @param referenceNowMillis      reference timestamp used as "now"
     * @param accumulatedPausedMillis total paused duration already accumulated
     * @param pausedPhase             whether the run is currently paused
     * @param pausedStartedMillis     timestamp when the current pause started
     * @return formatted elapsed time text for action bars and sidebars
     */
    public String formatElapsedTime(
            long runStartMillis,
            long referenceNowMillis,
            long accumulatedPausedMillis,
            boolean pausedPhase,
            long pausedStartedMillis) {
        if (runStartMillis <= 0L) {
            return "00:00";
        }

        long pausedSoFar = accumulatedPausedMillis;
        if (pausedPhase && pausedStartedMillis > 0L) {
            pausedSoFar += Math.max(0L, referenceNowMillis - pausedStartedMillis);
        }

        long elapsedSeconds = Math.max(0L, (referenceNowMillis - runStartMillis - pausedSoFar) / 1000L);
        long hours = elapsedSeconds / 3600L;
        long minutes = (elapsedSeconds % 3600L) / 60L;
        long seconds = elapsedSeconds % 60L;

        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Builds the action-bar component containing objective and elapsed time.
     *
     * @param objectiveText objective status text to display
     * @param elapsedText   elapsed runtime text to display
     * @return formatted action-bar component for participant updates
     */
    public Component buildRunActionBarMessage(String objectiveText, String elapsedText) {
        return Component.text("Objective: ", NamedTextColor.YELLOW)
                .append(Component.text(objectiveText, NamedTextColor.GOLD))
                .append(Component.text("  |  Time: ", NamedTextColor.YELLOW))
                .append(Component.text(elapsedText, NamedTextColor.AQUA));
    }
}

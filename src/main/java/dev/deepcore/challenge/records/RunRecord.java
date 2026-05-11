package dev.deepcore.challenge.records;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a recorded team speedrun completion with timing data for each
 * section.
 * Sections are:
 * - Overworld to Nether
 * - Nether to End
 * - End to Dragon defeat
 */
public class RunRecord {
    private final long timestamp;
    private final long overallTimeMs;
    private final long overworldToNetherMs;
    private final long netherToBlazeRodsMs;
    private final long blazeRodsToEndMs;
    private final long netherToEndMs;
    private final long endToDragonMs;
    private final String participantsCsv;
    private final String componentsCsv;
    private final String difficulty;

    /**
     * Creates a run record snapshot with split timings, participant names, and
     * enabled mechanics.
     *
     * @param timestamp           record timestamp in epoch milliseconds
     * @param overallTimeMs       total run completion time in milliseconds
     * @param overworldToNetherMs elapsed time to first nether entry
     * @param netherToBlazeRodsMs elapsed time from nether entry to blaze rods
     * @param blazeRodsToEndMs    elapsed time from blaze rods to end entry
     * @param netherToEndMs       elapsed time from first nether entry to end entry
     * @param endToDragonMs       elapsed time from end entry to dragon defeat
     * @param participantsCsv     comma-separated participant names
     * @param componentsCsv       comma-separated enabled component keys
     * @param difficulty          difficulty key for the run (easy/normal/hard)
     */
    public RunRecord(
            long timestamp,
            long overallTimeMs,
            long overworldToNetherMs,
            long netherToBlazeRodsMs,
            long blazeRodsToEndMs,
            long netherToEndMs,
            long endToDragonMs,
            String participantsCsv,
            String componentsCsv,
            String difficulty) {
        this.timestamp = timestamp;
        this.overallTimeMs = overallTimeMs;
        this.overworldToNetherMs = overworldToNetherMs;
        this.netherToBlazeRodsMs = netherToBlazeRodsMs;
        this.blazeRodsToEndMs = blazeRodsToEndMs;
        this.netherToEndMs = netherToEndMs;
        this.endToDragonMs = endToDragonMs;
        this.participantsCsv = participantsCsv == null ? "" : participantsCsv;
        this.componentsCsv = componentsCsv == null ? "" : componentsCsv;
        this.difficulty = difficulty == null ? "" : difficulty;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getOverallTimeMs() {
        return overallTimeMs;
    }

    public long getOverworldToNetherMs() {
        return overworldToNetherMs;
    }

    public long getNetherToBlazeRodsMs() {
        return netherToBlazeRodsMs;
    }

    public long getBlazeRodsToEndMs() {
        return blazeRodsToEndMs;
    }

    public long getNetherToEndMs() {
        return netherToEndMs;
    }

    public long getEndToDragonMs() {
        return endToDragonMs;
    }

    public String getParticipantsCsv() {
        return participantsCsv;
    }

    public List<String> getParticipants() {
        if (participantsCsv.isBlank()) {
            return List.of();
        }

        return Arrays.stream(participantsCsv.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }

    public String getComponentsCsv() {
        return componentsCsv;
    }

    public String getDifficulty() {
        return difficulty;
    }

    /**
     * Returns the enabled component keys stored in this record.
     *
     * @return list of enabled challenge component keys, empty for standard runs
     */
    public List<String> getComponentKeys() {
        if (componentsCsv.isBlank()) {
            return List.of();
        }

        return Arrays.stream(componentsCsv.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "RunRecord{" + "timestamp="
                + timestamp + ", overallTimeMs="
                + overallTimeMs + ", overworldToNetherMs="
                + overworldToNetherMs + ", netherToBlazeRodsMs="
                + netherToBlazeRodsMs + ", blazeRodsToEndMs="
                + blazeRodsToEndMs + ", netherToEndMs="
                + netherToEndMs + ", endToDragonMs="
                + endToDragonMs + ", participantsCsv='"
                + participantsCsv + "', componentsCsv='"
                + componentsCsv + "', difficulty='"
                + difficulty + '\'' + "}";
    }
}

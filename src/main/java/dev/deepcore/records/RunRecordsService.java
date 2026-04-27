package dev.deepcore.records;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages global speedrun record persistence using SQLite.
 * Tracks team speedrun completion times with section breakdowns.
 */
public class RunRecordsService {
    private final JavaPlugin plugin;
    private final DeepCoreLogger log;
    private final String dbPath;
    private Connection connection;

    /**
     * Creates a records service bound to this plugin's data folder and logger.
     *
     * @param plugin plugin providing data folder and logger context
     */
    public RunRecordsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = ((DeepCorePlugin) plugin).getDeepCoreLogger();
        this.dbPath = "jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/records.db";
    }

    /**
     * Initializes the database connection and creates tables if they don't exist.
     */
    public void initialize() {
        try {
            openConnection();
            createTablesIfNotExist();
            log.debug("RunRecordsService initialized successfully.");
        } catch (SQLException e) {
            log.error("Failed to initialize RunRecordsService: " + e.getMessage(), e);
        }
    }

    /**
     * Opens a connection to the SQLite database.
     */
    private void openConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbPath);
            connection.setAutoCommit(true);
        }
    }

    /**
     * Creates tables if they don't already exist.
     */
    private void createTablesIfNotExist() throws SQLException {
        createRunRecordsTableIfMissing();

        // Explicitly reset legacy schemas that still carry old NOT NULL player_uuid.
        if (hasColumn("player_uuid")) {
            log.warn("Legacy run_records schema detected (player_uuid). Wiping run_records to apply current schema.");
            recreateRunRecordsTable();
        }
    }

    private boolean hasColumn(String columnName) throws SQLException {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(run_records)")) {
            while (rs.next()) {
                String existing = rs.getString("name");
                if (existing != null && existing.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createRunRecordsTableIfMissing() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS run_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "timestamp LONG NOT NULL,"
                    + "overall_time_ms LONG NOT NULL,"
                    + "overworld_to_nether_ms LONG NOT NULL,"
                    + "nether_to_blaze_rods_ms LONG NOT NULL,"
                    + "blaze_rods_to_end_ms LONG NOT NULL,"
                    + "nether_to_end_ms LONG NOT NULL,"
                    + "end_to_dragon_ms LONG NOT NULL,"
                    + "participants TEXT NOT NULL DEFAULT ''"
                    + ")");
        }
    }

    private void recreateRunRecordsTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS run_records");
        }
        createRunRecordsTableIfMissing();
        log.debug("run_records table recreated with current schema.");
    }

    /**
     * Records a completed team speedrun with section timings.
     *
     * @param overallTimeMs       total elapsed time in milliseconds
     * @param overworldToNetherMs time from start to reaching Nether
     * @param netherToBlazeRodsMs time from Nether entry to blaze objective
     *                            completion
     * @param blazeRodsToEndMs    time from blaze objective completion to End entry
     * @param netherToEndMs       time from Nether to End
     * @param endToDragonMs       time from End to dragon defeat
     * @param participants        participant names included in the run
     * @return the created RunRecord
     */
    public RunRecord recordRun(
            long overallTimeMs,
            long overworldToNetherMs,
            long netherToBlazeRodsMs,
            long blazeRodsToEndMs,
            long netherToEndMs,
            long endToDragonMs,
            List<String> participants) {
        long timestamp = System.currentTimeMillis();
        String participantsCsv = encodeParticipants(participants);

        try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO run_records "
                + "(timestamp, overall_time_ms, overworld_to_nether_ms, nether_to_blaze_rods_ms, blaze_rods_to_end_ms, nether_to_end_ms, end_to_dragon_ms, participants) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            pstmt.setLong(1, timestamp);
            pstmt.setLong(2, overallTimeMs);
            pstmt.setLong(3, overworldToNetherMs);
            pstmt.setLong(4, netherToBlazeRodsMs);
            pstmt.setLong(5, blazeRodsToEndMs);
            pstmt.setLong(6, netherToEndMs);
            pstmt.setLong(7, endToDragonMs);
            pstmt.setString(8, participantsCsv);
            pstmt.executeUpdate();

            log.debug(String.format(
                    "Recorded team speedrun: %.2fs (Overworld→Nether: %.2fs, Nether→Blaze Rods: %.2fs, Blaze Rods→End: %.2fs, Nether→End: %.2fs, End→Dragon: %.2fs)",
                    overallTimeMs / 1000.0,
                    overworldToNetherMs / 1000.0,
                    netherToBlazeRodsMs / 1000.0,
                    blazeRodsToEndMs / 1000.0,
                    netherToEndMs / 1000.0,
                    endToDragonMs / 1000.0));
        } catch (SQLException e) {
            log.error("Failed to record speedrun: " + e.getMessage(), e);
        }

        return new RunRecord(
                timestamp,
                overallTimeMs,
                overworldToNetherMs,
                netherToBlazeRodsMs,
                blazeRodsToEndMs,
                netherToEndMs,
                endToDragonMs,
                participantsCsv);
    }

    /**
     * Gets the best overall time across all recorded runs.
     *
     * @return the best overall time in milliseconds, or -1 if no records exist
     */
    public long getBestOverallTime() {
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT MIN(overall_time_ms) as best_time FROM run_records")) {
                if (rs.next()) {
                    long bestTime = rs.getLong("best_time");
                    return bestTime == 0 ? -1 : bestTime;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query best overall time: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Gets the best time for a specific section across all runs.
     *
     * @param section the section name: "overworld_to_nether", "nether_to_end", or
     *                "end_to_dragon"
     * @return the best section time in milliseconds, or -1 if no records exist
     */
    public long getBestSectionTime(String section) {
        String columnName;
        switch (section.toLowerCase()) {
            case "overworld_to_nether":
                columnName = "overworld_to_nether_ms";
                break;
            case "nether_to_end":
                columnName = "nether_to_end_ms";
                break;
            case "nether_to_blaze_rods":
                columnName = "nether_to_blaze_rods_ms";
                break;
            case "blaze_rods_to_end":
                columnName = "blaze_rods_to_end_ms";
                break;
            case "end_to_dragon":
                columnName = "end_to_dragon_ms";
                break;
            default:
                log.warn("Unknown section: " + section);
                return -1;
        }

        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT MIN(" + columnName + ") as best_time FROM run_records")) {
                if (rs.next()) {
                    long bestTime = rs.getLong("best_time");
                    return bestTime == 0 ? -1 : bestTime;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query best section time: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Gets all recorded runs.
     *
     * @return a list of all RunRecords
     */
    public List<RunRecord> getAllRecords() {
        List<RunRecord> records = new ArrayList<>();

        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT timestamp, overall_time_ms, overworld_to_nether_ms, nether_to_blaze_rods_ms, blaze_rods_to_end_ms, nether_to_end_ms, end_to_dragon_ms, participants "
                            + "FROM run_records ORDER BY timestamp DESC")) {
                while (rs.next()) {
                    records.add(new RunRecord(
                            rs.getLong("timestamp"),
                            rs.getLong("overall_time_ms"),
                            rs.getLong("overworld_to_nether_ms"),
                            rs.getLong("nether_to_blaze_rods_ms"),
                            rs.getLong("blaze_rods_to_end_ms"),
                            rs.getLong("nether_to_end_ms"),
                            rs.getLong("end_to_dragon_ms"),
                            rs.getString("participants")));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to retrieve records: " + e.getMessage(), e);
        }

        return records;
    }

    /**
     * Closes the database connection.
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.debug("RunRecordsService database connection closed.");
            }
        } catch (SQLException e) {
            log.error("Failed to close database connection: " + e.getMessage(), e);
        }
    }

    private String encodeParticipants(List<String> participants) {
        if (participants == null || participants.isEmpty()) {
            return "";
        }

        return participants.stream()
                .map(name -> name == null ? "" : name.trim())
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(", "));
    }
}

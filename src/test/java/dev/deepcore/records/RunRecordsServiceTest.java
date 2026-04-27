package dev.deepcore.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.deepcore.DeepCorePlugin;
import dev.deepcore.logging.DeepCoreLogger;
import java.io.File;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RunRecordsServiceTest {

    @Test
    void initialize_successfullyOpensConnectionAndCreatesSchema() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        File dataFolder = new File(".");
        when(plugin.getDataFolder()).thenReturn(dataFolder);

        RunRecordsService service = new RunRecordsService(plugin);

        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.Statement createStmt = mock(java.sql.Statement.class);
        java.sql.Statement pragmaStmt = mock(java.sql.Statement.class);
        java.sql.ResultSet pragmaRs = mock(java.sql.ResultSet.class);

        try (MockedStatic<java.sql.DriverManager> driverManager =
                org.mockito.Mockito.mockStatic(java.sql.DriverManager.class)) {
            String expectedDbPath = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + "/records.db";
            driverManager
                    .when(() -> java.sql.DriverManager.getConnection(expectedDbPath))
                    .thenReturn(connection);
            when(connection.createStatement()).thenReturn(createStmt).thenReturn(pragmaStmt);
            when(pragmaStmt.executeQuery(anyString())).thenReturn(pragmaRs);
            when(pragmaRs.next()).thenReturn(false);

            service.initialize();
        }

        verify(log).debug(org.mockito.ArgumentMatchers.contains("initialized successfully"));
    }

    @Test
    void recordRun_getBestTimes_getAllRecords_andShutdown_useConnectionSuccessfully() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);

        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.PreparedStatement insertStatement = mock(java.sql.PreparedStatement.class);
        java.sql.Statement bestOverallStmt = mock(java.sql.Statement.class);
        java.sql.Statement bestSectionStmt = mock(java.sql.Statement.class);
        java.sql.Statement allRecordsStmt = mock(java.sql.Statement.class);

        java.sql.ResultSet bestOverallRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet bestSectionRs = mock(java.sql.ResultSet.class);
        java.sql.ResultSet allRecordsRs = mock(java.sql.ResultSet.class);

        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(connection.createStatement())
                .thenReturn(bestOverallStmt)
                .thenReturn(bestSectionStmt)
                .thenReturn(allRecordsStmt);

        when(bestOverallStmt.executeQuery(anyString())).thenReturn(bestOverallRs);
        when(bestOverallRs.next()).thenReturn(true);
        when(bestOverallRs.getLong("best_time")).thenReturn(1500L);

        when(bestSectionStmt.executeQuery(anyString())).thenReturn(bestSectionRs);
        when(bestSectionRs.next()).thenReturn(true);
        when(bestSectionRs.getLong("best_time")).thenReturn(600L);

        when(allRecordsStmt.executeQuery(anyString())).thenReturn(allRecordsRs);
        when(allRecordsRs.next()).thenReturn(true, true, false);
        when(allRecordsRs.getLong("timestamp")).thenReturn(2000L, 1000L);
        when(allRecordsRs.getLong("overall_time_ms")).thenReturn(40000L, 45000L);
        when(allRecordsRs.getLong("overworld_to_nether_ms")).thenReturn(5000L, 6000L);
        when(allRecordsRs.getLong("nether_to_blaze_rods_ms")).thenReturn(7000L, 8000L);
        when(allRecordsRs.getLong("blaze_rods_to_end_ms")).thenReturn(9000L, 10000L);
        when(allRecordsRs.getLong("nether_to_end_ms")).thenReturn(11000L, 12000L);
        when(allRecordsRs.getLong("end_to_dragon_ms")).thenReturn(13000L, 14000L);
        when(allRecordsRs.getString("participants")).thenReturn("A, B", "C");

        setConnection(service, connection);

        RunRecord created = service.recordRun(
                40000L, 5000L, 7000L, 9000L, 11000L, 13000L, java.util.Arrays.asList("A", " ", null, "B"));
        assertEquals("A, B", created.getParticipantsCsv());

        assertEquals(1500L, service.getBestOverallTime());
        assertEquals(600L, service.getBestSectionTime("nether_to_end"));
        assertEquals(-1L, service.getBestSectionTime("unknown"));

        List<RunRecord> all = service.getAllRecords();
        assertEquals(2, all.size());
        assertEquals("A, B", all.get(0).getParticipantsCsv());

        when(connection.isClosed()).thenReturn(false);
        service.shutdown();
        verify(connection).close();
    }

    @Test
    void getBestTimes_andRecords_returnFallbackOnSqlExceptions() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        setConnection(service, connection);

        when(connection.createStatement()).thenThrow(new java.sql.SQLException("db error"));

        assertEquals(-1L, service.getBestOverallTime());
        assertEquals(-1L, service.getBestSectionTime("end_to_dragon"));
        assertTrue(service.getAllRecords().isEmpty());
        verify(log)
                .error(
                        org.mockito.ArgumentMatchers.contains("best overall time"),
                        org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    @Test
    void getBestOverallTime_returnsMinusOneWhenQueryReturnsZero() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.Statement statement = mock(java.sql.Statement.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);

        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("best_time")).thenReturn(0L);

        setConnection(service, connection);
        assertEquals(-1L, service.getBestOverallTime());
    }

    @Test
    void getBestSectionTime_returnsMinusOneWhenQueryReturnsZero() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.Statement statement = mock(java.sql.Statement.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);

        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getLong("best_time")).thenReturn(0L);

        setConnection(service, connection);
        assertEquals(-1L, service.getBestSectionTime("end_to_dragon"));
    }

    @Test
    void getBestTimes_returnMinusOneWhenResultSetHasNoRows() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.Statement stmt1 = mock(java.sql.Statement.class);
        java.sql.Statement stmt2 = mock(java.sql.Statement.class);
        java.sql.ResultSet rs1 = mock(java.sql.ResultSet.class);
        java.sql.ResultSet rs2 = mock(java.sql.ResultSet.class);

        when(connection.createStatement()).thenReturn(stmt1).thenReturn(stmt2);
        when(stmt1.executeQuery(anyString())).thenReturn(rs1);
        when(stmt2.executeQuery(anyString())).thenReturn(rs2);
        when(rs1.next()).thenReturn(false);
        when(rs2.next()).thenReturn(false);

        setConnection(service, connection);

        assertEquals(-1L, service.getBestOverallTime());
        assertEquals(-1L, service.getBestSectionTime("overworld_to_nether"));
    }

    @Test
    void recordRun_logsFailureWhenInsertThrows_andEncodesParticipants() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);

        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("insert failed"));
        setConnection(service, connection);

        RunRecord record =
                service.recordRun(100L, 10L, 20L, 30L, 40L, 50L, java.util.Arrays.asList("A", " ", null, "B"));

        assertEquals("A, B", record.getParticipantsCsv());
        verify(log)
                .error(
                        org.mockito.ArgumentMatchers.contains("Failed to record speedrun"),
                        org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    @Test
    void shutdown_isNoopWhenConnectionNullOrAlreadyClosed() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);

        service.shutdown();

        java.sql.Connection closedConnection = mock(java.sql.Connection.class);
        when(closedConnection.isClosed()).thenReturn(true);
        setConnection(service, closedConnection);

        service.shutdown();

        verify(closedConnection, never()).close();
    }

    @Test
    void shutdown_logsWhenCloseFails() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        when(connection.isClosed()).thenReturn(false);
        org.mockito.Mockito.doThrow(new java.sql.SQLException("close boom"))
                .when(connection)
                .close();
        setConnection(service, connection);

        service.shutdown();

        verify(log)
                .error(
                        org.mockito.ArgumentMatchers.contains("close database connection"),
                        org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    @Test
    void initialize_recreatesLegacySchemaWhenPlayerUuidColumnExists() throws Exception {
        DeepCorePlugin plugin = mock(DeepCorePlugin.class);
        DeepCoreLogger log = mock(DeepCoreLogger.class);
        when(plugin.getDeepCoreLogger()).thenReturn(log);
        when(plugin.getDataFolder()).thenReturn(new File("."));

        RunRecordsService service = new RunRecordsService(plugin);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.Statement createStmt = mock(java.sql.Statement.class);
        java.sql.Statement pragmaStmt = mock(java.sql.Statement.class);
        java.sql.Statement dropStmt = mock(java.sql.Statement.class);
        java.sql.Statement recreateStmt = mock(java.sql.Statement.class);
        java.sql.ResultSet pragmaRs = mock(java.sql.ResultSet.class);

        when(connection.createStatement())
                .thenReturn(createStmt)
                .thenReturn(pragmaStmt)
                .thenReturn(dropStmt)
                .thenReturn(recreateStmt);
        when(pragmaStmt.executeQuery(anyString())).thenReturn(pragmaRs);
        when(pragmaRs.next()).thenReturn(true, false);
        when(pragmaRs.getString("name")).thenReturn("player_uuid");

        setConnection(service, connection);

        java.lang.reflect.Method method = RunRecordsService.class.getDeclaredMethod("createTablesIfNotExist");
        method.setAccessible(true);
        method.invoke(service);

        verify(dropStmt).executeUpdate("DROP TABLE IF EXISTS run_records");
        verify(log).warn(org.mockito.ArgumentMatchers.contains("Legacy run_records schema detected"));
        verify(log).debug(org.mockito.ArgumentMatchers.contains("run_records table recreated"));
        verify(createStmt)
                .executeUpdate(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS run_records"));
        verify(recreateStmt)
                .executeUpdate(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS run_records"));
        verify(log, never())
                .error(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Throwable.class));
    }

    private static void setConnection(RunRecordsService service, java.sql.Connection connection) throws Exception {
        Field field = RunRecordsService.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(service, connection);
    }
}

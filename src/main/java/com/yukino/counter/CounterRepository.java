package com.yukino.counter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CounterRepository implements AutoCloseable {
    private final Connection connection;

    public CounterRepository() throws SQLException, IOException {
        Path dir = Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "YukinoCounter");
        Files.createDirectories(dir);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("counts.db"));
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("CREATE TABLE IF NOT EXISTS key_counts (day TEXT NOT NULL, key_code INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(day, key_code))");
            s.execute("CREATE TABLE IF NOT EXISTS mouse_distance_v2 (day TEXT PRIMARY KEY, meters REAL NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            migrateLegacyMouseDistance(s);
            s.execute("CREATE TABLE IF NOT EXISTS imported_sources (hash TEXT PRIMARY KEY, file_name TEXT NOT NULL, imported_at TEXT NOT NULL, mouse_imported INTEGER NOT NULL DEFAULT 0)");
            try { s.execute("ALTER TABLE imported_sources ADD COLUMN mouse_imported INTEGER NOT NULL DEFAULT 0"); }
            catch (SQLException alreadyExists) { /* database created by a newer version */ }
        }
    }

    private void migrateLegacyMouseDistance(Statement s) throws SQLException {
        boolean legacyExists;
        try (ResultSet rs = s.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='mouse_distance'")) {
            legacyExists = rs.next();
        }
        boolean migrated;
        try (ResultSet rs = s.executeQuery("SELECT 1 FROM app_meta WHERE key='mouse_distance_v2_migrated'")) {
            migrated = rs.next();
        }
        if (legacyExists && !migrated) {
            s.execute("INSERT INTO mouse_distance_v2(day,meters) SELECT day,millimeters/1000.0 FROM mouse_distance WHERE 1 " +
                    "ON CONFLICT(day) DO UPDATE SET meters=meters+excluded.meters");
            s.execute("INSERT INTO app_meta(key,value) VALUES('mouse_distance_v2_migrated','1')");
        }
    }

    public synchronized CounterService.ImportStatus importBatch(String hash, String fileName,
                                                                 Map<CounterService.DayKey, Long> batch,
                                                                 Map<LocalDate, Double> mouseDistances) throws SQLException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            boolean existing = false;
            try (PreparedStatement check = connection.prepareStatement("SELECT mouse_imported FROM imported_sources WHERE hash=?")) {
                check.setString(1, hash);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        existing = true;
                        if (rs.getInt(1) != 0) {
                            connection.rollback();
                            return CounterService.ImportStatus.DUPLICATE;
                        }
                    }
                }
            }
            if (!existing) try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO key_counts(day,key_code,count) VALUES(?,?,?) " +
                            "ON CONFLICT(day,key_code) DO UPDATE SET count=count+excluded.count")) {
                for (var item : batch.entrySet()) {
                    ps.setString(1, item.getKey().day().toString());
                    ps.setInt(2, item.getKey().keyCode());
                    ps.setLong(3, item.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mouse_distance_v2(day,meters) VALUES(?,?) " +
                            "ON CONFLICT(day) DO UPDATE SET meters=meters+excluded.meters")) {
                for (var item : mouseDistances.entrySet()) {
                    ps.setString(1, item.getKey().toString());
                    ps.setDouble(2, item.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            if (existing) {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE imported_sources SET mouse_imported=1 WHERE hash=?")) {
                    ps.setString(1, hash); ps.executeUpdate();
                }
            } else try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO imported_sources(hash,file_name,imported_at,mouse_imported) VALUES(?,?,datetime('now'),1)")) {
                ps.setString(1, hash); ps.setString(2, fileName); ps.executeUpdate();
            }
            connection.commit();
            return existing ? CounterService.ImportStatus.MOUSE_ONLY : CounterService.ImportStatus.FULL;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public synchronized void addBatch(Map<CounterService.DayKey, Long> batch, Map<LocalDate, Double> mouseBatch) throws SQLException {
        if (batch.isEmpty() && mouseBatch.isEmpty()) return;
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO key_counts(day,key_code,count) VALUES(?,?,?) " +
                        "ON CONFLICT(day,key_code) DO UPDATE SET count=count+excluded.count")) {
            for (var item : batch.entrySet()) {
                ps.setString(1, item.getKey().day().toString());
                ps.setInt(2, item.getKey().keyCode());
                ps.setLong(3, item.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
            try (PreparedStatement mouse = connection.prepareStatement(
                    "INSERT INTO mouse_distance_v2(day,meters) VALUES(?,?) " +
                            "ON CONFLICT(day) DO UPDATE SET meters=meters+excluded.meters")) {
                for (var item : mouseBatch.entrySet()) {
                    mouse.setString(1, item.getKey().toString());
                    mouse.setDouble(2, item.getValue());
                    mouse.addBatch();
                }
                mouse.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public synchronized Map<Integer, Long> counts(LocalDate day) throws SQLException {
        Map<Integer, Long> result = new HashMap<>();
        String sql = day == null
                ? "SELECT key_code,SUM(count) FROM key_counts GROUP BY key_code"
                : "SELECT key_code,count FROM key_counts WHERE day=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (day != null) ps.setString(1, day.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getInt(1), rs.getLong(2));
            }
        }
        return result;
    }

    public synchronized Map<LocalDate, Long> days() throws SQLException {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT day,SUM(count) FROM key_counts GROUP BY day ORDER BY day DESC")) {
            while (rs.next()) result.put(LocalDate.parse(rs.getString(1)), rs.getLong(2));
        }
        return result;
    }

    public synchronized double mouseDistance(LocalDate day) throws SQLException {
        String sql = day == null ? "SELECT COALESCE(SUM(meters),0) FROM mouse_distance_v2"
                : "SELECT COALESCE(meters,0) FROM mouse_distance_v2 WHERE day=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (day != null) ps.setString(1, day.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0d; }
        }
    }

    @Override public synchronized void close() throws SQLException { connection.close(); }
}

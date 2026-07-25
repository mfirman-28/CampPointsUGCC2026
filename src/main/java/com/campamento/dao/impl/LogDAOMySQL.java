package com.campamento.dao.impl;

import com.campamento.dao.LogDAO;
import com.campamento.dto.LogDetailDTO;
import com.campamento.model.Log;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LogDAOMySQL implements LogDAO {

    @Override
    public void insertLogBatch(Connection conn, Long monitorId, List<Long> kidIds, int numPoints) throws SQLException {
        String sql = "INSERT INTO logs (kid_id, monitor_id, num_points) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Long kidId : kidIds) {
                stmt.setLong(1, kidId);
                if (monitorId == null || monitorId == 0L) {
                    stmt.setNull(2, java.sql.Types.BIGINT);
                } else {
                    stmt.setLong(2, monitorId);
                }
                stmt.setInt(3, numPoints);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    @Override
    public Log insertLogSingle(Connection conn, Long monitorId, Long kidId, int numPoints) throws SQLException {
        String sql = "INSERT INTO logs (kid_id, monitor_id, num_points) VALUES (?, ?, ?)";
        Log log = new Log();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, kidId);
            if (monitorId == null || monitorId == 0L) {
                stmt.setNull(2, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(2, monitorId);
            }
            stmt.setInt(3, numPoints);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getLong(1));
                    log.setKidId(kidId);
                    log.setMonitorId(monitorId);
                    log.setNumPoints(numPoints);
                    log.setTime(LocalDateTime.now());
                }
            }
        }
        return log;
    }

    @Override
    public int getDailyPointsByMonitor(Connection conn, Long monitorId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(num_points), 0) FROM logs " +
                     "WHERE monitor_id = ? AND num_points > 0 AND DATE(time) = CURDATE() AND is_annulled = 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, monitorId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public List<LogDetailDTO> findLogsByDateRange(Connection conn, LocalDateTime start, LocalDateTime end) throws SQLException {
        List<LogDetailDTO> list = new ArrayList<>();
        String sql = "SELECT l.id AS log_id, l.time, m.name AS monitor_name, l.num_points, k.name AS kid_name, g.name AS group_name " +
                     "FROM logs l " +
                     "LEFT JOIN monitors m ON l.monitor_id = m.id " +
                     "JOIN kids k ON l.kid_id = k.id " +
                     "JOIN camp_groups g ON k.group_id = g.id " +
                     "WHERE l.time BETWEEN ? AND ? " +
                     "ORDER BY l.time DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(start));
            stmt.setTimestamp(2, Timestamp.valueOf(end));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLogDetail(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<LogDetailDTO> findRecentLogsByMonitor(Connection conn, Long monitorId, int limit) throws SQLException {
        List<LogDetailDTO> list = new ArrayList<>();
        String sql = "SELECT l.id AS log_id, l.time, m.name AS monitor_name, l.num_points, k.name AS kid_name, g.name AS group_name " +
                     "FROM logs l " +
                     "LEFT JOIN monitors m ON l.monitor_id = m.id " +
                     "JOIN kids k ON l.kid_id = k.id " +
                     "JOIN camp_groups g ON k.group_id = g.id " +
                     "WHERE (l.monitor_id = ? OR (? = 0 AND l.monitor_id IS NULL)) AND l.is_annulled = 0 " +
                     "ORDER BY l.time DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, monitorId);
            stmt.setLong(2, monitorId);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLogDetail(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<LogDetailDTO> findRecentLogsByMonitorForAudit(Connection conn, Long monitorId, int limit) throws SQLException {
        List<LogDetailDTO> list = new ArrayList<>();
        String sql = "SELECT l.id AS log_id, l.time, m.name AS monitor_name, l.num_points, k.name AS kid_name, g.name AS group_name " +
                     "FROM logs l " +
                     "LEFT JOIN monitors m ON l.monitor_id = m.id " +
                     "JOIN kids k ON l.kid_id = k.id " +
                     "JOIN camp_groups g ON k.group_id = g.id " +
                     "WHERE (l.monitor_id = ? OR (? = 0 AND l.monitor_id IS NULL)) " +
                     "ORDER BY l.time DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, monitorId);
            stmt.setLong(2, monitorId);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLogDetail(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<LogDetailDTO> findRecentGlobalLogs(Connection conn, int limit) throws SQLException {
        List<LogDetailDTO> list = new ArrayList<>();
        String sql = "SELECT l.id AS log_id, l.time, m.name AS monitor_name, l.num_points, k.name AS kid_name, g.name AS group_name " +
                     "FROM logs l " +
                     "LEFT JOIN monitors m ON l.monitor_id = m.id " +
                     "JOIN kids k ON l.kid_id = k.id " +
                     "JOIN camp_groups g ON k.group_id = g.id " +
                     "WHERE l.is_annulled = 0 " +
                     "ORDER BY l.time DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToLogDetail(rs));
                }
            }
        }
        return list;
    }

    @Override
    public Log findById(Connection conn, Long logId) throws SQLException {
        String sql = "SELECT id, kid_id, monitor_id, num_points, time, is_annulled FROM logs WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, logId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("time");
                    return new Log(
                            rs.getLong("id"),
                            rs.getLong("kid_id"),
                            rs.getLong("monitor_id"),
                            rs.getInt("num_points"),
                            ts != null ? ts.toLocalDateTime() : null,
                            rs.getBoolean("is_annulled")
                    );
                }
            }
        }
        return null;
    }

    @Override
    public void deleteLog(Connection conn, Long logId) throws SQLException {
        String sql = "DELETE FROM logs WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, logId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void markAsAnnulled(Connection conn, Long logId) throws SQLException {
        String sql = "UPDATE logs SET is_annulled = 1 WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, logId);
            stmt.executeUpdate();
        }
    }

    private LogDetailDTO mapRowToLogDetail(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("time");
        String monitorName = rs.getString("monitor_name");
        return new LogDetailDTO(
                rs.getLong("log_id"),
                ts != null ? ts.toLocalDateTime() : null,
                monitorName != null ? monitorName : "Súper Admin",
                rs.getInt("num_points"),
                rs.getString("kid_name"),
                rs.getString("group_name")
        );
    }
}

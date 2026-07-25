package com.campamento.dao.impl;

import com.campamento.dao.MonitorDAO;
import com.campamento.model.Monitor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MonitorDAOMySQL implements MonitorDAO {

    @Override
    public Monitor findByTelegramId(Connection conn, Long telegramId) throws SQLException {
        if (telegramId == null) return null;
        String sql = "SELECT id, name, telegram_id, group_id, is_admin, is_solo_monitor FROM monitors WHERE telegram_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, telegramId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToMonitor(rs);
                }
            }
        }
        return null;
    }

    @Override
    public Monitor findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT id, name, telegram_id, group_id, is_admin, is_solo_monitor FROM monitors WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToMonitor(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Monitor> findUnassigned(Connection conn) throws SQLException {
        List<Monitor> unassigned = new ArrayList<>();
        String sql = "SELECT id, name, telegram_id, group_id, is_admin, is_solo_monitor FROM monitors WHERE telegram_id IS NULL ORDER BY name ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                unassigned.add(mapRowToMonitor(rs));
            }
        }
        return unassigned;
    }

    @Override
    public List<Monitor> findAll(Connection conn) throws SQLException {
        List<Monitor> monitors = new ArrayList<>();
        String sql = "SELECT id, name, telegram_id, group_id, is_admin, is_solo_monitor FROM monitors ORDER BY name ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                monitors.add(mapRowToMonitor(rs));
            }
        }
        return monitors;
    }

    @Override
    public void assignTelegramId(Connection conn, Long monitorId, Long telegramId) throws SQLException {
        String sql = "UPDATE monitors SET telegram_id = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, telegramId);
            stmt.setLong(2, monitorId);
            stmt.executeUpdate();
        }
    }

    @Override
    public Monitor save(Connection conn, Monitor monitor) throws SQLException {
        if (monitor.getId() == null) {
            String sql = "INSERT INTO monitors (name, telegram_id, group_id, is_admin, is_solo_monitor) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, monitor.getName());
                if (monitor.getTelegramId() != null) {
                    stmt.setLong(2, monitor.getTelegramId());
                } else {
                    stmt.setNull(2, Types.BIGINT);
                }
                if (monitor.getGroupId() != null) {
                    stmt.setLong(3, monitor.getGroupId());
                } else {
                    stmt.setNull(3, Types.BIGINT);
                }
                stmt.setBoolean(4, monitor.isAdmin());
                stmt.setBoolean(5, monitor.isSoloMonitor());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        monitor.setId(rs.getLong(1));
                    }
                }
            }
        } else {
            String sql = "UPDATE monitors SET name = ?, telegram_id = ?, group_id = ?, is_admin = ?, is_solo_monitor = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, monitor.getName());
                if (monitor.getTelegramId() != null) {
                    stmt.setLong(2, monitor.getTelegramId());
                } else {
                    stmt.setNull(2, Types.BIGINT);
                }
                if (monitor.getGroupId() != null) {
                    stmt.setLong(3, monitor.getGroupId());
                } else {
                    stmt.setNull(3, Types.BIGINT);
                }
                stmt.setBoolean(4, monitor.isAdmin());
                stmt.setBoolean(5, monitor.isSoloMonitor());
                stmt.setLong(6, monitor.getId());
                stmt.executeUpdate();
            }
        }
        return monitor;
    }

    @Override
    public void update(Connection conn, Monitor monitor) throws SQLException {
        String sql = "UPDATE monitors SET name = ?, group_id = ?, is_admin = ?, is_solo_monitor = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, monitor.getName());
            if (monitor.getGroupId() == null) {
                stmt.setNull(2, Types.BIGINT);
            } else {
                stmt.setLong(2, monitor.getGroupId());
            }
            stmt.setBoolean(3, monitor.isAdmin());
            stmt.setBoolean(4, monitor.isSoloMonitor());
            stmt.setLong(5, monitor.getId());
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(Connection conn, Long id) throws SQLException {
        String sql = "DELETE FROM monitors WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Monitor mapRowToMonitor(ResultSet rs) throws SQLException {
        Long telegramId = rs.getObject("telegram_id") != null ? rs.getLong("telegram_id") : null;
        return new Monitor(
                rs.getLong("id"),
                rs.getString("name"),
                telegramId,
                rs.getLong("group_id"),
                rs.getBoolean("is_admin"),
                rs.getBoolean("is_solo_monitor")
        );
    }
}

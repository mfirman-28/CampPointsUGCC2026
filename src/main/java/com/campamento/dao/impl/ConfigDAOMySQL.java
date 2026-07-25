package com.campamento.dao.impl;

import com.campamento.dao.ConfigDAO;
import com.campamento.model.Config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConfigDAOMySQL implements ConfigDAO {

    @Override
    public Config getConfig(Connection conn) throws SQLException {
        String sql = "SELECT id, daily_limit, global_points_enable FROM config WHERE id = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new Config(
                        rs.getInt("id"),
                        rs.getInt("daily_limit"),
                        rs.getBoolean("global_points_enable")
                );
            }
        }
        // Fallback default config if row doesn't exist yet
        return new Config(1, 35, true);
    }

    @Override
    public void updateConfig(Connection conn, Config config) throws SQLException {
        String sql = "INSERT INTO config (id, daily_limit, global_points_enable) VALUES (1, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE daily_limit = VALUES(daily_limit), global_points_enable = VALUES(global_points_enable)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, config.getDailyLimit());
            stmt.setBoolean(2, config.isGlobalPointsEnable());
            stmt.executeUpdate();
        }
    }
}

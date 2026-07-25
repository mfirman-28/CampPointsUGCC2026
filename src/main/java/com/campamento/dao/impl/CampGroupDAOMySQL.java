package com.campamento.dao.impl;

import com.campamento.dao.CampGroupDAO;
import com.campamento.model.CampGroup;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CampGroupDAOMySQL implements CampGroupDAO {

    @Override
    public CampGroup findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT id, name FROM camp_groups WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CampGroup(rs.getLong("id"), rs.getString("name"));
                }
            }
        }
        return null;
    }

    @Override
    public CampGroup findByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT * FROM camp_groups WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CampGroup(rs.getLong("id"), rs.getString("name"));
                }
            }
        }
        return null;
    }

    @Override
    public List<CampGroup> findAll(Connection conn) throws SQLException {
        List<CampGroup> groups = new ArrayList<>();
        String sql = "SELECT id, name FROM camp_groups ORDER BY id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                groups.add(new CampGroup(rs.getLong("id"), rs.getString("name")));
            }
        }
        return groups;
    }

    @Override
    public CampGroup save(Connection conn, CampGroup group) throws SQLException {
        if (group.getId() == null) {
            String sql = "INSERT INTO camp_groups (name) VALUES (?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, group.getName());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        group.setId(rs.getLong(1));
                    }
                }
            }
        } else {
            String sql = "UPDATE camp_groups SET name = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, group.getName());
                stmt.setLong(2, group.getId());
                stmt.executeUpdate();
            }
        }
        return group;
    }

    @Override
    public void delete(Connection conn, Long id) throws SQLException {
        String sql = "DELETE FROM camp_groups WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }
}

package com.campamento.dao.impl;

import com.campamento.dao.KidDAO;
import com.campamento.dto.GroupRankingDTO;
import com.campamento.dto.KidRankingDTO;
import com.campamento.model.Kid;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class KidDAOMySQL implements KidDAO {

    @Override
    public Kid findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT id, name, points, group_id FROM kids WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToKid(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Kid> findByGroupId(Connection conn, Long groupId) throws SQLException {
        List<Kid> kids = new ArrayList<>();
        String sql = "SELECT id, name, points, group_id FROM kids WHERE group_id = ? ORDER BY name ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    kids.add(mapRowToKid(rs));
                }
            }
        }
        return kids;
    }


    @Override
    public void updatePointsBatch(Connection conn, List<Long> kidIds, int pointsDelta) throws SQLException {
        String sql = "UPDATE kids SET points = GREATEST(0, points + ?) WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Long kidId : kidIds) {
                stmt.setInt(1, pointsDelta);
                stmt.setLong(2, kidId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    @Override
    public void updatePointsSingle(Connection conn, Long kidId, int pointsDelta) throws SQLException {
        String sql = "UPDATE kids SET points = GREATEST(0, points + ?) WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pointsDelta);
            stmt.setLong(2, kidId);
            stmt.executeUpdate();
        }
    }

    @Override
    public List<KidRankingDTO> findTopGlobal(Connection conn, int limit) throws SQLException {
        List<KidRankingDTO> rankings = new ArrayList<>();
        String sql = "SELECT k.name AS kid_name, g.name AS group_name, k.points " +
                     "FROM kids k JOIN camp_groups g ON k.group_id = g.id " +
                     "ORDER BY k.points DESC, k.name ASC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rankings.add(new KidRankingDTO(
                            rs.getString("kid_name"),
                            rs.getString("group_name"),
                            rs.getInt("points")
                    ));
                }
            }
        }
        return rankings;
    }

    @Override
    public List<KidRankingDTO> findTopByGroup(Connection conn, Long groupId, int limit) throws SQLException {
        List<KidRankingDTO> rankings = new ArrayList<>();
        String sql = "SELECT k.name AS kid_name, g.name AS group_name, k.points " +
                     "FROM kids k JOIN camp_groups g ON k.group_id = g.id " +
                     "WHERE k.group_id = ? " +
                     "ORDER BY k.points DESC, k.name ASC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rankings.add(new KidRankingDTO(
                            rs.getString("kid_name"),
                            rs.getString("group_name"),
                            rs.getInt("points")
                    ));
                }
            }
        }
        return rankings;
    }

    @Override
    public List<GroupRankingDTO> findGroupRankings(Connection conn) throws SQLException {
        List<GroupRankingDTO> rankings = new ArrayList<>();
        String sql = "SELECT g.name AS group_name, COALESCE(AVG(k.points), 0.0) AS avg_points " +
                     "FROM camp_groups g LEFT JOIN kids k ON g.id = k.group_id " +
                     "GROUP BY g.id, g.name ORDER BY avg_points DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rankings.add(new GroupRankingDTO(
                        rs.getString("group_name"),
                        rs.getDouble("avg_points")
                ));
            }
        }
        return rankings;
    }

    @Override
    public Kid save(Connection conn, Kid kid) throws SQLException {
        if (kid.getId() == null) {
            String sql = "INSERT INTO kids (name, points, group_id) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, kid.getName());
                stmt.setInt(2, Math.max(0, kid.getPoints()));
                stmt.setLong(3, kid.getGroupId());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        kid.setId(rs.getLong(1));
                    }
                }
            }
        } else {
            String sql = "UPDATE kids SET name = ?, points = GREATEST(0, ?), group_id = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, kid.getName());
                stmt.setInt(2, kid.getPoints());
                stmt.setLong(3, kid.getGroupId());
                stmt.setLong(4, kid.getId());
                stmt.executeUpdate();
            }
        }
        return kid;
    }

    @Override
    public void update(Connection conn, Kid kid) throws SQLException {
        this.save(conn, kid);
    }

    @Override
    public void delete(Connection conn, Long id) throws SQLException {
        String sql = "DELETE FROM kids WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Kid mapRowToKid(ResultSet rs) throws SQLException {
        return new Kid(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("points"),
                rs.getLong("group_id")
        );
    }
}

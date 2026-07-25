package com.campamento.dao;

import com.campamento.dto.GroupRankingDTO;
import com.campamento.dto.KidRankingDTO;
import com.campamento.model.Kid;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface KidDAO {
    Kid findById(Connection conn, Long id) throws SQLException;
    List<Kid> findByGroupId(Connection conn, Long groupId) throws SQLException;

    void updatePointsBatch(Connection conn, List<Long> kidIds, int pointsDelta) throws SQLException;
    void updatePointsSingle(Connection conn, Long kidId, int pointsDelta) throws SQLException;
    List<KidRankingDTO> findTopGlobal(Connection conn, int limit) throws SQLException;
    List<KidRankingDTO> findTopByGroup(Connection conn, Long groupId, int limit) throws SQLException;
    List<GroupRankingDTO> findGroupRankings(Connection conn) throws SQLException;
    Kid save(Connection conn, Kid kid) throws SQLException;
    void update(Connection conn, Kid kid) throws SQLException;
    void delete(Connection conn, Long id) throws SQLException;
}

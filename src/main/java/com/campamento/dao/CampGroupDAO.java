package com.campamento.dao;

import com.campamento.model.CampGroup;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface CampGroupDAO {
    CampGroup findById(Connection conn, Long id) throws SQLException;
    CampGroup findByName(Connection conn, String name) throws SQLException;
    List<CampGroup> findAll(Connection conn) throws SQLException;
    CampGroup save(Connection conn, CampGroup group) throws SQLException;
    void delete(Connection conn, Long id) throws SQLException;
}

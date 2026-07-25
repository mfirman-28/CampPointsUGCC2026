package com.campamento.dao;

import com.campamento.model.Monitor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface MonitorDAO {
    Monitor findByTelegramId(Connection conn, Long telegramId) throws SQLException;
    Monitor findById(Connection conn, Long id) throws SQLException;
    List<Monitor> findUnassigned(Connection conn) throws SQLException;
    List<Monitor> findAll(Connection conn) throws SQLException;
    void assignTelegramId(Connection conn, Long monitorId, Long telegramId) throws SQLException;
    Monitor save(Connection conn, Monitor monitor) throws SQLException;
    void update(Connection conn, Monitor monitor) throws SQLException;
    void delete(Connection conn, Long id) throws SQLException;
}

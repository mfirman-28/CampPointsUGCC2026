package com.campamento.dao;

import com.campamento.dto.LogDetailDTO;
import com.campamento.model.Log;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface LogDAO {
    void insertLogBatch(Connection conn, Long monitorId, List<Long> kidIds, int numPoints) throws SQLException;
    Log insertLogSingle(Connection conn, Long monitorId, Long kidId, int numPoints) throws SQLException;
    int getDailyPointsByMonitor(Connection conn, Long monitorId) throws SQLException;
    List<LogDetailDTO> findLogsByDateRange(Connection conn, LocalDateTime start, LocalDateTime end) throws SQLException;
    List<LogDetailDTO> findRecentLogsByMonitor(Connection conn, Long monitorId, int limit) throws SQLException;
    List<LogDetailDTO> findRecentLogsByMonitorForAudit(Connection conn, Long monitorId, int limit) throws SQLException;
    List<LogDetailDTO> findRecentGlobalLogs(Connection conn, int limit) throws SQLException;
    Log findById(Connection conn, Long logId) throws SQLException;
    void markAsAnnulled(Connection conn, Long logId) throws SQLException;
    void deleteLog(Connection conn, Long logId) throws SQLException;
}

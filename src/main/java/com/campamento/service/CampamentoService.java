package com.campamento.service;

import com.campamento.config.DatabaseConfig;
import com.campamento.dao.*;
import com.campamento.dto.*;
import com.campamento.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CampamentoService {

    private final CampGroupDAO groupDAO;
    private final KidDAO kidDAO;
    private final MonitorDAO monitorDAO;
    private final LogDAO logDAO;
    private final ConfigDAO configDAO;


    public CampamentoService(CampGroupDAO groupDAO, KidDAO kidDAO, MonitorDAO monitorDAO, LogDAO logDAO, ConfigDAO configDAO) {
        this.groupDAO = groupDAO;
        this.kidDAO = kidDAO;
        this.monitorDAO = monitorDAO;
        this.logDAO = logDAO;
        this.configDAO = configDAO;
    }



    // --- Super Admin & Authorization ---
    public boolean isRealAdmin(Long telegramId) throws SQLException {
        if (isSuperAdmin(telegramId)) return true;
        Monitor m = getMonitorByTelegramId(telegramId);
        return m != null && m.isAdmin();
    }
    public boolean isSuperAdmin(Long telegramId) {
        if (telegramId == null) return false;
        String envSuperAdmin = System.getenv("SUPER_ADMIN_TELEGRAM_ID");
        if (envSuperAdmin != null && !envSuperAdmin.isBlank()) {
            try {
                long superAdminId = Long.parseLong(envSuperAdmin.trim());
                return superAdminId == telegramId;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public Monitor getMonitorByTelegramId(Long telegramId) throws SQLException {
        if (telegramId == null) return null;
        try (Connection conn = DatabaseConfig.getConnection()) {
            Monitor monitor = monitorDAO.findByTelegramId(conn, telegramId);
            if (monitor == null && isSuperAdmin(telegramId)) {
                // Auto-generar / retornar perfil de Super Admin virtual
                monitor = new Monitor(0L, "Super Admin", telegramId, 0L, true, true);
            }
            return monitor;
        }
    }

    // --- Gestión de Vinculación de Monitores ---
    public List<Monitor> getUnassignedMonitors() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return monitorDAO.findUnassigned(conn);
        }
    }

    public List<Monitor> getAllMonitors() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return monitorDAO.findAll(conn);
        }
    }



    public boolean assignTelegramIdToMonitor(Long monitorId, Long targetTelegramId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Monitor m = monitorDAO.findById(conn, monitorId);
                if (m == null || m.getTelegramId() != null) {
                    conn.rollback();
                    return false;
                }
                monitorDAO.assignTelegramId(conn, monitorId, targetTelegramId);
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // --- Asignación de Puntos en Lote (Batching & ACID) ---
    public void assignPointsBatch(Long monitorTelegramId, List<Long> kidIds, int pointsDelta) throws Exception {
        if (pointsDelta < 1 || pointsDelta > 3) {
            throw new IllegalArgumentException("Los monitores solo pueden dar 1, 2 o 3 puntos por acción.");
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Monitor monitor = getMonitorByTelegramId(monitorTelegramId);
                if (monitor == null) {
                    throw new IllegalStateException("Usuario no autorizado.");
                }

                Config config = configDAO.getConfig(conn);

                // Comprobación de restricción por grupos
                if (!config.isGlobalPointsEnable() && !isRealAdmin(monitorTelegramId) && monitor.getGroupId() != null) {
                    for (Long kidId : kidIds) {
                        Kid kid = kidDAO.findById(conn, kidId);
                        if (kid == null || !Objects.equals(kid.getGroupId(), monitor.getGroupId())) {
                            throw new IllegalStateException("El modo estricto está activo. Solo puedes dar puntos a niños de tu grupo.");
                        }
                    }
                }

                // Comprobación de Límite Diario
                int dailyLimitBase = config.getDailyLimit();
                int effectiveLimit = monitor.isSoloMonitor() ? dailyLimitBase * 2 : dailyLimitBase;
                int usedToday = logDAO.getDailyPointsByMonitor(conn, monitor.getId());
                int pointsToGiveTotal = kidIds.size() * pointsDelta;

                if (!isRealAdmin(monitorTelegramId) && (usedToday + pointsToGiveTotal > effectiveLimit)) {
                    int remaining = Math.max(0, effectiveLimit - usedToday);
                    throw new IllegalStateException(String.format(
                            "Has superado tu límite diario. Límite: %d pts | Usado hoy: %d pts | Disponible: %d pts.",
                            effectiveLimit, usedToday, remaining
                    ));
                }

                // Ejecución en lote de puntos y logs
                kidDAO.updatePointsBatch(conn, kidIds, pointsDelta);
                logDAO.insertLogBatch(conn, monitor.getId(), kidIds, pointsDelta);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // --- Puntos consumidos hoy ---
    public int getDailyPointsUsed(Long telegramId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            Monitor monitor = getMonitorByTelegramId(telegramId);
            if (monitor == null || monitor.getId() == 0L) return 0;
            return logDAO.getDailyPointsByMonitor(conn, monitor.getId());
        } catch (SQLException e) {
            return 0;
        }
    }

    // --- Anulación de Puntos por Monitor ---
    public Log annulRecentLog(Long monitorTelegramId, Long logId) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Monitor monitor = getMonitorByTelegramId(monitorTelegramId);
                if (monitor == null) return null;

                Log log = logDAO.findById(conn, logId);
                if (log == null || !Objects.equals(log.getMonitorId(), monitor.getId())) {
                    conn.rollback();
                    return null;
                }

                // Revertir los puntos en el niño (asegurando puntos >= 0)
                kidDAO.updatePointsSingle(conn, log.getKidId(), -log.getNumPoints());
                logDAO.markAsAnnulled(conn, logId);
                Log contraLog = logDAO.insertLogSingle(conn, log.getMonitorId(), log.getKidId(), -log.getNumPoints());
                logDAO.markAsAnnulled(conn, contraLog.getId());

                conn.commit();
                return log;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // --- Consultas de Datos y Rankings ---
    public List<CampGroup> getAllGroups() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return groupDAO.findAll(conn);
        }
    }

    public List<Kid> getKidsByGroup(Long groupId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return kidDAO.findByGroupId(conn, groupId);
        }
    }


    public List<KidRankingDTO> getTopKidsGlobal(int limit) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return kidDAO.findTopGlobal(conn, limit);
        }
    }

    public List<KidRankingDTO> getTopKidsByGroup(Long groupId, int limit) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return kidDAO.findTopByGroup(conn, groupId, limit);
        }
    }

    public List<GroupRankingDTO> getGroupRankings() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return kidDAO.findGroupRankings(conn);
        }
    }

    public List<LogDetailDTO> getLogsForAudit(LocalDateTime start, LocalDateTime end) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return logDAO.findLogsByDateRange(conn, start, end);
        }
    }

    public List<LogDetailDTO> getRecentLogsByMonitor(Long monitorTelegramId, int limit) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            Monitor monitor = getMonitorByTelegramId(monitorTelegramId);
            if (monitor == null) return Collections.emptyList();
            return logDAO.findRecentLogsByMonitor(conn, monitor.getId(), limit);
        }
    }

    public List<LogDetailDTO> getRecentLogsByMonitorIdForAdmin(Long adminTelegramId, Long targetMonitorId, int limit) throws Exception {
        if (!isRealAdmin(adminTelegramId)) {
            throw new IllegalStateException("Solo los Administradores pueden ver logs de otros monitores.");
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            return logDAO.findRecentLogsByMonitorForAudit(conn, targetMonitorId, limit);
        }
    }

    public List<LogDetailDTO> getRecentGlobalLogs(int limit) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return logDAO.findRecentGlobalLogs(conn, limit);
        }
    }

    // --- Ajustes Globales de Configuración ---
    public Config getConfig() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return configDAO.getConfig(conn);
        }
    }

    public void updateConfig(Long adminTelegramId, int dailyLimit, boolean globalPointsEnable) throws Exception {
        if (!isRealAdmin(adminTelegramId)) {
            throw new IllegalStateException("Solo los Administradores pueden cambiar la configuración.");
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Config cfg = new Config(1, dailyLimit, globalPointsEnable);
                configDAO.updateConfig(conn, cfg);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
    // --- Altas de Entidades (Admin) ---
    public Monitor createMonitorProfile(Long adminTelegramId, String name, Long groupId, boolean isAdmin, boolean isSoloMonitor) throws Exception {
        if (!isRealAdmin(adminTelegramId)) {
            throw new IllegalStateException("Solo los Administradores pueden registrar monitores.");
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Monitor m = new Monitor(null, name, null, groupId, isAdmin, isSoloMonitor);
                m = monitorDAO.save(conn, m);
                conn.commit();
                return m;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
    // --- Configuración rápida desde Telegram ---
    public CampGroup addGroup(Long adminTelegramId, String groupName) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden añadir grupos.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            CampGroup group = groupDAO.findByName(conn, groupName);
            if (group == null) {
                group = groupDAO.save(conn, new CampGroup(null, groupName));
            }
            return group;
        }
    }

    public boolean addKidToExistingGroup(Long adminTelegramId, String kidName, Long groupId) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden añadir niños.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                kidDAO.save(conn, new Kid(null, kidName, 0, groupId));
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // --- CRUD Admin ---
    public void updateMonitor(Long adminTelegramId, Monitor monitor) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden editar monitores.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                monitorDAO.update(conn, monitor);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void deleteMonitor(Long adminTelegramId, Long monitorId) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden eliminar monitores.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                monitorDAO.delete(conn, monitorId);
                conn.commit();
            } catch (java.sql.SQLIntegrityConstraintViolationException e) {
                conn.rollback();
                throw new IllegalStateException("Не можна видалити монітора, який вже роздавав бали. Замість цього просто заберіть у нього права та відв'яжіть від Telegram.");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void updateKid(Long adminTelegramId, Kid kid) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden editar niños.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                kidDAO.update(conn, kid);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void deleteKid(Long adminTelegramId, Long kidId) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden eliminar niños.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                kidDAO.delete(conn, kidId);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void assignManualPoints(Long adminTelegramId, Long kidId, int pointsDelta) throws Exception {
        if (!isRealAdmin(adminTelegramId)) throw new IllegalStateException("Solo los Administradores pueden dar puntos manuales sin límite.");
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Monitor admin = getMonitorByTelegramId(adminTelegramId);
                Long monitorId = (admin != null) ? admin.getId() : 0L;
                
                kidDAO.updatePointsSingle(conn, kidId, pointsDelta);
                logDAO.insertLogSingle(conn, monitorId, kidId, pointsDelta);
                
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Kid getKidById(Long kidId) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return kidDAO.findById(conn, kidId);
        }
    }

    public Monitor getMonitorById(Long monitorId) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return monitorDAO.findById(conn, monitorId);
        }
    }
}

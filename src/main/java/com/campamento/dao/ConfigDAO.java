package com.campamento.dao;

import com.campamento.model.Config;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConfigDAO {
    Config getConfig(Connection conn) throws SQLException;
    void updateConfig(Connection conn, Config config) throws SQLException;
}

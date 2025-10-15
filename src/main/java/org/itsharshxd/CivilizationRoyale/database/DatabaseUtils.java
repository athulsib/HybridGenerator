package org.itsharshxd.CivilizationRoyale.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.itsharshxd.CivilizationRoyale.HybridMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseUtils {

    private static HikariDataSource dataSource;

    public static void connect(String host, int port, String database, String username, String password) {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false");
        config.setUsername(username);
        config.setPassword(password);

        dataSource = new HikariDataSource(config);

        Bukkit.getLogger().info("Successfully connected to the database: " + config.getJdbcUrl());
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection to database is not established");
        }
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }

    public static void setupDatabase() {
        try (Connection conn = getConnection()) {
            String dbname = HybridMode.getPlugin().getConfig().getString("database.table_prefix") + "world_requests";
            String createTableSQL = "CREATE TABLE IF NOT EXISTS " + dbname + " (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "world_name VARCHAR(255), " + // world_name column added
                    "compressed_world BLOB, " +
                    "status ENUM('PENDING', 'PROCESSED') NOT NULL DEFAULT 'PENDING', " +
                    "request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "response_time TIMESTAMP NULL" +
                    ") ENGINE=InnoDB;";
            try (PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
                stmt.execute();
                Bukkit.getLogger().info("Database setup completed successfully.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to setup database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
package com.mystenchants.database;

import com.mystenchants.MystEnchants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages database connections and operations
 */
public class DatabaseManager {

    private final MystEnchants plugin;
    private HikariDataSource dataSource;
    private boolean isMySQL;

    public DatabaseManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database connection
     */
    public void initialize() throws SQLException {
        String databaseType = plugin.getConfigManager().getString("config.yml", "database.type", "SQLITE");
        isMySQL = databaseType.equalsIgnoreCase("MYSQL");

        HikariConfig config = new HikariConfig();

        if (isMySQL) {
            setupMySQL(config);
        } else {
            setupSQLite(config);
        }

        dataSource = new HikariDataSource(config);
        createTables();

        plugin.getLogger().info("Database initialized successfully using " + databaseType);
    }

    /**
     * Sets up MySQL connection
     */
    private void setupMySQL(HikariConfig config) {
        String host = plugin.getConfigManager().getString("config.yml", "database.mysql.host", "localhost");
        int port = plugin.getConfigManager().getInt("config.yml", "database.mysql.port", 3306);
        String database = plugin.getConfigManager().getString("config.yml", "database.mysql.database", "mystenchants");
        String username = plugin.getConfigManager().getString("config.yml", "database.mysql.username", "root");
        String password = plugin.getConfigManager().getString("config.yml", "database.mysql.password", "");
        boolean useSSL = plugin.getConfigManager().getBoolean("config.yml", "database.mysql.useSSL", false);

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool settings
        config.setMaximumPoolSize(plugin.getConfigManager().getInt("config.yml", "database.connection-pool.maximum-pool-size", 10));
        config.setMinimumIdle(plugin.getConfigManager().getInt("config.yml", "database.connection-pool.minimum-idle", 2));
        config.setConnectionTimeout(plugin.getConfigManager().getInt("config.yml", "database.connection-pool.connection-timeout", 30000));
        config.setIdleTimeout(plugin.getConfigManager().getInt("config.yml", "database.connection-pool.idle-timeout", 600000));
        config.setMaxLifetime(plugin.getConfigManager().getInt("config.yml", "database.connection-pool.max-lifetime", 1800000));
    }

    /**
     * Sets up SQLite connection
     */
    private void setupSQLite(HikariConfig config) {
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/database.db");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite doesn't support multiple connections well
    }

    /**
     * Creates all necessary tables
     */
    private void createTables() {
        try (Connection connection = getConnection()) {
            // Player data table
            String playerDataTable = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "souls BIGINT DEFAULT 0, " +
                    "last_seen BIGINT NOT NULL, " +
                    "redemption_cooldown BIGINT DEFAULT 0" +
                    ")";

            // Player enchants table
            String playerEnchantsTable = "CREATE TABLE IF NOT EXISTS player_enchants (" +
                    "id " + (isMySQL ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "enchant_name VARCHAR(50) NOT NULL, " +
                    "level INT NOT NULL, " +
                    "unlocked_at BIGINT NOT NULL, " +
                    "UNIQUE(uuid, enchant_name)" +
                    ")";

            // Player statistics table
            String playerStatsTable = "CREATE TABLE IF NOT EXISTS player_statistics (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "blocks_mined BIGINT DEFAULT 0, " +
                    "blocks_walked BIGINT DEFAULT 0, " +
                    "wheat_broken BIGINT DEFAULT 0, " +
                    "creepers_killed BIGINT DEFAULT 0, " +
                    "iron_ingots_traded BIGINT DEFAULT 0, " +
                    "pants_crafted BIGINT DEFAULT 0, " +
                    "souls_collected BIGINT DEFAULT 0, " +
                    "enchants_unlocked INT DEFAULT 0" +
                    ")";

            // Player perks table
            String playerPerksTable = "CREATE TABLE IF NOT EXISTS player_perks (" +
                    "id " + (isMySQL ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "perk_name VARCHAR(50) NOT NULL, " +
                    "amount INT DEFAULT 0, " +
                    "last_used BIGINT DEFAULT 0" +
                    ")";

            // Redemption data table
            String redemptionTable = "CREATE TABLE IF NOT EXISTS redemption_data (" +
                    "id " + (isMySQL ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "boss_spawn_x DOUBLE, " +
                    "boss_spawn_y DOUBLE, " +
                    "boss_spawn_z DOUBLE, " +
                    "boss_spawn_world VARCHAR(50), " +
                    "player_spawn_x DOUBLE, " +
                    "player_spawn_y DOUBLE, " +
                    "player_spawn_z DOUBLE, " +
                    "player_spawn_world VARCHAR(50), " +
                    "current_fighter VARCHAR(36), " +
                    "fight_start_time BIGINT DEFAULT 0" +
                    ")";

            // Execute table creation
            try (Statement statement = connection.createStatement()) {
                statement.execute(playerDataTable);
                statement.execute(playerEnchantsTable);
                statement.execute(playerStatsTable);
                statement.execute(playerPerksTable);
                statement.execute(redemptionTable);
            }

            // Create indexes for better performance
            createIndexes(connection);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    /**
     * Creates database indexes for better performance
     */
    private void createIndexes(Connection connection) throws SQLException {
        String[] indexes = {
                "CREATE INDEX IF NOT EXISTS idx_player_enchants_uuid ON player_enchants(uuid)",
                "CREATE INDEX IF NOT EXISTS idx_player_perks_uuid ON player_perks(uuid)",
                "CREATE INDEX IF NOT EXISTS idx_player_data_username ON player_data(username)"
        };

        try (Statement statement = connection.createStatement()) {
            for (String index : indexes) {
                try {
                    statement.execute(index);
                } catch (SQLException e) {
                    // Ignore if index already exists
                    if (!e.getMessage().contains("already exists")) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Gets a database connection
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes a query asynchronously
     */
    public CompletableFuture<Void> executeAsync(String sql, Object... parameters) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }

                statement.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error executing: " + sql, e);
            }
        });
    }

    /**
     * Executes a query and returns a result asynchronously
     */
    public <T> CompletableFuture<T> queryAsync(String sql, ResultSetHandler<T> handler, Object... parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }

                try (ResultSet resultSet = statement.executeQuery()) {
                    return handler.handle(resultSet);
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error querying: " + sql, e);
                return null;
            }
        });
    }

    /**
     * Executes a batch update asynchronously
     */
    public CompletableFuture<Void> executeBatchAsync(String sql, Object[]... parameterSets) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                for (Object[] parameters : parameterSets) {
                    for (int i = 0; i < parameters.length; i++) {
                        statement.setObject(i + 1, parameters[i]);
                    }
                    statement.addBatch();
                }

                statement.executeBatch();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database error executing batch: " + sql, e);
            }
        });
    }

    /**
     * Closes the database connection
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }

    /**
     * Functional interface for handling result sets
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet resultSet) throws SQLException;
    }

    /**
     * Checks if the database is MySQL
     */
    public boolean isMySQL() {
        return isMySQL;
    }
}
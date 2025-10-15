package org.itsharshxd.CivilizationRoyale;

import com.github.luben.zstd.Zstd;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.plugin.java.JavaPlugin;
import org.itsharshxd.CivilizationRoyale.database.DatabaseUtils;
import org.itsharshxd.CivilizationRoyale.file.FileUtils;
import org.itsharshxd.CivilizationRoyale.world.WorldUtil;
import org.popcraft.chunky.api.ChunkyAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.itsharshxd.CivilizationRoyale.world.WorldUtil.removeWorldFromList;

public final class HybridMode extends JavaPlugin {

    private static HybridMode plugin;
    private static int DEFAULT_WORLD_COUNT;
    private boolean processWorldRequests = false;
    private static final ChunkyAPI chunky = Bukkit.getServer().getServicesManager().load(ChunkyAPI.class);

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        saveResource("config.yml", false);
        DEFAULT_WORLD_COUNT = getConfig().getInt("default_world_generation_count");
        connectToDatabase();
        DatabaseUtils.setupDatabase(); // Make sure this is called

        generateDefaultWorlds();
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::processWorldRequests, 0L, 1L);
    }

    @Override
    public void onDisable() {
        DatabaseUtils.close();
        WorldUtil.deleteWorlds(getConfig().getStringList("worlds"));
    }

    private void connectToDatabase() {
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String database = getConfig().getString("database.database");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");

        try {
            DatabaseUtils.connect(host, port, database, username, password);
            if (DatabaseUtils.getConnection() != null && !DatabaseUtils.getConnection().isClosed()) {
                Bukkit.getLogger().info("Database connection is active.");
            } else {
                Bukkit.getLogger().severe("Database connection is not established or is closed.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to connect to the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void generateDefaultWorlds() {
        for (int i = 1; i <= DEFAULT_WORLD_COUNT; i++) {
            final int worldNumber = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                UUID worldUUID = UUID.randomUUID();
                String worldName = "world_" + worldUUID;
                WorldUtil.createBukkitWorld(worldName);
                saveConfig();
                getLogger().info("Created world: " + worldName);
                loadChunksAtBorderCorners(worldName);
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                WorldUtil.unloadWorld(Bukkit.getWorld(worldName)); // we don't need worlds to be loaded & consume resources
                getLogger().info("Unloaded World: " + worldName);

                // Check if this is the last world to be generated
                if (worldNumber == DEFAULT_WORLD_COUNT) {
                    processWorldRequests = true; // Now set this flag after all worlds are generated
                }
            }, 20L * worldNumber);
        }
    }

    public static void loadChunksAtBorderCorners(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2;

        // Calculate corner locations
        Location corner1 = new Location(world, center.getX() - halfSize, 0, center.getZ() - halfSize); // Southwest corner
        Location corner2 = new Location(world, center.getX() + halfSize, 0, center.getZ() + halfSize); // Northeast corner

        // Number of chunks to load around each corner
        int chunksToLoad = 30;
        int chunkRadius = (int) Math.ceil(Math.sqrt(chunksToLoad)) / 2; // Radius in chunks

        // Log corner coordinates
        Logger logger = HybridMode.plugin.getLogger();
        logger.info("Southwest corner coordinates: X=" + corner1.getBlockX() + ", Z=" + corner1.getBlockZ());
        logger.info("Northeast corner coordinates: X=" + corner2.getBlockX() + ", Z=" + corner2.getBlockZ());


        // Execute chunk loading tasks immediately on the main thread
        logger.info("Starting chunk loading tasks...");

        // Load area around the first corner
        startChunkyTask(worldName, corner1, chunkRadius, "Southwest");

        // Load area around the second corner
        startChunkyTask(worldName, corner2, chunkRadius, "Northeast");
    }

    private static void startChunkyTask(String worldName, Location corner, int chunkRadius, String cornerName) {
        Logger logger = HybridMode.plugin.getLogger();
        int chunkX = corner.getBlockX() / 16 - chunkRadius;
        int chunkZ = corner.getBlockZ() / 16 - chunkRadius;
        int size = chunkRadius * 2 + 1;

        logger.info("Starting Chunky task for " + cornerName + " corner...");
        chunky.startTask(
                worldName,
                "square",
                chunkX,
                chunkZ,
                size,
                size,
                "concentric"
        );
        logger.info("Chunky task started for " + cornerName + " corner.");
    }

    private void processWorldRequests() {
        if (!processWorldRequests) {
            return;
        }
        String dbname = getConfig().getString("database.table_prefix") + "world_requests";
        try (Connection conn = DatabaseUtils.getConnection()) {
            String selectQuery = "SELECT id FROM " + dbname + " WHERE status='PENDING' ORDER BY request_time ASC";
            try (PreparedStatement selectStatement = conn.prepareStatement(selectQuery);
                 ResultSet resultSet = selectStatement.executeQuery()) {

                while (resultSet.next()) {
                    int requestId = resultSet.getInt("id");
                    String worldName = getRandomWorldFromList();
                    if (worldName != null) {
                        File worldFolder = new File(getServer().getWorldContainer(), worldName);
                        if (worldFolder.exists()) {
                            byte[] compressedWorld = compressWorld(worldFolder);
                            storeCompressedWorld(requestId, worldName, compressedWorld);
                            deleteWorld(worldName);
                        }
                    } else {
                        generateDefaultWorlds();
                    }
                }
            }
        } catch (SQLException | IOException e) {
            Bukkit.getLogger().severe("An error occurred while processing world requests: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] compressWorld(File worldFolder) throws IOException {
        getLogger().info("Compressing world: " + worldFolder.getName());
        File tempZipFile = new File(worldFolder.getParentFile(), worldFolder.getName() + ".zip");
        FileUtils.zip(worldFolder, tempZipFile);

        byte[] data = Files.readAllBytes(tempZipFile.toPath());
        tempZipFile.delete();

        return Zstd.compress(data);
    }

    private void storeCompressedWorld(int requestId, String worldName, byte[] compressedWorld) throws SQLException {
        getLogger().info("Storing compressed world for request: " + requestId);
        String dbname = getConfig().getString("database.table_prefix") + "world_requests";
        try (Connection conn = DatabaseUtils.getConnection()) {
            String updateQuery = "UPDATE " + dbname + " SET world_name=?, compressed_world=?, status='PROCESSED', response_time=NOW() WHERE id=?";
            PreparedStatement updateStatement = conn.prepareStatement(updateQuery);
            updateStatement.setString(1, worldName);
            updateStatement.setBytes(2, compressedWorld);
            updateStatement.setInt(3, requestId);
            updateStatement.executeUpdate();
        }
    }

    private void deleteWorld(String worldName) {
        getLogger().info("Processed Request, Deleting world from disk: " + worldName);
        File worldFolder = new File(getServer().getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            FileUtils.deleteDirectory(worldFolder);
            removeWorldFromList(worldName);
            saveConfig();
        }
    }

    public String getRandomWorldFromList() {
        List<String> list = getConfig().getStringList("worlds");
        if (list.isEmpty()) {
            return null;
        }
        return list.get(new Random().nextInt(list.size()));
    }

    public static HybridMode getPlugin() {
        return plugin;
    }
}
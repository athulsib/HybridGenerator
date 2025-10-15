package org.itsharshxd.CivilizationRoyale.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.itsharshxd.CivilizationRoyale.HybridMode;

import java.io.File;
import java.util.List;

/**
 * @Author: Athishh
 * Package: org.itsharshxd
 * Created on: 02-Aug-24
 */
public class WorldUtil {

    public static void createBukkitWorld(String worldName) {
        WorldCreator worldCreator = new WorldCreator(worldName);
        worldCreator.environment(World.Environment.NORMAL);
        worldCreator.type(WorldType.NORMAL);
        Bukkit.createWorld(worldCreator);
        addWorldToList(worldName);
    }

    public static void unloadWorld(World world) {
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
    }

    public static void addWorldToList(String worldName) {
        List<String> list = HybridMode.getPlugin().getConfig().getStringList("worlds");
        if (!list.contains(worldName)) {
            list.add(worldName);
            HybridMode.getPlugin().getConfig().set("worlds", list);
        }
    }

    public static void removeWorldFromList(String worldName) {
        List<String> list = HybridMode.getPlugin().getConfig().getStringList("worlds");
        if (list.contains(worldName)) {
            list.remove(worldName);
            HybridMode.getPlugin().getConfig().set("worlds", list);
        }
    }

    public static void deleteWorlds(List<String> worldNames) {
        for (String worldName : worldNames) {
            // Get the world by name
            World world = Bukkit.getWorld(worldName);
            removeWorldFromList(worldName);
            HybridMode.getPlugin().saveConfig();

            if (world != null) {
                // Unload the world if it's loaded
                boolean unloaded = Bukkit.unloadWorld(world, false);
                if (unloaded) {
                    Bukkit.getLogger().info("[Disable] Successfully unloaded world: " + worldName);
                } else {
                    Bukkit.getLogger().warning("[Disable] Failed to unload world: " + worldName);
                    continue;
                }
            }

            // Delete the world folder
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (deleteDirectory(worldFolder)) {
                Bukkit.getLogger().info("[Disable] Successfully deleted world: " + worldName);
            } else {
                Bukkit.getLogger().warning("[Disable] Failed to delete world folder: " + worldName);
            }
        }
    }

    // Helper method to delete the directory
    private static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return directory.delete();
    }
}

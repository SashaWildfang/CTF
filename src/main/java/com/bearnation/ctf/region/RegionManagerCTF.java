package com.bearnation.ctf.region;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

public class RegionManagerCTF {

    public static void setupRegions(RegionSetupPayload payload) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        Map<String, List<String>> regionsByWorld = new HashMap<>();

        // Group region IDs by world
        for (Map.Entry<String, RegionBounds> entry : payload.regionBounds.entrySet()) {
            String worldName = entry.getValue().getWorld();
            regionsByWorld.computeIfAbsent(worldName, k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<String, List<String>> worldEntry : regionsByWorld.entrySet()) {
            String worldName = worldEntry.getKey();
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                Bukkit.getLogger().warning("[CTF] World not loaded: " + worldName);
                continue;
            }

            RegionManager rm = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (rm == null) {
                Bukkit.getLogger().warning("[CTF] No region manager for world: " + worldName);
                continue;
            }

            // Clear existing regions ONCE per world
            Bukkit.getLogger().info("[CTF] Clearing existing regions in world: " + worldName);
            new HashSet<>(rm.getRegions().keySet()).forEach(rm::removeRegion);

            // Add new regions for this world
            for (String regionId : worldEntry.getValue()) {
                RegionBounds b = payload.regionBounds.get(regionId);

                BlockVector3 min = BlockVector3.at(b.getMinX(), b.getMinY(), b.getMinZ());
                BlockVector3 max = BlockVector3.at(b.getMaxX(), b.getMaxY(), b.getMaxZ());
                ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

                Map<String, String> flags = payload.regionFlags.get(regionId);
                if (flags != null) {
                    for (var fe : flags.entrySet()) {
                        Flag<?> flag = Flags.fuzzyMatchFlag(registry, fe.getKey());
                        if (flag == null) {
                            Bukkit.getLogger().warning("[CTF] Unknown flag: " + fe.getKey());
                            continue;
                        }

                        try {
                            @SuppressWarnings("unchecked")
                            Flag<Object> casted = (Flag<Object>) flag;

                            // Convert "true"/"false" to "allow"/"deny"
                            String input = fe.getValue()
                                    .replace("true", "allow")
                                    .replace("false", "deny");

                            FlagContext context = FlagContext.create().setInput(input).build();
                            Object parsed = casted.parseInput(context);

                            region.setFlag(casted, parsed);
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[CTF] Could not parse flag " + fe.getKey() + ": " + e.getMessage());
                        }
                    }
                }

                rm.addRegion(region);
                Bukkit.getLogger().info("[CTF] Region added: " + regionId);
            }

            try {
                rm.save(); // Save once after all regions are added
            } catch (StorageException e) {
                Bukkit.getLogger().warning("[CTF] Failed to save regions for " + worldName);
            }
        }
    }
}

package com.bearnation.ctf.listeners;

import com.bearnation.ctf.CTF;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public class PhysicsGuardListener implements Listener {

    private final CTF plugin;

    public PhysicsGuardListener(CTF plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean shouldFreeze(World world) {
        if (world == null) return false;
        // Only in the CTF world and only while we’re pre-game (map freshly pasted)
        return "world".equalsIgnoreCase(world.getName())
                && "PRE_GAME".equalsIgnoreCase(plugin.getCurrentPhase());
    }

    // Stop sand/gravel/ConcretePowder from turning into blocks
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock fb && shouldFreeze(fb.getWorld())) {
            event.setCancelled(true);
        }
    }

    // Stop general physics (torch pops, fluids, dust updates, etc.)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        World w = event.getBlock().getWorld();
        if (shouldFreeze(w)) {
            event.setCancelled(true);
        }
    }
}

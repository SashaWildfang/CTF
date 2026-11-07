package com.bearnation.ctf.bus;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class BukkitDispatch {
    private final Plugin plugin;

    public BukkitDispatch(Plugin plugin) {
        this.plugin = plugin;
    }

    public void main(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}

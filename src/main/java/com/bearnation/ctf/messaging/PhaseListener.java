package com.bearnation.ctf.messaging;

import com.bearnation.ctf.CTF;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class PhaseListener implements PluginMessageListener {

    private final CTF plugin;
    private boolean handledPreGamePhase = false;

    public PhaseListener(CTF plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equalsIgnoreCase("cardinal:main")) return;

        String[] parts = new String(message).split("\0", 2);
        if (parts.length < 2) return;

        String subChannel = parts[0];
        String payload = parts[1];

        if (subChannel.equalsIgnoreCase("phase")) {
            plugin.getLogger().info("Received phase: " + payload);
            handlePhase(payload.trim().toUpperCase());
        }
    }

    private void handlePhase(String phase) {
        plugin.setCurrentPhase(phase); // store latest phase

        if (phase.equals("PRE_GAME")) {
            if (handledPreGamePhase) {
                plugin.getLogger().info("PRE_GAME already handled. Skipping.");
                return;
            }
            handledPreGamePhase = true;

            Component message = Component.text("You have 20 seconds to preview the map!", NamedTextColor.AQUA);
            Bukkit.broadcast(message);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.setAllowFlight(true);
                p.setFlying(true);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                }
            }, 20 * 20);
        }

        if (phase.equals("IN_GAME") || phase.equals("POST_GAME")) {
            handledPreGamePhase = false;
        }
    }

}

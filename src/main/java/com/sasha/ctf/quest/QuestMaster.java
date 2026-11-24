/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.quest;

import com.sasha.ctf.FractalCTF;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /quests setnpc (hub, staff): puts the Quest Master where the player stands, facing the way they face. It's a
 * character in hub-npcs.yml with role: quests (clicking it opens /quests); the line is added (or moved) at the top of
 * the npcs: list, so the rest of the file stays as it was.
 */
public final class QuestMaster {

    private QuestMaster() {}

    public static void place(FractalCTF plugin, Player player) {
        if (!plugin.ctfConfig().hub() || plugin.hub() == null) {
            plugin.messages().send(player, "quests.npc-hub-only");
            return;
        }
        File file = new File(plugin.getDataFolder(), "hub-npcs.yml");
        try {
            if (!file.isFile()) {
                var in = plugin.getResource("hub-npcs.yml");
                if (in == null) return;
                Files.copy(in, file.toPath());
            }
            Location at = player.getLocation();
            float yaw = Math.round((at.getYaw() + 180) / 45f) * 45f;    // face back at the player, to the nearest 45°
            String line = String.format(Locale.ROOT, "  - {id: quest-master, name: \"%s\", role: quests, profession: librarian, type: plains,"
                    + " x: %.1f, y: %.1f, z: %.1f, yaw: %.0f, lines: [%s]}",
                    plugin.messages().raw("quests.npc.name").replace("\"", "'"), at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5,
                    yaw > 180 ? yaw - 360 : yaw, lines(plugin));
            List<String> out = new ArrayList<>();
            boolean added = false;
            for (String l : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (l.contains("id: quest-master")) continue;
                out.add(l);
                if (!added && l.trim().equals("npcs:")) {
                    out.add(line);
                    added = true;
                }
            }
            if (!added) {
                out.add("npcs:");
                out.add(line);
            }
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            plugin.hub().reloadFile();
            plugin.messages().send(player, "quests.npc-placed");
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't write hub-npcs.yml: " + e.getMessage());
        }
    }

    private static String lines(FractalCTF plugin) {
        List<String> quoted = new ArrayList<>();
        for (String line : plugin.messages().list("quests.npc.lines")) quoted.add("\"" + line.replace("\"", "'") + "\"");
        return String.join(", ", quoted);
    }
}

package com.bearnation.ctf.schematic;

import com.bearnation.ctf.CTF;
import com.bearnation.ctf.schematic.SchematicPastePayload;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SchematicUtil {

    public static void pasteSchematic(SchematicPastePayload payload) {
        try {
            File schematic = new File(CTF.getInstance().getDataFolder(), "schematics/" + payload.mapId + ".schem");
            if (!schematic.exists()) {
                Bukkit.getLogger().warning("[CTF] Schematic not found: " + schematic.getAbsolutePath());
                return;
            }

            ClipboardFormat format = ClipboardFormats.findByFile(schematic);
            if (format == null) {
                Bukkit.getLogger().warning("[CTF] Unknown schematic format: " + schematic.getName());
                return;
            }

            try (InputStream is = new FileInputStream(schematic);
                 ClipboardReader reader = format.getReader(is)) {

                Clipboard clipboard = reader.read();

                org.bukkit.World bukkitWorld = Bukkit.getWorld("ctf"); // Optional: make configurable
                if (bukkitWorld == null) {
                    Bukkit.getLogger().severe("[CTF] World 'ctf' not found."); // fixed message
                    return;
                }

                World adaptedWorld = BukkitAdapter.adapt(bukkitWorld);

                int safeY = Math.max(payload.pasteY, 64);
                BlockVector3 pasteAt = BlockVector3.at(payload.pasteX, safeY, payload.pasteZ);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(adaptedWorld)) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(pasteAt)
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                    editSession.flushSession();

                    // Removed: PluginMessageUtils.sendMessageToProxy(...)
                    Bukkit.getLogger().info("[CTF] Successfully pasted schematic: " + payload.mapId + " at " + pasteAt);
                }

            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[CTF] Error pasting schematic " + payload.mapId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

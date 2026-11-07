package com.bearnation.ctf.messaging;
// package com.bearnation.ctf.messaging;

import com.bearnation.ctf.CTF;
import com.bearnation.ctf.bus.Bus;
import com.bearnation.ctf.bus.MessageBus;
import com.bearnation.ctf.schematic.SchematicPastePayload;
import com.bearnation.ctf.util.WorldUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

public class SchematicPasteListener {

    private final CTF plugin;
    private final MessageBus bus;          // NEW
    private final Gson gson = new Gson();

    public SchematicPasteListener(CTF plugin, MessageBus bus) {  // NEW ctor
        this.plugin = plugin;
        this.bus = bus;
    }

    /** Called by CtfMessaging on the Bukkit main thread */
    public void handle(com.google.gson.JsonObject payload) {
        try {
            SchematicPastePayload pojo = gson.fromJson(payload, SchematicPastePayload.class);
            handlePaste(pojo);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[CTF] Failed to handle schematic paste: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Allows triggering a paste from anywhere — not just NATS. */
    public void handlePaste(SchematicPastePayload payload) {
        String worldName = "ctf"; // make configurable later if you like
        Bukkit.getLogger().info("[CTF] Initiating world reset for: " + worldName);

        plugin.startReset();

        WorldUtil.resetWorldAndPaste(worldName, payload, () -> {
            Bukkit.getLogger().info("[CTF] Paste callback — finishing reset.");
            plugin.finishReset();

            // Notify proxy (or Hub) via NATS instead of plugin messaging:
            try {
                JsonObject p = new JsonObject();
                p.addProperty("mapId", payload.mapId);
                bus.publish("bn.proxy", Bus.env("schematic_paste_done", "proxy", p).toString());
            } catch (Exception ex) {
                plugin.getLogger().warning("[CTF] Failed to publish schematic_paste_done: " + ex.getMessage());
            }

            // Run any queued region setup
            var r = plugin.getRegionSetupListener();
            if (r != null) {
                Bukkit.getLogger().info("[CTF] Triggering queued region setup immediately after paste.");
                r.runQueuedIfPresent();
            }
        });
    }
}

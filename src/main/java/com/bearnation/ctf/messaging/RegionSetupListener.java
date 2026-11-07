package com.bearnation.ctf.messaging;

import com.bearnation.ctf.CTF;
import com.bearnation.ctf.region.RegionManagerCTF;
import com.bearnation.ctf.region.RegionSetupPayload;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

public class RegionSetupListener {

    private final CTF plugin;
    private final Gson gson = new Gson();
    private RegionSetupPayload queuedPayload = null;

    public RegionSetupListener(CTF plugin) {
        this.plugin = plugin;
    }

    /** Called by CtfMessaging on the Bukkit main thread */
    public void handle(JsonObject payload) {
        try {
            RegionSetupPayload pojo = gson.fromJson(payload, RegionSetupPayload.class);

            // --- Update TAB header/footer immediately (even if we end up queuing) ---
            String mapName = payload.has("mapName")
                    ? payload.get("mapName").getAsString()
                    : (payload.has("mapId") ? payload.get("mapId").getAsString() : "Unknown Map");
            String author = payload.has("author")
                    ? payload.get("author").getAsString()
                    : "Unknown";

            plugin.updateTabHeader(mapName, author);
            plugin.refreshTabGrid();

            // Queue region setup if a reset is in progress
            if (plugin.isResetInProgress()) {
                Bukkit.getLogger().warning("[CTF] World reset in progress — queuing region setup until after paste.");
                queuedPayload = pojo;
                return;
            }

            applyRegions(pojo);

        } catch (Exception e) {
            Bukkit.getLogger().warning("[CTF] Failed to deserialize RegionSetupPayload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runQueuedIfPresent() {
        if (queuedPayload != null) {
            Bukkit.getLogger().info("[CTF] Running queued region setup now...");
            RegionSetupPayload tmp = queuedPayload;
            queuedPayload = null;
            applyRegions(tmp);
        }
    }

    private void applyRegions(RegionSetupPayload payload) {
        try {
            Bukkit.getLogger().info("[CTF] Applying regions for map: " + payload.mapId);
            RegionManagerCTF.setupRegions(payload);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[CTF] Error setting up regions: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

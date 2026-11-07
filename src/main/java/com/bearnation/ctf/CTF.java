package com.bearnation.ctf;

import com.bearnation.ctf.bus.MessageBus;
import com.bearnation.ctf.bus.NatsBus;
import com.bearnation.ctf.listeners.PhysicsGuardListener;
import com.bearnation.ctf.messaging.CtfMessaging;
import com.bearnation.ctf.messaging.RegionSetupListener;
import com.bearnation.ctf.messaging.SchematicPasteListener;
import com.bearnation.ctf.tablist.TablistGridManager;
import com.bearnation.ctf.tablist.TablistListenerCTF;
import com.bearnation.ctf.tablist.TablistManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class CTF extends JavaPlugin {

    private static CTF instance;

    private MessageBus bus;
    private String currentPhase = "IDLE";
    private boolean resetInProgress = false;

    // Keep a reference so we can run queued setup after reset finishes
    private RegionSetupListener regionSetupListener;

    // --- Tablist managers (NEW) ---
    private TablistManager tablists;
    private TablistGridManager tabGrid;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1) Connect to NATS
        try {
            // NOTE: if your password has '$', encode it as %24 in URLs.
            String url = getConfig().getString("nats.url", "nats://localhost:4222");
            bus = new NatsBus(url);
        } catch (Exception e) {
            getLogger().severe("NATS connect failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2) Instantiate domain handlers
        this.regionSetupListener = new RegionSetupListener(this);
        SchematicPasteListener schematicPasteListener = new SchematicPasteListener(this, bus); // pass bus here

        // 3) Tablist system (NEW)
        this.tablists = new TablistManager();
        this.tabGrid = new TablistGridManager();
        new TablistListenerCTF(this, tablists, tabGrid);

        // Optional default header until map info arrives
        tablists.setMapHeader("Loading Map", "…");
        tablists.applyHeaderFooterAll();
        // Ensure everyone gets a filled grid next tick
        Bukkit.getScheduler().runTask(this, tabGrid::rebuildAll);

        // 4) Subscribe to CTF subject via CtfMessaging
        String shardId = "ctf-1"; // or null to accept all
        new CtfMessaging(this, bus, regionSetupListener, schematicPasteListener, shardId).subscribe();
        // If your CtfMessaging ctor has no shardId, use:
        // new CtfMessaging(this, bus, regionSetupListener, schematicPasteListener).subscribe();

        // 5) Register Bukkit listeners
        getServer().getPluginManager().registerEvents(new PhysicsGuardListener(this), this);

        getLogger().info("CTF enabled (NATS wired, tablist initialized, listening on bn.ctf).");
    }

    @Override
    public void onDisable() {
        if (tabGrid != null) {
            try { tabGrid.clearAll(); } catch (Exception ignored) {}
        }
        if (bus != null) try { bus.close(); } catch (Exception ignored) {}
    }

    public static CTF getInstance() { return instance; }

    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String phase) { this.currentPhase = phase; }

    public boolean isResetInProgress() { return resetInProgress; }
    public void startReset() {
        resetInProgress = true;
        getLogger().info("[CTF] Reset flag set TRUE.");
    }
    public void finishReset() {
        resetInProgress = false;
        getLogger().info("[CTF] Reset flag set FALSE — running queued region setup (if any).");
        if (regionSetupListener != null) regionSetupListener.runQueuedIfPresent();
    }

    public RegionSetupListener getRegionSetupListener() {
        return regionSetupListener;
    }

    /* ===================== Tablist helpers (NEW) ===================== */

    /** Call this when the match/map loads so the header shows "MapName by Author". */
    public void updateTabHeader(String mapName, String author) {
        if (tablists != null) {
            tablists.setMapHeader(mapName, author);
            tablists.applyHeaderFooterAll();
        }
    }

    /** Re-top-off the fake grid for all viewers (e.g., after team shuffles). */
    public void refreshTabGrid() {
        if (tabGrid != null) tabGrid.rebuildAll();
    }

    public TablistManager getTablists() { return tablists; }
    public TablistGridManager getTabGrid() { return tabGrid; }
}

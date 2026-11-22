/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf;

import com.sasha.ctf.chat.Chat;
import com.sasha.ctf.command.CtfCommands;
import com.sasha.ctf.config.CtfConfig;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.Database;
import com.sasha.ctf.game.GameManager;
import com.sasha.ctf.game.MapStore;
import com.sasha.ctf.hub.HubManager;
import com.sasha.ctf.kit.Kits;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import com.sasha.ctf.util.Network;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Capture the Flag for Fractal Network. The same jar runs on both CTF servers: in hub mode it runs the
 * waiting lobby (votes, kits, countdown); in game mode it plays the matches. The two talk through a
 * shared MySQL database.
 */
public final class FractalCTF extends JavaPlugin {

    private CtfConfig config;
    private Messages messages;
    private Database database;
    private Kits kits;
    private HubManager hub;
    private GameManager game;
    private MapStore maps;
    private Chat chat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        Items.init(new NamespacedKey(this, "item"), new NamespacedKey(this, "kit_slot"));
        Network.register(this);
        connectDatabase();

        kits = new Kits(this);
        getServer().getPluginManager().registerEvents(new Menu.Clicks(), this);
        chat = new Chat(this);
        chat.start();

        if (config.hub()) {
            hub = new HubManager(this);
            hub.start();
        } else {
            maps = new MapStore(this);
            maps.reload();
            game = new GameManager(this);
            game.start();
        }
        new CtfCommands(this).register();
        getLogger().info("FractalCTF is running as the " + (config.hub() ? "hub" : "game server") + ".");
    }

    @Override
    public void onDisable() {
        if (hub != null) hub.stop();
        if (game != null) game.stop();
        if (database != null) database.close();
    }

    /**
     * Lets bukkit.yml give a world an empty generator ({@code worlds: world: generator: FractalCTF}), so the
     * hub's floating island never gets new terrain generated around it.
     */
    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new com.sasha.ctf.game.VoidGenerator();
    }

    public void reloadSettings() {
        reloadConfig();
        config = CtfConfig.read(getConfig());
        messages = Messages.load(this);
    }

    private void connectDatabase() {
        if (!config.database().configured()) {
            getLogger().severe("No database set up: fill in database: in config.yml (the same database on CTF-Hub and CTF-Game), then restart.");
            return;
        }
        try {
            database = new Database(config.database(), getLogger());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Couldn't connect to the CTF database: check database: in config.yml", e);
            database = null;
        }
    }

    public CtfConfig ctfConfig() { return config; }
    public Messages messages() { return messages; }
    /** Null when the database isn't set up or reachable. */
    public Database database() { return database; }
    public Kits kits() { return kits; }
    public HubManager hub() { return hub; }
    public GameManager game() { return game; }
    public MapStore maps() { return maps; }
    public Chat chat() { return chat; }
}

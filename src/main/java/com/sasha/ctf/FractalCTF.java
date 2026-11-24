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
    private com.sasha.ctf.kit.Boosters boosters;

    private com.sasha.ctf.kit.PendingCoins pendingCoins;

    public com.sasha.ctf.kit.PendingCoins pendingCoins() { return pendingCoins; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        Items.init(new NamespacedKey(this, "item"), new NamespacedKey(this, "kit_slot"));
        Network.register(this);
        parties.register(this);
        connectDatabase();
        network = com.sasha.ctf.data.NetworkLink.open(getConfig().getConfigurationSection("network-database"), getLogger());

        kits = new Kits(this);
        getServer().getPluginManager().registerEvents(new Menu.Clicks(), this);
        chat = new Chat(this);
        chat.listenForGgWaves();
        chat.start();
        boosters = new com.sasha.ctf.kit.Boosters(this);
        boosters.start();
        if (network != null) {
            var networkCoins = new com.sasha.ctf.game.NetworkCoins(this);
            getServer().getPluginManager().registerEvents(networkCoins, this);
            networkCoins.start();
        }
        pendingCoins = new com.sasha.ctf.kit.PendingCoins(this);
        getServer().getPluginManager().registerEvents(pendingCoins, this);

        if (config.hub()) {
            hub = new HubManager(this);
            hub.start();
        } else {
            maps = new MapStore(this);
            maps.reload();
            game = new GameManager(this);
            game.start();
            if (network != null) {
                networkXp = new com.sasha.ctf.game.NetworkXp(this);
                getServer().getPluginManager().registerEvents(networkXp, this);
                networkXp.start();
            }
        }
        if (database != null) {
            quests = new com.sasha.ctf.quest.Quests(this);
            questMenu = new com.sasha.ctf.quest.QuestMenu(this, quests);
            getServer().getPluginManager().registerEvents(quests, this);
            quests.start();
            if (config.hub() && hub != null && com.sasha.ctf.quest.QuestMaster.refresh(this)) hub.reloadFile();
            if (!config.hub()) {
                questTracker = new com.sasha.ctf.quest.QuestTracker(this);
                getServer().getPluginManager().registerEvents(questTracker, this);
                questTracker.start();
            }
        }
        var questCommand = getCommand("quests");
        if (questCommand != null) questCommand.setExecutor((sender, command, label, args) -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("setnpc") && sender.hasPermission("ctf.admin")) {
                if (sender instanceof org.bukkit.entity.Player player) com.sasha.ctf.quest.QuestMaster.place(this, player);
                return true;
            }
            if (!(sender instanceof org.bukkit.entity.Player player)) return true;
            if (questMenu == null) messages.send(player, "general.no-database");
            else questMenu.open(player);
            return true;
        });
        hints = new com.sasha.ctf.chat.Hints(this);
        hints.start();
        new CtfCommands(this).register();
        shortPrefix();
        new com.sasha.ctf.util.WorldKeeper(this).start();
        getLogger().info("FractalCTF is running as the " + (config.hub() ? "hub" : "game server") + ".");
    }

    @Override
    public void onDisable() {
        if (hub != null) hub.stop();
        if (game != null) game.stop();
        if (networkXp != null) networkXp.flush();
        if (quests != null) quests.shutdown();
        if (database != null) database.close();
        if (network != null) network.close();
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

    private final com.sasha.ctf.util.Parties parties = new com.sasha.ctf.util.Parties();
    private com.sasha.ctf.quest.Quests quests;
    private com.sasha.ctf.quest.QuestMenu questMenu;
    private com.sasha.ctf.quest.QuestTracker questTracker;

    /** Quests and challenges (null without the CTF database). */
    public com.sasha.ctf.quest.Quests quests() { return quests; }
    public com.sasha.ctf.quest.QuestMenu questMenu() { return questMenu; }
    /** Game server only. */
    public com.sasha.ctf.quest.QuestTracker questTracker() { return questTracker; }

    /** Parties (from Core on the proxy), so Auto Select can keep them together. */
    public com.sasha.ctf.util.Parties parties() { return parties; }

    private com.sasha.ctf.data.NetworkLink network;
    private com.sasha.ctf.game.NetworkXp networkXp;

    /** Network XP and the level coin multiplier (game server with the network database); null otherwise. */
    public com.sasha.ctf.game.NetworkXp networkXp() { return networkXp; }

    /** The network database (boosters, network XP), shared with the Lobby; null without it. */
    public com.sasha.ctf.data.NetworkLink network() { return network; }
    public Kits kits() { return kits; }
    public HubManager hub() { return hub; }
    public GameManager game() { return game; }
    public MapStore maps() { return maps; }
    public Chat chat() { return chat; }

    private com.sasha.ctf.chat.Hints hints;

    public com.sasha.ctf.chat.Hints hints() { return hints; }
    public com.sasha.ctf.kit.Boosters boosters() { return boosters; }

    /**
     * Every command also answers to /ctf:<name> (the server only gives /fractalctf:<name> by itself, from the
     * plugin's name): a copy of each is registered under the "ctf" prefix that runs the real one.
     */
    private void shortPrefix() {
        var map = getServer().getCommandMap();
        for (String name : getDescription().getCommands().keySet()) {
            org.bukkit.command.PluginCommand real = getCommand(name);
            if (real == null) continue;
            org.bukkit.command.Command copy = new org.bukkit.command.Command(name, real.getDescription(), real.getUsage(), real.getAliases()) {
                @Override
                public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                    return real.execute(sender, label, args);
                }

                @Override
                public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                    return real.tabComplete(sender, alias, args);
                }
            };
            copy.setPermission(real.getPermission());
            map.register("ctf", copy);
        }
    }
}

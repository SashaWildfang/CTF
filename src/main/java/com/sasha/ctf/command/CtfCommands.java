/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.command;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.game.MapEditor;
import com.sasha.ctf.model.Spot;
import com.sasha.ctf.util.Network;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /kit, /vote, /join, /leave, /ctfstats, /ctf and /ctfmap. */
public final class CtfCommands implements TabExecutor {

    private final FractalCTF plugin;
    private final MapEditor editor;
    private final StatsMenu stats;

    public CtfCommands(FractalCTF plugin) {
        this.plugin = plugin;
        this.editor = plugin.ctfConfig().hub() ? null : new MapEditor(plugin);
        this.stats = plugin.hub() != null ? plugin.hub().stats() : new StatsMenu(plugin);
    }

    public void register() {
        for (String name : List.of("kit", "vote", "join", "leave", "ctfstats", "ctf", "ctfmap", "t", "tc", "coins", "rate",
                "spectate", "match", "pausematch", "resumematch", "endmatch", "editlobby", "savelobby", "regenlobby", "coinsadmin", "fly", "coinbooster", "pickteam", "setmap", "spawn", "previewkit", "endvoting", "mutechat", "unmutechat", "slowchat", "clearchat", "chatfilter", "hints", "tptohill")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) continue;
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    /** /mutechat, /unmutechat, /slowchat, /clearchat and /chatfilter. */
    private void chatControl(CommandSender sender, String name, String[] args) {
        Messages messages = plugin.messages();
        com.sasha.ctf.chat.ChatGuard guard = plugin.chat().guard();
        String by = sender instanceof Player p ? p.getName() : "Staff";
        switch (name) {
            case "mutechat" -> {
                if (guard.muted()) messages.send(sender, "chat.already-muted");
                else guard.mute(by, String.join(" ", args));
            }
            case "unmutechat" -> {
                if (!guard.muted()) messages.send(sender, "chat.not-muted");
                else guard.unmute(by);
            }
            case "slowchat" -> {
                if (args.length == 0) {
                    messages.send(sender, "chat.slow-usage", Messages.arg("now", guard.slow() > 0 ? guard.slow() + "s" : "off"));
                    return;
                }
                if (args[0].equalsIgnoreCase("off") || args[0].equals("0")) {
                    guard.slow(by, 0);
                    return;
                }
                try {
                    double seconds = Double.parseDouble(args[0].replace("s", ""));
                    if (seconds <= 0 || seconds > 600) throw new NumberFormatException();
                    guard.slow(by, seconds);
                } catch (NumberFormatException e) {
                    messages.send(sender, "chat.slow-usage", Messages.arg("now", guard.slow() > 0 ? guard.slow() + "s" : "off"));
                }
            }
            case "clearchat" -> guard.clear(by);
            case "chatfilter" -> {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
                switch (sub) {
                    case "add", "remove" -> {
                        if (args.length < 3 || !(args[1].equalsIgnoreCase("censor") || args[1].equalsIgnoreCase("block"))) {
                            messages.send(sender, "chat.filter-usage");
                            return;
                        }
                        String list = args[1].toLowerCase(java.util.Locale.ROOT), word = args[2];
                        boolean changed = sub.equals("add") ? guard.add(list, word) : guard.remove(list, word);
                        messages.send(sender, changed ? (sub.equals("add") ? "chat.filter-added" : "chat.filter-removed") : "chat.filter-unchanged",
                                Messages.arg("word", word), Messages.arg("list", list));
                    }
                    case "list" -> {
                        String list = args.length > 1 && args[1].equalsIgnoreCase("block") ? "block" : "censor";
                        List<String> words = guard.list(list);
                        messages.send(sender, "chat.filter-list", Messages.arg("list", list), Messages.arg("count", words.size()),
                                Messages.arg("words", String.join(", ", words)));
                    }
                    case "test" -> messages.send(sender, "chat.filter-test", Messages.arg("result",
                            guard.test(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)))));
                    case "reload" -> {
                        guard.load();
                        messages.send(sender, "chat.filter-reloaded");
                    }
                    default -> messages.send(sender, "chat.filter-usage");
                }
            }
            default -> { }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Messages messages = plugin.messages();
        boolean hub = plugin.ctfConfig().hub();
        switch (command.getName()) {
            case "editlobby", "savelobby", "regenlobby" -> {
                if (plugin.hub() == null) {
                    messages.send(sender, "admin.hub-only");
                    return true;
                }
                switch (command.getName()) {
                    case "editlobby" -> {
                        if (sender instanceof Player player) plugin.hub().lobby().edit(player);
                        else players(sender);
                    }
                    case "savelobby" -> plugin.hub().lobby().save(sender);
                    default -> plugin.hub().lobby().regenerate(sender, args);
                }
            }
            case "kit" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (!hub && !player.hasPermission("ctf.kit.ingame")) {
                    messages.send(player, "perk.kit-ingame");                // Champion and up change kits in a match
                    return true;
                }
                if (args.length == 0) {
                    plugin.kits().openMenu(player);
                    return true;
                }
                // /kit <kit>: straight to a kit you have unlocked
                var kit = com.sasha.ctf.kit.Kit.parse(args[0]).orElse(null);
                if (kit == null) messages.send(player, "kits.unknown", Messages.arg("kit", args[0]));
                else if (!plugin.kits().owns(player.getUniqueId(), kit)) {
                    messages.send(player, "kits.not-owned", Messages.arg("kit", kit.display()));
                    if (hub) plugin.kits().confirmBuy(player, kit);
                } else plugin.kits().choose(player, kit);
            }
            case "vote" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (!hub) {
                    messages.send(player, "vote.closed");
                } else if (args.length > 0) {
                    try {
                        plugin.hub().vote(player, Integer.parseInt(args[0]) - 1);
                    } catch (NumberFormatException e) {
                        messages.send(player, "vote.usage");
                    }
                } else {
                    plugin.hub().items().openVote(player);
                }
            }
            case "join" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (hub) plugin.hub().joinMatch(player);
                else messages.send(player, "game.already-here");
            }
            case "leave" -> {
                if (!(sender instanceof Player player)) return players(sender);
                Network.send(plugin, player, hub ? plugin.ctfConfig().lobbyServer() : plugin.ctfConfig().hubServer());
            }
            case "ctfstats" -> {
                if (args.length > 0 && sender instanceof Player viewer) {
                    stats.open(viewer, args[0]);
                    return true;
                }
                @SuppressWarnings("deprecation") OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0])
                        : sender instanceof Player self ? self : null;
                if (target == null) return players(sender);
                if (sender instanceof Player viewer) stats.open(viewer, target);
                else StatsView.show(plugin, sender, target);
            }
            case "spectate" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (hub) {
                    if (!player.hasPermission("ctf.spectate")) messages.send(player, "perk.spectate");
                    else plugin.hub().spectate(player);
                } else {
                    var match = plugin.game().match();
                    if (match == null || match.phase() == com.sasha.ctf.game.Match.Phase.OVER) messages.send(player, "admin.no-match");
                    else if (match.spectating(player.getUniqueId())) match.playAgain(player);       // back onto a team: anyone may
                    else if (!player.hasPermission("ctf.spectate")) messages.send(player, "perk.spectate");
                    else match.leaveToSpectate(player);
                }
            }
            case "coinsadmin" -> coinsAdmin(sender, args);
            case "coinbooster" -> {
                // /coinbooster: what is running. /coinbooster add <player> [minutes] [multiplier] and /coinbooster clear: staff and the store.
                String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (action.isEmpty() || !sender.hasPermission("ctf.admin")) {
                    plugin.boosters().tell(sender, false);
                } else if (action.equals("clear") || action.equals("stop")) {
                    plugin.boosters().clear(sender);
                } else if ((action.equals("add") || action.equals("give")) && args.length >= 2) {
                    int minutes = 60, multiplier = 2;
                    try {
                        if (args.length > 2) minutes = Integer.parseInt(args[2]);
                        if (args.length > 3) multiplier = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        minutes = -1;
                    }
                    if (minutes < 1 || minutes > 1440 || multiplier < 2 || multiplier > 10) messages.send(sender, "booster.usage");
                    else plugin.boosters().add(sender, args[1], minutes, multiplier);
                } else {
                    messages.send(sender, "booster.usage");
                }
            }
            case "pickteam" -> {
                // /jointeam (/team, /pickteam): the team menu, on the game server while a match is loaded or on.
                if (!(sender instanceof Player player)) return players(sender);
                var match = plugin.game() == null ? null : plugin.game().match();
                if (plugin.hub() != null) messages.send(player, "team.game-only");
                else if (match == null || match.phase() == com.sasha.ctf.game.Match.Phase.OVER || !match.world().equals(player.getWorld())) messages.send(player, "team.no-match");
                else if (args.length == 0) plugin.game().teamPicker().open(player, match);
                else if (args[0].equalsIgnoreCase("auto") || args[0].equalsIgnoreCase("random")) plugin.game().teamPicker().pick(player, match, null);
                else {
                    var team = com.sasha.ctf.model.TeamColor.parse(args[0]).orElse(null);
                    if (team == null) messages.send(player, "team.usage");
                    else plugin.game().teamPicker().pick(player, match, team);
                }
            }
            case "hints" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (!player.hasPermission("ctf.hints.toggle")) messages.send(player, "hints.locked");   // a Guardian+ perk
                else plugin.hints().toggle(player);
            }
            case "mutechat", "unmutechat", "slowchat", "clearchat", "chatfilter" -> {
                if (!sender.hasPermission("ctf.chat.staff")) { messages.send(sender, "general.no-permission"); return true; }
                chatControl(sender, command.getName(), args);
            }
            case "endvoting" -> {
                if (!sender.hasPermission("ctf.admin")) { messages.send(sender, "general.no-permission"); return true; }
                if (plugin.hub() == null) messages.send(sender, "admin.hub-only");
                else if (plugin.hub().endVoting()) messages.broadcast("hub.vote-ended", Messages.arg("player", sender instanceof Player p ? p.getName() : "Staff"));
                else messages.send(sender, "admin.endvoting-none");
            }
            case "setmap" -> {
                if (!sender.hasPermission("ctf.admin")) messages.send(sender, "general.no-permission");
                else setMap(sender, args);
            }
            case "spawn" -> {
                // in a match: back to base after standing still for a while; in the lobby: the lobby's spawn
                if (!(sender instanceof Player player)) return players(sender);
                if (plugin.hub() != null) plugin.hub().move(player, plugin.hub().spawn());
                else plugin.game().spawnReturn().start(player);
            }
            case "previewkit" -> {
                if (!(sender instanceof Player player)) return players(sender);
                var kit = args.length == 0 ? plugin.kits().of(player.getUniqueId()) : com.sasha.ctf.kit.Kit.parse(args[0]).orElse(null);
                if (kit == null) messages.send(player, "kits.unknown", Messages.arg("kit", args[0]));
                else plugin.kits().preview(player, kit);
            }
            case "fly" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (plugin.hub() == null) messages.send(player, "fly.hub-only");
                else if (!player.hasPermission("ctf.fly")) messages.send(player, "perk.fly");
                else plugin.hub().fly(player);
            }
            case "match" -> {
                if (!sender.hasPermission("ctf.admin")) {
                    messages.send(sender, "general.no-permission");
                    return true;
                }
                matchCommand(sender, args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT));
            }
            case "pausematch", "resumematch", "endmatch" -> {
                if (!sender.hasPermission("ctf.admin")) {
                    messages.send(sender, "general.no-permission");
                    return true;
                }
                matchCommand(sender, command.getName().replace("match", ""));
            }
            case "t" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (args.length == 0) plugin.chat().toggle(player);
                else plugin.chat().teamMessage(player, String.join(" ", args));
            }
            case "tc" -> {
                if (!(sender instanceof Player player)) return players(sender);
                plugin.chat().toggle(player);
            }
            case "coins" -> {
                if (!(sender instanceof Player player)) return players(sender);
                messages.send(player, "coins.balance", Messages.arg("coins", plugin.kits().coins(player.getUniqueId())));
            }
            case "tptohill" -> tpToHill(sender, args);
            case "ctf" -> admin(sender, args);
            case "rate" -> {
                if (!(sender instanceof Player player)) return players(sender);
                rate(player, args);
            }
            case "ctfmap" -> {
                if (editor == null) messages.send(sender, "editor.game-only");
                else editor.run(sender, args);
            }
            default -> { }
        }
        return true;
    }

    /**
     * /coinsadmin add|remove|set|reset <player> [amount]: for staff and for the store (run from the console).
     * The player is found among who is online, then by name among everyone who has played CTF, so it works for
     * someone who is offline or on the other CTF server.
     */
    private void coinsAdmin(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean reset = action.equals("reset");
        if (!List.of("add", "give", "remove", "take", "set", "reset").contains(action) || args.length < (reset ? 2 : 3) || plugin.database() == null) {
            messages.send(sender, plugin.database() == null ? "general.no-database" : "admin.coinsadmin-usage");
            return;
        }
        int amount = 0;
        if (!reset) {
            try {
                amount = Integer.parseInt(args[2].replace(",", ""));
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount < 0) {
                messages.send(sender, "admin.coinsadmin-usage");
                return;
            }
        }
        String typed = args[1];
        Player online = Bukkit.getPlayerExact(typed);
        int number = amount;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.util.UUID id = online == null ? null : online.getUniqueId();
                String name = online == null ? typed : online.getName();
                if (id == null) {
                    var known = plugin.database().playerByName(typed);
                    if (known != null) {
                        id = known.getKey();
                        name = known.getValue();
                    } else {
                        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(typed);   // has been on this server, has no CTF record yet
                        if (cached != null) {
                            id = cached.getUniqueId();
                            if (cached.getName() != null) name = cached.getName();
                        }
                    }
                }
                if (id == null && (action.equals("add") || action.equals("give")) && number > 0
                        && typed.matches("\\.?[A-Za-z0-9_]{2,20}")) {
                    // never been on CTF (bought in the store first): the coins wait and are paid when they join
                    plugin.pendingCoins().hold(typed, number);
                    Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "admin.coinsadmin-held", Messages.arg("player", typed),
                            Messages.arg("coins", number)));
                    return;
                }
                if (id == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "admin.coinsadmin-unknown", Messages.arg("player", typed)));
                    return;
                }
                int balance = switch (action) {
                    case "add", "give" -> plugin.database().addCoins(id, name, number);
                    case "remove", "take" -> plugin.database().addCoins(id, name, -number);
                    case "set" -> plugin.database().setCoins(id, name, number);
                    default -> plugin.database().setCoins(id, name, 0);
                };
                java.util.UUID who = id;
                String shown = name;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.kits().setCoins(who, balance);
                    messages.send(sender, "admin.coins-done", Messages.arg("player", shown), Messages.arg("coins", balance));
                    Player there = Bukkit.getPlayer(who);
                    if (there != null && (action.equals("add") || action.equals("give")) && number > 0) {
                        messages.send(there, "coins.received", Messages.arg("coins", number), Messages.arg("balance", com.sasha.ctf.util.Numbers.coins(balance)));
                        there.playSound(there.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                        if (plugin.hub() != null) plugin.hub().board().update(there);
                    }
                });
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.no-database"));
            }
        });
    }

    private void admin(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        boolean hub = plugin.ctfConfig().hub();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (!hub) messages.send(sender, "admin.hub-only");
                else messages.send(sender, plugin.hub().forceStart() ? "admin.starting" : "admin.cant-start");
            }
            case "stop", "end" -> {
                if (hub) messages.send(sender, plugin.hub().endMatch() ? "admin.end-sent" : "admin.no-match");
                else messages.send(sender, plugin.game().stopMatch() ? "admin.stopped" : "admin.no-match");
            }
            case "pause", "resume" -> matchCommand(sender, sub);
            case "setmap" -> setMap(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "kit" -> kitAdmin(sender, args);
            case "status" -> {
                if (hub) {
                    var row = plugin.hub().row();
                    messages.send(sender, "admin.status-hub", Messages.arg("phase", plugin.hub().phase().name()),
                            Messages.arg("state", row.state().name()), Messages.arg("map", String.valueOf(row.map())),
                            Messages.arg("database", plugin.database() == null ? "not connected" : "connected"));
                } else {
                    var match = plugin.game().match();
                    messages.send(sender, "admin.status-game", Messages.arg("state", plugin.game().state().name()),
                            Messages.arg("map", match == null ? "-" : match.map().name),
                            Messages.arg("mode", match == null ? "-" : match.mode().display()),
                            Messages.arg("database", plugin.database() == null ? "not connected" : "connected"));
                }
            }
            case "sethubspawn" -> {
                if (!(sender instanceof Player player)) {
                    players(sender);
                    return;
                }
                if (!hub) {
                    messages.send(sender, "admin.hub-only");
                    return;
                }
                plugin.getConfig().set("hub.spawn", Spot.of(player.getLocation()).serialize());
                plugin.saveConfig();
                plugin.reloadSettings();
                messages.send(sender, "admin.hub-spawn-set");
            }
            case "reload" -> {
                plugin.reloadSettings();
                messages.send(sender, "admin.reloaded");
            }
            case "coins" -> {
                // /ctf coins <player> <amount>  (negative takes coins away)
                if (args.length < 3 || plugin.database() == null) {
                    messages.send(sender, "admin.coins-usage");
                    return;
                }
                @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    messages.send(sender, "admin.coins-usage");
                    return;
                }
                String name = target.getName() == null ? args[1] : target.getName();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        int balance = plugin.database().addCoins(target.getUniqueId(), name, amount);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.kits().setCoins(target.getUniqueId(), balance);
                            messages.send(sender, "admin.coins-done", Messages.arg("player", name), Messages.arg("coins", balance));
                        });
                    } catch (java.sql.SQLException e) {
                        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "general.no-database"));
                    }
                });
            }
            default -> messages.send(sender, "admin.help");
        }
    }

    /** /setmap <map> [mode] (also /ctf setmap): that map wins the lobby's next vote. /setmap off: the vote decides again. */
    private void setMap(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (plugin.hub() == null) {
            messages.send(sender, "admin.hub-only");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "admin.setmap-usage");
            return;
        }
        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("vote")) {
            messages.send(sender, plugin.hub().clearNext() ? "admin.setmap-cleared" : "admin.setmap-none");
            return;
        }
        if (plugin.hub().locked() || plugin.hub().phase() == com.sasha.ctf.hub.HubManager.Phase.STARTING) {
            messages.send(sender, "admin.setmap-too-late");
            return;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        var mode = args.length >= 2 ? com.sasha.ctf.model.Mode.parse(args[1]).orElse(null) : null;
        if (args.length >= 2 && mode == null) {
            messages.send(sender, "admin.setmap-usage");
        } else if (plugin.hub().setNext(id, mode)) {
            var forced = plugin.hub().forced();
            // everyone hears it, and the vote ends now: the gate opens and the countdown starts
            messages.broadcast("hub.map-set", Messages.arg("player", sender instanceof Player p ? p.getName() : "Staff"),
                    Messages.arg("map", forced.mapName()), Messages.arg("mode", forced.mode().display()));
            plugin.hub().endVoting();
        } else {
            messages.send(sender, "admin.setmap-unknown");
        }
    }

    /** The modes a map can be played in (for tab completion), or every mode. */
    private List<String> modesOf(String mapId) {
        List<String> modes = new ArrayList<>();
        var map = plugin.hub() == null ? null : plugin.hub().maps().get(mapId.toLowerCase(Locale.ROOT));
        for (var mode : com.sasha.ctf.model.Mode.values()) if (map == null || map.getValue().contains(mode)) modes.add(mode.name().toLowerCase(Locale.ROOT));
        return modes;
    }

    private List<String> setMapOptions(String[] args) {
        if (args.length == 1) {
            List<String> maps = plugin.hub() == null ? new ArrayList<>() : new ArrayList<>(plugin.hub().maps().keySet());
            maps.add("off");
            return maps;
        }
        return args.length == 2 ? modesOf(args[0]) : List.of();
    }

    /** /coinsadmin add|remove|set|reset <player> [amount]: online players' names, then a few amounts. */
    private List<String> coinsAdminOptions(String[] args) {
        if (args.length == 1) return List.of("add", "remove", "set", "reset");
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return names;
        }
        if (args.length == 3 && !args[0].equalsIgnoreCase("reset")) return List.of("100", "500", "1000", "1500", "4000");
        return List.of();
    }

    /** /tptohill [hill]: staff go to a hill of the King of the Hill match on this server (no name: the list). */
    private void tpToHill(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return;
        }
        var match = plugin.game() == null ? null : plugin.game().match();
        if (match == null || !(match.rules() instanceof com.sasha.ctf.game.HillRules hills)) {
            messages.send(sender, "admin.tptohill-none");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "admin.tptohill-list", Messages.arg("hills", String.join(", ", hills.pointNames())));
            return;
        }
        String wanted = String.join(" ", args);
        org.bukkit.Location spot = hills.pointSpot(wanted);
        if (spot == null) {
            messages.send(sender, "admin.tptohill-unknown", Messages.arg("hill", wanted), Messages.arg("hills", String.join(", ", hills.pointNames())));
            return;
        }
        spot.setYaw(player.getLocation().getYaw());
        spot.setPitch(player.getLocation().getPitch());
        player.teleport(spot);
        String key = wanted.replace(" ", "").toLowerCase(Locale.ROOT), shown = wanted;
        List<String> names = hills.pointNames();
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).replace(" ", "").toLowerCase(Locale.ROOT).equals(key) || String.valueOf(i + 1).equals(key)) shown = names.get(i);
        }
        messages.send(sender, "admin.tptohill-done", Messages.arg("hill", shown));
    }

    /** /match pause|resume|end, also /pausematch, /resumematch and /endmatch. Works from the hub too. */
    private void matchCommand(CommandSender sender, String action) {
        Messages messages = plugin.messages();
        boolean hub = plugin.ctfConfig().hub();
        switch (action) {
            case "pause", "resume" -> {
                boolean pause = action.equals("pause");
                if (hub) {
                    if (plugin.hub().phase() == com.sasha.ctf.hub.HubManager.Phase.IN_MATCH) {
                        messages.send(sender, plugin.hub().matchCommand(action + ":" + sender.getName()) ? (pause ? "admin.match-pause-sent" : "admin.match-resume-sent") : "admin.no-match");
                    } else {
                        // no match on: pause or resume the lobby's countdown instead
                        messages.send(sender, plugin.hub().pause(pause) ? (pause ? "admin.paused" : "admin.resumed") : pause ? "admin.lobby-already-paused" : "admin.lobby-not-paused");
                    }
                } else {
                    var current = plugin.game().match();
                    if (current != null && current.live() && current.paused() == pause) {
                        messages.send(sender, pause ? "admin.match-already-paused" : "admin.match-not-paused");
                        return;
                    }
                    boolean done = plugin.game().pauseMatch(pause, sender instanceof org.bukkit.entity.Player ? sender.getName() : null);
                    messages.send(sender, !done ? "admin.no-match" : pause ? "admin.match-paused" : "admin.match-resumed");
                }
            }
            case "end", "stop" -> {
                if (hub) messages.send(sender, plugin.hub().endMatch() ? "admin.end-sent" : "admin.no-match");
                else messages.send(sender, plugin.game().stopMatch() ? "admin.stopped" : "admin.no-match");
            }
            case "start" -> {
                if (!hub) messages.send(sender, "admin.hub-only");
                else messages.send(sender, plugin.hub().forceStart() ? "admin.starting" : "admin.cant-start");
            }
            default -> messages.send(sender, "admin.match-help");
        }
    }

    /** /rate 1-5: rates the map of your last match (one rating per player per map; rating again changes it). */
    private void rate(Player player, String[] args) {
        Messages messages = plugin.messages();
        String mapId;
        String mapName;
        if (plugin.ctfConfig().hub()) {
            mapId = plugin.hub().lastPlayed();
            mapName = mapId == null ? null : plugin.hub().mapName(mapId);
        } else {
            var match = plugin.game().match();
            mapId = match == null ? null : match.map().id();
            mapName = match == null ? null : match.map().name;
        }
        if (mapId == null) {
            messages.send(player, "rate.nothing");
            return;
        }
        int stars;
        try {
            stars = args.length == 0 ? 0 : Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            stars = 0;
        }
        if (stars < 1 || stars > 5) {
            messages.send(player, "rate.prompt", Messages.arg("map", mapName));
            return;
        }
        if (plugin.database() == null) {
            messages.send(player, "general.no-database");
            return;
        }
        int rating = stars;
        String id = mapId;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean first = plugin.database().rate(player.getUniqueId(), id, rating);
                // the first rating of each map pays coins.first-rating (20)
                int reward = first ? plugin.getConfig().getInt("coins.first-rating", 20) : 0;
                int balance = reward > 0 ? plugin.database().addCoins(player.getUniqueId(), player.getName(), reward) : -1;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(player, "rate.done", Messages.arg("map", mapName), Messages.arg("stars", com.sasha.ctf.hub.HubItems.stars(rating)));
                    if (reward > 0 && player.isOnline()) {
                        plugin.kits().setCoins(player.getUniqueId(), balance);
                        messages.send(player, "rate.first-coins", Messages.arg("coins", reward), Messages.arg("map", mapName));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                        if (plugin.hub() != null) plugin.hub().board().update(player);
                    }
                    if (plugin.hub() != null) plugin.hub().refreshRatings();
                });
            } catch (java.sql.SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "general.no-database"));
            }
        });
    }

    /** /ctf kit grant|remove <player> <kit>, /ctf kit disable|enable <kit>. */
    private void kitAdmin(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (plugin.database() == null) {
            messages.send(sender, "general.no-database");
            return;
        }
        switch (action) {
            case "grant", "give", "remove", "take" -> {
                var kit = args.length > 3 ? com.sasha.ctf.kit.Kit.parse(args[3]).orElse(null) : null;
                if (kit == null) {
                    messages.send(sender, "admin.kit-usage");
                    return;
                }
                @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                boolean give = action.equals("grant") || action.equals("give");
                plugin.kits().setOwned(target.getUniqueId(), kit, give);
                messages.send(sender, give ? "admin.kit-granted" : "admin.kit-removed", Messages.arg("kit", kit.display()),
                        Messages.arg("player", target.getName() == null ? args[2] : target.getName()));
            }
            case "disable", "enable" -> {
                var kit = args.length > 2 ? com.sasha.ctf.kit.Kit.parse(args[2]).orElse(null) : null;
                if (kit == null) {
                    messages.send(sender, "admin.kit-usage");
                    return;
                }
                if (kit == plugin.kits().defaultKit() && action.equals("disable")) {
                    messages.send(sender, "admin.kit-default", Messages.arg("kit", kit.display()));
                    return;
                }
                plugin.kits().setDisabled(kit, action.equals("disable"));
                messages.send(sender, action.equals("disable") ? "admin.kit-disabled" : "admin.kit-enabled", Messages.arg("kit", kit.display()));
            }
            default -> messages.send(sender, "admin.kit-usage");
        }
    }

    private List<String> ctfOptions(String[] args) {
        List<String> kits = new ArrayList<>();
        for (var kit : com.sasha.ctf.kit.Kit.values()) kits.add(kit.id());
        List<String> modes = List.of("ctf", "ctf4", "koth", "dtw", "dtm", "ctw");
        if (args.length == 1) return List.of("start", "end", "pause", "resume", "setmap", "status", "kit", "coins", "sethubspawn", "reload");
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "coins" -> args.length == 2 ? null : List.of();
            case "setmap" -> setMapOptions(java.util.Arrays.copyOfRange(args, 1, args.length));
            case "kit" -> args.length == 2 ? List.of("grant", "remove", "disable", "enable")
                    : args.length == 3 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("remove")) ? null
                    : args.length == 3 || args.length == 4 && !args[1].toLowerCase(Locale.ROOT).matches("disable|enable") ? kits : List.of();
            default -> List.of();
        };
    }

    private List<String> kitIds() {
        List<String> ids = new ArrayList<>();
        for (var kit : com.sasha.ctf.kit.Kit.values()) ids.add(kit.id());
        return ids;
    }

    private List<String> ownedKitIds(Player player) {
        List<String> ids = new ArrayList<>();
        for (var kit : com.sasha.ctf.kit.Kit.values()) if (plugin.kits().owns(player.getUniqueId(), kit)) ids.add(kit.id());
        return ids;
    }

    private boolean players(CommandSender sender) {
        plugin.messages().send(sender, "general.players-only");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<String> options = switch (command.getName()) {
            case "ctf" -> ctfOptions(args);
            case "ctfmap" -> editor == null ? List.of() : editor.complete(args);
            case "vote" -> args.length == 1 ? List.of("1", "2", "3") : List.of();
            case "rate" -> args.length == 1 ? List.of("1", "2", "3", "4", "5") : List.of();
            case "match" -> args.length == 1 ? List.of("pause", "resume", "end", "start") : List.of();
            case "ctfstats" -> null; // player names
            case "slowchat" -> args.length == 1 ? List.of("off", "3", "5", "10", "30") : List.of();
            case "chatfilter" -> args.length == 1 ? List.of("add", "remove", "list", "test", "reload")
                    : args.length == 2 && !args[0].equalsIgnoreCase("test") && !args[0].equalsIgnoreCase("reload") ? List.of("censor", "block") : List.of();
            case "mutechat", "unmutechat", "clearchat", "hints" -> List.of();
            case "coinsadmin" -> coinsAdminOptions(args);
            case "setmap" -> setMapOptions(args);
            case "pickteam" -> args.length == 1 ? List.of("auto", "red", "blue", "yellow", "green") : List.of();
            case "tptohill" -> args.length == 1 && plugin.game() != null && plugin.game().match() != null
                    && plugin.game().match().rules() instanceof com.sasha.ctf.game.HillRules hills
                    ? hills.pointNames().stream().map(n -> n.replace(" ", "")).toList() : List.of();
            case "previewkit" -> args.length == 1 ? kitIds() : List.of();
            case "kit" -> args.length == 1 && sender instanceof Player p ? ownedKitIds(p) : List.of();
            case "coinbooster" -> !sender.hasPermission("ctf.admin") ? List.of() : args.length == 1 ? List.of("add", "clear")
                    : args.length == 2 && args[0].equalsIgnoreCase("add") ? coinsAdminOptions(new String[] {"add", ""}) : args.length == 3 ? List.of("30", "60", "120")
                    : args.length == 4 ? List.of("2", "3") : List.of();
            default -> List.of();
        };
        if (options == null) return null;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) matches.add(option);
        return matches;
    }
}

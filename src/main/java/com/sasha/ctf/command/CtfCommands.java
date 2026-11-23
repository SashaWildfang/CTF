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
                "spectate", "match", "pausematch", "resumematch", "endmatch", "editlobby", "savelobby", "regenlobby")) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) continue;
            command.setExecutor(this);
            command.setTabCompleter(this);
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
                    messages.send(player, "kits.hub-only");
                    return true;
                }
                plugin.kits().openMenu(player);
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
                @SuppressWarnings("deprecation") OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0])
                        : sender instanceof Player self ? self : null;
                if (target == null) return players(sender);
                if (sender instanceof Player viewer) stats.open(viewer, target);
                else StatsView.show(plugin, sender, target);
            }
            case "spectate" -> {
                if (!(sender instanceof Player player)) return players(sender);
                if (hub) {
                    plugin.hub().spectate(player);
                } else {
                    var match = plugin.game().match();
                    if (match == null || match.phase() == com.sasha.ctf.game.Match.Phase.OVER) messages.send(player, "admin.no-match");
                    else if (match.spectating(player.getUniqueId())) match.playAgain(player);
                    else match.leaveToSpectate(player);
                }
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
            case "setmap" -> {
                if (!hub) {
                    messages.send(sender, "admin.hub-only");
                    return;
                }
                var mode = args.length >= 3 ? com.sasha.ctf.model.Mode.parse(args[2]).orElse(null) : null;
                if (args.length < 3 || mode == null) {
                    messages.send(sender, "admin.setmap-usage");
                } else if (plugin.hub().setNext(args[1].toLowerCase(Locale.ROOT), mode)) {
                    messages.send(sender, "admin.setmap-done", Messages.arg("map", plugin.hub().mapName(args[1].toLowerCase(Locale.ROOT))), Messages.arg("mode", mode.display()));
                } else {
                    messages.send(sender, "admin.setmap-unknown");
                }
            }
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

    /** /match pause|resume|end, also /pausematch, /resumematch and /endmatch. Works from the hub too. */
    private void matchCommand(CommandSender sender, String action) {
        Messages messages = plugin.messages();
        boolean hub = plugin.ctfConfig().hub();
        switch (action) {
            case "pause", "resume" -> {
                boolean pause = action.equals("pause");
                if (hub) {
                    if (plugin.hub().phase() == com.sasha.ctf.hub.HubManager.Phase.IN_MATCH) {
                        messages.send(sender, plugin.hub().matchCommand(action) ? (pause ? "admin.match-pause-sent" : "admin.match-resume-sent") : "admin.no-match");
                    } else {
                        // no match on: pause or resume the lobby's countdown instead
                        messages.send(sender, plugin.hub().pause(pause) ? (pause ? "admin.paused" : "admin.resumed") : "admin.already");
                    }
                } else {
                    messages.send(sender, plugin.game().pauseMatch(pause) ? (pause ? "admin.match-paused" : "admin.match-resumed") : "admin.no-match");
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
                plugin.database().rate(player.getUniqueId(), id, rating);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(player, "rate.done", Messages.arg("map", mapName), Messages.arg("stars", com.sasha.ctf.hub.HubItems.stars(rating)));
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
        List<String> modes = List.of("ctf", "ctf4", "koth", "dtw");
        if (args.length == 1) return List.of("start", "end", "pause", "resume", "setmap", "status", "kit", "coins", "sethubspawn", "reload");
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "coins" -> args.length == 2 ? null : List.of();
            case "setmap" -> args.length == 2 && plugin.hub() != null ? new ArrayList<>(plugin.hub().maps().keySet()) : args.length == 3 ? modes : List.of();
            case "kit" -> args.length == 2 ? List.of("grant", "remove", "disable", "enable")
                    : args.length == 3 && (args[1].equalsIgnoreCase("grant") || args[1].equalsIgnoreCase("remove")) ? null
                    : args.length == 3 || args.length == 4 && !args[1].toLowerCase(Locale.ROOT).matches("disable|enable") ? kits : List.of();
            default -> List.of();
        };
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
            default -> List.of();
        };
        if (options == null) return null;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(last)) matches.add(option);
        return matches;
    }
}

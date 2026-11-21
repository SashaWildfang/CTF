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

    public CtfCommands(FractalCTF plugin) {
        this.plugin = plugin;
        this.editor = plugin.ctfConfig().hub() ? null : new MapEditor(plugin);
    }

    public void register() {
        for (String name : List.of("kit", "vote", "join", "leave", "ctfstats", "ctf", "ctfmap")) {
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
            case "kit" -> {
                if (!(sender instanceof Player player)) return players(sender);
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
                if (args.length > 0) {
                    @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                    StatsView.show(plugin, sender, target);
                } else if (sender instanceof Player player) {
                    StatsView.show(plugin, sender, player);
                } else {
                    return players(sender);
                }
            }
            case "ctf" -> admin(sender, args);
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
            case "stop" -> {
                if (hub) messages.send(sender, "admin.game-only");
                else messages.send(sender, plugin.game().stopMatch() ? "admin.stopped" : "admin.no-match");
            }
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
            default -> messages.send(sender, "admin.help");
        }
    }

    private boolean players(CommandSender sender) {
        plugin.messages().send(sender, "general.players-only");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<String> options = switch (command.getName()) {
            case "ctf" -> args.length == 1 ? List.of("start", "stop", "status", "sethubspawn", "reload") : List.of();
            case "ctfmap" -> editor == null ? List.of() : editor.complete(args);
            case "vote" -> args.length == 1 ? List.of("1", "2", "3") : List.of();
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

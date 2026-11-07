package com.bearnation.ctf.messaging;

import com.bearnation.ctf.CTF;
import com.bearnation.ctf.bus.BukkitDispatch;
import com.bearnation.ctf.bus.MessageBus;
import com.google.gson.*;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Subscribes to NATS (bn.ctf) and routes messages to CTF handlers.
 * Also answers "ping" and "help_request" for Cardinal /help aggregation.
 */
public final class CtfMessaging {

    private static final String SUBJECT = "bn.ctf";

    private final CTF plugin;
    private final MessageBus bus;
    private final BukkitDispatch dispatch;
    private final RegionSetupListener regionSetup;
    private final SchematicPasteListener schematicPaste;

    /** Optional shard/server id (e.g. "ctf-1"). If null, accepts all messages. */
    private final String shardId;

    // --- added for help_request handling ---
    private CommandMap commandMap; // cached

    public CtfMessaging(
            CTF plugin,
            MessageBus bus,
            RegionSetupListener regionSetup,
            SchematicPasteListener schematicPaste,
            String shardId // pass your server id or null
    ) {
        this.plugin = plugin;
        this.bus = bus;
        this.dispatch = new BukkitDispatch(plugin);
        this.regionSetup = regionSetup;
        this.schematicPaste = schematicPaste;
        this.shardId = shardId;
    }

    public void subscribe() {
        bus.subscribe(SUBJECT, msg -> {
            JsonObject root = JsonParser.parseString(msg).getAsJsonObject();

            // Optional target filter: only handle if broadcast or matches this shardId
            if (root.has("target") && shardId != null) {
                String target = root.get("target").getAsString();
                if (!"broadcast".equalsIgnoreCase(target) && !shardId.equalsIgnoreCase(target)) {
                    return; // not for us
                }
            }

            if (!root.has("type")) return;
            String type = root.get("type").getAsString();
            JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                    ? root.getAsJsonObject("payload")
                    : new JsonObject();

            switch (type) {
                /* ===================== CONNECTIVITY / HELP ===================== */
                case "ping" -> dispatch.main(() -> {
                    String replySubject = getString(root, "reply", "bn.help.reply");
                    String corr = getString(root, "corr", "nocorr-" + System.currentTimeMillis());

                    JsonObject pong = new JsonObject();
                    pong.addProperty("type", "pong");
                    pong.addProperty("corr", corr);
                    pong.add("commands", new JsonArray()); // not used

                    bus.publish(replySubject, pong.toString());
                });

                case "help_request" -> dispatch.main(() -> {
                    String replySubject = getString(root, "reply", "bn.help.reply");
                    String corr = getString(root, "corr", "nocorr-" + System.currentTimeMillis());

                    UUID playerId = getUuid(root, "player");
                    Player p = (playerId != null) ? Bukkit.getPlayer(playerId) : null;

                    List<Cmd> cmds = collectServerCommands(p);
                    JsonArray arr = new JsonArray();
                    for (Cmd c : cmds) {
                        JsonObject o = new JsonObject();
                        o.addProperty("name", c.name());
                        o.addProperty("desc", c.desc() == null || c.desc().isBlank() ? "No description" : c.desc());
                        arr.add(o);
                    }

                    JsonObject reply = new JsonObject();
                    reply.addProperty("type", "help_response");
                    reply.addProperty("corr", corr);
                    reply.add("commands", arr);

                    bus.publish(replySubject, reply.toString());
                });

                /* ===================== EXISTING HANDLERS ===================== */
                case "region_setup" -> dispatch.main(() -> {
                    try {
                        regionSetup.handle(payload);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[CTF] region_setup failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                case "schematic_paste" -> dispatch.main(() -> {
                    try {
                        schematicPaste.handle(payload);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[CTF] schematic_paste failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

                case "world_reset_start" -> dispatch.main(plugin::startReset);
                case "world_reset_finish" -> dispatch.main(plugin::finishReset);

                case "phase" -> dispatch.main(() -> {
                    try {
                        String phase = payload.get("phase").getAsString();
                        plugin.setCurrentPhase(phase);
                    } catch (Exception ignored) {}
                });

                default -> {
                    // Unknown type; ignore or log lightly
                    // plugin.getLogger().fine("[CTF] Unhandled type on bn.ctf: " + type);
                }
            }
        });
    }

    /* ===================== helpers ===================== */

    private void ensureCommandMap() {
        if (commandMap != null) return;
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            this.commandMap = (CommandMap) f.get(Bukkit.getServer());
        } catch (Throwable t) {
            plugin.getLogger().warning("[CTF Messaging] Could not access CommandMap: " + t);
        }
    }

    private record Cmd(String name, String desc) {}

    /** Collect only commands the player can run; hide namespaced; collapse aliases; sort. */
    private List<Cmd> collectServerCommands(Player p) {
        ensureCommandMap();
        if (commandMap == null) return List.of();

        Map<String, Command> known = Map.of();
        try {
            Field knownF = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownF.setAccessible(true);
            //noinspection unchecked
            known = (Map<String, Command>) knownF.get(commandMap);
        } catch (Throwable ignored) {}

        if (known.isEmpty()) return List.of();

        Map<Command, Set<String>> byCmd = new IdentityHashMap<>();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            String alias = e.getKey();
            Command cmd = e.getValue();
            if (alias.contains(":")) continue; // hide namespaced aliases (e.g., "minecraft:tp")

            boolean allowed = (p != null) ? cmd.testPermissionSilent(p) : true; // fallback to include when player unknown
            if (!allowed) continue;

            byCmd.computeIfAbsent(cmd, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(alias);
        }

        // If permission filtering yielded nothing, show all non-namespaced as a fallback
        if (byCmd.isEmpty()) {
            for (Map.Entry<String, Command> e : known.entrySet()) {
                String alias = e.getKey();
                if (alias.contains(":")) continue;
                byCmd.computeIfAbsent(e.getValue(), k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(alias);
            }
        }

        List<Cmd> out = new ArrayList<>();
        for (Map.Entry<Command, Set<String>> e : byCmd.entrySet()) {
            Command c = e.getKey();
            Set<String> aliases = e.getValue();

            // Nice display name: first non-namespaced alias
            String display = aliases.stream()
                    .filter(a -> !a.contains(":"))
                    .findFirst()
                    .orElseGet(() -> aliases.iterator().next());

            String desc = c.getDescription();
            if ((desc == null || desc.isBlank()) && c instanceof PluginCommand pc) {
                desc = pc.getDescription();
            }
            out.add(new Cmd(display, (desc == null || desc.isBlank()) ? "No description" : desc));
        }

        out.sort(Comparator.comparing(Cmd::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static UUID getUuid(JsonObject o, String key) {
        try {
            String s = getString(o, key, "");
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}

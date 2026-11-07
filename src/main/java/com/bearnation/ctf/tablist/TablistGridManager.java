package com.bearnation.ctf.tablist;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** 4x20 tab grid with bottom-row spectators per column. All fakes use DetectivePlapper's skin. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class TablistGridManager {

    // ====== layout ======
    private static final int COLS  = 4;
    private static final int ROWS  = 20;
    private static final int TOTAL_SLOTS = COLS * ROWS;

    // ====== header/footer ======
    private static final String HEADER_TITLE = "§bLoading Map by …";
    private static final String FOOTER_BRAND = "§f§lBEAR NATION §7– §fplay.mcbearnation.net";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ====== fallback uniform fake skin (used only if DetectivePlapper not online) ======
    private static final String FALLBACK_HASH = "07e046ab2b4c4ffa";
    private static final String FALLBACK_TEXTURE_BASE64 =
            Base64.getEncoder().encodeToString((
                    "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" +
                            FALLBACK_HASH + "\"}}}"
            ).getBytes(StandardCharsets.UTF_8));

    // ====== state ======
    private final Map<UUID, List<UUID>> placeholders = new HashMap<>();
    private final Map<UUID, List<UUID>> spectatorFakes = new HashMap<>();
    private final Map<UUID, Map<UUID, String>> spectatorFakeLabels = new HashMap<>();
    private final Map<UUID, Map<UUID, String>> spectatorFakeSortNames = new HashMap<>();
    private final Map<UUID, List<Integer>> viewerIndices = new HashMap<>();

    private final Set<UUID> spectators = new HashSet<>();
    private volatile boolean gridDisabled = false;
    private String tabClock = "00:00";

    // NMS reflection cache
    private Class<?> clsPktUpdate, clsEntry, clsAction, clsComponent, clsGameType, clsGameProfile, clsPropertyMap, clsProperty;
    private Constructor<?> ctorPktUpdate, ctorEntry, ctorGameProfile, ctorProperty, ctorPktRemove;
    private Object gmAdventure;
    private ProtocolManager pm;

    public TablistGridManager() {
        try {
            clsPktUpdate   = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            clsComponent   = Class.forName("net.minecraft.network.chat.Component");
            clsGameType    = Class.forName("net.minecraft.world.level.GameType");
            clsGameProfile = Class.forName("com.mojang.authlib.GameProfile");
            try { clsPropertyMap = Class.forName("com.mojang.authlib.properties.PropertyMap"); } catch (Throwable ignored) {}
            try { clsProperty   = Class.forName("com.mojang.authlib.properties.Property");   } catch (Throwable ignored) {}

            for (Class<?> c : clsPktUpdate.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Entry"))  clsEntry = c;
                if (c.getSimpleName().equals("Action")) clsAction = c;
            }
            if (clsEntry == null || clsAction == null) throw new ClassNotFoundException("Entry/Action not found");

            for (Constructor<?> c : clsPktUpdate.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 2 && EnumSet.class.isAssignableFrom(p[0]) && Collection.class.isAssignableFrom(p[1])) {
                    c.setAccessible(true);
                    ctorPktUpdate = c;
                    break;
                }
            }
            if (ctorPktUpdate == null) throw new NoSuchMethodException("ClientboundPlayerInfoUpdatePacket(EnumSet, Collection)");

            gmAdventure = Enum.valueOf((Class) clsGameType, "ADVENTURE");

            ctorGameProfile = clsGameProfile.getConstructor(UUID.class, String.class);
            ctorGameProfile.setAccessible(true);

            if (clsProperty != null) {
                try {
                    ctorProperty = clsProperty.getConstructor(String.class, String.class);
                    ctorProperty.setAccessible(true);
                } catch (Throwable ignored) {}
            }

            try {
                Class<?> clsPktRemove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
                for (Constructor<?> c : clsPktRemove.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length == 1 && Collection.class.isAssignableFrom(p[0])) {
                        c.setAccessible(true);
                        ctorPktRemove = c;
                        break;
                    }
                }
            } catch (Throwable ignored) { /* use ProtocolLib */ }

            if (ctorPktRemove == null) pm = ProtocolLibrary.getProtocolManager();

            detectEntryCtor();
            Bukkit.getLogger().info("[CTF] Tab grid ready.");
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[CTF] Tab grid disabled: failed to resolve NMS.", t);
            gridDisabled = true;
        }
    }

    /* ====================== Public API ====================== */

    public void enableFor(Player viewer) {
        if (gridDisabled) return;
        UUID v = viewer.getUniqueId();
        placeholders.putIfAbsent(v, new ArrayList<>());
        spectatorFakes.putIfAbsent(v, new ArrayList<>());
        spectatorFakeLabels.putIfAbsent(v, new HashMap<>());
        spectatorFakeSortNames.putIfAbsent(v, new HashMap<>());
        viewerIndices.putIfAbsent(v, new ArrayList<>());

        ensureMainScoreboard(viewer);
        applyTeamSortForRealPlayers();

        Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("CTF"), () -> {
            rebuildFor(viewer);
            applyHeaderFooter(viewer);
        }, 10L);
    }

    public void disableFor(Player viewer) {
        if (gridDisabled) return;
        UUID v = viewer.getUniqueId();
        List<UUID> fakes = placeholders.remove(v);
        List<UUID> specs = spectatorFakes.remove(v);
        spectatorFakeLabels.remove(v);
        spectatorFakeSortNames.remove(v);
        viewerIndices.remove(v);

        List<UUID> toRemove = new ArrayList<>();
        if (fakes != null) toRemove.addAll(fakes);
        if (specs != null) toRemove.addAll(specs);
        if (!toRemove.isEmpty()) sendRemoveFakes(viewer, toRemove);
    }

    public void rebuildAll() {
        if (gridDisabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ensureMainScoreboard(p);
            rebuildFor(p);
        }
        applyTeamSortForRealPlayers();
    }

    public void setSpectating(UUID playerId, boolean isSpectator) {
        if (isSpectator) spectators.add(playerId); else spectators.remove(playerId);
        for (Player p : Bukkit.getOnlinePlayers()) rebuildFor(p);
        applyTeamSortForRealPlayers();
    }

    public void setTabClock(String mmss) {
        this.tabClock = mmss;
        for (Player p : Bukkit.getOnlinePlayers()) applyHeaderFooter(p);
    }

    /* ====================== Core grid ====================== */

    private void rebuildFor(Player viewer) {
        UUID v = viewer.getUniqueId();

        // clear spectator fakes for this viewer
        List<UUID> oldSpecFakes = spectatorFakes.getOrDefault(v, Collections.emptyList());
        if (!oldSpecFakes.isEmpty()) sendRemoveFakes(viewer, new ArrayList<>(oldSpecFakes));
        spectatorFakes.put(v, new ArrayList<>());
        spectatorFakeLabels.put(v, new HashMap<>());
        spectatorFakeSortNames.put(v, new HashMap<>());

        // stable list of online spectators
        List<Player> onlineSpecs = new ArrayList<>();
        for (UUID id : spectators) {
            Player sp = Bukkit.getPlayer(id);
            if (sp != null && sp.isOnline()) onlineSpecs.add(sp);
        }
        onlineSpecs.sort(Comparator.comparing(Player::getName));

        // Reserve absolute indices for spectators: bottom cells L->R then stack upward.
        Set<Integer> reserved = new HashSet<>();
        int placedSpecs = 0;
        for (int s = 0; s < onlineSpecs.size(); s++) {
            int col = s % COLS;
            int depth = s / COLS;
            int row = ROWS - 1 - depth;
            if (row < 0) break; // no more vertical room
            int targetIndex = col * ROWS + row;
            reserved.add(targetIndex);

            Player sp = onlineSpecs.get(s);
            UUID fakeId = UUID.randomUUID();
            spectatorFakes.get(v).add(fakeId);
            spectatorFakeLabels.get(v).put(fakeId, "§b" + sp.getName());
            spectatorFakeSortNames.get(v).put(fakeId, techNameBetween(targetIndex));
            placedSpecs++;
        }

        // hide/show real players appropriately for this viewer
        refreshListedFlags(viewer, onlineSpecs.subList(0, placedSpecs));

        // Count non-spectator real players
        int nonSpectReal = (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !spectators.contains(p.getUniqueId()))
                .count();

        // Keep total rows at 20: placeholders = 80 - nonSpectReal - placedSpecs
        int need = Math.max(0, TOTAL_SLOTS - nonSpectReal - placedSpecs);

        // choose the exact absolute indices to fill with placeholders (skip reserved spectator rows)
        List<Integer> placeholderIndices = new ArrayList<>(need);
        for (int idx = 0; idx < TOTAL_SLOTS && placeholderIndices.size() < need; idx++) {
            if (!reserved.contains(idx)) placeholderIndices.add(idx);
        }

        // size/resize placeholder IDs
        List<UUID> cur = placeholders.computeIfAbsent(v, k -> new ArrayList<>());
        if (need > cur.size()) {
            for (int i = cur.size(); i < need; i++) cur.add(forceDefaultVariant(UUID.randomUUID(), 0));
        } else if (need < cur.size()) {
            List<UUID> toRemove = new ArrayList<>(cur.subList(need, cur.size()));
            cur.subList(need, cur.size()).clear();
            if (!toRemove.isEmpty()) sendRemoveFakes(viewer, toRemove);
        }

        viewerIndices.put(v, placeholderIndices);

        sendAddOrUpdate(viewer);
        applyHeaderFooter(viewer);
    }

    private void sendAddOrUpdate(Player viewer) {
        if (gridDisabled) return;
        try {
            Object pkt1 = buildUpdate(viewer, false);
            sendRaw(viewer, pkt1);

            Object pkt2 = buildUpdate(viewer, true);
            sendRaw(viewer, pkt2);
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[CTF] Disabling tab grid: NMS send failed.", t);
            gridDisabled = true;
        }
    }

    private void sendRemoveFakes(Player viewer, List<UUID> ids) {
        try {
            if (ctorPktRemove != null) {
                Object pkt = ctorPktRemove.newInstance(ids);
                sendRaw(viewer, pkt);
            } else if (pm != null) {
                PacketContainer remove = pm.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                remove.getUUIDLists().write(0, ids);
                pm.sendServerPacket(viewer, remove);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[CTF] Tab grid: remove failed.", t);
        }
    }

    /* ====================== Builders ====================== */

    // Sort key for a specific absolute index 0..79 (A00..A79...)
    private static String techNameForAbsIndex(UUID id, int absIndex) {
        String hex = id.toString().replace("-", "");
        String prefix = "A" + (absIndex < 10 ? "0" + absIndex : (absIndex < 100 ? "" + absIndex : "99"));
        return prefix + hex.substring(0, 13);
    }

    // A key that sorts *at* absolute index i, by sitting after i-1 and before i+1
    private static String techNameBetween(int targetAbsIndex) {
        int before = Math.max(0, targetAbsIndex - 1);
        String prefix = "A" + (before < 10 ? "0" + before : (before < 100 ? "" + before : "99"));
        return prefix + "zxxxxxxxxxxxx"; // after A<before>*, before A<before+1>*
    }

    private String playerCountLabel() {
        int now = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        return "§7 " + now + "/" + max + " §ePlayers";
    }

    /** Build a packet for (placeholders @ exact indices + spectator fakes). */
    private Object buildUpdate(Player viewer, boolean includeTweaks) throws Exception {
        UUID v = viewer.getUniqueId();

        EnumSet actions = EnumSet.noneOf((Class) clsAction);
        actions.add(Enum.valueOf((Class) clsAction, "ADD_PLAYER"));
        actions.add(Enum.valueOf((Class) clsAction, "UPDATE_LISTED"));
        if (includeTweaks) {
            actions.add(Enum.valueOf((Class) clsAction, "UPDATE_LATENCY"));
            actions.add(Enum.valueOf((Class) clsAction, "UPDATE_GAME_MODE"));
            actions.add(Enum.valueOf((Class) clsAction, "UPDATE_DISPLAY_NAME"));
        }

        List<Object> entries = new ArrayList<>();

        // 1) Placeholders at their absolute indices
        List<Integer> idxs = viewerIndices.getOrDefault(v, Collections.emptyList());
        List<UUID> ids = placeholders.getOrDefault(v, Collections.emptyList());
        for (int i = 0; i < Math.min(idxs.size(), ids.size()); i++) {
            int absIndex = idxs.get(i);
            UUID id = ids.get(i);
            String sortName = techNameForAbsIndex(id, absIndex);
            String visible  = (absIndex == 0) ? playerCountLabel() : " ";
            entries.add(newEntry(id, sortName, visible, true));
        }

        // 2) Spectator bottom cells
        List<UUID> specIds = spectatorFakes.getOrDefault(v, Collections.emptyList());
        Map<UUID, String> labels   = spectatorFakeLabels.getOrDefault(v, Collections.emptyMap());
        Map<UUID, String> sortName = spectatorFakeSortNames.getOrDefault(v, Collections.emptyMap());
        for (UUID id : specIds) {
            entries.add(newEntry(id, sortName.get(id), labels.get(id), true));
        }

        return ctorPktUpdate.newInstance(actions, entries);
    }

    /** Build an Entry with DetectivePlapper's skin (or fallback) and a display name. */
    private Object newEntry(UUID id, String profileName, String visibleText, boolean listed) throws Exception {
        Class<?>[] p = ctorEntry.getParameterTypes();
        Object[] args = new Object[p.length];

        // NOTE: keep profileName (Axx...) for ordering; we only copy the skin property.
        Object gp = ctorGameProfile.newInstance(id, profileName);

        // ---- inject "DetectivePlapper" skin into this GameProfile ----
        injectDetectiveSkinIntoProfile(gp);

        Object display = toComponent(visibleText);
        int latency = 9999; // 1 bar

        for (int i = 0; i < p.length; i++) {
            Class<?> t = p[i];
            if (t == UUID.class)                         args[i] = id;
            else if (t == String.class)                  args[i] = profileName;
            else if (t == int.class)                     args[i] = latency;
            else if (t == boolean.class)                 args[i] = listed;
            else if (t.isAssignableFrom(clsGameProfile)) args[i] = gp;
            else if (t.isAssignableFrom(clsGameType))    args[i] = gmAdventure;
            else if (t.isAssignableFrom(clsComponent))   args[i] = display;
            else if (clsPropertyMap != null && t.isAssignableFrom(clsPropertyMap))
                args[i] = clsPropertyMap.getConstructor().newInstance();
            else if (t.getName().equals("java.util.Optional"))
                args[i] = Optional.empty();
            else
                args[i] = null;
        }
        return ctorEntry.newInstance(args);
    }

    /** Copies DetectivePlapper's live textures property if possible; otherwise uses the fallback base64. */
    private void injectDetectiveSkinIntoProfile(Object targetGameProfile) {
        try {
            // Get target PropertyMap (Multimap-like)
            Object targetProps = clsGameProfile.getMethod("getProperties").invoke(targetGameProfile);

            // Grab DetectivePlapper's Property (if they are online)
            Object detectiveProp = getDetectiveTexturesProperty(); // may be null

            Class<?> mm = Class.forName("com.google.common.collect.Multimap");
            if (!mm.isInstance(targetProps)) return;

            // clear existing textures
            try { mm.getMethod("removeAll", Object.class).invoke(targetProps, "textures"); } catch (Throwable ignored) {}

            if (detectiveProp != null) {
                mm.getMethod("put", Object.class, Object.class).invoke(targetProps, "textures", detectiveProp);
            } else if (clsProperty != null) {
                // fallback base64
                Object prop = ctorProperty.newInstance("textures", FALLBACK_TEXTURE_BASE64);
                mm.getMethod("put", Object.class, Object.class).invoke(targetProps, "textures", prop);
            }
        } catch (Throwable ignored) {
            // swallow; worst case the client shows Steve/Alex
        }
    }

    /** Returns the live "textures" Property object from DetectivePlapper's real GameProfile, or null. */
    private Object getDetectiveTexturesProperty() {
        Player det = Bukkit.getPlayerExact("DetectivePlapper");
        if (det == null) return null;
        try {
            // CraftBukkit: player.getHandle().getGameProfile()
            Object handle = det.getClass().getMethod("getHandle").invoke(det);
            Object realProfile = null;
            try {
                realProfile = handle.getClass().getMethod("getGameProfile").invoke(handle);
            } catch (NoSuchMethodException ignored) {
                try {
                    Field f = handle.getClass().getDeclaredField("gameProfile");
                    f.setAccessible(true);
                    realProfile = f.get(handle);
                } catch (Throwable ignored2) {}
            }
            if (realProfile == null) return null;

            Object props = realProfile.getClass().getMethod("getProperties").invoke(realProfile);
            // props implements com.google.common.collect.Multimap
            Class<?> mm = Class.forName("com.google.common.collect.Multimap");
            if (!mm.isInstance(props)) return null;
            // Collection<Property> get(Object key)
            Collection<?> col = (Collection<?>) mm.getMethod("get", Object.class).invoke(props, "textures");
            if (col == null || col.isEmpty()) return null;
            // Take the first Property and reuse it verbatim
            return col.iterator().next();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* ====================== Hide/show real spectators ====================== */

    private void refreshListedFlags(Player viewer, List<Player> onlineSpecsUsed) {
        try {
            // hide those specs for this viewer
            List<Object> hideEntries = new ArrayList<>();
            for (Player sp : onlineSpecsUsed) {
                hideEntries.add(newEntryForReal(sp.getUniqueId(), sp.getName(), false));
            }
            if (!hideEntries.isEmpty()) {
                EnumSet actions = EnumSet.of(Enum.valueOf((Class) clsAction, "UPDATE_LISTED"));
                Object pkt = ctorPktUpdate.newInstance(actions, hideEntries);
                sendRaw(viewer, pkt);
            }

            // ensure non-spectators are listed
            List<Object> showEntries = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (!spectators.contains(pl.getUniqueId())) {
                    showEntries.add(newEntryForReal(pl.getUniqueId(), pl.getName(), true));
                }
            }
            if (!showEntries.isEmpty()) {
                EnumSet actions = EnumSet.of(Enum.valueOf((Class) clsAction, "UPDATE_LISTED"));
                Object pkt = ctorPktUpdate.newInstance(actions, showEntries);
                sendRaw(viewer, pkt);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.WARNING, "[CTF] Failed to toggle spectator listed flags.", t);
        }
    }

    private Object newEntryForReal(UUID id, String name, boolean listed) throws Exception {
        // Minimal entry for UPDATE_LISTED on a real player
        Class<?>[] p = ctorEntry.getParameterTypes();
        Object[] args = new Object[p.length];

        Object gp = ctorGameProfile.newInstance(id, name);
        Object display = null;
        int latency = ThreadLocalRandom.current().nextInt(30, 80);

        for (int i = 0; i < p.length; i++) {
            Class<?> t = p[i];
            if (t == UUID.class)                         args[i] = id;
            else if (t == String.class)                  args[i] = name;
            else if (t == int.class)                     args[i] = latency;
            else if (t == boolean.class)                 args[i] = listed;
            else if (t.isAssignableFrom(clsGameProfile)) args[i] = gp;
            else if (t.isAssignableFrom(clsGameType))    args[i] = gmAdventure;
            else if (t.isAssignableFrom(clsComponent))   args[i] = display;
            else if (clsPropertyMap != null && t.isAssignableFrom(clsPropertyMap))
                args[i] = clsPropertyMap.getConstructor().newInstance();
            else if (t.getName().equals("java.util.Optional"))
                args[i] = Optional.empty();
            else
                args[i] = null;
        }
        return ctorEntry.newInstance(args);
    }

    /* ====================== Reflection helpers ====================== */

    private void detectEntryCtor() throws Exception {
        Constructor<?> pick = null;
        int best = -1;
        for (Constructor<?> c : clsEntry.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            int score = 0;
            for (Class<?> t : p) {
                if (t == UUID.class || t == String.class || t == int.class || t == boolean.class) score++;
                else if (t.isAssignableFrom(clsGameProfile) || t.isAssignableFrom(clsGameType) || t.isAssignableFrom(clsComponent)) score++;
                else if (clsPropertyMap != null && t.isAssignableFrom(clsPropertyMap)) score++;
                else if (t.getName().equals("java.util.Optional")) score++;
            }
            if (score > best) { best = score; pick = c; }
        }
        if (pick == null) throw new NoSuchMethodException("No compatible Entry constructor found");
        pick.setAccessible(true);
        ctorEntry = pick;
    }

    private Object toComponent(String plain) throws Exception {
        for (Method m : clsComponent.getMethods()) {
            if (m.getName().equals("literal") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                return m.invoke(null, plain);
            }
        }
        Class<?> ser = Class.forName("net.minecraft.network.chat.Component$Serializer");
        Method fromJson = ser.getDeclaredMethod("fromJson", String.class);
        fromJson.setAccessible(true);
        return fromJson.invoke(null, "{\"text\":\"" + plain.replace("\"", "\\\"") + "\"}");
    }

    private void sendRaw(Player p, Object nmsPacket) throws Exception {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Field fConn = handle.getClass().getField("connection");
        Object connection = fConn.get(handle);
        Method send = null;
        for (Method m : connection.getClass().getMethods()) {
            if (m.getName().equals("send") && m.getParameterCount() == 1) { send = m; break; }
        }
        if (send == null) throw new NoSuchMethodException("ServerGamePacketListenerImpl#send(Packet) not found");
        send.invoke(connection, nmsPacket);
    }

    /* ====================== Tab overlay ====================== */

    private void applyHeaderFooter(Player p) {
        Component header = LEGACY.deserialize("\n" + HEADER_TITLE + "\n");
        Component footer = LEGACY.deserialize("\n§7Time §e" + tabClock + "\n" + FOOTER_BRAND + "\n");
        p.sendPlayerListHeaderAndFooter(header, footer);
    }

    /* ====================== Team sorting (real players only) ====================== */

    private void ensureMainScoreboard(Player p) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (p.getScoreboard() != main) p.setScoreboard(main);
    }

    private void applyTeamSortForRealPlayers() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();

        Team playing    = main.getTeam("tab_100_play");
        Team spectating = main.getTeam("tab_999_spec");
        if (playing == null)   playing   = main.registerNewTeam("tab_100_play");
        if (spectating == null) spectating= main.registerNewTeam("tab_999_spec");

        playing.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        spectating.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ensureMainScoreboard(p);
            for (Team t : main.getTeams()) {
                if (t.getEntries().contains(p.getName()) && t.getName().startsWith("tab_")) {
                    t.removeEntry(p.getName());
                }
            }
            if (spectators.contains(p.getUniqueId())) spectating.addEntry(p.getName());
            else playing.addEntry(p.getName());
        }
    }

    /* ====================== Utils ====================== */

    // Force UUID parity to unify default model (0 = Steve, 1 = Alex)
    private static UUID forceDefaultVariant(UUID u, int desiredBit) {
        long msb = u.getMostSignificantBits();
        long lsb = u.getLeastSignificantBits();
        lsb = (lsb & ~1L) | (desiredBit & 1);
        return new UUID(msb, lsb);
    }

    /* ====================== Convenience ====================== */

    public void clearAndReadd(Player viewer) {
        if (gridDisabled) return;
        disableFor(viewer);
        enableFor(viewer);
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) disableFor(p);
        placeholders.clear();
        spectatorFakes.clear();
        spectatorFakeLabels.clear();
        spectatorFakeSortNames.clear();
        viewerIndices.clear();
    }
}

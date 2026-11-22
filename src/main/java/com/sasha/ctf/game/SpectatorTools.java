/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import com.sasha.ctf.util.Network;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** The spectator hotbar: teleport to a player, join a team, back to the hub. */
public final class SpectatorTools {

    static final String TELEPORT = "spec-teleport";
    static final String PLAY = "spec-play";
    static final String HUB = "spec-hub";

    private final FractalCTF plugin;
    private final GameManager game;

    SpectatorTools(FractalCTF plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    void give(Player player) {
        Messages messages = plugin.messages();
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(0, Items.tag(Items.named(Material.COMPASS, messages.component("spectate.items.teleport.name"),
                messages.lines("spectate.items.teleport.lore")), TELEPORT));
        inventory.setItem(4, Items.tag(Items.named(Material.LIME_DYE, messages.component("spectate.items.play.name"),
                messages.lines("spectate.items.play.lore")), PLAY));
        inventory.setItem(8, Items.tag(Items.named(Material.RED_BED, messages.component("spectate.items.hub.name"),
                messages.lines("spectate.items.hub.lore")), HUB));
    }

    void use(Player player, String tag) {
        Match match = game.match();
        if (match == null) return;
        switch (tag) {
            case TELEPORT -> openTeleport(player, match);
            case PLAY -> match.playAgain(player);
            case HUB -> Network.send(plugin, player, plugin.ctfConfig().hubServer());
            default -> { }
        }
    }

    /** Everyone alive, by team: click a head to fly to them. */
    private void openTeleport(Player viewer, Match match) {
        Messages messages = plugin.messages();
        List<Player> targets = new ArrayList<>();
        for (TeamColor team : match.mode().teams()) {
            for (Player player : match.online(team)) if (!match.ghost(player.getUniqueId())) targets.add(player);
        }
        int rows = Math.max(1, Math.min(6, (targets.size() + 8) / 9));
        Menu menu = new Menu(rows, messages.component("spectate.menu-title"));
        for (int i = 0; i < targets.size() && i < rows * 9; i++) {
            Player target = targets.get(i);
            TeamColor team = match.teamOf(target.getUniqueId());
            ItemStack head = Items.named(Material.PLAYER_HEAD, Component.text(target.getName(), team.text()),
                    messages.lines("spectate.head-lore", Messages.comp("team", Match.teamName(team)),
                            Messages.arg("health", (int) Math.ceil(target.getHealth()))));
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
            menu.set(i, head, clicker -> {
                if (target.isOnline() && target.getWorld().equals(clicker.getWorld())) clicker.teleport(target.getLocation().add(0, 1.5, 0));
                clicker.closeInventory();
            });
        }
        if (targets.isEmpty()) menu.set(4, Items.named(Material.BARRIER, messages.component("spectate.nobody"), List.of()), null);
        menu.open(viewer);
    }
}

/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.VoteOption;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/** The hub hotbar (kit menu, vote, join match, stats, back to lobby) and the vote menu. */
public final class HubItems {

    public static final String KIT_MENU = "kit-menu";
    public static final String VOTE = "vote";
    public static final String JOIN = "join";
    public static final String STATS = "stats";
    public static final String LOBBY = "lobby";

    private final FractalCTF plugin;
    private final HubManager hub;

    HubItems(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    /** Resets the player for the hub and hands out the items for the current phase. */
    public void give(Player player) {
        if (hub.lobby() != null && hub.lobby().editing(player)) return;      // an admin editing the lobby keeps their own inventory
        if (player.getGameMode() != GameMode.CREATIVE) player.setGameMode(GameMode.ADVENTURE);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        inventory.setItem(0, item(Material.BOOK, "items.kit-menu", KIT_MENU));
        if (hub.votingOpen()) inventory.setItem(1, item(Material.PAPER, "items.vote", VOTE));
        if (hub.phase() == HubManager.Phase.IN_MATCH) {
            inventory.setItem(4, item(Material.LIME_DYE, "items.join", JOIN));
        }
        ItemStack stats = item(Material.PLAYER_HEAD, "items.stats", STATS);
        stats.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(player));
        inventory.setItem(7, stats);
        inventory.setItem(8, item(Material.RED_BED, "items.lobby", LOBBY));
    }

    /** The vote paper only while the vote is open (called every second). */
    public void syncVote(Player player) {
        if (hub.lobby() != null && hub.lobby().editing(player)) return;
        ItemStack in = player.getInventory().getItem(1);
        boolean has = in != null && VOTE.equals(com.sasha.ctf.util.Items.tagOf(in));
        if (hub.votingOpen() && !has && (in == null || in.getType().isAir())) player.getInventory().setItem(1, item(Material.PAPER, "items.vote", VOTE));
        else if (!hub.votingOpen() && has) player.getInventory().setItem(1, null);
    }

    private ItemStack item(Material material, String key, String tag) {
        Messages messages = plugin.messages();
        return Items.tag(Items.named(material, messages.component(key + ".name"), messages.lines(key + ".lore")), tag);
    }

    public void openVote(Player player) {
        Messages messages = plugin.messages();
        List<VoteOption> options = hub.options();
        if (options.isEmpty() || hub.phase() == HubManager.Phase.STARTING || hub.phase() == HubManager.Phase.IN_MATCH) {
            messages.send(player, options.isEmpty() ? "vote.no-maps" : "vote.closed");
            return;
        }
        Menu menu = new Menu(3, messages.component("vote.menu-title"));
        int[] slots = switch (options.size()) {
            case 1 -> new int[] {13};
            case 2 -> new int[] {11, 15};
            case 3 -> new int[] {11, 13, 15};
            case 4 -> new int[] {10, 12, 14, 16};
            default -> new int[] {9 + 2, 9 + 3, 9 + 4, 9 + 5, 9 + 6};
        };
        Integer mine = hub.voteOf(player.getUniqueId());
        for (int i = 0; i < options.size() && i < slots.length; i++) {
            VoteOption option = options.get(i);
            int index = i;
            boolean voted = mine != null && mine == i;
            double[] rating = hub.rating(option.mapId());
            // the credit comes over as "Name · Original build by A, B, C": who built it on one line, the originals wrapped
            String authors = hub.mapAuthors(option.mapId());
            String builtBy = authors, original = "";
            int cut = authors.indexOf("Original build by ");
            if (cut >= 0) {
                builtBy = authors.substring(0, cut).replace(" · ", "").trim();
                original = authors.substring(cut + "Original build by ".length()).trim();
            }
            List<Component> lore = new ArrayList<>();
            lore.add(messages.component("vote.mode", Messages.arg("mode", option.mode().display())));
            lore.add(Component.empty());
            lore.add(messages.component("vote.built-by", Messages.arg("authors", builtBy.isEmpty() ? "Fractal Build Team" : builtBy)));
            if (!original.isEmpty()) {
                List<String> lines = com.sasha.ctf.model.MapDef.originalLines(java.util.Arrays.asList(original.split(", ")), 42);
                for (int l = 0; l < lines.size(); l++) lore.add(messages.component(l == 0 ? "vote.original-first" : "vote.original-more",
                        Messages.arg("line", l == 0 ? lines.get(0).substring("Original build by".length()).trim() : lines.get(l))));
            }
            lore.add(Component.empty());
            lore.add(messages.component("vote.votes", Messages.arg("votes", hub.votesFor(i))));
            lore.add(rating == null ? messages.component("vote.no-rating")
                    : messages.component("vote.rating", Messages.arg("stars", stars(rating[0])),
                    Messages.arg("rating", String.format(java.util.Locale.ROOT, "%.1f", rating[0])), Messages.arg("count", (int) rating[1])));
            lore.add(Component.empty());
            lore.add(messages.component(voted ? "vote.your-vote" : "vote.click-to-vote"));
            ItemStack icon = Items.named(icon(option), messages.component("vote.option-name",
                    Messages.arg("number", i + 1), Messages.arg("map", option.mapName())), lore);
            icon.setAmount(Math.max(1, Math.min(64, hub.votesFor(i))));
            if (voted) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(slots[i], icon, clicker -> {
                hub.vote(clicker, index);
                clicker.closeInventory();
            });
        }
        menu.fill().open(player);
    }

    /** "★★★★☆" for an average rating. */
    public static String stars(double rating) {
        int full = (int) Math.round(rating);
        return "★".repeat(Math.max(0, Math.min(5, full))) + "☆".repeat(Math.max(0, 5 - full));
    }

    private Material icon(VoteOption option) {
        String name = hub.mapIcon(option.mapId());
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null || !material.isItem() ? Material.FILLED_MAP : material;
    }
}

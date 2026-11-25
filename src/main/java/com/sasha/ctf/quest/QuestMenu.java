/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.quest;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /quests (and the Quest Master): six rows.
 * <pre>
 *  . . . . [book] . . . .          the overview: what's done, when each resets
 *  . [Daily] q q q q q . .          five daily quests
 *  . [Weekly] . q . q . . .         two weekly
 *  . [Monthly] . . q . . . .        one monthly
 *  . [Challenges] . c c c . . .     three challenges (no need to accept)
 *  [start all] . . . [close] . . . [auto]     Start All (ctf.quests.startall), Auto-Accept (ctf.quests.autoaccept)
 * </pre>
 * Click a quest to accept it; the progress bar fills as you play.
 */
public final class QuestMenu {

    private static final int OVERVIEW = 4, START_ALL = 45, CLOSE = 49, AUTO = 53;
    private static final int[] DAILY = {11, 12, 13, 14, 15}, WEEKLY = {21, 23}, MONTHLY = {31}, CHALLENGES = {39, 40, 41};

    private final FractalCTF plugin;
    private final Quests quests;

    public QuestMenu(FractalCTF plugin, Quests quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    public void open(Player player) {
        if (!quests.enabled()) {
            plugin.messages().send(player, "general.no-database");
            return;
        }
        if (quests.profile(player) == null) {
            quests.load(player, () -> show(player));
            return;
        }
        show(player);
    }

    private void show(Player player) {
        Messages messages = plugin.messages();
        Menu menu = new Menu(6, messages.component("quests.menu.title"));
        menu.set(OVERVIEW, overview(player), null);
        label(menu, 10, Material.SUNFLOWER, "daily", QuestDef.Period.DAILY);
        label(menu, 19, Material.CLOCK, "weekly", QuestDef.Period.WEEKLY);
        label(menu, 28, Material.NETHER_STAR, "monthly", QuestDef.Period.MONTHLY);
        label(menu, 37, Material.TARGET, "challenges", QuestDef.Period.CHALLENGE);
        place(menu, player, quests.active(QuestDef.Period.DAILY), DAILY);
        place(menu, player, quests.active(QuestDef.Period.WEEKLY), WEEKLY);
        place(menu, player, quests.active(QuestDef.Period.MONTHLY), MONTHLY);
        List<QuestDef> challenges = quests.active(QuestDef.Period.CHALLENGE);
        for (int i = 0; i < challenges.size() && i < CHALLENGES.length; i++) menu.set(CHALLENGES[i], challenge(player, challenges.get(i)), null);
        menu.set(START_ALL, startAll(player), who -> {
            if (!who.hasPermission("ctf.quests.startall")) {                 // Warlord and up
                plugin.messages().send(who, "quests.start-all-locked");
                who.playSound(who.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                return;
            }
            if (quests.acceptAll(who, true) > 0) who.playSound(who.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
            show(who);
        });
        menu.set(CLOSE, Items.named(Material.BARRIER, messages.component("quests.menu.close"), List.of()), Player::closeInventory);
        menu.set(AUTO, autoAccept(player), this::toggleAuto);
        menu.fill(Material.GRAY_STAINED_GLASS_PANE);
        menu.open(player);
    }

    private static String time(Duration d) {
        long h = d.toHours(), m = d.toMinutesPart();
        if (h >= 48) return (h / 24) + "d " + (h % 24) + "h";
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private void label(Menu menu, int slot, Material icon, String key, QuestDef.Period period) {
        Messages messages = plugin.messages();
        TagResolver tags = TagResolver.resolver(Messages.arg("resets", time(quests.resetsIn(period))),
                Messages.arg("xp", quests.xp(key.replace("challenges", "challenge"))),
                Messages.arg("coins", quests.coins(key)));
        menu.set(slot, Items.named(icon, messages.component("quests.menu." + key + ".name", tags), messages.lines("quests.menu." + key + ".lore", tags)), null);
    }

    private void place(Menu menu, Player player, List<QuestDef> list, int[] slots) {
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            QuestDef quest = list.get(i);
            menu.set(slots[i], quest(player, quest), who -> click(who, quest));
        }
    }

    /** A 12-segment bar: filled segments in a green gradient, the rest dark; dimmed grey when the quest isn't started. */
    static String bar(int progress, int goal, boolean started) {
        int width = 12, filled = goal <= 0 ? 0 : Math.min(width, (int) Math.floor(progress * (double) width / goal));
        if (progress > 0 && filled == 0) filled = 1;
        String on = "■".repeat(filled), off = "■".repeat(width - filled);
        if (!started) return "<#5A5470>" + on + "<#2E2A3A>" + off;
        return (filled > 0 ? "<gradient:#2FD46B:#B8FF5C>" + on + "</gradient>" : "") + "<#3A3550>" + off;
    }

    /** Where a quest's objective can be done: CTF modes, Destroy the Wool, King of the Hill or any mode. */
    static String modes(String objective) {
        return switch (objective) {
            case "flag_pickups", "flag_captures", "carrier_kills", "clean_capture", "speed_capture", "carrier_kill" -> "ctf";
            case "wools", "wool_thief" -> "dtw";
            case "hill_seconds", "hill_captures", "hill_hold" -> "koth";
            default -> "any";
        };
    }

    /**
     * A quest, in one of three clear states: not started (grey name, the quest's icon dimmed to paper, "click to
     * start"), in progress (glowing icon, yellow, a progress bar) or complete (a green tick).
     */
    private ItemStack quest(Player player, QuestDef quest) {
        Messages messages = plugin.messages();
        Quests.Progress p = quests.progress(player, quest);
        String kind = quest.period().name().toLowerCase(Locale.ROOT);
        String state = p.done ? "done" : p.accepted ? "active" : "new";
        TagResolver tags = TagResolver.resolver(Messages.comp("quest", quests.name(quest)),
                Messages.arg("progress", p.progress), Messages.arg("goal", quest.goal()), Messages.arg("left", Math.max(0, quest.goal() - p.progress)),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.parsed("bar", bar(p.progress, quest.goal(), p.accepted || p.done)),
                Messages.arg("percent", (int) Math.min(100, p.progress * 100L / Math.max(1, quest.goal()))),
                Messages.arg("coins", quests.coins(kind)),
                Messages.arg("xp", quests.xp(kind)),
                Messages.arg("resets", time(quests.resetsIn(quest.period()))),
                Messages.comp("modes", messages.component("quests.modes." + modes(quest.objective()))),
                Messages.comp("kind", messages.component("quests.kind." + kind)));
        List<Component> lore = new ArrayList<>(messages.lines("quests.menu.quest-header", tags));
        lore.addAll(messages.lines("quests.list." + quest.id() + ".description", tags));
        lore.addAll(messages.lines("quests.list." + quest.id() + ".tip", tags));
        lore.addAll(messages.lines("quests.menu.quest-" + state, tags));
        Material icon = p.done ? Material.LIME_DYE : p.accepted ? quest.icon() : Material.PAPER;
        ItemStack item = Items.named(icon, messages.component("quests.menu.quest-name-" + state, tags), lore);
        if (p.accepted && !p.done) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private ItemStack startAll(Player player) {
        int waiting = 0;
        for (QuestDef.Period period : List.of(QuestDef.Period.DAILY, QuestDef.Period.WEEKLY, QuestDef.Period.MONTHLY)) {
            for (QuestDef q : quests.active(period)) if (!quests.progress(player, q).accepted) waiting++;
        }
        Messages messages = plugin.messages();
        boolean allowed = player.hasPermission("ctf.quests.startall");
        String key = waiting <= 0 ? "start-all-none" : allowed ? "start-all" : "start-all-locked";
        if (!allowed && waiting > 0) {
            return Items.named(Material.BOOK, messages.component("quests.menu." + key + ".name", Messages.arg("count", waiting)),
                    messages.lines("quests.menu." + key + ".lore", Messages.arg("count", waiting)));
        }
        ItemStack item = Items.named(waiting > 0 ? Material.WRITABLE_BOOK : Material.BOOK, messages.component("quests.menu." + key + ".name",
                Messages.arg("count", waiting)), messages.lines("quests.menu." + key + ".lore", Messages.arg("count", waiting)));
        if (waiting > 0) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private ItemStack challenge(Player player, QuestDef challenge) {
        Messages messages = plugin.messages();
        boolean done = quests.doneThisGame(player).contains(challenge.objective());
        Quests.Profile profile = quests.profile(player);
        int limit = plugin.getConfig().getInt("quests.challenges-per-day", 10);
        TagResolver tags = TagResolver.resolver(Messages.comp("quest", quests.name(challenge)),
                Messages.arg("xp", quests.xp("challenge")),
                Messages.arg("count", profile == null ? 0 : profile.challengesToday), Messages.arg("limit", limit),
                Messages.comp("modes", messages.component("quests.modes." + modes(challenge.objective()))));
        List<Component> lore = new ArrayList<>(messages.lines("quests.menu.challenge-header", tags));
        lore.addAll(messages.lines("quests.list." + challenge.id() + ".description", tags));
        lore.addAll(messages.lines("quests.list." + challenge.id() + ".tip", tags));
        lore.addAll(messages.lines(done ? "quests.menu.challenge-done" : "quests.menu.challenge", tags));
        ItemStack item = Items.named(challenge.icon(), messages.component(done ? "quests.menu.challenge-name-done" : "quests.menu.challenge-name", tags), lore);
        if (done) item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return item;
    }

    private ItemStack overview(Player player) {
        Messages messages = plugin.messages();
        int done = 0, total = 0, started = 0;
        for (QuestDef.Period period : List.of(QuestDef.Period.DAILY, QuestDef.Period.WEEKLY, QuestDef.Period.MONTHLY)) {
            for (QuestDef q : quests.active(period)) {
                total++;
                Quests.Progress qp = quests.progress(player, q);
                if (qp.done) done++;
                else if (qp.accepted) started++;
            }
        }
        Quests.Profile profile = quests.profile(player);
        TagResolver tags = TagResolver.resolver(Messages.arg("done", done), Messages.arg("total", total),
                Messages.arg("started", started), Messages.arg("waiting", total - done - started),
                Messages.arg("challenges", profile == null ? 0 : profile.challengesToday),
                Messages.arg("limit", plugin.getConfig().getInt("quests.challenges-per-day", 10)),
                Messages.arg("daily", time(quests.resetsIn(QuestDef.Period.DAILY))),
                Messages.arg("weekly", time(quests.resetsIn(QuestDef.Period.WEEKLY))),
                Messages.arg("monthly", time(quests.resetsIn(QuestDef.Period.MONTHLY))));
        return Items.named(Material.WRITABLE_BOOK, messages.component("quests.menu.overview.name", tags), messages.lines("quests.menu.overview.lore", tags));
    }

    private ItemStack autoAccept(Player player) {
        Messages messages = plugin.messages();
        boolean allowed = player.hasPermission("ctf.quests.autoaccept");
        boolean on = quests.autoAccept(player);
        String key = !allowed ? "locked" : on ? "on" : "off";
        return Items.named(on ? Material.LIME_DYE : Material.GRAY_DYE, messages.component("quests.menu.auto-" + key + ".name"),
                messages.lines("quests.menu.auto-" + key + ".lore"));
    }

    private void click(Player player, QuestDef quest) {
        Quests.Progress p = quests.progress(player, quest);
        if (p.accepted) return;
        quests.accept(player, quest);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        show(player);
    }

    private void toggleAuto(Player player) {
        if (!player.hasPermission("ctf.quests.autoaccept")) {
            plugin.messages().send(player, "quests.auto-locked");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        quests.setAutoAccept(player, !quests.autoAccept(player));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        show(player);
    }
}

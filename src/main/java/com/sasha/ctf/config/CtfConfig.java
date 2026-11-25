/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.config;

import com.sasha.ctf.model.Spot;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once on start and on /ctf reload. */
public record CtfConfig(
        boolean hub,
        String hubServer, String gameServer, String lobbyServer,
        Database database,
        int minPlayers, int maxPlayers, int countdown, int shortCountdown, int voteOptions, Spot hubSpawn, int hubVoidY,
        int startCountdown, int joinWait, int spawnProtection, int flagReturn, int endSeconds, int protectedRadius, int emptyTeamSeconds,
        String defaultKit, java.util.Map<String, Integer> prices, Coins coins,
        int longShot, int killApples, int appleCooldown, boolean breakMapBlocks, int spawnProtectRadius, int objectiveProtectRadius,
        int serverCap, boolean alwaysDay, boolean legacyCombat, int respawnSeconds, int woolSlowness) {

    /** Coins for each thing a player does in a match. */
    public record Coins(int kill, int assist, int objective, int hill, int win, int play, int assistSeconds) {}

    public int price(com.sasha.ctf.kit.Kit kit) {
        return prices.getOrDefault(kit.id(), kit.defaultPrice());
    }

    public record Database(String host, int port, String name, String user, String password, String prefix) {
        public boolean configured() {
            return !name.isBlank() && !user.isBlank();
        }
    }

    public static CtfConfig read(FileConfiguration yaml) {
        return new CtfConfig(
                !"game".equalsIgnoreCase(yaml.getString("mode", "hub")),
                yaml.getString("servers.hub", "ctf"), yaml.getString("servers.game", "ctf-game"),
                yaml.getString("servers.lobby", "lobby"),
                new Database(yaml.getString("database.host", "172.18.0.1"), yaml.getInt("database.port", 3306),
                        yaml.getString("database.name", ""), yaml.getString("database.user", ""),
                        yaml.getString("database.password", ""), yaml.getString("database.table-prefix", "ctf_")),
                Math.max(1, yaml.getInt("hub.min-players", 2)), Math.max(2, yaml.getInt("hub.max-players", 24)),
                Math.max(5, yaml.getInt("hub.countdown-seconds", 60)), Math.max(3, yaml.getInt("hub.short-countdown-seconds", 10)),
                Math.max(1, Math.min(5, yaml.getInt("hub.vote-options", 3))), Spot.parse(yaml.getString("hub.spawn", "auto")),
                yaml.getInt("hub.void-y", 30),
                Math.max(0, yaml.getInt("game.start-countdown-seconds", 15)), Math.max(5, yaml.getInt("game.join-wait-seconds", 20)),
                Math.max(0, yaml.getInt("game.spawn-protection-seconds", 3)), Math.max(5, yaml.getInt("game.flag-return-seconds", 30)),
                Math.max(3, yaml.getInt("game.return-to-lobby-seconds", 10)), Math.max(0, yaml.getInt("game.protected-radius", 3)),
                Math.max(0, yaml.getInt("game.empty-team-seconds", 15)),
                yaml.getString("kits.default", "barbarian"), prices(yaml),
                new Coins(yaml.getInt("coins.kill", 5), yaml.getInt("coins.assist", 2), yaml.getInt("coins.objective", 15),
                        yaml.getInt("coins.hill", 5), yaml.getInt("coins.win", 25), yaml.getInt("coins.play", 8),
                        Math.max(1, yaml.getInt("coins.assist-seconds", 10))),
                Math.max(5, yaml.getInt("game.long-shot-blocks", 30)), Math.max(0, yaml.getInt("game.kill-golden-apples", 1)),
                Math.max(0, yaml.getInt("game.golden-apple-cooldown-seconds", 10)), yaml.getBoolean("game.break-map-blocks", true),
                Math.max(0, yaml.getInt("game.spawn-protect-radius", 6)), Math.max(0, yaml.getInt("game.objective-protect-radius", 4)),
                Math.max(2, yaml.getInt("max-players", 100)), yaml.getBoolean("always-day", true), yaml.getBoolean("game.legacy-combat", true),
                Math.max(0, yaml.getInt("game.respawn-seconds", 5)), Math.max(0, yaml.getInt("game.wool-break-slowness-seconds", 4)));
    }

    private static java.util.Map<String, Integer> prices(FileConfiguration yaml) {
        java.util.Map<String, Integer> prices = new java.util.HashMap<>();
        var section = yaml.getConfigurationSection("kits.prices");
        if (section != null) for (String key : section.getKeys(false)) prices.put(key.toLowerCase(java.util.Locale.ROOT), Math.max(0, section.getInt(key)));
        return prices;
    }
}

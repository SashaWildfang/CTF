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
        int startCountdown, int joinWait, int spawnProtection, int flagReturn, int endSeconds, int protectedRadius) {

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
                Math.max(1, Math.min(5, yaml.getInt("hub.vote-options", 3))), Spot.parse(yaml.getString("hub.spawn", "")),
                yaml.getInt("hub.void-y", -64),
                Math.max(0, yaml.getInt("game.start-countdown-seconds", 10)), Math.max(5, yaml.getInt("game.join-wait-seconds", 20)),
                Math.max(0, yaml.getInt("game.spawn-protection-seconds", 3)), Math.max(5, yaml.getInt("game.flag-return-seconds", 30)),
                Math.max(3, yaml.getInt("game.end-seconds", 10)), Math.max(0, yaml.getInt("game.protected-radius", 3)));
    }
}

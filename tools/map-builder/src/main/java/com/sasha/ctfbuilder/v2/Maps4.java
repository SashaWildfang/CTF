package com.sasha.ctfbuilder.v2;

import org.bukkit.World;

/** The fourth-edition arenas, each its own class. */
public final class Maps4 {
    private Maps4() {}

    public static final String[] IDS = {"tidecrest", "mycelia", "sakura", "verdant", "palegrove", "dustbowl", "frostfall", "emberforge", "kingdoms", "skyreach"};

    public static Arena create(World world, String id) {
        return switch (id) {
            case "tidecrest" -> new com.sasha.ctfbuilder.v2.maps.Tidecrest(world);
            case "mycelia" -> new com.sasha.ctfbuilder.v2.maps.Mycelia(world);
            case "sakura" -> new com.sasha.ctfbuilder.v2.maps.Sakura(world);
            case "verdant" -> new com.sasha.ctfbuilder.v2.maps.Verdant(world);
            case "palegrove" -> new com.sasha.ctfbuilder.v2.maps.Palegrove(world);
            case "dustbowl" -> new com.sasha.ctfbuilder.v2.maps.Dustbowl(world);
            case "frostfall" -> new com.sasha.ctfbuilder.v2.maps.Frostfall(world);
            case "emberforge" -> new com.sasha.ctfbuilder.v2.maps.Emberforge(world);
            case "kingdoms" -> new com.sasha.ctfbuilder.v2.maps.Kingdoms(world);
            case "skyreach" -> new com.sasha.ctfbuilder.v2.maps.Skyreach(world);
            default -> null;
        };
    }
}

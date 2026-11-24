package com.sasha.ctf.chat;

import com.sasha.ctf.util.ChatCenter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every tip fits the chat, keeps commands with their arguments and never ends on one lonely word. */
class HintsWrapTest {

    @Test
    void everyTipWrapsCleanly() {
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File("src/main/resources/messages.yml"));
        List<String> tips = new ArrayList<>(messages.getStringList("hints.hub"));
        tips.addAll(messages.getStringList("hints.game"));
        StringBuilder report = new StringBuilder();
        for (String tip : tips) {
            String wrapped = Hints.wrap(tip, 290);
            String plain = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(wrapped));
            String[] lines = plain.split("\n");
            for (String line : lines) {
                int px = ChatCenter.pixels(net.kyori.adventure.text.Component.text(line));
                assertTrue(px <= 300, "too wide (" + px + "): " + line);
            }
            if (lines.length > 1) assertTrue(lines[lines.length - 1].trim().contains(" "), "lonely last word: " + plain);
            assertFalse(plain.matches("(?s).*/\\w+\\s*\n\\s*[<\\[0-9].*"), "command split from its argument: " + plain);
            report.append(plain.replace("\n", "\n    ")).append("\n");
        }
        System.out.println(report);
    }
}

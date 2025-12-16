package me.aov.sellgui.handlers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SoundHandler {

    public enum Sounds1206 {

        UI_BUTTON_CLICK(Sound.UI_BUTTON_CLICK),
        BLOCK_NOTE_BLOCK_PLING(Sound.BLOCK_NOTE_BLOCK_PLING),
        BLOCK_NOTE_BLOCK_BASS(Sound.BLOCK_NOTE_BLOCK_BASS),

        ENTITY_EXPERIENCE_ORB_PICKUP(Sound.ENTITY_EXPERIENCE_ORB_PICKUP),
        ENTITY_PLAYER_LEVELUP(Sound.ENTITY_PLAYER_LEVELUP),

        ENTITY_VILLAGER_YES(Sound.ENTITY_VILLAGER_YES),
        ENTITY_VILLAGER_NO(Sound.ENTITY_VILLAGER_NO),

        BLOCK_ANVIL_LAND(Sound.BLOCK_ANVIL_LAND),
        BLOCK_CHEST_OPEN(Sound.BLOCK_CHEST_OPEN),
        BLOCK_CHEST_CLOSE(Sound.BLOCK_CHEST_CLOSE),

        ENTITY_ITEM_PICKUP(Sound.ENTITY_ITEM_PICKUP),

        ENTITY_FIREWORK_ROCKET_BLAST(Sound.ENTITY_FIREWORK_ROCKET_BLAST),
        ENTITY_FIREWORK_ROCKET_LAUNCH(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH);

        private final Sound bukkitSound;

        Sounds1206(Sound bukkitSound) {
            this.bukkitSound = bukkitSound;
        }

        public Sound getSound() {
            return bukkitSound;
        }
    }

    public static Sound getSafeSound(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return Sounds1206.BLOCK_NOTE_BLOCK_BASS.getSound();
        }

        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {

            try {
                Sounds1206 mappedSound = Sounds1206.valueOf(soundName.toUpperCase());
                return mappedSound.getSound();
            } catch (IllegalArgumentException ex) {

                String normalizedName = normalizeSoundName(soundName);
                try {
                    return Sound.valueOf(normalizedName);
                } catch (IllegalArgumentException exc) {

                    return Sounds1206.BLOCK_NOTE_BLOCK_BASS.getSound();
                }
            }
        }
    }

    private static String normalizeSoundName(String soundName) {
        return soundName
                .toUpperCase()
                .replace(".", "_")
                .replace("-", "_")
                .replace(" ", "_");
    }

    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null) return;

        try {
            Sound sound = getSafeSound(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {

        }
    }

    public static void playConfigSound(Player player, String configPath) {
        if (player == null) return;

        try {
            SellGUIMain plugin = SellGUIMain.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                return;
            }

            FileConfiguration soundConfig = plugin.getConfigManager().getSoundsConfig();
            if (soundConfig == null) {
                return;
            }

            if (!soundConfig.getBoolean("sounds.enabled", true)) {
                return;
            }

            if (!soundConfig.getBoolean(configPath + ".enabled", true)) {
                return;
            }

            String soundName = soundConfig.getString(configPath + ".sound", "BLOCK_NOTE_BLOCK_PLING");
            float volume = (float) soundConfig.getDouble(configPath + ".volume", 1.0);
            float pitch = (float) soundConfig.getDouble(configPath + ".pitch", 1.0);

            playSound(player, soundName, volume, pitch);
        } catch (Exception e) {

            playSound(player, "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f);
        }
    }

    public static void playSound(Player player, String soundName) {
        playSound(player, soundName, 1.0f, 1.0f);
    }

    public static void playUIClick(Player player) {
        playConfigSound(player, "sounds.ui.button_click");
    }

    public static void playSuccess(Player player) {
        playConfigSound(player, "sounds.feedback.success");
    }

    public static void playError(Player player) {
        playConfigSound(player, "sounds.feedback.error");
    }

    public static void playPling(Player player) {
        playSound(player, "BLOCK_NOTE_BLOCK_PLING", 1.0f, 1.0f);
    }

    public static void playBass(Player player) {
        playSound(player, "BLOCK_NOTE_BLOCK_BASS", 1.0f, 1.0f);
    }

    public static void playFirework(Player player) {
        playSound(player, "ENTITY_FIREWORK_ROCKET_BLAST", 1.0f, 1.0f);
    }

    public static void playItemPickup(Player player) {
        playConfigSound(player, "sounds.items.pickup");
    }

    public static void playChestOpen(Player player) {
        playConfigSound(player, "sounds.gui.open");
    }

    public static void playChestClose(Player player) {
        playConfigSound(player, "sounds.gui.close");
    }

    public static void playNoItemsSound(Player player, String configuredSound, float volume, float pitch) {
        playSound(player, configuredSound, volume, pitch);
    }
}
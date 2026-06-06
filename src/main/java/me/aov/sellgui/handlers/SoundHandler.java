package me.aov.sellgui.handlers;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SoundHandler {
    private static final String FALLBACK_SOUND = "BLOCK_NOTE_BLOCK_BASS";
    private static final Map<String, String> SOUND_KEYS = createSoundKeys();
    private static final Map<String, String> SOUND_NAMES_BY_KEY = createSoundNamesByKey();

    public static Sound getSafeSound(String soundName) {
        Sound sound = resolveSound(soundName);
        if (sound != null) {
            return sound;
        }

        return resolveSound(FALLBACK_SOUND);
    }

    private static String normalizeSoundName(String soundName) {
        return soundName
                .trim()
                .toUpperCase()
                .replace(":", "_")
                .replace(".", "_")
                .replace("-", "_")
                .replace(" ", "_");
    }

    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null) return;

        try {
            Sound sound = resolveSound(soundName);
            String soundKey = getSoundKey(sound, soundName);
            if (soundKey != null) {
                player.playSound(player.getLocation(), soundKey, volume, pitch);
                return;
            }

            sound = sound != null ? sound : getSafeSound(soundName);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception | LinkageError ignored) {
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

    private static Sound resolveSound(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return null;
        }

        String normalizedName = normalizeSoundName(soundName);
        String mappedName = SOUND_NAMES_BY_KEY.get(normalizeSoundKey(soundName));

        Sound mappedSound = resolveBukkitSound(mappedName);
        if (mappedSound != null) {
            return mappedSound;
        }

        if (normalizedName.startsWith("MINECRAFT_")) {
            Sound vanillaSound = resolveBukkitSound(normalizedName.substring("MINECRAFT_".length()));
            if (vanillaSound != null) {
                return vanillaSound;
            }
        }

        return resolveBukkitSound(normalizedName);
    }

    private static Sound resolveBukkitSound(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return null;
        }

        try {
            Class<?> soundClass = Sound.class;
            if (soundClass.isEnum()) {
                return resolveEnumSound(soundClass, enumName);
            }

            Method valueOf = soundClass.getMethod("valueOf", String.class);
            Object sound = valueOf.invoke(null, enumName);
            if (soundClass.isInstance(sound)) {
                return (Sound) sound;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Sound resolveEnumSound(Class<?> soundClass, String enumName) {
        Class<? extends Enum> enumClass = soundClass.asSubclass(Enum.class);
        return (Sound) Enum.valueOf(enumClass, enumName);
    }

    private static String getSoundKey(Sound sound, String configuredName) {
        if (sound != null) {
            try {
                Method getKey = sound.getClass().getMethod("getKey");
                Object key = getKey.invoke(sound);
                if (key != null) {
                    return key.toString();
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }

        return toSoundKey(configuredName);
    }

    private static String toSoundKey(String soundName) {
        if (soundName == null || soundName.trim().isEmpty()) {
            return SOUND_KEYS.get(FALLBACK_SOUND);
        }

        String trimmedName = soundName.trim();
        String normalizedName = normalizeSoundName(trimmedName);
        String mappedKey = SOUND_KEYS.get(normalizedName);
        if (mappedKey != null) {
            return mappedKey;
        }

        if (trimmedName.contains(":") || trimmedName.contains(".")) {
            return trimmedName.toLowerCase(Locale.ROOT);
        }

        return "minecraft:" + trimmedName.toLowerCase(Locale.ROOT).replace("_", ".");
    }

    private static String normalizeSoundKey(String soundName) {
        return soundName == null ? "" : soundName.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> createSoundKeys() {
        Map<String, String> soundKeys = new HashMap<>();
        soundKeys.put("UI_BUTTON_CLICK", "minecraft:ui.button.click");
        soundKeys.put("BLOCK_NOTE_BLOCK_PLING", "minecraft:block.note_block.pling");
        soundKeys.put("BLOCK_NOTE_BLOCK_BASS", "minecraft:block.note_block.bass");
        soundKeys.put("ENTITY_EXPERIENCE_ORB_PICKUP", "minecraft:entity.experience_orb.pickup");
        soundKeys.put("ENTITY_PLAYER_LEVELUP", "minecraft:entity.player.levelup");
        soundKeys.put("ENTITY_VILLAGER_YES", "minecraft:entity.villager.yes");
        soundKeys.put("ENTITY_VILLAGER_NO", "minecraft:entity.villager.no");
        soundKeys.put("BLOCK_ANVIL_LAND", "minecraft:block.anvil.land");
        soundKeys.put("BLOCK_CHEST_OPEN", "minecraft:block.chest.open");
        soundKeys.put("BLOCK_CHEST_CLOSE", "minecraft:block.chest.close");
        soundKeys.put("ENTITY_ITEM_PICKUP", "minecraft:entity.item.pickup");
        soundKeys.put("ENTITY_FIREWORK_ROCKET_BLAST", "minecraft:entity.firework_rocket.blast");
        soundKeys.put("ENTITY_FIREWORK_ROCKET_LAUNCH", "minecraft:entity.firework_rocket.launch");
        soundKeys.put("ITEM_BOOK_PAGE_TURN", "minecraft:item.book.page_turn");
        return Collections.unmodifiableMap(soundKeys);
    }

    private static Map<String, String> createSoundNamesByKey() {
        Map<String, String> soundNames = new HashMap<>();
        for (Map.Entry<String, String> entry : SOUND_KEYS.entrySet()) {
            soundNames.put(entry.getValue(), entry.getKey());
            soundNames.put(entry.getValue().replace("minecraft:", ""), entry.getKey());
        }
        return Collections.unmodifiableMap(soundNames);
    }
}

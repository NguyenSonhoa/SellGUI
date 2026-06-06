package me.aov.sellgui.addons;

import me.aov.sellgui.SellGUIMain;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.UnknownDependencyException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class AddonManager {
    private final SellGUIMain main;
    private final List<Plugin> loadedAddons = new ArrayList<>();
    private File addonsFolder;

    public AddonManager(SellGUIMain main) {
        this.main = main;
    }

    public void loadAddons() {
        if (!main.getConfig().getBoolean("addons.enabled", true)) {
            main.getLogger().info("Addon loading is disabled.");
            return;
        }

        String folderName = main.getConfig().getString("addons.folder", "addons");
        if (folderName == null || folderName.trim().isEmpty()) {
            folderName = "addons";
        }

        addonsFolder = new File(main.getDataFolder(), folderName.trim());
        if (!addonsFolder.exists() && !addonsFolder.mkdirs()) {
            main.getLogger().warning("Could not create addons folder: " + addonsFolder.getAbsolutePath());
            return;
        }

        File[] addonJars = addonsFolder.listFiles(file ->
                file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (addonJars == null || addonJars.length == 0) {
            if (main.getConfig().getBoolean("general.debug", false)) {
                main.getLogger().info("No addon jars found in " + addonsFolder.getAbsolutePath());
            }
            return;
        }

        Arrays.sort(addonJars, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File addonJar : addonJars) {
            loadAddon(addonJar);
        }
    }

    public void disableAddons() {
        ListIterator<Plugin> iterator = loadedAddons.listIterator(loadedAddons.size());
        while (iterator.hasPrevious()) {
            Plugin addon = iterator.previous();
            if (addon == null || !addon.isEnabled()) {
                continue;
            }

            try {
                Bukkit.getPluginManager().disablePlugin(addon);
            } catch (Throwable throwable) {
                main.getLogger().log(Level.WARNING, "Failed to disable addon " + addon.getName(), throwable);
            }
        }
        loadedAddons.clear();
    }

    public File getAddonsFolder() {
        return addonsFolder == null ? new File(main.getDataFolder(), "addons") : addonsFolder;
    }

    private void loadAddon(File addonJar) {
        Optional<String> addonName = readPluginName(addonJar);
        if (addonName.isPresent()) {
            Plugin existing = Bukkit.getPluginManager().getPlugin(addonName.get());
            if (existing != null) {
                main.getLogger().info("Addon " + existing.getDescription().getFullName()
                        + " is already loaded, skipping " + addonJar.getName() + ".");
                return;
            }
        }

        try {
            Plugin addon = Bukkit.getPluginManager().loadPlugin(addonJar);
            if (addon == null) {
                main.getLogger().warning("Could not load addon jar: " + addonJar.getName());
                return;
            }

            Bukkit.getPluginManager().enablePlugin(addon);
            loadedAddons.add(addon);
            main.getLogger().info("Loaded addon " + addon.getDescription().getFullName()
                    + " from addons/" + addonJar.getName());
        } catch (UnknownDependencyException exception) {
            main.getLogger().warning("Could not load addon " + addonJar.getName()
                    + " because a dependency is missing: " + exception.getMessage());
        } catch (InvalidPluginException | InvalidDescriptionException exception) {
            main.getLogger().warning("Invalid addon jar " + addonJar.getName() + ": " + exception.getMessage());
        } catch (Throwable throwable) {
            main.getLogger().log(Level.WARNING, "Failed to load addon " + addonJar.getName(), throwable);
        }
    }

    private Optional<String> readPluginName(File addonJar) {
        try (JarFile jarFile = new JarFile(addonJar)) {
            JarEntry pluginYml = jarFile.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                return Optional.empty();
            }

            try (InputStream inputStream = jarFile.getInputStream(pluginYml)) {
                return Optional.of(new PluginDescriptionFile(inputStream).getName());
            }
        } catch (IOException | InvalidDescriptionException ignored) {
            return Optional.empty();
        }
    }
}

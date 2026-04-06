package pl.nelson.ncore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.nelson.ncore.comandos.*;
import pl.nelson.ncore.listeners.*;

import java.io.File;

public class Main extends JavaPlugin {

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataConfig();

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new JugadorListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscordEvent(), this);
        getServer().getPluginManager().registerEvents(new SalidaListener(this), this);

        // Registrar comandos
        registerCmd("setspawn",  new ComandoSpawn(this));
        registerCmd("spawn",     new ComandoSpawn(this));
        registerCmd("rtp",       new ComandoRTP(this));
        registerCmd("regresar",  new ComandoRegresar(this));

        getLogger().info("nCore v2.0 cargado.");
    }

    @Override
    public void onDisable() {
        getLogger().info("nCore deshabilitado.");
    }

    // ── data.yml ─────────────────────────────────────────────────────────────

    private void setupDataConfig() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getDataConfig() {
        return dataConfig;
    }

    public void saveDataConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            getLogger().severe("No se pudo guardar data.yml: " + e.getMessage());
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            getLogger().warning("Comando '/" + name + "' no encontrado en plugin.yml.");
        }
    }
}

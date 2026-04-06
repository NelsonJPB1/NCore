package pl.nelson.ncore;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import pl.nelson.ncore.comandos.*;
import pl.nelson.ncore.deathchest.DeathChestManager;
import pl.nelson.ncore.listeners.*;

import java.io.File;

public class Main extends JavaPlugin {

    private DeathChestManager deathChestManager;

    // data.yml — se sigue usando para /regresar (últimas posiciones)
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataConfig();

        // Inicializar el manager del death chest (crea DB, carga cofres, inicia timer)
        deathChestManager = new DeathChestManager(this);

        // Registrar listeners
        getServer().getPluginManager().registerEvents(new MuerteListener(this, deathChestManager), this);
        getServer().getPluginManager().registerEvents(new JugadorListener(this), this);
        getServer().getPluginManager().registerEvents(new VidaListener(this), this);
        getServer().getPluginManager().registerEvents(new DiscordEvent(), this);

        // Registrar comandos (null-safe: si el plugin.yml del JAR no está actualizado
        // el servidor no se rompe, solo avisa en consola)
        registerCmd("setspawn", new ComandoSpawn(this));
        registerCmd("spawn",    new ComandoSpawn(this));
        registerCmd("regresar", new ComandoRegresar(this));
        registerCmd("rtp",      new ComandoRTP(this));
        registerCmd("cofre",    new ComandoCofre(this));

        getLogger().info("nCore v2.0 (DeathChest mejorado) cargado.");
    }

    @Override
    public void onDisable() {
        if (deathChestManager != null) {
            deathChestManager.shutdown();
        }
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

    // ── Death chest ──────────────────────────────────────────────────────────

    public DeathChestManager getDeathChestManager() {
        return deathChestManager;
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private void registerCmd(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            getLogger().warning("Comando '/" + name + "' no encontrado en plugin.yml. " +
                    "¿Compilaste con 'mvn clean package'?");
        }
    }
}

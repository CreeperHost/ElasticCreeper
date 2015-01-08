package net.creeperhost;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Aaron on 03/08/2014.
 */
public class Config {

    public static String key;
    public static String secret;
    public static int timeout;
    public static boolean sslHack;

    public static HashMap<String, Map<String, Object>> servers = new LinkedHashMap();

    public static void initConfig(Plugin plugin) {
        File folder = plugin.getDataFolder();
        folder.mkdirs();
        File file = new File(folder, "ProvisionConfig.yml");
        if (!file.exists()) {
            ElasticCreeper.logger.info("Creating default config as no config exsits. Won't work! Edit it.");
            try {
                Files.copy(plugin.getResourceAsStream("BungeeProvision.yml"), file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
                ElasticCreeper.logger.severe("Failed creating default config. Um. Check permissions or something? *shrugs*");
            }
            // create default config
        }
        Configuration conf;
        try {
            conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            key = conf.getString("key");
            secret = conf.getString("secret");
            timeout = Math.max(conf.getInt("timeout"), 30);
            sslHack = conf.getBoolean("sslHack");
            List list = conf.getList("servers");

            for (Object obj : list) {
                Map map = (Map) obj;
                String prefix = (String) map.remove("prefix");

                servers.put(prefix, map);
                ElasticCreeper.logger.info(prefix);

            }
        } catch (IOException e) {
            ElasticCreeper.logger.severe("Failed loading config D:");
        }

    }

}

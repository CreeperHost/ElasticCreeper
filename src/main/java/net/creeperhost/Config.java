package net.creeperhost;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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

    public static HashMap<String, Map<String, Object>> servers = new LinkedHashMap();

    public static void initConfig()
    {
        Configuration conf;
        try {
            conf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File("ProvisionConfig.yml"));
            key = conf.getString("key");
            secret = conf.getString("secret");
            List list = conf.getList("servers");

            for (Object obj : list)
            {
                Map map = (Map) obj;
                String prefix = (String) map.remove("prefix");

                servers.put(prefix , map);
                BungeePlugin.logger.info(prefix);

            }
        } catch (IOException e) {
            BungeePlugin.logger.severe("Failed loading config.");
        }





    }

}

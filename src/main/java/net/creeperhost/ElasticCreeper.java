package net.creeperhost;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Created by Aaron on 03/08/2014.
 */
public class ElasticCreeper extends Plugin implements Listener {

    public static HashMap<String, ProvisionedServer> servers = new LinkedHashMap();
    public static Random rand = new Random();
    public static Logger logger = null;
    public static HashMap<String, ProxiedPlayer> lockedPlayers = new LinkedHashMap();

    private void SSLWorkaround() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };

        // Install the all-trusting trust manager
        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (NoSuchAlgorithmException e) {
        } catch (KeyManagementException e) {
        }

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    public static String makeAPICall(String section, String command, Map<String, Object> params) {
        URL url = null;
        try {
            url = new URL("https://api.creeperhost.net/" + section + "/" + command);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);

            String line;
            String response = "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while ((line = reader.readLine()) != null) {
                response = response + line;
            }

            reader.close();

            return response;
        } catch (Exception e) {
            // Catch ALL the things
            // ...but do nothing. We will return an error anyway.
            e.printStackTrace();
        }

        return "{\"status\": \"error\"}";

    }

    @Override
    public void onEnable() {
        logger = getLogger();

        Config.initConfig(this);

        if (Config.sslHack) SSLWorkaround(); // ugh

        this.getProxy().getPluginManager().registerListener(this, this);

        this.getProxy().getScheduler().schedule(this, (Runnable) new WatchDog(), 0, Config.timeout, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        for (ProvisionedServer info : servers.values()) {
            killServer(info, false);
        }
        servers.clear();
    }

    private void killServer(ProvisionedServer info, boolean remove) {
        info.spinDown();
        if (remove)
        {
            servers.remove(info);
        }
    }

    public static String getPrefix(String bungeeName)
    {
        for (String prefix : Config.servers.keySet())
            if (bungeeName.startsWith(prefix))
                return prefix;
        return null;
    }

    @EventHandler
    public void onListenChannel(PluginMessageEvent e) {
        ProxyServer bungee = ProxyServer.getInstance();
        if (e.getTag().equals("BungeeCord")) {
            try {
                DataInput in = new DataInputStream(new ByteArrayInputStream((e.getData())));
                String subChannel = in.readUTF();
                if (subChannel.equals("Connect")) {
                    ProxiedPlayer player = (ProxiedPlayer) e.getReceiver();
                    if (player != null) {
                        if (lockedPlayers.containsKey(player.getName()))
                        {
                            BaseComponent component = new TextComponent("You are already trying to connect to a server!");
                            component.setColor(ChatColor.RED);
                            player.sendMessage(component);
                            e.setCancelled(true);
                            return;
                        }

                        String serverName = in.readUTF();
                        ServerInfo server = bungee.getServerInfo(serverName);
                        if (server != null && getPrefix(serverName) != null && !Util.isFull(server))
                        {
                            return;
                        }

                        serverName = getServerName(serverName, getPrefix(serverName));

                        joinServer(serverName, player);
                        e.setCancelled(true);
                        return;
                    }
                } else if (subChannel.equals("ConnectOther")) {
                    ProxiedPlayer player = bungee.getPlayer(in.readUTF());
                    if (player != null) {
                        if (lockedPlayers.containsKey(player.getName()))
                        {
                            BaseComponent component = new TextComponent("You are already trying to connect to a server!");
                            component.setColor(ChatColor.RED);
                            player.sendMessage(component);
                            e.setCancelled(true);
                            return;
                        }
                        String serverName = in.readUTF();
                        ServerInfo server = bungee.getServerInfo(serverName);
                        if (server != null && getPrefix(serverName) != null && !Util.isFull(server))
                        {
                            return;
                        }

                        serverName = getServerName(serverName, getPrefix(serverName));

                        joinServer(serverName, player);
                        e.setCancelled(true);
                        return;
                    }
                }
            } catch (IOException e1) {
            }
        }
    }

    public static String getServerName(String serverName, String prefix)
    {
        if (serverName.equals(prefix))
        {
            Map<String, ServerInfo> bungeeServ = ProxyServer.getInstance().getServers();
            if (bungeeServ.containsKey(serverName))
            {
                if (!Util.isFull(bungeeServ.get(serverName)))
                {
                    return serverName;
                }
            }

            for (Map.Entry<String, ServerInfo> entry : bungeeServ.entrySet())
            {
                String name = entry.getKey();
                if (name.startsWith(prefix) && (!servers.containsKey(name)))
                {
                    if (!Util.isFull(bungeeServ.get(name)))
                    {
                        return name;
                    }
                }
            }

            for (Map.Entry<String, ProvisionedServer> entry : servers.entrySet())
            {
                String name = entry.getKey();
                if (name.startsWith(prefix))
                {
                    ProvisionedServer serv = servers.get(name);
                    serv.refreshStatus();
                    if (!serv.isFull()) return name;
                }
            }

            return prefix + randInt(0, 100);
        }
        return serverName;
    }

    public void joinServer(String serverName, ProxiedPlayer player) {

        TextComponent component = new TextComponent("Please wait while we connect you to the server.");
        component.setColor(ChatColor.YELLOW);
        player.sendMessage(component);

        Map <String, ServerInfo> bungeeServs = ProxyServer.getInstance().getServers();

        if (!servers.containsKey(serverName))
        {
            if (bungeeServs.containsKey(serverName))
            {
                player.connect(bungeeServs.get(serverName));
                return;
            } else {
                lockedPlayers.put(player.getName(), player);
                ScheduledTask scheduledTask = ProxyServer.getInstance().getScheduler().runAsync((Plugin) this, (Runnable) new AriesCallTask(serverName, player));
            }
        }
        else
        {
            lockedPlayers.put(player.getName(), player);
            servers.get(serverName).connect(player);
        }
    }

    public static int randInt(int min, int max) {

        int randomNum = rand.nextInt((max - min) + 1) + min;

        return randomNum;
    }

    private final class AriesCallTask implements Runnable {

        String serverName;
        ProxiedPlayer player;

        public AriesCallTask(String serverName, ProxiedPlayer player) {
            this.serverName = serverName;
            this.player = player;
        }

        @Override
        public void run() {
            logger.info("Provisioning " + serverName);
            ProvisionedServer server = new ProvisionedServer(serverName);

            servers.put(serverName, server);

            if (player != null)
            {
                server.connect(player); // add player onto the list

            }

            ServerInfo info = server.provision();

            if (info != null)
            {
                getProxy().getServers().put(serverName, info);

                for (ProxiedPlayer player : server.waiting)
                {
                    server.connect(player);
                }

                return;
            }

            servers.remove(serverName);

            for (ProxiedPlayer player : server.waiting)
            {
                ElasticCreeper.lockedPlayers.remove(player.getName());
                BaseComponent component = new TextComponent("Failed starting server, please try again.");
                component.setColor(ChatColor.RED);
                player.sendMessage(component);
            }

            logger.info("Failed provisioning " + serverName);

        }
    }

    private final class WatchDog implements Runnable
    {

        @Override
        public void run() {

            ArrayList<ProvisionedServer> serversToKill = new ArrayList();

            for (Map.Entry<String, ProvisionedServer> set : servers.entrySet())
            {
                ProvisionedServer server = set.getValue();

                server.update();

                if (server.shouldDie())
                    serversToKill.add(server);
            }

            for (ProvisionedServer server : serversToKill)
            {
                server.spinDown();
                servers.remove(server.bungeeName);
            }
        }
    }
}

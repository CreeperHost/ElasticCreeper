package net.creeperhost;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by Aaron on 03/08/2014.
 */
public class BungeePlugin extends Plugin implements Listener {

    public static HashMap<String, Runnable> serverTasks = new LinkedHashMap();
    public static HashMap<String, ArrayList<ProxiedPlayer>> waitingPlayers = new LinkedHashMap();
    public static HashMap<String, ServerInfo> runningServers = new HashMap();
    static Logger logger = null;

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
        }

        return "{\"status\": \"error\"}";

    }

    public static void spinDownServer(String uuid) {
        Map<String, Object> param = new LinkedHashMap<>();

        logger.info(uuid);

        param.put("key", Config.key);
        param.put("secret", Config.secret);

        JSONObject spindownObj = new JSONObject();

        spindownObj.put("uuid", uuid);

        param.put("data", spindownObj.toString());

        logger.info(makeAPICall("billing", "spindownMinigame", param));
    }

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

    @Override
    public void onEnable() {
        logger = getLogger();

        Config.initConfig(this);

        SSLWorkaround(); // ugh

        this.getProxy().getPluginManager().registerListener(this, this);
    }

    @Override
    public void onDisable() {
        for (ServerInfo info : runningServers.values()) {
            killServer(info);
        }
    }

    private void killServer(ServerInfo info) {
        spinDownServer(info.getName());
        ProxyServer.getInstance().getServers().remove(info.getName()); // IT'S DEAD JIM
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
                        String serverName = in.readUTF();
                        ServerInfo server = bungee.getServerInfo(serverName);
                        if (server != null) {
                            return; // lets let the existing handling logic handle it
                        }

                        for (String prefix : Config.servers.keySet()) {
                            if (serverName.startsWith(prefix)) {
                                makeServer(serverName, prefix, player);
                                e.setCancelled(true);
                                return;
                            }
                        }

                    }
                } else if (subChannel.equals("ConnectOther")) {
                    ProxiedPlayer player = bungee.getPlayer(in.readUTF());
                    if (player != null) {
                        String serverName = in.readUTF();
                        ServerInfo server = bungee.getServerInfo(serverName);
                        if (server != null) {
                            return; // lets let the existing handling logic handle it. No need to duplicate.
                        }

                        for (String prefix : Config.servers.keySet()) {
                            if (serverName.startsWith(prefix)) {
                                makeServer(serverName, prefix, player);
                                e.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            } catch (IOException e1) {
            }
        }
    }

    @EventHandler
    public void onChat(ChatEvent e) {
    }

    public boolean addWaitingPlayer(String server, ProxiedPlayer player) {

        for (ArrayList<ProxiedPlayer> playerInList : waitingPlayers.values()) {
            if (playerInList.contains(player)) {
                // No users should be able to change server if they're waiting. Lets tell them.
                TextComponent component = new TextComponent("You're already waiting to connect to a minigame!");
                component.setColor(ChatColor.RED);
                player.sendMessage(component);
                return false;
            }
        }

        if (!waitingPlayers.containsKey(server)) {
            waitingPlayers.put(server, new ArrayList());
        }

        waitingPlayers.get(server).add(player);

        return true;
    }

    public void makeServer(String serverName, String prefix, ProxiedPlayer player) {
        if (!addWaitingPlayer(serverName, player)) {
            return; // already waiting D: WE NO MAKE SERVER NAOW
        }

        TextComponent component = new TextComponent("Please wait while we connect you to the minigame.");
        component.setColor(ChatColor.YELLOW);
        player.sendMessage(component);

        ScheduledTask scheduledTask = ProxyServer.getInstance().getScheduler().runAsync((Plugin) this, (Runnable) new AriesCallTask(serverName, prefix));
    }

    private final class AriesCallTask implements Runnable {

        String serverName;
        String prefix;

        public AriesCallTask(String serverName, String prefix) {
            ArrayList list = new ArrayList();

            this.serverName = serverName;
            this.prefix = prefix;
        }

        @Override
        public void run() {
            logger.info("Provisioning " + serverName);
            serverTasks.put(serverName, this);
            String json = "{\"status\": \"error\"}";
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("key", Config.key);
            params.put("secret", Config.secret);
            Map<String, Object> serverObj = Config.servers.get(prefix);
            JSONObject obj = new JSONObject(serverObj);
            params.put("data", obj.toString());
            json = makeAPICall("billing", "spinupMinigame", params);

            JSONObject newObj = new JSONObject(json);

            String success = newObj.getString("status");

            if (success.equals("error")) {
                logger.info("Provisioning failed for " + serverName + " :(");
                logger.info(json);
                return;
            }

            String ip = newObj.getString("ip");
            String uuid = newObj.getString("uuid");
            int port = newObj.getInt("port");

            InetSocketAddress socketAddress = new InetSocketAddress(ip, port);

            ServerInfo info = ProxyServer.getInstance().constructServerInfo(uuid, socketAddress, "lolhax", false);
            ProxyServer.getInstance().getServers().put(serverName, info);

            runningServers.put(serverName, info);

            ArrayList<ProxiedPlayer> players = waitingPlayers.get(serverName);

            for (ProxiedPlayer player : players) {
                player.connect(info);
            }

            waitingPlayers.remove(serverName);

            players = waitingPlayers.get(prefix);

            if (players != null)
                for (ProxiedPlayer player : players) {
                    player.connect(info);
                }

            waitingPlayers.remove(prefix);

            serverTasks.remove(serverName);
        }
    }
}

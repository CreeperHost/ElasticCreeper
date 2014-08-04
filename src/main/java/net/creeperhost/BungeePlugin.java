package net.creeperhost;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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

    static Logger logger = null;

    public static HashMap<String, Runnable> serverTasks = new LinkedHashMap();

    public static HashMap<String, ArrayList<ProxiedPlayer>> waitingPlayers = new LinkedHashMap();

    public static HashMap<String, ServerInfo> runningServers = new HashMap();

    private void SSLWorkaround()
    {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
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
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
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

        Config.initConfig();

        SSLWorkaround(); // ugh

        this.getProxy().getPluginManager().registerListener(this, this);

    }

    @EventHandler
    public void onListenChannel(PluginMessageEvent e) {
    }

    @EventHandler
    public void onChat(ChatEvent e) {
        Connection sender = e.getSender();
        if (sender instanceof ProxiedPlayer)
        {
            ProxiedPlayer player = (ProxiedPlayer) sender;
            ServerInfo info = ProxyServer.getInstance().getServerInfo(e.getMessage());
            if (info != null)
                player.connect(info);

            String serverName = e.getMessage();

            for (String prefix : Config.servers.keySet())
            {
                if (serverName.startsWith(prefix))
                {
                    doServerThing(serverName, prefix, player);
                    return;
                }
            }
        }

    }

    public void addWaitingPlayer(String server, ProxiedPlayer player)
    {

        for (ArrayList<ProxiedPlayer> playerInList : waitingPlayers.values())
        {
            playerInList.remove(player);
        }

        if (!waitingPlayers.containsKey(server))
        {
            waitingPlayers.put(server, new ArrayList());
        }

        waitingPlayers.get(server).add(player);
    }

    public void doServerThing(String json, String prefix, ProxiedPlayer player)
    {
        if (serverTasks.containsKey(json))
        {
            addWaitingPlayer(json, player);
            return;
        }

        ScheduledTask scheduledTask = ProxyServer.getInstance().getScheduler().runAsync((Plugin) this, (Runnable) new AriesCallTask(json, prefix, player));
    }

    public static String makeAPICall(String section, String command, Map<String,Object> params)
    {
        URL url = null;
        try {
            url = new URL("https://api.creeperhost.net/" + section + "/" + command);
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
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
            //Catch ALL the things
            e.printStackTrace();
        }

        return "{\"status\": \"error\"}";

    }

    private final class AriesCallTask implements Runnable
    {

        String json;
        String serverName;
        String prefix;

        public AriesCallTask(String serverName, String prefix, ProxiedPlayer player)
        {
            this.json = "{ \"status\" : \"success\",    \"ip\" : \"127.0.0.1\",    \"port\" : 25566,    \"readytime\" : \"192.46348595619\",    \"cost\" : \"0.25\",    \"uuid\" : \"uuidstringhere\"}";
            ArrayList list = new ArrayList();


            addWaitingPlayer(serverName, player);

            this.serverName = serverName;
            this.prefix = prefix;
        }

        @Override
        public void run()
        {
            logger.info("Provisioning " + serverName);
            serverTasks.put(serverName, this);
            String json = "{\"status\": \"error\"}";


            Map<String,Object> params = new LinkedHashMap<>();
            params.put("key", Config.key);
            params.put("secret", Config.secret);

            Map<String, Object> serverObj = Config.servers.get(prefix);



            JSONObject obj = new JSONObject(serverObj);

            params.put("data", obj.toString());


            json = makeAPICall("billing", "spinupMinigame", params);

            JSONObject newObj = new JSONObject(json);

            String success = newObj.getString("status");

            if (success.equals("error"))
            {
                logger.info("Provisioning failed for " + serverName + " :(");
                logger.info(json);
                return;
            }

            String ip = newObj.getString("ip");
            String uuid = newObj.getString("uuid");
            int port = newObj.getInt("port");

            InetSocketAddress socketAddress = new InetSocketAddress(ip, port);

            ServerInfo info = ProxyServer.getInstance().constructServerInfo(uuid, socketAddress, "lolhax", false);
            ProxyServer.getInstance().getServers().put(uuid, info);

            runningServers.put(serverName, info);

            ArrayList<ProxiedPlayer> players = waitingPlayers.get(serverName);

            for (ProxiedPlayer player : players)
            {
                player.connect(info);
            }

            waitingPlayers.remove(serverName);

            players = waitingPlayers.get(prefix);

            if (players != null)
                for (ProxiedPlayer player : players)
                {
                    player.connect(info);
                }

            waitingPlayers.remove(serverName);

/*            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {

            }

            spinDownServer(uuid);*/


            serverTasks.remove(serverName);

        }

        private void spinDownServer(String uuid)
        {
            Map<String,Object> param = new LinkedHashMap<>();

            param.put("key", Config.key);
            param.put("secret", Config.secret);

            JSONObject spindownObj = new JSONObject();
            spindownObj.put("uuid", uuid);

            logger.info(spindownObj.toString());

            param.put("data", spindownObj.toString());

            logger.info(makeAPICall("billing", "spindownMinigame", param));
        }
    }
}

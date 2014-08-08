package net.creeperhost;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Minion 2 on 07/08/2014.
 */
public class ProvisionedServer {

    public ServerInfo info;
    public String bungeeName;
    public String uuid;
    public int players = -1;

    public InetSocketAddress socketAddress;
    public int playerLimit = 9001;

    private boolean killMe = false;

    private boolean provisioning = true;

    public ArrayList<ProxiedPlayer> waiting = new ArrayList();

    public ProvisionedServer(String bungeeName)
    {
        this.bungeeName = bungeeName;
    }

    public ServerInfo provision() {
        Map<String, Object> params = new LinkedHashMap<>();

        params.put("key", Config.key);
        params.put("secret", Config.secret);
        Map<String, Object> serverObj = Config.servers.get(ElasticCreeper.getPrefix(bungeeName));
        JSONObject obj = new JSONObject(serverObj);
        params.put("data", obj.toString());

        String json = ElasticCreeper.makeAPICall("billing", "spinupMinigame", params);
        provisioning = false;

        JSONObject newObj = new JSONObject(json);

        String success = newObj.getString("status");

        if (success.equals("error")) {
            ElasticCreeper.logger.severe(json);
            return null;
        }

        String ip = newObj.getString("ip");
        uuid = newObj.getString("uuid");
        int port = newObj.getInt("port");

        socketAddress = new InetSocketAddress(ip, port);

        info = ProxyServer.getInstance().constructServerInfo(uuid, socketAddress, "lolhax", false);
        ProxyServer.getInstance().getServers().put(bungeeName, info);

        return info;
    }

    public void spinDown() {

        Map<String, Object> param = new LinkedHashMap<>();

        param.put("key", Config.key);
        param.put("secret", Config.secret);

        JSONObject spindownObj = new JSONObject();

        spindownObj.put("uuid", uuid);

        param.put("data", spindownObj.toString());

        ElasticCreeper.logger.info(ElasticCreeper.makeAPICall("billing", "spindownMinigame", param));

        ProxyServer.getInstance().getServers().remove(bungeeName);
    }

    public int timeLeft()
    {
        if (info == null)
        {
            return 2000;
        }
        Map<String, Object> param = new LinkedHashMap<>();

        param.put("key", Config.key);
        param.put("secret", Config.secret);

        JSONObject spindownObj = new JSONObject();

        spindownObj.put("uuid", uuid);

        param.put("data", spindownObj.toString());

        String result = ElasticCreeper.makeAPICall("billing", "timerMinigame", param);

        JSONObject newObj = new JSONObject(result);

        return newObj.getString("status").equals("error") ? 0 : (int) newObj.get("remaining");
    }

    public boolean addTime()
    {
        Map<String, Object> param = new LinkedHashMap<>();

        param.put("key", Config.key);
        param.put("secret", Config.secret);

        JSONObject spindownObj = new JSONObject();

        spindownObj.put("uuid", uuid);

        param.put("data", spindownObj.toString());

        String result = ElasticCreeper.makeAPICall("billing", "extendMinigame", param);

        JSONObject newObj = new JSONObject(result);

        return !newObj.getString("status").equals("error");

    }

    public void refreshStatus()
    {
        if (info == null) return;

        Util.PingCallback callback = new Util.PingCallback(this);

        info.ping(callback);

        while(!callback.isDone)
        {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }

    }

    public void update()
    {
        if (info == null)
            return;

        int oldPlayers = players;

        refreshStatus();

        if (players == 0 && oldPlayers == 0)
        {
            killMe = true;
        }
        if(timeLeft() <= 300 && !killMe && players > 0)
        {
            //We should extend it if we have existing players mid game, not just when new people join!
            if (addTime()) {
                ElasticCreeper.logger.info("Extended server " + bungeeName);
            } else
            {
                ElasticCreeper.logger.info("Failed to extend server " + bungeeName);
            }
        }
    }

    public void connect(ProxiedPlayer player)
    {
        if ((!provisioning && isUp()) && !shouldDie()) {
            player.connect(info);
            ElasticCreeper.lockedPlayers.remove(player.getName());
        } else {
            ElasticCreeper.lockedPlayers.put(player.getName(), player);
            waiting.add(player);
        }
    }

    /*****************************************************
     I believe we'll be better placed with BungeeCord's ServerInfo.ping(Callback <ServerPing> callback) function.
     That returns a 'Players' object with 'Current' and 'Max' integers I believe.
     I'm just too java noob to know what to feed it.
     *****************************************************/
    public String[] currentStatus()
    {
        try {
            InetSocketAddress srv = socketAddress;
            Socket sock = new Socket(srv.getAddress(), srv.getPort());

            sock.setSoTimeout(30);

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());

            out.write(0xFE);

            int b;
            StringBuffer str = new StringBuffer();
            while ((b = in.read()) != -1) {
                if (b != 0 && b > 16 && b != 255 && b != 23 && b != 24) {
                    // Not sure what use the two characters are so I omit them
                    str.append((char) b);
                    System.out.println(b + ":" + ((char) b));
                }
            }

            String[] data = str.toString().split("§");//Will break with colourised MoTD's
            return data; // 0 is MoTD, 1 is current players, 2 is maximum players.

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new String[] {"lol", "0", "0"};

    }

    public boolean isUp()
    {
        return (provisioning || info != null);
    }

    public boolean shouldDie() {
        return killMe;
    }

    public boolean isFull() { return players == playerLimit; }


}

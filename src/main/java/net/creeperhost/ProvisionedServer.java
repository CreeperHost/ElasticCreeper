package net.creeperhost;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
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
    public int playerLimit;

    private boolean killMe = false;

    public ArrayList<ProxiedPlayer> waiting = new ArrayList();

    public ProvisionedServer(String bungeeName)
    {
        this.bungeeName = bungeeName;
    }

    public ServerInfo provision() {
        Map<String, Object> params = new LinkedHashMap<>();

        params.put("key", Config.key);
        params.put("secret", Config.secret);
        Map<String, Object> serverObj = Config.servers.get(BungeePlugin.getPrefix(bungeeName));
        JSONObject obj = new JSONObject(serverObj);
        params.put("data", obj.toString());

        String json = BungeePlugin.makeAPICall("billing", "spinupMinigame", params);

        JSONObject newObj = new JSONObject(json);

        String success = newObj.getString("status");

        if (success.equals("error")) {
            BungeePlugin.logger.severe(json);
            return null;
        }

        String ip = newObj.getString("ip");
        uuid = newObj.getString("uuid");
        int port = newObj.getInt("port");

        InetSocketAddress socketAddress = new InetSocketAddress(ip, port);

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

        BungeePlugin.logger.info(uuid);

        BungeePlugin.logger.info(BungeePlugin.makeAPICall("billing", "spindownMinigame", param));
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

        String result = BungeePlugin.makeAPICall("billing", "timerMinigame", param);

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

        String result = BungeePlugin.makeAPICall("billing", "extendMinigame", param);

        JSONObject newObj = new JSONObject(result);

        return !newObj.getString("status").equals("error");

    }

    public void update()
    {
        if (info == null)
            return;

        int newPlayers = info.getPlayers().size();

        if (players == 0 && newPlayers == 0)
        {
            killMe = true;
        }
        if(timeLeft() <= 300 && !killMe)
        {
            //We should extend it if we have existing players mid game, not just when new people join!
            addTime();
        }

        players = newPlayers;
    }

    public void connect(ProxiedPlayer player)
    {
        if (isUp() && !shouldDie()) {
            player.connect(info);
        } else {
            waiting.add(player);
        }
    }

    public boolean isUp()
    {
        return (info != null);
    }

    public boolean shouldDie() {
        return killMe;
    }
}

package net.creeperhost;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;

/**
 * Created by Minion 2 on 08/08/2014.
 */
public class Util {

    public static boolean isFull(ServerInfo info)
    {
        PingCallback callback = new PingCallback();
        info.ping(callback);

        return callback.isFull();
    }

    public static class PingCallback implements Callback<ServerPing> {

        public boolean isDone = false;

        public ProvisionedServer server;

        public int players = 0;
        public int playerLimit = 0;

        public PingCallback(ProvisionedServer server)
        {
            this.server = server;
        }

        public PingCallback()
        {
        }

        @Override
        public void done(ServerPing result, Throwable error) {
            if (result != null)
            {
                ServerPing.Players playerInfo = result.getPlayers();
                players = playerInfo.getOnline();
                playerLimit = playerInfo.getMax();
                isDone = true;
            } else {
                String status[] = server.currentStatus();
                players = Integer.valueOf(status[1]);
                playerLimit = Integer.valueOf(status[2]);
                isDone = true;
                // lets try our own status
            }

            if (server != null)
            {
                server.players = players;
                server.playerLimit = playerLimit;
            }

        }

        public boolean isFull()
        {
            int counter = 0;
            while (!isDone) {
                try {
                    if (counter >= 20)
                        break;
                    Thread.sleep(50);
                    counter++;
                } catch (InterruptedException e) {
                    break;
                }
            }

            return players == playerLimit;
        }
    }



}

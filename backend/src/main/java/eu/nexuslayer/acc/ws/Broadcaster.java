package eu.nexuslayer.acc.ws;

/** Push channel from the daemon to every connected dashboard. */
public interface Broadcaster {
    void broadcast(String channel, Object payload);
}

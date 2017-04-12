package com.ihenjoy.registry.client.provider.zookeeper;

/**
 * @author chi
 */
public interface StateListener {
    int DISCONNECTED = 0;

    int CONNECTED = 1;

    int RECONNECTED = 2;

    void stateChanged(int state);
}

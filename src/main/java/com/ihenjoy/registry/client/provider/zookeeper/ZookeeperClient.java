package com.ihenjoy.registry.client.provider.zookeeper;

import com.ihenjoy.registry.client.common.URL;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNoNodeException;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.zookeeper.Watcher;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author chi
 */
public class ZookeeperClient {

    private final ZkClient zkClient;
    private final Set<StateListener> stateListeners = new CopyOnWriteArraySet<>();
    private volatile Watcher.Event.KeeperState state = Watcher.Event.KeeperState.SyncConnected;

    public ZookeeperClient(URL url) {
        String server = url.getBackupAddress();
        Assert.hasText(server, "zookeeper settings's server should not be empty!");
        zkClient = new ZkClient(server);
        zkClient.subscribeStateChanges(new IZkStateListener() {
            @Override
            public void handleStateChanged(Watcher.Event.KeeperState state) throws Exception {
                ZookeeperClient.this.state = state;
                if (state == Watcher.Event.KeeperState.Disconnected)
                    stateChanged(StateListener.DISCONNECTED);
                else if (state == Watcher.Event.KeeperState.SyncConnected)
                    stateChanged(StateListener.CONNECTED);
            }

            @Override
            public void handleNewSession() throws Exception {
                stateChanged(StateListener.RECONNECTED);
            }
        });
    }

    public boolean isConnected() {
        return state == Watcher.Event.KeeperState.SyncConnected;
    }

    public List<String> addChildListener(String path, IZkChildListener listener) {
        return zkClient.subscribeChildChanges(path, listener);
    }

    public void removeChildListener(String path, IZkChildListener listener) {
        zkClient.unsubscribeChildChanges(path, listener);
    }

    public void addStateListener(StateListener stateListener) {
        stateListeners.add(stateListener);
    }

    public void removeStateListener(StateListener stateListener) {
        stateListeners.remove(stateListener);
    }

    public Set<StateListener> getSessionListeners() {
        return stateListeners;
    }

    public void stateChanged(int state) {
        for (StateListener sessionListener : getSessionListeners())
            sessionListener.stateChanged(state);
    }

    public void create(String path, boolean ephemeral) {
        int index = path.lastIndexOf('/');
        if (index > 0) {
            create(path.substring(0, index), false);
        }
        try {
            if (ephemeral)
                zkClient.createEphemeral(path);
            else
                zkClient.createPersistent(path);
        } catch (ZkNodeExistsException e) {
            // if node is exists,we should not handle
        }
    }

    public void delete(String path) {
        try {
            zkClient.delete(path);
        } catch (ZkNoNodeException ex) {

        }
    }

    public void destroy() {
        if (this.zkClient != null)
            this.zkClient.close();
    }

}

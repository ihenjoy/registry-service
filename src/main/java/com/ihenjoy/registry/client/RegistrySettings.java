package com.ihenjoy.registry.client;

import com.ihenjoy.registry.client.provider.RegistryType;
import com.ihenjoy.registry.client.common.URL;

/**
 * @author chi
 */
public class RegistrySettings {

    private URL server;

    private int localPort;

    private RegistryType registryType;

    public RegistryType getRegistryType() {
        return registryType;
    }

    public void setRegistryType(RegistryType registryType) {
        this.registryType = registryType;
    }

    public URL getServer() {
        return server;
    }

    public void setServer(URL server) {
        this.server = server;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }
}

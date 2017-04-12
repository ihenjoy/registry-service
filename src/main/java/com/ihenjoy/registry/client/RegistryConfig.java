package com.ihenjoy.registry.client;


import com.ihenjoy.registry.client.api.NotifyListener;
import com.ihenjoy.registry.client.api.RegistryService;
import com.ihenjoy.registry.client.common.URL;
import com.ihenjoy.registry.client.util.NetworkUtils;

import java.text.MessageFormat;

/**
 * @author chi
 */
public class RegistryConfig {
    private final int port;
    private final RegistryService registryService;

    public RegistryConfig(RegistryService registryService, int port) {
        this.port = port;
        this.registryService = registryService;
    }


    public void register(URL url) {
        URL registerUrl = url.withParameter("_t", MessageFormat.format("{0}:{1}", NetworkUtils.localIP(), port));
        this.registryService.register(registerUrl);
    }


    public void subscribe(URL url, NotifyListener notifyListener) {
        if (url.getPort() <= 0) url.setPort(this.port);
        this.registryService.subscribe(url, notifyListener);
    }
}

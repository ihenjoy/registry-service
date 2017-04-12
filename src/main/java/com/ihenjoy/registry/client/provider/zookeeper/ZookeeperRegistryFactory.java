package com.ihenjoy.registry.client.provider.zookeeper;

import com.ihenjoy.registry.client.common.URL;
import com.ihenjoy.registry.client.api.RegistryService;
import com.ihenjoy.registry.client.api.RegistryFactory;

/**
 * @author chi
 */
public class ZookeeperRegistryFactory implements RegistryFactory {
    @Override
    public RegistryService getRegistry(URL url) {
        return new ZookeeperRegistry(url);
    }
}

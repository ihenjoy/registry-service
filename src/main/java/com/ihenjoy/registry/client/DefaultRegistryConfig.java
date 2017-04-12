package com.ihenjoy.registry.client;

import com.ihenjoy.registry.client.api.RegistryService;
import com.ihenjoy.registry.client.provider.zookeeper.ZookeeperRegistryFactory;
import org.springframework.context.annotation.Bean;

/**
 * @author chi
 */
public abstract class DefaultRegistryConfig {

    @Bean
    public RegistrySettings registrySettings() {
        return new RegistrySettings();
    }

    @Bean
    public RegistryService registryService() {
        RegistryService registryService = createRegistryService();
        RegistrySettings settings = registrySettings();
        registry(new RegistryConfig(registryService, settings.getLocalPort()));
        return registryService;
    }

    private RegistryService createRegistryService() {
        RegistrySettings settings = registrySettings();
        switch (settings.getRegistryType()) {
            case Zookeeper:
                return new ZookeeperRegistryFactory().getRegistry(settings.getServer());
            default:
                throw new IllegalStateException("not support type :" + settings.getRegistryType());
        }
    }


    protected abstract void registry(RegistryConfig registryConfig);

}

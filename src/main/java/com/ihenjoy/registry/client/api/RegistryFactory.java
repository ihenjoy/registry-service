package com.ihenjoy.registry.client.api;

import com.ihenjoy.registry.client.common.URL;

/**
 * @author chi
 */
public interface RegistryFactory {
    /**
     * 获取注册中心服务
     *
     * @param url 注册中心地址，不允许为空
     */
    RegistryService getRegistry(URL url);
}

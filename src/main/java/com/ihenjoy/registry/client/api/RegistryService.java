package com.ihenjoy.registry.client.api;

import com.ihenjoy.registry.client.common.URL;

/**
 * @author chi
 *         注册接口
 */
public interface RegistryService {

    /**
     * 注册服务
     *
     * @param url 注册服务信息
     */
    void register(URL url);

    /**
     * 取消注册
     *
     * @param url 注册服务信息
     */
    void unregister(URL url);


    /**
     * 订阅相关服务
     *
     * @param url            订阅服务信息
     * @param notifyListener 变更的监听器
     */
    void subscribe(URL url, NotifyListener notifyListener);

    /**
     * 取消订阅相关服务
     *
     * @param url            订阅服务信息
     * @param notifyListener 变更的监听器
     */
    void unSubscribe(URL url, NotifyListener notifyListener);
}

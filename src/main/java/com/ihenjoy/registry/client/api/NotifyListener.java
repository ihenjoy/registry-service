package com.ihenjoy.registry.client.api;

import com.ihenjoy.registry.client.common.URL;

import java.util.List;

/**
 * @author chi
 */
public interface NotifyListener {
    /**
     * change event
     */
    void notify(List<URL> urls);
}

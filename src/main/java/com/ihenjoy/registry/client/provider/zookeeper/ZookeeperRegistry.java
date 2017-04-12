package com.ihenjoy.registry.client.provider.zookeeper;


import com.ihenjoy.registry.client.api.Node;
import com.ihenjoy.registry.client.api.NotifyListener;
import com.ihenjoy.registry.client.api.RegistryService;
import com.ihenjoy.registry.client.common.Constants;
import com.ihenjoy.registry.client.common.URL;
import com.ihenjoy.registry.client.common.exception.RegistryException;
import com.ihenjoy.registry.client.util.ConcurrentHashSet;
import org.I0Itec.zkclient.IZkChildListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author chi
 */
public class ZookeeperRegistry implements RegistryService, Node, DisposableBean {

    final Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private final Set<URL> failedRegistered = new ConcurrentHashSet<>();
    private final URL registryUrl;
    private final ConcurrentMap<URL, Set<NotifyListener>> failedSubscribed = new ConcurrentHashMap<>();
    private final ZookeeperClient zookeeperClient;
    private final Set<URL> registered = new ConcurrentHashSet<>();
    private final ConcurrentMap<URL, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService reConnectExecutor = Executors.newScheduledThreadPool(1);
    private final ScheduledFuture<?> reConnectFuture;
    private final ScheduledFuture<?> retryFuture;
    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, IZkChildListener>> zkListeners = new ConcurrentHashMap<>();
    // 客户端获取过程锁，锁定客户端实例的创建过程，防止重复的客户端
    private final ReentrantLock clientLock = new ReentrantLock();

    public ZookeeperRegistry(URL url) {
        this.registryUrl = url;
        this.zookeeperClient = new ZookeeperClient(url);
        this.zookeeperClient.addStateListener(new StateListener() {
            @Override
            public void stateChanged(int state) {
                if (state == RECONNECTED) {
                    try {
                        recover();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        });

        // 重新注册
        this.retryFuture = retryExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                retry();
            }
        }, Constants.DEFAULT_REGISTRY_RETRY_PERIOD, Constants.DEFAULT_REGISTRY_RETRY_PERIOD, TimeUnit.MILLISECONDS);

        // 重新连接
        this.reConnectFuture = reConnectExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                connect();
            }
        }, Constants.RECONNECT_PERIOD_DEFAULT, Constants.RECONNECT_PERIOD_DEFAULT, TimeUnit.MILLISECONDS);
    }

    @Override
    public void register(URL url) {
        try {
            failedRegistered.remove(url);
            this.zookeeperClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
            registered.add(url);
        } catch (Throwable e) {
            failedRegistered.add(url);
            throw new RegistryException("Failed to register " + url + "to zookeeper ,cause :" + e.getMessage(), e);
        }
    }

    @Override
    public void unregister(URL url) {
        try {
            this.zookeeperClient.delete(toUrlPath(url));
        } catch (Throwable e) {
            throw new RegistryException("Failed to unregister " + url + " to zookeeper , cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(final URL url, final NotifyListener notifyListener) {
        removeFailedSubscribed(url, notifyListener);
        try {
            Set<NotifyListener> sublisteners = subscribed.get(url);
            if (sublisteners == null) {
                subscribed.putIfAbsent(url, new ConcurrentHashSet<NotifyListener>());
                sublisteners = subscribed.get(url);
            }
            sublisteners.add(notifyListener);
            register(url.withParameter(Constants.CATEGORY_KEY, Constants.CONSUMER_CATEGORY)); // 如果有订阅，记录下当前的信息
            List<URL> urls = new ArrayList<>();
            for (String path : toCategoriesPath(url.withParameter(Constants.CATEGORY_KEY, Constants.ANY_VALUE))) {
                ConcurrentMap<NotifyListener, IZkChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.put(url, new ConcurrentHashMap<NotifyListener, IZkChildListener>());
                    listeners = zkListeners.get(url);
                }

                IZkChildListener listener = listeners.get(notifyListener);
                if (listener == null) {
                    listeners.put(notifyListener, new IZkChildListener() {
                        @Override
                        public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
                            ZookeeperRegistry.this.notify(url, notifyListener, toUrlWithEmpty(url, parentPath, currentChilds));
                        }
                    });
                    listener = listeners.get(notifyListener);
                }


                zookeeperClient.create(path, false);
                List<String> children = zookeeperClient.addChildListener(path, listener);
                if (!CollectionUtils.isEmpty(children)) {
                    urls.addAll(toUrlWithEmpty(url, path, children));
                }

            }

            notify(url, notifyListener, urls);
        } catch (Throwable e) {
            addFailedSubscribed(url, notifyListener);
            throw new RegistryException("Failed to subscribe " + url + " to zookeeper , cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void unSubscribe(URL url, NotifyListener notifyListener) {
        try {

            ConcurrentMap<NotifyListener, IZkChildListener> listeners = zkListeners.get(url);
            if (listeners != null) {
                IZkChildListener zkListener = listeners.get(notifyListener);
                if (zkListener != null) {
                    zookeeperClient.removeChildListener(toUrlPath(url), zkListener);
                }
            }
        } catch (Throwable e) {
            throw new RegistryException("Failed to unsubscribe " + url + " to zookeeper , cause: " + e.getMessage(), e);
        }
    }

    public Future<?> getRetryFuture() {
        return retryFuture;
    }

    public Set<URL> getRegistered() {
        return registered;
    }

    public Map<URL, Set<NotifyListener>> getSubscribed() {
        return subscribed;
    }

    protected void recover() throws Exception {
        //register
        Set<URL> recoverRegistered = new HashSet<>(getRegistered());
        if (!recoverRegistered.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover register url " + recoverRegistered);
            }
            for (URL url : recoverRegistered) {
                failedRegistered.add(url);
            }
        }
        //subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<URL, Set<NotifyListener>>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    addFailedSubscribed(url, listener);
                }
            }
        }
    }

    private void addFailedSubscribed(URL url, NotifyListener listener) {
        Set<NotifyListener> listeners = failedSubscribed.get(url);
        if (listeners == null) {
            failedSubscribed.putIfAbsent(url, new ConcurrentHashSet<NotifyListener>());
            listeners = failedSubscribed.get(url);
        }
        listeners.add(listener);
    }

    private void removeFailedSubscribed(URL url, NotifyListener listener) {
        Set<NotifyListener> listeners = failedSubscribed.get(url);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    protected void retry() {
        if (!failedRegistered.isEmpty()) {
            Set<URL> failed = new HashSet<>(failedRegistered);
            if (!failed.isEmpty()) {
                for (URL url : failed) {
                    try {
                        register(url);
                        failedRegistered.remove(url);
                    } catch (Throwable e) {
                        logger.warn("Failed to retry register " + url + " ,waiting for again ,cause: " + e.getMessage(), e);
                    }
                }
            }
        }

        if (!failedSubscribed.isEmpty()) {
            Map<URL, Set<NotifyListener>> failed = new HashMap<URL, Set<NotifyListener>>(failedSubscribed);
            for (Map.Entry<URL, Set<NotifyListener>> entry : new HashMap<>(failed).entrySet()) {
                if (entry.getValue() == null || entry.getValue().size() == 0) {
                    failed.remove(entry.getKey());
                }
            }
            if (!failed.isEmpty()) {
                for (Map.Entry<URL, Set<NotifyListener>> entry : failed.entrySet()) {
                    URL url = entry.getKey();
                    Set<NotifyListener> listeners = entry.getValue();
                    for (NotifyListener listener : listeners) {
                        try {
                            subscribe(url, listener);
                            listeners.remove(listener);
                        } catch (Throwable t) {
                            logger.warn("Failed to retry subscribe " + failed + ", waiting for again, cause: " + t.getMessage(), t);
                        }
                    }
                }
            }
        }

    }

    private String toUrlPath(URL url) {
        return toCategoryPath(url) + Constants.PATH_SEPARATOR + url.encode(url.toFullString());
    }

    public String toServicePath(URL url) {
        String service = url.getService();
        return toRootDir() + service;
    }

    private String toRootDir() {
        return Constants.PATH_SEPARATOR + Constants.DEFAULT_ROOT + Constants.PATH_SEPARATOR;
    }

    private String toCategoryPath(URL url) {
        return toServicePath(url) + Constants.PATH_SEPARATOR + url.getParameter(Constants.CATEGORY_KEY, Constants.PROVIDER_CATEGORY);
    }

    private String[] toCategoriesPath(URL url) {
        String[] categories;
        if (Constants.ANY_VALUE.equals(url.getParameter(Constants.CATEGORY_KEY, ""))) {
            categories = new String[]{Constants.PROVIDER_CATEGORY, Constants.CONSUMER_CATEGORY};
        } else {
            categories = new String[]{Constants.PROVIDER_CATEGORY};
        }
        String[] paths = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            paths[i] = toServicePath(url) + Constants.PATH_SEPARATOR + categories[i];
        }
        return paths;
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList<>();
        if (CollectionUtils.isEmpty(providers)) return urls;
        for (String provider : providers) {
            provider = URL.decode(provider);
            if (provider.contains("://")) {
                URL url = URL.valueOf(provider);
                if (StringUtils.equalsIgnoreCase(consumer.getService(), url.getService())) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }


    private List<URL> toUrlWithEmpty(URL consumer, String path, List<String> providers) {
        List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
        if (urls.isEmpty()) {
            int i = path.lastIndexOf('/');
            String category = i < 0 ? path : path.substring(i + 1);
            URL url = consumer.withProtocol(Constants.EMPTY_PROTOCOL).withParameter(Constants.CATEGORY_KEY, category);
            urls.add(url);
        }
        return urls;
    }

    public void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            if (StringUtils.equals(u.getService(), url.getService())) {
                String category = u.getParameter(Constants.CATEGORY_KEY, Constants.PROVIDER_CATEGORY);
                List<URL> categoryList = result.get(category);
                if (categoryList == null) {
                    categoryList = new ArrayList<>();
                    result.put(category, categoryList);
                }
                categoryList.add(u);
            }
        }
        if (CollectionUtils.isEmpty(result)) {
            return;
        }

        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            List<URL> categoryList = entry.getValue();
            listener.notify(categoryList);
        }
    }

    public void destroy() throws Exception {
        try {
            if (!retryFuture.isCancelled())
                retryFuture.cancel(true);
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
        try {
            if (!reConnectFuture.isCancelled())
                reConnectFuture.cancel(true);
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }

    }

    private URL getUrl() {
        return this.registryUrl;
    }

    private void connect() {
        try {

            if (isAvailable()) {
                return;
            }
            if (logger.isInfoEnabled()) {
                logger.info("Reconnect to registry " + getUrl());
            }
            checkLock();

        } catch (Throwable t) {

            logger.error("Failed to connect to registry " + getUrl().getAddress() + ", cause: " + t.getMessage(), t);
        }
    }

    private void checkLock() throws Throwable {
        clientLock.lock();
        try {

            if (isAvailable()) {
                return;
            }
            recover();
        } finally {
            clientLock.unlock();
        }
    }


    @Override
    public boolean isAvailable() {
        return this.zookeeperClient.isConnected();
    }
}

package com.ihenjoy.registry.client.common;


import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author chi
 */
public class URL {
    private String protocol;
    private String host;
    private int port;
    private String service;
    private Map<String, String> parameters;
    private transient volatile String string;

    public URL(String protocol, String host, int port, String path) {
        this(protocol, host, port, path, null);
    }

    public URL(String protocol, String host, int port, String path, Map<String, String> parameters) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.service = path;
        if (parameters == null)
            this.parameters = Collections.unmodifiableMap(new HashMap<String, String>());
        else
            this.parameters = Collections.unmodifiableMap(parameters);
    }


    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String[] getParameter(String key, String[] defaultValue) {
        String value = getParameter(key);
        if (isEmpty(value))
            return defaultValue;
        return Constants.COMMA_SPLIT_PATTERN.split(value);
    }

    public String getParameter(String key) {
        return parameters.get(key);
    }

    public String getParameter(String key, String defaultValue) {
        String value = getParameter(key);
        if (isEmpty(value)) {
            return defaultValue;
        }
        return value;
    }

    public boolean getParameter(String key, boolean defaultValue) {
        String value = getParameter(key);
        if (isEmpty(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public String toServerString() {
        return buildString(false, false);
    }

    public String toFullString() {
        return buildString(true, true);
    }

    private boolean isEmpty(String value) {
        return !StringUtils.hasText(value);
    }

    private String buildString(boolean appendParameter, boolean appendService, String... parameters) {
        StringBuilder buf = new StringBuilder();
        if (isEmpty(this.protocol)) {
            buf.append(protocol);
            buf.append("://");
        }

        if (!isEmpty(this.host)) {
            buf.append(host);
            if (port > 0) {
                buf.append(':');
                buf.append(port);
            }
        }
        if (appendService && !isEmpty(this.service)) {
            buf.append('/');
            buf.append(service);
        }
        if (appendParameter) {
            buildParameters(buf, true, parameters);
        }
        return buf.toString();
    }

    private void buildParameters(StringBuilder buf, boolean concat, String[] parameters) {
        if (CollectionUtils.isEmpty(getParameters())) return;
        List<String> includes = parameters == null || parameters.length == 0 ? null : Arrays.asList(parameters);
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(getParameters()).entrySet()) {
            if (entry.getKey() != null && entry.getKey().length() > 0
                    && (includes == null || includes.contains(entry.getKey()))) {
                if (first) {
                    append(buf, concat);
                    first = false;
                } else {
                    buf.append('&');
                }
                buf.append(entry.getKey());
                buf.append('=');
                buf.append(entry.getValue() == null ? "" : entry.getValue().trim());
            }
        }

    }

    private void append(StringBuilder builder, boolean concat) {
        if (concat)
            builder.append('?');
    }

    public static String encode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static String decode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public URL withParameter(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) return this;
        if (value.equalsIgnoreCase(this.parameters.get(key))) return this;

        Map<String, String> newParameter = new HashMap<>(this.parameters);
        newParameter.put(key, value);
        return new URL(protocol, host, port, service, newParameter);
    }

    public URL withProtocol(String newProtocol) {
        return new URL(newProtocol, this.host, this.port, this.service, this.parameters);
    }


    public static URL valueOf(String value) {
        String url = value;
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("url == null");
        }

        url = url.trim();

        String protocol = null;
        String host = null;
        int port = 0;
        String service = null;
        Map<String, String> parameters = null;
        int i = url.indexOf('?'); // seperator between body and parameters
        if (i >= 0) {
            String[] parts = url.substring(i + 1).split("\\&");
            parameters = new HashMap<>();
            addParameter(parts, parameters);
            url = url.substring(0, i);
        }
        i = url.indexOf("://");

        if (i == 0) throw new IllegalStateException("url missing protocol: \"" + url + "\"");
        protocol = url.substring(0, i);
        url = url.substring(i + 3);


        i = url.indexOf('/');
        if (i >= 0) {
            service = url.substring(i + 1);
            url = url.substring(0, i);
        }
        i = url.indexOf(':');
        if (i >= 0 && i < url.length() - 1) {
            port = Integer.parseInt(url.substring(i + 1));
            url = url.substring(0, i);
        }
        if (url.length() > 0) host = url;
        return new URL(protocol, host, port, service, parameters);
    }

    private static void addParameter(String[] parts, Map<String, String> parameters) {
        for (String part : parts) {
            part = part.trim();
            if (part.length() > 0) {
                int j = part.indexOf('=');
                if (j >= 0) {
                    parameters.put(part.substring(0, j), part.substring(j + 1));
                } else {
                    parameters.put(part, part);
                }
            }
        }
    }

    public String getAddress() {
        return port <= 0 ? host : host + ":" + port;
    }

    public String getBackupAddress() {
        return getBackupAddress(0);
    }

    public String getBackupAddress(int defaultPort) {
        StringBuilder address = new StringBuilder(appendDefaultPort(getAddress(), defaultPort));
        String[] backups = getParameter(Constants.BACKUP_KEY, new String[0]);
        if (backups != null && backups.length > 0) {
            for (String backup : backups) {
                address.append(',');
                address.append(appendDefaultPort(backup, defaultPort));
            }
        }
        return address.toString();
    }

    private String appendDefaultPort(String address, int defaultPort) {
        if (!isEmpty(address)
                && defaultPort > 0) {
            int i = address.indexOf(':');
            if (i < 0) {
                return address + ":" + defaultPort;
            } else if (Integer.parseInt(address.substring(i + 1)) == 0) {
                return address.substring(0, i + 1) + defaultPort;
            }
        }
        return address;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((service == null) ? 0 : service.hashCode());
        result = prime * result + port;
        result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        URL other = (URL) obj;
        if (host == null) {
            if (other.host != null)
                return false;
        } else if (!host.equals(other.host))
            return false;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters))
            return false;

        if (service == null) {
            if (other.service != null)
                return false;
        } else if (!service.equals(other.service))
            return false;
        if (port != other.port)
            return false;
        if (protocol == null) {
            if (other.protocol != null)
                return false;
        } else if (!protocol.equals(other.protocol))
            return false;
        return true;
    }

    public String toString() {
        if (string != null) {
            return string;
        }
        return string = buildString(true, true);
    }
}

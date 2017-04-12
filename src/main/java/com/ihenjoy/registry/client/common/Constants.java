package com.ihenjoy.registry.client.common;

import java.util.regex.Pattern;

/**
 * @author chi
 */
public class Constants {

    public static final String PROVIDER = "provider";

    public static final String CONSUMER = "consumer";

    public static final String PROVIDER_CATEGORY = "providers";

    public static final String CONSUMER_CATEGORY = "consumers";

    public static final String EMPTY_PROTOCOL = "empty";

    public static final String CATEGORY_KEY = "category";

    public static final String DYNAMIC_KEY = "dynamic";

    public static final Pattern COMMA_SPLIT_PATTERN = Pattern
            .compile("\\s*[,]+\\s*");

    public static final String ANY_VALUE = "*";

    public static final String PATH_SEPARATOR = "/";

    public static final String DEFAULT_ROOT = "root";

    public static final int DEFAULT_REGISTRY_RETRY_PERIOD = 5 * 1000;

  
    public static final int RECONNECT_PERIOD_DEFAULT = 3 * 1000;

    public static final String BACKUP_KEY = "backup";
}

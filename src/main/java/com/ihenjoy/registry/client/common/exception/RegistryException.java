package com.ihenjoy.registry.client.common.exception;

/**
 * @author chi
 */
public final class RegistryException extends RuntimeException {

    public RegistryException() {
        super();
    }

    public RegistryException(String msg) {
        super(msg);
    }

    public RegistryException(String msg, Throwable cause) {
        super(msg, cause);
    }


}

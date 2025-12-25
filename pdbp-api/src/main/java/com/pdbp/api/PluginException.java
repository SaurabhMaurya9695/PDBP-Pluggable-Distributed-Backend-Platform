package com.pdbp.api;

/**
 * Exception thrown by plugins during lifecycle operations.
 * This is a checked exception to force proper error handling.
 * making error handling clearer and more specific.
 */
public class PluginException extends Exception {

    private static final long serialVersionUID = 1L;

    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }

    public PluginException(Throwable cause) {
        super(cause);
    }
}


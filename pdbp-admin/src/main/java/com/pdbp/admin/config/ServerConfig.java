package com.pdbp.admin.config;

/**
 * Server configuration parameters.
 *
 * @author Saurabh Maurya
 */
public final class ServerConfig {

    private final int port;
    private final String pluginDirectory;

    public ServerConfig(int port, String pluginDirectory) {
        this.port = port;
        this.pluginDirectory = pluginDirectory;
    }

    public int getPort() {
        return port;
    }

    public String getPluginDirectory() {
        return pluginDirectory;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(8080, "work");
    }
}


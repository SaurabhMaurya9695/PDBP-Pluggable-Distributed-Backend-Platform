package com.pdbp.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses command-line arguments into ServerConfig.
 *
 * @author Saurabh Maurya
 */
public final class CommandLineParser {

    private static final Logger logger = LoggerFactory.getLogger(CommandLineParser.class);
    private static final String PORT_FLAG = "--port";
    private static final String PLUGIN_DIR_FLAG = "--plugin-dir";

    private CommandLineParser() {
        // Utility class
    }

    /**
     * Parses command-line arguments into ServerConfig.
     *
     * @param args command-line arguments
     * @return ServerConfig with parsed values or defaults
     */
    public static ServerConfig parse(String[] args) {
        ServerConfig defaults = ServerConfig.defaults();
        int port = defaults.getPort();
        String pluginDir = defaults.getPluginDirectory();

        for (int i = 0; i < args.length; i++) {
            if (PORT_FLAG.equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                    if (port < 1 || port > 65535) {
                        logger.warn("Invalid port: {}. Using default: {}", port, defaults.getPort());
                        port = defaults.getPort();
                    }
                } catch (NumberFormatException ignored) {
                    logger.warn("Invalid port format: {}. Using default: {}", args[i + 1], defaults.getPort());
                }
                i++; // Skip next argument as it's the port value
            } else if (PLUGIN_DIR_FLAG.equals(args[i]) && i + 1 < args.length) {
                pluginDir = args[i + 1];
                i++; // Skip next argument as it's the directory value
            }
        }

        return new ServerConfig(port, pluginDir);
    }
}


package com.pdbp.admin;

import com.pdbp.admin.config.CommandLineParser;
import com.pdbp.admin.config.ServerConfig;
import com.pdbp.admin.service.LogRotationService;
import com.pdbp.controller.PluginController;
import com.pdbp.core.PluginDiscoveryService;
import com.pdbp.core.PluginManager;
import com.pdbp.core.PluginServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.port;

/**
 * Main entry point for PDBP runtime.
 *
 * @author Saurabh Maurya
 */
public class PDBPServer {

    private static final Logger logger = LoggerFactory.getLogger(PDBPServer.class);
    private static final String VERSION = "1.0-SNAPSHOT";

    private final ServerConfig config;
    private final PluginController pluginController;
    private final ServerLifecycle lifecycle;
    private final LogRotationService logRotationService;

    /**
     * Creates a PDBP server with default configuration.
     */
    public PDBPServer() {
        this(ServerConfig.defaults());
    }

    /**
     * Creates a PDBP server with specified configuration.
     *
     * @param config server configuration
     */
    public PDBPServer(ServerConfig config) {
        this.config = config;
        logStartupBanner();
        
        // Initialize log rotation service
        this.logRotationService = new LogRotationService();
        logRotationService.start();
        
        PluginManager pluginManager = new PluginManager();
        PluginDiscoveryService pluginDiscoveryService = new PluginDiscoveryService(config.getPluginDirectory());
        PluginServiceAdapter pluginService = new PluginServiceAdapter(pluginManager, pluginDiscoveryService);
        this.pluginController = new PluginController(pluginService);
        this.lifecycle = new ServerLifecycle(pluginManager);
        this.lifecycle.setLogRotationService(logRotationService);

        startServer();
        registerShutdownHook();
        logServerReady();
    }

    /**
     * Starts the REST API server.
     */
    private void startServer() {
        port(config.getPort());
        pluginController.registerRoutes();
    }

    /**
     * Registers shutdown hook for graceful shutdown.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(lifecycle::shutdown));
    }

    /**
     * Logs startup banner.
     */
    private void logStartupBanner() {
        logger.info("PDBP - Pluggable Distributed Backend Platform v{}", VERSION);
    }

    /**
     * Logs server ready message with essential information.
     */
    private void logServerReady() {
        logger.info("Server started on port {} | Plugin directory: {}", config.getPort(), config.getPluginDirectory());
        logger.info("API: http://localhost:{}/api/plugins | Health: http://localhost:{}/health", config.getPort(),
                config.getPort());
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments (--port, --plugin-dir)
     */
    public static void main(String[] args) {
        ServerConfig config = CommandLineParser.parse(args);
        new PDBPServer(config);
    }
}


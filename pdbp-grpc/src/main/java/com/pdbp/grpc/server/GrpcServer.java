package com.pdbp.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server for plugin communication.
 * Manages server lifecycle and exposes PluginService.
 *
 * @author Saurabh Maurya
 */
public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    private static final int DEFAULT_PORT = 9090;

    private final Server server;
    private final int port;

    public GrpcServer(PluginRegistry pluginRegistry, int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
                .addService(new PluginServiceImpl(pluginRegistry))
                .build();
    }

    public GrpcServer(PluginRegistry pluginRegistry) {
        this(pluginRegistry, DEFAULT_PORT);
    }

    /**
     * Starts the gRPC server.
     *
     * @throws IOException if server fails to start
     */
    public void start() throws IOException {
        server.start();
        logger.info("gRPC server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server...");
            try {
                GrpcServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error shutting down gRPC server", e);
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Stops the gRPC server.
     *
     * @throws InterruptedException if interrupted during shutdown
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("gRPC server did not terminate gracefully");
                }
            }
            logger.info("gRPC server stopped");
        }
    }

    /**
     * Blocks until the server is terminated.
     *
     * @throws InterruptedException if interrupted
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Gets the server port.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }
}


package com.pdbp.grpc.example;

import com.pdbp.api.GrpcService;
import com.pdbp.api.Plugin;
import com.pdbp.api.PluginContext;
import com.pdbp.api.PluginException;
import com.pdbp.api.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Example plugin demonstrating gRPC usage.
 * This plugin calls other plugins via gRPC.
 *
 * @author Saurabh Maurya
 */
public class GrpcExamplePlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(GrpcExamplePlugin.class);

    private PluginContext context;
    private GrpcService.GrpcClient grpcClient;
    private ScheduledExecutorService executor;
    private volatile boolean running = false;

    @Override
    public String getName() {
        return "grpc-example-plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void init(PluginContext context) throws PluginException {
        this.context = context;
        logger.info("Initializing GrpcExamplePlugin v{}", getVersion());

        // Get gRPC service from context
        Optional<GrpcService> grpcServiceOpt = context.getService(GrpcService.class);
        if (grpcServiceOpt.isPresent()) {
            // Create gRPC client (default: localhost:9090)
            grpcClient = grpcServiceOpt.get().createClient();
            logger.info("gRPC client created successfully");
        } else {
            logger.warn("gRPC service not available. gRPC features will be disabled.");
        }

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GrpcExamplePlugin-Worker");
            t.setDaemon(true);
            return t;
        });

        logger.info("GrpcExamplePlugin initialized");
    }

    @Override
    public void start() throws PluginException {
        logger.info("Starting GrpcExamplePlugin...");
        running = true;

        // Start periodic gRPC calls
        executor.scheduleWithFixedDelay(this::performGrpcCalls, 5, 10, TimeUnit.SECONDS);

        logger.info("GrpcExamplePlugin started - will make gRPC calls every 10 seconds");
    }

    @Override
    public void stop() throws PluginException {
        logger.info("Stopping GrpcExamplePlugin...");
        running = false;

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close gRPC client
        if (grpcClient != null) {
            grpcClient.close();
            logger.info("gRPC client closed");
        }

        logger.info("GrpcExamplePlugin stopped");
    }

    @Override
    public void destroy() {
        logger.info("GrpcExamplePlugin destroyed");
    }

    @Override
    public PluginState getState() {
        return running ? PluginState.STARTED : PluginState.STOPPED;
    }

    /**
     * Performs gRPC calls to demonstrate usage.
     */
    private void performGrpcCalls() {
        if (!running || grpcClient == null) {
            return;
        }

        try {
            // Example 1: Health check for example-plugin
            logger.info("=== gRPC Health Check ===");
            boolean isHealthy = grpcClient.healthCheck("example-plugin");
            logger.info("example-plugin health: {}", isHealthy ? "HEALTHY" : "UNHEALTHY");

            // Example 2: Call a plugin method
            logger.info("=== gRPC Call Plugin ===");
            Map<String, String> parameters = new HashMap<>();
            parameters.put("action", "test");
            parameters.put("timestamp", String.valueOf(System.currentTimeMillis()));

            String response = grpcClient.callPlugin("example-plugin", "process", parameters);
            logger.info("gRPC call response: {}", response);

            // Example 3: Try calling observability-plugin
            logger.info("=== gRPC Call Observability Plugin ===");
            boolean obsHealthy = grpcClient.healthCheck("observability-plugin");
            logger.info("observability-plugin health: {}", obsHealthy ? "HEALTHY" : "UNHEALTHY");

            if (obsHealthy) {
                Map<String, String> obsParams = new HashMap<>();
                obsParams.put("query", "metrics");
                String obsResponse = grpcClient.callPlugin("observability-plugin", "getMetrics", obsParams);
                logger.info("Observability plugin response: {}", obsResponse);
            }

        } catch (GrpcService.GrpcException e) {
            logger.error("gRPC call failed: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in gRPC calls", e);
        }
    }
}


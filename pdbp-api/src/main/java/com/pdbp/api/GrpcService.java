package com.pdbp.api;

/**
 * gRPC service interface for plugin-to-plugin communication.
 * Allows plugins to call other plugins via gRPC.
 *
 * @author Saurabh Maurya
 */
public interface GrpcService extends PlatformService {

    /**
     * Creates a gRPC client for communicating with other plugins.
     *
     * @param host the target host (default: localhost)
     * @param port the target port (default: 9090)
     * @return gRPC client instance
     */
    GrpcClient createClient(String host, int port);

    /**
     * Creates a gRPC client for local communication (localhost:9090).
     *
     * @return gRPC client instance
     */
    default GrpcClient createClient() {
        return createClient("localhost", 9090);
    }

    /**
     * gRPC client interface for plugin-to-plugin communication.
     */
    interface GrpcClient extends AutoCloseable {

        /**
         * Unary call: Simple request-response.
         *
         * @param pluginName the target plugin name
         * @param method     the method to call
         * @param parameters request parameters
         * @return the response data as string
         * @throws GrpcException if call fails
         */
        String callPlugin(String pluginName, String method, java.util.Map<String, String> parameters)
                throws GrpcException;

        /**
         * Health check for a plugin.
         *
         * @param pluginName the plugin name
         * @return true if plugin is healthy, false otherwise
         */
        boolean healthCheck(String pluginName);

        @Override
        void close();
    }

    /**
     * Exception thrown by gRPC operations.
     */
    class GrpcException extends Exception {

        public GrpcException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


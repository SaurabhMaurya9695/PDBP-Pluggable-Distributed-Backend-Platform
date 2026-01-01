package com.pdbp.core.grpc;

import com.pdbp.api.GrpcService;
import com.pdbp.grpc.client.GrpcClientException;
import com.pdbp.grpc.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Adapter that implements GrpcService interface using the gRPC client.
 *
 * @author Saurabh Maurya
 */
public class GrpcServiceAdapter implements GrpcService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServiceAdapter.class);

    @Override
    public GrpcService.GrpcClient createClient(String host, int port) {
        return new GrpcClientAdapter(host, port);
    }

    /**
     * Adapter that wraps the actual gRPC client.
     */
    private static class GrpcClientAdapter implements GrpcService.GrpcClient {

        private final com.pdbp.grpc.client.GrpcClient client;

        public GrpcClientAdapter(String host, int port) {
            this.client = new com.pdbp.grpc.client.GrpcClient(host, port);
        }

        @Override
        public String callPlugin(String pluginName, String method, Map<String, String> parameters)
                throws GrpcService.GrpcException {
            try {
                CallResponse response = client.callPlugin(pluginName, method, parameters);
                if (response.getStatus() == Status.OK) {
                    return response.getData() != null ? response.getData().toStringUtf8() : "";
                } else {
                    throw new GrpcService.GrpcException(
                            "gRPC call failed with status: " + response.getStatus() + ", message: "
                                    + response.getMessage(), null);
                }
            } catch (GrpcClientException e) {
                throw new GrpcService.GrpcException("gRPC call failed: " + e.getMessage(), e);
            }
        }

        @Override
        public boolean healthCheck(String pluginName) {
            try {
                HealthCheckResponse response = client.healthCheck(pluginName);
                return response.getStatus() == Status.OK;
            } catch (GrpcClientException e) {
                logger.debug("Health check failed for plugin: {}", pluginName, e);
                return false;
            }
        }

        @Override
        public void close() {
            client.close();
        }
    }
}


package com.pdbp.grpc.client;

import com.pdbp.grpc.CallRequest;
import com.pdbp.grpc.CallResponse;
import com.pdbp.grpc.PluginInfoRequest;
import com.pdbp.grpc.PluginInfoResponse;
import com.pdbp.grpc.*;
import com.pdbp.grpc.PluginServiceGrpc;
import com.pdbp.grpc.StreamRequest;
import com.pdbp.grpc.StreamResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for plugin-to-plugin communication.
 * Provides methods for all streaming types: unary, server-streaming, client-streaming, bidirectional.
 *
 * @author Saurabh Maurya
 */
public class GrpcClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(GrpcClient.class);

    private final ManagedChannel channel;
    private final PluginServiceGrpc.PluginServiceBlockingStub blockingStub;
    private final PluginServiceGrpc.PluginServiceStub asyncStub;

    public GrpcClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // For development - use TLS in production
                .build();
        this.blockingStub = PluginServiceGrpc.newBlockingStub(channel);
        this.asyncStub = PluginServiceGrpc.newStub(channel);
        logger.info("gRPC client created: {}:{}", host, port);
    }

    /**
     * Unary call: Simple request-response.
     *
     * @param pluginName the target plugin name
     * @param method     the method to call
     * @param parameters request parameters
     * @return the response
     * @throws GrpcClientException if call fails
     */
    public CallResponse callPlugin(String pluginName, String method,
                                                     java.util.Map<String, String> parameters)
            throws GrpcClientException {
        try {
            CallRequest request = CallRequest.newBuilder()
                    .setPluginName(pluginName)
                    .setMethod(method)
                    .putAllParameters(parameters)
                    .build();

            CallResponse response = blockingStub.callPlugin(request);
            logger.debug("Unary call completed: plugin={}, method={}", pluginName, method);
            return response;

        } catch (StatusRuntimeException e) {
            throw new GrpcClientException("Unary call failed", e);
        }
    }

    /**
     * Server streaming: Receive multiple responses from server.
     *
     * @param pluginName the target plugin name
     * @param streamId   unique stream identifier
     * @param observer   response observer
     */
    public void streamFromPlugin(String pluginName, String streamId,
                                StreamObserver<StreamResponse> observer) {
        StreamRequest request = StreamRequest.newBuilder()
                .setPluginName(pluginName)
                .setStreamId(streamId)
                .build();

        asyncStub.streamFromPlugin(request, observer);
        logger.debug("Server streaming started: plugin={}, streamId={}", pluginName, streamId);
    }

    /**
     * Client streaming: Send multiple requests, receive one response.
     *
     * @param pluginName the target plugin name
     * @param streamId   unique stream identifier
     * @return response observer for sending requests
     */
    public StreamObserver<StreamRequest> streamToPlugin(String pluginName, String streamId) {
        StreamObserver<StreamResponse> responseObserver = new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse response) {
                logger.debug("Client streaming response received: streamId={}", response.getStreamId());
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Client streaming error", t);
            }

            @Override
            public void onCompleted() {
                logger.debug("Client streaming completed");
            }
        };

        StreamObserver<StreamRequest> requestObserver = asyncStub.streamToPlugin(responseObserver);
        logger.debug("Client streaming started: plugin={}, streamId={}", pluginName, streamId);
        return requestObserver;
    }

    /**
     * Bidirectional streaming: Both client and server send streams.
     *
     * @param pluginName the target plugin name
     * @param streamId   unique stream identifier
     * @return response observer for sending requests
     */
    public StreamObserver<StreamRequest> bidirectionalStream(String pluginName, String streamId) {
        CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<StreamResponse> responseObserver = new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse response) {
                logger.debug("Bidirectional streaming response: streamId={}, data={}", 
                        response.getStreamId(), new String(response.getData().toByteArray()));
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Bidirectional streaming error", t);
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.debug("Bidirectional streaming completed");
                finishLatch.countDown();
            }
        };

        StreamObserver<StreamRequest> requestObserver = asyncStub.bidirectionalStream(responseObserver);
        logger.debug("Bidirectional streaming started: plugin={}, streamId={}", pluginName, streamId);
        return requestObserver;
    }

    /**
     * Get plugin information.
     *
     * @param pluginName the plugin name
     * @return plugin information
     * @throws GrpcClientException if call fails
     */
    public PluginInfoResponse getPluginInfo(String pluginName) throws GrpcClientException {
        try {
            PluginInfoRequest request = PluginInfoRequest.newBuilder()
                    .setPluginName(pluginName)
                    .build();

            return blockingStub.getPluginInfo(request);

        } catch (StatusRuntimeException e) {
            throw new GrpcClientException("Get plugin info failed", e);
        }
    }

    /**
     * Health check.
     *
     * @param pluginName the plugin name
     * @return health check response
     * @throws GrpcClientException if call fails
     */
    public HealthCheckResponse healthCheck(String pluginName) throws GrpcClientException {
        try {
            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setPluginName(pluginName)
                    .build();

            return blockingStub.healthCheck(request);

        } catch (StatusRuntimeException e) {
            throw new GrpcClientException("Health check failed", e);
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            logger.debug("gRPC client closed");
        } catch (InterruptedException e) {
            logger.error("Error closing gRPC client", e);
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}


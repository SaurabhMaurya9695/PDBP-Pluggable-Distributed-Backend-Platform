package com.pdbp.grpc.server;

import com.pdbp.api.Plugin;
import com.pdbp.api.PluginState;
import com.pdbp.grpc.CallRequest;
import com.pdbp.grpc.CallResponse;
import com.pdbp.grpc.HealthCheckRequest;
import com.pdbp.grpc.HealthCheckResponse;
import com.pdbp.grpc.PluginInfoRequest;
import com.pdbp.grpc.PluginInfoResponse;
import com.pdbp.grpc.PluginServiceGrpc;
import com.pdbp.grpc.StreamRequest;
import com.pdbp.grpc.StreamResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC service implementation for plugin communication.
 * Handles unary, server-streaming, client-streaming, and bidirectional streaming.
 *
 * @author Saurabh Maurya
 */
public class PluginServiceImpl extends PluginServiceGrpc.PluginServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(PluginServiceImpl.class);
    
    private final PluginRegistry pluginRegistry;
    private final Map<String, StreamObserver<StreamResponse>> activeStreams;

    public PluginServiceImpl(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
        this.activeStreams = new ConcurrentHashMap<>();
    }

    /**
     * Unary RPC: Simple request-response call.
     */
    @Override
    public void callPlugin(CallRequest request,
                          StreamObserver<CallResponse> responseObserver) {
        try {
            logger.debug("Unary call received: plugin={}, method={}", request.getPluginName(), request.getMethod());
            
            Plugin plugin = pluginRegistry.getPlugin(request.getPluginName());
            if (plugin == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Plugin not found: " + request.getPluginName())
                        .asException());
                return;
            }

            if (plugin.getState() != PluginState.STARTED) {
                responseObserver.onError(io.grpc.Status.FAILED_PRECONDITION
                        .withDescription("Plugin is not started: " + request.getPluginName())
                        .asException());
                return;
            }

            // Execute the call (simplified - in real implementation, this would invoke plugin methods)
            CallResponse response = executePluginCall(plugin, request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error in unary call", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asException());
        }
    }

    /**
     * Server Streaming: Server sends multiple responses to client.
     */
    @Override
    public void streamFromPlugin(StreamRequest request,
                                StreamObserver<StreamResponse> responseObserver) {
        try {
            logger.debug("Server streaming started: plugin={}, streamId={}", 
                    request.getPluginName(), request.getStreamId());

            Plugin plugin = pluginRegistry.getPlugin(request.getPluginName());
            if (plugin == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Plugin not found: " + request.getPluginName())
                        .asException());
                return;
            }

            // Store stream for later use
            activeStreams.put(request.getStreamId(), responseObserver);

            // Simulate streaming data (in real implementation, this would stream from plugin)
            streamDataFromPlugin(plugin, request.getStreamId(), responseObserver);

        } catch (Exception e) {
            logger.error("Error in server streaming", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asException());
        }
    }

    /**
     * Client Streaming: Client sends multiple requests, server responds once.
     */
    @Override
    public StreamObserver<StreamRequest> streamToPlugin(
            StreamObserver<StreamResponse> responseObserver) {
        
        return new StreamObserver<StreamRequest>() {
            private final StringBuilder aggregatedData = new StringBuilder();
            private String streamId = null;
            private String pluginName = null;

            @Override
            public void onNext(StreamRequest request) {
                if (streamId == null) {
                    streamId = request.getStreamId();
                    pluginName = request.getPluginName();
                    logger.debug("Client streaming started: plugin={}, streamId={}", pluginName, streamId);
                }

                // Aggregate data from client
                if (!request.getData().isEmpty()) {
                    aggregatedData.append(new String(request.getData().toByteArray()));
                }

                if (request.getEndStream()) {
                    // Process aggregated data and send response
                    StreamResponse response = processClientStream(pluginName, aggregatedData.toString());
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in client streaming", t);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Client stream error: " + t.getMessage())
                        .withCause(t)
                        .asException());
            }

            @Override
            public void onCompleted() {
                logger.debug("Client streaming completed: streamId={}", streamId);
                // Response already sent in onNext when end_stream=true
            }
        };
    }

    /**
     * Bidirectional Streaming: Both client and server send streams.
     */
    @Override
    public StreamObserver<StreamRequest> bidirectionalStream(
            StreamObserver<StreamResponse> responseObserver) {
        
        return new StreamObserver<StreamRequest>() {
            private String streamId = null;
            private String pluginName = null;

            @Override
            public void onNext(StreamRequest request) {
                if (streamId == null) {
                    streamId = request.getStreamId();
                    pluginName = request.getPluginName();
                    logger.debug("Bidirectional streaming started: plugin={}, streamId={}", pluginName, streamId);
                }

                try {
                    Plugin plugin = pluginRegistry.getPlugin(pluginName);
                    if (plugin == null) {
                        responseObserver.onError(io.grpc.Status.NOT_FOUND
                                .withDescription("Plugin not found: " + pluginName)
                                .asException());
                        return;
                    }

                    // Process request and send response immediately
                    StreamResponse response = processBidirectionalRequest(plugin, request);
                    responseObserver.onNext(response);

                    if (request.getEndStream()) {
                        responseObserver.onCompleted();
                    }
                } catch (Exception e) {
                    logger.error("Error in bidirectional streaming", e);
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("Error processing request: " + e.getMessage())
                            .withCause(e)
                            .asException());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in bidirectional streaming", t);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Stream error: " + t.getMessage())
                        .withCause(t)
                        .asException());
            }

            @Override
            public void onCompleted() {
                logger.debug("Bidirectional streaming completed: streamId={}", streamId);
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Get plugin information.
     */
    @Override
    public void getPluginInfo(PluginInfoRequest request,
                              StreamObserver<PluginInfoResponse> responseObserver) {
        try {
            Plugin plugin = pluginRegistry.getPlugin(request.getPluginName());
            if (plugin == null) {
                responseObserver.onError(io.grpc.Status.NOT_FOUND
                        .withDescription("Plugin not found: " + request.getPluginName())
                        .asException());
                return;
            }

            PluginInfoResponse response = PluginInfoResponse.newBuilder()
                    .setPluginName(plugin.getName())
                    .setVersion(plugin.getVersion())
                    .setState(plugin.getState().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error getting plugin info", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asException());
        }
    }

    /**
     * Health check.
     */
    @Override
    public void healthCheck(HealthCheckRequest request,
                           StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            Plugin plugin = pluginRegistry.getPlugin(request.getPluginName());
            com.pdbp.grpc.Status status = (plugin != null && plugin.getState() == PluginState.STARTED)
                    ? com.pdbp.grpc.Status.OK
                    : com.pdbp.grpc.Status.UNAVAILABLE;

            HealthCheckResponse response = HealthCheckResponse.newBuilder()
                    .setStatus(status)
                    .setMessage(status == com.pdbp.grpc.Status.OK ? "Plugin is healthy" : "Plugin is unavailable")
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("Error in health check", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asException());
        }
    }

    // Helper methods

    private CallResponse executePluginCall(Plugin plugin, CallRequest request) {
        // Simplified implementation - in real scenario, this would invoke plugin methods via reflection
        return CallResponse.newBuilder()
                .setStatus(com.pdbp.grpc.Status.OK)
                .setMessage("Call executed successfully")
                .setData(com.google.protobuf.ByteString.copyFromUtf8("Response data"))
                .build();
    }

    private void streamDataFromPlugin(Plugin plugin, String streamId, 
                                     StreamObserver<StreamResponse> responseObserver) {
        // Simulate streaming data
        for (int i = 0; i < 5; i++) {
            StreamResponse response = StreamResponse.newBuilder()
                    .setStreamId(streamId)
                    .setData(com.google.protobuf.ByteString.copyFromUtf8("Stream data chunk " + i))
                    .setStatus(com.pdbp.grpc.Status.OK)
                    .setEndStream(i == 4)
                    .build();

            responseObserver.onNext(response);
            try {
                Thread.sleep(100); // Simulate delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        responseObserver.onCompleted();
    }

    private StreamResponse processClientStream(String pluginName, String data) {
        return StreamResponse.newBuilder()
                .setStreamId("response-" + System.currentTimeMillis())
                .setData(com.google.protobuf.ByteString.copyFromUtf8("Processed: " + data))
                .setStatus(com.pdbp.grpc.Status.OK)
                .setMessage("Client stream processed successfully")
                .setEndStream(true)
                .build();
    }

    private StreamResponse processBidirectionalRequest(Plugin plugin, 
                                                                         StreamRequest request) {
        return StreamResponse.newBuilder()
                .setStreamId(request.getStreamId())
                .setData(com.google.protobuf.ByteString.copyFromUtf8("Echo: " +
                        (request.getData() != null ? new String(request.getData().toByteArray()) : "")))
                .setStatus(com.pdbp.grpc.Status.OK)
                .setEndStream(request.getEndStream())
                .build();
    }
}


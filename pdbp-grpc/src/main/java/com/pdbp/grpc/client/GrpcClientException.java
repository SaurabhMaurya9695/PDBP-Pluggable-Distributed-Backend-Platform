package com.pdbp.grpc.client;

import io.grpc.StatusRuntimeException;

/**
 * Exception thrown by gRPC client operations.
 * Wraps gRPC StatusRuntimeException with additional context.
 *
 * @author Saurabh Maurya
 */
public class GrpcClientException extends Exception {

    public GrpcClientException(String message, StatusRuntimeException cause) {
        super(message, cause);
    }

    /**
     * Gets the gRPC status code.
     *
     * @return the status code
     */
    public io.grpc.Status.Code getStatusCode() {
        if (getCause() instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) getCause()).getStatus().getCode();
        }
        return io.grpc.Status.Code.UNKNOWN;
    }

    /**
     * Gets the gRPC status description.
     *
     * @return the status description
     */
    public String getStatusDescription() {
        if (getCause() instanceof StatusRuntimeException) {
            return ((StatusRuntimeException) getCause()).getStatus().getDescription();
        }
        return getMessage();
    }
}


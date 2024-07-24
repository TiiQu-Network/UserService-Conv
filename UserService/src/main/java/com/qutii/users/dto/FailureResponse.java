package com.qutii.users.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a failure response with details about the failure.
 */
@Data
@Builder
public class FailureResponse {

    /**
     * Indicates whether the operation was successful or not.
     * In the context of this class, it typically represents a failure.
     */
    private boolean isSuccessful;

    /**
     * A message describing the failure or providing additional information.
     */
    private String message;
}

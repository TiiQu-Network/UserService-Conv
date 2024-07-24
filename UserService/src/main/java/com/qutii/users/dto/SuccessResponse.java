package com.qutii.users.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a success response with details about the successful operation.
 *
 * @param <T> The type of the response body.
 */
@Data
@Builder
public class SuccessResponse<T> {

    /**
     * Indicates whether the operation was successful.
     * In the context of this class, it typically represents success.
     */
    private boolean isSuccessful;

    /**
     * The status code of the response, typically representing an HTTP status code.
     */
    private int statusCode;

    /**
     * The body of the response, containing additional details or data related to the success.
     */
    private T body;
}

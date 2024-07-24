package com.qutii.users.dto;

import lombok.Builder;

/**
 * Represents a response containing a user object.
 */
@Builder
public record UserResponse(User user) { }

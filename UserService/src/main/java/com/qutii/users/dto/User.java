package com.qutii.users.dto;

import lombok.Builder;

/**
 * Represents a user with their personal details and status.
 */
@Builder
public record User
        (String email,
                           String firstName,
                           String lastName,
                           String phone,
                           String cognitoUserId,
                           boolean emailVerified
        ) {}

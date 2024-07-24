package com.qutii.users.dto;

import lombok.Builder;
import java.util.List;

/**
 * Represents a response containing a list of user objects.
 */
@Builder
public record UserResponses(List<User> users) { }

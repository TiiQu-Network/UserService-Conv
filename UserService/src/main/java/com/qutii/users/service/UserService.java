package com.qutii.users.service;

import com.google.gson.JsonObject;

/**
 * Interface representing the user service for managing users and user groups in an application.
 */
public interface UserService {

    /**
     * Creates a new user in the application.
     *
     * @param user The details of the user to be created as a JsonObject.
     * @param appClientId The application client ID.
     * @param appClientSecret The application client secret.
     * @return A JsonObject containing the response from the user creation operation.
     */
    JsonObject createUser(JsonObject user, String appClientId, String appClientSecret);

    /**
     * Confirms a user's signup using the provided confirmation code.
     *
     * @param appClientId The application client ID.
     * @param appClientSecret The application client secret.
     * @param email The email address of the user to be confirmed.
     * @param confirmationCode The confirmation code sent to the user's email.
     * @return A JsonObject containing the response from the confirmation operation.
     */
    JsonObject confirmUserSignup(String appClientId, String appClientSecret, String email, String confirmationCode);

    /**
     * Logs in a user with the provided login details.
     *
     * @param appClientId The application client ID.
     * @param appClientSecret The application client secret.
     * @param loginDetails The login details as a JsonObject.
     * @return A JsonObject containing the response from the login operation.
     */
    JsonObject loginUser(String appClientId, String appClientSecret, JsonObject loginDetails);

    /**
     * Adds a user to a group.
     *
     * @param userPoolId The user pool ID.
     * @param userGroupDetails The details of the user and group as a JsonObject.
     * @return A JsonObject containing the response from the add operation.
     */
    JsonObject addUserToGroup(String userPoolId, JsonObject userGroupDetails);

    /**
     * Removes a user from a group.
     *
     * @param userPoolId The user pool ID.
     * @param userGroupDetails The details of the user and group as a JsonObject.
     * @return A JsonObject containing the response from the remove operation.
     */
    JsonObject removeUserFromGroup(String userPoolId, JsonObject userGroupDetails);

    /**
     * Retrieves the details of a user.
     *
     * @param accessToken The access token of the user.
     * @return A JsonObject containing the user details.
     */
    JsonObject getUser(String accessToken);

    /**
     * Updates the details of a user.
     *
     * @param userPoolId The user pool ID.
     * @param userDetails The updated user details as a JsonObject.
     * @return A JsonObject containing the response from the update operation.
     */
    JsonObject updateUser(String userPoolId, JsonObject userDetails);

    /**
     * Deletes a user from the application.
     *
     * @param userPoolId The user pool ID.
     * @param cognitoUserId The Cognito user ID of the user to be deleted.
     * @return A JsonObject containing the response from the delete operation.
     */
    JsonObject deleteUser(String userPoolId, String cognitoUserId);

    /**
     * Retrieves a list of users in the specified user pool.
     *
     * @param userPoolId The user pool ID.
     * @return A JsonObject containing the list of users.
     */
    JsonObject getUsers(String userPoolId);

    /**
     * Resends a verification code to a user.
     *
     * This method initiates the process of resending a verification code to a user's email or phone
     * for account verification purposes. The method requires the user pool ID, the app client secret,
     * and the app client ID to identify the user pool and the client application making the request.
     *
     * @param userPoolId the ID of the user pool. This is a required parameter that specifies the user pool
     *                   in which the user is registered.
     * @param appClientSecret the secret of the app client. This is a required parameter that is used to
     *                        authenticate the client application.
     * @param appClientId the ID of the app client. This is a required parameter that specifies the client
     *                    application making the request.
     * @return a {@link JsonObject} containing the response from the server. The response typically includes
     *         information about the status of the verification code resending process.
     */
    JsonObject resendVerificationCode(String userPoolId, String appClientSecret, String appClientId);

    JsonObject forgotPassword(String appClientId, String appClientSecret, String username);

    JsonObject confirmForgotPassword(String appClientId, String appClientSecret, JsonObject userDetails);
}


package com.qutii.users.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qutii.users.utils.Utils;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

public class CognitoUserService implements UserService  {
    private final CognitoIdentityProviderClient cognitoIdentityProviderClient;

    public CognitoUserService(CognitoIdentityProviderClient cognitoIdentityProviderClient) {
        this.cognitoIdentityProviderClient = cognitoIdentityProviderClient;
    }

    public CognitoUserService(String region) {
        this.cognitoIdentityProviderClient = CognitoIdentityProviderClient.builder()
                .httpClient(ApacheHttpClient.builder().build())
                .region(Region.of(region))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
    }

    @Override
    public JsonObject createUser(JsonObject user, String appClientId, String appClientSecret) {

        String email = user.get("email").getAsString();
        String password = user.get("password").getAsString();
        String firstName = user.get("firstName").getAsString();
        String lastName = user.get("lastName").getAsString();
        String phone = Optional.ofNullable(user.get("phone"))
                .map(JsonElement::getAsString)
                .orElse(null);

        AttributeType emailAttribute = AttributeType.builder()
                .name("email")
                .value(email)
                .build();

        AttributeType firstNameAttribute = AttributeType.builder()
                .name("custom:firstName")
                .value(firstName)
                .build();

        AttributeType lastNameAttribute = AttributeType.builder()
                .name("custom:lastName")
                .value(lastName)
                .build();

        List<AttributeType> attributes;
        if (phone != null) {
            AttributeType phoneAttribute = AttributeType.builder()
                    .name("custom:phone")
                    .value(phone)
                    .build();
            attributes = List.of(emailAttribute, firstNameAttribute,
                    lastNameAttribute, phoneAttribute);
        } else {
            attributes = List.of(emailAttribute, firstNameAttribute,
                    lastNameAttribute);
        }

        String secretHash = calculateSecretHash(appClientId, appClientSecret, email);

        SignUpRequest signUpRequest = SignUpRequest.builder()
                .username(email)
                .password(password)
                .userAttributes(attributes)
                .clientId(appClientId)
                .secretHash(secretHash)
                .build();

        SignUpResponse signUpResponse = cognitoIdentityProviderClient.signUp(signUpRequest);

        //Add user to the Users' group
        JsonObject addUserToGroupRequest = new JsonObject();
        addUserToGroupRequest.addProperty("email", email);
        addUserToGroupRequest.addProperty("group", System.getenv("COGNITO_USER_POOL_USER_GROUP"));
        JsonObject addUserToGroupResponse = addUserToGroup(Utils.decryptKey(System.getenv("COGNITO_USER_POOL_ID")),
                addUserToGroupRequest);
        if (!addUserToGroupResponse.get("isSuccessful").getAsBoolean()) {
            return addUserToGroupResponse;
        }

        JsonObject createUserResponse = new JsonObject();
        createUserResponse.addProperty("isSuccessful", signUpResponse.sdkHttpResponse().isSuccessful());
        createUserResponse.addProperty("statusCode", signUpResponse.sdkHttpResponse().statusCode());
        createUserResponse.addProperty("statusMessage", signUpResponse.sdkHttpResponse().statusText().orElse("unknown"));
        createUserResponse.addProperty("cognitoUserId", signUpResponse.userSub());
        createUserResponse.addProperty("emailVerified", signUpResponse.userConfirmed());

        return createUserResponse;
    }

    @Override
    public JsonObject confirmUserSignup(String appClientId, String appClientSecret, String email, String confirmationCode) {
        String secretHash = calculateSecretHash(appClientId, appClientSecret, email);
        ConfirmSignUpRequest confirmSignUpRequest = ConfirmSignUpRequest.builder()
                .username(email)
                .confirmationCode(confirmationCode)
                .clientId(appClientId)
                .secretHash(secretHash)
                .build();
        ConfirmSignUpResponse confirmSignUpResponse = cognitoIdentityProviderClient.confirmSignUp(confirmSignUpRequest);

        JsonObject confirmUserResponse = new JsonObject();
        confirmUserResponse.addProperty("isSuccessful", confirmSignUpResponse.sdkHttpResponse().isSuccessful());
        confirmUserResponse.addProperty("statusCode", confirmSignUpResponse.sdkHttpResponse().statusCode());
        confirmUserResponse.addProperty("statusMessage", confirmSignUpResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return confirmUserResponse;
    }

    @Override
    public JsonObject loginUser(String appClientId, String appClientSecret, JsonObject loginDetails) {
        String email = loginDetails.get("email").getAsString();
        String password = loginDetails.get("password").getAsString();
        String secretHash = calculateSecretHash(appClientId, appClientSecret, email);
        Map<String, String> authParameters = Map.of("USERNAME", email, "PASSWORD", password,
                "SECRET_HASH", secretHash);

        InitiateAuthRequest initiateAuthRequest = InitiateAuthRequest.builder()
                .clientId(appClientId)
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(authParameters)
                .build();
        InitiateAuthResponse initiateAuthResponse = cognitoIdentityProviderClient.initiateAuth(initiateAuthRequest);
        AuthenticationResultType authenticationResultType = initiateAuthResponse.authenticationResult();

        JsonObject loginUserResult = new JsonObject();
        loginUserResult.addProperty("isSuccessful", initiateAuthResponse.sdkHttpResponse().isSuccessful());
        loginUserResult.addProperty("statusCode", initiateAuthResponse.sdkHttpResponse().statusCode());
        loginUserResult.addProperty("statusMessage", initiateAuthResponse.sdkHttpResponse().statusText().orElse("unknown"));
        loginUserResult.addProperty("idToken", authenticationResultType.idToken());
        loginUserResult.addProperty("accessToken", authenticationResultType.accessToken());
        loginUserResult.addProperty("refreshToken", authenticationResultType.refreshToken());
        loginUserResult.addProperty("expiresIn", authenticationResultType.expiresIn());
        loginUserResult.addProperty("tokenType", authenticationResultType.tokenType());

        return loginUserResult;
    }

    @Override
    public JsonObject addUserToGroup(String userPoolId, JsonObject userGroupDetails) {
        var groupName = userGroupDetails.get("group").getAsString();
        var email = userGroupDetails.get("email").getAsString();
        AdminAddUserToGroupRequest addUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .groupName(groupName)
                .username(email)
                .userPoolId(userPoolId)
                .build();

        AdminAddUserToGroupResponse adminAddUserToGroupResponse = cognitoIdentityProviderClient
                .adminAddUserToGroup(addUserToGroupRequest);

        JsonObject addUserToGroupResponse = new JsonObject();
        addUserToGroupResponse.addProperty("isSuccessful", adminAddUserToGroupResponse.sdkHttpResponse().isSuccessful());
        addUserToGroupResponse.addProperty("statusCode", adminAddUserToGroupResponse.sdkHttpResponse().statusCode());
        addUserToGroupResponse.addProperty("statusMessage", adminAddUserToGroupResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return addUserToGroupResponse;
    }

    @Override
    public JsonObject removeUserFromGroup(String userPoolId, JsonObject userGroupDetails) {
        var groupName = userGroupDetails.get("group").getAsString();
        var email = userGroupDetails.get("email").getAsString();
        AdminRemoveUserFromGroupRequest removeUserFromGroupRequest = AdminRemoveUserFromGroupRequest.builder()
                .groupName(groupName)
                .username(email)
                .userPoolId(userPoolId)
                .build();

        AdminRemoveUserFromGroupResponse adminRemoveUserFromGroupResponse = cognitoIdentityProviderClient
                .adminRemoveUserFromGroup(removeUserFromGroupRequest);

        JsonObject removeUserFromGroupResponse = new JsonObject();
        removeUserFromGroupResponse.addProperty("isSuccessful", adminRemoveUserFromGroupResponse.sdkHttpResponse().isSuccessful());
        removeUserFromGroupResponse.addProperty("statusCode", adminRemoveUserFromGroupResponse.sdkHttpResponse().statusCode());
        removeUserFromGroupResponse.addProperty("statusMessage", adminRemoveUserFromGroupResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return removeUserFromGroupResponse;
    }

    @Override
    public JsonObject getUser(String accessToken) {
        GetUserRequest getUserRequest = GetUserRequest.builder()
                .accessToken(accessToken)
                .build();
        GetUserResponse getUserResponse = cognitoIdentityProviderClient.getUser(getUserRequest);

        JsonObject userResponse = new JsonObject();
        userResponse.addProperty("isSuccessful", getUserResponse.sdkHttpResponse().isSuccessful());
        userResponse.addProperty("statusCode", getUserResponse.sdkHttpResponse().statusCode());
        userResponse.addProperty("statusMessage", getUserResponse.sdkHttpResponse().statusText().orElse("unknown"));

        JsonObject userDetails = Optional.ofNullable(getUserResponse.userAttributes())
                .orElse(List.of())
                .stream()
                .collect(JsonObject::new,
                        (json, attribute) -> json.addProperty(attribute.name(), attribute.value()),
                        (json1, json2) -> {});

        userResponse.add("user", userDetails);

        return userResponse;
    }

    @Override
    public JsonObject updateUser(String userPoolId, JsonObject userDetails) {
        JsonObject updateUserAttributesResponse = new JsonObject();
        String cognitoUserId = userDetails.get("cognitoUserId").getAsString();

        List<AttributeType> userAttributes = Stream.of(
                        new AttributeData("email", userDetails.get("email")),
                        new AttributeData("custom:firstName", userDetails.get("firstName")),
                        new AttributeData("custom:lastName", userDetails.get("lastName"))
                        //new AttributeData("custom:phone", userDetails.get("phone"))
                ).flatMap(attribute -> attribute.toAttributeType().stream())
                .toList();

        AdminUpdateUserAttributesRequest updateUserAttributesRequest = AdminUpdateUserAttributesRequest.builder()
                .username(cognitoUserId)
                .userPoolId(userPoolId)
                .userAttributes(userAttributes)
                .build();

        AdminUpdateUserAttributesResponse adminUpdateUserAttributesResponse =
                cognitoIdentityProviderClient.adminUpdateUserAttributes(updateUserAttributesRequest);

        updateUserAttributesResponse.addProperty("isSuccessful",
                adminUpdateUserAttributesResponse.sdkHttpResponse().isSuccessful());
        updateUserAttributesResponse.addProperty("statusCode",
                adminUpdateUserAttributesResponse.sdkHttpResponse().statusCode());
        updateUserAttributesResponse.addProperty("statusMessage",
                adminUpdateUserAttributesResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return updateUserAttributesResponse;
    }

    @Override
    public JsonObject deleteUser(String userPoolId, String cognitoUserId) {
        AdminDeleteUserRequest deleteUserRequest = AdminDeleteUserRequest.builder()
                .userPoolId(userPoolId)
                .username(cognitoUserId)
                .build();
        AdminDeleteUserResponse adminDeleteUserResponse =
                cognitoIdentityProviderClient.adminDeleteUser(deleteUserRequest);

        JsonObject deleteUserResponse = new JsonObject();
        deleteUserResponse.addProperty("isSuccessful", adminDeleteUserResponse.sdkHttpResponse().isSuccessful());
        deleteUserResponse.addProperty("statusCode", adminDeleteUserResponse.sdkHttpResponse().statusCode());
        deleteUserResponse.addProperty("statusMessage", adminDeleteUserResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return deleteUserResponse;
    }

    @Override
    public JsonObject getUsers(String userPoolId) {
        ListUsersRequest listUsersRequest = ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .build();

        ListUsersResponse listUsersResponse = cognitoIdentityProviderClient.listUsers(listUsersRequest);

        JsonObject usersResponse = new JsonObject();
        usersResponse.addProperty("isSuccessful", listUsersResponse.sdkHttpResponse().isSuccessful());
        usersResponse.addProperty("statusCode", listUsersResponse.sdkHttpResponse().statusCode());
        usersResponse.addProperty("statusMessage", listUsersResponse.sdkHttpResponse().statusText().orElse("unknown"));

        Optional.ofNullable(listUsersResponse.users())
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(this::convertUserTypeToJson)
                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
                .ifPresent(usersArray -> usersResponse.add("users", usersArray));

        return usersResponse;
    }

    @Override
    public JsonObject resendVerificationCode(String appClientId, String appClientSecret, String username) {
        String secretHash = calculateSecretHash(appClientId, appClientSecret, username);
        ResendConfirmationCodeRequest resendConfirmationCodeRequest =
                ResendConfirmationCodeRequest.builder()
                        .clientId(appClientId)
                        .secretHash(secretHash)
                        .username(username)
                        .build();
        ResendConfirmationCodeResponse resendConfirmationCodeResponse =
                cognitoIdentityProviderClient.resendConfirmationCode(resendConfirmationCodeRequest);

        JsonObject getUserResponse = new JsonObject();
        getUserResponse.addProperty("isSuccessful", resendConfirmationCodeResponse.sdkHttpResponse().isSuccessful());
        getUserResponse.addProperty("statusCode", resendConfirmationCodeResponse.sdkHttpResponse().statusCode());
        getUserResponse.addProperty("statusMessage", resendConfirmationCodeResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return getUserResponse;
    }

    @Override
    public JsonObject forgotPassword(String appClientId, String appClientSecret, String username) {
        String secretHash = calculateSecretHash(appClientId, appClientSecret, username);
        ForgotPasswordRequest forgotPasswordRequest = ForgotPasswordRequest.builder()
                .clientId(appClientId)
                .secretHash(secretHash)
                .username(username)
                .build();
        ForgotPasswordResponse forgotPasswordResponse = cognitoIdentityProviderClient.forgotPassword(forgotPasswordRequest);

        JsonObject passwordResponse = new JsonObject();
        passwordResponse.addProperty("isSuccessful", forgotPasswordResponse.sdkHttpResponse().isSuccessful());
        passwordResponse.addProperty("statusCode", forgotPasswordResponse.sdkHttpResponse().statusCode());
        passwordResponse.addProperty("statusMessage", forgotPasswordResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return passwordResponse;
    }

    @Override
    public JsonObject confirmForgotPassword(String appClientId, String appClientSecret, JsonObject userDetails) {
        JsonObject passwordResponse = new JsonObject();

        String username = Optional.ofNullable(userDetails.get("username"))
                .map(JsonElement::getAsString).orElse(null);
        String password = Optional.ofNullable(userDetails.get("password"))
                .map(JsonElement::getAsString).orElse(null);
        String code = Optional.ofNullable(userDetails.get("code"))
                .map(JsonElement::getAsString).orElse(null);

        if (username == null || password == null || code == null) {
            passwordResponse.addProperty("isSuccessful", false);
            passwordResponse.addProperty("statusCode", 400);
            passwordResponse.addProperty("statusMessage", "invalid request body");

            return passwordResponse;
        }

        String secretHash = calculateSecretHash(appClientId, appClientSecret, username);
        ConfirmForgotPasswordRequest confirmForgotPasswordRequest = ConfirmForgotPasswordRequest.builder()
                .clientId(appClientId)
                .secretHash(secretHash)
                .username(username)
                .password(password)
                .confirmationCode(code)
                .build();
        ConfirmForgotPasswordResponse confirmForgotPasswordResponse =
                cognitoIdentityProviderClient.confirmForgotPassword(confirmForgotPasswordRequest);

        passwordResponse.addProperty("isSuccessful", confirmForgotPasswordResponse.sdkHttpResponse().isSuccessful());
        passwordResponse.addProperty("statusCode", confirmForgotPasswordResponse.sdkHttpResponse().statusCode());
        passwordResponse.addProperty("statusMessage", confirmForgotPasswordResponse.sdkHttpResponse().statusText().orElse("unknown"));

        return passwordResponse;
    }

    /**
     * Calculates a secret hash using the provided user pool client ID, user pool client secret, and username.
     * The hash is generated using the HMAC-SHA256 algorithm and is encoded in Base64.
     *
     * @param userPoolClientId The client ID of the user pool.
     * @param userPoolClientSecret The client secret of the user pool.
     * @param userName The username for which the secret hash is to be calculated.
     * @return A Base64-encoded string representing the calculated secret hash.
     * @throws RuntimeException If an error occurs while calculating the secret hash.
     */
    private String calculateSecretHash(String userPoolClientId, String userPoolClientSecret, String userName) {
        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

        SecretKeySpec signingKey = new SecretKeySpec(
                userPoolClientSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(userName.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(userPoolClientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating ");
        }
    }

    /**
     * Converts a UserType object to a JsonObject.
     * The method iterates over the attributes of the UserType object and adds them as properties to the JsonObject.
     *
     * @param userType The UserType object to be converted to JsonObject.
     * @return A JsonObject containing the attributes of the UserType object as properties.
     */
    private JsonObject convertUserTypeToJson(UserType userType) {
        JsonObject userDetails = new JsonObject();
        userType.attributes().forEach(attribute ->
                userDetails.addProperty(attribute.name(), attribute.value())
        );
        return userDetails;
    }

    /**
     * A data class that holds an attribute name and its corresponding value.
     * The value is represented as a JsonElement.
     */
    private static class AttributeData {
        private final String name;
        private final JsonElement value;

        /**
         * Constructs an AttributeData object with the specified name and value.
         *
         * @param name The name of the attribute.
         * @param value The value of the attribute as a JsonElement.
         */
        AttributeData(String name, JsonElement value) {
            this.name = name;
            this.value = value;
        }

        /**
         * Converts this AttributeData object to an Optional of AttributeType.
         * The conversion only occurs if the value is a non-blank JsonPrimitive.
         *
         * @return An Optional containing the corresponding AttributeType if the value is a non-blank JsonPrimitive,
         *         otherwise an empty Optional.
         */
        Optional<AttributeType> toAttributeType() {
            return Optional.ofNullable(value)
                    .filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsString)
                    .filter(str -> !str.isBlank())
                    .map(val -> AttributeType.builder().name(name).value(val).build());
        }
    }
}

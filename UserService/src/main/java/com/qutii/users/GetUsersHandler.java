package com.qutii.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.qutii.users.dto.FailureResponse;
import com.qutii.users.dto.SuccessResponse;
import com.qutii.users.dto.User;
import com.qutii.users.dto.UserResponses;
import com.qutii.users.service.CognitoUserService;
import com.qutii.users.service.UserService;
import com.qutii.users.utils.Utils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetUsersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final UserService userService;
    private final String userPoolId;
    private final Gson gson;

    public GetUsersHandler() {
        this.userService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.userPoolId = Utils.decryptKey(System.getenv("COGNITO_USER_POOL_ID"));
        this.gson = new GsonBuilder().create();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        LambdaLogger logger = context.getLogger();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*"); // Allow all origins
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,AccessToken");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(headers);

        logger.log("original request body::: getUsers");
        try {
            JsonObject getUsersResult = userService.getUsers(userPoolId);
            logger.log("original response body::: " + getUsersResult);
            boolean isSuccessful = getUsersResult.get("isSuccessful").getAsBoolean();
            int statusCode = getUsersResult.get("statusCode").getAsInt();
            if (isSuccessful) {
                List<User> users = new ArrayList<>();
                JsonArray usersArray = getUsersResult.get("users").getAsJsonArray();
                usersArray.forEach(userArray -> {
                    JsonObject userObject = (JsonObject) userArray;
                    var user = User.builder()
                            .email(userObject.get("email").getAsString())
                            .emailVerified(userObject.get("email_verified").getAsBoolean())
                            .firstName(userObject.get("custom:firstName").getAsString())
                            .lastName(userObject.get("custom:lastName").getAsString())
                            //.phone(userObject.get("custom:phone").getAsString())
                            .cognitoUserId(userObject.get("sub").getAsString())
                            .build();
                    users.add(user);
                });
                var userResponses = UserResponses.builder().users(users).build();
                var successResponse = SuccessResponse.builder()
                        .isSuccessful(isSuccessful)
                        .statusCode(statusCode)
                        .body(userResponses)
                        .build();
                response.withStatusCode(statusCode);
                response.withBody(gson.toJson(successResponse, SuccessResponse.class));
            } else {
                logger.log("request failed with statusCode: " + statusCode);
                var failureResponse = FailureResponse.builder()
                        .isSuccessful(isSuccessful)
                        .message(getUsersResult.get("statusMessage").getAsString())
                        .build();
                response.withStatusCode(statusCode);
                response.withBody(gson.toJson(failureResponse, FailureResponse.class));
            }

        } catch (AwsServiceException e) {
            logger.log(e.awsErrorDetails().errorMessage());
            var failureResponse = FailureResponse.builder()
                    .isSuccessful(false)
                    .message(e.awsErrorDetails().errorMessage())
                    .build();
            response.withBody(gson.toJson(failureResponse, FailureResponse.class));
            response.withStatusCode(e.awsErrorDetails().sdkHttpResponse().statusCode());
        } catch (Exception e) {
            logger.log(e.getMessage());
            var failureResponse = FailureResponse.builder()
                    .isSuccessful(false)
                    .message(e.getMessage())
                    .build();
            response.withBody(gson.toJson(failureResponse, FailureResponse.class));
            response.withStatusCode(500);
        }
        return response;
    }
}

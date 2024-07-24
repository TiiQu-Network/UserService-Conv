package com.qutii.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.qutii.users.dto.*;
import com.qutii.users.service.CognitoUserService;
import com.qutii.users.service.UserService;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.HashMap;
import java.util.Map;

public class GetUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final UserService userService;
    private final Gson gson;

    public GetUserHandler() {
        this.userService = new CognitoUserService(System.getenv("AWS_REGION"));
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

        try {
            Map<String, String> inputHeaders = input.getHeaders();
            logger.log("original request body::: getUser(me)");
            JsonObject getUserResult = userService.getUser(inputHeaders.getOrDefault("AccessToken", ""));
            logger.log("original response body::: " + getUserResult);
            boolean isSuccessful = getUserResult.get("isSuccessful").getAsBoolean();
            int statusCode = getUserResult.get("statusCode").getAsInt();
            if (isSuccessful) {
                JsonObject userResponseBody = getUserResult.get("user").getAsJsonObject();

                var user = User.builder()
                        .email(userResponseBody.get("email").getAsString())
                        .emailVerified(userResponseBody.get("email_verified").getAsBoolean())
                        .firstName(userResponseBody.get("custom:firstName").getAsString())
                        .lastName(userResponseBody.get("custom:lastName").getAsString())
                        //.phone(userResponseBody.get("custom:phone").getAsString())
                        .cognitoUserId(userResponseBody.get("sub").getAsString())
                        .build();
                var userResponse = UserResponse.builder().user(user).build();

                var successResponse = SuccessResponse.builder()
                        .isSuccessful(isSuccessful)
                        .statusCode(statusCode)
                        .body(userResponse)
                        .build();
                response.withStatusCode(statusCode);
                response.withBody(gson.toJson(successResponse, SuccessResponse.class));
            } else {
                logger.log("request failed with statusCode: " + statusCode);
                var failureResponse = FailureResponse.builder()
                        .isSuccessful(isSuccessful)
                        .message(getUserResult.get("statusMessage").getAsString())
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

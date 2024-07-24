package com.qutii.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.*;
import com.qutii.users.dto.FailureResponse;
import com.qutii.users.dto.SuccessResponse;
import com.qutii.users.service.CognitoUserService;
import com.qutii.users.service.UserService;
import com.qutii.users.utils.Utils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.*;

public class UpdateUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final UserService userService;
    private final String userPoolId;
    private final Gson gson;

    public UpdateUserHandler() {
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

        try {
            //user request details
            String requestBody = input.getBody();
            logger.log("original request body::: " + requestBody);
            JsonObject userDetails = JsonParser.parseString(requestBody).getAsJsonObject();
            String cognitoUserId = Optional.ofNullable(userDetails.get("cognitoUserId"))
                    .map(JsonElement::getAsString)
                    .orElse(null);
            if (cognitoUserId == null) {
                logger.log("cognitoUserId is required but missing");
                FailureResponse failureResponse = FailureResponse.builder()
                        .isSuccessful(false)
                        .message("cognitoUserId is required but missing")
                        .build();
                response.withStatusCode(400);
                response.withBody(gson.toJson(failureResponse, FailureResponse.class));
                return response;
            }

            //get user from session/token
            JsonObject getUserFromTokenResult = userService.getUser(input.getHeaders()
                    .getOrDefault("AccessToken", ""));
            JsonObject tokenUser = getUserFromTokenResult.get("user").getAsJsonObject();
            String cognitoUserIdFromToken = tokenUser.get("sub").getAsString();

            if (!cognitoUserId.equals(cognitoUserIdFromToken)) {
                logger.log("userId doesn't match cognitoUserId");
                FailureResponse failureResponse = FailureResponse.builder()
                        .isSuccessful(false)
                        .message("userId doesn't match cognitoUserId")
                        .build();
                response.withBody(gson.toJson(failureResponse, FailureResponse.class));
                response.withStatusCode(400);
                return response;
            }
            JsonObject updateUserResult = userService.updateUser(userPoolId,userDetails);
            logger.log("original response body::: " + updateUserResult);
            boolean isSuccessful = updateUserResult.get("isSuccessful").getAsBoolean();
            int statusCode = updateUserResult.get("statusCode").getAsInt();

            if (isSuccessful) {
                var successResponse = SuccessResponse.builder()
                        .isSuccessful(isSuccessful)
                        .statusCode(statusCode)
                        .build();
                response.withStatusCode(statusCode);
                response.withBody(gson.toJson(successResponse, SuccessResponse.class));
            } else {
                logger.log("request failed with statusCode: " + statusCode);
                var failureResponse = FailureResponse.builder()
                        .isSuccessful(isSuccessful)
                        .message(updateUserResult.get("statusMessage").getAsString())
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

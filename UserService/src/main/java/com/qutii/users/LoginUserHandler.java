package com.qutii.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qutii.users.dto.FailureResponse;
import com.qutii.users.dto.SuccessResponse;
import com.qutii.users.service.CognitoUserService;
import com.qutii.users.service.UserService;
import com.qutii.users.utils.Utils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.HashMap;
import java.util.Map;

public class LoginUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final UserService userService;
    private final String appClientId;
    private final String appClientSecret;
    private final Gson gson;

    public LoginUserHandler() {
        this.userService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.appClientId = Utils.decryptKey(System.getenv("COGNITO_POOL_APP_CLIENT_ID"));
        this.appClientSecret = Utils.decryptKey(System.getenv("COGNITO_POOL_APP_CLIENT_SECRET"));
        this.gson = new GsonBuilder().serializeNulls().create();
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
            String requestBody = input.getBody();
            logger.log("original request body::: " + "login details");

            JsonObject loginDetails = JsonParser.parseString(requestBody).getAsJsonObject();
            JsonObject loginUserResult = userService.loginUser(appClientId, appClientSecret, loginDetails);
            logger.log("original response body::: " + "token details");
            boolean isSuccessful = loginUserResult.get("isSuccessful").getAsBoolean();
            int statusCode = loginUserResult.get("statusCode").getAsInt();
            JsonObject responseBody = new JsonObject();
            if (isSuccessful) {
                responseBody.addProperty("idToken", loginUserResult.get("idToken").getAsString());
                responseBody.addProperty("accessToken", loginUserResult.get("accessToken").getAsString());
                responseBody.addProperty("refreshToken", loginUserResult.get("refreshToken").getAsString());
                responseBody.addProperty("expiresIn", loginUserResult.get("expiresIn").getAsString());
                responseBody.addProperty("tokenType", loginUserResult.get("tokenType").getAsString());

                var successResponse = SuccessResponse.builder()
                        .isSuccessful(isSuccessful)
                        .statusCode(statusCode)
                        .body(responseBody)
                        .build();
                response.withStatusCode(statusCode);
                response.withBody(gson.toJson(successResponse, SuccessResponse.class));
            } else {
                logger.log("request failed with statusCode: " + statusCode);
                var failureResponse = FailureResponse.builder()
                        .isSuccessful(isSuccessful)
                        .message(loginUserResult.get("statusMessage").getAsString())
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

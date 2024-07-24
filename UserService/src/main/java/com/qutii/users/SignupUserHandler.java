package com.qutii.users;

import java.util.HashMap;
import java.util.Map;
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

/**
 * Handler for requests to Lambda function.
 */
public class SignupUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserService userService;
    private final String appClientId;
    private final String appClientSecret;
    private final Gson gson;

    public SignupUserHandler() {
        this.userService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.appClientId = Utils.decryptKey(System.getenv("COGNITO_POOL_APP_CLIENT_ID"));
        this.appClientSecret = Utils.decryptKey(System.getenv("COGNITO_POOL_APP_CLIENT_SECRET"));
        this.gson = new GsonBuilder().create();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*"); // Allow all origins
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization,AccessToken");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(headers);

        LambdaLogger logger = context.getLogger();

        try {
            String requestBody = input.getBody();
            logger.log("original request body::: " + "requestBody contains sensitive information");
            JsonObject userDetails = JsonParser.parseString(requestBody).getAsJsonObject();

            JsonObject createUserResult = userService.createUser(userDetails, appClientId, appClientSecret);
            logger.log("original response body::: " + createUserResult);
            boolean isSuccessful = createUserResult.get("isSuccessful").getAsBoolean();
            int statusCode = createUserResult.get("statusCode").getAsInt();

            if (isSuccessful) {
                JsonObject responseBody = new JsonObject();
                responseBody.addProperty("emailVerified", createUserResult.get("emailVerified").getAsBoolean());
                responseBody.addProperty("cognitoUserId", createUserResult.get("cognitoUserId").getAsString());

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
                        .message(createUserResult.get("statusMessage").getAsString())
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

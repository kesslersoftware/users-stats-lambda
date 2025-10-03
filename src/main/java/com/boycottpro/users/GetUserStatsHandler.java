package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.boycottpro.models.Causes;
import com.boycottpro.models.Companies;
import com.boycottpro.users.model.ResponsePojo;
import com.boycottpro.utilities.CausesUtility;
import com.boycottpro.utilities.CompanyUtility;
import com.boycottpro.utilities.JwtUtility;
import com.boycottpro.utilities.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.awt.event.FocusEvent;
import java.util.*;
import java.util.stream.Collectors;

public class GetUserStatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GetUserStatsHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public GetUserStatsHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String sub = null;
        int lineNum = 41;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) {
                Logger.error(45, sub, "user is Unauthorized");
                return response(401, Map.of("message", "Unauthorized"));
            }
            lineNum = 48;
            int totalBoycotts = getNumCompaniesBoycotted(sub);
            lineNum = 50;
            int numCausesFollowed = getNumCausesFollowed(sub);
            lineNum = 52;
            Companies worstCompany = getCompanyWithMostBoycotts();
            lineNum = 54;
            String worstCompanyName = worstCompany.getCompany_name();
            lineNum = 56;
            int worstCount = worstCompany.getBoycott_count();
            String topReason = reasonPeopleAreBoycottingCompany(worstCompany.getCompany_id());
            lineNum = 59;
            Causes bestCause = topCauseBeingFollowed();
            lineNum = 61;
            String causeName = bestCause.getCause_desc();
            int followerCount = bestCause.getFollower_count();
            if(followerCount == 0){
                causeName = "no causes yet";
            }
            ResponsePojo stats = new ResponsePojo(totalBoycotts,numCausesFollowed,worstCompanyName,
                    worstCount,topReason,causeName,followerCount);
            lineNum = 69;
            return response(200, stats);
        } catch (Exception e) {
            Logger.error(lineNum, sub, e.getMessage());
            return response(500,"{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }

    private int getNumCompaniesBoycotted(String userId) {
        QueryRequest query = QueryRequest.builder()
                .tableName("user_boycotts")
                .keyConditionExpression("user_id = :uid")
                .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                .build();

        QueryResponse response = dynamoDb.query(query);

        Set<String> distinctCompanyIds = response.items().stream()
                .map(item -> item.get("company_id").s())
                .collect(Collectors.toSet());

        int companyCount = distinctCompanyIds.size();
        return companyCount;
    }
    private int getNumCausesFollowed(String userId) {
        QueryRequest query = QueryRequest.builder()
                .tableName("user_causes")
                .keyConditionExpression("user_id = :uid")
                .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                .build();

        QueryResponse response = dynamoDb.query(query);
        int causeCount = response.items().size();  // or response.items().size()
        return causeCount;
    }
    private Companies getCompanyWithMostBoycotts() {
        ScanRequest scan = ScanRequest.builder()
                .tableName("companies")
                .projectionExpression("company_id, company_name, boycott_count")
                .build();

        ScanResponse scanResponse = dynamoDb.scan(scan);

        return scanResponse.items().stream()
                .filter(item -> item.containsKey("boycott_count"))
                .max(Comparator.comparingInt(item ->
                        Integer.parseInt(item.get("boycott_count").n())))
                .map(CompanyUtility::mapToCompany)
                .orElse(null);
    }

    private String reasonPeopleAreBoycottingCompany(String companyId) {
        QueryRequest query = QueryRequest.builder()
                .tableName("cause_company_stats")
                .indexName("company_cause_stats_index")
                .keyConditionExpression("company_id = :cid")
                .expressionAttributeValues(Map.of(
                        ":cid", AttributeValue.fromS(companyId)
                ))
                .projectionExpression("cause_desc, boycott_count")
                .scanIndexForward(false) // descending by boycott_count
                .limit(1) // get only top cause
                .build();

        QueryResponse response = dynamoDb.query(query);

        return response.items().stream()
                .filter(item -> item.containsKey("boycott_count"))
                .map(item -> item.get("cause_desc").s())
                .findFirst()
                .orElse("N/A");
    }

    private Causes topCauseBeingFollowed() {
        ScanRequest scan = ScanRequest.builder()
                .tableName("causes")
                .projectionExpression("cause_id, category, cause_desc, follower_count")
                .build();

        ScanResponse response = dynamoDb.scan(scan);

        return response.items().stream()
                .filter(item -> item.containsKey("follower_count"))
                .max(Comparator.comparingInt(item -> Integer.parseInt(item.get("follower_count").n())))
                .map(CausesUtility::mapToCauses)
                .orElse(null);
    }
}
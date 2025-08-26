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
        try {
            String sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) return response(401, "Unauthorized");
            int totalBoycotts = getNumCompaniesBoycotted(sub);
            int numCausesFollowed = getNumCausesFollowed(sub);
            Companies worstCompany = getCompanyWithMostBoycotts();
            String worstCompanyName = worstCompany.getCompany_name();
            int worstCount = worstCompany.getBoycott_count();
            String topReason = reasonPeopleAreBoycottingCompany(worstCompany.getCompany_id());
            Causes bestCause = topCauseBeingFollowed();
            String causeName = bestCause.getCause_desc();
            int followerCount = bestCause.getFollower_count();
            if(followerCount == 0){
                causeName = "no causes yet";
            }
            ResponsePojo stats = new ResponsePojo(totalBoycotts,numCausesFollowed,worstCompanyName,
                    worstCount,topReason,causeName,followerCount);
            String responseBody = objectMapper.writeValueAsString(stats);
            return response(200, responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            return response(500,"{\"error\": \"Unexpected server error: " + e.getMessage() + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body);
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
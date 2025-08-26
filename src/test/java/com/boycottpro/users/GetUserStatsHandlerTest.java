package com.boycottpro.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.Causes;
import com.boycottpro.models.Companies;
import com.boycottpro.utilities.CausesUtility;
import com.boycottpro.utilities.CompanyUtility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GetUserStatsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private GetUserStatsHandler handler;

    @Test
    public void testHandleRequestReturnsStatsSuccessfully() throws Exception {
        String userId = "user-123";

        // Mock user_boycotts response
        QueryResponse mockBoycotts = QueryResponse.builder()
                .items(List.of(
                        Map.of("company_id", AttributeValue.fromS("c1")),
                        Map.of("company_id", AttributeValue.fromS("c2"))
                ))
                .build();
        when(dynamoDb.query(argThat((QueryRequest r) -> r != null &&
                r.tableName().equals("user_boycotts"))))
                .thenReturn(mockBoycotts);

        // Mock user_causes response
        QueryResponse mockCauses = QueryResponse.builder()
                .items(List.of(
                        Map.of("cause_id", AttributeValue.fromS("cause1")),
                        Map.of("cause_id", AttributeValue.fromS("cause2"))
                ))
                .build();
        when(dynamoDb.query(argThat((QueryRequest r) -> r != null &&
                r.tableName().equals("user_causes"))))
                .thenReturn(mockCauses);

        // Mock companies scan
        Map<String, AttributeValue> maxCompanyItem = Map.of(
                "company_id", AttributeValue.fromS("worstCo"),
                "company_name", AttributeValue.fromS("Worst Co"),
                "boycott_count", AttributeValue.fromN("15")
        );
        when(dynamoDb.scan(argThat((ScanRequest r) -> r != null &&
                r.tableName().equals("companies"))))
                .thenReturn(ScanResponse.builder().items(List.of(maxCompanyItem)).build());

        // Mock cause_company_stats scan
        Map<String, AttributeValue> topReason = Map.of(
                "cause_desc", AttributeValue.fromS("Labor"),
                "boycott_count", AttributeValue.fromN("7")
        );

        when(dynamoDb.query(argThat((QueryRequest r) ->
                r != null &&
                        "cause_company_stats".equals(r.tableName()) &&
                        "company_cause_stats_index".equals(r.indexName()) &&
                        r.keyConditionExpression().contains("company_id")
        ))).thenReturn(QueryResponse.builder().items(List.of(topReason)).build());
        // Mock causes scan
        Map<String, AttributeValue> topCauseItem = Map.of(
                "cause_id", AttributeValue.fromS("causeA"),
                "cause_desc", AttributeValue.fromS("Environment"),
                "follower_count", AttributeValue.fromN("99")
        );
        when(dynamoDb.scan(argThat((ScanRequest r) -> r != null &&
                r.tableName().equals("causes"))))
                .thenReturn(ScanResponse.builder().items(List.of(topCauseItem)).build());

        // Static mocks: CompanyUtility.mapToCompany and CausesUtility.mapToCauses
        Companies mockCompany = new Companies("worstCoId", "Worst Co",
                "description", "industry", "city", "state", "zip",
                0,0L,0L,0L,"SYMB","CEO",15); // adjust to your constructor
        Causes mockCause = new Causes("causeA", "category","Environment", 99);       // adjust to your constructor

        try (var companyMocked = mockStatic(CompanyUtility.class);
             var causeMocked = mockStatic(CausesUtility.class)) {

            companyMocked.when(() -> CompanyUtility.mapToCompany(maxCompanyItem)).thenReturn(mockCompany);
            causeMocked.when(() -> CausesUtility.mapToCauses(topCauseItem)).thenReturn(mockCause);

            APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
            Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
            Map<String, Object> authorizer = new HashMap<>();
            authorizer.put("claims", claims);

            APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
            rc.setAuthorizer(authorizer);
            event.setRequestContext(rc);

            // Path param "s" since client calls /users/s
            event.setPathParameters(Map.of("user_id", "s"));
            APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

            assertEquals(200, response.getStatusCode());
            assertTrue(response.getBody().contains("Worst Co"));
            assertTrue(response.getBody().contains("Labor"));
            assertTrue(response.getBody().contains("Environment"));
        }
    }

}
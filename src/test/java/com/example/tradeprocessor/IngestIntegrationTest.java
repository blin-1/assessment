package com.example.tradeprocessor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lightweight integration-style test that exercises POST /api/trades against a
 * running local instance. The test will be skipped when the application is not
 * reachable at http://localhost:8082.
 */
public class IngestIntegrationTest {

    @Test
    public void ingestTradeAgainstRunningApp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Short-circuit the test if the app is not running
        HttpRequest healthReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8082/actuator/health"))
                .GET()
                .build();

        try {
            HttpResponse<String> healthResp = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
            Assumptions.assumeTrue(healthResp.statusCode() == 200, "Application health check not OK");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Application not reachable: " + e.getMessage());
        }

        // Read sample request body from workspace
        String json = Files.readString(Path.of("sample-data/requests/ingest-trade.json"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8082/api/trades"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, resp.statusCode(), "Expected HTTP 201 Created from /api/trades");
        assertNotNull(resp.body(), "Response body should not be null");
        assertFalse(resp.body().trim().isEmpty(), "Response body should contain canonical id string");
    }
}

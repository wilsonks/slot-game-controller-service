package com.slotcentral.gamecontroller.client;

import com.slotcentral.gamecontroller.dto.GameEngineSpinRequest;
import com.slotcentral.gamecontroller.dto.GameEngineSpinResponse;
import com.slotcentral.gamecontroller.exception.GameEngineException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class GameEngineServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "gameEngineService";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public GameEngineServiceClient(RestClient.Builder restClientBuilder,
                                    @Value("${services.game-engine.url:http://localhost:8083}") String gameEngineUrl) {
        this.restClient = restClientBuilder
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(gameEngineUrl)
            .build();
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "computeSpinFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public GameEngineSpinResponse computeSpin(GameEngineSpinRequest request) {
        try {
            return requestSpec("/api/v1/engine/spin")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GameEngineSpinResponse.class);
        } catch (RestClientException e) {
            throw new GameEngineException("Failed to compute spin: " + e.getMessage(), e);
        }
    }

    public GameEngineSpinResponse computeSpinFallback(GameEngineSpinRequest request, Throwable t) {
        throw new GameEngineException("Game engine service unavailable: " + t.getMessage(), 502);
    }

    private RequestBodySpec requestSpec(String uri) {
        RequestBodySpec spec = restClient.post().uri(uri);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            spec.header(CORRELATION_ID_HEADER, correlationId);
        }
        return spec;
    }
}

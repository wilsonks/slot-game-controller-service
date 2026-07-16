package com.slotcentral.gamecontroller.client;

import com.slotcentral.gamecontroller.dto.BankReserveRequest;
import com.slotcentral.gamecontroller.dto.BankReserveResponse;
import com.slotcentral.gamecontroller.dto.BankSettleRequest;
import com.slotcentral.gamecontroller.dto.BankSettleResponse;
import com.slotcentral.gamecontroller.exception.BankServiceException;
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
public class BankServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "bankService";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public BankServiceClient(RestClient.Builder restClientBuilder,
                              @Value("${services.bank.url:http://localhost:8084}") String bankServiceUrl) {
        this.restClient = restClientBuilder
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(bankServiceUrl)
            .build();
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "reserveBetFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public BankReserveResponse reserveBet(BankReserveRequest request) {
        try {
            return requestSpec("/api/v1/bank/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BankReserveResponse.class);
        } catch (RestClientException e) {
            throw new BankServiceException("Failed to reserve bet: " + e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "settleWinFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public BankSettleResponse settleWin(BankSettleRequest request) {
        try {
            return requestSpec("/api/v1/bank/settle")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BankSettleResponse.class);
        } catch (RestClientException e) {
            throw new BankServiceException("Failed to settle win: " + e.getMessage(), e);
        }
    }

    public BankReserveResponse reserveBetFallback(BankReserveRequest request, Throwable t) {
        throw new BankServiceException("Bank service unavailable for reserve: " + t.getMessage(), 502);
    }

    public BankSettleResponse settleWinFallback(BankSettleRequest request, Throwable t) {
        throw new BankServiceException("Bank service unavailable for settle: " + t.getMessage(), 502);
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

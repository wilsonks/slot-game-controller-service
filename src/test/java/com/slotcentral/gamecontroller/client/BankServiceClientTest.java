package com.slotcentral.gamecontroller.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.slotcentral.gamecontroller.dto.BankReserveRequest;
import com.slotcentral.gamecontroller.dto.BankReserveResponse;
import com.slotcentral.gamecontroller.dto.BankSettleRequest;
import com.slotcentral.gamecontroller.dto.BankSettleResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class BankServiceClientTest {

    static WireMockServer wireMockServer;
    BankServiceClient client;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        client = new BankServiceClient(RestClient.builder(), "http://localhost:" + wireMockServer.port());
    }

    @Test
    void reserveBet_success() throws Exception {
        String responseBody = objectMapper.writeValueAsString(
            new BankReserveResponse("txn-001", "RESERVED", "OK"));

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/bank/reserve"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        BankReserveRequest request = new BankReserveRequest("spin-001", "player-123",
            new BigDecimal("5.00"), "Test reserve");
        BankReserveResponse response = client.reserveBet(request);

        assertThat(response.transactionId()).isEqualTo("txn-001");
        assertThat(response.status()).isEqualTo("RESERVED");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/bank/reserve"))
            .withRequestBody(matchingJsonPath("$.spinId", equalTo("spin-001")))
            .withRequestBody(matchingJsonPath("$.playerUid", equalTo("player-123"))));
    }

    @Test
    void settleWin_success() throws Exception {
        String responseBody = objectMapper.writeValueAsString(
            new BankSettleResponse("txn-002", "SETTLED", "OK"));

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/bank/settle"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        BankSettleRequest request = new BankSettleRequest("spin-001", "player-123",
            new BigDecimal("10.00"), "Test settle");
        BankSettleResponse response = client.settleWin(request);

        assertThat(response.transactionId()).isEqualTo("txn-002");
        assertThat(response.status()).isEqualTo("SETTLED");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/bank/settle"))
            .withRequestBody(matchingJsonPath("$.spinId", equalTo("spin-001")))
            .withRequestBody(matchingJsonPath("$.playerUid", equalTo("player-123"))));
    }
}

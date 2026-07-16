package com.slotcentral.gamecontroller.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.slotcentral.gamecontroller.dto.GameEngineSpinRequest;
import com.slotcentral.gamecontroller.dto.GameEngineSpinResponse;
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

class GameEngineServiceClientTest {

    static WireMockServer wireMockServer;
    GameEngineServiceClient client;
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
        client = new GameEngineServiceClient(RestClient.builder(), "http://localhost:" + wireMockServer.port());
    }

    @Test
    void computeSpin_success() throws Exception {
        String responseBody = objectMapper.writeValueAsString(
            new GameEngineSpinResponse("spin-001", "game-01", new BigDecimal("3.50"),
                "{\"stops\":[1,2,3]}", true, null));

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/engine/spin"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));

        GameEngineSpinRequest request = new GameEngineSpinRequest("spin-001", "game-01",
            new BigDecimal("1.00"), 20, 1);
        GameEngineSpinResponse response = client.computeSpin(request);

        assertThat(response.spinId()).isEqualTo("spin-001");
        assertThat(response.payoutAmount()).isEqualByComparingTo("3.50");
        assertThat(response.valid()).isTrue();

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/engine/spin"))
            .withRequestBody(matchingJsonPath("$.spinId", equalTo("spin-001")))
            .withRequestBody(matchingJsonPath("$.gameId", equalTo("game-01"))));
    }
}

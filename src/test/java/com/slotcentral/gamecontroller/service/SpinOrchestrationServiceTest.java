package com.slotcentral.gamecontroller.service;

import com.slotcentral.gamecontroller.client.BankServiceClient;
import com.slotcentral.gamecontroller.client.GameEngineServiceClient;
import com.slotcentral.gamecontroller.dto.BankReserveResponse;
import com.slotcentral.gamecontroller.dto.BankSettleResponse;
import com.slotcentral.gamecontroller.dto.GameEngineSpinResponse;
import com.slotcentral.gamecontroller.dto.SpinRequest;
import com.slotcentral.gamecontroller.dto.SpinResponse;
import com.slotcentral.gamecontroller.entity.GameRecord;
import com.slotcentral.gamecontroller.entity.SpinStatus;
import com.slotcentral.gamecontroller.exception.BankServiceException;
import com.slotcentral.gamecontroller.exception.GameEngineException;
import com.slotcentral.gamecontroller.exception.SpinDiscrepancyException;
import com.slotcentral.gamecontroller.repository.GameRecordRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpinOrchestrationServiceTest {

    @Mock
    private BankServiceClient bankServiceClient;

    @Mock
    private GameEngineServiceClient gameEngineServiceClient;

    @Mock
    private GameRecordRepository gameRecordRepository;

    private SpinOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new SpinOrchestrationService(bankServiceClient, gameEngineServiceClient, gameRecordRepository);
        setupSecurityContext("player-123");
        when(gameRecordRepository.save(any(GameRecord.class))).thenAnswer(inv -> {
            GameRecord record = inv.getArgument(0);
            if (record.getId() == null) {
                record.setId(1L);
            }
            if (record.getCreatedAt() == null) {
                java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
                record.setCreatedAt(now);
                record.setUpdatedAt(now);
            }
            return record;
        });
    }

    @Test
    void happyPath_returnCompletedSpin() {
        SpinRequest request = new SpinRequest("player-123", "game-01", new BigDecimal("1.00"), 20, 1);
        when(bankServiceClient.reserveBet(any())).thenReturn(new BankReserveResponse("txn-1", "RESERVED", "OK"));
        when(gameEngineServiceClient.computeSpin(any())).thenReturn(
            new GameEngineSpinResponse("spin-1", "game-01", new BigDecimal("2.50"), "{\"lines\":[[1,2,3]]}", true, null));
        when(bankServiceClient.settleWin(any())).thenReturn(new BankSettleResponse("txn-2", "SETTLED", "OK"));

        SpinResponse response = service.processSpin(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.playerUid()).isEqualTo("player-123");
        assertThat(response.payoutAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void engineFailure_statusSetToEngineFailure() {
        SpinRequest request = new SpinRequest("player-123", "game-01", new BigDecimal("1.00"), 20, 1);
        when(bankServiceClient.reserveBet(any())).thenReturn(new BankReserveResponse("txn-1", "RESERVED", "OK"));
        when(gameEngineServiceClient.computeSpin(any())).thenThrow(new GameEngineException("Engine down", 502));

        assertThatThrownBy(() -> service.processSpin(request))
            .isInstanceOf(GameEngineException.class);

        ArgumentCaptor<GameRecord> captor = ArgumentCaptor.forClass(GameRecord.class);
        verify(gameRecordRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(record -> record.getStatus() == SpinStatus.ENGINE_FAILURE);
    }

    @Test
    void settlementFailure_throwsDiscrepancyException() {
        SpinRequest request = new SpinRequest("player-123", "game-01", new BigDecimal("1.00"), 20, 1);
        when(bankServiceClient.reserveBet(any())).thenReturn(new BankReserveResponse("txn-1", "RESERVED", "OK"));
        when(gameEngineServiceClient.computeSpin(any())).thenReturn(
            new GameEngineSpinResponse("spin-1", "game-01", new BigDecimal("5.00"), "{}", true, null));
        when(bankServiceClient.settleWin(any())).thenThrow(new BankServiceException("Settle failed", 502));

        assertThatThrownBy(() -> service.processSpin(request))
            .isInstanceOf(SpinDiscrepancyException.class)
            .hasMessageContaining("reconciliation");

        ArgumentCaptor<GameRecord> captor = ArgumentCaptor.forClass(GameRecord.class);
        verify(gameRecordRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(record -> record.getStatus() == SpinStatus.SETTLEMENT_FAILED);
    }

    @Test
    void bankReserveFailure_throwsBankServiceException() {
        SpinRequest request = new SpinRequest("player-123", "game-01", new BigDecimal("1.00"), 20, 1);
        when(bankServiceClient.reserveBet(any())).thenThrow(new BankServiceException("Insufficient funds", 422));

        assertThatThrownBy(() -> service.processSpin(request))
            .isInstanceOf(BankServiceException.class)
            .hasMessageContaining("Insufficient funds");
    }

    private void setupSecurityContext(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}

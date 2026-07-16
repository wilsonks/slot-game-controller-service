package com.slotcentral.gamecontroller.service;

import com.slotcentral.gamecontroller.client.BankServiceClient;
import com.slotcentral.gamecontroller.client.GameEngineServiceClient;
import com.slotcentral.gamecontroller.dto.BankReserveRequest;
import com.slotcentral.gamecontroller.dto.BankReserveResponse;
import com.slotcentral.gamecontroller.dto.BankSettleRequest;
import com.slotcentral.gamecontroller.dto.BankSettleResponse;
import com.slotcentral.gamecontroller.dto.GameEngineSpinRequest;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpinOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(SpinOrchestrationService.class);

    private final BankServiceClient bankServiceClient;
    private final GameEngineServiceClient gameEngineServiceClient;
    private final GameRecordRepository gameRecordRepository;

    public SpinOrchestrationService(BankServiceClient bankServiceClient,
                                     GameEngineServiceClient gameEngineServiceClient,
                                     GameRecordRepository gameRecordRepository) {
        this.bankServiceClient = bankServiceClient;
        this.gameEngineServiceClient = gameEngineServiceClient;
        this.gameRecordRepository = gameRecordRepository;
    }

    @Transactional
    public SpinResponse processSpin(SpinRequest request) {
        String playerUid = extractPlayerUidFromJwt();
        String spinId = UUID.randomUUID().toString();
        String correlationId = MDC.get("correlationId");

        GameRecord record = new GameRecord();
        record.setSpinId(spinId);
        record.setPlayerUid(playerUid);
        record.setGameId(request.gameId());
        record.setBetAmount(request.betAmount());
        record.setStatus(SpinStatus.PENDING);
        record.setCorrelationId(correlationId);
        record = gameRecordRepository.save(record);

        try {
            BankReserveResponse reserveResponse = bankServiceClient.reserveBet(
                new BankReserveRequest(spinId, playerUid, request.betAmount(),
                    "Bet reserve for game " + request.gameId()));
            record.setBankReserveStatus(reserveResponse.status());
        } catch (BankServiceException e) {
            log.error("Bank reserve failed for spinId={}: {}", spinId, e.getMessage());
            record.setStatus(SpinStatus.PENDING);
            record.setBankReserveStatus("FAILED");
            gameRecordRepository.save(record);
            throw e;
        }

        GameEngineSpinResponse engineResponse;
        try {
            engineResponse = gameEngineServiceClient.computeSpin(
                new GameEngineSpinRequest(spinId, request.gameId(), request.betAmount(),
                    request.lines(), request.denomination()));
            record.setEngineStatus("SUCCESS");
            record.setResultSummary(engineResponse.resultSummary());
            record.setPayoutAmount(engineResponse.payoutAmount());
        } catch (GameEngineException e) {
            log.error("Game engine failed for spinId={}: {}", spinId, e.getMessage());
            record.setStatus(SpinStatus.ENGINE_FAILURE);
            record.setEngineStatus("FAILED");
            gameRecordRepository.save(record);
            throw e;
        }

        if (!engineResponse.valid()) {
            record.setStatus(SpinStatus.INVALID_BET);
            record.setEngineStatus("INVALID");
            record.setResultSummary(engineResponse.errorMessage());
            record.setPayoutAmount(BigDecimal.ZERO);
            record = gameRecordRepository.save(record);
            return toSpinResponse(record);
        }

        BigDecimal payoutAmount = engineResponse.payoutAmount() != null ? engineResponse.payoutAmount() : BigDecimal.ZERO;

        try {
            BankSettleResponse settleResponse = bankServiceClient.settleWin(
                new BankSettleRequest(spinId, playerUid, payoutAmount,
                    "Win settlement for game " + request.gameId()));
            record.setBankSettleStatus(settleResponse.status());
            record.setStatus(SpinStatus.COMPLETED);
        } catch (BankServiceException e) {
            log.error("Bank settle failed for spinId={}: {}", spinId, e.getMessage());
            record.setStatus(SpinStatus.SETTLEMENT_FAILED);
            record.setBankSettleStatus("FAILED");
            gameRecordRepository.save(record);
            throw new SpinDiscrepancyException(
                "Spin completed but settlement failed - requires reconciliation",
                spinId, playerUid, payoutAmount);
        }

        record = gameRecordRepository.save(record);
        return toSpinResponse(record);
    }

    public Page<SpinResponse> getHistory(String playerUid, String gameId, SpinStatus status,
                                          OffsetDateTime from, OffsetDateTime to,
                                          int page, int size, String sort) {
        Sort sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortObj = Sort.by(direction, field);
        }
        Pageable pageable = PageRequest.of(page, size, sortObj);
        return gameRecordRepository.findWithFilters(playerUid, gameId, status, from, to, pageable)
            .map(this::toSpinResponse);
    }

    @Scheduled(fixedDelayString = "${reconciliation.retry-interval-ms:60000}")
    public void reconcileFailedSpins() {
        List<GameRecord> failedRecords = gameRecordRepository.findByStatusIn(
            List.of(SpinStatus.SETTLEMENT_FAILED, SpinStatus.PENDING));
        if (!failedRecords.isEmpty()) {
            log.info("Reconciliation job found {} records requiring attention", failedRecords.size());
        }
    }

    private String extractPlayerUidFromJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new IllegalStateException("Unable to extract playerUid from JWT");
    }

    private SpinResponse toSpinResponse(GameRecord record) {
        return new SpinResponse(
            record.getSpinId(),
            record.getPlayerUid(),
            record.getGameId(),
            record.getBetAmount(),
            record.getPayoutAmount(),
            record.getResultSummary(),
            record.getStatus().name(),
            record.getCreatedAt()
        );
    }
}

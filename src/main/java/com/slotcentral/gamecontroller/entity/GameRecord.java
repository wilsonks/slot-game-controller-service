package com.slotcentral.gamecontroller.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_records")
public class GameRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spin_id", nullable = false, unique = true, length = 36)
    private String spinId;

    @Column(name = "player_uid", nullable = false)
    private String playerUid;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "bet_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal betAmount;

    @Column(name = "payout_amount", precision = 19, scale = 4)
    private BigDecimal payoutAmount;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SpinStatus status;

    @Column(name = "bank_reserve_status", length = 100)
    private String bankReserveStatus;

    @Column(name = "bank_settle_status", length = 100)
    private String bankSettleStatus;

    @Column(name = "engine_status", length = 100)
    private String engineStatus;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.spinId == null) {
            this.spinId = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpinId() { return spinId; }
    public void setSpinId(String spinId) { this.spinId = spinId; }
    public String getPlayerUid() { return playerUid; }
    public void setPlayerUid(String playerUid) { this.playerUid = playerUid; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public BigDecimal getBetAmount() { return betAmount; }
    public void setBetAmount(BigDecimal betAmount) { this.betAmount = betAmount; }
    public BigDecimal getPayoutAmount() { return payoutAmount; }
    public void setPayoutAmount(BigDecimal payoutAmount) { this.payoutAmount = payoutAmount; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public SpinStatus getStatus() { return status; }
    public void setStatus(SpinStatus status) { this.status = status; }
    public String getBankReserveStatus() { return bankReserveStatus; }
    public void setBankReserveStatus(String bankReserveStatus) { this.bankReserveStatus = bankReserveStatus; }
    public String getBankSettleStatus() { return bankSettleStatus; }
    public void setBankSettleStatus(String bankSettleStatus) { this.bankSettleStatus = bankSettleStatus; }
    public String getEngineStatus() { return engineStatus; }
    public void setEngineStatus(String engineStatus) { this.engineStatus = engineStatus; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

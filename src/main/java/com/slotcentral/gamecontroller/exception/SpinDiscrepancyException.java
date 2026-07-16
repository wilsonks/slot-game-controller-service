package com.slotcentral.gamecontroller.exception;

import java.math.BigDecimal;

public class SpinDiscrepancyException extends RuntimeException {
    private final String spinId;
    private final String playerUid;
    private final BigDecimal payoutAmount;

    public SpinDiscrepancyException(String message, String spinId, String playerUid, BigDecimal payoutAmount) {
        super(message);
        this.spinId = spinId;
        this.playerUid = playerUid;
        this.payoutAmount = payoutAmount;
    }

    public String getSpinId() { return spinId; }
    public String getPlayerUid() { return playerUid; }
    public BigDecimal getPayoutAmount() { return payoutAmount; }
}

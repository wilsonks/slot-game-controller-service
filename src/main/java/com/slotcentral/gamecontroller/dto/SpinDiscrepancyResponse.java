package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;

public record SpinDiscrepancyResponse(
    String spinId,
    String playerUid,
    BigDecimal payoutAmount,
    String message,
    String status
) {}

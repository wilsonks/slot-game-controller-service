package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SpinResponse(
    String spinId,
    String playerUid,
    String gameId,
    BigDecimal betAmount,
    BigDecimal payoutAmount,
    String resultSummary,
    String status,
    OffsetDateTime timestamp
) {}

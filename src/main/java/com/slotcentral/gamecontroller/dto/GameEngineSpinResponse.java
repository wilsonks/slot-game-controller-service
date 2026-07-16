package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;

public record GameEngineSpinResponse(
    String spinId,
    String gameId,
    BigDecimal payoutAmount,
    String resultSummary,
    boolean valid,
    String errorMessage
) {}

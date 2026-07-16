package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;

public record GameEngineSpinRequest(
    String spinId,
    String gameId,
    BigDecimal betAmount,
    Integer lines,
    Integer denomination
) {}

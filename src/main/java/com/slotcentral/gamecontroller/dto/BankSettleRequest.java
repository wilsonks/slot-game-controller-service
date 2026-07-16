package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;

public record BankSettleRequest(
    String spinId,
    String playerUid,
    BigDecimal winAmount,
    String description
) {}

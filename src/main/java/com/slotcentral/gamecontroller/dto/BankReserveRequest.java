package com.slotcentral.gamecontroller.dto;

import java.math.BigDecimal;

public record BankReserveRequest(
    String spinId,
    String playerUid,
    BigDecimal amount,
    String description
) {}

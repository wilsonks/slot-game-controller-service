package com.slotcentral.gamecontroller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SpinRequest(
    @NotBlank String playerUid,
    @NotBlank String gameId,
    @NotNull @Positive BigDecimal betAmount,
    Integer lines,
    Integer denomination
) {}

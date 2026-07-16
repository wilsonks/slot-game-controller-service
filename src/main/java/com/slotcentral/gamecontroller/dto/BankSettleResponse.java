package com.slotcentral.gamecontroller.dto;

public record BankSettleResponse(
    String transactionId,
    String status,
    String message
) {}

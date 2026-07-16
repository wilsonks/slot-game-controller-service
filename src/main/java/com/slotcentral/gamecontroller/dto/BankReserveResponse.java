package com.slotcentral.gamecontroller.dto;

public record BankReserveResponse(
    String transactionId,
    String status,
    String message
) {}

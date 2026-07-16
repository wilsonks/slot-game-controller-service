package com.slotcentral.gamecontroller.dto;

import com.slotcentral.gamecontroller.entity.SpinStatus;
import java.time.OffsetDateTime;

public record HistoryFilterRequest(
    String playerUid,
    String gameId,
    SpinStatus status,
    OffsetDateTime from,
    OffsetDateTime to,
    int page,
    int size,
    String sort
) {}

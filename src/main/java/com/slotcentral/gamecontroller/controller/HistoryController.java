package com.slotcentral.gamecontroller.controller;

import com.slotcentral.gamecontroller.dto.SpinResponse;
import com.slotcentral.gamecontroller.entity.SpinStatus;
import com.slotcentral.gamecontroller.service.SpinOrchestrationService;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final SpinOrchestrationService spinOrchestrationService;

    public HistoryController(SpinOrchestrationService spinOrchestrationService) {
        this.spinOrchestrationService = spinOrchestrationService;
    }

    @GetMapping("/history")
    public ResponseEntity<Page<SpinResponse>> getHistory(
            @RequestParam(required = false) String playerUid,
            @RequestParam(required = false) String gameId,
            @RequestParam(required = false) SpinStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        Page<SpinResponse> history = spinOrchestrationService.getHistory(
            playerUid, gameId, status, from, to, page, size, sort);
        return ResponseEntity.ok(history);
    }
}

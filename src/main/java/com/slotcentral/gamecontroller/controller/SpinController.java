package com.slotcentral.gamecontroller.controller;

import com.slotcentral.gamecontroller.dto.SpinRequest;
import com.slotcentral.gamecontroller.dto.SpinResponse;
import com.slotcentral.gamecontroller.service.SpinOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SpinController {

    private final SpinOrchestrationService spinOrchestrationService;

    public SpinController(SpinOrchestrationService spinOrchestrationService) {
        this.spinOrchestrationService = spinOrchestrationService;
    }

    @PostMapping("/spin")
    public ResponseEntity<SpinResponse> spin(@Valid @RequestBody SpinRequest request) {
        SpinResponse response = spinOrchestrationService.processSpin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

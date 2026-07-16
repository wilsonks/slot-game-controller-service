package com.slotcentral.gamecontroller.exception;

import com.slotcentral.gamecontroller.dto.SpinDiscrepancyResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                fe -> fe.getField(),
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (a, b) -> a));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("status", 422, "message", "Validation failed", "errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("status", 422, "message", ex.getMessage()));
    }

    @ExceptionHandler(BankServiceException.class)
    public ResponseEntity<Map<String, Object>> handleBankServiceException(BankServiceException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("status", ex.getStatusCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(GameEngineException.class)
    public ResponseEntity<Map<String, Object>> handleGameEngineException(GameEngineException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("status", ex.getStatusCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(SpinDiscrepancyException.class)
    public ResponseEntity<SpinDiscrepancyResponse> handleSpinDiscrepancy(SpinDiscrepancyException ex) {
        SpinDiscrepancyResponse response = new SpinDiscrepancyResponse(
            ex.getSpinId(),
            ex.getPlayerUid(),
            ex.getPayoutAmount(),
            ex.getMessage(),
            "SETTLEMENT_FAILED"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("status", 401, "message", ex.getMessage()));
    }
}

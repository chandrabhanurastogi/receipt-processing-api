package com.receipts.api.web.exception;

import com.receipts.api.dto.response.ErrorResponse;
import com.receipts.api.dto.response.ReconciliationConflictResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.math.BigDecimal;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ReconciliationConflictException.class)
    public ResponseEntity<ReconciliationConflictResponse> handleConflict(ReconciliationConflictException ex) {
        BigDecimal itemsTotal = ex.getProposedItemsTotal();
        BigDecimal taxTotal = ex.getTaxTotal();
        BigDecimal expectedTotal = ex.getExpectedGrandTotal();
        BigDecimal calculatedTotal = itemsTotal.add(taxTotal);
        BigDecimal difference = expectedTotal.subtract(calculatedTotal);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ReconciliationConflictResponse(
                        "RECONCILIATION_CONFLICT",
                        ex.getMessage(),
                        expectedTotal,
                        itemsTotal,
                        taxTotal,
                        calculatedTotal,
                        difference));
    }

    @ExceptionHandler(ExtractionFailedException.class)
    public ResponseEntity<ErrorResponse> handleExtractionFailed(ExtractionFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed request: " + ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed request body"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("Uploaded file exceeds the maximum allowed size"));
    }
}

package com.receipts.api.web.exception;

import com.receipts.api.dto.response.ErrorResponse;
import com.receipts.api.dto.response.ReconciliationConflictResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ReconciliationConflictException.class)
    public ResponseEntity<ReconciliationConflictResponse> handleConflict(ReconciliationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ReconciliationConflictResponse(
                        ex.getMessage(),
                        ex.getProposedItemsTotal(),
                        ex.getTaxTotal(),
                        ex.getExpectedGrandTotal()));
    }

    @ExceptionHandler(ExtractionFailedException.class)
    public ResponseEntity<ErrorResponse> handleExtractionFailed(ExtractionFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
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

    /**
     * Endpoints whose business logic is not implemented yet at this project
     * stage (processing/itemize/PATCH flow) report 501 instead of an
     * uncontrolled 500 until the processing-flow stage lands.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleNotImplemented(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ErrorResponse(ex.getMessage()));
    }
}

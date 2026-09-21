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
import org.springframework.web.multipart.MultipartException;

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

    /**
     * More specific than the IllegalArgumentException handler below, so it
     * takes precedence for it: a non-numeric @PathVariable Long (e.g.
     * GET /transactions/abc) surfaces as a raw NumberFormatException from
     * Spring's argument conversion. Without this, its message ("For input
     * string: \"abc\"") would leak straight through the generic handler
     * instead of a clean, consistent error.
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleNumberFormat(NumberFormatException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Malformed request: invalid numeric identifier"));
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

    /**
     * Catches the general "this isn't a multipart request at all" case (e.g.
     * POST /receipts with no multipart body/boundary) - without this it was
     * an uncaught 500 instead of a 400 "invalid upload".
     * MaxUploadSizeExceededException above is more specific and still wins
     * for that case.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Invalid or missing multipart upload"));
    }
}

package com.worksphere.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldViolation(
                        fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR, "Validation failed", request.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Request body is malformed or contains an invalid value",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // e.g. status=NOT_A_STATUS or /tasks/not-a-uuid. The message names the parameter only -
        // never echoes the raw value or the exception text back to the client.
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Parameter '" + ex.getName() + "' has an invalid value",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSortProperty(
            PropertyReferenceException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                "Cannot sort or filter by '" + ex.getPropertyName() + "'",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Spring's own "this request cannot be routed or understood" errors: unknown URL (404), wrong HTTP
     * method (405), unsupported or unacceptable media type (415 / 406), missing parameter or file part
     * (400). Without this they fall into the catch-all below and are reported as 500 INTERNAL_ERROR,
     * which is wrong for clients and pollutes server-error alerts. The message is generic on purpose.
     */
    @ExceptionHandler({
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleRoutingProblem(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(((org.springframework.web.ErrorResponse) ex).getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        String code;
        String message;
        switch (status) {
            case NOT_FOUND -> {
                code = ErrorCode.NOT_FOUND;
                message = "The requested resource does not exist";
            }
            case METHOD_NOT_ALLOWED -> {
                code = ErrorCode.METHOD_NOT_ALLOWED;
                message = "This HTTP method is not supported for this resource";
            }
            case UNSUPPORTED_MEDIA_TYPE -> {
                code = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                message = "The request content type is not supported";
            }
            case NOT_ACCEPTABLE -> {
                code = ErrorCode.NOT_ACCEPTABLE;
                message = "The requested response type is not available";
            }
            default -> {
                code = ErrorCode.VALIDATION_ERROR;
                message = "The request is missing a required parameter or file";
            }
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.FILE_TOO_LARGE, "The file is larger than the 10 MB limit", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ex.getCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse body =
                ErrorResponse.of(ErrorCode.ACCESS_DENIED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        // Covers the genuine-race case: two requests both read the same version and one commits
        // first. The far more common case - a client simply sending a stale version on purpose
        // or by accident - is caught earlier by an explicit check and throws StaleVersionException
        // (an ApiException) directly, never reaching here.
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.STALE_VERSION,
                "This resource was modified by someone else - reload it and try again",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

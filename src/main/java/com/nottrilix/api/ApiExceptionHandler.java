package com.nottrilix.api;

import com.nottrilix.agent.AgentUnavailableException;
import com.nottrilix.analysis.AnalysisFailedException;
import com.nottrilix.privacy.ConsentRequiredException;
import com.nottrilix.api.dto.ErrorResponse;
import com.nottrilix.kb.KnowledgeBaseUnavailableException;
import com.nottrilix.service.BusinessRuleException;
import com.nottrilix.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/** Greske -> JSON {error, message}. Poslovna pravila su vec logirana u servisu (WARN). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse businessRule(BusinessRuleException e) {
        return new ErrorResponse(e.code(), e.getMessage());
    }

    /** Neuspjela prijava (login endpointi); pokusaj je vec logiran u AuthenticationProvideru. */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse authentication(AuthenticationException e) {
        return new ErrorResponse("INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(AgentUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse agentUnavailable(AgentUnavailableException e) {
        log.warn("AGENT UNAVAILABLE - {}", e.getMessage());
        return new ErrorResponse("AGENT_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(ConsentRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse consentRequired(ConsentRequiredException e) {
        log.warn("CONSENT REQUIRED - {}", e.getMessage());
        return new ErrorResponse("CONSENT_REQUIRED", e.getMessage());
    }

    @ExceptionHandler(AnalysisFailedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse analysisFailed(AnalysisFailedException e) {
        log.warn("ANALYSIS FAILED - {}", e.getMessage());
        return new ErrorResponse("ANALYSIS_FAILED", e.getMessage());
    }

    @ExceptionHandler(KnowledgeBaseUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse knowledgeBaseUnavailable(KnowledgeBaseUnavailableException e) {
        log.warn("KB UNAVAILABLE - {}", e.getMessage());
        return new ErrorResponse("KB_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(NotFoundException e) {
        log.warn("NOT FOUND - {}", e.getMessage());
        return new ErrorResponse("NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("VALIDATION - {}", details);
        return new ErrorResponse("VALIDATION_ERROR", details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(Exception e) {
        log.warn("BAD REQUEST - {}", e.getMessage());
        return new ErrorResponse("BAD_REQUEST", "Neispravan zahtjev");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> other(Exception e) {
        // Springove vlastite HTTP greske (404 resurs, 405 metoda...) zadrze svoj status
        if (e instanceof org.springframework.web.ErrorResponse spring) {
            HttpStatusCode status = spring.getStatusCode();
            log.warn("HTTP {} - {}", status.value(), e.getMessage());
            return ResponseEntity.status(status)
                    .body(new ErrorResponse("HTTP_" + status.value(), spring.getBody().getDetail()));
        }
        log.error("UNEXPECTED - {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Neočekivana greška"));
    }
}

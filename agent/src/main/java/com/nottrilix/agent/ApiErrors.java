package com.nottrilix.agent;

import com.nottrilix.agent.SystemGateway.InvalidTokenException;
import com.nottrilix.agent.SystemGateway.SystemUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Errors -> JSON {error, message}, the same shape as in the system. */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    public record ErrorResponse(String error, String message) {
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse invalidToken(InvalidTokenException e) {
        log.warn("401 - {}", e.getMessage());
        return new ErrorResponse("UNAUTHORIZED", e.getMessage());
    }

    @ExceptionHandler(AgentUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse agentUnavailable(AgentUnavailableException e) {
        log.warn("AGENT UNAVAILABLE - {}", e.getMessage());
        return new ErrorResponse("AGENT_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(VoiceService.VoiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse voiceUnavailable(VoiceService.VoiceUnavailableException e) {
        log.warn("VOICE UNAVAILABLE - {}", e.getMessage());
        return new ErrorResponse("VOICE_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(SystemUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse systemUnavailable(SystemUnavailableException e) {
        log.warn("SYSTEM UNAVAILABLE - {}", e.getMessage());
        return new ErrorResponse("SYSTEM_UNAVAILABLE", e.getMessage());
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(HttpMessageNotReadableException e) {
        log.warn("BAD REQUEST - {}", e.getMessage());
        return new ErrorResponse("BAD_REQUEST", "Neispravan zahtjev");
    }
}

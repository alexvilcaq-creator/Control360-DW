package com.control360.infrastructure.web;

import com.control360.domain.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(RuleViolation.class)
    ResponseEntity<Map<String, String>> rule(RuleViolation error) {
        HttpStatus status = switch (error.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case MISSING -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return message(status, error.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> credentials() {
        return message(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error) {
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message", "Revisa los campos del formulario", "fields", fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return message(HttpStatus.CONFLICT, "El registro ya existe o entra en conflicto con otros datos");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> locked() {
        return message(HttpStatus.CONFLICT, "Otra operación está actualizando este registro. Inténtalo de nuevo.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalidBody() {
        return message(HttpStatus.BAD_REQUEST, "La petición contiene datos inválidos");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> generic(Exception error) {
        if (error instanceof ErrorResponse http) {
            return ResponseEntity.status(http.getStatusCode()).body(Map.of("message",
                    http.getStatusCode().value() == 400 ? "La petición contiene datos inválidos" : "No se pudo procesar la petición"));
        }
        LOG.error("Error inesperado en la API", error);
        return message(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un problema al procesar la operación");
    }

    private ResponseEntity<Map<String, String>> message(HttpStatus status, String text) {
        return ResponseEntity.status(status).body(Map.of("message", text));
    }
}

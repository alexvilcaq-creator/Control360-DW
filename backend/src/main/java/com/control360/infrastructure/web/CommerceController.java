package com.control360.infrastructure.web;

import com.control360.application.CommerceUseCases;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CommerceController {
    private final CommerceUseCases operations;
    private final Store store;

    public CommerceController(CommerceUseCases operations, Store store) {
        this.operations = operations;
        this.store = store;
    }

    record PaymentBody(@NotNull PaymentMethod method, @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal received,
                       boolean confirmed) {}
    record IncidentBody(@NotNull @Positive Long productId, @NotNull IncidentType type, @NotBlank @Size(max = 500) String observation,
                        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,80}") String requestKey) {}
    record ReviewBody(@NotNull IncidentStatus status, @NotBlank @Size(max = 500) String note) {}

    @GetMapping("/pagos")
    List<PaymentView> payments(Principal principal) { return operations.payments(actor(principal)); }

    @GetMapping("/pedidos/{id}/pago")
    PaymentView payment(Principal principal, @PathVariable long id) { return operations.payment(actor(principal), id); }

    @PostMapping("/pedidos/{id}/pago")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentView record(Principal principal, @PathVariable long id, @Valid @RequestBody PaymentBody body) {
        return operations.recordPayment(actor(principal), id, body.method(), body.received(), body.confirmed());
    }

    @GetMapping("/incidencias")
    List<IncidentView> incidents(Principal principal) { return operations.incidents(actor(principal)); }

    @PostMapping("/incidencias")
    @ResponseStatus(HttpStatus.CREATED)
    IncidentView report(Principal principal, @Valid @RequestBody IncidentBody body) {
        return operations.report(actor(principal), body.productId(), body.type(), body.observation(), body.requestKey());
    }

    @PutMapping("/incidencias/{id}")
    IncidentView review(Principal principal, @PathVariable long id, @Valid @RequestBody ReviewBody body) {
        return operations.review(actor(principal), id, body.status(), body.note());
    }

    private User actor(Principal principal) { return store.user(principal.getName()).orElseThrow(); }
}

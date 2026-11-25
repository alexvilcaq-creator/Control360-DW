package com.control360.infrastructure.web;

import com.control360.application.OrderUseCases;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import static com.control360.infrastructure.web.Dtos.*;

@RestController
@RequestMapping("/api/pedidos")
public class OrdersController {
    private final OrderUseCases operations;
    private final Store store;

    public OrdersController(OrderUseCases operations, Store store) {
        this.operations = operations;
        this.store = store;
    }

    @GetMapping
    List<OrderView> orders(Principal principal) { return operations.orders(actor(principal)); }

    @GetMapping("/{id}")
    OrderView order(Principal principal, @PathVariable long id) { return operations.order(actor(principal), id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderView create(Principal principal, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                     @Valid @RequestBody OrderBody body) {
        return operations.createOrder(actor(principal), body.customerId(), body.items().stream()
                .map(item -> new LineRequest(item.productId(), item.quantity())).toList(), key);
    }

    @PostMapping("/{id}/aceptar")
    OrderView accept(Principal principal, @PathVariable long id, @Valid @RequestBody(required = false) Assignee body) {
        return operations.transition(actor(principal), id, Status.ACEPTADO, body == null ? null : body.workerId());
    }

    @PutMapping("/{id}/estado")
    OrderView change(Principal principal, @PathVariable long id, @Valid @RequestBody StateBody body) {
        return operations.transition(actor(principal), id, body.status(), body.workerId());
    }

    private User actor(Principal principal) { return store.user(principal.getName()).orElseThrow(); }
}

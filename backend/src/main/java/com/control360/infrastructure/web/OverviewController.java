package com.control360.infrastructure.web;

import com.control360.application.Operations;
import com.control360.application.Operations.NotificationView;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OverviewController {
    private final Operations operations;
    private final Store store;

    public OverviewController(Operations operations, Store store) {
        this.operations = operations;
        this.store = store;
    }

    @GetMapping("/calendario")
    Map<String, LocalDate> businessDate() { return Map.of("today", operations.today()); }

    @GetMapping({"/dashboard", "/reportes/resumen"})
    Dashboard dashboard(Principal principal, @RequestParam(required = false) LocalDate from,
                        @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? operations.today() : to;
        return operations.dashboard(actor(principal), from == null ? end : from, end);
    }

    @GetMapping("/notificaciones")
    List<NotificationView> notifications(Principal principal) { return operations.notifications(actor(principal)); }

    @PostMapping("/notificaciones/leer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(Principal principal) { operations.readNotifications(actor(principal)); }

    private User actor(Principal principal) { return store.user(principal.getName()).orElseThrow(); }
}

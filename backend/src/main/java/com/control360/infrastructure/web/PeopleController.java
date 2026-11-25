package com.control360.infrastructure.web;

import com.control360.application.Operations;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import com.control360.domain.RuleViolation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import static com.control360.infrastructure.web.Dtos.*;

@RestController
@RequestMapping("/api")
public class PeopleController {
    private final Operations operations;
    private final Store store;
    private final PasswordEncoder encoder;

    public PeopleController(Operations operations, Store store, PasswordEncoder encoder) {
        this.operations = operations;
        this.store = store;
        this.encoder = encoder;
    }

    @GetMapping("/trabajadores")
    List<WorkerView> workers() { return operations.workers(); }

    @PostMapping("/trabajadores")
    @ResponseStatus(HttpStatus.CREATED)
    Worker createWorker(@Valid @RequestBody WorkerBody body) { return operations.saveWorker(body.toDomain(null)); }

    @PutMapping("/trabajadores/{id}")
    Worker editWorker(@PathVariable Long id, @Valid @RequestBody WorkerBody body) {
        return operations.saveWorker(body.toDomain(id));
    }

    @GetMapping("/turnos")
    List<Shift> shifts() { return operations.list(Shift.class); }

    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    Shift createShift(@Valid @RequestBody ShiftBody body) { return operations.saveShift(body.toDomain(null)); }

    @PutMapping("/turnos/{id}")
    Shift editShift(@PathVariable Long id, @Valid @RequestBody ShiftBody body) { return operations.saveShift(body.toDomain(id)); }

    @GetMapping("/usuarios")
    List<UserProfile> users() { return operations.users(); }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    UserProfile createUser(Principal principal, @Valid @RequestBody UserBody body) { return saveUser(principal, null, body); }

    @PutMapping("/usuarios/{id}")
    UserProfile editUser(Principal principal, @PathVariable Long id, @Valid @RequestBody UserBody body) {
        return saveUser(principal, id, body);
    }

    private UserProfile saveUser(Principal principal, Long id, UserBody body) {
        String hash = null;
        if (body.password() != null && !body.password().isEmpty()) {
            if (body.password().isBlank() || body.password().length() < 12 || body.password().getBytes(StandardCharsets.UTF_8).length > 72)
                throw RuleViolation.invalid("La contraseña requiere al menos 12 caracteres y como máximo 72 bytes");
            hash = encoder.encode(body.password());
        }
        return operations.saveUser(new User(id, body.username(), hash, body.role(), body.active(), body.workerId()), actor(principal));
    }

    private User actor(Principal principal) { return store.user(principal.getName()).orElseThrow(); }
}
